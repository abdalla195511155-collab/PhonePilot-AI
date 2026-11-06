package com.phonepilot.app.models

sealed class RecordingState {
    object Idle : RecordingState()
    data class Recording(val taskId: Long, val recordingId: Long, val iterationNumber: Int, val eventCount: Int) : RecordingState()
    object Completed : RecordingState()
    data class Interrupted(val taskId: Long, val recordingId: Long, val eventCount: Int) : RecordingState()
}
