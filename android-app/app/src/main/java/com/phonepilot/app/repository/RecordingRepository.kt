package com.phonepilot.app.repository

import android.content.Context
import com.phonepilot.app.database.TaskDatabaseHelper
import com.phonepilot.app.models.EventEntity
import com.phonepilot.app.models.RecordingState
import com.phonepilot.app.privacy.PrivacyFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

object RecordingRepository {

    private val _recordingState = MutableStateFlow(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.stateIn(
        CoroutineScope(Dispatchers.IO + Job()),
        SharingStarted.WhileSubscribed(5000),
        RecordingState.Idle
    )

    private val _eventCount = MutableStateFlow(0)
    val eventCount: StateFlow<Int> = _eventCount.stateIn(
        CoroutineScope(Dispatchers.IO + Job()),
        SharingStarted.WhileSubscribed(5000),
        0
    )

    private var dbHelper: TaskDatabaseHelper? = null

    fun init(context: Context) {
        dbHelper = TaskDatabaseHelper.getInstance(context)
    }

    private val db: TaskDatabaseHelper
        get() = dbHelper ?: throw IllegalStateException("RecordingRepository not initialized")

    fun isInitialized(): Boolean = dbHelper != null

    /** التحقق من whether the service is connected and recording */
    fun isRecording(): Boolean = _recordingState.value is RecordingState.Recording

    fun startRecording(taskId: Long, iterationNumber: Int) {
        _recordingState.value = RecordingState.Recording(taskId, iterationNumber, 0)
        _eventCount.value = 0
        db.startRecording(taskId, iterationNumber)
    }

    fun stopRecording() {
        val current = _recordingState.value
        val taskId = when (current) {
            is RecordingState.Recording -> current.taskId
            else -> return
        }
        val count = _eventCount.value
        db.stopRecording(taskId, count)
        _recordingState.value = RecordingState.Completed
        _eventCount.value = 0
    }

    fun serviceConnected() {
        val current = _recordingState.value
        if (current is RecordingState.Recording) {
            _recordingState.value = RecordingState.Interrupted(current.taskId, _eventCount.value)
        }
    }

    fun serviceInterrupted() {
        val current = _recordingState.value
        if (current is RecordingState.Recording) {
            _recordingState.value = RecordingState.Interrupted(current.taskId, _eventCount.value)
            db.interruptRecording(current.taskId)
        }
    }

    fun addEvent(event: EventEntity) {
        val masked = PrivacyFilter.maskEventIfNeeded(event)
        val current = _recordingState.value
        if (current !is RecordingState.Recording) return

        db.insertEvent(current.taskId, masked)
        _eventCount.update { it + 1 }
        _recordingState.update {
            if (it is RecordingState.Recording) {
                RecordingState.Recording(it.taskId, it.iterationNumber, _eventCount.value)
            } else it
        }
    }

    fun getEventCount(): Int = _eventCount.value

    fun getAllRecordings() = db.getAllRecordings()

    fun getEventsForRecording(recordingId: Long) = db.getEventsForRecording(recordingId)
}
