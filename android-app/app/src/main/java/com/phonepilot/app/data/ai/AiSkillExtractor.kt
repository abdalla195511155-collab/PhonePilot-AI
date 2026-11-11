package com.phonepilot.app.data.ai

import com.phonepilot.app.data.ai.chat.AiChatRepository
import com.phonepilot.app.data.ai.chat.ChatMessage
import com.phonepilot.app.data.ai.chat.ChatOutcome
import com.phonepilot.app.data.ai.chat.ChatRepository
import com.phonepilot.app.data.ai.chat.ChatRole
import com.phonepilot.app.models.AiExtractedException
import com.phonepilot.app.models.AiExtractedStep
import com.phonepilot.app.models.AiSkillExtractionResult
import com.phonepilot.app.privacy.PrivacyFilter

/**
 * Skill extraction through the configured AI provider, with local validation
 * and privacy filtering before any demonstration text is sent to a provider.
 */
class AiSkillExtractor(
    private val providerRepository: AiProviderRepository = AiProviderRepository,
    private val chatRepository: ChatRepository = AiChatRepository,
) {
    suspend fun extractSkillFromDemonstrations(
        taskName: String,
        demonstrations: List<String>,
        language: String = "ar",
    ): AiSkillExtractionResult? {
        val provider = providerRepository.selectedProvider() ?: return null
        val model = providerRepository.selectedModel() ?: return null
        val apiKey = providerRepository.apiKeyFor(provider)
        if (apiKey.isNullOrBlank()) return null

        val filteredDemos = demonstrations.map { PrivacyFilter.maskEventText(it) }
        val prompt = buildExtractionPrompt(taskName, filteredDemos, language)
        val messages = listOf(
            ChatMessage(
                role = ChatRole.SYSTEM,
                content = "أنت مساعد يستخرج خطوات المهارة من عروض تسجيل الشاشة. أجب بصيغة JSON صالحة فقط، دون أي نص إضافي.",
            ),
            ChatMessage(role = ChatRole.USER, content = prompt),
        )

        return when (val outcome = chatRepository.send(provider, model, apiKey, messages)) {
            is ChatOutcome.Success -> parseAndValidateAiOutput(outcome.content)
            is ChatOutcome.Failure -> null
        }
    }

    private fun buildExtractionPrompt(
        taskName: String,
        demonstrations: List<String>,
        language: String,
    ): String {
        val demosText = demonstrations.withIndex().joinToString(separator = "\n---\n") { indexed ->
            "العرض ${indexed.index + 1}:\n${indexed.value}"
        }
        return """
            المهمة: $taskName
            عدد العروض: ${demonstrations.size}
            اللغة: $language

            العروض المسجلة (نصوص الأحداث فقط، لا صور):
            $demosText

            المطلوب:
            1. استخراج الخطوات المشتركة عبر جميع العروض
            2. تحديد الخطوات الاختيارية (تظهر في بعض العروض وليس كلها)
            3. تحديد الاستثناءات والشرطية
            4. إعطاء درجة ثقة من 0.0 إلى 1.0
            5. توضيح سبب الدرجة

            أجب بصيغة JSON صالحة فقط:
            {
              "steps": [{"stepNumber":1,"action":"click","target":"اسم العنصر","elementRole":"button","verification":"التحقق","isOptional":false,"condition":null}],
              "exceptions": [{"condition":"الشرط","action":"الإجراء"}],
              "confidence": 0.85,
              "reasoning": "سبب درجة الثقة"
            }
        """.trimIndent()
    }

    private fun parseAndValidateAiOutput(rawContent: String): AiSkillExtractionResult? = try {
        val json = org.json.JSONObject(rawContent)
        val stepsArray = json.getJSONArray("steps")
        val steps = (0 until stepsArray.length()).map { i ->
            val step = stepsArray.getJSONObject(i)
            AiExtractedStep(
                stepNumber = step.optInt("stepNumber", i + 1),
                action = step.optString("action", "unknown"),
                target = step.optString("target", ""),
                elementRole = step.optString("elementRole").takeIf(String::isNotBlank),
                verification = step.optString("verification").takeIf(String::isNotBlank),
                isOptional = step.optBoolean("isOptional", false),
                condition = step.optString("condition").takeIf(String::isNotBlank),
            )
        }
        val exceptionsArray = json.optJSONArray("exceptions")
        val exceptions = if (exceptionsArray == null) emptyList() else {
            (0 until exceptionsArray.length()).map { i ->
                val exception = exceptionsArray.getJSONObject(i)
                AiExtractedException(
                    condition = exception.optString("condition", ""),
                    action = exception.optString("action", ""),
                )
            }
        }
        val reasoning = json.optString("reasoning").takeIf(String::isNotBlank)
        AiSkillExtractionResult(
            steps = steps,
            exceptions = exceptions,
            confidence = json.optDouble("confidence", 0.0).coerceIn(0.0, 1.0),
            reasoning = reasoning?.takeUnless(PrivacyFilter::isSensitiveText),
        )
    } catch (_: Exception) {
        null
    }

    fun extractLocally(demonstrations: List<String>): AiSkillExtractionResult {
        val filtered = demonstrations.map { PrivacyFilter.maskEventText(it) }
        val steps = filtered.flatMap { demo ->
            demo.lines().filter(String::isNotBlank).mapIndexed { index, line ->
                AiExtractedStep(
                    stepNumber = index + 1,
                    action = "extracted_locally",
                    target = line.take(100),
                    elementRole = null,
                    verification = null,
                    isOptional = false,
                    condition = null,
                )
            }
        }.distinctBy { it.target }
        return AiSkillExtractionResult(
            steps = steps,
            exceptions = emptyList(),
            confidence = 0.3,
            reasoning = "تحليل محلي دون نموذج AI؛ يُنصح بالمراجعة اليدوية",
        )
    }
}

private fun PrivacyFilter.maskEventText(text: String): String =
    if (isSensitiveText(text)) "[مخفي]" else text
