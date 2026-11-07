package com.phonepilot.app.vision

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Mutex
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

/**
 * Manages visual context across screen analyses.
 * Maintains history, handles cleanup, and provides context for decision making.
 */
class VisualContextManager(
    private val context: Context,
    private val config: VisionConfig = VisionConfig(),
    private val screenStateDetector: ScreenStateDetector,
    private val screenChangeDetector: ScreenChangeDetector,
) {

    private val TAG = "VisualContextManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateMutex = Mutex()

    // Current visual context
    private var _context = VisualContext()
    private val contextRef = AtomicReference(_context)

    // Analysis job tracking
    private var currentAnalysisJob: Job? = null

    /**
     * Get the current visual context.
     */
    val currentContext: VisualContext
        get() = contextRef.get()

    /**
     * Analyze a new screen and update context.
     * Returns the detected change from previous state.
     */
    suspend fun analyzeAndUpdate(
        rootNode: android.view.accessibility.AccessibilityNodeInfo?,
        ocrResult: OcrResult? = null,
        packageName: String? = null,
        activityName: String? = null,
    ): ScreenChange? = withContext(Dispatchers.IO) {
        // Cancel any ongoing analysis
        currentAnalysisJob?.cancel()

        currentAnalysisJob = scope.launch {
            stateMutex.withLock {
                val previousState = _context.currentState

                // Detect new screen state
                val newState = screenStateDetector.analyzeScreen(
                    rootNode,
                    ocrResult,
                    packageName,
                    activityName,
                )

                // Detect changes if we have a previous state
                val change = previousState?.let { prev ->
                    screenChangeDetector.detectChanges(prev, newState)
                } ?: ScreenChange(
                    changeType = ChangeType.NO_CHANGE,
                    currentState = newState,
                    significance = Significance.MINOR,
                )

                // Update context
                _context = _context.addChange(change)
                _context = _context.copy(
                    currentState = newState,
                    isAnalyzing = false,
                )
                contextRef.set(_context)

                // Apply privacy filtering to context
                if (config.enablePrivacyFilter) {
                    sanitizeContext()
                }

                // Enforce history limits
                enforceHistoryLimits()

                change
            }
        }

        return currentAnalysisJob?.await()
    }

    /**
     * Get the current screen state without analyzing.
     */
    fun getCurrentState(): ScreenState? = currentContext.currentState

    /**
     * Get the previous screen state.
     */
    fun getPreviousState(): ScreenState? = currentContext.previousState

    /**
     * Get recent screen changes.
     */
    fun getRecentChanges(limit: Int = 10): List<ScreenChange> {
        return currentContext.recentChanges.takeLast(limit)
    }

    /**
     * Find elements matching a query in the current state.
     */
    fun findElements(query: String, threshold: Float = 0.6f): List<UiElement> {
        val state = currentContext.currentState ?: return emptyList()
        val recognizer = UiElementRecognizer(config)
        return recognizer.findByText(state.elements, query, threshold).map { it.element }
    }

    /**
     * Find elements by semantic role.
     */
    fun findElementsByRole(role: SemanticRole): List<UiElement> {
        val state = currentContext.currentState ?: return emptyList()
        val recognizer = UiElementRecognizer(config)
        return recognizer.findByRole(state.elements, role)
    }

    /**
     * Find interactive elements in current state.
     */
    fun findInteractiveElements(): List<UiElement> {
        val state = currentContext.currentState ?: return emptyList()
        val recognizer = UiElementRecognizer(config)
        return recognizer.findInteractive(state.elements)
    }

    /**
     * Get element at specific screen coordinates.
     */
    fun getElementAt(x: Int, y: Int): UiElement? {
        val state = currentContext.currentState ?: return null
        val recognizer = UiElementRecognizer(config)
        return recognizer.getElementAt(state.elements, x, y)
    }

    /**
     * Get elements in a specific region.
     */
    fun getElementsInRegion(region: Bounds): List<UiElement> {
        val state = currentContext.currentState ?: return emptyList()
        val recognizer = UiElementRecognizer(config)
        return recognizer.findInRegion(state.elements, region)
    }

    /**
     * Check if screen has changed significantly since last analysis.
     */
    fun hasSignificantChange(): Boolean {
        return currentContext.recentChanges.lastOrNull()?.hasSignificantChange == true
    }

    /**
     * Get the last significant change.
     */
    fun getLastSignificantChange(): ScreenChange? {
        return currentContext.recentChanges.findLast { it.hasSignificantChange }
    }

    /**
     * Get screen identity for current state.
     */
    fun getCurrentScreenIdentity(): ScreenIdentity? {
        val state = currentContext.currentState
        return if (state != null) {
            screenStateDetector.detectScreenIdentity(state.elements, state.packageName, state.activityName)
        } else null
    }

    /**
     * Compare current screen with a previous screen identity.
     */
    fun compareWithIdentity(identity: ScreenIdentity): Float {
        val currentIdentity = getCurrentScreenIdentity() ?: return 0f
        return currentIdentity.similarity(identity)
    }

    /**
     * Clear context history.
     */
    fun clearHistory() {
        stateMutex.withLock {
            _context = _context.clearHistory()
            contextRef.set(_context)
        }
    }

    /**
     * Reset context completely.
     */
    fun reset() {
        stateMutex.withLock {
            _context = VisualContext()
            contextRef.set(_context)
        }
    }

    /**
     * Sanitize context for privacy - mask sensitive data.
     */
    private fun sanitizeContext() {
        val state = _context.currentState
        if (state == null) return

        val packageName = state.packageName ?: context.packageName

        // Mask sensitive text in elements
        val sanitizedElements = state.elements.map { element ->
            var sanitized = element
            if (element.text?.let { PrivacyFilter.isSensitiveText(it) } == true ||
                PrivacyFilter.isSensitivePackage(packageName)) {
                sanitized = sanitized.copy(text = "[MASKED]")
            }
            if (element.contentDescription?.let { PrivacyFilter.isSensitiveText(it) } == true ||
                PrivacyFilter.isSensitivePackage(packageName)) {
                sanitized = sanitized.copy(contentDescription = "[MASKED]")
            }
            sanitized
        }

        _context = _context.copy(
            currentState = state.copy(elements = sanitizedElements),
        )
        contextRef.set(_context)
    }

    /**
     * Enforce history size limits.
     */
    private fun enforceHistoryLimits() {
        if (_context.recentChanges.size > _context.maxHistorySize) {
            _context = _context.copy(
                recentChanges = _context.recentChanges.takeLast(_context.maxHistorySize).toList()
            )
        }
    }

    /**
     * Get context summary for debugging/logging.
     */
    fun getContextSummary(): String {
        val ctx = currentContext
        val state = ctx.currentState
        return "VisualContext(" +
            "elements=${state?.elements.size ?: 0}, " +
            "changes=${ctx.recentChanges.size}, " +
            "package=${state?.packageName}, " +
            "activity=${state?.activityName}, " +
            "confidence=${state?.confidence ?: 0f}, " +
            "hasDialog=${state?.hasDialog}, " +
            "hasKeyboard=${state?.hasKeyboard}, " +
            "lastAnalysis=${ctx.lastAnalysisTime}" +
        ")"
    }

    /**
     * Shutdown the manager.
     */
    fun shutdown() {
        currentAnalysisJob?.cancel()
        scope.cancel()
    }
}

/**
 * Extension for PrivacyFilter to check text sensitivity.
 * This is a placeholder - actual implementation should be in PrivacyFilter.
 */
private object PrivacyFilter {
    fun isSensitiveText(text: String?): Boolean {
        text ?: return false
        val sensitiveKeywords = setOf(
            "password", "كلمة مرور", "pin", "رمز", "passcode",
            "credit card", "بطاقة", "cvv", "otp", "verification"
        )
        return sensitiveKeywords.any { text.contains(it, ignoreCase = true) }
    }

    fun isSensitivePackage(packageName: String?): Boolean {
        packageName ?: return false
        val sensitivePackages = setOf(
            "com.android.chrome", "com.google.android.apps.authenticator",
            "com.bank", "com.paypal", "com.stripe", "wallet", "banking"
        )
        return sensitivePackages.any { packageName.contains(it, ignoreCase = true) }
    }
}
