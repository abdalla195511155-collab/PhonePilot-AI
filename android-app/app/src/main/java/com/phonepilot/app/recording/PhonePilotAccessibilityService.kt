package com.phonepilot.app.recording

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.phonepilot.app.database.TaskDatabaseHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class PhonePilotAccessibilityService : AccessibilityService() {

    private val TAG = "PhonePilotA11y"
    private var currentTaskId: Long = -1
    private var currentIteration: Int = 1
    private val eventsBuffer = mutableListOf<String>()
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val db by lazy { TaskDatabaseHelper.getInstance(this) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")
        val info = serviceInfo
        info?.flags = (info?.flags ?: 0) or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (currentTaskId < 0) return

        val eventType = when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "click"
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> "long_click"
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "input"
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> "focus"
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> "scroll"
            else -> "other"
        }

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
                put("left", r.left); put("top", r.top); put("right", r.right); put("bottom", r.bottom)
            }.toString()
        } ?: ""

        val beforeSnapshot = snapshot(rootInActiveWindow)
        val inputText = if (eventType == "input") event.text?.joinToString("") else null

        val eventJson = JSONObject().apply {
            put("type", eventType)
            put("packageName", packageName ?: "")
            put("className", className ?: "")
            put("viewId", viewId ?: "")
            put("text", text ?: "")
            put("contentDescription", contentDesc ?: "")
            put("bounds", bounds)
            put("inputText", inputText ?: "")
            put("time", event.eventTime)
            put("beforeSnapshot", beforeSnapshot)
        }.toString()

        eventsBuffer.add(eventJson)
        Log.d(TAG, "Accessibility event: $eventType, viewId: $viewId")
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    fun startRecording(taskId: Long, iterationNumber: Int) {
        currentTaskId = taskId
        currentIteration = iterationNumber
        eventsBuffer.clear()
    }

    fun stopRecording() {
        val rawEvents = JSONArray(eventsBuffer).toString()
        scope.launch {
            db.insertRecording(currentTaskId, currentIteration, rawEvents)
        }
        currentTaskId = -1
        currentIteration = 1
        eventsBuffer.clear()
    }

    private fun snapshot(node: AccessibilityNodeInfo?): String {
        if (node == null) return "null"
        val arr = JSONArray()
        val stack = mutableListOf<Pair<AccessibilityNodeInfo, Int>>()
        stack.add(node to 0)
        var count = 0
        while (stack.isNotEmpty() && count < 200) {
            val (n, depth) = stack.removeAt(stack.lastIndex)
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
