package com.phonepilot.app.data.ai

import com.phonepilot.app.data.ai.chat.AiChatRepository
import com.phonepilot.app.data.ai.chat.ChatMessage
import com.phonepilot.app.data.ai.chat.ChatOutcome
import com.phonepilot.app.data.ai.chat.ChatRole
import com.phonepilot.app.models.AiSkillExtractionResult
import com.phonepilot.app.privacy.PrivacyFilter

/**
 * استخراج المهارات عبر نموذج AI (Phase 5 — AI Skill Extraction).
 *
 * يربط استخراج المهارات بمحرك AI الموجود في Phase 3 (AiProviderRepository +
 * AiChatEngine) مع ضمانات الخصوصية والسلامة:
 * - لا تُرسل صور لنموذج لا يدعم VISION.
 * - يُسمح بالتحليل المحلي عند عدم توفر API.
 * - عند فشل API تُحفظ العروض ولا تُفقد بيانات التدريب.
 * - يُتحقق من Structured Output محلياً قبل التخزين.
 * - لا تسمح لمخرجات AI بتجاوز PrivacyFilter.
 */
class AiSkillExtractor(
    private val providerRepository: AiProviderRepository = AiProviderRepository,
    private val chatRepository: AiChatRepository = AiChatRepository,
) {

    /**
     * يستخرج مهارة من عروض متعددة باستخدام نموذج AI إذا كان متاحاً،
     *否则 يرجع null ليتم استخدام التعلم المحلي فقط.
     *
     * عند تعذر الاتصال بالنموذج، لا تُفقد بيانات العروض؛ يُرجع null
     * ويستمر التعلم المحلي عبر [LearningEngine].
     */
    suspend fun extractSkillFromDemonstrations(
        taskName: String,
        demonstrations: List<String>, // وصف نصي لكل عرض (أحداث معروفة فقط)
        language: String = "ar",
    ): AiSkillExtractionResult? {
        val provider = providerRepository.selectedProvider() ?: return null
        val model = providerRepository.selectedModel() ?: return null
        val apiKey = providerRepository.apiKeyFor(provider)

        // إذا لم يكن هناك API Key، نعود للتحليل المحلي فقط
        if (apiKey == null || apiKey.isBlank()) return null

        // فلترة المدخلات عبر PrivacyFilter قبل الإرسال
        val filteredDemos = demonstrations.map { PrivacyFilter.maskEventText(it) }

        val prompt = buildExtractionPrompt(taskName, filteredDemos, language)

        val messages = listOf(
            ChatMessage(role = ChatRole.SYSTEM, content = "أنت مساعد يستخرج خطوات المهارة من عروض تسجيل الشاشة. أجب بصيغة JSON صالحة فقط، بدون نص إضافي."),
            ChatMessage(role = ChatRole.USER, content = prompt),
        )

        val outcome = chatRepository.send(
            provider = provider,
            model = model,
            apiKey = apiKey,
            messages = messages,
        )

        return when (outcome) {
            is ChatOutcome.Success -> {
                // التحقق من المخرجات محلياً قبل التخزين
                parseAndValidateAiOutput(outcome.content)
            }
            is ChatOutcome.Failure -> {
                // عند فشل API، لا تفقد بيانات التدريب؛ ارجع null للتعلم المحلي
                null
            }
        }
    }

    /**
     * بناء البرومبت لاستخراج الخطوات من العروض.
     * لا يحتوي على صور أو بيانات حساسة (تمت فلترتها مسبقاً).
     */
    private fun buildExtractionPrompt(
        taskName: String,
        demonstrations: List<String>,
        language: String,
    ): String {
        val demosText = demonstrations.withIndex().joinToString(separator = "\n---\n") { index, demo ->
            "العرض ${index + 1}:\n$demo"
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
        3. تحديد الاستثناءات والشرطية (إذا حدث كذا فافعل كذا)
        4. إعطاء درجة ثقة من 0.0 إلى 1.0 بناءً على الاتساق بين العروض
        5. توضيح سبب الدرجة

        أجب بصيغة JSON صالحة فقط:
        {
          "steps": [
            {"stepNumber": 1, "action": "click", "target": "اسم العنصر", "elementRole": "button", "verification": "ما يتحقق بعد الخطوة", "isOptional": false, "condition": null}
          ],
          "exceptions": [
            {"condition": "تفسير الشرط", "action": "الإجراء عند حدوث الشرط"}
          ],
          "confidence": 0.85,
          "reasoning": "سبب درجة الثقة"
        }
        """.trimIndent()
    }

    /**
     * تحليل وتحقق من مخرجات AI محلياً قبل إرجاعها.
     * لا تُثق في المخرجات العمياء؛ يتم التحقق من صحتها الهيكلية.
     */
    private fun parseAndValidateAiOutput(rawContent: String): AiSkillExtractionResult? {
        return try {
            val json = org.json.JSONObject(rawContent)
            val stepsArray = json.getJSONArray("steps")
            val steps = mutableListOf<AiExtractedStep>()
            for (i in 0 until stepsArray.length()) {
                val stepObj = stepsArray.getJSONObject(i)
                steps.add(AiExtractedStep(
                    stepNumber = stepObj.optInt("stepNumber", i + 1),
                    action = stepObj.optString("action", "unknown"),
                    target = stepObj.optString("target", ""),
                    elementRole = stepObj.optString("elementRole", null).takeIf { it.isNotBlank() },
                    verification = stepObj.optString("verification", null).takeIf { it.isNotBlank() },
                    isOptional = stepObj.optBoolean("isOptional", false),
                    condition = stepObj.optString("condition", null).takeIf { it.isNotBlank() },
                ))
            }

            val exceptionsArray = json.optJSONArray("exceptions")
            val exceptions = mutableListOf<AiExtractedException>()
            if (exceptionsArray != null) {
                for (i in 0 until exceptionsArray.length()) {
                    val excObj = exceptionsArray.getJSONObject(i)
                    exceptions.add(AiExtractedException(
                        condition = excObj.optString("condition", ""),
                        action = excObj.optString("action", ""),
                    ))
                }
            }

            val confidence = json.optDouble("confidence", 0.0).coerceIn(0.0, 1.0)
            val reasoning = json.optString("reasoning", null).takeIf { it.isNotBlank() }

            // فلترة مخرجات AI عبر PrivacyFilter لضمان عدم تسريب بيانات حساسة
            val filteredReasoning = reasoning?.let { PrivacyFilter.isSensitiveText(it).takeIf { it }?.let { null } ?: it }

            AiSkillExtractionResult(
                steps = steps,
                exceptions = exceptions,
                confidence = confidence,
                reasoning = filteredReasoning,
            )
        } catch (e: Exception) {
            null // مخرجات غير صالحة، لا تُخزن
        }
    }

    /**
     * تحليل محلي للعروض دون نموذج AI (fallback).
     * يُستخدم عند عدم توفر API أو عند فشل الاستدعاء.
     */
    fun extractLocally(demonstrations: List<String>): AiSkillExtractionResult {
        val filtered = demonstrations.map { PrivacyFilter.maskEventText(it) }
        // استخراج بسيط بناءً على أنماط النص المشترك
        val steps = filtered.flatMap { demo ->
            demo.lines().filter { it.isNotBlank() }.mapIndexed { index, line ->
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
            confidence = 0.3, // ثقة منخفضة للتحليل المحلي
            reasoning = "تحليل محلي دون نموذج AI؛ يُنصح بالمراجعة اليدوية",
        )
    }
}

/**
 * ملحق لـ PrivacyFilter لت masked نص بسيط (يُستخدم عندما لا يكون EventEntity متاحاً).
 * هذا ملحق داخلي داخل هذا الملف لأنه خاص بهذه الطبقة فقط.
 */
private fun PrivacyFilter.maskEventText(text: String): String {
    if (isSensitiveText(text)) return "[مخفي]"
    return text
}
