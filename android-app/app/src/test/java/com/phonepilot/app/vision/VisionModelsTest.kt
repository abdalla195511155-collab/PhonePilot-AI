package com.phonepilot.app.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionModelsTest {

    @Test
    fun `bounds intersection and containment`() {
        val b1 = Bounds(0, 0, 100, 100)
        val b2 = Bounds(50, 50, 150, 150)
        val b3 = Bounds(200, 200, 300, 300)
        val b4 = Bounds(10, 10, 90, 90) // contained in b1

        assertTrue(b1.intersects(b2))
        assertFalse(b1.intersects(b3))
        assertTrue(b1.contains(b4))
        assertFalse(b1.contains(b2))
    }

    @Test
    fun `bounds IoU calculation`() {
        val b1 = Bounds(0, 0, 100, 100)
        val b2 = Bounds(0, 0, 100, 100) // identical
        val b3 = Bounds(50, 50, 150, 150) // 25% overlap
        val b4 = Bounds(200, 200, 300, 300) // no overlap

        assertEquals(1.0f, b1.iou(b2), 0.001f)
        assertEquals(0.142857f, b1.iou(b3), 0.001f) // 2500 / (10000 + 10000 - 2500)
        assertEquals(0.0f, b1.iou(b4), 0.001f)
    }

    @Test
    fun `bounds center point`() {
        val b = Bounds(10, 20, 110, 120)
        assertEquals(60, b.centerX())
        assertEquals(70, b.centerY())
        assertEquals(100, b.width())
        assertEquals(100, b.height())
    }

    @Test
    fun `ui element creation and properties`() {
        val bounds = Bounds(0, 0, 100, 50)
        val element = UiElement(
            elementId = "test_1",
            text = "Hello",
            contentDescription = "World",
            bounds = bounds,
            clickable = true,
            semanticRole = SemanticRole.BUTTON,
            source = ElementSource.ACCESSIBILITY,
        )

        assertEquals("test_1", element.elementId)
        assertEquals("Hello", element.primaryText)
        assertTrue(element.hasTextContent)
        assertTrue(element.clickable)
        assertEquals(50, element.centerX())
        assertEquals(25, element.centerY())
        assertEquals(100, element.width)
        assertEquals(50, element.height)
    }

    @Test
    fun `ocr text block`() {
        val bounds = Bounds(10, 10, 200, 50)
        val block = OcrTextBlock(
            text = "Test text",
            bounds = bounds,
            confidence = 0.9f,
            language = "en",
            readingOrder = 0,
        )

        assertEquals("Test text", block.text)
        assertEquals(0.9f, block.confidence, 0.001f)
        assertEquals("en", block.language)
    }

    @Test
    fun `ocr result aggregation`() {
        val block1 = OcrTextBlock("Hello", Bounds(0, 0, 100, 50), 0.9f, "en", 0)
        val block2 = OcrTextBlock("World", Bounds(0, 60, 100, 110), 0.8f, "en", 1)

        val result = OcrResult(
            textBlocks = listOf(block1, block2),
            fullText = "Hello\nWorld",
            imageWidth = 100,
            imageHeight = 110,
            engineName = "Test",
            success = true,
        )

        assertTrue(result.hasText)
        assertEquals(2, result.textBlocks.size)
        assertEquals("Hello\nWorld", result.fullText)
    }

    @Test
    fun `screen state interactive elements`() {
        val button = UiElement("1", text = "Click", clickable = true, semanticRole = SemanticRole.BUTTON)
        val text = UiElement("2", text = "Label", semanticRole = SemanticRole.TEXT)
        val field = UiElement("3", editable = true, semanticRole = SemanticRole.TEXT_FIELD)

        val state = ScreenState(elements = listOf(button, text, field))

        assertEquals(2, state.interactiveElements.size)
        assertEquals(2, state.textElements.size)
    }

    @Test
    fun `screen change detection types`() {
        val change = ScreenChange(
            changeType = ChangeType.ELEMENT_ADDED,
            significance = Significance.MINOR,
        )

        assertEquals(ChangeType.ELEMENT_ADDED, change.changeType)
        assertEquals(Significance.MINOR, change.significance)
        assertFalse(change.hasSignificantChange)

        val majorChange = change.copy(significance = Significance.MAJOR)
        assertTrue(majorChange.hasSignificantChange)
    }

    @Test
    fun `visual context history management`() {
        val context = VisualContext(maxHistorySize = 3)
        val state1 = ScreenState(timestamp = 1000)
        val state2 = ScreenState(timestamp = 2000)
        val state3 = ScreenState(timestamp = 3000)
        val state4 = ScreenState(timestamp = 4000)

        val change1 = ScreenChange(changeType = ChangeType.NO_CHANGE, currentState = state1)
        val change2 = ScreenChange(changeType = ChangeType.NO_CHANGE, currentState = state2)
        val change3 = ScreenChange(changeType = ChangeType.NO_CHANGE, currentState = state3)
        val change4 = ScreenChange(changeType = ChangeType.NO_CHANGE, currentState = state4)

        var ctx = context.addChange(change1).addChange(change2).addChange(change3).addChange(change4)

        // Should only keep last 3
        assertEquals(3, ctx.recentChanges.size)
        assertEquals(4000, ctx.currentState?.timestamp)
        assertEquals(3000, ctx.previousState?.timestamp)
    }

    @Test
    fun `vision config defaults`() {
        val config = VisionConfig()

        assertTrue(config.enableAccessibilityAnalysis)
        assertTrue(config.enableOcr)
        assertTrue(config.enableFusion)
        assertEquals(listOf("ar", "en"), config.ocrLanguageHints)
        assertEquals(1000L, config.captureIntervalMs)
        assertEquals(500, config.maxElementsPerScreen)
        assertEquals(200, config.maxOcrTextBlocks)
        assertEquals(0.3f, config.minConfidenceThreshold, 0.001f)
        assertTrue(config.enablePrivacyFilter)
        assertFalse(config.retainScreenshots)
    }
}
