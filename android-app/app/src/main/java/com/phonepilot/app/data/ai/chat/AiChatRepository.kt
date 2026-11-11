package com.phonepilot.app.data.ai.chat

import com.phonepilot.app.data.ai.AiModelSpec
import com.phonepilot.app.data.ai.AiProviderSpec
import com.phonepilot.app.data.ai.UsageStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Glue between the UI and [AiChatEngine]: declared-limit guard, usage counters,
 * and failure recording. It never retries a POST automatically.
 */
object AiChatRepository : ChatRepository {

    private val engine = AiChatEngine(
        transport = OkHttpHttpTransport(),
        streamingTransport = OkHttpStreamingTransport(),
    )

    override suspend fun send(
        provider: AiProviderSpec,
        model: AiModelSpec,
        apiKey: String?,
        messages: List<ChatMessage>,
    ): ChatOutcome {
        UsageStore.guard(provider)?.let { limit ->
            return ChatOutcome.Failure(
                ChatError(
                    kind = ChatErrorKind.LIMIT_REACHED,
                    userMessage = limit.message,
                    retryAfterSeconds = limit.retryAfterSeconds,
                ),
            )
        }
        UsageStore.recordAttempt(provider.id, model.id)
        val outcome = engine.send(call(provider, model, apiKey, messages, stream = false))
        when (outcome) {
            is ChatOutcome.Success -> UsageStore.recordSuccess(provider.id, model.id)
            is ChatOutcome.Failure -> UsageStore.recordFailure(provider.id, model.id, outcome.error)
        }
        return outcome
    }

    fun stream(
        provider: AiProviderSpec,
        model: AiModelSpec,
        apiKey: String?,
        messages: List<ChatMessage>,
    ): Flow<ChatStreamEvent> = flow {
        UsageStore.guard(provider)?.let { limit ->
            emit(
                ChatStreamEvent.Failed(
                    ChatError(
                        kind = ChatErrorKind.LIMIT_REACHED,
                        userMessage = limit.message,
                        retryAfterSeconds = limit.retryAfterSeconds,
                    ),
                ),
            )
            return@flow
        }
        UsageStore.recordAttempt(provider.id, model.id)
        var settled = false

        try {
            engine.stream(call(provider, model, apiKey, messages, stream = true)).collect { event ->
                when (event) {
                    is ChatStreamEvent.Completed -> {
                        settled = true
                        UsageStore.recordSuccess(provider.id, model.id)
                        emit(event)
                    }
                    is ChatStreamEvent.Failed -> {
                        settled = true
                        UsageStore.recordFailure(provider.id, model.id, event.error)
                        emit(event)
                    }
                    is ChatStreamEvent.Delta -> emit(event)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            if (!settled) {
                // Cancelled by the user: count it, but never retry.
                UsageStore.recordFailure(
                    provider.id,
                    model.id,
                    ChatError(ChatErrorKind.CANCELLED, "أُلغي الطلب"),
                )
            }
        }
    }

    fun supportsStreaming(model: AiModelSpec): Boolean =
        model.hasCapability(com.phonepilot.app.data.ai.ModelCapability.STREAMING)

    private fun call(
        provider: AiProviderSpec,
        model: AiModelSpec,
        apiKey: String?,
        messages: List<ChatMessage>,
        stream: Boolean,
    ): ChatCall = ChatCall(
        providerId = provider.id,
        modelId = model.id,
        baseUrl = provider.baseUrl,
        protocol = provider.protocol,
        apiKey = apiKey,
        messages = messages,
        capabilities = model.capabilitySet(),
        timeoutMs = 90_000L,
        stream = stream,
    )
}
