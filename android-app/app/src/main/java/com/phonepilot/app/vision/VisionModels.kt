package com.phonepilot.app.vision

import android.graphics.Rect
import android.os.Parcel
import android.os.Parcelable
import kotlin.math.max
import kotlin.math.min
import kotlinx.serialization.Serializable

/** Source of element information */
enum class ElementSource { ACCESSIBILITY, OCR, FUSED }

/** Semantic role of a UI element */
enum class SemanticRole {
    UNKNOWN,
    BUTTON,
    TEXT_FIELD,
    CHECKBOX,
    RADIO_BUTTON,
    SWITCH,
    SLIDER,
    TAB,
    MENU_ITEM,
    LIST_ITEM,
    GRID_ITEM,
    IMAGE,
    TEXT,
    LABEL,
    HEADER,
    FOOTER,
    DIALOG,
    TOAST,
    SNACKBAR,
    PROGRESS_BAR,
    SEEK_BAR,
    SPINNER,
    PICKER,
    NAVIGATION_DRAWER,
    APP_BAR,
    TAB_BAR,
    BOTTOM_SHEET,
    MODAL,
    POPOVER,
    TOOLTIP,
    SCROLL_VIEW,
    RECYCLER_VIEW,
    LIST_VIEW,
    GRID_VIEW,
    WEB_VIEW,
    VIDEO_VIEW,
    MAP_VIEW,
    AD_VIEW,
    UNKNOWN_CONTAINER
}

/** Unified UI element model combining accessibility and OCR data */
@Serializable
data class UiElement(
    val elementId: String,
    val text: String? = null,
    val contentDescription: String? = null,
    val elementType: String? = null,
    val className: String? = null,
    val viewIdResourceName: String? = null,
    val bounds: Bounds? = null,
    val clickable: Boolean = false,
    val longClickable: Boolean = false,
    val focusable: Boolean = false,
    val editable: Boolean = false,
    val scrollable: Boolean = false,
    val selected: Boolean = false,
    val checked: Boolean = false,
    val enabled: Boolean = true,
    val visible: Boolean = true,
    val isPassword: Boolean = false,
    val confidence: Float = 1.0f,
    val source: ElementSource = ElementSource.ACCESSIBILITY,
    val semanticRole: SemanticRole = SemanticRole.UNKNOWN,
    val parentId: String? = null,
    val childrenIds: List<String> = emptyList(),
    val depth: Int = 0,
) {
    /** Center point of the element bounds */
    val centerX: Int get() = bounds?.centerX() ?: 0
    val centerY: Int get() = bounds?.centerY() ?: 0

    /** Width and height of the element */
    val width: Int get() = bounds?.width() ?: 0
    val height: Int get() = bounds?.height() ?: 0

    /** Whether this element has any text content (text or contentDescription) */
    val hasTextContent: Boolean get() = text?.isNotBlank() == true || contentDescription?.isNotBlank() == true

    /** Primary text for matching - prefers text over contentDescription */
    val primaryText: String? get() = text?.takeIf { it.isNotBlank() } ?: contentDescription?.takeIf { it.isNotBlank() }
}

/** Bounds in screen coordinates */
@Serializable
data class Bounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    fun centerX() = (left + right) / 2
    fun centerY() = (top + bottom) / 2
    fun width() = right - left
    fun height() = bottom - top

    /** Check if this bounds intersects with another */
    fun intersects(other: Bounds): Boolean {
        return left < other.right && right > other.left &&
               top < other.bottom && bottom > other.top
    }

    /** Check if this bounds contains a point */
    fun contains(x: Int, y: Int): Boolean {
        return x >= left && x < right && y >= top && y < bottom
    }

    /** Check if this bounds contains another bounds */
    fun contains(other: Bounds): Boolean {
        return left <= other.left && right >= other.right &&
               top <= other.top && bottom >= other.bottom
    }

    /** Intersection over Union (IoU) with another bounds */
    fun iou(other: Bounds): Float {
        val interLeft = max(left, other.left)
        val interTop = max(top, other.top)
        val interRight = min(right, other.right)
        val interBottom = min(bottom, other.bottom)

        if (interLeft >= interRight || interTop >= interBottom) return 0f

        val intersection = (interRight - interLeft) * (interBottom - interTop)
        val area1 = width() * height()
        val area2 = other.width() * other.height()
        val union = area1 + area2 - intersection

        return if (union > 0) intersection.toFloat() / union else 0f
    }

    companion object {
        fun fromRect(rect: Rect): Bounds = Bounds(rect.left, rect.top, rect.right, rect.bottom)
        fun fromAccessibilityNodeInfo(node: android.view.accessibility.AccessibilityNodeInfo): Bounds? {
            val rect = Rect()
            return if (node.getBoundsInScreen(rect)) Bounds.fromRect(rect) else null
        }
    }
}

/** OCR result for a text block */
@Serializable
data class OcrTextBlock(
    val text: String,
    val bounds: Bounds,
    val confidence: Float,
    val language: String? = null,
    val readingOrder: Int = 0,
)

/** OCR engine result */
@Serializable
data class OcrResult(
    val textBlocks: List<OcrTextBlock> = emptyList(),
    val fullText: String = "",
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val processingTimeMs: Long = 0,
    val engineName: String = "",
    val success: Boolean = true,
    val errorMessage: String? = null,
) {
    val hasText: Boolean get() = textBlocks.isNotEmpty()
}

/** Screen state snapshot */
@Serializable
data class ScreenState(
    val packageName: String? = null,
    val activityName: String? = null,
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
    val elements: List<UiElement> = emptyList(),
    val ocrResult: OcrResult? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val confidence: Float = 0.0f,
    val hasKeyboard: Boolean = false,
    val hasDialog: Boolean = false,
    val loadingIndicators: List<UiElement> = emptyList(),
    val errorMessages: List<String> = emptyList(),
    val emptyStates: List<UiElement> = emptyList(),
) {
    /** Interactive elements (clickable, focusable, editable) */
    val interactiveElements: List<UiElement> get() = elements.filter {
        it.clickable || it.longClickable || it.focusable || it.editable
    }

    /** Text elements */
    val textElements: List<UiElement> get() = elements.filter { it.hasTextContent }

    /** Elements with Vision capability evidence (images, icons) */
    val visualElements: List<UiElement> get() = elements.filter {
        it.semanticRole in setOf(SemanticRole.IMAGE, SemanticRole.VIDEO_VIEW, SemanticRole.MAP_VIEW, SemanticRole.AD_VIEW)
    }
}

/** Screen change detection result */
@Serializable
data class ScreenChange(
    val changeType: ChangeType,
    val addedElements: List<UiElement> = emptyList(),
    val removedElements: List<UiElement> = emptyList(),
    val changedElements: List<ElementChange> = emptyList(),
    val previousState: ScreenState? = null,
    val currentState: ScreenState? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val significance: Significance = Significance.MINOR,
) {
    val hasSignificantChange: Boolean get() = significance != Significance.MINOR
}

/** Type of screen change */
enum class ChangeType {
    ELEMENT_ADDED,
    ELEMENT_REMOVED,
    ELEMENT_CHANGED,
    TEXT_CHANGED,
    STATE_CHANGED,
    SCREEN_TRANSITION,
    DIALOG_APPEARED,
    DIALOG_DISMISSED,
    KEYBOARD_SHOWN,
    KEYBOARD_HIDDEN,
    ORIENTATION_CHANGED,
    SCROLL,
    NO_CHANGE,
}

/** Significance of change */
enum class Significance { MINOR, MODERATE, MAJOR }

/** Detailed element change */
@Serializable
data class ElementChange(
    val elementId: String,
    val previousElement: UiElement?,
    val currentElement: UiElement?,
    val changeFlags: Set<ChangeFlag> = emptySet(),
)

/** Flags for what changed in an element */
enum class ChangeFlag {
    TEXT_CHANGED,
    BOUNDS_CHANGED,
    STATE_CHANGED, // enabled, selected, checked, etc.
    VISIBILITY_CHANGED,
    HIERARCHY_CHANGED,
}

/** Visual context manager state */
@Serializable
data class VisualContext(
    val currentState: ScreenState? = null,
    val previousState: ScreenState? = null,
    val recentChanges: List<ScreenChange> = emptyList(),
    val maxHistorySize: Int = 10,
    val lastAnalysisTime: Long = 0,
    val isAnalyzing: Boolean = false,
) {
    fun addChange(change: ScreenChange): VisualContext {
        val newChanges = (recentChanges + change).takeLast(maxHistorySize)
        return copy(
            previousState = currentState,
            currentState = change.currentState,
            recentChanges = newChanges,
            lastAnalysisTime = change.timestamp,
        )
    }

    fun clearHistory(): VisualContext = copy(
        recentChanges = emptyList(),
        previousState = null,
    )
}

/** Vision engine configuration */
data class VisionConfig(
    val enableAccessibilityAnalysis: Boolean = true,
    val enableOcr: Boolean = true,
    val enableFusion: Boolean = true,
    val ocrLanguageHints: List<String> = listOf("en"),
    val captureIntervalMs: Long = 1000,
    val maxElementsPerScreen: Int = 500,
    val maxOcrTextBlocks: Int = 200,
    val minConfidenceThreshold: Float = 0.3f,
    val enablePrivacyFilter: Boolean = true,
    val sensitivePackages: Set<String> = setOf(),
    val sensitiveKeywords: Set<String> = setOf(),
    val retainScreenshots: Boolean = false,
) {
    /** Languages explicitly NOT supported by the current OCR engine (ML Kit Latin script). */
    val unsupportedOcrLanguages: Set<String> = setOf("ar", "fa", "ur", "he")
    
    /** Returns true if the given language is supported by the current OCR engine. */
    fun isLanguageSupported(language: String): Boolean {
        return language.toLowerCase() !in unsupportedOcrLanguages
    }
}

/** Human-readable label for a SemanticRole. */
internal fun SemanticRole.label(): String = when (this) {
    SemanticRole.BUTTON -> "زر"
    SemanticRole.TEXT_FIELD -> "حقل نص"
    SemanticRole.CHECKBOX -> "مربع اختيار"
    SemanticRole.RADIO_BUTTON -> "زر راديو"
    SemanticRole.SWITCH -> "مفتاح"
    SemanticRole.SLIDER -> "شريط تمرير"
    SemanticRole.TAB -> "تبويب"
    SemanticRole.MENU_ITEM -> "عنصر قائمة"
    SemanticRole.LIST_ITEM -> "عنصر قائمة"
    SemanticRole.GRID_ITEM -> "عنصر شبكة"
    SemanticRole.IMAGE -> "صورة"
    SemanticRole.TEXT -> "نص"
    SemanticRole.LABEL -> "تسمية"
    SemanticRole.HEADER -> "رأس"
    SemanticRole.FOOTER -> "تذييل"
    SemanticRole.DIALOG -> "حوار"
    SemanticRole.TOAST -> "تنبيه"
    SemanticRole.SNACKBAR -> "سناكبار"
    SemanticRole.PROGRESS_BAR -> "شريط تقدم"
    SemanticRole.SEEK_BAR -> "شريط بحث"
    SemanticRole.SPINNER -> "محمل"
    SemanticRole.PICKER -> "منتقي"
    SemanticRole.NAVIGATION_DRAWER -> "درج تنقل"
    SemanticRole.APP_BAR -> "شريط تطبيق"
    SemanticRole.TAB_BAR -> "شريط تبويبات"
    SemanticRole.BOTTOM_SHEET -> "ورقة سفلية"
    SemanticRole.MODAL -> "مودال"
    SemanticRole.POPOVER -> "بوبأوفر"
    SemanticRole.TOOLTIP -> "تلميح"
    SemanticRole.SCROLL_VIEW -> "عرض تمرير"
    SemanticRole.RECYCLER_VIEW -> "ريسيكلر فيو"
    SemanticRole.LIST_VIEW -> "ليست فيو"
    SemanticRole.GRID_VIEW -> "غريد فيو"
    SemanticRole.WEB_VIEW -> "ويب فيو"
    SemanticRole.VIDEO_VIEW -> "فيديو فيو"
    SemanticRole.MAP_VIEW -> "خريطة"
    SemanticRole.AD_VIEW -> "إعلان"
    SemanticRole.UNKNOWN_CONTAINER -> "حاوية"
    SemanticRole.UNKNOWN -> "غير معروف"
}
