package com.phonepilot.app.data

import java.net.URI

/**
 * Pure validation shared by the provider screen and its unit test.
 * Returns a user-facing Arabic error, or null when the endpoint is usable.
 */
object AiEndpointValidator {

    fun validate(endpoint: String): String? {
        val value = endpoint.trim()
        if (value.isEmpty()) return "أدخل نقطة نهاية الخدمة"
        val uri = try {
            URI(value)
        } catch (error: Exception) {
            return "صيغة الرابط غير صحيحة"
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return "يجب أن يبدأ الرابط بـ http:// أو https://"
        }
        if (uri.host.isNullOrBlank()) {
            return "الرابط لا يحتوي على نطاق صالح"
        }
        return null
    }
}
