package com.phonepilot.app.vision

import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ScreenStateDetector(
    private val context: Context,
    private val config: VisionConfig = VisionConfig(),
    private val accessibilityAnalyzer: AccessibilityTreeAnalyzer,
    private val elementRecognizer: UiElementRecognizer = UiElementRecognizer(config),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val screenIdentityCache = mutableMapOf<String, ScreenIdentity>()

    suspend fun analyzeScreen(
        rootNode: AccessibilityNodeInfo?,
        ocrResult: OcrResult? = null,
        packageName: String? = null,
        activityName: String? = null,
    ): ScreenState = withContext(Dispatchers.IO) {
        val accessibilityElements = accessibilityAnalyzer.analyze(rootNode)
        val fusedElements = if (ocrResult != null && ocrResult.hasText) {
            VisionFusionEngine(config, elementRecognizer).fuse(
                accessibilityElements, ocrResult,
                config.maxElementsPerScreen, config.maxElementsPerScreen,
            )
        } else accessibilityElements
        val identity = detectScreenIdentity(fusedElements, packageName, activityName)
        val loadingIndicators = identifyLoadingIndicators(fusedElements)
        val errorMessages = identifyErrorMessages(fusedElements)
        val emptyStates = identifyEmptyStates(fusedElements)
        ScreenState(
            packageName = packageName,
            activityName = activityName,
            screenWidth = config.maxElementsPerScreen,
            screenHeight = config.maxElementsPerScreen,
            elements = fusedElements,
            ocrResult = ocrResult,
            timestamp = System.currentTimeMillis(),
            confidence = calculateConfidence(fusedElements, ocrResult),
            hasKeyboard = detectKeyboard(fusedElements),
            hasDialog = detectDialog(fusedElements, rootNode),
            loadingIndicators = loadingIndicators,
            errorMessages = errorMessages,
            emptyStates = emptyStates,
        )
    }

    fun detectScreenIdentity(elements: List<UiElement>, packageName: String?, activityName: String?): ScreenIdentity {
        val cacheKey = "${packageName}_${activityName}_${elements.size}"
        return screenIdentityCache.getOrPut(cacheKey) {
            val elementTypes = elements.map { it.semanticRole }.groupingBy { it }.eachCount()
            val interactiveCount = elements.count { it.clickable || it.longClickable || it.focusable || it.editable }
            val textCount = elements.count { it.hasTextContent }
            val hasScrollable = elements.any { it.scrollable }
            val hasWebView = elements.any { it.semanticRole == SemanticRole.WEB_VIEW }
            val hasList = elements.any {
                it.semanticRole in setOf(SemanticRole.LIST_VIEW, SemanticRole.RECYCLER_VIEW, SemanticRole.LIST_ITEM)
            }
            val parts = mutableListOf<String>()
            packageName?.let { parts.add("App: $it") }
            activityName?.let { parts.add("Activity: $it") }
            elementTypes.entries.sortedByDescending { it.value }.take(3).takeIf { it.isNotEmpty() }?.let { types ->
                parts.add("Elements: ${types.map { "${it.key}(${it.value})" }.joinToString(", ")}")
            }
            if (hasList) parts.add("List-based")
            if (hasScrollable) parts.add("Scrollable")
            if (hasWebView) parts.add("WebView")
            parts.add("Interactive: $interactiveCount, Text: $textCount")
            ScreenIdentity(
                packageName, activityName, parts.joinToString(" | "), elementTypes.toString(),
                if (elements.isNotEmpty()) interactiveCount.toFloat() / elements.size else 0f,
            )
        }
    }

    private fun identifyLoadingIndicators(elements: List<UiElement>) = elements.filter { element ->
        element.semanticRole in setOf(SemanticRole.PROGRESS_BAR, SemanticRole.SPINNER) ||
            element.className?.lowercase()?.let { it.contains("progress") || it.contains("spinner") || it.contains("loading") } == true
    }

    private fun identifyErrorMessages(elements: List<UiElement>): List<String> {
        val keywords = setOf("error", "failed", "فشل", "خطأ", "exception", "crash", "تعذر", "مشكلة")
        return elements.filter { it.hasTextContent }
            .map { it.primaryText?.lowercase() ?: "" }
            .filter { text -> keywords.any(text::contains) }
            .distinct()
    }

    private fun identifyEmptyStates(elements: List<UiElement>) = elements.filter { element ->
        val keywords = setOf("empty", "no data", "لا يوجد", "فارغ", "no results", "لا نتائج", "nothing here")
        element.hasTextContent && keywords.any { element.primaryText?.lowercase()?.contains(it) == true }
    }

    private fun detectKeyboard(elements: List<UiElement>): Boolean = elements.any { element ->
        element.semanticRole == SemanticRole.TEXT_FIELD &&
            (element.className?.lowercase()?.contains("keyboard") == true ||
                element.viewIdResourceName?.lowercase()?.contains("keyboard") == true)
    }

    private fun detectDialog(elements: List<UiElement>, rootNode: AccessibilityNodeInfo?): Boolean {
        if (elements.any { it.semanticRole == SemanticRole.DIALOG }) return true
        val node = rootNode ?: return false
        if (node.windowId != 0) {
            val className = node.className?.toString()?.lowercase() ?: ""
            return className.contains("dialog") || className.contains("popup") || className.contains("bottomsheet")
        }
        return false
    }

    private fun calculateConfidence(elements: List<UiElement>, ocrResult: OcrResult?): Float {
        if (elements.isEmpty()) return 0f
        var confidence = 0.5f + min(elements.size / 50f, 0.2f)
        val highConfidenceCount = elements.count { it.confidence > 0.7f }
        confidence += min(highConfidenceCount.toFloat() / elements.size * 0.2f, 0.2f)
        if (ocrResult?.success == true && ocrResult.hasText) confidence += 0.1f
        return min(confidence, 1f)
    }

    fun shutdown() { scope.cancel() }
}

data class ScreenIdentity(
    val packageName: String?,
    val activityName: String?,
    val semanticDescription: String,
    val elementTypeSignature: String,
    val interactiveDensity: Float,
) {
    fun similarity(other: ScreenIdentity): Float {
        var score = 0f
        var factors = 0
        if (packageName == other.packageName) { score += 1f; factors++ }
        if (activityName == other.activityName) { score += 1f; factors++ }
        val tokens1 = elementTypeSignature.split("[^a-zA-Z0-9]+".toRegex()).filter { it.isNotBlank() }.toSet()
        val tokens2 = other.elementTypeSignature.split("[^a-zA-Z0-9]+".toRegex()).filter { it.isNotBlank() }.toSet()
        if (tokens1.isNotEmpty() || tokens2.isNotEmpty()) {
            val union = tokens1.union(tokens2).size
            score += if (union > 0) tokens1.intersect(tokens2).size.toFloat() / union else 0f
            factors++
        }
        score += max(0f, 1f - abs(interactiveDensity - other.interactiveDensity) * 2)
        factors++
        return if (factors > 0) score / factors else 0f
    }
}

class ScreenChangeDetector(
    private val config: VisionConfig = VisionConfig(),
    private val elementRecognizer: UiElementRecognizer = UiElementRecognizer(config),
) {
    fun detectChanges(previous: ScreenState, current: ScreenState): ScreenChange {
        if (previous.packageName != current.packageName || previous.activityName != current.activityName) {
            return ScreenChange(
                changeType = ChangeType.SCREEN_TRANSITION,
                previousState = previous,
                currentState = current,
                significance = Significance.MAJOR,
            )
        }
        val matches = elementRecognizer.matchElements(previous.elements, current.elements)
        val matchedPrevious = matches.map { it.previous.elementId }.toSet()
        val matchedCurrent = matches.map { it.current.elementId }.toSet()
        val added = current.elements.filter { it.elementId !in matchedCurrent }
        val removed = previous.elements.filter { it.elementId !in matchedPrevious }
        val changed = matches.mapNotNull { match ->
            val flags = mutableSetOf<ChangeFlag>()
            if (match.previous.primaryText != match.current.primaryText) flags.add(ChangeFlag.TEXT_CHANGED)
            val previousBounds = match.previous.bounds
            val currentBounds = match.current.bounds
            if (previousBounds != currentBounds) flags.add(ChangeFlag.BOUNDS_CHANGED)
            if (match.previous.enabled != match.current.enabled || match.previous.selected != match.current.selected ||
                match.previous.checked != match.current.checked || match.previous.visible != match.current.visible
            ) flags.add(ChangeFlag.STATE_CHANGED)
            if (match.previous.visible != match.current.visible) flags.add(ChangeFlag.VISIBILITY_CHANGED)
            if (flags.isEmpty()) null else ElementChange(
                match.current.elementId, match.previous, match.current, flags,
            )
        }
        val dialogChange = when {
            !previous.hasDialog && current.hasDialog -> ChangeType.DIALOG_APPEARED
            previous.hasDialog && !current.hasDialog -> ChangeType.DIALOG_DISMISSED
            else -> null
        }
        val keyboardChange = when {
            !previous.hasKeyboard && current.hasKeyboard -> ChangeType.KEYBOARD_SHOWN
            previous.hasKeyboard && !current.hasKeyboard -> ChangeType.KEYBOARD_HIDDEN
            else -> null
        }
        val (baseType, baseSignificance) = when {
            added.size > 10 || removed.size > 10 || changed.size > 15 -> ChangeType.SCREEN_TRANSITION to Significance.MAJOR
            added.size > 3 || removed.size > 3 || changed.size > 5 -> ChangeType.STATE_CHANGED to Significance.MODERATE
            added.isNotEmpty() -> ChangeType.ELEMENT_ADDED to Significance.MINOR
            removed.isNotEmpty() -> ChangeType.ELEMENT_REMOVED to Significance.MINOR
            changed.any { ChangeFlag.TEXT_CHANGED in it.changeFlags } -> ChangeType.TEXT_CHANGED to Significance.MINOR
            changed.isNotEmpty() -> ChangeType.ELEMENT_CHANGED to Significance.MINOR
            else -> ChangeType.NO_CHANGE to Significance.MINOR
        }
        val finalType = dialogChange ?: keyboardChange ?: baseType
        val finalSignificance = when {
            dialogChange != null -> Significance.MAJOR
            keyboardChange != null -> Significance.MODERATE
            else -> baseSignificance
        }
        return ScreenChange(
            changeType = finalType,
            addedElements = added,
            removedElements = removed,
            changedElements = changed,
            previousState = previous,
            currentState = current,
            significance = finalSignificance,
        )
    }
}
