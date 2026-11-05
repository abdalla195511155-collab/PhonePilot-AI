package com.phonepilot.app.privacy

import com.phonepilot.app.models.EventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyFilterTest {

    @Test
    fun `isSensitivePackage returns true for known sensitive package`() {
        assertTrue(PrivacyFilter.isSensitivePackage("com.whatsapp"))
        assertTrue(PrivacyFilter.isSensitivePackage("org.telegram.messenger"))
        assertTrue(PrivacyFilter.isSensitivePackage("com.paypal.android"))
    }

    @Test
    fun `isSensitivePackage returns false for unknown package`() {
        assertFalse(PrivacyFilter.isSensitivePackage("com.phonepilot.app"))
        assertFalse(PrivacyFilter.isSensitivePackage("com.android.settings"))
    }

    @Test
    fun `isSensitivePackage returns false for null`() {
        assertFalse(PrivacyFilter.isSensitivePackage(null))
    }

    @Test
    fun `isSensitiveText returns true for sensitive keyword`() {
        assertTrue(PrivacyFilter.isSensitiveText("كلمة مرور"))
        assertTrue(PrivacyFilter.isSensitiveText("password"))
        assertTrue(PrivacyFilter.isSensitiveText("تحويل"))
        assertTrue(PrivacyFilter.isSensitiveText("pin"))
    }

    @Test
    fun `isSensitiveText returns false for non-sensitive text`() {
        assertFalse(PrivacyFilter.isSensitiveText("مرحبا بالعالم"))
        assertFalse(PrivacyFilter.isSensitiveText("إعدادات Wi-Fi"))
    }

    @Test
    fun `isSensitiveText returns false for null`() {
        assertFalse(PrivacyFilter.isSensitiveText(null))
    }

    @Test
    fun `maskEventIfNeeded masks text for sensitive package`() {
        val event = EventEntity(
            recordingId = 1,
            sequenceNumber = 1,
            eventType = "input",
            packageName = "com.whatsapp",
            className = "android.widget.EditText",
            viewId = "com.whatsapp:id/input",
            text = "我的密码123",
            contentDescription = "حقل إدخال",
            bounds = "{\"left\":0,\"top\":0,\"right\":100,\"bottom\":50}",
            inputText = "私のパスワード",
            timestamp = System.currentTimeMillis(),
            beforeSnapshot = null,
            afterSnapshot = null
        )
        val masked = PrivacyFilter.maskEventIfNeeded(event)
        assertEquals("[MASKED]", masked.text)
        assertEquals("[MASKED]", masked.inputText)
        assertTrue(masked.isPrivacyMasked)
    }

    @Test
    fun `maskEventIfNeeded masks text for sensitive keyword`() {
        val event = EventEntity(
            recordingId = 1,
            sequenceNumber = 1,
            eventType = "input",
            packageName = "com.phonepilot.app",
            className = "android.widget.EditText",
            viewId = "app:id/input",
            text = "أدخل كلمة المرور هنا",
            contentDescription = null,
            bounds = null,
            inputText = "secret123",
            timestamp = System.currentTimeMillis(),
            beforeSnapshot = null,
            afterSnapshot = null
        )
        val masked = PrivacyFilter.maskEventIfNeeded(event)
        assertEquals("[MASKED]", masked.text)
        assertEquals("[MASKED]", masked.inputText)
        assertTrue(masked.isPrivacyMasked)
    }

    @Test
    fun `maskEventIfNeeded does not mask non-sensitive event`() {
        val event = EventEntity(
            recordingId = 1,
            sequenceNumber = 1,
            eventType = "click",
            packageName = "com.android.settings",
            className = "android.widget.Button",
            viewId = "android:id/button1",
            text = "Wi-Fi",
            contentDescription = "تفعيل الواي فاي",
            bounds = null,
            inputText = null,
            timestamp = System.currentTimeMillis(),
            beforeSnapshot = null,
            afterSnapshot = null
        )
        val masked = PrivacyFilter.maskEventIfNeeded(event)
        assertEquals("Wi-Fi", masked.text)
        assertEquals("تفعيل الواي فاي", masked.contentDescription)
        assertFalse(masked.isPrivacyMasked)
    }

    @Test
    fun `maskEventIfNeeded masks content description when sensitive`() {
        val event = EventEntity(
            recordingId = 1,
            sequenceNumber = 1,
            eventType = "focus",
            packageName = "com.phonepilot.app",
            className = "android.widget.EditText",
            viewId = null,
            text = null,
            contentDescription = "أدخل رمز السري",
            bounds = null,
            inputText = null,
            timestamp = System.currentTimeMillis(),
            beforeSnapshot = null,
            afterSnapshot = null
        )
        val masked = PrivacyFilter.maskEventIfNeeded(event)
        assertEquals("[MASKED]", masked.contentDescription)
        assertTrue(masked.isPrivacyMasked)
    }
}
