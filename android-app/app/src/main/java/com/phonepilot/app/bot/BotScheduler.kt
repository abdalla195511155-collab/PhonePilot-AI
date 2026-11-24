package com.phonepilot.app.bot

import java.util.Calendar
import java.util.TimeZone

object BotScheduler {
    /**
     * وقت التشغيل القادم بالميلي ثانية، أو null للمحفّزات التي لا تعتمد على الوقت.
     *
     * - EveryMinutes: بعد آخر تشغيل بـ minutes، وإن فات الموعد فالآن. بدون تشغيل سابق: بعد minutes من الآن.
     * - DailyAt: أقرب وقت محلي قادم (بعد [now] تمامًا).
     */
    fun nextRunAt(trigger: BotTrigger, lastRunAt: Long?, now: Long, zone: TimeZone = TimeZone.getDefault()): Long? =
        when (trigger) {
            is BotTrigger.EveryMinutes -> {
                val step = trigger.minutes * 60_000L
                if (lastRunAt == null) now + step else maxOf(lastRunAt + step, now)
            }
            is BotTrigger.DailyAt -> {
                val cal = Calendar.getInstance(zone)
                cal.timeInMillis = now
                cal.set(Calendar.HOUR_OF_DAY, trigger.hour)
                cal.set(Calendar.MINUTE, trigger.minute)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_MONTH, 1)
                cal.timeInMillis
            }
            BotTrigger.Manual, is BotTrigger.NotificationFrom -> null
        }
}
