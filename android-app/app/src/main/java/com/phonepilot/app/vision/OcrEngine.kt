package com.phonepilot.app.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.util.SparseArray
import androidx.annotation.RequiresApi
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * OCR Engine interface for pluggable OCR implementations.
 */
interface OcrEngine {
    /**
     * Process an image and return OCR results.
     */
    suspend fun process(image: Bitmap): OcrResult

    /**
     * Process an image from InputImage (for ML Kit).
     */
    suspend fun process(inputImage: InputImage): OcrResult

    /**
     * Get the engine name.
     */
    val engineName: String

    /**
     * Get supported languages.
     */
    val supportedLanguages: List<String>

    /**
     * Check if engine is available.
     */
    fun isAvailable(): Boolean

    /**
     * Release resources.
     */
    fun release()
}

/**
 * ML Kit Text Recognition implementation.
 * Supports Latin, Chinese, Devanagari, Japanese, Korean scripts.
 * 
 * NOTE: Arabic is NOT supported by Google ML Kit Text Recognition (as of 2024).
 * The Latin script recognizer does NOT recognize Arabic text.
 * If Arabic OCR is needed, a different engine (e.g., Tesseract with Arabic traineddata)
 * must be implemented separately. This engine explicitly marks Arabic as UNSUPPORTED.
 */
class MlKitOcrEngine(
    private val context: Context,
    private val languageHints: List<String> = listOf("en"),
) : OcrEngine {

    private val TAG = "MlKitOcrEngine"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recognizer: TextRecognizer? = null
    private var initialized = false

    // Languages supported by ML Kit Latin script recognizer
    private val mlKitSupportedLanguages = setOf("en", "es", "fr", "de", "it", "pt", "ru")
    
    // Languages explicitly NOT supported by ML Kit (will return empty results)
    private val unsupportedLanguages = setOf("ar", "fa", "ur", "he", "zh", "ja", "ko", "hi")

    override val engineName = "ML Kit Text Recognition (Latin script)"
    override val supportedLanguages = listOf("en", "es", "fr", "de", "it", "pt", "ru")

    /**
     * Initialize the ML Kit text recognizer.
     * Uses Latin script recognizer which only supports Latin-based languages.
     */
    private fun initialize(): Boolean {
        if (initialized) return recognizer != null

        // Filter out unsupported languages from hints
        val filteredHints = languageHints.filter { it.toLowercase() in mlKitSupportedLanguages }
        if (filteredHints.isEmpty()) {
            Log.w(TAG, "No supported languages in hints (${languageHints.joinToString()}), defaulting to English")
        }
        
        return try {
            val options = TextRecognizerOptions.Builder().build()
            recognizer = TextRecognition.getClient(options)
            initialized = true
            Log.d(TAG, "ML Kit OCR initialized successfully (Latin script only)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ML Kit OCR", e)
            initialized = false
            false
        }
    }

    override suspend fun process(image: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        val inputImage = InputImage.fromBitmap(image, 0)
        processInputImage(inputImage)
    }

    override suspend fun process(inputImage: InputImage): OcrResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        if (!initialize()) {
            return@withContext OcrResult(
                errorMessage = "OCR engine not available",
                success = false,
                engineName = engineName,
                processingTimeMs = System.currentTimeMillis() - startTime,
            )
        }

        // Warn about unsupported languages in hints
        val unsupportedInHints = languageHints.filter { it.toLowercase() in unsupportedLanguages }
        if (unsupportedInHints.isNotEmpty()) {
            Log.w(TAG, "Language hints contain unsupported languages: ${unsupportedInHints.joinToString()}. Results may be empty for these languages.")
        }

        return@withContext try {
            val result = recognizer?.process(inputImage).await()
            if (result == null) {
                OcrResult(
                    errorMessage = "OCR returned null result",
                    success = false,
                    engineName = engineName,
                    processingTimeMs = System.currentTimeMillis() - startTime,
                )
            } else {
                parseMlKitResult(result, inputImage, startTime)
            }
        } catch (e: Exception) {
            Log.e(TAG, "OCR processing failed", e)
            OcrResult(
                errorMessage = "OCR processing failed: ${e.message}",
                success = false,
                engineName = engineName,
                processingTimeMs = System.currentTimeMillis() - startTime,
            )
        }
    }

    // Internal method to avoid overload ambiguity
    private suspend fun processInputImage(inputImage: InputImage): OcrResult = process(inputImage)

    /**
     * Parse ML Kit Text result into our OcrResult format.
     */
    private fun parseMlKitResult(
        mlResult: com.google.mlkit.vision.text.Text,
        inputImage: InputImage,
        startTime: Long,
    ): OcrResult {
        val textBlocks = mutableListOf<OcrTextBlock>()
        val fullTextBuilder = StringBuilder()

        // ML Kit returns text in reading order
        var readingOrder = 0

        for (block in mlResult.textBlocks) {
            val blockText = block.text
            if (blockText.isBlank()) continue

            val bounds = Bounds.fromRect(block.boundingBox)
            val confidence = block.confidence ?: 0.5f

            // Get language if available
            val language = block.recognizedLanguages?.firstOrNull()?.let { it.toString() }

            val textBlock = OcrTextBlock(
                text = blockText,
                bounds = bounds,
                confidence = confidence,
                language = language,
                readingOrder = readingOrder++,
            )
            textBlocks.add(textBlock)

            if (fullTextBuilder.isNotEmpty()) fullTextBuilder.append("\n")
            fullTextBuilder.append(blockText)
        }

        OcrResult(
            textBlocks = textBlocks,
            fullText = fullTextBuilder.toString(),
            imageWidth = inputImage.width,
            imageHeight = inputImage.height,
            processingTimeMs = System.currentTimeMillis() - startTime,
            engineName = engineName,
            success = true,
        )
    }

    override fun isAvailable(): Boolean {
        return initialize()
    }

    override fun release() {
        recognizer?.close()
        recognizer = null
        initialized = false
        scope.cancel()
    }
}

/**
 * Tesseract OCR Engine (placeholder for future implementation).
 * Would require tess-two or similar library.
 */
class TesseractOcrEngine(
    private val context: Context,
    private val languageHints: List<String> = listOf("ara", "eng"),
) : OcrEngine {

    private val TAG = "TesseractOcrEngine"
    private var initialized = false

    override val engineName = "Tesseract OCR"
    override val supportedLanguages = listOf("ara", "eng", "fra", "deu", "spa", "ita", "por", "rus", "chi_sim", "jpn", "kor")

    private fun initialize(): Boolean {
        if (initialized) return true
        // TODO: Initialize Tesseract with tess-two library
        // This would require adding the tess-two dependency
        Log.w(TAG, "Tesseract OCR not implemented - requires tess-two dependency")
        return false
    }

    override suspend fun process(image: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        if (!initialize()) {
            return@withContext OcrResult(
                errorMessage = "Tesseract not initialized",
                success = false,
                engineName = engineName,
            )
        }
        // TODO: Implement Tesseract processing
        return@withContext OcrResult(
            errorMessage = "Tesseract processing not implemented",
            success = false,
            engineName = engineName,
        )
    }

    override suspend fun process(inputImage: InputImage): OcrResult = withContext(Dispatchers.IO) {
        val bitmap = inputImage.bitmap ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        process(bitmap)
    }

    override fun isAvailable(): Boolean = false

    override fun release() {
        // TODO: Release Tesseract resources
    }
}

/**
 * OCR Engine factory for creating the appropriate engine.
 */
object OcrEngineFactory {

    /** Available engine types */
    enum class EngineType { ML_KIT, TESSERACT }

    private var cachedEngine: OcrEngine? = null
    private var cachedConfig: VisionConfig? = null

    /**
     * Get or create an OCR engine based on configuration.
     */
    fun getEngine(
        context: Context,
        config: VisionConfig = VisionConfig(),
        preferredType: EngineType = EngineType.ML_KIT,
    ): OcrEngine {
        // Return cached engine if config hasn't changed
        if (cachedEngine != null && cachedConfig == config) {
            return cachedEngine!!
        }

        val engine = when (preferredType) {
            EngineType.ML_KIT -> MlKitOcrEngine(context, config.ocrLanguageHints)
            EngineType.TESSERACT -> TesseractOcrEngine(context, config.ocrLanguageHints)
        }

        if (engine.isAvailable()) {
            cachedEngine?.release()
            cachedEngine = engine
            cachedConfig = config
            return engine
        }

        // Fallback to ML Kit if preferred engine unavailable
        if (preferredType != EngineType.ML_KIT) {
            Log.w("OcrEngineFactory", "Preferred engine unavailable, falling back to ML Kit")
            return getEngine(context, config, EngineType.ML_KIT)
        }

        // Return unavailable engine (will fail gracefully)
        return engine
    }

    /**
     * Release the cached engine.
     */
    fun release() {
        cachedEngine?.release()
        cachedEngine = null
        cachedConfig = null
    }
}
