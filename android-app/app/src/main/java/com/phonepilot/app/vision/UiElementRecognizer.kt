package com.phonepilot.app.vision

import android.graphics.Rect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Recognizes and matches UI elements across screen states.
 * Handles fuzzy matching, semantic similarity, and ambiguous element detection.
 */
class UiElementRecognizer(
    private val config: VisionConfig = VisionConfig(),
) {

    private val TAG = "UiElementRecognizer"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Weights for different matching criteria
    private val WEIGHT_TEXT = 0.35f
    private val WEIGHT_CONTENT_DESC = 0.25f
    private val WEIGHT_BOUNDS = 0.20f
    private val WEIGHT_CLASS = 0.10f
    private val WEIGHT_VIEW_ID = 0.05f
    private val WEIGHT_SEMANTIC_ROLE = 0.05f

    /** Minimum confidence for a positive match */
    private val MIN_MATCH_CONFIDENCE = 0.5f

    /** IoU threshold for considering bounds as matching */
    private val BOUNDS_IOU_THRESHOLD = 0.5f

    /**
     * Match elements between two screen states.
     * Returns pairs of (previousElement, currentElement) with confidence scores.
     */
    fun matchElements(
        previousElements: List<UiElement>,
        currentElements: List<UiElement>,
    ): List<ElementMatch> {
        val matches = mutableListOf<ElementMatch>()
        val usedCurrent = mutableSetOf<String>()

        // First pass: exact matches by view ID (most reliable)
        for (prev in previousElements) {
            if (prev.viewIdResourceName.isNullOrBlank()) continue

            currentElements.firstOrNull { curr ->
                !usedCurrent.contains(curr.elementId) &&
                curr.viewIdResourceName == prev.viewIdResourceName
            }?.let { curr ->
                matches.add(ElementMatch(prev, curr, 1.0f, MatchType.EXACT_VIEW_ID))
                usedCurrent.add(curr.elementId)
            }
        }

        // Second pass: high-confidence semantic matches
        for (prev in previousElements) {
            if (matches.any { it.previous.elementId == prev.elementId }) continue

            val bestMatch = findBestMatch(prev, currentElements.filter { !usedCurrent.contains(it.elementId) })
            if (bestMatch != null && bestMatch.confidence >= MIN_MATCH_CONFIDENCE) {
                matches.add(bestMatch)
                usedCurrent.add(bestMatch.current.elementId)
            }
        }

        // Third pass: bounds-based matches for remaining elements
        for (prev in previousElements) {
            if (matches.any { it.previous.elementId == prev.elementId }) continue

            val boundsMatch = findBoundsMatch(prev, currentElements.filter { !usedCurrent.contains(it.elementId) })
            if (boundsMatch != null && boundsMatch.confidence >= MIN_MATCH_CONFIDENCE) {
                matches.add(boundsMatch)
                usedCurrent.add(boundsMatch.current.elementId)
            }
        }

        return matches
    }

    /**
     * Find the best semantic match for an element.
     */
    private fun findBestMatch(
        previous: UiElement,
        candidates: List<UiElement>,
    ): ElementMatch? {
        var bestMatch: ElementMatch? = null
        var bestScore = 0.0f

        for (candidate in candidates) {
            val score = calculateMatchScore(previous, candidate)
            if (score > bestScore) {
                bestScore = score
                bestMatch = ElementMatch(previous, candidate, score, determineMatchType(previous, candidate, score))
            }
        }

        return if (bestScore >= MIN_MATCH_CONFIDENCE) bestMatch else null
    }

    /**
     * Find match based on bounds overlap (IoU).
     */
    private fun findBoundsMatch(
        previous: UiElement,
        candidates: List<UiElement>,
    ): ElementMatch? {
        previous.bounds?.let { prevBounds ->
            var bestMatch: ElementMatch? = null
            var bestIou = 0.0f

            for (candidate in candidates) {
                candidate.bounds?.let { currBounds ->
                    val iou = prevBounds.iou(currBounds)
                    if (iou > bestIou && iou >= BOUNDS_IOU_THRESHOLD) {
                        bestIou = iou
                        bestMatch = ElementMatch(previous, candidate, iou, MatchType.BOUNDS_OVERLAP)
                    }
                }
            }

            return bestMatch
        }
        return null
    }

    /**
     * Calculate composite match score between two elements.
     */
    private fun calculateMatchScore(e1: UiElement, e2: UiElement): Float {
        var score = 0.0f

        // Text similarity (most important)
        score += WEIGHT_TEXT * textSimilarity(e1.primaryText, e2.primaryText)

        // Content description similarity
        score += WEIGHT_CONTENT_DESC * textSimilarity(e1.contentDescription, e2.contentDescription)

        // Bounds overlap (IoU)
        score += WEIGHT_BOUNDS * boundsSimilarity(e1.bounds, e2.bounds)

        // Class name similarity
        score += WEIGHT_CLASS * classSimilarity(e1.className, e2.className)

        // View ID similarity
        score += WEIGHT_VIEW_ID * viewIdSimilarity(e1.viewIdResourceName, e2.viewIdResourceName)

        // Semantic role match
        score += WEIGHT_SEMANTIC_ROLE * if (e1.semanticRole == e2.semanticRole) 1.0f else 0.0f

        return score
    }

    /**
     * Calculate text similarity using Jaccard index on tokens.
     */
    fun textSimilarity(text1: String?, text2: String?): Float {
        if (text1.isNullOrBlank() && text2.isNullOrBlank()) return 1.0f
        if (text1.isNullOrBlank() || text2.isNullOrBlank()) return 0.0f

        val tokens1 = text1!!.toLowerCase().split("\\s+".toRegex()).filter { it.isNotBlank() }.toSet()
        val tokens2 = text2!!.toLowerCase().split("\\s+".toRegex()).filter { it.isNotBlank() }.toSet()

        if (tokens1.isEmpty() && tokens2.isEmpty()) return 1.0f
        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0.0f

        val intersection = tokens1.intersect(tokens2).size
        val union = tokens1.union(tokens2).size

        return intersection.toFloat() / union
    }

    /**
     * Calculate bounds similarity using IoU.
     */
    private fun boundsSimilarity(b1: Bounds?, b2: Bounds?): Float {
        if (b1 == null || b2 == null) return 0.0f
        return b1.iou(b2)
    }

    /**
     * Calculate class name similarity.
     */
    private fun classSimilarity(c1: String?, c2: String?): Float {
        if (c1.isNullOrBlank() && c2.isNullOrBlank()) return 1.0f
        if (c1.isNullOrBlank() || c2.isNullOrBlank()) return 0.0f

        // Simple class name comparison
        val simple1 = c1!!.substringAfterLast('.')
        val simple2 = c2!!.substringAfterLast('.')
        return if (simple1 == simple2) 1.0f else 0.0f
    }

    /**
     * Calculate view ID similarity.
     */
    private fun viewIdSimilarity(id1: String?, id2: String?): Float {
        if (id1.isNullOrBlank() && id2.isNullOrBlank()) return 1.0f
        if (id1.isNullOrBlank() || id2.isNullOrBlank()) return 0.0f
        return if (id1 == id2) 1.0f else 0.0f
    }

    /**
     * Determine the type of match based on what matched.
     */
    private fun determineMatchType(e1: UiElement, e2: UiElement, score: Float): MatchType {
        if (e1.viewIdResourceName?.isNotBlank() == true && e1.viewIdResourceName == e2.viewIdResourceName) {
            return MatchType.EXACT_VIEW_ID
        }
        if (e1.primaryText?.isNotBlank() == true && e1.primaryText == e2.primaryText) {
            return MatchType.EXACT_TEXT
        }
        if (e1.bounds != null && e2.bounds != null && e1.bounds!!.iou(e2.bounds!!) > 0.8f) {
            return MatchType.HIGH_BOUNDS_OVERLAP
        }
        if (e1.semanticRole == e2.semanticRole && e1.semanticRole != SemanticRole.UNKNOWN) {
            return MatchType.SEMANTIC_ROLE
        }
        return MatchType.FUZZY
    }

    /**
     * Detect ambiguous elements - elements that could match multiple candidates.
     */
    fun detectAmbiguousElements(
        previousElements: List<UiElement>,
        currentElements: List<UiElement>,
    ): List<AmbiguousMatch> {
        val ambiguous = mutableListOf<AmbiguousMatch>()

        for (prev in previousElements) {
            val scoredCandidates = currentElements.map { curr ->
                curr to calculateMatchScore(prev, curr)
            }.filter { it.second >= MIN_MATCH_CONFIDENCE }
                .sortedByDescending { it.second }

            // If multiple candidates have similar scores, it's ambiguous
            if (scoredCandidates.size >= 2) {
                val top = scoredCandidates[0]
                val second = scoredCandidates[1]

                // Check if scores are close (within 0.15)
                if (top.second - second.second < 0.15f) {
                    ambiguous.add(AmbiguousMatch(
                        previousElement = prev,
                        candidates = scoredCandidates.take(5).map { it.first },
                        topScore = top.second,
                        scoreDifference = top.second - second.second,
                    ))
                }
            }
        }

        return ambiguous
    }

    /**
     * Find element by text content (fuzzy search).
     */
    fun findByText(
        elements: List<UiElement>,
        query: String,
        threshold: Float = 0.6f,
    ): List<ElementSearchResult> {
        val results = mutableListOf<ElementSearchResult>()
        val queryLower = query.lowercase()

        for (element in elements) {
            var bestScore = 0.0f
            var matchedField = ""

            // Check text
            element.text?.let { text ->
                val score = textSimilarity(text, query)
                if (score > bestScore) {
                    bestScore = score
                    matchedField = "text"
                }
                // Also check contains for partial matches
                if (text.lowercase().contains(queryLower)) {
                    val score2 = 0.8f + 0.2f * score
                    if (score2 > bestScore) {
                        bestScore = score2
                        matchedField = "text_contains"
                    }
                }
            }

            // Check content description
            element.contentDescription?.let { desc ->
                val score = textSimilarity(desc, query)
                if (score > bestScore) {
                    bestScore = score
                    matchedField = "contentDescription"
                }
                if (desc.lowercase().contains(queryLower)) {
                    val score2 = 0.8f + 0.2f * score
                    if (score2 > bestScore) {
                        bestScore = score2
                        matchedField = "contentDescription_contains"
                    }
                }
            }

            // Check view ID
            element.viewIdResourceName?.let { id ->
                if (id.lowercase().contains(queryLower)) {
                    val score = 0.9f
                    if (score > bestScore) {
                        bestScore = score
                        matchedField = "viewId"
                    }
                }
            }

            if (bestScore >= threshold) {
                results.add(ElementSearchResult(element, bestScore, matchedField))
            }
        }

        return results.sortedByDescending { it.score }
    }

    /**
     * Find element by semantic role.
     */
    fun findByRole(elements: List<UiElement>, role: SemanticRole): List<UiElement> {
        return elements.filter { it.semanticRole == role }
    }

    /**
     * Find interactive elements.
     */
    fun findInteractive(elements: List<UiElement>): List<UiElement> {
        return elements.filter { it.clickable || it.longClickable || it.focusable || it.editable }
    }

    /**
     * Find elements in a specific region.
     */
    fun findInRegion(elements: List<UiElement>, region: Bounds): List<UiElement> {
        return elements.filter { element ->
            element.bounds?.let { region.contains(it) } == true
        }
    }

    /**
     * Get element at point.
     */
    fun getElementAt(elements: List<UiElement>, x: Int, y: Int): UiElement? {
        return elements.firstOrNull { element ->
            element.bounds?.contains(x, y) == true
        }
    }

    fun shutdown() {
        scope.cancel()
    }
}

/**
 * Result of matching two elements.
 */
data class ElementMatch(
    val previous: UiElement,
    val current: UiElement,
    val confidence: Float,
    val matchType: MatchType,
) {
    val isHighConfidence: Boolean get() = confidence >= 0.8f
    val isAmbiguous: Boolean get() = confidence < 0.7f
}

/**
 * Type of match between elements.
 */
enum class MatchType {
    EXACT_VIEW_ID,
    EXACT_TEXT,
    HIGH_BOUNDS_OVERLAP,
    SEMANTIC_ROLE,
    FUZZY,
    BOUNDS_OVERLAP,
}

/**
 * Ambiguous match - multiple candidates with similar scores.
 */
data class AmbiguousMatch(
    val previousElement: UiElement,
    val candidates: List<UiElement>,
    val topScore: Float,
    val scoreDifference: Float,
) {
    val isHighlyAmbiguous: Boolean get() = scoreDifference < 0.05f
}

/**
 * Search result for element lookup.
 */
data class ElementSearchResult(
    val element: UiElement,
    val score: Float,
    val matchedField: String,
)
