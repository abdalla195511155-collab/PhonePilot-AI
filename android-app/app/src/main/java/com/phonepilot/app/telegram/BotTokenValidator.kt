package com.phonepilot.app.telegram

/**
 * نتيجة التحقق من توكن التيليغرام.
 */
data class BotTokenValidation(
    val isValid: Boolean,
    val message: String,
)

/**
 * مدقق توكن البوت (بدون إرسال طلبات شبكة).
 *
 * يتحقق من تنسيق التوكن فقط، لا يتصل بخدمة التيليغرام الفعلية.
 */
object TelegramTokenValidator {

    /**
     * يتحقق من أن تنسيق التوكن يبدو صحيحاً (رقمين:nقطة:nقطة...).
     * لا يتصل بالتيليغرام ولا يُرسل أي بيانات.
     */
    fun validateFormat(token: String?): BotTokenValidation {
        if (token == null || token.isBlank()) {
            return BotTokenValidation(
                isValid = false,
                message = "التوكن فارغ",
            )
        }

        // تنسيق توكن التيليغرام: 123456789:ABCdefGHIjklMNOpqrsTUVwxyz
        val parts = token.split(":")
        if (parts.size != 2) {
            return BotTokenValidation(
                isValid = false,
                message = "تنسيق التوكن غير صحيح: يجب أن يحتوي على نقطتين ",
            )
        }

        val botId = parts[0]
        val botToken = parts[1]

        if (botId.length < 5 || !botId.all { it.isDigit() }) {
            return BotTokenValidation(
                isValid = false,
                message = "معرف البوت غير صالح",
            )
        }

        if (botToken.length < 10) {
            return BotTokenValidation(
                isValid = false,
                message = "توكن البوت قصير جداً",
            )
        }

        return BotTokenValidation(
            isValid = true,
            message = "تنسيق التوكن صحيح (لم يتم التحقق من conectivity)",
        )
    }
}
