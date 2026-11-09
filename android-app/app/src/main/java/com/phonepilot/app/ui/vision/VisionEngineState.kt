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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.phonepilot.app.vision.label

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
                _uiState.value = _uiState.value.copy(isRunning = running)
            }
        }

        // Observe visual context flow for automatic UI updates
        scope.launch {
            visionEngine.visualContextManager.currentContextFlow.collect { ctx ->
                _uiState.value = _uiState.value.copy(
                    currentState = ctx.currentState,
                    recentChanges = ctx.recentChanges,
                )
            }
        }

        // Observe vision repository
        scope.launch {
            visionEngine.visionRepository.lastError.collect { error ->
                if (error != null) {
                    _uiState.value = _uiState.value.copy(error = error)
                }
            }
        }
    }

    /**
     * Start vision engine with accessibility service.
     */
    fun start(service: android.accessibilityservice.AccessibilityService) {
        visionEngine.initialize(service)
        _uiState.value = _uiState.value.copy(isRunning = true, message = "محرك الرؤية يعمل")
    }

    /**
     * Stop vision engine.
     */
    fun stop() {
        visionEngine.shutdown()
        _uiState.value = _uiState.value.copy(isRunning = false, message = "تم إيقاف محرك الرؤية")
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
            _uiState.value = _uiState.value.copy(isAnalyzing = true, error = null)
            try {
                val result = visionEngine.analyzeScreen(
                    rootNode = rootNode,
                    packageName = packageName,
                    activityName = activityName,
                    sendToAi = sendToAi,
                    aiPrompt = aiPrompt,
                )
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    lastAnalysis = result,
                    lastChange = result.change,
                    message = if (result.success) "تم التحليل بنجاح" else "فشل التحليل",
                    error = result.errorMessage,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isAnalyzing = false, error = e.message)
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
            _uiState.value = _uiState.value.copy(isAnalyzing = true, error = null)
            try {
                val result = visionEngine.analyzeAccessibilityOnly(
                    rootNode = rootNode,
                    packageName = packageName,
                    activityName = activityName,
                )
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    lastAnalysis = result,
                    lastChange = result.change,
                    message = if (result.success) "تم التحليل بنجاح" else "فشل التحليل",
                    error = result.errorMessage,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isAnalyzing = false, error = e.message)
            }
        }
    }

    /**
     * Ask a question about the current screen.
     */
    fun askAboutScreen(question: String) {
        scope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzing = true, error = null)
            try {
                val result = visionEngine.askAboutScreen(question)
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    lastAnalysis = result,
                    message = if (result.success) "تم الرد" else "فشل الرد",
                    error = result.errorMessage,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isAnalyzing = false, error = e.message)
            }
        }
    }

    /**
     * Toggle OCR overlay display.
     */
    fun toggleOcrOverlay() {
        _uiState.value = _uiState.value.copy(showOcrOverlay = !_uiState.value.showOcrOverlay)
    }

    /**
     * Toggle accessibility overlay display.
     */
    fun toggleAccessibilityOverlay() {
        _uiState.value = _uiState.value.copy(showAccessibilityOverlay = !_uiState.value.showAccessibilityOverlay)
    }

    /**
     * Toggle fused overlay display.
     */
    fun toggleFusedOverlay() {
        _uiState.value = _uiState.value.copy(showFusedOverlay = !_uiState.value.showFusedOverlay)
    }

    /**
     * Update vision configuration.
     */
    fun updateConfig(newConfig: VisionConfig) {
        _uiState.value = _uiState.value.copy(config = newConfig)
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Clear analysis history.
     */
    suspend fun clearHistory() {
        visionEngine.clearHistory()
        _uiState.value = _uiState.value.copy(lastAnalysis = null, lastChange = null, recentChanges = emptyList())
    }

    fun shutdown() {
        scope.cancel()
    }
}
