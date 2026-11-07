package com.phonepilot.app.ui.logs

import android.content.Context
import com.phonepilot.app.database.TaskDatabaseHelper
import com.phonepilot.app.models.EventEntity
import com.phonepilot.app.models.TaskRecordingEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LogRecordingSummary(
    val recording: TaskRecordingEntity,
    val taskName: String?,
    val eventCount: Int,
)

data class LogsHistoryUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val query: String = "",
    val recordings: List<LogRecordingSummary> = emptyList(),
    val expandedRecordingId: Long? = null,
    val events: List<EventEntity> = emptyList(),
    val isLoadingEvents: Boolean = false,
) {
    val filteredRecordings: List<LogRecordingSummary>
        get() {
            val value = query.trim()
            if (value.isEmpty()) return recordings
            return recordings.filter { summary ->
                summary.recording.id.toString().contains(value) ||
                    summary.recording.iterationNumber.toString() == value ||
                    summary.taskName?.contains(value, ignoreCase = true) == true
            }
        }
}

/**
 * Real log history from SQLite: recordings with their event counts plus the
 * events of the expanded row. No synthetic log lines.
 */
class LogsHistoryStateHolder(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val database = TaskDatabaseHelper.getInstance(context.applicationContext)
    private val maxRecordings = 50

    private val _uiState = MutableStateFlow(LogsHistoryUiState())
    val uiState: StateFlow<LogsHistoryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val summaries = withContext(Dispatchers.IO) {
                    val tasks = database.getAllTasks().associateBy { it.id }
                    database.getAllRecordings()
                        .take(maxRecordings)
                        .map { recording ->
                            LogRecordingSummary(
                                recording = recording,
                                taskName = tasks[recording.taskId]?.name,
                                eventCount = database.getEventsForRecording(recording.id).size,
                            )
                        }
                }
                _uiState.update { current ->
                    val expandedStillExists = current.expandedRecordingId != null &&
                        summaries.any { it.recording.id == current.expandedRecordingId }
                    current.copy(
                        isLoading = false,
                        errorMessage = null,
                        recordings = summaries,
                        expandedRecordingId = if (expandedStillExists) current.expandedRecordingId else null,
                        events = if (expandedStillExists) current.events else emptyList(),
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "تعذر تحميل السجلات",
                    )
                }
            }
        }
    }

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    /** Expands a row and loads its events; tapping again collapses it. */
    fun toggleRecording(recordingId: Long) {
        if (_uiState.value.expandedRecordingId == recordingId) {
            _uiState.update {
                it.copy(expandedRecordingId = null, events = emptyList(), isLoadingEvents = false)
            }
            return
        }
        _uiState.update {
            it.copy(expandedRecordingId = recordingId, events = emptyList(), isLoadingEvents = true)
        }
        scope.launch {
            try {
                val events = withContext(Dispatchers.IO) {
                    database.getEventsForRecording(recordingId)
                }
                _uiState.update { current ->
                    if (current.expandedRecordingId != recordingId) {
                        current
                    } else {
                        current.copy(events = events, isLoadingEvents = false)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(isLoadingEvents = false, errorMessage = error.message ?: "تعذر تحميل الأحداث")
                }
            }
        }
    }
}
