package com.phonepilot.app.recording

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.phonepilot.app.models.EventEntity
import com.phonepilot.app.models.RecordingState
import com.phonepilot.app.privacy.PrivacyFilter
import com.phonepilot.app.repository.RecordingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import org.json.JSONArray
import org.json.JSONObject

class PhonePilotAccessibilityService : AccessibilityService() {

    private val TAG = "PhonePilotA11y"

    private val handler = Handler(Looper.getMainLooper())

    private var currentSequence = 0

    private var lastContentChangeTime = 0L
    private val CONTENT_CHANGE_DEBOUNCE_MS = 300L

    private var isServiceConnected = false

    override fun onCreate() {
        super.onCreate()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")
        val info = serviceInfo
        info?.flags = (info?.flags ?: 0) or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        serviceInfo = info

        RecordingRepository.init(this)
        isServiceConnected = true
        RecordingRepository.serviceConnected()

        val dbHelper = com.phonepilot.app.database.TaskDatabaseHelper.getInstance(this)
        dbHelper.markInterruptedRecordings()

        currentSequence = 0
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val eventType = event.eventType

        // Debounce TYPE_WINDOW_CONTENT_CHANGED
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val now = System.currentTimeMillis()
            if (now - lastContentChangeTime < CONTENT_CHANGE_DEBOUNCE_MS) {
                return
            }
            lastContentChangeTime = now
        }

        val recordingState = RecordingRepository.recordingState.value
        if (recordingState !is RecordingState.Recording) {
            return
        }

        val source = event.source
        val packageName = event.packageName?.toString()

        // Check password BEFORE extracting text/desc
        val isPasswordEvent = event.isPassword
        val isPasswordSource = source?.isPassword == true
        val eventIsPassword = isPasswordEvent || isPasswordSource
        val nodeSensitive = (source != null && PrivacyFilter.isNodeSensitive(source, packageName)) || eventIsPassword

        // Extract text only if not password/sensitive
        val rawText = if (nodeSensitive) null else source?.text?.toString()
        val rawDesc = if (nodeSensitive) null else source?.contentDescription?.toString()
        val viewId = source?.viewIdResourceName
        val className = source?.className?.toString()

        val bounds = source?.let {
            val r = android.graphics.Rect()
            it.getBoundsInScreen(r)
            JSONObject().apply {
                put("left", r.left)
                put("top", r.top)
                put("right", r.right)
                put("bottom", r.bottom)
            }.toString()
        } ?: ""

        // Snapshot with masking
        val beforeSnapshot = snapshot(source)
        val maskedBeforeSnapshot = PrivacyFilter.maskSnapshotJson(beforeSnapshot, packageName)

        val eventTypeStr = when (eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "click"
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> "long_click"
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "input"
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> "focus"
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> "scroll"
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "window_state"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "content_changed"
            else -> "other"
        }

        val rawInputText = if (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            event.text?.joinToString("")
        } else null

        // Apply privacy masking
        val text = if (nodeSensitive) "[MASKED]" else rawText
        val contentDesc = if (nodeSensitive) "[MASKED]" else rawDesc
        val inputText = if (nodeSensitive || PrivacyFilter.isSensitiveText(rawInputText)) "[MASKED]" else rawInputText

        // Also mask if package sensitive
        val pkgSensitive = PrivacyFilter.isSensitivePackage(packageName)
        val finalText = if (pkgSensitive) "[MASKED]" else text
        val finalDesc = if (pkgSensitive) "[MASKED]" else contentDesc
        val finalInput = if (pkgSensitive) "[MASKED]" else inputText

        val seq = RecordingRepository.nextSequence()

        val eventEntity = EventEntity(
            recordingId = 0, // Will be set by repository from current session
            sequenceNumber = seq,
            eventType = eventTypeStr,
            packageName = packageName,
            className = className,
            viewId = viewId,
            text = finalText,
            contentDescription = finalDesc,
            bounds = bounds,
            inputText = finalInput,
            timestamp = event.eventTime,
            beforeSnapshot = maskedBeforeSnapshot,
            afterSnapshot = null,
            isPrivacyMasked = nodeSensitive || pkgSensitive ||
                PrivacyFilter.isSensitiveText(rawText) || PrivacyFilter.isSensitiveText(rawDesc)
        )

        // Enqueue via repository (handles channel, dropped count)
        val accepted = RecordingRepository.tryEnqueueEvent(eventEntity)
        if (!accepted) {
            Log.w(TAG, "Event not accepted: channel full or session ended. sequence=$currentSequence")
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
        isServiceConnected = false
        RecordingRepository.serviceInterrupted()
        currentSequence = 0
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Log.d(TAG, "Accessibility service unbind")
        isServiceConnected = false
        RecordingRepository.serviceInterrupted()
        currentSequence = 0
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceConnected = false
        // Best-effort cleanup: close channel and attempt drain via coroutine.
        // Note: if the process is killed before the drain completes, pending
        // events may be lost. This is a fundamental Android limitation.
        // serviceInterrupted() is idempotent — safe to call after onUnbind.
        RecordingRepository.serviceInterrupted()
    }

    private fun snapshot(node: AccessibilityNodeInfo?): String {
        if (node == null) return "null"

        val MAX_DEPTH = 5
        val MAX_NODES = 100

        val arr = JSONArray()
        val stack = mutableListOf<Pair<AccessibilityNodeInfo, Int>>()
        stack.add(node to 0)
        var count = 0

        while (stack.isNotEmpty() && count < MAX_NODES) {
            val (n, depth) = stack.removeAt(stack.lastIndex)
            if (depth > MAX_DEPTH) continue

            val isNodePassword = n.isPassword
            val nodePkg = n.packageName?.toString()
            val nodeSensitive = PrivacyFilter.isNodeSensitive(n, nodePkg)

            val o = JSONObject().apply {
                put("depth", depth)
                put("class", n.className?.toString() ?: "")
                put("viewId", n.viewIdResourceName ?: "")
                put("text", if (isNodePassword || nodeSensitive) "[MASKED]" else n.text?.toString() ?: "")
                put("desc", if (isNodePassword || nodeSensitive) "[MASKED]" else n.contentDescription?.toString() ?: "")
            }
            arr.put(o)
            count++

            for (i in n.childCount - 1 downTo 0) {
                n.getChild(i)?.let { stack.add(it to depth + 1) }
            }
        }
        return arr.toString()
    }

    companion object {
        fun isServiceEnabled(context: android.content.Context): Boolean {
            try {
                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                if (enabledServices == null || enabledServices.isBlank()) return false

                val componentName = ComponentName(context.packageName, "com.phonepilot.app.recording.PhonePilotAccessibilityService")
                return enabledServices.contains(componentName.flattenToString())
            } catch (e: Exception) {
                return false
            }
        }
    }
}
