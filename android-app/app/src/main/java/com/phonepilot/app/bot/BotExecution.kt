package com.phonepilot.app.bot

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** تشغيل بوت مرة واحدة مع تحديث حالته (عدّاد اليوم وآخر نتيجة). */
object BotExecution {
    fun dayKey(now: Long, zone: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = zone }.format(now)

    suspend fun runOnce(
        def: BotDefinition,
        store: BotStore,
        runner: BotRunner,
        approvedForThisRun: Boolean,
        now: Long = System.currentTimeMillis(),
        zone: TimeZone = TimeZone.getDefault(),
        shouldStop: () -> Boolean = { false },
    ): BotRunResult {
        val before = store.state(def.id)
        val today = dayKey(now, zone)
        val runsToday = if (before.runsDay == today) before.runsToday else 0
        val result = runner.run(def, approvedForThisRun, runsToday, shouldStop)
        // التشغيل الذي لم يبدأ أي خطوة (حد أو عدم موافقة أو شرط أو تعريف غير صالح) لا يُحسب
        val counted = result.status !in setOf(
            BotRunStatus.INVALID, BotRunStatus.LIMIT_REACHED, BotRunStatus.NOT_APPROVED, BotRunStatus.CONDITION_NOT_MET,
        )
        store.setState(
            def.id,
            before.copy(
                lastRunAt = now,
                lastStatus = result.status.name + (result.reason?.let { ": $it" } ?: ""),
                runsToday = if (counted) runsToday + 1 else runsToday,
                runsDay = today,
            ),
        )
        return result
    }
}
