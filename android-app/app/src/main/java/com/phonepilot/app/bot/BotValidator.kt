package com.phonepilot.app.bot

object BotValidator {
    const val MIN_INTERVAL_MINUTES = 15
    const val MAX_STEPS = 50
    const val MAX_RUNS_PER_DAY_LIMIT = 48
    private val PACKAGE_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    /** يعيد قائمة أخطاء. القائمة الفارغة تعني أن التعريف صالح. */
    fun validate(def: BotDefinition): List<String> {
        val errors = mutableListOf<String>()
        if (def.id.isBlank()) errors += "معرّف البوت فارغ"
        if (def.name.isBlank()) errors += "اسم البوت فارغ"
        if (def.steps.isEmpty()) errors += "لا توجد خطوات"
        if (def.steps.size > MAX_STEPS) errors += "عدد الخطوات يزيد على $MAX_STEPS"
        if (def.maxRunsPerDay < 1 || def.maxRunsPerDay > MAX_RUNS_PER_DAY_LIMIT) {
            errors += "الحد اليومي للتشغيل يجب أن يكون بين 1 و $MAX_RUNS_PER_DAY_LIMIT"
        }
        if (def.retriesPerStep < 0 || def.retriesPerStep > 3) errors += "عدد إعادة المحاولة يجب أن يكون بين 0 و 3"

        when (val t = def.trigger) {
            is BotTrigger.EveryMinutes ->
                if (t.minutes < MIN_INTERVAL_MINUTES) errors += "الفاصل الزمني أقل من $MIN_INTERVAL_MINUTES دقيقة"
            is BotTrigger.DailyAt ->
                if (t.hour !in 0..23 || t.minute !in 0..59) errors += "وقت يومي غير صالح"
            is BotTrigger.NotificationFrom ->
                if (!PACKAGE_REGEX.matches(t.packageName)) errors += "اسم حزمة المحفّز غير صالح"
            BotTrigger.Manual -> Unit
        }

        def.conditions.forEach { c ->
            when (c) {
                is BotCondition.ForegroundPackageIs ->
                    if (!PACKAGE_REGEX.matches(c.packageName)) errors += "اسم حزمة الشرط غير صالح"
                is BotCondition.ScreenContainsText ->
                    if (c.text.isBlank()) errors += "نص الشرط فارغ"
            }
        }

        def.steps.forEachIndexed { i, s ->
            val n = i + 1
            when (val a = s.action) {
                is BotAction.OpenApp -> if (!PACKAGE_REGEX.matches(a.packageName)) errors += "الخطوة $n: اسم حزمة غير صالح"
                is BotAction.TypeText -> if (a.text.isEmpty()) errors += "الخطوة $n: النص فارغ"
                is BotAction.Click -> if (a.target.isBlank()) errors += "الخطوة $n: هدف النقر فارغ"
                is BotAction.TapAt -> if (a.x < 0 || a.y < 0) errors += "الخطوة $n: إحداثيات سالبة"
                is BotAction.Swipe ->
                    if (a.x1 < 0 || a.y1 < 0 || a.x2 < 0 || a.y2 < 0 || a.durationMs <= 0) errors += "الخطوة $n: سحب غير صالح"
                is BotAction.WaitForText -> if (a.text.isBlank()) errors += "الخطوة $n: نص الانتظار فارغ"
                BotAction.Back -> Unit
            }
        }

        val hasExternal = def.steps.any { it.externalEffect }
        if (hasExternal && def.trigger != BotTrigger.Manual && !def.unattendedApproved) {
            errors += "بوت له خطوات بأثر خارجي ومحفّزه غير يدوي يحتاج موافقة صريحة على التشغيل بدون حضورك"
        }
        return errors
    }
}
