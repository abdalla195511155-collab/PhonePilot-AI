package com.phonepilot.app.bot

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.TimeZone

private class QuietScreen(var text: String = "ok", var fg: String? = "com.whatsapp") : ScreenActions {
    var calls = 0
    override suspend fun openApp(packageName: String): Boolean { calls++; return true }
    override suspend fun typeText(text: String, target: String?): Boolean { calls++; return true }
    override suspend fun click(target: String): Boolean { calls++; return true }
    override suspend fun tap(x: Int, y: Int): Boolean { calls++; return true }
    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): Boolean { calls++; return true }
    override suspend fun back(): Boolean { calls++; return true }
    override fun screenText(): String? = text
    override fun foregroundPackage(): String? = fg
}

class BotPersistenceTest {
    private val full = BotDefinition(
        id = "b1", name = "اختبار",
        trigger = BotTrigger.DailyAt(9, 30),
        conditions = listOf(BotCondition.ForegroundPackageIs("com.whatsapp"), BotCondition.ScreenContainsText("x")),
        steps = listOf(
            BotStep(BotAction.OpenApp("com.whatsapp"), verifyText = "ok"),
            BotStep(BotAction.TypeText("مرحبا", "رسالة")),
            BotStep(BotAction.Click("إرسال"), externalEffect = true),
            BotStep(BotAction.TapAt(1, 2)),
            BotStep(BotAction.Swipe(1, 2, 3, 4, 500L)),
            BotStep(BotAction.Back),
            BotStep(BotAction.WaitForText("تم", 1000L)),
        ),
        allowedPackages = setOf("com.android.chrome"),
        maxRunsPerDay = 3, retriesPerStep = 2, unattendedApproved = true,
    )

    @Test fun `json round trip keeps every field`() {
        assertEquals(full, BotJson.parse(BotJson.toJson(full)))
        assertEquals(full, BotJson.parse(BotJson.toPrettyJson(full)))
    }

    @Test fun `every trigger type round trips`() {
        listOf(
            BotTrigger.Manual, BotTrigger.EveryMinutes(30), BotTrigger.DailyAt(1, 2),
            BotTrigger.NotificationFrom("com.whatsapp", "عنوان"), BotTrigger.NotificationFrom("com.whatsapp"),
        ).forEach {
            val d = full.copy(trigger = it)
            assertEquals(d, BotJson.parse(BotJson.toJson(d)))
        }
    }

    @Test fun `unknown action is rejected not ignored`() {
        val bad = """{"id":"a","name":"n","steps":[{"action":"delete_everything"}]}"""
        try { BotJson.parse(bad); fail("expected ParseException") } catch (e: BotJson.ParseException) {
            assertTrue(e.message!!.contains("delete_everything"))
        }
    }

    @Test fun `unknown trigger and broken json are rejected`() {
        try { BotJson.parse("""{"id":"a","name":"n","trigger":{"type":"magic"},"steps":[]}"""); fail() } catch (e: BotJson.ParseException) {}
        try { BotJson.parse("{not json"); fail() } catch (e: BotJson.ParseException) {}
        try { BotJson.parse("[1]"); fail() } catch (e: BotJson.ParseException) {}
        try { BotJson.parse("""{"name":"n","steps":[]}"""); fail() } catch (e: BotJson.ParseException) {}
    }

    @Test fun `defaults apply when optional fields are missing`() {
        val d = BotJson.parse("""{"id":"a","name":"n","steps":[{"action":"back"}]}""")
        assertEquals(BotTrigger.Manual, d.trigger)
        assertEquals(12, d.maxRunsPerDay)
        assertFalse(d.unattendedApproved)
        assertFalse(d.steps[0].externalEffect)
    }

    @Test fun `store saves lists and deletes with state`() {
        val s = InMemoryBotStore()
        s.save(full)
        assertEquals(listOf(full), s.list())
        s.setState("b1", BotState(enabled = true))
        assertTrue(s.state("b1").enabled)
        s.delete("b1")
        assertTrue(s.list().isEmpty())
        assertFalse(s.state("b1").enabled)
    }

    @Test fun `whatsapp template needs explicit unattended approval before it can be scheduled`() {
        val t = BotTemplates.scheduledWhatsApp("w", "Ali", "hi", 9, 0)
        assertTrue(t.steps.last().externalEffect)
        assertFalse(t.unattendedApproved)
        assertTrue(BotValidator.validate(t).any { it.contains("موافقة") })
        assertTrue(BotValidator.validate(t.copy(unattendedApproved = true)).isEmpty())
        assertTrue(BotValidator.validate(t.copy(trigger = BotTrigger.Manual)).isEmpty())
    }

    @Test fun `blank template is valid`() {
        assertTrue(BotValidator.validate(BotTemplates.blankManual("x")).isEmpty())
    }

    @Test fun `execution counts runs per day and resets next day`() = runBlocking {
        val store = InMemoryBotStore()
        val def = BotDefinition("d", "n", steps = listOf(BotStep(BotAction.Back)), maxRunsPerDay = 2)
        store.save(def)
        val runner = BotRunner(QuietScreen(), pause = {})
        val zone = TimeZone.getTimeZone("UTC")
        val day1 = 1_700_000_000_000L
        assertEquals(BotRunStatus.COMPLETED, BotExecution.runOnce(def, store, runner, true, day1, zone).status)
        assertEquals(BotRunStatus.COMPLETED, BotExecution.runOnce(def, store, runner, true, day1 + 1000, zone).status)
        assertEquals(BotRunStatus.LIMIT_REACHED, BotExecution.runOnce(def, store, runner, true, day1 + 2000, zone).status)
        assertEquals(2, store.state("d").runsToday)
        assertNotNull(store.state("d").lastStatus)
        assertEquals(BotRunStatus.COMPLETED, BotExecution.runOnce(def, store, runner, true, day1 + 86_400_000L, zone).status)
        assertEquals(1, store.state("d").runsToday)
    }

    @Test fun `unapproved external step is not counted and sends nothing`() = runBlocking {
        val store = InMemoryBotStore()
        val screen = QuietScreen()
        val def = BotDefinition("e", "n", steps = listOf(BotStep(BotAction.Click("send"), externalEffect = true)))
        store.save(def)
        val r = BotExecution.runOnce(def, store, BotRunner(screen, pause = {}), approvedForThisRun = false)
        assertEquals(BotRunStatus.NOT_APPROVED, r.status)
        assertEquals(0, screen.calls)
        assertEquals(0, store.state("e").runsToday)
    }

    @Test fun `summary text`() {
        assertEquals("يوميًا 09:05", BotSummary.trigger(BotTrigger.DailyAt(9, 5)))
        assertTrue(BotSummary.triggerSupported(BotTrigger.NotificationFrom("com.whatsapp")))
        assertTrue(BotSummary.triggerSupported(BotTrigger.Manual))
        assertEquals("غير مفعّل", BotSummary.status(BotState()))
    }
}
