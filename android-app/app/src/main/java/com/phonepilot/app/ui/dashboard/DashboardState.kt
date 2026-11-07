package com.phonepilot.app.ui.dashboard

import android.content.Context
import com.phonepilot.app.data.SettingsRepository
import com.phonepilot.app.database.TaskDatabaseHelper
import com.phonepilot.app.models.RecordingState
import com.phonepilot.app.models.TaskEntity
import com.phonepilot.app.models.TaskRecordingEntity
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

/**
 * Immutable snapshot rendered by [com.phonepilot.app.ui.screens.DashboardScreen].
 * Every field is either read from a real repository flow or from SQLite — no placeholders.
 */
data class DashboardUiState(
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    val message: String? = null,
    val recordingState: RecordingState = RecordingState.Idle,
    val acceptedCount: Int = 0,
    val savedCount: Int = 0,
    val failedCount: Int = 0,
    val serviceEnabled: Boolean = false,
    val serviceConnected: Boolean = false,
    val recordings: List<TaskRecordingEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val activeTaskId: Long? = null,
) {
    val isRecording: Boolean get() = recordingState is RecordingState.Recording
    val hasTask: Boolean get() = tasks.isNotEmpty()
    val activeTask: TaskEntity? get() = tasks.firstOrNull { it.id == activeTaskId } ?: tasks.firstOrNull()
}

/**
 * Screen-scoped state holder for the Dashboard slice.
 *
 * Data flow (unidirectional):
 *  UI intent -> [DashboardStateHolder] -> RecordingRepository / TaskDatabaseHelper
 *  SQLite + repository StateFlows -> [uiState] -> UI.
 *
 * It is a plain class on purpose: the project has no ViewModel Compose artifact,
 * and adding a dependency is not justified for a single screen.
 */
class DashboardStateHolder(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val database: TaskDatabaseHelper = TaskDatabaseHelper.getInstance(appContext)

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        observeRecordingState()
        observeActiveTask()
        refresh()
    }

    private fun observeActiveTask() {
        scope.launch {
            SettingsRepository.activeTaskId.collect { taskId ->
                _uiState.update { it.copy(activeTaskId = taskId) }
            }
        }
    }

    /** Live recording state and counters — always reflects the real repository. */
    private fun observeRecordingState() {
        scope.launch {
            combine(
                RecordingRepository.recordingState,
                RecordingRepository.acceptedCount,
                RecordingRepository.verifiedPersistedCount,
                RecordingRepository.failedCount,
                RecordingRepository.serviceConnected,
            ) { recordingState, accepted, saved, failed, connected ->
                RecordingSnapshot(recordingState, accepted, saved, failed, connected)
            }.collect { snapshot ->
                _uiState.update { current ->
                    current.copy(
                        recordingState = snapshot.recordingState,
                        acceptedCount = snapshot.accepted,
                        savedCount = snapshot.saved,
                        failedCount = snapshot.failed,
                        serviceConnected = snapshot.connected,
                    )
                }
            }
        }
    }

    /** Reloads persisted rows off the main thread and refreshes the service flag. */
    fun refresh() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    PersistedSnapshot(
                        recordings = RecordingRepository.getAllRecordings(),
                        tasks = database.getAllTasks(),
                    )
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = null,
                        recordings = snapshot.recordings,
                        tasks = snapshot.tasks,
                        serviceEnabled = PhonePilotAccessibilityService.isServiceEnabled(appContext),
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "تعذر تحميل البيانات المحلية",
                    )
                }
            }
        }
    }

    /** Creates the first real task row when the database has none. */
    fun createDefaultTask() {
        if (_uiState.value.isBusy) return
        scope.launch {
            _uiState.update { it.copy(isBusy = true, message = null) }
            val existing = try {
                withContext(Dispatchers.IO) { database.getAllTasks() }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                emptyList()
            }
            if (existing.isNotEmpty()) {
                SettingsRepository.setActiveTaskId(existing.first().id)
                _uiState.update {
                    it.copy(isBusy = false, message = "تم اختيار المهمة ${existing.first().name}")
                }
                refresh()
                return@launch
            }
            val taskId = try {
                withContext(Dispatchers.IO) {
                    database.insertTask(
                        name = "المهمة الأساسية",
                        description = "أُنشئت من لوحة التحكم",
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                -1L
            }
            if (taskId > 0) {
                SettingsRepository.setActiveTaskId(taskId)
                _uiState.update { it.copy(isBusy = false, message = "تم إنشاء المهمة الأساسية") }
                refresh()
            } else {
                _uiState.update {
                    it.copy(isBusy = false, message = "تعذر إنشاء المهمة التدريبية")
                }
            }
        }
    }

    /** Starts a real recording session for the active task. */
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
            _uiState.update { it.copy(message = "أنشئ مهمة تدريبية أولاً") }
            return
        }
        if (_uiState.value.activeTaskId != task.id) {
            SettingsRepository.setActiveTaskId(task.id)
        }
        scope.launch {
            _uiState.update { it.copy(isBusy = true, message = null) }
            try {
                val iteration = _uiState.value.recordings.count { it.taskId == task.id } + 1
                val started = withContext(Dispatchers.IO) {
                    RecordingRepository.startRecording(task.id, iteration)
                }
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        message = if (started) null else "تعذر بدء التسجيل — توجد جلسة غير مكتملة",
                    )
                }
                if (started) refresh()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(isBusy = false, message = error.message ?: "تعذر بدء التسجيل")
                }
            }
        }
    }

    /** Stops the active session: drains pending events, then marks the row COMPLETED. */
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
                        message = if (stopped) "تم إنهاء التسجيل" else "تعذر إنهاء التسجيل",
                    )
                }
                refresh()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(isBusy = false, message = error.message ?: "تعذر إنهاء التسجيل")
                }
            }
        }
    }

    private data class RecordingSnapshot(
        val recordingState: RecordingState,
        val accepted: Int,
        val saved: Int,
        val failed: Int,
        val connected: Boolean,
    )

    private data class PersistedSnapshot(
        val recordings: List<TaskRecordingEntity>,
        val tasks: List<TaskEntity>,
    )
}
