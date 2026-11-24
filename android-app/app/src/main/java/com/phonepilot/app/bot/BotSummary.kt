package com.phonepilot.app.bot

object BotSummary {
    fun trigger(t: BotTrigger): String = when (t) {
        BotTrigger.Manual -> "يدوي"
        is BotTrigger.EveryMinutes -> "كل ${t.minutes} دقيقة"
        is BotTrigger.DailyAt -> "يوميًا ${"%02d".format(t.hour)}:${"%02d".format(t.minute)}"
        is BotTrigger.NotificationFrom -> "عند إشعار من ${t.packageName} (يحتاج إذن الوصول للإشعارات)"
    }

    fun hasExternalEffect(def: BotDefinition) = def.steps.any { it.externalEffect }

    /** نص حالة قصير للقائمة. */
    fun status(state: BotState): String = when {
        state.lastStatus == null -> if (state.enabled) "مفعّل، لم يعمل بعد" else "غير مفعّل"
        else -> (if (state.enabled) "مفعّل" else "غير مفعّل") + " • آخر نتيجة: " + state.lastStatus
    }

    /** البوتات ذات المحفّز المبني على إشعار لا تعمل حتى يُبنى مستمع الإشعارات. */
    fun triggerSupported(@Suppress("UNUSED_PARAMETER") t: BotTrigger) = true
}
