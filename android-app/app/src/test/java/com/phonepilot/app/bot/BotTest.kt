package com.phonepilot.app.bot

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

private class FakeScreen(
    var screen: String = "",
    var foreground: String? = null,
    private val failClicks: Int = 0,
) : ScreenActions {
    val calls = mutableListOf<String>()
    private var clickFailuresLeft = failClicks
    /** عند فتح تطبيق: النص الذي يظهر. */
    val onOpen = mutableMapOf<String, String>()
    /** بعد النقر على هدف: النص الذي يظهر. */
    val onClick = mutableMapOf<String, String>()

    override suspend fun openApp(packageName: String): Boolean {
        calls += "open:$packageName"
        foreground = packageName
        screen = onOpen[packageName] ?: screen
        return true
    }
    override suspend fun typeText(text: String, target: String?): Boolean { calls += "type"; return true }
    override suspend fun click(target: String): Boolean {
        calls += "click:$target"
        if (clickFailuresLeft > 0) { clickFailuresLeft--; return false }
        screen = onClick[target] ?: screen
        return true
    }
    override suspend fun tap(x: Int, y: Int): Boolean { calls += "tap:$x,$y"; return true }
    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): Boolean { calls += "swipe"; return true }
    override suspend fun back(): Boolean { calls += "back"; return true }
    override fun screenText(): String? = screen
    override fun foregroundPackage(): String? = foreground
}

private fun bot(
    steps: List<BotStep>,
    trigger: BotTrigger = BotTrigger.Manual,
    conditions: List<BotCondition> = emptyList(),
    unattended: Boolean = false,
    maxRuns: Int = 12,
    retries: Int = 1,
) = BotDefinition(
    id = "b1", name = "test", trigger = trigger, conditions = conditions, steps = steps,
    maxRunsPerDay = maxRuns, retriesPerStep = retries, unattendedApproved = unattended,
)

private fun runner(screen: ScreenActions) = BotRunner(screen, pause = { }, pollIntervalMs = 100L)

class BotValidatorTest {
    @Test fun `valid bot has no errors`() {
        assertTrue(BotValidator.validate(bot(listOf(BotStep(BotAction.OpenApp("com.example.app"))))).isEmpty())
    }
    @Test fun `empty steps rejected`() {
        assertTrue(BotValidator.validate(bot(emptyList())).isNotEmpty())
    }
    @Test fun `interval under 15 minutes rejected`() {
        val d = bot(listOf(BotStep(BotAction.Back)), trigger = BotTrigger.EveryMinutes(5))
        assertTrue(BotValidator.validate(d).any { it.contains("15") })
    }
    @Test fun `every 120 minutes accepted`() {
        val d = bot(listOf(BotStep(BotAction.Back)), trigger = BotTrigger.EveryMinutes(120))
        assertTrue(BotValidator.validate(d).isEmpty())
    }
    @Test fun `bad package name rejected`() {
        assertTrue(BotValidator.validate(bot(listOf(BotStep(BotAction.OpenApp("not a package"))))).isNotEmpty())
    }
    @Test fun `scheduled bot with external effect needs unattended approval`() {
        val steps = listOf(BotStep(BotAction.Click("Send"), externalEffect = true))
        assertTrue(BotValidator.validate(bot(steps, trigger = BotTrigger.DailyAt(9, 0))).isNotEmpty())
        assertTrue(BotValidator.validate(bot(steps, trigger = BotTrigger.DailyAt(9, 0), unattended = true)).isEmpty())
        assertTrue(BotValidator.validate(bot(steps, trigger = BotTrigger.Manual)).isEmpty())
    }
    @Test fun `invalid daily time rejected`() {
        assertTrue(BotValidator.validate(bot(listOf(BotStep(BotAction.Back)), trigger = BotTrigger.DailyAt(25, 0))).isNotEmpty())
    }
}

class BotSchedulerTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private fun at(h: Int, m: Int, day: Int = 10): Long {
        val c = Calendar.getInstance(utc)
        c.clear(); c.set(2026, Calendar.NOVEMBER, day, h, m, 0)
        return c.timeInMillis
    }

    @Test fun `interval without previous run starts after one interval`() {
        val now = at(10, 0)
        assertEquals(now + 120 * 60_000L, BotScheduler.nextRunAt(BotTrigger.EveryMinutes(120), null, now, utc))
    }
    @Test fun `interval after last run`() {
        val last = at(10, 0)
        assertEquals(at(12, 0), BotScheduler.nextRunAt(BotTrigger.EveryMinutes(120), last, at(11, 0), utc))
    }
    @Test fun `overdue interval runs now`() {
        val now = at(15, 0)
        assertEquals(now, BotScheduler.nextRunAt(BotTrigger.EveryMinutes(120), at(10, 0), now, utc))
    }
    @Test fun `daily later today`() {
        assertEquals(at(18, 30), BotScheduler.nextRunAt(BotTrigger.DailyAt(18, 30), null, at(9, 0), utc))
    }
    @Test fun `daily already passed goes to tomorrow`() {
        assertEquals(at(8, 0, day = 11), BotScheduler.nextRunAt(BotTrigger.DailyAt(8, 0), null, at(9, 0), utc))
    }
    @Test fun `daily exactly now goes to tomorrow`() {
        assertEquals(at(9, 0, day = 11), BotScheduler.nextRunAt(BotTrigger.DailyAt(9, 0), null, at(9, 0), utc))
    }
    @Test fun `manual and notification have no time`() {
        assertNull(BotScheduler.nextRunAt(BotTrigger.Manual, null, 1L, utc))
        assertNull(BotScheduler.nextRunAt(BotTrigger.NotificationFrom("com.example.app"), null, 1L, utc))
    }
}

class BotRunnerTest {
    @Test fun `happy path completes and verifies each step`() = runBlocking {
        val s = FakeScreen()
        s.onOpen["com.example.app"] = "Home"
        s.onClick["Build"] = "Level 20 reached"
        val d = bot(listOf(
            BotStep(BotAction.OpenApp("com.example.app"), verifyText = "Home"),
            BotStep(BotAction.Click("Build"), verifyText = "Level 20"),
        ))
        val r = runner(s).run(d, approvedForThisRun = false, runsToday = 0)
        assertEquals(BotRunStatus.COMPLETED, r.status)
        assertEquals(2, r.log.size)
        assertTrue(r.log.all { it.ok })
    }

    @Test fun `failed verification stops the run and does not continue`() = runBlocking {
        val s = FakeScreen()
        s.onOpen["com.example.app"] = "Home"
        val d = bot(listOf(
            BotStep(BotAction.OpenApp("com.example.app")),
            BotStep(BotAction.Click("Build"), verifyText = "Level 20", verifyTimeoutMs = 300),
            BotStep(BotAction.Click("Collect")),
        ))
        val r = runner(s).run(d, approvedForThisRun = false, runsToday = 0)
        assertEquals(BotRunStatus.STEP_FAILED, r.status)
        assertEquals(2, r.failedStep)
        assertTrue(s.calls.none { it == "click:Collect" })
    }

    @Test fun `non external step is retried once`() = runBlocking {
        val s = FakeScreen(failClicks = 1)
        s.foreground = "com.example.app"
        val d = bot(listOf(BotStep(BotAction.Click("Build"))), retries = 1)
        val r = runner(s).run(d, approvedForThisRun = false, runsToday = 0)
        assertEquals(BotRunStatus.COMPLETED, r.status)
        assertEquals(2, r.log[0].attempts)
    }

    @Test fun `external effect step is never retried`() = runBlocking {
        val s = FakeScreen(failClicks = 1)
        s.foreground = "com.example.app"
        val d = bot(listOf(BotStep(BotAction.Click("Send"), externalEffect = true)), retries = 3)
        val r = runner(s).run(d, approvedForThisRun = true, runsToday = 0)
        assertEquals(BotRunStatus.STEP_FAILED, r.status)
        assertEquals(1, r.log[0].attempts)
        assertEquals(1, s.calls.count { it == "click:Send" })
    }

    @Test fun `external effect needs approval and nothing runs before it`() = runBlocking {
        val s = FakeScreen()
        val d = bot(listOf(
            BotStep(BotAction.OpenApp("com.example.app")),
            BotStep(BotAction.Click("Send"), externalEffect = true),
        ))
        val r = runner(s).run(d, approvedForThisRun = false, runsToday = 0)
        assertEquals(BotRunStatus.NOT_APPROVED, r.status)
        assertTrue(s.calls.isEmpty())
    }

    @Test fun `daily limit stops the run`() = runBlocking {
        val s = FakeScreen()
        val d = bot(listOf(BotStep(BotAction.Back)), maxRuns = 12)
        val r = runner(s).run(d, approvedForThisRun = false, runsToday = 12)
        assertEquals(BotRunStatus.LIMIT_REACHED, r.status)
        assertTrue(s.calls.isEmpty())
    }

    @Test fun `unmet condition does nothing`() = runBlocking {
        val s = FakeScreen(screen = "Settings", foreground = "com.other.app")
        val d = bot(listOf(BotStep(BotAction.Back)), conditions = listOf(BotCondition.ScreenContainsText("Event")))
        val r = runner(s).run(d, approvedForThisRun = false, runsToday = 0)
        assertEquals(BotRunStatus.CONDITION_NOT_MET, r.status)
        assertTrue(s.calls.isEmpty())
    }

    @Test fun `unexpected app on screen stops the run`() = runBlocking {
        val s = FakeScreen()
        s.onOpen["com.example.app"] = "Home"
        val d = bot(listOf(
            BotStep(BotAction.OpenApp("com.example.app")),
            BotStep(BotAction.Click("Build")),
        ))
        // بعد الفتح يظهر تطبيق آخر (مثلًا نافذة نظام)
        val screen = object : ScreenActions by s {
            override suspend fun openApp(packageName: String): Boolean { s.openApp(packageName); s.foreground = "com.popup.ads"; return true }
        }
        val r = runner(screen).run(d, approvedForThisRun = false, runsToday = 0)
        assertEquals(BotRunStatus.UNEXPECTED_SCREEN, r.status)
        assertEquals(2, r.failedStep)
        assertTrue(s.calls.none { it.startsWith("click") })
    }

    @Test fun `user stop is honored before the next step`() = runBlocking {
        val s = FakeScreen()
        var stop = false
        val d = bot(listOf(BotStep(BotAction.Back), BotStep(BotAction.Back)))
        val screen = object : ScreenActions by s {
            override suspend fun back(): Boolean { s.back(); stop = true; return true }
        }
        val r = runner(screen).run(d, approvedForThisRun = false, runsToday = 0, shouldStop = { stop })
        assertEquals(BotRunStatus.STOPPED_BY_USER, r.status)
        assertEquals(1, s.calls.count { it == "back" })
    }

    @Test fun `typed text is not written to the log`() = runBlocking {
        val s = FakeScreen()
        s.foreground = "com.example.app"
        val d = bot(listOf(BotStep(BotAction.TypeText("secret-123"))))
        val r = runner(s).run(d, approvedForThisRun = false, runsToday = 0)
        assertTrue(r.log.none { it.description.contains("secret-123") })
    }

    @Test fun `invalid definition is reported`() = runBlocking {
        val r = runner(FakeScreen()).run(bot(emptyList()), approvedForThisRun = false, runsToday = 0)
        assertEquals(BotRunStatus.INVALID, r.status)
    }
}
