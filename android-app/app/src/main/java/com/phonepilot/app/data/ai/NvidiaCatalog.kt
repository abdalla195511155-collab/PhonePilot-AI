package com.phonepilot.app.data.ai

/**
 * Built-in NVIDIA API Catalog.
 *
 * Rules encoded here:
 *  - Only models that NVIDIA actually serves (verified against the official
 *    `GET https://integrate.api.nvidia.com/v1/models`, HTTP 200) are listed.
 *  - A capability exists only with a source URL. No capability is inferred from
 *    the model name.
 *  - No rate limit is declared unless NVIDIA documents it. There is no
 *    universal "200 requests/hour" for this catalog, and this constant is never
 *    applied to NVIDIA or to custom providers.
 *  - Limits shown for NVIDIA apply to NVIDIA's own hosted endpoint only; they
 *    say nothing about any intermediary (Harness, Kilo, or another proxy).
 */
object NvidiaCatalog {

    const val PROVIDER_ID = "nvidia-builtin"
    const val BASE_URL = "https://integrate.api.nvidia.com/v1"
    const val MODELS_URL = "$BASE_URL/models"
    const val CHAT_COMPLETIONS_URL = "$BASE_URL/chat/completions"
    const val DOCS_URL = "https://docs.api.nvidia.com/nim/docs/api-quickstart"
    const val MODEL_LIST_URL = "https://build.nvidia.com/models"
    const val FUNCTION_CALLING_DOC =
        "https://docs.nvidia.com/nim/large-language-models/1.8.0/function-calling.html"
    const val VISION_MODEL_PAGE =
        "https://build.nvidia.com/meta/llama-3.2-90b-vision-instruct"
    const val API_REFERENCE_DOC =
        "https://docs.nvidia.com/nim/large-language-models/latest/api-reference.html"

    /**
     * Verified on this device by calling the official models endpoint, which
     * answers without an API key. Kept as a date so stale data is obvious.
     */
    const val CATALOG_VERIFIED_AT = "2026-11-06"

    /** Explanatory text shown in the UI next to NVIDIA limits. */
    const val LIMITS_NOTE =
        "حدود NVIDIA تنطبق على نقطة NVIDIA الرسمية وحدها، ولا تُطبَّق على أي وسيط " +
            "(Harness أو Kilo أو غيرهم). لا توجد قيمة افتراضية مثل 200 طلب/ساعة: " +
            "تُعرض القيمة فقط إن أعلنها المصدر."

    private fun textEvidence(sourceUrl: String, note: String? = null): CapabilityEvidence =
        CapabilityEvidence(
            capability = ModelCapability.TEXT,
            sourceUrl = sourceUrl,
            verifiedAtEpochMs = VERIFIED_AT_MS,
            note = note,
        )

    private fun streamingEvidence(): CapabilityEvidence =
        CapabilityEvidence(
            capability = ModelCapability.STREAMING,
            sourceUrl = API_REFERENCE_DOC,
            verifiedAtEpochMs = VERIFIED_AT_MS,
            note = "توثيق NIM: نقطة chat/completions تدعم البث",
        )

    private fun toolsEvidence(): CapabilityEvidence =
        CapabilityEvidence(
            capability = ModelCapability.TOOLS,
            sourceUrl = FUNCTION_CALLING_DOC,
            verifiedAtEpochMs = VERIFIED_AT_MS,
            note = "مذكور صراحة في قائمة النماذج الداعمة لاستدعاء الأدوات",
        )

    private fun visionEvidenceFromPage(): CapabilityEvidence =
        CapabilityEvidence(
            capability = ModelCapability.VISION,
            sourceUrl = VISION_MODEL_PAGE,
            verifiedAtEpochMs = VERIFIED_AT_MS,
            note = "مثال الطلب الرسمي يرسل content من نوع image_url",
        )

    /** 2026-11-06T00:00:00Z, the date the sources above were checked. */
    private const val VERIFIED_AT_MS = 1_762_387_200_000L

    private val llamaTools = listOf(
        "meta/llama-3.1-8b-instruct",
        "meta/llama-3.1-70b-instruct",
        "meta/llama-3.1-405b-instruct",
    )

    fun seedModels(): List<AiModelSpec> = buildList {
        llamaTools.forEach { id ->
            add(
                AiModelSpec(
                    id = id,
                    displayName = id.substringAfter('/'),
                    capabilities = listOf(
                        textEvidence(DOCS_URL, "متاح عبر /v1/chat/completions"),
                        toolsEvidence(),
                        streamingEvidence(),
                    ),
                    sourceUrl = MODEL_LIST_URL,
                ),
            )
        }

        add(
            AiModelSpec(
                id = "meta/llama-3.2-90b-vision-instruct",
                displayName = "Llama 3.2 90B Vision Instruct",
                capabilities = listOf(
                    textEvidence(DOCS_URL, "متاح عبر /v1/chat/completions"),
                    visionEvidenceFromPage(),
                    streamingEvidence(),
                ),
                deprecatedAt = "2026-11-15",
                sourceUrl = VISION_MODEL_PAGE,
                rateLimit = null,
            ),
        )

        add(
            AiModelSpec(
                id = "nvidia/nemotron-4-340b-instruct",
                displayName = "Nemotron-4 340B Instruct",
                capabilities = listOf(
                    textEvidence(DOCS_URL, "متاح عبر /v1/chat/completions"),
                    streamingEvidence(),
                ),
                sourceUrl = MODEL_LIST_URL,
            ),
        )

        add(
            AiModelSpec(
                id = "nvidia/nemotron-nano-3-30b-a3b",
                displayName = "Nemotron Nano 3 30B A3B",
                capabilities = listOf(
                    textEvidence(DOCS_URL, "متاح عبر /v1/chat/completions"),
                    streamingEvidence(),
                ),
                sourceUrl = MODEL_LIST_URL,
            ),
        )
    }

    fun seedProvider(hasKey: Boolean = false): AiProviderSpec = AiProviderSpec(
        id = PROVIDER_ID,
        name = "NVIDIA API Catalog (مجاني)",
        kind = ProviderKind.BUILT_IN_NVIDIA,
        baseUrl = BASE_URL,
        protocol = AiProtocol.OPENAI_COMPATIBLE,
        models = seedModels(),
        accountRateLimit = null,
        docsUrl = DOCS_URL,
        notes = LIMITS_NOTE,
        hasApiKey = hasKey,
    )

    /**
     * Merges a freshly fetched model list with the documented seed. Fetched
     * models keep any evidence we already have; everything else is added with
     * no capabilities instead of guessed ones.
     */
    fun mergeFetchedModels(fetchedIds: List<String>): List<AiModelSpec> {
        val known = seedModels().associateBy { it.id }
        val merged = linkedMapOf<String, AiModelSpec>()
        fetchedIds.distinct().forEach { id ->
            merged[id] = known[id] ?: AiModelSpec(
                id = id,
                displayName = id.substringAfter('/'),
                capabilities = emptyList(),
                sourceUrl = MODELS_URL,
            )
        }
        known.forEach { (id, model) -> merged.putIfAbsent(id, model) }
        return merged.values.toList()
    }
}
