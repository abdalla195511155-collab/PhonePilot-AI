package com.phonepilot.app.vision

import android.graphics.Rect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * Fuses Accessibility Tree data with OCR results for a unified view.
 * Handles conflicts, merges overlapping elements, and enriches elements with OCR text.
 */
class VisionFusionEngine(
    private val config: VisionConfig = VisionConfig(),
    private val elementRecognizer: UiElementRecognizer = UiElementRecognizer(config),
) {

    private val TAG = "VisionFusionEngine"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** IoU threshold for considering elements as overlapping */
    private val FUSION_IOU_THRESHOLD = 0.3f

    /** Distance threshold for considering elements as nearby (in pixels) */
    private val PROXIMITY_THRESHOLD_DP = 48f

    /**
     * Fuse accessibility elements with OCR results.
     * Returns a unified list of elements with merged information.
     */
    suspend fun fuse(
        accessibilityElements: List<UiElement>,
        ocrResult: OcrResult?,
        screenWidth: Int,
        screenHeight: Int,
    ): List<UiElement> = withContext(Dispatchers.IO) {
        if (ocrResult == null || !ocrResult.hasText) {
            return@withContext accessibilityElements
        }

        val fusedElements = mutableListOf<UiElement>()
        val usedOcrBlocks = mutableSetOf<Int>()
        val dpToPx = screenWidth / 360f // approximate conversion

        // First pass: match OCR blocks to accessibility elements by bounds overlap
        for ((i, ocrBlock) in ocrResult.textBlocks.withIndex()) {
            var bestMatch: UiElement? = null
            var bestIou = 0.0f

            for (element in accessibilityElements) {
                element.bounds?.let { elementBounds ->
                    val iou = elementBounds.iou(ocrBlock.bounds)
                    if (iou > bestIou && iou >= FUSION_IOU_THRESHOLD) {
                        bestIou = iou
                        bestMatch = element
                    }
                }
            }

            if (bestMatch != null) {
                // Fuse OCR text into the accessibility element
                val fused = fuseOcrIntoElement(bestMatch, ocrBlock, bestIou)
                val index = accessibilityElements.indexOf(bestMatch)
                if (index >= 0) {
                    fusedElements.add(fused)
                } else {
                    fusedElements.add(fused)
                }
                usedOcrBlocks.add(i)
            }
        }

        // Add unmatched accessibility elements
        for (element in accessibilityElements) {
            if (!fusedElements.any { it.elementId == element.elementId }) {
                fusedElements.add(element)
            }
        }

        // Second pass: create new elements for unmatched OCR blocks
        for ((i, ocrBlock) in ocrResult.textBlocks.withIndex()) {
            if (i in usedOcrBlocks) continue

            // Check if this OCR block is near any existing element
            val nearbyElement = fusedElements.firstOrNull { element ->
                element.bounds?.let { elementBounds ->
                    val distance = calculateDistance(elementBounds, ocrBlock.bounds)
                    distance < PROXIMITY_THRESHOLD_DP * dpToPx
                }
            }

            if (nearbyElement != null) {
                // Merge into nearby element
                val fused = fuseOcrIntoElement(nearbyElement, ocrBlock, 0f)
                val index = fusedElements.indexOf(nearbyElement)
                if (index >= 0) {
                    fusedElements[index] = fused
                }
            } else {
                // Create new element from OCR block
                fusedElements.add(createElementFromOcr(ocrBlock, screenWidth, screenHeight))
            }
        }

        // Post-process: resolve conflicts and deduplicate
        val finalElements = resolveConflicts(fusedElements)

        Log.d(TAG, "Fused ${accessibilityElements.size} accessibility + ${ocrResult.textBlocks.size} OCR = ${finalElements.size} elements")
        return@withContext finalElements
    }

    /**
     * Fuse OCR text block into an accessibility element.
     */
    private fun fuseOcrIntoElement(
        element: UiElement,
        ocrBlock: OcrTextBlock,
        iou: Float,
    ): UiElement {
        val hasOcrText = ocrBlock.text.isNotBlank()
        val hasElementText = element.text?.isNotBlank() == true

        // Determine best text source
        val fusedText = when {
            !hasElementText && hasOcrText -> ocrBlock.text
            hasElementText && hasOcrText -> {
                // Prefer longer, more complete text
                if (ocrBlock.text.length > element.text!!.length) ocrBlock.text else element.text!!
            }
            else -> element.text
        }

        val fusedContentDesc = when {
            element.contentDescription?.isNotBlank() == true -> element.contentDescription
            hasOcrText && ocrBlock.text != fusedText -> ocrBlock.text
            else -> element.contentDescription
        }

        // Boost confidence if OCR confirms the text
        val confidenceBoost = if (iou > 0.5f && hasOcrText && hasElementText) {
            if (element.text?.lowercase() == ocrBlock.text.lowercase()) 0.15f else 0.05f
        } else if (iou > 0.3f && hasOcrText) {
            0.1f
        } else 0f

        // Update source to FUSED if we merged OCR data
        val newSource = if (iou > 0.3f || hasOcrText) ElementSource.FUSED else element.source

        return element.copy(
            text = fusedText,
            contentDescription = fusedContentDesc,
            confidence = min(element.confidence + confidenceBoost, 1.0f),
            source = newSource,
            bounds = element.bounds ?: ocrBlock.bounds,
        )
    }

    /**
     * Create a new element from an OCR text block.
     */
    private fun createElementFromOcr(
        ocrBlock: OcrTextBlock,
        screenWidth: Int,
        screenHeight: Int,
    ): UiElement {
        return UiElement(
            elementId = "ocr_${ocrBlock.readingOrder}_${ocrBlock.bounds.left}_${ocrBlock.bounds.top}",
            text = ocrBlock.text,
            bounds = ocrBlock.bounds,
            confidence = ocrBlock.confidence * 0.8f, // Lower confidence for OCR-only elements
            source = ElementSource.OCR,
            semanticRole = inferRoleFromText(ocrBlock.text),
        )
    }

    /**
     * Infer semantic role from text content.
     */
    private fun inferRoleFromText(text: String): SemanticRole {
        val lower = text.lowercase()
        return when {
            lower.matches("(?i).*\\b(button|click|press|tap|submit|confirm|cancel|ok|done|next|back|close)\\b.*") -> SemanticRole.BUTTON
            lower.matches("(?i).*\\b(search|find|query)\\b.*") -> SemanticRole.TEXT_FIELD
            lower.matches("(?i).*\\b(menu|navigation|nav)\\b.*") -> SemanticRole.MENU_ITEM
            lower.matches("(?i).*\\b(tab|section)\\b.*") -> SemanticRole.TAB
            else -> SemanticRole.TEXT
        }
    }

    /**
     * Calculate center-to-center distance between two bounds.
     */
    private fun calculateDistance(b1: Bounds, b2: Bounds): Float {
        val dx = b1.centerX() - b2.centerX()
        val dy = b1.centerY() - b2.centerY()
        return sqrt(dx * dx + dy * dy).toFloat()
    }

    /**
     * Resolve conflicts and deduplicate fused elements.
     */
    private fun resolveConflicts(elements: List<UiElement>): List<UiElement> {
        val resolved = mutableListOf<UiElement>()
        val processed = mutableSetOf<String>()

        // Sort by confidence descending
        val sorted = elements.sortedByDescending { it.confidence }

        for (element in sorted) {
            if (processed.contains(element.elementId)) continue

            // Find conflicting elements (high IoU overlap)
            val conflicts = sorted.filter { other ->
                other != element &&
                !processed.contains(other.elementId) &&
                element.bounds != null &&
                other.bounds != null &&
                element.bounds!!.iou(other.bounds!!) > 0.7f
            }

            if (conflicts.isEmpty()) {
                resolved.add(element)
                processed.add(element.elementId)
            } else {
                // Merge conflicting elements - keep the one with highest confidence
                // but combine their text content
                val allTexts = mutableSetOf<String>()
                element.text?.let { allTexts.add(it) }
                element.contentDescription?.let { allTexts.add(it) }

                for (conflict in conflicts) {
                    conflict.text?.let { allTexts.add(it) }
                    conflict.contentDescription?.let { allTexts.add(it) }
                    processed.add(conflict.elementId)
                }

                val merged = element.copy(
                    text = allTexts.maxByOrNull { it.length } ?: element.text,
                    contentDescription = allTexts.minus(element.text?.takeIf { it.isNotBlank() } ?: emptySet())
                        .firstOrNull() ?: element.contentDescription,
                    confidence = max(element.confidence, conflicts.maxByOrNull { it.confidence }?.confidence ?: 0f),
                    source = ElementSource.FUSED,
                )
                resolved.add(merged)
                processed.add(element.elementId)
            }
        }

        return resolved
    }

    /**
     * Detect conflicts between accessibility and OCR data.
     */
    fun detectConflicts(
        accessibilityElements: List<UiElement>,
        ocrResult: OcrResult?,
    ): List<FusionConflict> {
        val conflicts = mutableListOf<FusionConflict>()

        ocrResult?.textBlocks?.forEach { ocrBlock ->
            for (element in accessibilityElements) {
                element.bounds?.let { elementBounds ->
                    val iou = elementBounds.iou(ocrBlock.bounds)
                    if (iou > FUSION_IOU_THRESHOLD) {
                        // Check for text mismatch
                        val elementText = element.primaryText?.lowercase() ?: ""
                        val ocrText = ocrBlock.text.lowercase()

                        if (elementText.isNotBlank() && ocrText.isNotBlank() &&
                            !elementText.contains(ocrText) && !ocrText.contains(elementText)) {

                            val similarity = elementRecognizer.textSimilarity(element.primaryText, ocrBlock.text)
                            if (similarity < 0.5f) {
                                conflicts.add(FusionConflict(
                                    accessibilityElement = element,
                                    ocrBlock = ocrBlock,
                                    iou = iou,
                                    conflictType = ConflictType.TEXT_MISMATCH,
                                    similarity = similarity,
                                ))
                            }
                        }

                        // Check for bounds mismatch (OCR finds text where accessibility says no text)
                        if (elementText.isBlank() && ocrText.isNotBlank() && iou > 0.5f) {
                            conflicts.add(FusionConflict(
                                accessibilityElement = element,
                                ocrBlock = ocrBlock,
                                iou = iou,
                                conflictType = ConflictType.MISSING_TEXT_IN_ACCESSIBILITY,
                                similarity = 0f,
                            ))
                        }
                    }
                }
            }
        }

        return conflicts
    }

    fun shutdown() {
        scope.cancel()
        elementRecognizer.shutdown()
    }
}

/**
 * Conflict between accessibility and OCR data.
 */
data class FusionConflict(
    val accessibilityElement: UiElement,
    val ocrBlock: OcrTextBlock,
    val iou: Float,
    val conflictType: ConflictType,
    val similarity: Float,
) {
    val severity: ConflictSeverity get() = when (conflictType) {
        ConflictType.TEXT_MISMATCH -> if (similarity < 0.2f) ConflictSeverity.HIGH else ConflictSeverity.MEDIUM
        ConflictType.MISSING_TEXT_IN_ACCESSIBILITY -> ConflictSeverity.MEDIUM
        ConflictType.BOUNDS_MISMATCH -> ConflictSeverity.LOW
    }
}

/**
 * Type of fusion conflict.
 */
enum class ConflictType {
    TEXT_MISMATCH,
    MISSING_TEXT_IN_ACCESSIBILITY,
    BOUNDS_MISMATCH,
}

/**
 * Severity of fusion conflict.
 */
enum class ConflictSeverity { LOW, MEDIUM, HIGH }
