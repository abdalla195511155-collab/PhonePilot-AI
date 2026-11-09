package com.phonepilot.app.vision

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import java.util.concurrent.atomic.AtomicReference

/**
 * Detects and describes the current screen state from accessibility data.
 */
class ScreenStateDetector(
    private val context: Context,
    private val config: VisionConfig = VisionConfig(),
    private val accessibilityAnalyzer: AccessibilityTreeAnalyzer,
    private val elementRecognizer: UiElementRecognizer = UiElementRecognizer(config),
) {

    private val TAG = "ScreenStateDetector"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Cache for screen identity detection */
    private val screenIdentityCache = mutableMapOf<String, ScreenIdentity>()

    /**
     * Analyze the current screen and produce a ScreenState.
     */
    suspend fun analyzeScreen(
        rootNode: AccessibilityNodeInfo?,
        ocrResult: OcrResult? = null,
        packageName: String? = null,
        activityName: String? = null,
    ): ScreenState = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        // Extract elements from accessibility tree
        val accessibilityElements = accessibilityAnalyzer.analyze(rootNode)

        // Fuse with OCR if available
        val fusedElements = if (ocrResult != null && ocrResult.hasText) {
            VisionFusionEngine(config, elementRecognizer).fuse(
                accessibilityElements,
                ocrResult,
                config.maxElementsPerScreen, // Using as screen width approx
                config.maxElementsPerScreen, // Using as screen height approx
            )
        } else {
            accessibilityElements
        }

        // Detect screen identity
        val screenIdentity = detectScreenIdentity(fusedElements, packageName, activityName)

        // Identify special elements
        val loadingIndicators = identifyLoadingIndicators(fusedElements)
        val errorMessages = identifyErrorMessages(fusedElements)
        val emptyStates = identifyEmptyStates(fusedElements)
        val hasKeyboard = detectKeyboard(fusedElements)
        val hasDialog = detectDialog(fusedElements, rootNode)

        // Calculate overall confidence
        val confidence = calculateConfidence(fusedElements, ocrResult)

        ScreenState(
            packageName = packageName,
            activityName = activityName,
            screenWidth = config.maxElementsPerScreen, // Placeholder
            screenHeight = config.maxElementsPerScreen, // Placeholder
            elements = fusedElements,
            ocrResult = ocrResult,
            timestamp = System.currentTimeMillis(),
            confidence = confidence,
            hasKeyboard = hasKeyboard,
            hasDialog = hasDialog,
            loadingIndicators = loadingIndicators,
            errorMessages = errorMessages,
            emptyStates = emptyStates,
        )
    }

    /**
     * Detect screen identity from elements and package/activity.
     */
    fun detectScreenIdentity(
        elements: List<UiElement>,
        packageName: String?,
        activityName: String?,
    ): ScreenIdentity {
        val cacheKey = "${packageName}_${activityName}_${elements.size}"

        return screenIdentityCache.getOrPut(cacheKey) {
            // Generate semantic description of the screen
            val elementTypes = elements.map { it.semanticRole }.groupingBy { it }.eachCount()
            val interactiveCount = elements.count { it.clickable || it.longClickable || it.focusable || it.editable }
            val textCount = elements.count { it.hasTextContent }
            val hasScrollable = elements.any { it.scrollable }
            val hasWebView = elements.any { it.semanticRole == SemanticRole.WEB_VIEW }
            val hasList = elements.any { it.semanticRole in setOf(SemanticRole.LIST_VIEW, SemanticRole.RECYCLER_VIEW, SemanticRole.LIST_ITEM) }

            val description = buildScreenDescription(
                elementTypes, interactiveCount, textCount,
                hasScrollable, hasWebView, hasList, packageName, activityName
            )

            ScreenIdentity(
                packageName = packageName,
                activityName = activityName,
                semanticDescription = description,
                elementTypeSignature = elementTypes.toString(),
                interactiveDensity = if (elements.isNotEmpty()) interactiveCount.toFloat() / elements.size else 0f,
            )
        }
    }

    /**
     * Build human-readable screen description.
     */
    private fun buildScreenDescription(
        elementTypes: Map<SemanticRole, Int>,
        interactiveCount: Int,
        textCount: Int,
        hasScrollable: Boolean,
        hasWebView: Boolean,
        hasList: Boolean,
        packageName: String?,
        activityName: String?,
    ): String {
        val parts = mutableListOf<String>()

        // App context
        packageName?.let { parts.add("App: $it") }
        activityName?.let { parts.add("Activity: $it") }

        // Dominant element types
        val sortedTypes = elementTypes.entries.sortedByDescending { it.value }.take(3)
        if (sortedTypes.isNotEmpty()) {
            parts.add("Elements: ${sortedTypes.map { "${it.key}(${it.value})" }.joinToString(", ")}")
        }

        // Structural hints
        if (hasList) parts.add("List-based")
        if (hasScrollable) parts.add("Scrollable")
        if (hasWebView) parts.add("WebView")

        // Interaction density
        parts.add("Interactive: $interactiveCount, Text: $textCount")

        return parts.joinToString(" | ")
    }

    /**
     * Identify loading indicators (progress bars, spinners).
     */
    private fun identifyLoadingIndicators(elements: List<UiElement>): List<UiElement> {
        return elements.filter { element ->
            element.semanticRole in setOf(
                SemanticRole.PROGRESS_BAR,
                SemanticRole.SPINNER,
            ) || element.className?.lowercase()?.let {
                it.contains("progress") || it.contains("spinner") || it.contains("loading")
            } == true
        }
    }

    /**
     * Identify error messages.
     */
    private fun identifyErrorMessages(elements: List<UiElement>): List<String> {
        val errorKeywords = setOf("error", "failed", "فشل", "خطأ", "error", "exception", "crash", "تعذر", "مشكلة")
        return elements
            .filter { it.hasTextContent }
            .map { it.primaryText?.lowercase() ?: "" }
            .filter { text -> errorKeywords.any { text.contains(it) } }
            .distinct()
    }

    /**
     * Identify empty states.
     */
    private fun identifyEmptyStates(elements: List<UiElement>): List<UiElement> {
        val emptyKeywords = setOf("empty", "no data", "لا يوجد", "فارغ", "no results", "لا نتائج", "nothing here")
        return elements.filter { element ->
            element.hasTextContent && emptyKeywords.any { element.primaryText?.lowercase()?.contains(it) == true }
        }
    }

    /**
     * Detect keyboard presence.
     */
    private fun detectKeyboard(elements: List<UiElement>): Boolean {
        return elements.any { element ->
            element.semanticRole == SemanticRole.TEXT_FIELD &&
            (element.className?.lowercase()?.contains("keyboard") == true ||
             element.viewIdResourceName?.lowercase()?.contains("keyboard") == true)
        }
    }

    /**
     * Detect dialog presence from root node.
     */
    private fun detectDialog(elements: List<UiElement>, rootNode: AccessibilityNodeInfo?): Boolean {
        // Check elements for dialog role
        if (elements.any { it.semanticRole == SemanticRole.DIALOG }) return true

        // Check root node for dialog window
        rootNode?.let { node ->
            if (node.windowId != 0) {
                val className = node.className?.toString()?.lowercase() ?: ""
                return className.contains("dialog") || className.contains("popup") || className.contains("bottomsheet")
            }
        }
        return false
    }

    /**
     * Calculate overall confidence in the screen state.
     */
    private fun calculateConfidence(elements: List<UiElement>, ocrResult: OcrResult?): Float {
        if (elements.isEmpty()) return 0.0f

        var confidence = 0.5f

        // More elements = more confidence (up to a point)
        confidence += min(elements.size / 50f, 0.2f)

        // High-confidence elements boost score
        val highConfCount = elements.count { it.confidence > 0.7f }
        confidence += min(highConfCount.toFloat() / elements.size * 0.2f, 0.2f)

        // OCR confirmation boosts confidence
        if (ocrResult != null && ocrResult.success && ocrResult.hasText) {
            confidence += 0.1f
        }

        return min(confidence, 1.0f)
    }

    fun shutdown() {
        scope.coroutineContext.cancel()
    }
}

/**
 * Screen identity for tracking and comparison.
 */
data class ScreenIdentity(
    val packageName: String?,
    val activityName: String?,
    val semanticDescription: String,
    val elementTypeSignature: String,
    val interactiveDensity: Float,
) {
    fun similarity(other: ScreenIdentity): Float {
        var score = 0.0f
        var factors = 0

        if (packageName == other.packageName) { score += 1.0f; factors++ }
        if (activityName == other.activityName) { score += 1.0f; factors++ }

        // Element type signature similarity (Jaccard on tokens)
        val tokens1 = elementTypeSignature.split("[^a-zA-Z0-9]+".toRegex()).filter { it.isNotBlank() }.toSet()
        val tokens2 = other.elementTypeSignature.split("[^a-zA-Z0-9]+".toRegex()).filter { it.isNotBlank() }.toSet()
        if (tokens1.isNotEmpty() || tokens2.isNotEmpty()) {
            val intersection = tokens1.intersect(tokens2).size
            val union = tokens1.union(tokens2).size
            score += intersection.toFloat() / union
            factors++
        }

        // Interactive density similarity
        val densityDiff = abs(interactiveDensity - other.interactiveDensity)
        score += max(0f, 1f - densityDiff * 2)
        factors++

        return if (factors > 0) score / factors else 0f
    }
}

/**
 * Detects changes between screen states.
 */
class ScreenChangeDetector(
    private val config: VisionConfig = VisionConfig(),
    private val elementRecognizer: UiElementRecognizer = UiElementRecognizer(config),
) {

    private val TAG = "ScreenChangeDetector"
    private val MIN_CHANGE_CONFIDENCE = 0.3f

    /**
     * Compare two screen states and detect changes.
     */
    fun detectChanges(
        previous: ScreenState,
        current: ScreenState,
    ): ScreenChange {
        // Quick checks for major changes
        if (previous.packageName != current.packageName ||
            previous.activityName != current.activityName) {
            return ScreenChange(
                changeType = ChangeType.SCREEN_TRANSITION,
                previousState = previous,
                currentState = current,
                significance = Significance.MAJOR,
            )
        }

        // Match elements between states
        val matches = elementRecognizer.matchElements(previous.elements, current.elements)

        // Categorize changes
        val added = mutableListOf<UiElement>()
        val removed = mutableListOf<UiElement>()
        val changed = mutableListOf<ElementChange>()

        val matchedPrevious = matches.map { it.previous.elementId }.toSet()
        val matchedCurrent = matches.map { it.current.elementId }.toSet()

        // Find added elements
        for (element in current.elements) {
            if (element.elementId !in matchedCurrent) {
                added.add(element)
            }
        }

        // Find removed elements
        for (element in previous.elements) {
            if (element.elementId !in matchedPrevious) {
                removed.add(element)
            }
        }

        // Find changed elements
        for (match in matches) {
            val changes = detectElementChanges(match.previous, match.current)
            if (changes.isNotEmpty()) {
                changed.add(ElementChange(
                    elementId = match.current.elementId,
                    previousElement = match.previous,
                    currentElement = match.current,
                    changeFlags = changes.toSet(),
                ))
            }
        }

        // Determine overall change type and significance
        val (changeType, significance) = classifyChange(added, removed, changed, previous, current)

        // Check for dialog/keyboard changes
        val dialogChange = detectDialogChange(previous, current)
        val keyboardChange = detectKeyboardChange(previous, current)

        var finalChangeType = changeType
        var finalSignificance = significance

        if (dialogChange != null) {
            finalChangeType = dialogChange
            finalSignificance = Significance.MAJOR
        } else if (keyboardChange != null) {
            finalChangeType = keyboardChange
            finalSignificance = Significance.MODERATE
        }

        return ScreenChange(
            changeType = finalChangeType,
            addedElements = added,
            removedElements = removed,
            changedElements = changed,
            previousState = previous,
            currentState = current,
            significance = finalSignificance,
        )
    }

    /**
     * Detect specific changes between two matched elements.
     */
    private fun detectElementChanges(prev: UiElement, curr: UiElement): List<ChangeFlag> {
        val changes = mutableListOf<ChangeFlag>()

        // Text changed
        if (prev.primaryText != curr.primaryText) {
            changes.add(ChangeFlag.TEXT_CHANGED)
        }

        // Bounds changed significantly
        prev.bounds?.let { pBounds ->
            curr.bounds?.let { cBounds ->
                val iou = pBounds.iou(cBounds)
                if (iou < 0.8f) {
                    changes.add(ChangeFlag.BOUNDS_CHANGED)
                }
            } ?: run { changes.add(ChangeFlag.BOUNDS_CHANGED) }
        }

        // State changed (enabled, selected, checked, etc.)
        if (prev.enabled != curr.enabled ||
            prev.selected != curr.selected ||
            prev.checked != curr.checked ||
            prev.visible != curr.visible) {
            changes.add(ChangeFlag.STATE_CHANGED)
        }

        // Visibility changed
        if (prev.visible != curr.visible) {
            changes.add(ChangeFlag.VISIBILITY_CHANGED)
        }

        return changes
    }

    /**
     * Classify the overall change.
     */
    private fun classifyChange(
        added: List<UiElement>,
        removed: List<UiElement>,
        changed: List<ElementChange>,
        previous: ScreenState,
        current: ScreenState,
    ): Pair<ChangeType, Significance> {
        // Major: screen transition (already handled), dialog, many changes
        if (added.size > 10 || removed.size > 10 || changed.size > 15) {
            return ChangeType.SCREEN_TRANSITION to Significance.MAJOR
        }

        // Moderate: several elements added/removed/changed
        if (added.size > 3 || removed.size > 3 || changed.size > 5) {
            return ChangeType.STATE_CHANGED to Significance.MODERATE
        }

        // Minor: few changes
        if (added.isNotEmpty()) return ChangeType.ELEMENT_ADDED to Significance.MINOR
        if (removed.isNotEmpty()) return ChangeType.ELEMENT_REMOVED to Significance.MINOR
        if (changed.isNotEmpty()) {
            val hasTextChange = changed.any { ChangeFlag.TEXT_CHANGED in it.changeFlags }
            return if (hasTextChange) ChangeType.TEXT_CHANGED to Significance.MINOR else ChangeType.ELEMENT_CHANGED to Significance.MINOR
        }

        return ChangeType.NO_CHANGE to Significance.MINOR
    }

    /**
     * Detect dialog appearance/disappearance.
     */
    private fun detectDialogChange(previous: ScreenState, current: ScreenState): ChangeType? {
        if (!previous.hasDialog && current.hasDialog) return ChangeType.DIALOG_APPEARED
        if (previous.hasDialog && !current.hasDialog) return ChangeType.DIALOG_DISMISSED
        return null
    }

    /**
     * Detect keyboard show/hide.
     */
    private fun detectKeyboardChange(previous: ScreenState, current: ScreenState): ChangeType? {
        if (!previous.hasKeyboard && current.hasKeyboard) return ChangeType.KEYBOARD_SHOWN
        if (previous.hasKeyboard && !current.hasKeyboard) return ChangeType.KEYBOARD_HIDDEN
        return null
    }
}
