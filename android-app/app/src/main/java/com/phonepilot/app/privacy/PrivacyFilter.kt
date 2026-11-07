package com.phonepilot.app.privacy

import android.view.accessibility.AccessibilityNodeInfo
import com.phonepilot.app.data.SettingsRepository
import com.phonepilot.app.models.EventEntity
import org.json.JSONArray
import org.json.JSONObject

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
        if (!SettingsRepository.isPrivacyFilterEnabled()) return false
        packageName ?: return false
        return sensitivePackages.any { packageName.contains(it, ignoreCase = true) }
    }

    fun isSensitiveText(text: String?): Boolean {
        if (!SettingsRepository.isPrivacyFilterEnabled()) return false
        text ?: return false
        return sensitiveKeywords.any { text.contains(it, ignoreCase = true) }
    }

    /** إخفاء نص بسيط إذا كان حساساً (يُستخدم لعناصر غير EventEntity مثل وصف العروض). */
    fun maskEventText(text: String?): String {
        if (text == null) return ""
        if (!isSensitiveText(text)) return text
        return "[مخفي]"
    }

    /** التحقق من حساسية العقدة عبر isPassword والحزم والكلمات المفتاحية */
    fun isNodeSensitive(node: AccessibilityNodeInfo, packageName: String?): Boolean {
        if (!SettingsRepository.isPrivacyFilterEnabled()) return false
        if (node.isPassword) return true
        if (isSensitivePackage(packageName)) return true
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        return isSensitiveText(text) || isSensitiveText(desc)
    }

    fun maskEventIfNeeded(event: EventEntity): EventEntity {
        if (!SettingsRepository.isPrivacyFilterEnabled()) return event
        var maskedText = event.text
        var maskedContentDesc = event.contentDescription
        var maskedInput = event.inputText

        val pkg = event.packageName
        val pkgSensitive = isSensitivePackage(pkg)
        val textSensitive = pkgSensitive || isSensitiveText(event.text)
        val descSensitive = pkgSensitive || isSensitiveText(event.contentDescription)
        val inputSensitive = pkgSensitive || isSensitiveText(event.inputText)

        if (textSensitive) {
            maskedText = "[MASKED]"
        }
        if (descSensitive) {
            maskedContentDesc = "[MASKED]"
        }
        if (inputSensitive) {
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

    /** تطبيق القناع على نص الشجرة (snapshot) - تُستبدل النصوص الحساسة بـ [MASKED] */
    fun maskSnapshotJson(snapshotJson: String, packageName: String?): String {
        if (!SettingsRepository.isPrivacyFilterEnabled()) return snapshotJson
        if (snapshotJson == "null") return snapshotJson
        try {
            val arr = JSONArray(snapshotJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val depth = obj.optInt("depth", 0)
                val className = obj.optString("class", "")
                val viewId = obj.optString("viewId", "")
                val text = obj.optString("text", "")
                val desc = obj.optString("desc", "")

                // التحقق من الحساسية: نص أو وصف يحتوي كلمات حساسة، أو حزمة حساسة
                val textSensitive = isSensitiveText(text)
                val descSensitive = isSensitiveText(desc)
                val pkgSensitive = isSensitivePackage(packageName)

                if (textSensitive || pkgSensitive) {
                    obj.put("text", "[MASKED]")
                }
                if (descSensitive || pkgSensitive) {
                    obj.put("desc", "[MASKED]")
                }
            }
            return arr.toString()
        } catch (e: Exception) {
            // في حالة فشل التحليل، إرجاع مُقنّع بالكامل للأمان
            return "[MASKED_SNAPSHOT]"
        }
    }
}
