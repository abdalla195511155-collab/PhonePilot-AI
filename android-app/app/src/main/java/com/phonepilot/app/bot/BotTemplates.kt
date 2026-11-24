package com.phonepilot.app.bot

/**
 * قوالب جاهزة للبدء. القوالب لا تُفعَّل ولا تُرسل شيئًا: المستخدم يراجعها ويعدّل النص والهدف ثم يحفظ.
 */
object BotTemplates {
    const val WHATSAPP = "com.whatsapp"

    /** إرسال رسالة واتساب إلى جهة محددة في وقت يومي. الإرسال خطوة ذات أثر خارجي ولا تُعاد. */
    fun scheduledWhatsApp(id: String, contact: String, message: String, hour: Int, minute: Int): BotDefinition =
        BotDefinition(
            id = id,
            name = "رسالة واتساب مجدولة: $contact",
            trigger = BotTrigger.DailyAt(hour, minute),
            steps = listOf(
                BotStep(BotAction.OpenApp(WHATSAPP), verifyText = "WhatsApp", verifyTimeoutMs = 6_000L),
                BotStep(BotAction.Click("بحث"), verifyTimeoutMs = 3_000L),
                BotStep(BotAction.TypeText(contact), verifyText = contact, verifyTimeoutMs = 4_000L),
                BotStep(BotAction.Click(contact), verifyText = "رسالة", verifyTimeoutMs = 4_000L),
                BotStep(BotAction.TypeText(message, target = "رسالة"), verifyText = message, verifyTimeoutMs = 3_000L),
                BotStep(BotAction.Click("إرسال"), externalEffect = true),
            ),
            allowedPackages = setOf(WHATSAPP),
            maxRunsPerDay = 1,
            unattendedApproved = false,
        )

    /** بوت يدوي فارغ لتعديله. */
    fun blankManual(id: String): BotDefinition =
        BotDefinition(
            id = id,
            name = "بوت جديد",
            steps = listOf(BotStep(BotAction.OpenApp(WHATSAPP))),
        )
}
