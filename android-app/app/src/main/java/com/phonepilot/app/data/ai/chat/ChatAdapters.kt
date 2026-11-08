package com.phonepilot.app.data.ai.chat

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.phonepilot.app.data.ai.AiProtocol
import java.net.URI

/**
 * Wire adapters. Protocol differences are explicit: OpenAI-compatible and
 * Anthropic Messages are separate implementations, and anything else is
 * rejected instead of pretending to be compatible.
 */
interface ChatAdapter {
    fun endpoint(baseUrl: String): String
    fun headers(apiKey: String?): Map<String, String>
    fun body(call: ChatCall): String
    fun parseSuccess(http: HttpResult): ChatOutcome
    fun parseError(status: Int, body: String, retryAfter: Long?): ChatError
    fun parseStreamLine(line: String): StreamLine
}

sealed class StreamLine {
    data class Delta(val text: String) : StreamLine()
    data class Done(val usage: TokenUsage?) : StreamLine()
    data class Failed(val error: ChatError) : StreamLine()
    object Ignore : StreamLine()
}

object ChatAdapters {

    fun forProtocol(protocol: AiProtocol): ChatAdapter? = when (protocol) {
        AiProtocol.OPENAI_COMPATIBLE -> OpenAiCompatibleAdapter
        AiProtocol.ANTHROPIC_MESSAGES -> AnthropicMessagesAdapter
        AiProtocol.CUSTOM_ADAPTER -> null
    }

    fun errorFor(
        status: Int,
        body: String,
        retryAfterSeconds: Long?,
    ): ChatError {
        val providerMessage = runCatching {
            Gson().fromJson(body, ErrorEnvelope::class.java)?.error?.message
        }.getOrNull()?.takeIf { it.isNotBlank() }

        val base = when (status) {
            400 -> "طلب غير صالح — تحقق من النموذج والمعاملات"
            401 -> "المفتاح مرفوض (401) — تحقق من API Key"
            403 -> "لا تملك صلاحية لهذا النموذج (403)"
            404 -> "النموذج أو المسار غير موجود (404)"
            408 -> "انتهت مهلة الطلب (408)"
            429 -> "تم تجاوز حد الطلبات (429)"
            in 500..599 -> "خطأ في خدمة المزود ($status)"
            else -> "فشل الطلب (HTTP $status)"
        }
        val retryNote = retryAfterSeconds?.let { " — أعد المحاولة بعد ${it}ث" } ?: ""
        val message = if (providerMessage.isNullOrBlank()) base + retryNote else "$base: $providerMessage"
        return ChatError(
            kind = when (status) {
                401 -> ChatErrorKind.AUTH
                403 -> ChatErrorKind.FORBIDDEN
                404 -> ChatErrorKind.NOT_FOUND
                400 -> ChatErrorKind.BAD_REQUEST
                408 -> ChatErrorKind.TIMEOUT
                429 -> ChatErrorKind.RATE_LIMIT
                in 500..599 -> ChatErrorKind.SERVER
                else -> ChatErrorKind.INVALID_RESPONSE
            },
            userMessage = message,
            httpStatus = status,
            retryAfterSeconds = retryAfterSeconds,
        )
    }

    /** Shared URL normalisation: accepts base URLs with or without `/v1`. */
    fun chatUrl(baseUrl: String, suffix: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return trimmed + suffix
    }

    fun isHttps(baseUrl: String): Boolean = runCatching {
        URI(baseUrl).scheme?.equals("https", ignoreCase = true) == true
    }.getOrDefault(false)

    data class ErrorEnvelope(val error: ErrorBody? = null)
    data class ErrorBody(val message: String? = null)
}

/** OpenAI-compatible: NVIDIA API Catalog, DeepSeek, OpenAI, local servers. */
object OpenAiCompatibleAdapter : ChatAdapter {

    private val gson = Gson()

    override fun endpoint(baseUrl: String): String =
        ChatAdapters.chatUrl(baseUrl, "/chat/completions")

    override fun headers(apiKey: String?): Map<String, String> = buildMap {
        put("Content-Type", "application/json")
        put("Accept", "application/json")
        if (!apiKey.isNullOrBlank()) put("Authorization", "Bearer $apiKey")
    }

    override fun body(call: ChatCall): String {
        val payload = OpenAiRequest(
            model = call.modelId,
            messages = call.messages.map { message ->
                if (message.imageUrls.isEmpty()) {
                    OpenAiContentMessage(role = message.role.name.lowercase(), content = message.content)
                } else {
                    OpenAiContentMessage(
                        role = message.role.name.lowercase(),
                        content = buildList {
                            add(ContentPart(type = "text", text = message.content))
                            message.imageUrls.forEach { url ->
                                add(ContentPart(type = "image_url", imageUrl = ImageUrl(url)))
                            }
                        },
                    )
                }
            },
            temperature = call.temperature,
            maxTokens = call.maxTokens,
            stream = call.stream,
        )
        return gson.toJson(payload)
    }

    override fun parseSuccess(http: HttpResult): ChatOutcome {
        val response = runCatching {
            gson.fromJson(http.body, OpenAiResponse::class.java)
        }.getOrNull()

        val message = response?.choices?.firstOrNull()?.message?.content
        if (message.isNullOrBlank()) {
            return ChatOutcome.Failure(
                ChatError(
                    kind = ChatErrorKind.INVALID_RESPONSE,
                    userMessage = "استجابة فارغة أو غير مفهومة من المزود",
                    httpStatus = http.status,
                ),
            )
        }
        return ChatOutcome.Success(
            content = message,
            usage = response.usage?.toUsage(),
            finishReason = response.choices.firstOrNull()?.finishReason,
        )
    }

    override fun parseError(status: Int, body: String, retryAfter: Long?): ChatError =
        ChatAdapters.errorFor(status, body, retryAfter)

    override fun parseStreamLine(line: String): StreamLine {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return StreamLine.Ignore
        if (!trimmed.startsWith("data:")) return StreamLine.Ignore
        val payload = trimmed.removePrefix("data:").trim()
        if (payload == "[DONE]") return StreamLine.Done(null)
        val chunk = runCatching {
            gson.fromJson(payload, OpenAiStreamChunk::class.java)
        }.getOrNull() ?: return StreamLine.Ignore

        val delta = chunk.choices?.firstOrNull()?.delta?.content
        if (!delta.isNullOrEmpty()) return StreamLine.Delta(delta)
        if (chunk.choices?.firstOrNull()?.finishReason != null) {
            return StreamLine.Done(chunk.usage?.toUsage())
        }
        return StreamLine.Ignore
    }

    private data class OpenAiRequest(
        val model: String,
        val messages: List<OpenAiContentMessage>,
        val temperature: Double?,
        @SerializedName("max_tokens") val maxTokens: Int?,
        val stream: Boolean,
    )

    private data class OpenAiContentMessage(
        val role: String,
        val content: Any?,
    )

    private data class ContentPart(
        val type: String,
        val text: String? = null,
        @SerializedName("image_url") val imageUrl: ImageUrl? = null,
    )

    private data class ImageUrl(val url: String)

    private data class OpenAiResponse(
        val choices: List<Choice>? = null,
        val usage: UsageDto? = null,
    )

    private data class OpenAiStreamChunk(
        val choices: List<StreamChoice>? = null,
        val usage: UsageDto? = null,
    )

    private data class Choice(
        val message: MessageDto? = null,
        @SerializedName("finish_reason") val finishReason: String? = null,
    )

    private data class StreamChoice(
        val delta: DeltaDto? = null,
        @SerializedName("finish_reason") val finishReason: String? = null,
    )

    private data class MessageDto(val content: String? = null)
    private data class DeltaDto(val content: String? = null)

    private data class UsageDto(
        @SerializedName("prompt_tokens") val promptTokens: Int? = null,
        @SerializedName("completion_tokens") val completionTokens: Int? = null,
        @SerializedName("total_tokens") val totalTokens: Int? = null,
    ) {
        fun toUsage() = TokenUsage(promptTokens, completionTokens, totalTokens)
    }
}

/** Anthropic Messages protocol (custom providers that use it). */
object AnthropicMessagesAdapter : ChatAdapter {

    private val gson = Gson()

    override fun endpoint(baseUrl: String): String =
        ChatAdapters.chatUrl(baseUrl, "/messages")

    override fun headers(apiKey: String?): Map<String, String> = buildMap {
        put("Content-Type", "application/json")
        put("Accept", "application/json")
        put("anthropic-version", "2023-06-01")
        if (!apiKey.isNullOrBlank()) put("x-api-key", apiKey)
    }

    override fun body(call: ChatCall): String {
        val system = call.messages
            .filter { it.role == ChatRole.SYSTEM }
            .joinToString("\n") { it.content }
            .takeIf { it.isNotBlank() }
        val turns = call.messages
            .filter { it.role != ChatRole.SYSTEM }
            .map { AnthropicMessage(role = it.role.name.lowercase(), content = it.content) }
        return gson.toJson(
            AnthropicRequest(
                model = call.modelId,
                system = system,
                messages = turns,
                maxTokens = call.maxTokens ?: 1024,
                temperature = call.temperature,
                stream = call.stream,
            ),
        )
    }

    override fun parseSuccess(http: HttpResult): ChatOutcome {
        val response = runCatching {
            gson.fromJson(http.body, AnthropicResponse::class.java)
        }.getOrNull()
        val text = response?.content
            ?.filter { it.type == "text" }
            ?.joinToString("") { it.text.orEmpty() }
            .orEmpty()
        if (text.isBlank()) {
            return ChatOutcome.Failure(
                ChatError(
                    kind = ChatErrorKind.INVALID_RESPONSE,
                    userMessage = "استجابة فارغة من المزود",
                    httpStatus = http.status,
                ),
            )
        }
        return ChatOutcome.Success(
            content = text,
            usage = response?.usage?.let {
                TokenUsage(it.inputTokens, it.outputTokens)
            },
            finishReason = response?.stopReason,
        )
    }

    override fun parseError(status: Int, body: String, retryAfter: Long?): ChatError =
        ChatAdapters.errorFor(status, body, retryAfter)

    override fun parseStreamLine(line: String): StreamLine {
        val trimmed = line.trim()
        if (!trimmed.startsWith("data:")) return StreamLine.Ignore
        val payload = trimmed.removePrefix("data:").trim()
        if (payload.isEmpty()) return StreamLine.Ignore
        val event = runCatching {
            gson.fromJson(payload, AnthropicStreamEvent::class.java)
        }.getOrNull() ?: return StreamLine.Ignore
        return when (event.type) {
            "content_block_delta" -> event.delta?.text
                ?.takeIf { it.isNotEmpty() }
                ?.let { StreamLine.Delta(it) }
                ?: StreamLine.Ignore

            "message_stop" -> StreamLine.Done(null)
            "error" -> StreamLine.Failed(
                ChatError(
                    kind = ChatErrorKind.SERVER,
                    userMessage = event.error?.message ?: "خطأ في بث المزود",
                ),
            )

            else -> StreamLine.Ignore
        }
    }

    private data class AnthropicRequest(
        val model: String,
        val system: String?,
        val messages: List<AnthropicMessage>,
        @SerializedName("max_tokens") val maxTokens: Int,
        val temperature: Double?,
        val stream: Boolean,
    )

    private data class AnthropicMessage(val role: String, val content: String)

    private data class AnthropicResponse(
        val content: List<AnthropicContent>? = null,
        @SerializedName("stop_reason") val stopReason: String? = null,
        val usage: AnthropicUsage? = null,
    )

    private data class AnthropicContent(
        val type: String? = null,
        val text: String? = null,
    )

    private data class AnthropicUsage(
        @SerializedName("input_tokens") val inputTokens: Int? = null,
        @SerializedName("output_tokens") val outputTokens: Int? = null,
    )

    private data class AnthropicStreamEvent(
        val type: String? = null,
        val delta: AnthropicDelta? = null,
        val error: ErrorEnvelope? = null,
    )

    private data class AnthropicDelta(val text: String? = null)
    private data class ErrorEnvelope(val message: String? = null)
}
