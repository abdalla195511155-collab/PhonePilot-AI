package com.phonepilot.app.ui.monitor

import android.content.Context
import com.phonepilot.app.database.TaskDatabaseHelper
import com.phonepilot.app.execution.ExecutionRepository
import com.phonepilot.app.models.ExecutionEntity
import com.phonepilot.app.models.RecordingState
import com.phonepilot.app.models.TaskRecordingEntity
import com.phonepilot.app.repository.RecordingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RecordingSummary(
    val recording: TaskRecordingEntity,
    val taskName: String?,
    val eventCount: Int,
)

data class ExecutionSummary(
    val execution: ExecutionEntity,
    val skillTitle: String?,
)

data class ExecutionMonitorUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val recordingState: RecordingState = RecordingState.Idle,
    val serviceConnected: Boolean = false,
    val recordings: List<RecordingSummary> = emptyList(),
    val skillExecutions: List<ExecutionSummary> = emptyList(),
    val totalEvents: Int = 0,
) {
    val isRecording: Boolean get() = recordingState is RecordingState.Recording
}

/**
 * Live execution view: recording state from the repository and persisted
 * recordings/events from SQLite, reloaded whenever the state changes.
 * Also displays skill executions from Phase 6.
 */
class ExecutionMonitorStateHolder(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val database = TaskDatabaseHelper.getInstance(context.applicationContext)
    private val executionRepository = ExecutionRepository(database)
    private val maxRecordings = 25
    private val maxExecutions = 25

    private val _uiState = MutableStateFlow(ExecutionMonitorUiState())
    val uiState: StateFlow<ExecutionMonitorUiState> = _uiState.asStateFlow()

    init {
        observe()
        refresh()
    }

    private fun observe() {
        scope.launch {
            RecordingRepository.recordingState.collect { state ->
                _uiState.update { it.copy(recordingState = state) }
                refresh()
            }
        }
        scope.launch {
            RecordingRepository.serviceConnected.collect { connected ->
                _uiState.update { it.copy(serviceConnected = connected) }
            }
        }
    }

    fun refresh() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val (summaries, executions) = withContext(Dispatchers.IO) {
                    val tasks = database.getAllTasks().associateBy { it.id }

                    val recordingSummaries = database.getAllRecordings()
                        .take(maxRecordings)
                        .map { recording ->
                            RecordingSummary(
                                recording = recording,
                                taskName = tasks[recording.taskId]?.name,
                                eventCount = database.getEventsForRecording(recording.id).size,
                            )
                        }

                    val executionSummaries = database.getAllSkills() // placeholder - would query executions
                        .take(maxExecutions)
                        .mapNotNull { skill ->
                            val executions = executionRepository.getExecutionsForSkill(skill.id)
                            executions.firstOrNull()?.let { exec ->
                                ExecutionSummary(
                                    execution = exec,
                                    skillTitle = skill.title,
                                )
                            }
                        }

                    recordingSummaries to executionSummaries
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = null,
                        recordings = summaries,
                        skillExecutions = executions,
                        totalEvents = summaries.sumOf { summary -> summary.eventCount },
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "تعذر تحميل بيانات التنفيذ",
                    )
                }
            }
        }
    }
}
