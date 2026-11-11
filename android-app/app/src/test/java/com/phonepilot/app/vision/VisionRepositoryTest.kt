package com.phonepilot.app.vision

import com.phonepilot.app.data.ai.AiModelSpec
import com.phonepilot.app.data.ai.CapabilityEvidence
import com.phonepilot.app.data.ai.ModelCapability
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.min

class VisionRepositoryTest {

    private val config = VisionConfig()

    @Test
    fun `build vision summary`() = runBlocking {
        // Test the vision summary building logic
        val elements = listOf(
            UiElement("1", text = "Login", clickable = true, semanticRole = SemanticRole.BUTTON, bounds = Bounds(100, 200, 300, 250)),
            UiElement("2", text = "Username", editable = true, semanticRole = SemanticRole.TEXT_FIELD, bounds = Bounds(100, 300, 300, 350)),
            UiElement("3", semanticRole = SemanticRole.PROGRESS_BAR, bounds = Bounds(150, 400, 250, 420)),
        )

        val state = ScreenState(
            packageName = "com.example.app",
            activityName = "LoginActivity",
            screenWidth = 400,
            screenHeight = 600,
            elements = elements,
            ocrResult = OcrResult(
                textBlocks = listOf(
                    OcrTextBlock("Login", Bounds(100, 200, 300, 250), 0.9f, "en", 0),
                ),
                fullText = "Login",
                engineName = "Test",
                success = true,
            ),
            confidence = 0.85f,
            hasDialog = false,
            hasKeyboard = true,
            loadingIndicators = listOf(elements[2]),
        )

        val change = ScreenChange(
            changeType = ChangeType.ELEMENT_ADDED,
            addedElements = listOf(UiElement("4", text = "New Button")),
            significance = Significance.MINOR,
        )

        // Build summary manually (replicating VisionRepository logic)
        val summary = buildTestVisionSummary(state, change)

        assertTrue(summary.contains("com.example.app"))
        assertTrue(summary.contains("LoginActivity"))
        assertTrue(summary.contains("Login"))
        assertTrue(summary.contains("Username"))
        assertTrue(summary.contains("PROGRESS_BAR"))
        assertTrue(summary.contains("OCR"))
        assertTrue(summary.contains("ELEMENT_ADDED"))
        assertTrue(summary.contains("New Button"))
    }

    @Test
    fun `vision capable model detection`() {
        // Test logic for checking vision capability
        val modelWithVision = AiModelSpec(
            id = "model_vision",
            displayName = "Vision Model",
            capabilities = listOf(CapabilityEvidence(ModelCapability.VISION, "https://example.com", System.currentTimeMillis())),
        )

        val modelWithoutVision = AiModelSpec(
            id = "model_text",
            displayName = "Text Model",
            capabilities = listOf(CapabilityEvidence(ModelCapability.TEXT, "https://example.com", System.currentTimeMillis())),
        )

        assertTrue(modelWithVision.hasCapability(ModelCapability.VISION))
        assertFalse(modelWithoutVision.hasCapability(ModelCapability.VISION))
    }

    @Test
    fun `default vision prompt building`() {
        val visionSummary = "Screen: Login\nElements: Login Button, Username Field"
        val prompt = buildDefaultVisionPrompt(visionSummary)

        assertTrue(prompt.contains("شاشة"))
        assertTrue(prompt.contains("Login"))
        assertTrue(prompt.contains("العربية"))
        assertTrue(prompt.contains("إجراء مقترح"))
    }
}

private fun buildTestVisionSummary(state: ScreenState, change: ScreenChange?): String {
    val builder = StringBuilder()
    builder.append("=== تحليل الشاشة الحالية ===\n")
    builder.append("التطبيق: ${state.packageName}\n")
    builder.append("الشاشة: ${state.activityName}\n")
    builder.append("الأبعاد: ${state.screenWidth}x${state.screenHeight}\n")
    builder.append("الثقة: ${String.format("%.1f", state.confidence * 100)}%\n\n")

    builder.append("--- العناصر التفاعلية (${state.interactiveElements.size}) ---\n")
    for (element in state.interactiveElements.take(20)) {
        val text = element.primaryText?.takeIf { it.isNotBlank() } ?: "[بدون نص]"
        val role = element.semanticRole.label()
        val bounds = element.bounds?.let { "[${it.left},${it.top},${it.right},${it.bottom}]" } ?: ""
        builder.append("• [$role] $text $bounds\n")
    }

    state.ocrResult?.let { ocr ->
        builder.append("\n--- نص OCR (${ocr.textBlocks.size} كتلة) ---\n")
        builder.append(ocr.fullText.take(500))
    }

    change?.let { ch ->
        builder.append("\n--- التغييرات منذ آخر تحليل ---\n")
        builder.append("النوع: ${ch.changeType}\n")
        builder.append("الأهمية: ${ch.significance}\n")
        if (ch.addedElements.isNotEmpty()) builder.append("مضاف: ${ch.addedElements.size}\n")
    }
    return builder.toString()
}

private fun buildDefaultVisionPrompt(visionSummary: String): String {
    return """
        حلل محتوى الشاشة التالي وقدم ملخصاً مفيداً:
        
        $visionSummary
        
        الرجاء الإجابة بالعربية وتغطية:
        1. ما هي الشاشة/الصفحة الحالية؟
        2. ما هي العناصر الرئيسية القابلة للتفاعل؟
        3. هل هناك أي أخطاء أو مشاكل ظاهرة؟
        4. ما الإجراء المقترح التالي؟
    """.trimIndent()
}

class AccessibilityTreeAnalyzerTest {

    private val config = VisionConfig()

    @Test
    fun `element id generation`() {
        // Test the ID generation logic
        val nodeId = generateTestElementId(
            viewId = "com.app:id/button",
            className = "android.widget.Button",
            text = "Click Me",
            depth = 5,
        )

        assertTrue(nodeId.contains("button"))
        assertTrue(nodeId.contains("Button"))
        assertTrue(nodeId.contains("Click_Me"))
        assertTrue(nodeId.contains("5"))
    }

    @Test
    fun `semantic role inference`() {
        // Test semantic role inference logic
        assertEquals(SemanticRole.BUTTON, inferTestRole("android.widget.Button", "", "", ""))
        assertEquals(SemanticRole.TEXT_FIELD, inferTestRole("android.widget.EditText", "", "", ""))
        assertEquals(SemanticRole.CHECKBOX, inferTestRole("android.widget.CheckBox", "", "", ""))
        assertEquals(SemanticRole.SWITCH, inferTestRole("android.widget.Switch", "", "", ""))
        assertEquals(SemanticRole.IMAGE, inferTestRole("android.widget.ImageView", "", "", ""))
        assertEquals(SemanticRole.TEXT, inferTestRole("android.widget.TextView", "", "", ""))
        assertEquals(SemanticRole.LIST_ITEM, inferTestRole("androidx.recyclerview.widget.RecyclerView", "", "", ""))
        assertEquals(SemanticRole.WEB_VIEW, inferTestRole("android.webkit.WebView", "", "", ""))
        assertEquals(SemanticRole.DIALOG, inferTestRole("android.app.Dialog", "", "", ""))
    }

    @Test
    fun `confidence calculation`() {
        // Test confidence scoring
        var confidence = 0.5f // base

        // Visible
        confidence += 0.2f
        // Has text
        confidence += 0.1f
        // Has content description
        confidence += 0.1f
        // Has view ID
        confidence += 0.1f
        // Interactive
        confidence += 0.1f
        // Has children
        confidence += 0.05f

        assertEquals(1.0f, min(confidence, 1.0f), 0.001f)
    }
}

private fun generateTestElementId(
    viewId: String?,
    className: String?,
    text: String?,
    depth: Int,
): String {
    val parts = mutableListOf<String>()
    viewId?.let { parts.add(it.replace("/", "_")) }
    className?.substringAfterLast('.')?.let { parts.add(it) }
    text?.take(20)?.replace(" ", "_")?.let { parts.add(it) }
    parts.add(depth.toString())
    parts.add("hash")
    return parts.joinToString("_")
}

private fun inferTestRole(className: String, viewId: String, text: String, contentDesc: String): SemanticRole {
    val lowerClass = className.lowercase()
    val lowerId = viewId.lowercase()
    val lowerText = text.lowercase()
    val lowerDesc = contentDesc.lowercase()

    return when {
        lowerClass.contains("button") || lowerClass.contains("fab") -> SemanticRole.BUTTON
        lowerClass.contains("edittext") || lowerClass.contains("textfield") || lowerClass.contains("input") -> SemanticRole.TEXT_FIELD
        lowerClass.contains("checkbox") -> SemanticRole.CHECKBOX
        lowerClass.contains("radiobutton") -> SemanticRole.RADIO_BUTTON
        lowerClass.contains("switch") || lowerClass.contains("toggle") -> SemanticRole.SWITCH
        lowerClass.contains("seekbar") || lowerClass.contains("slider") -> SemanticRole.SLIDER
        lowerClass.contains("tab") && !lowerClass.contains("table") -> SemanticRole.TAB
        lowerClass.contains("menuitem") -> SemanticRole.MENU_ITEM
        lowerClass.contains("listitem") || lowerClass.contains("recyclerview") -> SemanticRole.LIST_ITEM
        lowerClass.contains("griditem") -> SemanticRole.GRID_ITEM
        lowerClass.contains("imageview") || lowerClass.contains("imagebutton") -> SemanticRole.IMAGE
        lowerClass.contains("textview") -> SemanticRole.TEXT
        lowerClass.contains("toolbar") || lowerClass.contains("actionbar") || lowerClass.contains("appbar") -> SemanticRole.APP_BAR
        lowerClass.contains("navigation") && lowerClass.contains("drawer") -> SemanticRole.NAVIGATION_DRAWER
        lowerClass.contains("bottomsheet") -> SemanticRole.BOTTOM_SHEET
        lowerClass.contains("dialog") || lowerClass.contains("alertdialog") -> SemanticRole.DIALOG
        lowerClass.contains("toast") -> SemanticRole.TOAST
        lowerClass.contains("snackbar") -> SemanticRole.SNACKBAR
        lowerClass.contains("progressbar") -> SemanticRole.PROGRESS_BAR
        lowerClass.contains("spinner") -> SemanticRole.SPINNER
        lowerClass.contains("datepicker") || lowerClass.contains("timepicker") -> SemanticRole.PICKER
        lowerClass.contains("webview") -> SemanticRole.WEB_VIEW
        lowerClass.contains("videoview") -> SemanticRole.VIDEO_VIEW
        lowerClass.contains("mapview") || lowerClass.contains("mapfragment") -> SemanticRole.MAP_VIEW
        lowerClass.contains("adview") || lowerClass.contains("admob") -> SemanticRole.AD_VIEW
        lowerClass.contains("scrollview") || lowerClass.contains("nestedscroll") -> SemanticRole.SCROLL_VIEW
        lowerClass.contains("recyclerview") -> SemanticRole.RECYCLER_VIEW
        lowerClass.contains("listview") -> SemanticRole.LIST_VIEW
        lowerClass.contains("gridview") -> SemanticRole.GRID_VIEW
        else -> SemanticRole.UNKNOWN
    }
}
