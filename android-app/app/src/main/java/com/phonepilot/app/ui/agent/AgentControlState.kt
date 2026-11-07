package com.phonepilot.app.ui.agent

import android.content.Context
import com.phonepilot.app.data.SettingsRepository
import com.phonepilot.app.database.TaskDatabaseHelper
import com.phonepilot.app.models.RecordingState
import com.phonepilot.app.models.TaskEntity
import com.phonepilot.app.recording.PhonePilotAccessibilityService
import com.phonepilot.app.repository.RecordingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AgentControlUiState(
    val isBusy: Boolean = false,
    val message: String? = null,
    val recordingState: RecordingState = RecordingState.Idle,
    val acceptedCount: Int = 0,
    val savedCount: Int = 0,
    val failedCount: Int = 0,
    val serviceEnabled: Boolean = false,
    val serviceConnected: Boolean = false,
    val hasPendingRecovery: Boolean = false,
    val hasInterruptFailure: Boolean = false,
    val tasks: List<TaskEntity> = emptyList(),
    val activeTaskId: Long? = null,
) {
    val activeTask: TaskEntity? get() = tasks.firstOrNull { it.id == activeTaskId }
    val isRecording: Boolean get() = recordingState is RecordingState.Recording
}

/**
 * Binds the Agent Control screen to real recording operations.
 * No AI agent exists yet, so the screen only drives the recorder and reports
 * the true service/recording state.
 */
class AgentControlStateHolder(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val database = TaskDatabaseHelper.getInstance(context.applicationContext)
    private val appContext = context.applicationContext

    private val _uiState = MutableStateFlow(AgentControlUiState())
    val uiState: StateFlow<AgentControlUiState> = _uiState.asStateFlow()

    init {
        observeRecording()
        observeActiveTask()
        refresh()
    }

    private fun observeRecording() {
        scope.launch {
            combine(
                RecordingRepository.recordingState,
                RecordingRepository.acceptedCount,
                RecordingRepository.verifiedPersistedCount,
                RecordingRepository.failedCount,
                RecordingRepository.serviceConnected,
            ) { state, accepted, saved, failed, connected ->
                Snapshot(state, accepted, saved, failed, connected)
            }.collect { snapshot ->
                _uiState.update {
                    it.copy(
                        recordingState = snapshot.state,
                        acceptedCount = snapshot.accepted,
                        savedCount = snapshot.saved,
                        failedCount = snapshot.failed,
                        serviceConnected = snapshot.connected,
                        hasPendingRecovery = RecordingRepository.hasPendingRecovery,
                        hasInterruptFailure = RecordingRepository.hasInterruptFailure,
                    )
                }
            }
        }
    }

    private fun observeActiveTask() {
        scope.launch {
            SettingsRepository.activeTaskId.collect { taskId ->
                _uiState.update { it.copy(activeTaskId = taskId) }
            }
        }
    }

    fun refresh() {
        scope.launch {
            try {
                val tasks = withContext(Dispatchers.IO) { database.getAllTasks() }
                val serviceEnabled = PhonePilotAccessibilityService.isServiceEnabled(appContext)
                val currentActive = _uiState.value.activeTaskId
                val resolvedActive = currentActive?.takeIf { id -> tasks.any { it.id == id } }
                    ?: tasks.firstOrNull()?.id
                if (resolvedActive != null && resolvedActive != currentActive) {
                    SettingsRepository.setActiveTaskId(resolvedActive)
                }
                _uiState.update { current ->
                    current.copy(
                        tasks = tasks,
                        activeTaskId = resolvedActive,
                        serviceEnabled = serviceEnabled,
                        hasPendingRecovery = RecordingRepository.hasPendingRecovery,
                        hasInterruptFailure = RecordingRepository.hasInterruptFailure,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(message = error.message ?: "تعذر تحميل بيانات الوكيل")
                }
            }
        }
    }

    fun startRecording() {
        if (_uiState.value.isBusy) return
        if (!_uiState.value.serviceEnabled) {
            _uiState.update {
                it.copy(message = "فعّل خدمة الوصولية أولاً — لن تُسجَّل أي أحداث بدونها")
            }
            return
        }
        val task = _uiState.value.activeTask
        if (task == null) {
            _uiState.update { it.copy(message = "اختر مهمة من استديو التدريب أولاً") }
            return
        }
        scope.launch {
            _uiState.update { it.copy(isBusy = true, message = null) }
            try {
                val iteration = withContext(Dispatchers.IO) {
                    database.getAllRecordings().count { it.taskId == task.id } + 1
                }
                val started = withContext(Dispatchers.IO) {
                    RecordingRepository.startRecording(task.id, iteration)
                }
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        message = if (started) {
                            "بدأ التسجيل للمهمة ${task.name}"
                        } else {
                            "تعذر بدء التسجيل — توجد جلسة غير مكتملة"
                        },
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(isBusy = false, message = error.message ?: "تعذر بدء التسجيل")
                }
            }
        }
    }

    fun stopRecording() {
        if (_uiState.value.isBusy) return
        scope.launch {
            _uiState.update { it.copy(isBusy = true, message = null) }
            try {
                val stopped = withContext(NonCancellable + Dispatchers.IO) {
                    RecordingRepository.stopRecording()
                }
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        message = if (stopped) "تم إنهاء التسجيل وحفظ الأحداث" else "تعذر إنهاء التسجيل",
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(isBusy = false, message = error.message ?: "تعذر إنهاء التسجيل")
                }
            }
        }
    }

    /** Retries the DB finalization of an interrupted session (real recovery path). */
    fun recoverSession() {
        if (_uiState.value.isBusy) return
        val recovered = RecordingRepository.recoverSession()
        _uiState.update {
            it.copy(
                hasPendingRecovery = RecordingRepository.hasPendingRecovery,
                hasInterruptFailure = RecordingRepository.hasInterruptFailure,
                message = if (recovered) "تمت استعادة الجلسة" else "تعذرت الاستعادة — راجع السجلات",
            )
        }
    }

    /** Ends a failed session without losing the persisted rows. */
    fun abandonSession() {
        if (_uiState.value.isBusy) return
        val abandoned = RecordingRepository.abandonSession()
        _uiState.update {
            it.copy(
                hasPendingRecovery = RecordingRepository.hasPendingRecovery,
                hasInterruptFailure = RecordingRepository.hasInterruptFailure,
                message = if (abandoned) "تم إنهاء الجلسة" else "تعذر إنهاء الجلسة",
            )
        }
    }

    private data class Snapshot(
        val state: RecordingState,
        val accepted: Int,
        val saved: Int,
        val failed: Int,
        val connected: Boolean,
    )
}
