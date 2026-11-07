package com.phonepilot.app.vision

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Main Vision Engine facade - coordinates all vision components.
 * Provides a simple interface for the rest of the app to use vision capabilities.
 */
class VisionEngine(
    private val context: Context,
    private val config: VisionConfig = VisionConfig(),
) {

    private val TAG = "VisionEngine"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Core components
    private val screenCaptureManager = ScreenCaptureManager(context, config)
    private val accessibilityAnalyzer = AccessibilityTreeAnalyzer(context, config)
    private val ocrEngine = OcrEngineFactory.getEngine(context, config)
    private val elementRecognizer = UiElementRecognizer(config)
    private val fusionEngine = VisionFusionEngine(config, elementRecognizer)
    private val screenStateDetector = ScreenStateDetector(context, config, accessibilityAnalyzer, elementRecognizer)
    private val screenChangeDetector = ScreenChangeDetector(config, elementRecognizer)
    val visualContextManager = VisualContextManager(context, config, screenStateDetector, screenChangeDetector)
    val visionRepository = VisionRepository(context, config, visualContextManager)

    // State
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    // Accessibility service reference
    private var accessibilityService: AccessibilityService? = null

    /**
     * Initialize the vision engine with an accessibility service.
     */
    fun initialize(service: AccessibilityService) {
        accessibilityService = service
        screenCaptureManager.setAccessibilityService(service)
        aiProviderRepository.init(context)
        _isRunning.value = true
        Log.d(TAG, "VisionEngine initialized")
    }

    /**
     * Initialize MediaProjection for screen capture (requires user permission).
     * Call this from Activity.onActivityResult() or Activity Result callback.
     */
    fun initializeMediaProjection(resultCode: Int, data: android.content.Intent?): Boolean {
        return screenCaptureManager.initMediaProjection(resultCode, data)
    }

    /**
     * Get MediaProjection permission intent.
     * Must be started via Activity.startActivityForResult() or Activity Result API.
     */
    fun createMediaProjectionIntent(): android.content.Intent? {
        return screenCaptureManager.createMediaProjectionPermissionIntent()
    }

    /**
     * Request MediaProjection permission and wait for user response (suspend function).
     * The Activity must call initializeMediaProjection() with the result.
     */
    suspend fun requestMediaProjectionPermission(activity: Activity): ScreenCaptureResult {
        return screenCaptureManager.requestMediaProjectionPermission(activity)
    }

    /**
     * Perform a complete screen analysis cycle.
     */
    suspend fun analyzeScreen(
        rootNode: android.view.accessibility.AccessibilityNodeInfo?,
        packageName: String? = null,
        activityName: String? = null,
        sendToAi: Boolean = false,
        aiPrompt: String? = null,
    ): VisionAnalysisResult {
        _lastError.value = null

        return try {
            // Capture screen
            val captureResult = screenCaptureManager.capture()
            
            var ocrResult: OcrResult? = null
            var captureBitmap: android.graphics.Bitmap? = null
            
            when (captureResult) {
                is ScreenCaptureResult.Success -> {
                    captureBitmap = captureResult.bitmap
                    // Run OCR if capture successful and OCR enabled
                    if (config.enableOcr && captureBitmap != null) {
                        ocrResult = ocrEngine.process(captureBitmap!!)
                        if (!ocrResult.success) {
                            Log.w(TAG, "OCR failed: ${ocrResult.errorMessage}")
                            ocrResult = null
                        }
                    }
                }
                is ScreenCaptureResult.Failure -> {
                    Log.w(TAG, "Screen capture failed: ${captureResult.error} - ${captureResult.errorMessage}")
                    // For secure windows, we can still analyze accessibility tree
                    if (captureResult.error == ScreenCaptureResult.CaptureError.SECURE_WINDOW) {
                        Log.i(TAG, "Secure window detected, proceeding with accessibility-only analysis")
                    }
                }
            }

            // Analyze screen state (with or without OCR)
            val result = visionRepository.analyzeScreen(
                rootNode = rootNode,
                ocrResult = ocrResult,
                packageName = packageName,
                activityName = activityName,
                sendToAi = sendToAi,
                aiPrompt = aiPrompt,
            )

            // Recycle capture bitmap if we have one
            captureBitmap?.recycle()

            if (!result.success) {
                _lastError.value = result.errorMessage
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "Screen analysis failed", e)
            _lastError.value = e.message
            VisionAnalysisResult(
                screenState = null,
                change = null,
                visionSummary = "",
                aiResponse = null,
                success = false,
                errorMessage = e.message,
            )
        }
    }

    /**
     * Quick analysis using only accessibility tree (no screen capture/OCR).
     */
    suspend fun analyzeAccessibilityOnly(
        rootNode: android.view.accessibility.AccessibilityNodeInfo?,
        packageName: String? = null,
        activityName: String? = null,
    ): VisionAnalysisResult {
        return visionRepository.analyzeScreen(
            rootNode = rootNode,
            ocrResult = null,
            packageName = packageName,
            activityName = activityName,
            sendToAi = false,
        )
    }

    /**
     * Ask a question about the current screen context.
     */
    suspend fun askAboutScreen(question: String): VisionAnalysisResult {
        return visionRepository.askAboutScreen(question)
    }

    /**
     * Find elements by text query.
     */
    fun findElements(query: String): List<UiElement> {
        return visualContextManager.findElements(query)
    }

    /**
     * Find elements by semantic role.
     */
    fun findElementsByRole(role: SemanticRole): List<UiElement> {
        return visualContextManager.findElementsByRole(role)
    }

    /**
     * Get interactive elements.
     */
    fun getInteractiveElements(): List<UiElement> {
        return visualContextManager.findInteractiveElements()
    }

    /**
     * Get element at coordinates.
     */
    fun getElementAt(x: Int, y: Int): UiElement? {
        return visualContextManager.getElementAt(x, y)
    }

    /**
     * Get current screen state.
     */
    fun getCurrentState(): ScreenState? {
        return visualContextManager.currentContext.currentState
    }

    /**
     * Get recent screen changes.
     */
    fun getRecentChanges(): List<ScreenChange> {
        return visualContextManager.currentContext.recentChanges
    }

    /**
     * Check if current model supports vision.
     */
    fun isVisionCapable(): Boolean {
        return visionRepository.isCurrentModelVisionCapable()
    }

    /**
     * Get vision-capable models.
     */
    fun getVisionModels(): List<com.phonepilot.app.data.ai.AiModelSpec> {
        return visionRepository.getVisionCapableModels()
    }

    /**
     * Get context summary for debugging.
     */
    fun getContextSummary(): String {
        return visualContextManager.getContextSummary()
    }

    /**
     * Clear context history.
     */
    fun clearHistory() {
        visualContextManager.clearHistory()
    }

    /**
     * Shutdown the vision engine and all components.
     */
    fun shutdown() {
        screenCaptureManager.shutdown()
        accessibilityAnalyzer.shutdown()
        ocrEngine.release()
        elementRecognizer.shutdown()
        fusionEngine.shutdown()
        screenStateDetector.shutdown()
        visualContextManager.shutdown()
        visionRepository.shutdown()
        scope.cancel()
        _isRunning.value = false
        Log.d(TAG, "VisionEngine shutdown")
    }

    // Expose components for advanced usage
    val captureManager: ScreenCaptureManager get() = screenCaptureManager
    val analyzer: AccessibilityTreeAnalyzer get() = accessibilityAnalyzer
    val ocr: OcrEngine get() = ocrEngine
    val recognizer: UiElementRecognizer get() = elementRecognizer
    val fusion: VisionFusionEngine get() = fusionEngine
    val stateDetector: ScreenStateDetector get() = screenStateDetector
    val changeDetector: ScreenChangeDetector get() = screenChangeDetector
    val contextManager: VisualContextManager get() = visualContextManager
    val repository: VisionRepository get() = visionRepository
}
