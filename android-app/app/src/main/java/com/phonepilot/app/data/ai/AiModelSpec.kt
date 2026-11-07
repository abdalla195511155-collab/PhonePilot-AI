package com.phonepilot.app.data.ai

/** Where a provider definition comes from. */
enum class ProviderKind { BUILT_IN_NVIDIA, CUSTOM }

/** Wire protocol used to talk to the provider. */
enum class AiProtocol {
    OPENAI_COMPATIBLE,
    ANTHROPIC_MESSAGES,
    CUSTOM_ADAPTER,
}

/** Capabilities are only shown when there is documented evidence for them. */
enum class ModelCapability { TEXT, VISION, REASONING, TOOLS, STREAMING }

enum class LimitWindow { MINUTE, HOUR, DAY, MONTH }

enum class LimitScope { ACCOUNT, API_KEY, MODEL, PROVIDER }

/** Who declared a limit — NVIDIA itself, the provider API, or the app user. */
enum class LimitSource { NVIDIA_OFFICIAL, PROVIDER_DECLARED, USER_DEFINED }

/**
 * A capability plus the evidence that proves it. Nothing is inferred from a
 * model name: if there is no evidence, the capability is simply absent.
 */
data class CapabilityEvidence(
    val capability: ModelCapability,
    val sourceUrl: String,
    val verifiedAtEpochMs: Long,
    val note: String? = null,
)

/**
 * Rate limit exactly as declared by its source. There is deliberately no
 * default value: an undeclared limit stays null and the UI says so.
 */
data class RateLimitSpec(
    val requests: Int,
    val window: LimitWindow,
    val scope: LimitScope,
    val source: LimitSource,
    val sourceUrl: String? = null,
    val note: String? = null,
)

data class ModelPricing(
    val inputPerMillionUsd: Double? = null,
    val outputPerMillionUsd: Double? = null,
    val sourceUrl: String? = null,
)

data class AiModelSpec(
    val id: String,
    val displayName: String,
    val capabilities: List<CapabilityEvidence> = emptyList(),
    val contextTokens: Int? = null,
    val pricing: ModelPricing? = null,
    val deprecatedAt: String? = null,
    val rateLimit: RateLimitSpec? = null,
    val sourceUrl: String? = null,
) {
    fun hasCapability(capability: ModelCapability): Boolean =
        capabilities.any { it.capability == capability }

    fun capabilitySet(): Set<ModelCapability> =
        capabilities.map { it.capability }.toSet()
}

data class AiProviderSpec(
    val id: String,
    val name: String,
    val kind: ProviderKind,
    val baseUrl: String,
    val protocol: AiProtocol,
    val models: List<AiModelSpec> = emptyList(),
    val accountRateLimit: RateLimitSpec? = null,
    val docsUrl: String? = null,
    val notes: String? = null,
    val hasApiKey: Boolean = false,
    val enabled: Boolean = true,
    val isSyncing: Boolean = false,
    val lastSyncedAtEpochMs: Long? = null,
    val syncError: String? = null,
) {
    val isBuiltIn: Boolean get() = kind == ProviderKind.BUILT_IN_NVIDIA
}

/** Result of the latest catalog/discovery attempt. */
data class SyncResult(
    val addedModels: Int,
    val updatedModels: Int,
    val totalModels: Int,
    val errorMessage: String? = null,
)

data class ConnectionTestResult(
    val ok: Boolean,
    val message: String,
    val latencyMs: Long? = null,
    val modelCount: Int = 0,
)

/**
 * Limit formatting shared by the UI and tests. Returns null when nothing was
 * declared, so callers never invent a number.
 */
object RateLimitFormatter {

    fun describe(limit: RateLimitSpec?): String? {
        if (limit == null) return null
        if (limit.requests <= 0) return null
        val window = when (limit.window) {
            LimitWindow.MINUTE -> "دقيقة"
            LimitWindow.HOUR -> "ساعة"
            LimitWindow.DAY -> "يوم"
            LimitWindow.MONTH -> "شهر"
        }
        val scope = when (limit.scope) {
            LimitScope.ACCOUNT -> "الحساب"
            LimitScope.API_KEY -> "المفتاح"
            LimitScope.MODEL -> "النموذج"
            LimitScope.PROVIDER -> "المزود"
        }
        return "${limit.requests} طلب / $window لكل $scope"
    }

    fun sourceLabel(source: LimitSource): String = when (source) {
        LimitSource.NVIDIA_OFFICIAL -> "بيان رسمي من NVIDIA"
        LimitSource.PROVIDER_DECLARED -> "بيان من واجهة المزود"
        LimitSource.USER_DEFINED -> "حد أدخله المستخدم"
    }
}
