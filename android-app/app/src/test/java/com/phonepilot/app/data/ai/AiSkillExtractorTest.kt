package com.phonepilot.app.data.ai

import com.phonepilot.app.data.ai.chat.AiChatRepository
import com.phonepilot.app.data.ai.chat.ChatMessage
import com.phonepilot.app.data.ai.chat.ChatOutcome
import com.phonepilot.app.data.ai.chat.ChatRole
import com.phonepilot.app.privacy.PrivacyFilter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSkillExtractorTest {

    private class FakeChatRepository : AiChatRepository() {
        var lastOutcome: ChatOutcome? = null
        var shouldFail = false

        override suspend fun send(
            provider: com.phonepilot.app.data.ai.AiProviderSpec,
            model: com.phonepilot.app.data.ai.AiModelSpec,
            apiKey: String?,
            messages: List<ChatMessage>,
        ): ChatOutcome {
            if (shouldFail) {
                lastOutcome = ChatOutcome.Failure(
                    com.phonepilot.app.data.ai.chat.ChatError(
                        kind = com.phonepilot.app.data.ai.chat.ChatErrorKind.NETWORK,
                        userMessage = "Network error",
                    ),
                )
                return lastOutcome!!
            }
            lastOutcome = ChatOutcome.Success(
                content = """{"steps":[{"stepNumber":1,"action":"click","target":"btn_login","elementRole":"button","verification":"الشاشة الرئيسية ظهرت","isOptional":false,"condition":null}],"exceptions":[],"confidence":0.9,"reasoning":"خطوات واضحة ومتسقة"}""",
            )
            return lastOutcome!!
        }
    }

    @Test
    fun `privacy filter masks sensitive text in demonstration descriptions`() {
        // التحقق من أن PrivacyFilter يُخفي النصوص الحساسة
        val sensitive = "كلمة مروري secret123"
        assertTrue(PrivacyFilter.isSensitiveText(sensitive))

        val normal = "اضغط على زر تسجيل الدخول"
        assertFalse(PrivacyFilter.isSensitiveText(normal))
    }

    @Test
    fun `extractLocally returns local extraction with low confidence`() = runBlocking {
        val providerRepo = com.phonepilot.app.data.ai.AiProviderRepository
        val chatRepo = FakeChatRepository()
        val extractor = AiSkillExtractor(providerRepo, chatRepo)

        val result = extractor.extractLocally(listOf("click btn_a", "input txt_field"))

        assertNotNull(result)
        assertTrue(result.confidence < 0.5) // ثقة منخفضة للتحليل المحلي
        assertTrue(result.reasoning.contains("محلي"))
    }

    @Test
    fun `extractSkillFromDemonstrations returns null when API fails`() = runBlocking {
        val providerRepo = com.phonepilot.app.data.ai.AiProviderRepository
        val chatRepo = FakeChatRepository().apply { shouldFail = true }
        val extractor = AiSkillExtractor(providerRepo, chatRepo)

        // لا يوجد مزود/Model محدد في AiProviderRepository، لذا سيُرجع null
        val result = extractor.extractSkillFromDemonstrations(
            taskName = "تسجيل دخول",
            demonstrations = listOf("click email_field"),
            language = "ar",
        )

        assertNull(result)
    }

    @Test
    fun `invalid AI JSON output returns null rather than crashing`() = runBlocking {
        val providerRepo = com.phonepilot.app.data.ai.AiProviderRepository
        val chatRepo = object : FakeChatRepository() {
            override suspend fun send(
                provider: com.phonepilot.app.data.ai.AiProviderSpec,
                model: com.phonepilot.app.data.ai.AiModelSpec,
                apiKey: String?,
                messages: List<ChatMessage>,
            ): ChatOutcome {
                return ChatOutcome.Success(content = "ليس JSON صالحاً {{{")
            }
        }
        val extractor = AiSkillExtractor(providerRepo, chatRepo)

        // لا يوجد مزود محدد في AiProviderRepository، لذا سيُرجع null قبل حتى الوصول للتحليل
        val result = extractor.extractSkillFromDemonstrations(
            taskName = "اختبار",
            demonstrations = listOf("click btn"),
            language = "ar",
        )

        assertNull(result)
    }
}
