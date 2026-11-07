package com.phonepilot.app.data.ai.chat

import com.phonepilot.app.data.ai.AiProtocol
import com.phonepilot.app.data.ai.ModelCapability
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow

/**
 * Protocol-agnostic chat engine.
 *
 * Responsibilities:
 *  - pick the right adapter and respect its base URL / protocol;
 *  - refuse to send when configuration is invalid (no key, insecure URL, empty
 *    conversation, images without proven vision support);
 *  - never retry automatically (a duplicated POST can cost money);
 *  - map every failure to a user message without guessing.
 */
class AiChatEngine(
    private val transport: HttpTransport,
    private val streamingTransport: StreamingTransport,
) {

    suspend fun send(call: ChatCall): ChatOutcome {
        val adapter = ChatAdapters.forProtocol(call.protocol)
            ?: return failure(
                ChatErrorKind.UNSUPPORTED,
                "هذا البروتوكول لا يدعم التنفيذ المباشر — استخدم OpenAI-compatible أو Anthropic Messages",
            )
        validate(call)?.let { return ChatOutcome.Failure(it) }

        return try {
            val url = adapter.endpoint(call.baseUrl)
            val http = transport.post(
                url = url,
                headers = adapter.headers(call.apiKey),
                body = adapter.body(call),
                timeoutMs = call.timeoutMs,
            )
            if (http.status in 200..299) {
                adapter.parseSuccess(http)
            } else {
                ChatOutcome.Failure(
                    sanitize(adapter.parseError(http.status, http.body, http.retryAfterSeconds), call.apiKey),
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (transportError: ChatTransportException) {
            ChatOutcome.Failure(sanitize(transportError.error, call.apiKey))
        } catch (error: Exception) {
            failure(
                ChatErrorKind.INVALID_RESPONSE,
                "فشل غير متوقع في المحرك: ${error.javaClass.simpleName}",
            )
        }
    }

    fun stream(call: ChatCall): Flow<ChatStreamEvent> = flow {
        val adapter = ChatAdapters.forProtocol(call.protocol)
            ?: run {
                emit(ChatStreamEvent.Failed(
                    ChatError(
                        ChatErrorKind.UNSUPPORTED,
                        "هذا البروتوكول لا يدعم البث",
                    ),
                ))
                return@flow
            }
        validate(call)?.let {
            emit(ChatStreamEvent.Failed(it))
            return@flow
        }
        if (!call.capabilities.contains(ModelCapability.STREAMING)) {
            emit(ChatStreamEvent.Failed(
                ChatError(
                    ChatErrorKind.UNSUPPORTED,
                    "البث غير موثق لهذا النموذج — استخدم طلبًا عاديًا",
                ),
            ))
            return@flow
        }

        var finished = false
        streamingTransport.postStream(
            url = adapter.endpoint(call.baseUrl),
            headers = adapter.headers(call.apiKey) + mapOf("Accept" to "text/event-stream"),
            body = adapter.body(call.copy(stream = true)),
            timeoutMs = call.timeoutMs,
        ).collect { line ->
            when (val parsed = adapter.parseStreamLine(line)) {
                is StreamLine.Delta -> emit(ChatStreamEvent.Delta(parsed.text))
                is StreamLine.Done -> {
                    finished = true
                    emit(ChatStreamEvent.Completed(parsed.usage))
                }

                is StreamLine.Failed -> {
                    finished = true
                    emit(ChatStreamEvent.Failed(sanitize(parsed.error, call.apiKey)))
                }

                StreamLine.Ignore -> Unit
            }
        }
        if (!finished) {
            emit(ChatStreamEvent.Failed(
                ChatError(
                    ChatErrorKind.INVALID_RESPONSE,
                    "انتهى البث دون إشارة اكتمال",
                ),
            ))
        }
    }.catch { error ->
        if (error is CancellationException) throw error
        val mapped = when (error) {
            is ChatTransportException -> error.error
            else -> ChatError(
                ChatErrorKind.NETWORK,
                "انقطع البث: ${error.javaClass.simpleName}",
            )
        }
        emit(ChatStreamEvent.Failed(sanitize(mapped, call.apiKey)))
    }

    /**
     * Removes the API key if a provider echoed it, and bounds the message so a
     * huge error body cannot flood the UI.
     */
    private fun sanitize(error: ChatError, apiKey: String?): ChatError {
        var message = error.userMessage
        if (!apiKey.isNullOrBlank() && message.contains(apiKey)) {
            message = message.replace(apiKey, "[REDACTED]")
        }
        return error.copy(userMessage = message.take(300))
    }

    /** Returns the first blocking configuration error, or null. */
    private fun validate(call: ChatCall): ChatError? {
        if (call.messages.none { it.role == ChatRole.USER && it.content.isNotBlank() }) {
            return ChatError(ChatErrorKind.BAD_REQUEST, "لا توجد رسالة مستخدم لإرسالها")
        }
        if (call.messages.any { it.imageUrls.isNotEmpty() } &&
            !call.capabilities.contains(ModelCapability.VISION)
        ) {
            return ChatError(
                ChatErrorKind.UNSUPPORTED,
                "هذا النموذج لا يملك دليلًا على دعم الصور — لم يُرسَل أي محتوى بصري",
            )
        }
        if (!ChatAdapters.isHttps(call.baseUrl) && !isLocalHost(call.baseUrl)) {
            return ChatError(
                ChatErrorKind.CONFIG,
                "عنوان المزود يجب أن يستخدم HTTPS (يُسمح بـ http للمضيف المحلي فقط)",
            )
        }
        if (call.apiKey.isNullOrBlank() && !isLocalHost(call.baseUrl)) {
            return ChatError(ChatErrorKind.CONFIG, "أضف API Key لهذا المزود أولًا")
        }
        if (call.protocol == AiProtocol.CUSTOM_ADAPTER) {
            return ChatError(ChatErrorKind.UNSUPPORTED, "المحول المخصص غير مدعوم للتنفيذ")
        }
        return null
    }

    private fun isLocalHost(baseUrl: String): Boolean {
        val host = runCatching { URI(baseUrl).host }.getOrNull()?.lowercase() ?: return false
        return host == "localhost" ||
            host == "127.0.0.1" ||
            host == "10.0.2.2" ||
            host.endsWith(".local")
    }

    private fun failure(kind: ChatErrorKind, message: String) =
        ChatOutcome.Failure(ChatError(kind, message))
}
