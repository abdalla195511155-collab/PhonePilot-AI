package com.phonepilot.app.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.runBlocking

class VisualContextManagerTest {

    private lateinit var contextManager: VisualContextManager
    private lateinit var stateDetector: ScreenStateDetector
    private lateinit var changeDetector: ScreenChangeDetector
    private lateinit var recognizer: UiElementRecognizer
    private val config = VisionConfig()

    @Before
    fun setup() {
        recognizer = UiElementRecognizer(config)
        // For unit tests, we create minimal mock implementations
        // Since VisualContextManager requires Context and ScreenStateDetector, we test the logic directly
    }

    @Test
    fun `context history management`() = runBlocking {
        val context = VisualContext(maxHistorySize = 3)
        val state1 = ScreenState(elements = listOf(UiElement("1")), timestamp = 1000)
        val state2 = ScreenState(elements = listOf(UiElement("2")), timestamp = 2000)
        val state3 = ScreenState(elements = listOf(UiElement("3")), timestamp = 3000)
        val state4 = ScreenState(elements = listOf(UiElement("4")), timestamp = 4000)

        var ctx = context
        for (state in listOf(state1, state2, state3, state4)) {
            val change = ScreenChange(changeType = ChangeType.NO_CHANGE, currentState = state)
            ctx = ctx.addChange(change)
        }

        assertEquals(3, ctx.recentChanges.size)
        assertEquals(4000, ctx.currentState?.timestamp)
        assertEquals(3000, ctx.previousState?.timestamp)
    }

    @Test
    fun `context clear history`() = runBlocking {
        val context = VisualContext(maxHistorySize = 10)
        val state1 = ScreenState(elements = listOf(UiElement("1")), timestamp = 1000)
        val state2 = ScreenState(elements = listOf(UiElement("2")), timestamp = 2000)

        var ctx = context
        ctx = ctx.addChange(ScreenChange(changeType = ChangeType.NO_CHANGE, currentState = state1))
        ctx = ctx.addChange(ScreenChange(changeType = ChangeType.NO_CHANGE, currentState = state2))

        assertEquals(2, ctx.recentChanges.size)

        ctx = ctx.clearHistory()

        assertEquals(0, ctx.recentChanges.size)
        assertNull(ctx.previousState)
        assertNull(ctx.currentState)
    }

    @Test
    fun `screen state derived properties`() {
        val button = UiElement("1", text = "Click", clickable = true, semanticRole = SemanticRole.BUTTON)
        val text = UiElement("2", text = "Label", semanticRole = SemanticRole.TEXT)
        val field = UiElement("3", editable = true, semanticRole = SemanticRole.TEXT_FIELD)
        val image = UiElement("4", semanticRole = SemanticRole.IMAGE)

        val state = ScreenState(elements = listOf(button, text, field, image))

        assertEquals(3, state.interactiveElements.size) // button, field, (text not interactive)
        assertEquals(2, state.textElements.size) // button, text
        assertEquals(1, state.visualElements.size) // image
    }
}

class VisionEngineIntegrationTest {

    private val config = VisionConfig()

    @Test
    fun `full analysis flow - accessibility only`() = runBlocking {
        // This test verifies the data flow without requiring Android context
        // We test the core logic by creating elements directly

        val accElements = listOf(
            UiElement("1", text = "Login", clickable = true, semanticRole = SemanticRole.BUTTON, viewIdResourceName = "btn_login"),
            UiElement("2", text = "Username", editable = true, semanticRole = SemanticRole.TEXT_FIELD, viewIdResourceName = "et_username"),
            UiElement("3", text = "Password", editable = true, isPassword = true, semanticRole = SemanticRole.TEXT_FIELD, viewIdResourceName = "et_password"),
        )

        val ocrBlocks = listOf(
            OcrTextBlock("Login", Bounds(100, 200, 300, 250), 0.9f, "en", 0),
            OcrTextBlock("Username", Bounds(100, 300, 300, 350), 0.85f, "en", 1),
        )

        val ocrResult = OcrResult(textBlocks = ocrBlocks, fullText = "Login\nUsername", engineName = "Test", success = true)

        val recognizer = UiElementRecognizer(config)
        val fusionEngine = VisionFusionEngine(config, recognizer)

        val fused = fusionEngine.fuse(accElements, ocrResult, 400, 500)

        // Should have 3 elements (2 fused, 1 unchanged)
        assertEquals(3, fused.size)

        // Check fused elements have enhanced text
        val loginElement = fused.find { it.viewIdResourceName == "btn_login" }
        assertNotNull(loginElement)
        assertEquals("Login", loginElement?.text) // Already good from accessibility

        val usernameElement = fused.find { it.viewIdResourceName == "et_username" }
        assertNotNull(usernameElement)
    }

    @Test
    fun `privacy filtering in elements`() {
        val elements = listOf(
            UiElement("1", text = "password123", viewIdResourceName = "et_password"),
            UiElement("2", text = "Normal text", viewIdResourceName = "et_normal"),
            UiElement("3", contentDescription = "PIN: 1234", viewIdResourceName = "et_pin"),
        )

        // Test privacy filter logic
        val sensitiveKeywords = setOf("password", "كلمة مرور", "pin", "رمز", "passcode")

        val filtered = elements.map { element ->
            var sanitized = element
            val textSensitive = element.text?.let { sensitiveKeywords.any { it.contains(it, ignoreCase = true) } } == true
            val descSensitive = element.contentDescription?.let { sensitiveKeywords.any { it.contains(it, ignoreCase = true) } } == true

            if (textSensitive) sanitized = sanitized.copy(text = "[MASKED]")
            if (descSensitive) sanitized = sanitized.copy(contentDescription = "[MASKED]")
            sanitized
        }

        assertEquals("[MASKED]", filtered[0].text)
        assertEquals("Normal text", filtered[1].text)
        assertEquals("[MASKED]", filtered[2].contentDescription)
    }

    @Test
    fun `element matching across states`() = runBlocking {
        val recognizer = UiElementRecognizer(config)

        val prevElements = listOf(
            UiElement("prev_1", text = "Login", clickable = true, viewIdResourceName = "btn_login", bounds = Bounds(100, 200, 300, 250)),
            UiElement("prev_2", text = "Register", clickable = true, viewIdResourceName = "btn_register", bounds = Bounds(100, 300, 300, 350)),
        )

        val currElements = listOf(
            UiElement("curr_1", text = "Log In", clickable = true, viewIdResourceName = "btn_login", bounds = Bounds(100, 200, 300, 250)), // Same ID, text changed
            UiElement("curr_2", text = "Register", clickable = true, viewIdResourceName = "btn_register", bounds = Bounds(100, 300, 300, 350)), // Same
            UiElement("curr_3", text = "Forgot Password", clickable = true, viewIdResourceName = "btn_forgot", bounds = Bounds(100, 400, 300, 450)), // New
        )

        val matches = recognizer.matchElements(prevElements, currElements)

        // Should match 2 by view ID, 1 added
        assertEquals(2, matches.size)
        assertEquals(1, matches.count { it.matchType == MatchType.EXACT_VIEW_ID })

        // Detect ambiguous - none expected here
        val ambiguous = recognizer.detectAmbiguousElements(prevElements, currElements)
        assertTrue(ambiguous.isEmpty())
    }

    @Test
    fun `change detection with multiple changes`() = runBlocking {
        val recognizer = UiElementRecognizer(config)
        val changeDetector = ScreenChangeDetector(config, recognizer)

        val prev = ScreenState(
            packageName = "com.app",
            activityName = "MainActivity",
            elements = listOf(
                UiElement("1", text = "A", viewIdResourceName = "id_a"),
                UiElement("2", text = "B", viewIdResourceName = "id_b"),
            ),
        )

        val curr = ScreenState(
            packageName = "com.app",
            activityName = "MainActivity",
            elements = listOf(
                UiElement("1", text = "A Modified", viewIdResourceName = "id_a"), // Text changed
                UiElement("3", text = "C", viewIdResourceName = "id_c"), // Added
                // id_b removed
            ),
        )

        val change = changeDetector.detectChanges(prev, curr)

        assertEquals(ChangeType.STATE_CHANGED, change.changeType)
        assertEquals(Significance.MODERATE, change.significance)
        assertEquals(1, change.addedElements.size)
        assertEquals(1, change.removedElements.size)
        assertEquals(1, change.changedElements.size)
        assertTrue(ChangeFlag.TEXT_CHANGED in change.changedElements[0].changeFlags)
    }
}
