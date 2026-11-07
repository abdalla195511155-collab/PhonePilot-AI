package com.phonepilot.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NvidiaCatalogTest {

    @Test
    fun `every seeded capability carries evidence`() {
        val models = NvidiaCatalog.seedModels()
        assertTrue(models.isNotEmpty())
        models.forEach { model ->
            model.capabilities.forEach { evidence ->
                assertTrue(
                    "capability without source in ${model.id}",
                    evidence.sourceUrl.isNotBlank(),
                )
                assertTrue(
                    "capability without verification date in ${model.id}",
                    evidence.verifiedAtEpochMs > 0L,
                )
            }
        }
    }

    @Test
    fun `built-in provider does not invent a rate limit`() {
        val provider = NvidiaCatalog.seedProvider()
        assertNull(provider.accountRateLimit)
        provider.models.forEach { model ->
            assertNull("unexpected model limit on ${model.id}", model.rateLimit)
        }
    }

    @Test
    fun `merged catalog keeps evidence and never guesses for unknown models`() {
        val merged = NvidiaCatalog.mergeFetchedModels(
            listOf(
                "meta/llama-3.1-8b-instruct",
                "vendor/unknown-model",
            ),
        )

        val known = merged.first { it.id == "meta/llama-3.1-8b-instruct" }
        assertTrue(known.hasCapability(ModelCapability.TOOLS))

        val unknown = merged.first { it.id == "vendor/unknown-model" }
        assertTrue(unknown.capabilities.isEmpty())
    }

    @Test
    fun `rate limit formatter never invents a value`() {
        assertNull(RateLimitFormatter.describe(null))

        val declared = RateLimitSpec(
            requests = 40,
            window = LimitWindow.MINUTE,
            scope = LimitScope.ACCOUNT,
            source = LimitSource.NVIDIA_OFFICIAL,
        )
        assertEquals("40 طلب / دقيقة لكل الحساب", RateLimitFormatter.describe(declared))
        assertEquals(
            "بيان رسمي من NVIDIA",
            RateLimitFormatter.sourceLabel(declared.source),
        )
    }
}
