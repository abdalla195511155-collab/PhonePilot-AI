package com.phonepilot.app.recording

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.phonepilot.app.models.EventEntity
import com.phonepilot.app.repository.RecordingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class PhonePilotAccessibilityService : AccessibilityService() {

    private val TAG = "PhonePilotA11y"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val handler = Handler(Looper.getMainLooper())

    /** counter for sequence numbers per recording */
    private var currentSequence = 0

    /** debounce tracking for TYPE_WINDOW_CONTENT_CHANGED */
    private var lastContentChangeTime = 0L
    private val CONTENT_CHANGE_DEBOUNCE_MS = 300L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")
        val info = serviceInfo
        info?.flags = (info?.flags ?: 0) or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        serviceInfo = info

        RecordingRepository.init(this)
        RecordingRepository.serviceConnected()

        // Reset sequence for new session
        currentSequence = 0
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val eventType = event.eventType

        // Debounce TYPE_WINDOW_CONTENT_CHANGED - skip rapid repeats
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val now = System.currentTimeMillis()
            if (now - lastContentChangeTime < CONTENT_CHANGE_DEBOUNCE_MS) {
                return
            }
            lastContentChangeTime = now
        }

        val recordingState = RecordingRepository.recordingState.value
        if (recordingState !is com.phonepilot.app.models.RecordingState.Recording) {
            return
        }

        currentSequence++

        val source = event.source
        val viewId = source?.viewIdResourceName
        val className = source?.className?.toString()
        val packageName = event.packageName?.toString()
        val text = source?.text?.toString()
        val contentDesc = source?.contentDescription?.toString()
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

        val beforeSnapshot = snapshot(source)

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

        val inputText = if (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            event.text?.joinToString("")
        } else null

        val eventEntity = EventEntity(
            recordingId = recordingState.taskId,
            sequenceNumber = currentSequence,
            eventType = eventTypeStr,
            packageName = packageName,
            className = className,
            viewId = viewId,
            text = text,
            contentDescription = contentDesc,
            bounds = bounds,
            inputText = inputText,
            timestamp = event.eventTime,
            beforeSnapshot = beforeSnapshot,
            afterSnapshot = null
        )

        RecordingRepository.addEvent(eventEntity)
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
        RecordingRepository.serviceInterrupted()
        currentSequence = 0
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Log.d(TAG, "Accessibility service unbind")
        RecordingRepository.serviceInterrupted()
        currentSequence = 0
        return super.onUnbind(intent)
    }

    /** التقاط شجرة AccessibilityNodeInfo بصيغة JSON مع حد أقصى للعمق والعقد */
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

            val o = JSONObject().apply {
                put("depth", depth)
                put("class", n.className?.toString() ?: "")
                put("viewId", n.viewIdResourceName ?: "")
                put("text", n.text?.toString() ?: "")
                put("desc", n.contentDescription?.toString() ?: "")
            }
            arr.put(o)
            count++

            for (i in n.childCount - 1 downTo 0) {
                n.getChild(i)?.let { stack.add(it to depth + 1) }
            }
        }
        return arr.toString()
    }
}
