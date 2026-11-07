package com.phonepilot.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelDiscoveryParserTest {

    @Test
    fun `provider returning only ids yields models without invented capabilities`() {
        val json = """
            {"object":"list","data":[
              {"id":"meta/llama-3.1-8b-instruct","object":"model","owned_by":"meta"}
            ]}
        """.trimIndent()

        val models = ModelDiscoveryParser.parse(
            baseUrl = "https://integrate.api.nvidia.com/v1",
            json = json,
            verifiedAtEpochMs = 1_700_000_000_000L,
        )

        assertEquals(1, models.size)
        assertEquals("meta/llama-3.1-8b-instruct", models[0].id)
        assertTrue(models[0].capabilities.isEmpty())
        assertNull(models[0].contextTokens)
        assertNull(models[0].pricing)
    }

    @Test
    fun `metadata from the provider is mapped with evidence`() {
        val json = """
            {"data":[{
              "id":"example/multimodal",
              "name":"Multimodal Model",
              "context_length":128000,
              "architecture":{"input_modalities":["text","image"],"output_modalities":["text"]},
              "supported_parameters":["tools","reasoning","temperature"],
              "pricing":{"prompt":"0.0000005","completion":"0.0000015"}
            }]}
        """.trimIndent()

        val models = ModelDiscoveryParser.parse(
            baseUrl = "https://api.example.com/v1",
            json = json,
            verifiedAtEpochMs = 1_700_000_000_000L,
        )

        assertEquals(1, models.size)
        val model = models[0]
        assertEquals("Multimodal Model", model.displayName)
        assertEquals(128000, model.contextTokens)
        assertTrue(model.hasCapability(ModelCapability.TEXT))
        assertTrue(model.hasCapability(ModelCapability.VISION))
        assertTrue(model.hasCapability(ModelCapability.TOOLS))
        assertTrue(model.hasCapability(ModelCapability.REASONING))
        assertEquals(
            "https://api.example.com/v1/models",
            model.capabilities.first().sourceUrl,
        )
        assertEquals(0.5, model.pricing?.inputPerMillionUsd ?: 0.0, 0.0001)
        assertEquals(1.5, model.pricing?.outputPerMillionUsd ?: 0.0, 0.0001)
    }

    @Test
    fun `malformed payload yields an empty list`() {
        assertTrue(ModelDiscoveryParser.parse("https://x/v1", "not json", 0L).isEmpty())
        assertTrue(ModelDiscoveryParser.parse("https://x/v1", "{}", 0L).isEmpty())
    }

    @Test
    fun `models url is normalised`() {
        assertEquals(
            "https://api.example.com/v1/models",
            ModelDiscoveryParser.modelsUrl("https://api.example.com/v1/"),
        )
    }
}
