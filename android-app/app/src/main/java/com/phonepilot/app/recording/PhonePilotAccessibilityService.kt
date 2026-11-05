package com.phonepilot.app.recording

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import com.phonepilot.app.database.TaskDatabaseHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PhonePilotAccessibilityService : AccessibilityService() {

    private val TAG = "PhonePilotAccessibility"

    private var currentTaskId: Long = -1
    private var currentIteration: Int = 1
    private var eventsBuffer: MutableList<String> = mutableListOf()
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")
        val info = serviceInfo
        info?.flags = info?.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (currentTaskId < 0) return

        val eventType = when (event.eventType) {
            android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED -> "click"
            android.view.accessibility.AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "input"
            android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED -> "focus"
            else -> "other"
        }

        val sourceText = event.source?.text?.toString().takeIf { it.isNotEmpty() } ?: "[مخفي]"
        val eventJson = """{"type":"$eventType","text":"$sourceText","time":${event.eventTime}}"""
        eventsBuffer.add(eventJson)

        Log.d(TAG, "Accessibility event: $eventType, text: $sourceText")
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    fun startRecording(taskId: Long, iterationNumber: Int) {
        currentTaskId = taskId
        currentIteration = iterationNumber
        eventsBuffer.clear()
        Log.d(TAG, "Started recording task $taskId iteration $iterationNumber")
    }

    fun stopRecording() {
        if (currentTaskId < 0) return
        val rawEvents = eventsBuffer.joinToString(",", "[", "]")
        val db = TaskDatabaseHelper.getInstance(applicationContext)
        scope.launch {
            db.insertRecording(currentTaskId, currentIteration, rawEvents)
        }
        Log.d(TAG, "Stopped recording. Saved ${eventsBuffer.size} events for task $currentTaskId")
        currentTaskId = -1
        currentIteration = 1
        eventsBuffer.clear()
    }
}
