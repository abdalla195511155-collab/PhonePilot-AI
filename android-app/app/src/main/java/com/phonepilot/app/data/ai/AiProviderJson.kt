package com.phonepilot.app.data.ai

import com.google.gson.Gson

/**
 * DTO-based persistence. Gson bypasses Kotlin constructors, so every DTO field
 * is nullable and mapped with defaults — corrupt or older JSON becomes an empty
 * list instead of a crash.
 */
object AiProviderJson {

    private val gson = Gson()

    fun encodeProviders(providers: List<AiProviderSpec>): String =
        gson.toJson(providers.map { provider ->
            ProviderDto(
                id = provider.id,
                name = provider.name,
                baseUrl = provider.baseUrl,
                protocol = provider.protocol.name,
                docsUrl = provider.docsUrl,
                notes = provider.notes,
                limitRequests = provider.accountRateLimit?.requests,
                limitWindow = provider.accountRateLimit?.window?.name,
                enabled = provider.enabled,
                models = provider.models.map { model -> model.toDto() },
            )
        })

    fun decodeProviders(json: String?): List<AiProviderSpec> {
        if (json.isNullOrBlank()) return emptyList()
        val dtos = runCatching { gson.fromJson(json, Array<ProviderDto>::class.java) }
            .getOrNull() ?: return emptyList()
        return dtos.mapNotNull { dto ->
            val id = dto.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = dto.name?.takeIf { it.isNotBlank() } ?: id
            val baseUrl = dto.baseUrl?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val protocol = runCatching { AiProtocol.valueOf(dto.protocol ?: "") }
                .getOrDefault(AiProtocol.OPENAI_COMPATIBLE)
            AiProviderSpec(
                id = id,
                name = name,
                kind = ProviderKind.CUSTOM,
                baseUrl = baseUrl,
                protocol = protocol,
                models = dto.models.orEmpty().mapNotNull { it.toModel() },
                accountRateLimit = dto.limitRequests?.takeIf { it > 0 }?.let { requests ->
                    RateLimitSpec(
                        requests = requests,
                        window = runCatching { LimitWindow.valueOf(dto.limitWindow ?: "") }
                            .getOrDefault(LimitWindow.HOUR),
                        scope = LimitScope.PROVIDER,
                        source = LimitSource.USER_DEFINED,
                        note = "حد أدخله المستخدم لهذا المزود",
                    )
                },
                docsUrl = dto.docsUrl,
                notes = dto.notes,
                enabled = dto.enabled ?: true,
            )
        }
    }

    fun encodeModels(models: List<AiModelSpec>): String =
        gson.toJson(models.map { it.toDto() })

    fun decodeModels(json: String?): List<AiModelSpec> {
        if (json.isNullOrBlank()) return emptyList()
        val dtos = runCatching { gson.fromJson(json, Array<ModelDto>::class.java) }
            .getOrNull() ?: return emptyList()
        return dtos.mapNotNull { it.toModel() }
    }

    private fun AiModelSpec.toDto(): ModelDto = ModelDto(
        id = id,
        displayName = displayName,
        contextTokens = contextTokens,
        deprecatedAt = deprecatedAt,
        inputPerMillionUsd = pricing?.inputPerMillionUsd,
        outputPerMillionUsd = pricing?.outputPerMillionUsd,
        sourceUrl = sourceUrl,
        capabilities = capabilities.map { evidence ->
            CapabilityDto(
                capability = evidence.capability.name,
                sourceUrl = evidence.sourceUrl,
                verifiedAt = evidence.verifiedAtEpochMs,
                note = evidence.note,
            )
        },
    )

    private fun ModelDto.toModel(): AiModelSpec? {
        val modelId = id?.takeIf { it.isNotBlank() } ?: return null
        return AiModelSpec(
            id = modelId,
            displayName = displayName?.takeIf { it.isNotBlank() } ?: modelId,
            capabilities = capabilities.orEmpty().mapNotNull { dto ->
                val capability = runCatching { ModelCapability.valueOf(dto.capability ?: "") }
                    .getOrNull() ?: return@mapNotNull null
                CapabilityEvidence(
                    capability = capability,
                    sourceUrl = dto.sourceUrl ?: "unknown",
                    verifiedAtEpochMs = dto.verifiedAt ?: 0L,
                    note = dto.note,
                )
            },
            contextTokens = contextTokens?.takeIf { it > 0 },
            pricing = if (inputPerMillionUsd == null && outputPerMillionUsd == null) null else ModelPricing(
                inputPerMillionUsd = inputPerMillionUsd,
                outputPerMillionUsd = outputPerMillionUsd,
                sourceUrl = sourceUrl,
            ),
            deprecatedAt = deprecatedAt?.takeIf { it.isNotBlank() },
            sourceUrl = sourceUrl,
        )
    }

    private data class ProviderDto(
        val id: String? = null,
        val name: String? = null,
        val baseUrl: String? = null,
        val protocol: String? = null,
        val docsUrl: String? = null,
        val notes: String? = null,
        val limitRequests: Int? = null,
        val limitWindow: String? = null,
        val enabled: Boolean? = null,
        val models: List<ModelDto>? = null,
    )

    private data class ModelDto(
        val id: String? = null,
        val displayName: String? = null,
        val contextTokens: Int? = null,
        val deprecatedAt: String? = null,
        val inputPerMillionUsd: Double? = null,
        val outputPerMillionUsd: Double? = null,
        val sourceUrl: String? = null,
        val capabilities: List<CapabilityDto>? = null,
    )

    private data class CapabilityDto(
        val capability: String? = null,
        val sourceUrl: String? = null,
        val verifiedAt: Long? = null,
        val note: String? = null,
    )
}
