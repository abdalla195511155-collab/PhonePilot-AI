package com.phonepilot.app.bot

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.phonepilot.app.recording.PhonePilotAccessibilityService
import java.util.concurrent.TimeUnit

/**
 * جدولة البوتات الزمنية عبر WorkManager.
 *
 * ملاحظة: WorkManager لا يضمن الدقة. قد يتأخر التنفيذ عن الموعد (وفي بعض أجهزة شاومي/MIUI
 * أو مع توفير البطارية قد لا يعمل إلا بعد فتح التطبيق). هذا قيد معروف وليس خطأ في البوت.
 */
object BotScheduling {
    private fun uniqueName(id: String) = "phonepilot_bot_$id"

    /** يضبط التشغيل القادم للبوت، أو يلغيه إن لم يكن مفعّلًا أو كان محفّزه غير زمني. */
    fun reschedule(context: Context, def: BotDefinition, state: BotState, now: Long = System.currentTimeMillis()) {
        val wm = WorkManager.getInstance(context.applicationContext)
        val next = if (state.enabled) BotScheduler.nextRunAt(def.trigger, state.lastRunAt, now) else null
        if (next == null) {
            wm.cancelUniqueWork(uniqueName(def.id))
            return
        }
        val delay = (next - now).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<BotWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString(BotWorker.KEY_BOT_ID, def.id).build())
            .build()
        wm.enqueueUniqueWork(uniqueName(def.id), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context, id: String) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(uniqueName(id))
    }
}

class BotWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_BOT_ID) ?: return Result.failure()
        val store = PrefsBotStore(applicationContext)
        val def = store.get(id) ?: return Result.success()
        val state = store.state(id)
        if (!state.enabled) return Result.success()

        // التشغيل المجدول بدون حضور: لا موافقة لهذا التشغيل. البوت ذو الأثر الخارجي يحتاج unattendedApproved.
        val runner = BotRunner(AccessibilityScreenActions { PhonePilotAccessibilityService.instance })
        BotExecution.runOnce(def, store, runner, approvedForThisRun = false)

        // ضبط الموعد التالي بعد التشغيل (حتى لو فشل: السبب في الحالة، ولا نكرر الخطوات تلقائيًا الآن)
        BotScheduling.reschedule(applicationContext, def, store.state(id))
        return Result.success()
    }

    companion object {
        const val KEY_BOT_ID = "bot_id"
    }
}
