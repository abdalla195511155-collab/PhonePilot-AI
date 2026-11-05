package com.phonepilot.app.privacy

import com.phonepilot.app.models.EventEntity

object PrivacyFilter {

    /** حزم حساسة لا يُسجَّل نصها، يُسجَّل فقط أنها ظهرت. */
    val sensitivePackages = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "com.instagram.android",
        "com.facebook.katana",
        "com.android.chrome",
        "com.google.android.apps.authenticator",
        "com.bankofmatter",
        "com.paypal.android",
        "com.stripe.android",
    )

    /** كلمات أنماط حساسة لا يُسجَّل نصها. */
    val sensitiveKeywords = setOf(
        "كلمة مرور",
        "password",
        "passcode",
        "رمز",
        "pin",
        "تحويل",
        "دفع",
        "حذف",
        "إرسال نهائي",
    )

    fun isSensitivePackage(packageName: String?): Boolean {
        packageName ?: return false
        return sensitivePackages.any { packageName.contains(it, ignoreCase = true) }
    }

    fun isSensitiveText(text: String?): Boolean {
        text ?: return false
        return sensitiveKeywords.any { text.contains(it, ignoreCase = true) }
    }

    fun maskEventIfNeeded(event: EventEntity): EventEntity {
        var maskedText = event.text
        var maskedContentDesc = event.contentDescription
        var maskedInput = event.inputText

        if (isSensitivePackage(event.packageName) || isSensitiveText(event.text)) {
            maskedText = "[MASKED]"
        }
        if (isSensitiveText(event.contentDescription)) {
            maskedContentDesc = "[MASKED]"
        }
        if (isSensitiveText(event.inputText)) {
            maskedInput = "[MASKED]"
        }

        return if (maskedText != event.text || maskedContentDesc != event.contentDescription || maskedInput != event.inputText) {
            event.copy(
                text = maskedText,
                contentDescription = maskedContentDesc,
                inputText = maskedInput,
                isPrivacyMasked = true
            )
        } else {
            event
        }
    }
}
