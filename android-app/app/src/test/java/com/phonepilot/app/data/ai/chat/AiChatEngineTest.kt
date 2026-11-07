package com.phonepilot.app.data.ai.chat

import com.phonepilot.app.data.ai.AiProtocol
import com.phonepilot.app.data.ai.ModelCapability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatEngineTest {

    private class FakeTransport(
        private val result: HttpResult = HttpResult(200, "{}"),
        private val failure: ChatTransportException? = null,
    ) : HttpTransport {
        var calls = 0
        override suspend fun post(
            url: String,
            headers: Map<String, String>,
            body: String,
            timeoutMs: Long,
        ): HttpResult {
            calls++
            failure?.let { throw it }
            return result
        }
    }

    private class FakeStream(
        private val lines: List<String> = emptyList(),
    ) : StreamingTransport {
        override fun postStream(
            url: String,
            headers: Map<String, String>,
            body: String,
            timeoutMs: Long,
        ): Flow<String> = flowOf(*lines.toTypedArray())
    }

    private fun call(
        messages: List<ChatMessage> = listOf(ChatMessage(ChatRole.USER, "مرحبا")),
        apiKey: String? = "sk-secret-123",
        baseUrl: String = "https://api.example.com/v1",
        images: List<String> = emptyList(),
        capabilities: Set<ModelCapability> = setOf(ModelCapability.TEXT, ModelCapability.STREAMING),
        protocol: AiProtocol = AiProtocol.OPENAI_COMPATIBLE,
    ) = ChatCall(
        providerId = "p1",
        modelId = "model-1",
        baseUrl = baseUrl,
        protocol = protocol,
        apiKey = apiKey,
        messages = if (images.isEmpty()) {
            messages
        } else {
            messages.map { it.copy(imageUrls = images) }
        },
        capabilities = capabilities,
    )

    @Test
    fun `empty user message is refused before any request`() = runBlocking {
        val transport = FakeTransport()
        val engine = AiChatEngine(transport, FakeStream())

        val outcome = engine.send(call(messages = listOf(ChatMessage(ChatRole.SYSTEM, "x"))))

        assertTrue(outcome is ChatOutcome.Failure)
        assertEquals(ChatErrorKind.BAD_REQUEST, (outcome as ChatOutcome.Failure).error.kind)
        assertEquals(0, transport.calls)
    }

    @Test
    fun `missing key and insecure url are refused`() = runBlocking {
        val transport = FakeTransport()
        val engine = AiChatEngine(transport, FakeStream())

        val noKey = engine.send(call(apiKey = null))
        assertEquals(ChatErrorKind.CONFIG, (noKey as ChatOutcome.Failure).error.kind)

        val insecure = engine.send(call(baseUrl = "http://api.example.com/v1"))
        assertEquals(ChatErrorKind.CONFIG, (insecure as ChatOutcome.Failure).error.kind)

        assertEquals(0, transport.calls)
    }

    @Test
    fun `images require documented vision support`() = runBlocking {
        val transport = FakeTransport()
        val engine = AiChatEngine(transport, FakeStream())

        val outcome = engine.send(call(images = listOf("https://example.com/a.png")))

        assertEquals(ChatErrorKind.UNSUPPORTED, (outcome as ChatOutcome.Failure).error.kind)
        assertEquals(0, transport.calls)
    }

    @Test
    fun `unsupported adapter is refused`() = runBlocking {
        val engine = AiChatEngine(FakeTransport(), FakeStream())
        val outcome = engine.send(call(protocol = AiProtocol.CUSTOM_ADAPTER))
        assertEquals(ChatErrorKind.UNSUPPORTED, (outcome as ChatOutcome.Failure).error.kind)
    }

    @Test
    fun `success parses exactly one request`() = runBlocking {
        val transport = FakeTransport(
            HttpResult(
                200,
                """{"choices":[{"message":{"content":"أهلًا"}}]}""",
            ),
        )
        val engine = AiChatEngine(transport, FakeStream())

        val outcome = engine.send(call())

        assertTrue(outcome is ChatOutcome.Success)
        assertEquals("أهلًا", (outcome as ChatOutcome.Success).content)
        assertEquals(1, transport.calls)
    }

    @Test
    fun `http errors are mapped and never retried`() = runBlocking {
        val transport = FakeTransport(HttpResult(401, """{"error":{"message":"bad key"}}"""))
        val engine = AiChatEngine(transport, FakeStream())

        val outcome = engine.send(call())

        val error = (outcome as ChatOutcome.Failure).error
        assertEquals(ChatErrorKind.AUTH, error.kind)
        assertEquals(1, transport.calls)
        assertFalse(error.userMessage.contains("sk-secret-123"))
    }

    @Test
    fun `transport failure is reported once with a safe message`() = runBlocking {
        val transport = FakeTransport(
            failure = ChatTransportException(
                ChatError(ChatErrorKind.NETWORK, "تعذر الاتصال بالمزود"),
            ),
        )
        val engine = AiChatEngine(transport, FakeStream())

        val outcome = engine.send(call())

        assertEquals(ChatErrorKind.NETWORK, (outcome as ChatOutcome.Failure).error.kind)
        assertEquals(1, transport.calls)
    }

    @Test
    fun `streaming emits deltas and completion only with evidence`() = runBlocking {
        val stream = FakeStream(
            listOf(
                """data: {"choices":[{"delta":{"content":"مر"}}]}""",
                """data: [DONE]""",
            ),
        )
        val engine = AiChatEngine(FakeTransport(), stream)

        val events = mutableListOf<ChatStreamEvent>()
        engine.stream(call()).collect { events.add(it) }

        assertEquals(2, events.size)
        assertTrue(events[0] is ChatStreamEvent.Delta)
        assertTrue(events[1] is ChatStreamEvent.Completed)

        val unsupported = mutableListOf<ChatStreamEvent>()
        engine.stream(call(capabilities = setOf(ModelCapability.TEXT))).collect {
            unsupported.add(it)
        }
        assertTrue((unsupported.single() as ChatStreamEvent.Failed).error.kind == ChatErrorKind.UNSUPPORTED)
    }
}
