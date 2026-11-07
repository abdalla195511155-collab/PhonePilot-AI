package com.phonepilot.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AiEndpointValidatorTest {

    @Test
    fun `blank endpoint is rejected`() {
        assertEquals(
            "أدخل نقطة نهاية الخدمة",
            AiEndpointValidator.validate("   "),
        )
    }

    @Test
    fun `http and https endpoints are accepted`() {
        assertNull(AiEndpointValidator.validate("https://api.example.com/v1"))
        assertNull(AiEndpointValidator.validate("http://localhost:11434/v1"))
    }

    @Test
    fun `missing scheme is rejected`() {
        assertEquals(
            "يجب أن يبدأ الرابط بـ http:// أو https://",
            AiEndpointValidator.validate("api.example.com"),
        )
    }

    @Test
    fun `malformed url is rejected`() {
        assertNotNull(AiEndpointValidator.validate("ht tp://bad host"))
    }
}
