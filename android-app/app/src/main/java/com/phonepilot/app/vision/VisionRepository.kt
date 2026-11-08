package com.phonepilot.app.vision

import android.content.Context
import android.util.Log
import com.phonepilot.app.data.ai.AiProviderRepository
import com.phonepilot.app.data.ai.AiProviderSpec
import com.phonepilot.app.data.ai.ModelCapability
import com.phonepilot.app.data.ai.chat.AiChatRepository
import com.phonepilot.app.data.ai.chat.ChatMessage
import com.phonepilot.app.data.ai.chat.ChatRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Vision Repository - integrates Vision Engine with AI Provider system.
 * Provides high-level interface for vision-based AI interactions.
 */
class VisionRepository(
    private val context: Context,
    private val config: VisionConfig = VisionConfig(),
    private val visualContextManager: VisualContextManager,
    private val aiProviderRepository: AiProviderRepository = AiProviderRepository,
) {

    private val TAG = "VisionRepository"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // State flows for UI observation
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _lastVisionResponse = MutableStateFlow<String?>(null)
    val lastVisionResponse: StateFlow<String?> = _lastVisionResponse.asStateFlow()

    /**
     * Analyze current screen and optionally send to AI for interpretation.
     */
    suspend fun analyzeScreen(
        rootNode: android.view.accessibility.AccessibilityNodeInfo?,
        ocrResult: OcrResult? = null,
        packageName: String? = null,
        activityName: String? = null,
        sendToAi: Boolean = false,
        aiPrompt: String? = null,
    ): VisionAnalysisResult = withContext(Dispatchers.IO) {
        _isAnalyzing.value = true
        _lastError.value = null

        return try {
            // Update visual context
            val change = visualContextManager.analyzeAndUpdate(
                rootNode = rootNode,
                ocrResult = ocrResult,
                packageName = packageName,
                activityName = activityName,
            )

            val currentState = visualContextManager.currentContext.currentState
            if (currentState == null) {
                throw IllegalStateException("No screen state after analysis")
            }

            // Build vision summary for AI
            val visionSummary = buildVisionSummary(currentState, change)

            // Send to AI if requested
            var aiResponse: String? = null
            if (sendToAi && aiProviderRepository.activeProviderId.value != null) {
                aiResponse = sendToAiModel(visionSummary, aiPrompt)
            }

            _lastVisionResponse.value = aiResponse

            VisionAnalysisResult(
                screenState = currentState,
                change = change,
                visionSummary = visionSummary,
                aiResponse = aiResponse,
                success = true,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Vision analysis failed", e)
            _lastError.value = e.message
            VisionAnalysisResult(
                screenState = null,
                change = null,
                visionSummary = "",
                aiResponse = null,
                success = false,
                errorMessage = e.message,
            )
        } finally {
            _isAnalyzing.value = false
        }
    }

    /**
     * Send vision data to AI model for interpretation.
     */
    private suspend fun sendToAiModel(
        visionSummary: String,
        customPrompt: String?,
    ): String? {
        val providerId = aiProviderRepository.activeProviderId.value
        val modelId = aiProviderRepository.activeModelId.value
        val provider = aiProviderRepository.getProvider(providerId)
        val model = provider?.models?.firstOrNull { it.id == modelId }

        if (provider == null || model == null) {
            _lastError.value = "No active provider or model"
            return null
        }

        // Check if model supports vision
        if (!model.hasCapability(ModelCapability.VISION)) {
            _lastError.value = "Active model does not support vision (no VISION capability evidence)"
            return null
        }

        // Get API key
        val apiKey = if (provider.isBuiltIn) {
            com.phonepilot.app.data.ai.SecureKeyStore.get(context, "nvidia_api_key")
        } else {
            com.phonepilot.app.data.ai.SecureKeyStore.get(context, "custom_api_key_${provider.id}")
        }

        if (apiKey.isNullOrBlank()) {
            _lastError.value = "API key not configured for provider"
            return null
        }

        // Build prompt
        val prompt = customPrompt ?: buildDefaultVisionPrompt(visionSummary)

        // Send to AI
        val messages = listOf(
            ChatMessage(ChatRole.SYSTEM, "أنت مساعد بصري في تطبيق PhonePilot AI. حلل محتوى الشاشة وأجب بالعربية بدقة."),
            ChatMessage(ChatRole.USER, prompt),
        )

        val outcome = com.phonepilot.app.data.ai.chat.AiChatRepository.send(provider, model, apiKey, messages)

        return when (outcome) {
            is com.phonepilot.app.data.ai.chat.ChatOutcome.Success -> outcome.content
            is com.phonepilot.app.data.ai.chat.ChatOutcome.Failure -> {
                _lastError.value = outcome.error.userMessage
                null
            }
        }
    }

    /**
     * Build a comprehensive vision summary for AI consumption.
     */
    private fun buildVisionSummary(state: ScreenState, change: ScreenChange?): String {
        val builder = StringBuilder()

        builder.append("=== تحليل الشاشة الحالية ===\n")
        builder.append("التطبيق: ${state.packageName}\n")
        builder.append("الشاشة: ${state.activityName}\n")
        builder.append("الأبعاد: ${state.screenWidth}x${state.screenHeight}\n")
        builder.append("الثقة: ${String.format("%.1f", state.confidence * 100)}%\n\n")

        builder.append("--- العناصر التفاعلية (${state.interactiveElements.size}) ---\n")
        for (element in state.interactiveElements.take(20)) {
            val text = element.primaryText?.takeIf { it.isNotBlank() } ?: "[بدون نص]"
            val role = element.semanticRole.label
            val bounds = element.bounds?.let { "[${it.left},${it.top},${it.right},${it.bottom}]" } ?: ""
            builder.append("• [$role] $text $bounds\n")
        }

        if (state.textElements.size > state.interactiveElements.size) {
            builder.append("\n--- العناصر النصية الإضافية ---\n")
            for (element in state.textElements.filterNot { it in state.interactiveElements }.take(10)) {
                val text = element.primaryText?.takeIf { it.isNotBlank() } ?: ""
                if (text.isNotBlank()) {
                    builder.append("• $text\n")
                }
            }
        }

        if (state.loadingIndicators.isNotEmpty()) {
            builder.append("\n--- مؤشرات التحميل ---\n")
            for (indicator in state.loadingIndicators) {
                builder.append("• ${indicator.semanticRole.label} ${indicator.primaryText ?: ""}\n")
            }
        }

        if (state.errorMessages.isNotEmpty()) {
            builder.append("\n--- رسائل الخطأ ---\n")
            for (error in state.errorMessages) {
                builder.append("• $error\n")
            }
        }

        if (state.emptyStates.isNotEmpty()) {
            builder.append("\n--- حالات فارغة ---\n")
            for (empty in state.emptyStates) {
                builder.append("• ${empty.primaryText ?: ""}\n")
            }
        }

        if (state.hasDialog) builder.append("\n⚠ يوجد حوار مفتوح\n")
        if (state.hasKeyboard) builder.append("\n⌨ لوحة المفاتيح ظاهرة\n")

        // OCR info
        state.ocrResult?.let { ocr ->
            builder.append("\n--- نص OCR (${ocr.textBlocks.size} كتلة) ---\n")
            builder.append(ocr.fullText.take(500))
            if (ocr.fullText.length > 500) builder.append("...")
            builder.append("\n")
        }

        // Change info
        change?.let { ch ->
            builder.append("\n--- التغييرات منذ آخر تحليل ---\n")
            builder.append("النوع: ${ch.changeType}\n")
            builder.append("الأهمية: ${ch.significance}\n")
            if (ch.addedElements.isNotEmpty()) builder.append("مضاف: ${ch.addedElements.size}\n")
            if (ch.removedElements.isNotEmpty()) builder.append("محذوف: ${ch.removedElements.size}\n")
            if (ch.changedElements.isNotEmpty()) builder.append("متغير: ${ch.changedElements.size}\n")
        }

        return builder.toString()
    }

    /**
     * Build default prompt for vision analysis.
     */
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

    /**
     * Ask a specific question about the current screen.
     */
    suspend fun askAboutScreen(question: String): VisionAnalysisResult {
        return analyzeScreen(
            rootNode = null, // Use current context
            sendToAi = true,
            aiPrompt = "بناءً على السياق البصري الحالي، أجب على السؤال التالي: $question"
        )
    }

    /**
     * Get available vision-capable models.
     */
    fun getVisionCapableModels(): List<com.phonepilot.app.data.ai.AiModelSpec> {
        val providerId = aiProviderRepository.activeProviderId.value
        val provider = aiProviderRepository.getProvider(providerId)
        return provider?.models?.filter { it.hasCapability(ModelCapability.VISION) } ?: emptyList()
    }

    /**
     * Check if current active model supports vision.
     */
    fun isCurrentModelVisionCapable(): Boolean {
        val providerId = aiProviderRepository.activeProviderId.value
        val modelId = aiProviderRepository.activeModelId.value
        val provider = aiProviderRepository.getProvider(providerId)
        val model = provider?.models?.firstOrNull { it.id == modelId }
        return model?.hasCapability(ModelCapability.VISION) == true
    }

    /**
     * Get visual context summary for UI display.
     */
    fun getContextSummary(): String {
        return visualContextManager.getContextSummary()
    }

    /**
     * Clear vision context history.
     */
    suspend fun clearHistory() {
        visualContextManager.clearHistory()
    }

    /**
     * Shutdown the repository.
     */
    fun shutdown() {
        scope.cancel()
    }
}

/**
 * Result of vision analysis.
 */
data class VisionAnalysisResult(
    val screenState: ScreenState?,
    val change: ScreenChange?,
    val visionSummary: String,
    val aiResponse: String?,
    val success: Boolean,
    val errorMessage: String? = null,
)
