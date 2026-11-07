package com.phonepilot.app.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UiElementRecognizerTest {

    private lateinit var recognizer: UiElementRecognizer

    @Before
    fun setup() {
        recognizer = UiElementRecognizer()
    }

    @Test
    fun `exact view id match`() {
        val prev = UiElement("prev_1", viewIdResourceName = "com.app:id/button", text = "Old", clickable = true)
        val curr = UiElement("curr_1", viewIdResourceName = "com.app:id/button", text = "New", clickable = true)

        val matches = recognizer.matchElements(listOf(prev), listOf(curr))

        assertEquals(1, matches.size)
        assertEquals(MatchType.EXACT_VIEW_ID, matches[0].matchType)
        assertEquals(1.0f, matches[0].confidence, 0.001f)
    }

    @Test
    fun `text similarity matching`() {
        val prev = UiElement("prev_1", text = "Submit Form", clickable = true, semanticRole = SemanticRole.BUTTON)
        val curr = UiElement("curr_1", text = "Submit Form", clickable = true, semanticRole = SemanticRole.BUTTON)

        val matches = recognizer.matchElements(listOf(prev), listOf(curr))

        assertEquals(1, matches.size)
        assertTrue(matches[0].confidence > 0.7f)
    }

    @Test
    fun `fuzzy text matching`() {
        val prev = UiElement("prev_1", text = "Submit", clickable = true)
        val curr = UiElement("curr_1", text = "Submit Form", clickable = true)

        val matches = recognizer.matchElements(listOf(prev), listOf(curr))

        assertEquals(1, matches.size)
        assertTrue(matches[0].confidence > 0.5f)
    }

    @Test
    fun `no match for completely different elements`() {
        val prev = UiElement("prev_1", text = "Login", clickable = true, semanticRole = SemanticRole.BUTTON)
        val curr = UiElement("curr_1", text = "Settings", clickable = true, semanticRole = SemanticRole.BUTTON)

        val matches = recognizer.matchElements(listOf(prev), listOf(curr))

        // Should not match with high confidence
        assertTrue(matches.isEmpty() || matches[0].confidence < 0.5f)
    }

    @Test
    fun `bounds overlap matching`() {
        val prev = UiElement("prev_1", text = "A", bounds = Bounds(0, 0, 100, 100))
        val curr = UiElement("curr_1", text = "B", bounds = Bounds(10, 10, 110, 110)) // High IoU

        val matches = recognizer.matchElements(listOf(prev), listOf(curr))

        // Should match via bounds overlap
        assertEquals(1, matches.size)
    }

    @Test
    fun `ambiguous element detection`() {
        val prev = UiElement("prev_1", text = "Button", clickable = true)
        val curr1 = UiElement("curr_1", text = "Button", clickable = true)
        val curr2 = UiElement("curr_2", text = "Button", clickable = true)

        val ambiguous = recognizer.detectAmbiguousElements(listOf(prev), listOf(curr1, curr2))

        assertEquals(1, ambiguous.size)
        assertTrue(ambiguous[0].isHighlyAmbiguous)
        assertEquals(2, ambiguous[0].candidates.size)
    }

    @Test
    fun `find by text query`() {
        val elements = listOf(
            UiElement("1", text = "Login Button", clickable = true),
            UiElement("2", text = "Sign Up", clickable = true),
            UiElement("3", contentDescription = "Close dialog", clickable = true),
            UiElement("4", text = "Settings", clickable = true),
        )

        val results = recognizer.findByText(elements, "login")

        assertEquals(1, results.size)
        assertEquals("1", results[0].element.elementId)
        assertTrue(results[0].score > 0.6f)
    }

    @Test
    fun `find by partial text`() {
        val elements = listOf(
            UiElement("1", text = "Submit Form Now", clickable = true),
        )

        val results = recognizer.findByText(elements, "Submit")

        assertEquals(1, results.size)
        assertTrue(results[0].score > 0.7f)
    }

    @Test
    fun `find by view id`() {
        val elements = listOf(
            UiElement("1", viewIdResourceName = "com.app:id/submit_button", text = "Submit"),
        )

        val results = recognizer.findByText(elements, "submit_button")

        assertEquals(1, results.size)
        assertEquals("viewId", results[0].matchedField)
    }

    @Test
    fun `find by semantic role`() {
        val elements = listOf(
            UiElement("1", text = "Button 1", clickable = true, semanticRole = SemanticRole.BUTTON),
            UiElement("2", text = "Field 1", editable = true, semanticRole = SemanticRole.TEXT_FIELD),
            UiElement("3", text = "Button 2", clickable = true, semanticRole = SemanticRole.BUTTON),
        )

        val buttons = recognizer.findByRole(elements, SemanticRole.BUTTON)
        val fields = recognizer.findByRole(elements, SemanticRole.TEXT_FIELD)

        assertEquals(2, buttons.size)
        assertEquals(1, fields.size)
    }

    @Test
    fun `find interactive elements`() {
        val elements = listOf(
            UiElement("1", text = "Button", clickable = true),
            UiElement("2", text = "Label", semanticRole = SemanticRole.TEXT),
            UiElement("3", editable = true, semanticRole = SemanticRole.TEXT_FIELD),
            UiElement("4", focusable = true, semanticRole = SemanticRole.LIST_ITEM),
        )

        val interactive = recognizer.findInteractive(elements)

        assertEquals(3, interactive.size)
    }

    @Test
    fun `find in region`() {
        val region = Bounds(0, 0, 500, 500)
        val elements = listOf(
            UiElement("1", bounds = Bounds(100, 100, 200, 200)),
            UiElement("2", bounds = Bounds(600, 600, 700, 700)),
            UiElement("3", bounds = Bounds(250, 250, 350, 350)),
        )

        val inRegion = recognizer.findInRegion(elements, region)

        assertEquals(2, inRegion.size)
    }

    @Test
    fun `get element at point`() {
        val elements = listOf(
            UiElement("1", bounds = Bounds(0, 0, 100, 100)),
            UiElement("2", bounds = Bounds(100, 100, 200, 200)),
        )

        val element = recognizer.getElementAt(elements, 50, 50)

        assertNotNull(element)
        assertEquals("1", element?.elementId)
    }
}
