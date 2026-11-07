package com.phonepilot.app.data.ai.chat

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/** Raised when transport fails in a way that must reach the UI as a ChatError. */
class ChatTransportException(val error: ChatError) : IOException(error.userMessage)

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

internal fun Response.retryAfterSeconds(): Long? =
    header("Retry-After")?.trim()?.toLongOrNull()?.takeIf { it >= 0 }

class OkHttpHttpTransport(
    private val client: OkHttpClient = defaultClient(),
) : HttpTransport {

    override suspend fun post(
        url: String,
        headers: Map<String, String>,
        body: String,
        timeoutMs: Long,
    ): HttpResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON_MEDIA))
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()
        try {
            client.newBuilder()
                .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build()
                .newCall(request)
                .execute()
                .use { response ->
                    HttpResult(
                        status = response.code,
                        body = response.body?.string().orEmpty(),
                        retryAfterSeconds = response.retryAfterSeconds(),
                    )
                }
        } catch (timeout: SocketTimeoutException) {
            throw ChatTransportException(
                ChatError(ChatErrorKind.TIMEOUT, "انتهت مهلة الاتصال بالمزود"),
            )
        } catch (io: IOException) {
            throw ChatTransportException(
                ChatError(ChatErrorKind.NETWORK, "تعذر الاتصال بالمزود: ${io.javaClass.simpleName}"),
            )
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

class OkHttpStreamingTransport(
    private val client: OkHttpClient = OkHttpHttpTransport.defaultClient(),
) : StreamingTransport {

    override fun postStream(
        url: String,
        headers: Map<String, String>,
        body: String,
        timeoutMs: Long,
    ): Flow<String> = channelFlow {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON_MEDIA))
            .header("Accept", "text/event-stream")
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()

        val call = client.newBuilder()
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
            .newCall(request)

        coroutineContext.job.invokeOnCompletion { call.cancel() }

        val job = launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw ChatTransportException(
                            ChatAdapters.errorFor(
                                status = response.code,
                                body = response.body?.string().orEmpty(),
                                retryAfterSeconds = response.retryAfterSeconds(),
                            ),
                        )
                    }
                    val source = response.body?.source()
                        ?: throw ChatTransportException(
                            ChatError(ChatErrorKind.INVALID_RESPONSE, "استجابة بث فارغة"),
                        )
                    while (isActive) {
                        val line = source.readUtf8Line() ?: break
                        send(line)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (transport: ChatTransportException) {
                throw transport
            } catch (io: IOException) {
                if (isActive) {
                    throw ChatTransportException(
                        ChatError(
                            ChatErrorKind.NETWORK,
                            "انقطع الاتصال أثناء البث: ${io.javaClass.simpleName}",
                        ),
                    )
                }
            }
        }
        job.join()
    }.flowOn(Dispatchers.IO)
}
