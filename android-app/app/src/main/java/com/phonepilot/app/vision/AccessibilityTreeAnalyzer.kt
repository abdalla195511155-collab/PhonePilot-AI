package com.phonepilot.app.vision

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.phonepilot.app.privacy.PrivacyFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

/**
 * Analyzes the accessibility tree and extracts structured UI elements.
 *
 * Responsibilities:
 * - Traverse the accessibility node hierarchy
 * - Extract relevant properties from each node
 * - Filter out noise and non-useful elements
 * - Build parent-child relationships
 * - Apply privacy filtering
 * - Properly recycle AccessibilityNodeInfo objects
 */
class AccessibilityTreeAnalyzer(
    private val context: Context,
    private val config: VisionConfig = VisionConfig(),
) {

    private val TAG = "AccessibilityTreeAnalyzer"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Reusable objects to reduce allocations
    private val rect = Rect()
    private val nodeStack = mutableListOf<Pair<AccessibilityNodeInfo, Int>>()
    private val visitedNodes = mutableSetOf<AccessibilityNodeInfo>()

    /**
     * Analyze the current accessibility tree from the root node.
     * Returns a list of UiElement objects representing the screen.
     */
    fun analyze(rootNode: AccessibilityNodeInfo?): List<UiElement> {
        if (rootNode == null) return emptyList()

        val elements = mutableListOf<UiElement>()
        nodeStack.clear()
        visitedNodes.clear()

        try {
            // Iterative DFS to avoid stack overflow on deep trees
            nodeStack.add(rootNode to 0)

            while (nodeStack.isNotEmpty()) {
                val (node, depth) = nodeStack.removeAt(nodeStack.lastIndex)

                // Skip if already visited or depth exceeds limit
                if (depth > 50 || visitedNodes.contains(node)) {
                    recycleSafely(node)
                    continue
                }
                visitedNodes.add(node)

                // Skip if we've hit the element limit
                if (elements.size >= config.maxElementsPerScreen) {
                    recycleSafely(node)
                    continue
                }

                // Check if node should be included
                if (shouldIncludeNode(node)) {
                    val element = extractElement(node, depth)
                    if (element != null) {
                        elements.add(element)
                    }
                }

                // Add children to stack (reverse order for correct traversal)
                val childCount = node.childCount
                for (i in childCount - 1 downTo 0) {
                    node.getChild(i)?.let { child ->
                        nodeStack.add(child to (depth + 1))
                    }
                }

                // Recycle the node after processing
                recycleSafely(node)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing accessibility tree", e)
            // Ensure all nodes are recycled on error
            visitedNodes.forEach { recycleSafely(it) }
            nodeStack.forEach { (node, _) -> recycleSafely(node) }
        }

        // Post-process: build hierarchy relationships
        buildHierarchy(elements)

        // Apply privacy filter if enabled
        if (config.enablePrivacyFilter) {
            applyPrivacyFilter(elements)
        }

        // Remove duplicates and low-confidence elements
        val filtered = filterElements(elements)

        Log.d(TAG, "Analyzed ${filtered.size} elements from accessibility tree")
        return filtered
    }

    /**
     * Determine if a node should be included in the analysis.
     */
    private fun shouldIncludeNode(node: AccessibilityNodeInfo): Boolean {
        // Skip invisible nodes unless they might be important
        if (!node.isVisibleToUser) {
            // Still include if it's a container that might have visible children
            return node.childCount > 0
        }

        // Skip very small nodes (likely decorative)
        val hasBounds = node.getBoundsInScreen(rect)
        if (hasBounds && rect.width() < 4 && rect.height() < 4) return false

        return true
    }

    /**
     * Extract UiElement from AccessibilityNodeInfo.
     */
    private fun extractElement(node: AccessibilityNodeInfo, depth: Int): UiElement? {
        val bounds = Bounds.fromAccessibilityNodeInfo(node)
        val className = node.className?.toString()
        val viewId = node.viewIdResourceName
        val text = node.text?.toString()?.takeIf { it.isNotBlank() }
        val contentDesc = node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
        val packageName = node.packageName?.toString()

        // Skip nodes with no useful information
        if (bounds == null && text == null && contentDesc == null && viewId == null) {
            return null
        }

        val elementId = generateElementId(node, depth)

        return UiElement(
            elementId = elementId,
            text = text,
            contentDescription = contentDesc,
            elementType = className?.substringAfterLast('.') ?: className,
            className = className,
            viewIdResourceName = viewId,
            bounds = bounds,
            clickable = node.isClickable,
            longClickable = node.isLongClickable,
            focusable = node.isFocusable,
            editable = node.isEditable,
            scrollable = node.isScrollable,
            selected = node.isSelected,
            checked = node.isChecked,
            enabled = node.isEnabled,
            visible = node.isVisibleToUser,
            isPassword = node.isPassword,
            confidence = calculateConfidence(node),
            source = ElementSource.ACCESSIBILITY,
            semanticRole = inferSemanticRole(node),
            depth = depth,
        )
    }

    /**
     * Generate a stable element ID based on node properties.
     */
    private fun generateElementId(node: AccessibilityNodeInfo, depth: Int): String {
        val parts = mutableListOf<String>()
        node.viewIdResourceName?.let { parts.add(it.replace("/", "_")) }
        node.className?.toString()?.substringAfterLast('.')?.let { parts.add(it) }
        node.text?.toString()?.take(20)?.replace(" ", "_")?.let { parts.add(it) }
        node.contentDescription?.toString()?.take(20)?.replace(" ", "_")?.let { parts.add(it) }
        parts.add(depth.toString())
        parts.add(node.hashCode().toString())
        return parts.joinToString("_")
    }

    /**
     * Calculate confidence score for the element.
     */
    private fun calculateConfidence(node: AccessibilityNodeInfo): Float {
        var confidence = 0.5f

        if (node.isVisibleToUser) confidence += 0.2f
        if (node.text?.isNotBlank() == true) confidence += 0.1f
        if (node.contentDescription?.isNotBlank() == true) confidence += 0.1f
        if (node.viewIdResourceName?.isNotBlank() == true) confidence += 0.1f
        if (node.isClickable || node.isLongClickable || node.isFocusable || node.isEditable) confidence += 0.1f
        if (node.childCount > 0) confidence += 0.05f

        return min(confidence, 1.0f)
    }

    /**
     * Infer semantic role from node properties.
     */
    private fun inferSemanticRole(node: AccessibilityNodeInfo): SemanticRole {
        val className = node.className?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""

        // Check class name patterns
        return when {
            className.contains("button") || className.contains("fab") -> SemanticRole.BUTTON
            className.contains("edittext") || className.contains("textfield") || className.contains("input") -> SemanticRole.TEXT_FIELD
            className.contains("checkbox") -> SemanticRole.CHECKBOX
            className.contains("radiobutton") -> SemanticRole.RADIO_BUTTON
            className.contains("switch") || className.contains("toggle") -> SemanticRole.SWITCH
            className.contains("seekbar") || className.contains("slider") -> SemanticRole.SLIDER
            className.contains("tab") && !className.contains("table") -> SemanticRole.TAB
            className.contains("menuitem") -> SemanticRole.MENU_ITEM
            className.contains("listitem") || className.contains("recyclerview") && node.isClickable -> SemanticRole.LIST_ITEM
            className.contains("griditem") -> SemanticRole.GRID_ITEM
            className.contains("imageview") || className.contains("imagebutton") -> SemanticRole.IMAGE
            className.contains("textview") && !node.isClickable -> SemanticRole.TEXT
            className.contains("toolbar") || className.contains("actionbar") || className.contains("appbar") -> SemanticRole.APP_BAR
            className.contains("navigation") && className.contains("drawer") -> SemanticRole.NAVIGATION_DRAWER
            className.contains("bottomsheet") -> SemanticRole.BOTTOM_SHEET
            className.contains("dialog") || className.contains("alertdialog") -> SemanticRole.DIALOG
            className.contains("toast") -> SemanticRole.TOAST
            className.contains("snackbar") -> SemanticRole.SNACKBAR
            className.contains("progressbar") -> SemanticRole.PROGRESS_BAR
            className.contains("spinner") -> SemanticRole.SPINNER
            className.contains("datepicker") || className.contains("timepicker") -> SemanticRole.PICKER
            className.contains("webview") -> SemanticRole.WEB_VIEW
            className.contains("videoview") -> SemanticRole.VIDEO_VIEW
            className.contains("mapview") || className.contains("mapfragment") -> SemanticRole.MAP_VIEW
            className.contains("adview") || className.contains("admob") -> SemanticRole.AD_VIEW
            className.contains("scrollview") || className.contains("nestedscroll") -> SemanticRole.SCROLL_VIEW
            className.contains("recyclerview") -> SemanticRole.RECYCLER_VIEW
            className.contains("listview") -> SemanticRole.LIST_VIEW
            className.contains("gridview") -> SemanticRole.GRID_VIEW
            node.isClickable -> SemanticRole.BUTTON
            node.isEditable -> SemanticRole.TEXT_FIELD
            node.childCount > 0 -> SemanticRole.UNKNOWN_CONTAINER
            else -> SemanticRole.UNKNOWN
        }
    }

    /**
     * Build parent-child relationships between elements.
     */
    private fun buildHierarchy(elements: List<UiElement>) {
        val elementMap = elements.associateBy { it.elementId }

        // For each element, find its parent and children based on bounds containment
        elements.forEach { element ->
            if (element.bounds == null) return@forEach

            var bestParent: UiElement? = null
            var bestParentArea = Int.MAX_VALUE

            elements.forEach { potentialParent ->
                if (potentialParent === element) return@forEach
                if (potentialParent.bounds == null) return@forEach
                if (potentialParent.depth >= element.depth) return@forEach

                if (potentialParent.bounds!!.contains(element.bounds!!)) {
                    val area = potentialParent.bounds!!.width() * potentialParent.bounds!!.height()
                    if (area < bestParentArea) {
                        bestParent = potentialParent
                        bestParentArea = area
                    }
                }
            }

            if (bestParent != null) {
                val foundParent = bestParent
                val parentId = foundParent.elementId
                val childIds = foundParent.childrenIds + element.elementId
                val index = elements.indexOf(foundParent)
                if (index >= 0) {
                    // Update parent's children list
                    val updatedParent = foundParent.copy(childrenIds = childIds)
                    elements[index] = updatedParent
                }
                // Update child's parent reference
                val childIndex = elements.indexOf(element)
                if (childIndex >= 0) {
                    elements[childIndex] = element.copy(parentId = parentId)
                }
            }
        }
    }

    /**
     * Apply privacy filtering to elements.
     */
    private fun applyPrivacyFilter(elements: List<UiElement>) {
        val packageName = elements.firstOrNull()?.className?.let { 
            // Try to extract package name from class name or context
            context.packageName 
        } ?: context.packageName

        elements.forEach { element ->
            if (element.isPassword) return@forEach

            val textSensitive = element.text?.let { PrivacyFilter.isSensitiveText(it) } == true
            val descSensitive = element.contentDescription?.let { PrivacyFilter.isSensitiveText(it) } == true
            val pkgSensitive = PrivacyFilter.isSensitivePackage(packageName)

            if (textSensitive || pkgSensitive) {
                val index = elements.indexOf(element)
                if (index >= 0) {
                    elements[index] = element.copy(text = "[MASKED]")
                }
            }
            if (descSensitive || pkgSensitive) {
                val index = elements.indexOf(element)
                if (index >= 0) {
                    elements[index] = element.copy(contentDescription = "[MASKED]")
                }
            }
        }
    }

    /**
     * Filter out duplicate and low-quality elements.
     */
    private fun filterElements(elements: List<UiElement>): List<UiElement> {
        val filtered = mutableListOf<UiElement>()
        val seenBounds = mutableSetOf<String>()

        elements
            .filter { it.confidence >= config.minConfidenceThreshold }
            .sortedByDescending { it.confidence }
            .forEach { element ->
                val boundsKey = element.bounds?.let { "${it.left},${it.top},${it.right},${it.bottom}" } ?: ""
                
                // Skip if we've seen very similar bounds (likely duplicate)
                val isDuplicate = seenBounds.any { existing ->
                    val parts = existing.split(",")
                    if (parts.size == 4) {
                        val existingBounds = Bounds(
                            parts[0].toInt(), parts[1].toInt(),
                            parts[2].toInt(), parts[3].toInt()
                        )
                        element.bounds?.iou(existingBounds) ?: 0f > 0.9f
                    } else false
                }

                if (!isDuplicate) {
                    filtered.add(element)
                    if (boundsKey.isNotBlank()) seenBounds.add(boundsKey)
                }
            }

        return filtered
    }

    /**
     * Safely recycle an AccessibilityNodeInfo.
     */
    private fun recycleSafely(node: AccessibilityNodeInfo?) {
        node?.recycle()
    }

    /**
     * Shutdown the analyzer.
     */
    fun shutdown() {
        scope.cancel()
    }
}
