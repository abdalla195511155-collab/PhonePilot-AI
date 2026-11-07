package com.phonepilot.app.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VisionFusionEngineTest {

    private lateinit var fusionEngine: VisionFusionEngine
    private lateinit var recognizer: UiElementRecognizer
    private val config = VisionConfig()

    @Before
    fun setup() {
        recognizer = UiElementRecognizer(config)
        fusionEngine = VisionFusionEngine(config, recognizer)
    }

    @Test
    fun `fuse ocr text into accessibility element`() = runBlocking {
        val accElement = UiElement(
            elementId = "acc_1",
            text = "Btn",
            bounds = Bounds(10, 10, 100, 50),
            clickable = true,
            semanticRole = SemanticRole.BUTTON,
            source = ElementSource.ACCESSIBILITY,
        )

        val ocrBlock = OcrTextBlock(
            text = "Submit Button",
            bounds = Bounds(12, 12, 98, 48), // High overlap
            confidence = 0.9f,
            language = "en",
        )

        val ocrResult = OcrResult(
            textBlocks = listOf(ocrBlock),
            fullText = "Submit Button",
            engineName = "Test",
            success = true,
        )

        val fused = fusionEngine.fuse(listOf(accElement), ocrResult, 200, 200)

        assertEquals(1, fused.size)
        val fusedElement = fused[0]
        assertEquals("Submit Button", fusedElement.text) // OCR text preferred (longer)
        assertEquals(ElementSource.FUSED, fusedElement.source)
        assertTrue(fusedElement.confidence > accElement.confidence)
    }

    @Test
    fun `create new element from unmatched ocr`() = runBlocking {
        val accElement = UiElement(
            elementId = "acc_1",
            text = "Header",
            bounds = Bounds(0, 0, 200, 50),
        )

        val ocrBlock = OcrTextBlock(
            text = "Footer Text",
            bounds = Bounds(0, 300, 200, 350), // Far from accessibility element
            confidence = 0.8f,
        )

        val ocrResult = OcrResult(textBlocks = listOf(ocrBlock), fullText = "Footer Text", engineName = "Test", success = true)

        val fused = fusionEngine.fuse(listOf(accElement), ocrResult, 200, 400)

        // Should have both elements
        assertEquals(2, fused.size)
        val ocrElement = fused.find { it.source == ElementSource.OCR }
        assertNotNull(ocrElement)
        assertEquals("Footer Text", ocrElement?.text)
    }

    @Test
    fun `merge nearby ocr into existing element`() = runBlocking {
        val accElement = UiElement(
            elementId = "acc_1",
            text = "Title",
            bounds = Bounds(10, 10, 200, 50),
        )

        val ocrBlock = OcrTextBlock(
            text = "Subtitle",
            bounds = Bounds(10, 60, 200, 90), // Nearby (within proximity threshold)
            confidence = 0.7f,
        )

        val ocrResult = OcrResult(textBlocks = listOf(ocrBlock), fullText = "Subtitle", engineName = "Test", success = true)

        val fused = fusionEngine.fuse(listOf(accElement), ocrResult, 200, 200)

        // Should merge into existing element
        assertEquals(1, fused.size)
        val fusedElement = fused[0]
        assertTrue(fusedElement.text == "Title" || fusedElement.contentDescription == "Subtitle")
    }

    @Test
    fun `detect text mismatch conflict`() = runBlocking {
        val accElement = UiElement(
            elementId = "acc_1",
            text = "Login",
            bounds = Bounds(10, 10, 100, 50),
        )

        val ocrBlock = OcrTextBlock(
            text = "Register", // Completely different
            bounds = Bounds(12, 12, 98, 48),
            confidence = 0.9f,
        )

        val ocrResult = OcrResult(textBlocks = listOf(ocrBlock), fullText = "Register", engineName = "Test", success = true)

        val conflicts = fusionEngine.detectConflicts(listOf(accElement), ocrResult)

        assertEquals(1, conflicts.size)
        assertEquals(ConflictType.TEXT_MISMATCH, conflicts[0].conflictType)
        assertEquals(ConflictSeverity.HIGH, conflicts[0].severity)
    }

    @Test
    fun `detect missing text in accessibility`() = runBlocking {
        val accElement = UiElement(
            elementId = "acc_1",
            text = "", // No text in accessibility
            bounds = Bounds(10, 10, 100, 50),
        )

        val ocrBlock = OcrTextBlock(
            text = "Hidden Text",
            bounds = Bounds(12, 12, 98, 48), // High overlap
            confidence = 0.9f,
        )

        val ocrResult = OcrResult(textBlocks = listOf(ocrBlock), fullText = "Hidden Text", engineName = "Test", success = true)

        val conflicts = fusionEngine.detectConflicts(listOf(accElement), ocrResult)

        assertEquals(1, conflicts.size)
        assertEquals(ConflictType.MISSING_TEXT_IN_ACCESSIBILITY, conflicts[0].conflictType)
    }

    @Test
    fun `resolve conflicting elements`() = runBlocking {
        val element1 = UiElement("1", text = "A", bounds = Bounds(0, 0, 100, 100), confidence = 0.8f)
        val element2 = UiElement("2", text = "B", bounds = Bounds(10, 10, 110, 110), confidence = 0.7f) // High IoU
        val element3 = UiElement("3", text = "C", bounds = Bounds(200, 200, 300, 300), confidence = 0.9f) // Far away

        val resolved = fusionEngine.resolveConflicts(listOf(element1, element2, element3))

        // element1 and element2 should be merged, element3 kept separate
        assertEquals(2, resolved.size)
    }
}

import kotlinx.coroutines.runBlocking
