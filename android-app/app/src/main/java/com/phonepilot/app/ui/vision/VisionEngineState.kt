package com.phonepilot.app.ui.vision

import android.content.Context
import com.phonepilot.app.data.ai.AiProviderRepository
import com.phonepilot.app.vision.ScreenChange
import com.phonepilot.app.vision.ScreenState
import com.phonepilot.app.vision.VisionAnalysisResult
import com.phonepilot.app.vision.VisionConfig
import com.phonepilot.app.vision.VisionEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MutableStateFlow
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * UI State for Vision Engine screen.
 */
data class VisionEngineUiState(
    val isRunning: Boolean = false,
    val isAnalyzing: Boolean = false,
    val currentState: ScreenState? = null,
    val lastChange: ScreenChange? = null,
    val lastAnalysis: VisionAnalysisResult? = null,
    val recentChanges: List<ScreenChange> = emptyList(),
    val message: String? = null,
    val error: String? = null,
    val config: VisionConfig = VisionConfig(),
    val showOcrOverlay: Boolean = false,
    val showAccessibilityOverlay: Boolean = false,
    val showFusedOverlay: Boolean = true,
) {
    val hasState: Boolean get() = currentState != null
    val elementCount: Int get() = currentState?.elements?.size ?: 0
    val interactiveCount: Int get() = currentState?.interactiveElements?.size ?: 0
    val hasDialog: Boolean get() = currentState?.hasDialog == true
    val hasKeyboard: Boolean get() = currentState?.hasKeyboard == true
    val confidence: Float get() = currentState?.confidence ?: 0f
}

/**
 * State holder for Vision Engine UI.
 */
class VisionEngineStateHolder(
    private val context: Context,
    private val visionEngine: VisionEngine,
) {

    private val scope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    private val _uiState = MutableStateFlow(VisionEngineUiState())
    val uiState: StateFlow<VisionEngineUiState> = _uiState.asStateFlow()

    init {
        // Observe vision engine state
        scope.launch {
            visionEngine.isRunning.collect { running ->
                _uiState.update { it.copy(isRunning = running) }
            }
        }

        // Observe visual context
        scope.launch {
            visionEngine.visualContextManager.currentContext.collect { ctx =>
                _uiState.update { current ->
                    current.copy(
                        currentState = ctx.currentState,
                        recentChanges = ctx.recentChanges,
                    )
                }
            }
        }

        // Observe vision repository
        scope.launch {
            visionEngine.visionRepository.lastError.collect { error ->
                if (error != null) {
                    _uiState.update { it.copy(error = error) }
                }
            }
        }
    }

    /**
     * Start vision engine with accessibility service.
     */
    fun start(service: android.accessibilityservice.AccessibilityService) {
        visionEngine.initialize(service)
        _uiState.update { it.copy(isRunning = true, message = "محرك الرؤية يعمل") }
    }

    /**
     * Stop vision engine.
     */
    fun stop() {
        visionEngine.shutdown()
        _uiState.update { it.copy(isRunning = false, message = "تم إيقاف محرك الرؤية") }
    }

    /**
     * Perform screen analysis.
     */
    fun analyzeScreen(
        rootNode: android.view.accessibility.AccessibilityNodeInfo?,
        packageName: String? = null,
        activityName: String? = null,
        sendToAi: Boolean = false,
        aiPrompt: String? = null,
    ) {
        scope.launch {
            _uiState.update { it.copy(isAnalyzing = true, error = null) }
            try {
                val result = visionEngine.analyzeScreen(
                    rootNode = rootNode,
                    packageName = packageName,
                    activityName = activityName,
                    sendToAi = sendToAi,
                    aiPrompt = aiPrompt,
                )
                _uiState.update { current ->
                    current.copy(
                        isAnalyzing = false,
                        lastAnalysis = result,
                        lastChange = result.change,
                        message = if (result.success) "تم التحليل بنجاح" else "فشل التحليل",
                        error = result.errorMessage,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isAnalyzing = false, error = e.message) }
            }
        }
    }

    /**
     * Analyze using only accessibility (no OCR).
     */
    fun analyzeAccessibilityOnly(
        rootNode: android.view.accessibility.AccessibilityNodeInfo?,
        packageName: String? = null,
        activityName: String? = null,
    ) {
        scope.launch {
            _uiState.update { it.copy(isAnalyzing = true, error = null) }
            try {
                val result = visionEngine.analyzeAccessibilityOnly(
                    rootNode = rootNode,
                    packageName = packageName,
                    activityName = activityName,
                )
                _uiState.update { current ->
                    current.copy(
                        isAnalyzing = false,
                        lastAnalysis = result,
                        lastChange = result.change,
                        message = if (result.success) "تم التحليل بنجاح" else "فشل التحليل",
                        error = result.errorMessage,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isAnalyzing = false, error = e.message) }
            }
        }
    }

    /**
     * Ask a question about the current screen.
     */
    fun askAboutScreen(question: String) {
        scope.launch {
            _uiState.update { it.copy(isAnalyzing = true, error = null) }
            try {
                val result = visionEngine.askAboutScreen(question)
                _uiState.update { current ->
                    current.copy(
                        isAnalyzing = false,
                        lastAnalysis = result,
                        message = if (result.success) "تم الرد" else "فشل الرد",
                        error = result.errorMessage,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isAnalyzing = false, error = e.message) }
            }
        }
    }

    /**
     * Toggle OCR overlay display.
     */
    fun toggleOcrOverlay() {
        _uiState.update { it.copy(showOcrOverlay = !it.showOcrOverlay) }
    }

    /**
     * Toggle accessibility overlay display.
     */
    fun toggleAccessibilityOverlay() {
        _uiState.update { it.copy(showAccessibilityOverlay = !it.showAccessibilityOverlay) }
    }

    /**
     * Toggle fused overlay display.
     */
    fun toggleFusedOverlay() {
        _uiState.update { it.copy(showFusedOverlay = !it.showFusedOverlay) }
    }

    /**
     * Update vision configuration.
     */
    fun updateConfig(newConfig: VisionConfig) {
        _uiState.update { it.copy(config = newConfig) }
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Clear analysis history.
     */
    fun clearHistory() {
        visionEngine.clearHistory()
        _uiState.update { it.copy(lastAnalysis = null, lastChange = null, recentChanges = emptyList()) }
    }

    fun shutdown() {
        scope.cancel()
    }
}

/**
 * Extension for SemanticRole label.
 */
private fun com.phonepilot.app.vision.SemanticRole.label(): String = when (this) {
    com.phonepilot.app.vision.SemanticRole.BUTTON -> "زر"
    com.phonepilot.app.vision.SemanticRole.TEXT_FIELD -> "حقل نص"
    com.phonepilot.app.vision.SemanticRole.CHECKBOX -> "مربع اختيار"
    com.phonepilot.app.vision.SemanticRole.RADIO_BUTTON -> "زر راديو"
    com.phonepilot.app.vision.SemanticRole.SWITCH -> "مفتاح"
    com.phonepilot.app.vision.SemanticRole.SLIDER -> "شريط تمرير"
    com.phonepilot.app.vision.SemanticRole.TAB -> "تبويب"
    com.phonepilot.app.vision.SemanticRole.MENU_ITEM -> "عنصر قائمة"
    com.phonepilot.app.vision.SemanticRole.LIST_ITEM -> "عنصر قائمة"
    com.phonepilot.app.vision.SemanticRole.GRID_ITEM -> "عنصر شبكة"
    com.phonepilot.app.vision.SemanticRole.IMAGE -> "صورة"
    com.phonepilot.app.vision.SemanticRole.TEXT -> "نص"
    com.phonepilot.app.vision.SemanticRole.LABEL -> "تسمية"
    com.phonepilot.app.vision.SemanticRole.HEADER -> "رأس"
    com.phonepilot.app.vision.SemanticRole.FOOTER -> "تذييل"
    com.phonepilot.app.vision.SemanticRole.DIALOG -> "حوار"
    com.phonepilot.app.vision.SemanticRole.TOAST -> "تنبيه"
    com.phonepilot.app.vision.SemanticRole.SNACKBAR -> "سناكبار"
    com.phonepilot.app.vision.SemanticRole.PROGRESS_BAR -> "شريط تقدم"
    com.phonepilot.app.vision.SemanticRole.SEEK_BAR -> "شريط بحث"
    com.phonepilot.app.vision.SemanticRole.SPINNER -> "محمل"
    com.phonepilot.app.vision.SemanticRole.PICKER -> "منتقي"
    com.phonepilot.app.vision.SemanticRole.NAVIGATION_DRAWER -> "درج تنقل"
    com.phonepilot.app.vision.SemanticRole.APP_BAR -> "شريط تطبيق"
    com.phonepilot.app.vision.SemanticRole.TAB_BAR -> "شريط تبويبات"
    com.phonepilot.app.vision.SemanticRole.BOTTOM_SHEET -> "ورقة سفلية"
    com.phonepilot.app.vision.SemanticRole.MODAL -> "مودال"
    com.phonepilot.app.vision.SemanticRole.POPOVER -> "بوبओفر"
    com.phonepilot.app.vision.SemanticRole.TOOLTIP -> "تلميح"
    com.phonepilot.app.vision.SemanticRole.SCROLL_VIEW -> "عرض تمرير"
    com.phonepilot.app.vision.SemanticRole.RECYCLER_VIEW -> "ريسيكلر فيو"
    com.phonepilot.app.vision.SemanticRole.LIST_VIEW -> "ليست فيو"
    com.phonepilot.app.vision.SemanticRole.GRID_VIEW -> "غريد فيو"
    com.phonepilot.app.vision.SemanticRole.WEB_VIEW -> "ويب فيو"
    com.phonepilot.app.vision.SemanticRole.VIDEO_VIEW -> "فيديو فيو"
    com.phonepilot.app.vision.SemanticRole.MAP_VIEW -> "خريطة"
    com.phonepilot.app.vision.SemanticRole.AD_VIEW -> "إعلان"
    com.phonepilot.app.vision.SemanticRole.UNKNOWN_CONTAINER -> "حاوية"
    com.phonepilot.app.vision.SemanticRole.UNKNOWN -> "غير معروف"
}
