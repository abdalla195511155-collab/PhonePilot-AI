package com.phonepilot.app.data.ai.chat

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.phonepilot.app.data.ai.AiProtocol
import com.phonepilot.app.data.ai.ModelCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAdaptersTest {

    private val gson = Gson()

    private fun call(stream: Boolean = false, protocol: AiProtocol = AiProtocol.OPENAI_COMPATIBLE) =
        ChatCall(
            providerId = "p1",
            modelId = "meta/llama-3.1-8b-instruct",
            baseUrl = "https://integrate.api.nvidia.com/v1",
            protocol = protocol,
            apiKey = "sk-secret-123",
            messages = listOf(
                ChatMessage(ChatRole.SYSTEM, "system rules"),
                ChatMessage(ChatRole.USER, "مرحبا"),
            ),
            capabilities = setOf(ModelCapability.TEXT, ModelCapability.STREAMING),
            stream = stream,
        )

    @Test
    fun `openai body carries model messages and stream flag`() {
        val body = OpenAiCompatibleAdapter.body(call(stream = true))
        val json = gson.fromJson(body, JsonObject::class.java)

        assertEquals("meta/llama-3.1-8b-instruct", json["model"].asString)
        assertTrue(json["stream"].asBoolean)
        assertEquals(2, json["messages"].asJsonArray.size())
        assertEquals("system", json["messages"].asJsonArray[0].asJsonObject["role"].asString)
        assertEquals("مرحبا", json["messages"].asJsonArray[1].asJsonObject["content"].asString)
        assertFalse(body.contains("sk-secret-123"))
    }

    @Test
    fun `openai success parses content and usage`() {
        val outcome = OpenAiCompatibleAdapter.parseSuccess(
            HttpResult(
                status = 200,
                body = """{"choices":[{"message":{"content":"مرحبا بك"},"finish_reason":"stop"}],
                    "usage":{"prompt_tokens":4,"completion_tokens":2,"total_tokens":6}}""",
            ),
        )
        assertTrue(outcome is ChatOutcome.Success)
        val success = outcome as ChatOutcome.Success
        assertEquals("مرحبا بك", success.content)
        assertEquals(6, success.usage?.totalTokens)
    }

    @Test
    fun `empty choices is an invalid response`() {
        val outcome = OpenAiCompatibleAdapter.parseSuccess(
            HttpResult(status = 200, body = """{"choices":[]}"""),
        )
        assertTrue(outcome is ChatOutcome.Failure)
        assertEquals(
            ChatErrorKind.INVALID_RESPONSE,
            (outcome as ChatOutcome.Failure).error.kind,
        )
    }

    @Test
    fun `http statuses map to specific errors`() {
        val unauthorized = ChatAdapters.errorFor(401, "{}", null)
        assertEquals(ChatErrorKind.AUTH, unauthorized.kind)

        val rateLimited = ChatAdapters.errorFor(429, "{}", 30)
        assertEquals(ChatErrorKind.RATE_LIMIT, rateLimited.kind)
        assertEquals(30L, rateLimited.retryAfterSeconds)

        assertEquals(ChatErrorKind.NOT_FOUND, ChatAdapters.errorFor(404, "{}", null).kind)
        assertEquals(ChatErrorKind.TIMEOUT, ChatAdapters.errorFor(408, "{}", null).kind)
        assertEquals(ChatErrorKind.SERVER, ChatAdapters.errorFor(503, "{}", null).kind)
        assertEquals(ChatErrorKind.BAD_REQUEST, ChatAdapters.errorFor(400, "{}", null).kind)
    }

    @Test
    fun `anthropic body separates system and requires max tokens`() {
        val body = AnthropicMessagesAdapter.body(
            call(protocol = AiProtocol.ANTHROPIC_MESSAGES),
        )
        val json = gson.fromJson(body, JsonObject::class.java)
        assertEquals("system rules", json["system"].asString)
        assertEquals(1, json["messages"].asJsonArray.size())
        assertTrue(json["max_tokens"].asInt > 0)
        assertEquals(
            "sk-secret-123",
            AnthropicMessagesAdapter.headers("sk-secret-123")["x-api-key"],
        )
    }

    @Test
    fun `openai stream lines parse deltas and done`() {
        val delta = OpenAiCompatibleAdapter.parseStreamLine(
            """data: {"choices":[{"delta":{"content":"مر"}}]}""",
        )
        assertTrue(delta is StreamLine.Delta)
        assertEquals("مر", (delta as StreamLine.Delta).text)

        assertTrue(
            OpenAiCompatibleAdapter.parseStreamLine("data: [DONE]") is StreamLine.Done,
        )
        assertTrue(OpenAiCompatibleAdapter.parseStreamLine("") is StreamLine.Ignore)
    }

    @Test
    fun `urls are normalised and https detected`() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            OpenAiCompatibleAdapter.endpoint("https://api.example.com/v1/"),
        )
        assertTrue(ChatAdapters.isHttps("https://api.example.com/v1"))
        assertFalse(ChatAdapters.isHttps("http://api.example.com/v1"))
    }
}
