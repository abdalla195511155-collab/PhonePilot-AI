package com.phonepilot.app.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext

interface OcrEngine {
    suspend fun process(image: Bitmap): OcrResult
    suspend fun process(inputImage: InputImage): OcrResult
    val engineName: String
    val supportedLanguages: List<String>
    fun isAvailable(): Boolean
    fun release()
}

/** ML Kit Text Recognition implementation (Latin script). */
class MlKitOcrEngine(
    private val context: Context,
    private val languageHints: List<String> = listOf("en"),
) : OcrEngine {
    private val tag = "MlKitOcrEngine"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recognizer: TextRecognizer? = null
    private var initialized = false
    private val supported = setOf("en", "es", "fr", "de", "it", "pt", "ru")
    private val unsupported = setOf("ar", "fa", "ur", "he", "zh", "ja", "ko", "hi")

    override val engineName = "ML Kit Text Recognition (Latin script)"
    override val supportedLanguages = supported.toList()

    private fun initialize(): Boolean {
        if (initialized) return recognizer != null
        if (languageHints.none { it.lowercase() in supported }) {
            Log.w(tag, "No supported language hints; defaulting to English")
        }
        return try {
            recognizer = TextRecognition.getClient(TextRecognizerOptions.Builder().build())
            initialized = true
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize ML Kit OCR", e)
            initialized = false
            false
        }
    }

    override suspend fun process(image: Bitmap): OcrResult =
        process(InputImage.fromBitmap(image, 0))

    override suspend fun process(inputImage: InputImage): OcrResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        if (!initialize()) {
            return@withContext OcrResult(
                errorMessage = "OCR engine not available",
                success = false,
                engineName = engineName,
                processingTimeMs = System.currentTimeMillis() - startedAt,
            )
        }
        val unsupportedHints = languageHints.filter { it.lowercase() in unsupported }
        if (unsupportedHints.isNotEmpty()) {
            Log.w(tag, "Unsupported language hints: ${unsupportedHints.joinToString()}")
        }
        try {
            val result = recognizer!!.process(inputImage).awaitResult()
            parseMlKitResult(result, inputImage, startedAt)
        } catch (e: Exception) {
            Log.e(tag, "OCR processing failed", e)
            OcrResult(
                errorMessage = "OCR processing failed: ${e.message}",
                success = false,
                engineName = engineName,
                processingTimeMs = System.currentTimeMillis() - startedAt,
            )
        }
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitResult(): T =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { if (continuation.isActive) continuation.resume(it, onCancellation = null) }
            addOnFailureListener { if (continuation.isActive) continuation.resumeWith(Result.failure(it)) }
            addOnCanceledListener { continuation.cancel() }
        }

    private fun parseMlKitResult(
        mlResult: com.google.mlkit.vision.text.Text,
        inputImage: InputImage,
        startTime: Long,
    ): OcrResult {
        val textBlocks = mutableListOf<OcrTextBlock>()
        val fullText = StringBuilder()
        var order = 0
        for (block in mlResult.textBlocks) {
            val blockText = block.text
            if (blockText.isBlank()) continue
            textBlocks += OcrTextBlock(
                text = blockText,
                bounds = Bounds.fromRect(block.boundingBox ?: Rect(0, 0, 0, 0)),
                confidence = 0.5f,
                language = "en",
                readingOrder = order++,
            )
            if (fullText.isNotEmpty()) fullText.append('\n')
            fullText.append(blockText)
        }
        return OcrResult(
            textBlocks = textBlocks,
            fullText = fullText.toString(),
            imageWidth = inputImage.width,
            imageHeight = inputImage.height,
            processingTimeMs = System.currentTimeMillis() - startTime,
            engineName = engineName,
            success = true,
        )
    }

    override fun isAvailable(): Boolean = initialize()

    override fun release() {
        recognizer?.close()
        recognizer = null
        initialized = false
        scope.cancel()
    }
}

/** Placeholder OCR engine; Tesseract has not been integrated yet. */
class TesseractOcrEngine(
    private val context: Context,
    private val languageHints: List<String> = listOf("ara", "eng"),
) : OcrEngine {
    override val engineName = "Tesseract OCR"
    override val supportedLanguages = listOf("ara", "eng", "fra", "deu", "spa", "ita", "por", "rus", "chi_sim", "jpn", "kor")
    private fun unavailable() = OcrResult(
        errorMessage = "Tesseract processing is not implemented",
        success = false,
        engineName = engineName,
    )
    override suspend fun process(image: Bitmap): OcrResult = withContext(Dispatchers.IO) { unavailable() }
    override suspend fun process(inputImage: InputImage): OcrResult = withContext(Dispatchers.IO) { unavailable() }
    override fun isAvailable(): Boolean = false
    override fun release() = Unit
}

object OcrEngineFactory {
    enum class EngineType { ML_KIT, TESSERACT }
    private var cachedEngine: OcrEngine? = null
    private var cachedConfig: VisionConfig? = null

    fun getEngine(
        context: Context,
        config: VisionConfig = VisionConfig(),
        preferredType: EngineType = EngineType.ML_KIT,
    ): OcrEngine {
        if (cachedEngine != null && cachedConfig == config) return cachedEngine!!
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
        if (preferredType != EngineType.ML_KIT) return getEngine(context, config, EngineType.ML_KIT)
        return engine
    }

    fun release() {
        cachedEngine?.release()
        cachedEngine = null
        cachedConfig = null
    }
}
