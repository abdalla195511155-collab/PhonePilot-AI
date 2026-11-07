package com.phonepilot.app.data.ai.chat

import com.phonepilot.app.data.ai.ModelCapability

enum class ChatRole { SYSTEM, USER, ASSISTANT }

/** A single chat turn. Images stay empty until a model proves VISION support. */
data class ChatMessage(
    val role: ChatRole,
    val content: String,
    val imageUrls: List<String> = emptyList(),
    val error: Boolean = false,
)

data class TokenUsage(
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
)

enum class ChatErrorKind {
    CONFIG,
    UNSUPPORTED,
    LIMIT_REACHED,
    AUTH,
    FORBIDDEN,
    NOT_FOUND,
    BAD_REQUEST,
    RATE_LIMIT,
    TIMEOUT,
    NETWORK,
    SERVER,
    INVALID_RESPONSE,
    CANCELLED,
}

/** User-facing failure. Never contains the API key or raw headers. */
data class ChatError(
    val kind: ChatErrorKind,
    val userMessage: String,
    val httpStatus: Int? = null,
    val retryAfterSeconds: Long? = null,
)

sealed class ChatOutcome {
    data class Success(
        val content: String,
        val usage: TokenUsage? = null,
        val finishReason: String? = null,
    ) : ChatOutcome()

    data class Failure(val error: ChatError) : ChatOutcome()
}

sealed class ChatStreamEvent {
    data class Delta(val text: String) : ChatStreamEvent()
    data class Failed(val error: ChatError) : ChatStreamEvent()
    data class Completed(val usage: TokenUsage? = null) : ChatStreamEvent()
}

/** Everything the engine needs, already resolved from provider + model. */
data class ChatCall(
    val providerId: String,
    val modelId: String,
    val baseUrl: String,
    val protocol: com.phonepilot.app.data.ai.AiProtocol,
    val apiKey: String?,
    val messages: List<ChatMessage>,
    val capabilities: Set<ModelCapability> = emptySet(),
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val timeoutMs: Long = 60_000L,
    val stream: Boolean = false,
)

/** Pure HTTP result used by adapters and tests. */
data class HttpResult(
    val status: Int,
    val body: String,
    val retryAfterSeconds: Long? = null,
)

interface HttpTransport {
    suspend fun post(
        url: String,
        headers: Map<String, String>,
        body: String,
        timeoutMs: Long,
    ): HttpResult
}

interface StreamingTransport {
    /** Emits raw SSE lines exactly as received. */
    fun postStream(
        url: String,
        headers: Map<String, String>,
        body: String,
        timeoutMs: Long,
    ): kotlinx.coroutines.flow.Flow<String>
}
