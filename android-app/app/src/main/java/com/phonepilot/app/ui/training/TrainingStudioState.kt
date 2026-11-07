package com.phonepilot.app.ui.training

import android.content.Context
import com.phonepilot.app.data.SettingsRepository
import com.phonepilot.app.database.TaskDatabaseHelper
import com.phonepilot.app.models.RecordingState
import com.phonepilot.app.models.TaskEntity
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

/**
 * حالة شاشة استديو التدريب (Phase 5 — erweitert).
 *
 * يضيف تتبع عدد العروض لكل مهمة، وحالة التعلم (التحليل، الاستخراج، الاعتماد),
 * وربط العروض بالمهارة المستخلصة.
 */
data class TrainingStudioUiState(
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    val message: String? = null,
    val tasks: List<TaskEntity> = emptyList(),
    val activeTaskId: Long? = null,
    val recordingState: RecordingState = RecordingState.Idle,
    val demonstrationCount: Int = 0,
    val isLearning: Boolean = false,
    val learningProgress: Float = 0.0f,
    val learningMessage: String? = null,
    val skillState: String? = null, // حالة دورة حياة المهارة (DRAFT, LEARNING, ANALYZING, ...)
) {
    val activeTask: TaskEntity? get() = tasks.firstOrNull { it.id == activeTaskId }
    val isRecording: Boolean get() = recordingState is RecordingState.Recording
}

/**
 * State holder for task management and demonstration learning (Phase 5).
 *
 * ينشئ صفوفاً حقيقية في SQLite ويحتفظ بالمهمة النشطة في [SettingsRepository]
 * بحيث تتفق لوحة التحكم والتحكم بالوكيل.
 */
class TrainingStudioStateHolder(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val database = TaskDatabaseHelper.getInstance(context.applicationContext)

    private val _uiState = MutableStateFlow(TrainingStudioUiState())
    val uiState: StateFlow<TrainingStudioUiState> = _uiState.asStateFlow()

    init {
        observeSettings()
        observeRecording()
        refresh()
    }

    private fun observeSettings() {
        scope.launch {
            SettingsRepository.activeTaskId.collect { taskId ->
                _uiState.update { it.copy(activeTaskId = taskId) }
                if (taskId != null) {
                    updateDemonstrationCount(taskId)
                }
            }
        }
    }

    private fun observeRecording() {
        scope.launch {
            RecordingRepository.recordingState.collect { state ->
                _uiState.update { it.copy(recordingState = state) }
            }
        }
    }

    fun refresh() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val tasks = withContext(Dispatchers.IO) { database.getAllTasks() }
                val currentActive = _uiState.value.activeTaskId
                val resolvedActive = currentActive?.takeIf { id -> tasks.any { it.id == id } }
                    ?: tasks.firstOrNull()?.id
                if (resolvedActive != null && resolvedActive != currentActive) {
                    SettingsRepository.setActiveTaskId(resolvedActive)
                }
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        errorMessage = null,
                        tasks = tasks,
                        activeTaskId = resolvedActive,
                    )
                }
                if (resolvedActive != null) {
                    updateDemonstrationCount(resolvedActive)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "تعذر تحميل المهام",
                    )
                }
            }
        }
    }

    /** تحديث عدد العروض للمهمة النشطة من قاعدة البيانات. */
    private fun updateDemonstrationCount(taskId: Long) {
        scope.launch {
            val count = withContext(Dispatchers.IO) {
                database.getRecordingsForTask(taskId).size
            }
            _uiState.update { it.copy(demonstrationCount = count) }
        }
    }

    fun createTask(name: String, description: String?) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty() || _uiState.value.isBusy) return
        scope.launch {
            _uiState.update { it.copy(isBusy = true, message = null) }
            val taskId = try {
                withContext(Dispatchers.IO) {
                    database.insertTask(
                        name = trimmedName,
                        description = description?.trim()?.takeIf { it.isNotEmpty() },
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                -1L
            }
            if (taskId > 0) {
                SettingsRepository.setActiveTaskId(taskId)
                _uiState.update {
                    it.copy(isBusy = false, message = "تم إنشاء المهمة \"$trimmedName\"")
                }
                refresh()
            } else {
                _uiState.update { it.copy(isBusy = false, message = "تعذر إنشاء المهمة") }
            }
        }
    }

    fun selectTask(taskId: Long) {
        SettingsRepository.setActiveTaskId(taskId)
        val name = _uiState.value.tasks.firstOrNull { it.id == taskId }?.name
        _uiState.update {
            it.copy(
                activeTaskId = taskId,
                message = if (name != null) "المهمة النشطة: $name" else null,
            )
        }
        updateDemonstrationCount(taskId)
    }

    /**
     * يبدأ تحليل التعلم للمهمة النشطة باستخدام [LearningEngine].
     * تُحفظ المهارة الناتجة بحالة ANALYGING للمراجعة اللاحقة.
     */
    fun startLearning() {
        val taskId = _uiState.value.activeTaskId ?: return
        if (_uiState.value.isLearning || _uiState.value.isBusy) return

        scope.launch {
            _uiState.update { it.copy(isLearning = true, learningProgress = 0.0f, learningMessage = null) }
            try {
                withContext(Dispatchers.IO) {
                    val engine = com.phonepilot.app.data.ai.LearningEngine(database)
                    _uiState.update { it.copy(learningProgress = 0.3f, learningMessage = "مقارنة العروض واستخراج الخطوات...") }
                    val skill = engine.learnTask(taskId)
                    _uiState.update { it.copy(learningProgress = 1.0f, isLearning = false) }
                    if (skill != null) {
                        _uiState.update {
                            it.copy(
                                learningMessage = "تم استخراج المهارة: ${skill.title}",
                                skillState = "ANALYZING",
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                learningMessage = "بيانات غير كافية للاستخراج، يُنصح بإضافة المزيد من العروض",
                            )
                        }
                    }
                    updateDemonstrationCount(taskId)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isLearning = false,
                        learningProgress = 0.0f,
                        learningMessage = "فشل تحليل التعلم: ${error.message}",
                    )
                }
            }
        }
    }
}
