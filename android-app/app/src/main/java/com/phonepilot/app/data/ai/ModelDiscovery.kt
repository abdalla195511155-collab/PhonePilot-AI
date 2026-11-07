package com.phonepilot.app.data.ai

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Pure parsing result, kept separate from networking so it can be unit-tested. */
object ModelDiscoveryParser {

    private val gson = Gson()

    fun modelsUrl(baseUrl: String): String = baseUrl.trimEnd('/') + "/models"

    /**
     * Parses an OpenAI-compatible `GET /models` response.
     *
     * Capabilities come only from metadata the provider actually returned
     * (`architecture.*`, `supported_parameters`). Providers that return nothing
     * but ids produce models with no capabilities — never guessed ones.
     */
    fun parse(baseUrl: String, json: String, verifiedAtEpochMs: Long): List<AiModelSpec> {
        val envelope = runCatching {
            gson.fromJson(json, ModelsEnvelope::class.java)
        }.getOrNull() ?: return emptyList()

        val sourceUrl = modelsUrl(baseUrl)
        return envelope.data.orEmpty().mapNotNull { dto ->
            val id = dto.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val inputModalities = dto.architecture?.inputModalities.orEmpty()
            val outputModalities = dto.architecture?.outputModalities.orEmpty()
            val supportedParameters = dto.supportedParameters.orEmpty()

            val evidence = buildList {
                if (outputModalities.any { it.equals("text", ignoreCase = true) }) {
                    add(capability(ModelCapability.TEXT, sourceUrl, verifiedAtEpochMs))
                }
                if (inputModalities.any { it.equals("image", ignoreCase = true) }) {
                    add(capability(ModelCapability.VISION, sourceUrl, verifiedAtEpochMs))
                }
                if (supportedParameters.any { it.equals("tools", ignoreCase = true) }) {
                    add(capability(ModelCapability.TOOLS, sourceUrl, verifiedAtEpochMs))
                }
                if (supportedParameters.any { it.equals("reasoning", ignoreCase = true) }) {
                    add(capability(ModelCapability.REASONING, sourceUrl, verifiedAtEpochMs))
                }
            }

            AiModelSpec(
                id = id,
                displayName = dto.name?.takeIf { it.isNotBlank() } ?: id,
                capabilities = evidence,
                contextTokens = dto.contextLength?.takeIf { it > 0 },
                pricing = dto.pricing?.let { pricing ->
                    ModelPricing(
                        inputPerMillionUsd = pricing.prompt?.toDoubleOrNull()?.times(1_000_000),
                        outputPerMillionUsd = pricing.completion?.toDoubleOrNull()?.times(1_000_000),
                        sourceUrl = sourceUrl,
                    )
                },
                deprecatedAt = if (dto.deprecated == true) "أعلن المزود إيقافه" else null,
                sourceUrl = sourceUrl,
            )
        }
    }

    private fun capability(
        capability: ModelCapability,
        sourceUrl: String,
        verifiedAt: Long,
    ) = CapabilityEvidence(
        capability = capability,
        sourceUrl = sourceUrl,
        verifiedAtEpochMs = verifiedAt,
        note = "بيان من واجهة المزود",
    )

    data class ModelsEnvelope(val data: List<ModelDto>? = null)

    data class ModelDto(
        val id: String? = null,
        val name: String? = null,
        val deprecated: Boolean? = null,
        @SerializedName("context_length") val contextLength: Int? = null,
        val architecture: ArchitectureDto? = null,
        @SerializedName("supported_parameters") val supportedParameters: List<String>? = null,
        val pricing: PricingDto? = null,
    )

    data class ArchitectureDto(
        @SerializedName("input_modalities") val inputModalities: List<String>? = null,
        @SerializedName("output_modalities") val outputModalities: List<String>? = null,
    )

    data class PricingDto(
        val prompt: String? = null,
        val completion: String? = null,
    )
}

/** Thin networking wrapper. Errors are returned as user-facing Arabic text. */
object ModelDiscoveryClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(
        baseUrl: String,
        apiKey: String?,
        verifiedAtEpochMs: Long = System.currentTimeMillis(),
    ): DiscoveryOutcome = withContext(Dispatchers.IO) {
        val url = ModelDiscoveryParser.modelsUrl(baseUrl)
        val request = Request.Builder()
            .url(url)
            .get()
            .apply {
                if (!apiKey.isNullOrBlank()) {
                    header("Authorization", "Bearer $apiKey")
                }
                header("Accept", "application/json")
            }
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> {
                        val models = ModelDiscoveryParser.parse(baseUrl, body, verifiedAtEpochMs)
                        if (models.isEmpty()) {
                            DiscoveryOutcome(
                                models = emptyList(),
                                errorMessage = "لم تُرجع الواجهة أي نموذج",
                            )
                        } else {
                            DiscoveryOutcome(models = models, errorMessage = null)
                        }
                    }

                    response.code == 401 || response.code == 403 ->
                        DiscoveryOutcome(emptyList(), "المفتاح مرفوض (HTTP ${response.code})")

                    response.code == 404 ->
                        DiscoveryOutcome(emptyList(), "المسار /models غير مدعوم من هذا المزود")

                    else ->
                        DiscoveryOutcome(emptyList(), "فشل الطلب (HTTP ${response.code})")
                }
            }
        } catch (error: Exception) {
            // Never include the API key in messages or logs.
            DiscoveryOutcome(emptyList(), "تعذر الاتصال بالمزود: ${error.javaClass.simpleName}")
        }
    }
}

data class DiscoveryOutcome(
    val models: List<AiModelSpec>,
    val errorMessage: String? = null,
)
