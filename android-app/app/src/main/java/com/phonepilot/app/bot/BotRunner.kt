package com.phonepilot.app.bot

import kotlinx.coroutines.delay

/** ما يحتاجه المشغّل من الهاتف. التنفيذ الفعلي عبر خدمة الوصولية، والاختبارات تستخدم نسخة وهمية. */
interface ScreenActions {
    suspend fun openApp(packageName: String): Boolean
    suspend fun typeText(text: String, target: String?): Boolean
    suspend fun click(target: String): Boolean
    suspend fun tap(x: Int, y: Int): Boolean
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): Boolean
    suspend fun back(): Boolean
    fun screenText(): String?
    fun foregroundPackage(): String?
}

enum class BotRunStatus {
    COMPLETED,
    INVALID,
    LIMIT_REACHED,
    CONDITION_NOT_MET,
    NOT_APPROVED,
    STEP_FAILED,
    UNEXPECTED_SCREEN,
    STOPPED_BY_USER,
}

data class BotStepLog(val stepNumber: Int, val description: String, val ok: Boolean, val attempts: Int, val note: String? = null)

data class BotRunResult(
    val status: BotRunStatus,
    val log: List<BotStepLog>,
    val failedStep: Int? = null,
    val reason: String? = null,
)

class BotRunner(
    private val actions: ScreenActions,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val pollIntervalMs: Long = 250L,
) {
    /**
     * يشغّل البوت مرة واحدة.
     *
     * @param approvedForThisRun وافق المستخدم على هذا التشغيل (مطلوب لأي بوت فيه خطوة بأثر خارجي ما لم يكن مصرّحًا له بالتشغيل بدون حضور).
     * @param runsToday عدد مرات التشغيل اليوم حتى الآن.
     * @param shouldStop يُسأل قبل كل خطوة. true يوقف التشغيل فورًا.
     */
    suspend fun run(
        def: BotDefinition,
        approvedForThisRun: Boolean,
        runsToday: Int,
        shouldStop: () -> Boolean = { false },
    ): BotRunResult {
        val errors = BotValidator.validate(def)
        if (errors.isNotEmpty()) return BotRunResult(BotRunStatus.INVALID, emptyList(), reason = errors.joinToString("؛ "))
        if (runsToday >= def.maxRunsPerDay) {
            return BotRunResult(BotRunStatus.LIMIT_REACHED, emptyList(), reason = "بلغ الحد اليومي ${def.maxRunsPerDay}")
        }
        val hasExternal = def.steps.any { it.externalEffect }
        if (hasExternal && !approvedForThisRun && !def.unattendedApproved) {
            return BotRunResult(BotRunStatus.NOT_APPROVED, emptyList(), reason = "تحتاج موافقتك قبل الخطوات ذات الأثر الخارجي")
        }
        for (c in def.conditions) {
            if (!conditionHolds(c)) {
                return BotRunResult(BotRunStatus.CONDITION_NOT_MET, emptyList(), reason = "شرط غير متحقق: $c")
            }
        }

        val allowed = def.allowedPackages + def.steps.mapNotNull { (it.action as? BotAction.OpenApp)?.packageName }
        val log = mutableListOf<BotStepLog>()

        def.steps.forEachIndexed { index, step ->
            val n = index + 1
            if (shouldStop()) {
                return BotRunResult(BotRunStatus.STOPPED_BY_USER, log, failedStep = n, reason = "أوقفه المستخدم")
            }
            // شاشة غير متوقعة: خرجنا من التطبيقات المسموح بها (قبل أي خطوة غير فتح التطبيق)
            if (step.action !is BotAction.OpenApp && allowed.isNotEmpty()) {
                val fg = actions.foregroundPackage()
                if (fg != null && fg !in allowed) {
                    return BotRunResult(BotRunStatus.UNEXPECTED_SCREEN, log, failedStep = n, reason = "ظهرت شاشة من تطبيق غير مسموح: $fg")
                }
            }

            val maxAttempts = if (step.externalEffect) 1 else 1 + def.retriesPerStep
            var attempts = 0
            var ok = false
            var note: String? = null
            while (attempts < maxAttempts && !ok) {
                attempts++
                val done = perform(step.action)
                if (!done) {
                    note = "فشل تنفيذ الإجراء"
                    continue
                }
                if (step.verifyText != null && !waitForText(step.verifyText, step.verifyTimeoutMs)) {
                    note = "لم يظهر النص المتوقع بعد الخطوة"
                    continue
                }
                ok = true
                note = null
            }
            log += BotStepLog(n, describe(step.action), ok, attempts, note)
            if (!ok) {
                // لا نكمل بعد فشل. لا نخمّن حالة الشاشة.
                return BotRunResult(BotRunStatus.STEP_FAILED, log, failedStep = n, reason = note)
            }
        }
        return BotRunResult(BotRunStatus.COMPLETED, log)
    }

    private fun conditionHolds(c: BotCondition): Boolean = when (c) {
        is BotCondition.ForegroundPackageIs -> actions.foregroundPackage() == c.packageName
        is BotCondition.ScreenContainsText -> actions.screenText()?.contains(c.text, ignoreCase = true) == true
    }

    private suspend fun perform(a: BotAction): Boolean = when (a) {
        is BotAction.OpenApp -> actions.openApp(a.packageName)
        is BotAction.TypeText -> actions.typeText(a.text, a.target)
        is BotAction.Click -> actions.click(a.target)
        is BotAction.TapAt -> actions.tap(a.x, a.y)
        is BotAction.Swipe -> actions.swipe(a.x1, a.y1, a.x2, a.y2, a.durationMs)
        BotAction.Back -> actions.back()
        is BotAction.WaitForText -> waitForText(a.text, a.timeoutMs)
    }

    private suspend fun waitForText(text: String, timeoutMs: Long): Boolean {
        var waited = 0L
        while (true) {
            if (actions.screenText()?.contains(text, ignoreCase = true) == true) return true
            if (waited >= timeoutMs) return false
            pause(pollIntervalMs)
            waited += pollIntervalMs
        }
    }

    /** وصف للسجل بدون النص المكتوب (قد يكون حساسًا). */
    private fun describe(a: BotAction): String = when (a) {
        is BotAction.OpenApp -> "فتح ${a.packageName}"
        is BotAction.TypeText -> "كتابة نص (${a.text.length} حرفًا)"
        is BotAction.Click -> "نقر على: ${a.target}"
        is BotAction.TapAt -> "لمس (${a.x}, ${a.y})"
        is BotAction.Swipe -> "سحب"
        BotAction.Back -> "رجوع"
        is BotAction.WaitForText -> "انتظار نص: ${a.text}"
    }
}
