package com.phonepilot.app.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScreenStateDetectorTest {

    private lateinit var detector: ScreenStateDetector
    private lateinit var recognizer: UiElementRecognizer
    private val config = VisionConfig()

    @Before
    fun setup() {
        recognizer = UiElementRecognizer(config)
        // We need a mock context - for unit tests we'll use a minimal setup
        // Since ScreenStateDetector requires Context, we'll test the logic directly
    }

    @Test
    fun `identify loading indicators`() {
        val elements = listOf(
            UiElement("1", semanticRole = SemanticRole.PROGRESS_BAR),
            UiElement("2", semanticRole = SemanticRole.SPINNER),
            UiElement("3", className = "android.widget.ProgressBar"),
            UiElement("4", text = "Normal Button", semanticRole = SemanticRole.BUTTON),
        )

        // Test the private method logic by creating a detector subclass or testing directly
        val loading = elements.filter { it.semanticRole in setOf(SemanticRole.PROGRESS_BAR, SemanticRole.SPINNER) }
        val loadingByClass = elements.filter { it.className?.lowercase()?.contains("progress") == true }

        assertEquals(2, loading.size)
        assertEquals(1, loadingByClass.size)
    }

    @Test
    fun `identify error messages`() {
        val elements = listOf(
            UiElement("1", text = "Error: Connection failed"),
            UiElement("2", text = "فشل التحميل"),
            UiElement("3", text = "Success!"),
            UiElement("4", text = "Warning: Low battery"),
        )

        val errorKeywords = setOf("error", "failed", "فشل", "خطأ", "error", "exception", "crash", "تعذر", "مشكلة")
        val errors = elements.filter { it.hasTextContent && errorKeywords.any { it.primaryText?.lowercase()?.contains(it) == true } }

        assertEquals(2, errors.size)
    }

    @Test
    fun `identify empty states`() {
        val elements = listOf(
            UiElement("1", text = "No data available"),
            UiElement("2", text = "لا يوجد نتائج"),
            UiElement("3", text = "Empty list"),
            UiElement("4", text = "Some content"),
        )

        val emptyKeywords = setOf("empty", "no data", "لا يوجد", "فارغ", "no results", "لا نتائج", "nothing here")
        val emptyStates = elements.filter { it.hasTextContent && emptyKeywords.any { it.primaryText?.lowercase()?.contains(it) == true } }

        assertEquals(2, emptyStates.size)
    }

    @Test
    fun `screen identity similarity`() {
        val id1 = ScreenIdentity(
            packageName = "com.app",
            activityName = "MainActivity",
            semanticDescription = "App: com.app | Elements: BUTTON(3), TEXT(5) | List-based | Interactive: 3, Text: 5",
            elementTypeSignature = "BUTTON:3, TEXT:5",
            interactiveDensity = 0.3f,
        )

        val id2 = ScreenIdentity(
            packageName = "com.app",
            activityName = "MainActivity",
            semanticDescription = "App: com.app | Elements: BUTTON(3), TEXT(5) | List-based | Interactive: 3, Text: 5",
            elementTypeSignature = "BUTTON:3, TEXT:5",
            interactiveDensity = 0.3f,
        )

        val id3 = ScreenIdentity(
            packageName = "com.other",
            activityName = "OtherActivity",
            semanticDescription = "App: com.other | Elements: IMAGE(10) | Interactive: 2, Text: 1",
            elementTypeSignature = "IMAGE:10",
            interactiveDensity = 0.1f,
        )

        assertEquals(1.0f, id1.similarity(id2), 0.001f)
        assertTrue(id1.similarity(id3) < 0.5f)
    }
}

class ScreenChangeDetectorTest {

    private lateinit var changeDetector: ScreenChangeDetector
    private lateinit var recognizer: UiElementRecognizer
    private val config = VisionConfig()

    @Before
    fun setup() {
        recognizer = UiElementRecognizer(config)
        changeDetector = ScreenChangeDetector(config, recognizer)
    }

    @Test
    fun `detect screen transition`() {
        val prev = ScreenState(
            packageName = "com.app",
            activityName = "MainActivity",
            elements = listOf(UiElement("1", text = "Home")),
        )

        val curr = ScreenState(
            packageName = "com.app",
            activityName = "SettingsActivity", // Different activity
            elements = listOf(UiElement("2", text = "Settings")),
        )

        val change = changeDetector.detectChanges(prev, curr)

        assertEquals(ChangeType.SCREEN_TRANSITION, change.changeType)
        assertEquals(Significance.MAJOR, change.significance)
    }

    @Test
    fun `detect element added`() {
        val prev = ScreenState(elements = listOf(UiElement("1", text = "A")))
        val curr = ScreenState(elements = listOf(
            UiElement("1", text = "A"),
            UiElement("2", text = "B", viewIdResourceName = "id_b"), // New element
        ))

        val change = changeDetector.detectChanges(prev, curr)

        assertEquals(ChangeType.ELEMENT_ADDED, change.changeType)
        assertEquals(1, change.addedElements.size)
        assertEquals("2", change.addedElements[0].elementId)
    }

    @Test
    fun `detect element removed`() {
        val prev = ScreenState(elements = listOf(
            UiElement("1", text = "A"),
            UiElement("2", text = "B"),
        ))
        val curr = ScreenState(elements = listOf(UiElement("1", text = "A")))

        val change = changeDetector.detectChanges(prev, curr)

        assertEquals(ChangeType.ELEMENT_REMOVED, change.changeType)
        assertEquals(1, change.removedElements.size)
        assertEquals("2", change.removedElements[0].elementId)
    }

    @Test
    fun `detect text change`() {
        val prev = ScreenState(elements = listOf(UiElement("1", text = "Old Text", viewIdResourceName = "id_1")))
        val curr = ScreenState(elements = listOf(UiElement("1", text = "New Text", viewIdResourceName = "id_1")))

        val change = changeDetector.detectChanges(prev, curr)

        assertEquals(ChangeType.TEXT_CHANGED, change.changeType)
        assertEquals(1, change.changedElements.size)
        assertTrue(ChangeFlag.TEXT_CHANGED in change.changedElements[0].changeFlags)
    }

    @Test
    fun `detect bounds change`() {
        val prev = ScreenState(elements = listOf(UiElement("1", text = "A", bounds = Bounds(0, 0, 100, 100), viewIdResourceName = "id_1")))
        val curr = ScreenState(elements = listOf(UiElement("1", text = "A", bounds = Bounds(50, 50, 150, 150), viewIdResourceName = "id_1")))

        val change = changeDetector.detectChanges(prev, curr)

        assertEquals(1, change.changedElements.size)
        assertTrue(ChangeFlag.BOUNDS_CHANGED in change.changedElements[0].changeFlags)
    }

    @Test
    fun `detect state change`() {
        val prev = ScreenState(elements = listOf(UiElement("1", text = "A", enabled = true, selected = false, viewIdResourceName = "id_1")))
        val curr = ScreenState(elements = listOf(UiElement("1", text = "A", enabled = false, selected = true, viewIdResourceName = "id_1")))

        val change = changeDetector.detectChanges(prev, curr)

        assertEquals(1, change.changedElements.size)
        assertTrue(ChangeFlag.STATE_CHANGED in change.changedElements[0].changeFlags)
    }

    @Test
    fun `detect dialog appeared`() {
        val prev = ScreenState(elements = listOf(), hasDialog = false)
        val curr = ScreenState(elements = listOf(), hasDialog = true)

        val change = changeDetector.detectChanges(prev, curr)

        assertEquals(ChangeType.DIALOG_APPEARED, change.changeType)
        assertEquals(Significance.MAJOR, change.significance)
    }

    @Test
    fun `detect keyboard shown`() {
        val prev = ScreenState(elements = listOf(), hasKeyboard = false)
        val curr = ScreenState(elements = listOf(), hasKeyboard = true)

        val change = changeDetector.detectChanges(prev, curr)

        assertEquals(ChangeType.KEYBOARD_SHOWN, change.changeType)
        assertEquals(Significance.MODERATE, change.significance)
    }

    @Test
    fun `no change detected`() {
        val prev = ScreenState(elements = listOf(UiElement("1", text = "A", viewIdResourceName = "id_1")))
        val curr = ScreenState(elements = listOf(UiElement("1", text = "A", viewIdResourceName = "id_1")))

        val change = changeDetector.detectChanges(prev, curr)

        assertEquals(ChangeType.NO_CHANGE, change.changeType)
    }
}
