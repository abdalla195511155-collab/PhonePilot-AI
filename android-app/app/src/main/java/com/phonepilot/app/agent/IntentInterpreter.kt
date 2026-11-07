package com.phonepilot.app.agent

import com.phonepilot.app.data.ai.AiProviderRepository
import com.phonepilot.app.data.ai.chat.AiChatRepository
import com.phonepilot.app.data.ai.chat.ChatMessage
import com.phonepilot.app.data.ai.chat.ChatRole
import kotlinx.coroutines.runBlocking

/**
 * مفسر النية (Phase 7 — Agent Orchestration).
 *
 * يفسر أوامر المستخدم باللغة الطبيعية إلى نوايا منظمة يمكن للوكيل تنفيذها.
 * يستخدم نموذج AI للتفسير مع fallback محلي إذا لم يكن النموذج متاحاً.
 */
class IntentInterpreter(
    private val providerRepository: AiProviderRepository = AiProviderRepository,
    private val chatRepository: AiChatRepository = AiChatRepository,
) {

    /**
     * يفسر أمر المستخدم إلى نية منظمة.
     *
     * يعيد InterpretedIntent يحتوي على النوع، التطبيق المستهدف، الإجراء، والمعاملات.
     * إذا كانت المعلومات ناقصة، يعيد requiresClarification = true مع قائمة بالمعلومات الناقصة.
     */
    fun interpret(command: String): InterpretedIntent {
        val lower = command.lowercase()

        // محاولة التفسير عبر AI إذا كان متاحاً
        val aiResult = tryAiInterpret(command)
        if (aiResult != null) return aiResult

        // fallback محلي بناءً على أنماط النص
        return localInterpret(lower)
    }

    private fun tryAiInterpret(command: String): InterpretedIntent? {
        val provider = providerRepository.selectedProvider() ?: return null
        val model = providerRepository.selectedModel() ?: return null
        val apiKey = providerRepository.apiKeyFor(provider)
        if (apiKey.isNullOrBlank()) return null

        try {
            return runBlocking {
                val outcome = chatRepository.send(
                    provider = provider,
                    model = model,
                    apiKey = apiKey,
                    messages = listOf(
                        ChatMessage(
                            role = ChatRole.SYSTEM,
                            content = "أنت مفسر نوايا لوكيل تنفيذ مهام على أندرويد. حول الأمر إلى JSON يحتوي على: intent, target_app, target_action, parameters, confidence, missing_info, requires_clarification. لا تُرسل صوراً أو بيانات حساسة.",
                        ),
                        ChatMessage(role = ChatRole.USER, content = command),
                    ),
                )
                when (outcome) {
                    is com.phonepilot.app.data.ai.chat.ChatOutcome.Success -> parseAiIntent(outcome.content, command)
                    is com.phonepilot.app.data.ai.chat.ChatOutcome.Failure -> null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseAiIntent(jsonContent: String, rawCommand: String): InterpretedIntent? {
        try {
            val json = org.json.JSONObject(jsonContent)
            return InterpretedIntent(
                rawCommand = rawCommand,
                intent = json.optString("intent", "unknown"),
                targetApp = json.optString("target_app", null).takeIf { it.isNotBlank() },
                targetAction = json.optString("target_action", null).takeIf { it.isNotBlank() },
                parameters = json.optJSONObject("parameters")?.toMap() ?: emptyMap(),
                confidence = json.optDouble("confidence", 0.5f).toFloat(),
                missingInfo = json.optJSONArray("missing_info")?.toList().map { it.toString() } ?: emptyList(),
                requiresClarification = json.optBoolean("requires_clarification", false),
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun localInterpret(command: String): InterpretedIntent {
        val intent = when {
            command.contains("سجل") || command.contains("تسجيل") -> "record_task"
            command.contains("شغل") || command.contains("نفذ") -> "execute_skill"
            command.contains("افتح") || command.contains("افتح تطبيق") -> "open_app"
            command.contains("انقر") || command.contains("اضغط") -> "click_element"
            command.contains("أدخل") || command.contains("اكتب") -> "input_text"
            command.contains("أرسل") -> "send_message"
            command.contains("احذف") -> "delete_data"
            else -> "unknown"
        }

        var targetApp: String? = null
        var targetAction: String? = null
        val parameters = mutableMapOf<String, String>()

        // استخراج اسم التطبيق if mentioned
        val appKeywords = listOf("واتساب", "فيسبوك", "تليجرام", "إنستغرام", "البريد", "الرسائل")
        for (keyword in appKeywords) {
            if (command.contains(keyword, ignoreCase = true)) {
                targetApp = keyword
                break
            }
        }

        // استخراج الإجراء المستهدف
        val actionKeywords = listOf("زر", "حقل", "قائمة", "شاشة")
        for (keyword in actionKeywords) {
            if (command.contains(keyword, ignoreCase = true)) {
                targetAction = keyword
                break
            }
        }

        val missingInfo = mutableListOf<String>()
        var requiresClarification = false
        if (intent == "unknown") {
            requiresClarification = true
            missingInfo.add("النوع المطلوب من العملية")
        }
        if (targetApp == null && intent != "unknown") {
            missingInfo.add("التطبيق المستهدف")
        }

        return InterpretedIntent(
            rawCommand = command,
            intent = intent,
            targetApp = targetApp,
            targetAction = targetAction,
            parameters = parameters,
            confidence = 0.6f, // ثقة منخفضة للتفسير المحلي
            missingInfo = missingInfo,
            requiresClarification = requiresClarification,
        )
    }
}
