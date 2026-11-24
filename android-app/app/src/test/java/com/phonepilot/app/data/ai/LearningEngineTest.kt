package com.phonepilot.app.data.ai

import com.phonepilot.app.TestDatabase
import com.phonepilot.app.database.TaskDatabaseHelper
import com.phonepilot.app.models.EventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LearningEngine requires at least 5 completed iterations (MIN_ITERATIONS) and
 * compares steps by position using a semantic key (event type, element id, class).
 * These tests record 5 iterations per task through the real database helper.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LearningEngineTest {

    private lateinit var dbHelper: TaskDatabaseHelper

    @Before
    fun setUp() {
        dbHelper = TestDatabase.fresh()
    }

    private fun click(recId: Long, seq: Int, viewId: String, text: String) = EventEntity(
        recordingId = recId, sequenceNumber = seq, eventType = "click",
        viewId = viewId, text = text, timestamp = seq * 1000L,
    )

    private fun input(recId: Long, seq: Int, viewId: String, text: String) = EventEntity(
        recordingId = recId, sequenceNumber = seq, eventType = "input",
        viewId = viewId, text = text, timestamp = seq * 1000L,
    )

    /** Records one completed iteration; [events] builds the events for the new recording id. */
    private fun recordIteration(taskId: Long, iteration: Int, events: (Long) -> List<EventEntity>) {
        val rec = dbHelper.startRecording(taskId, iteration)
        val list = events(rec)
        list.forEach { dbHelper.insertEvent(rec, it) }
        dbHelper.stopRecording(rec, list.size)
    }

    @Test
    fun `learnTask returns null when fewer than minimum iterations`() {
        val engine = LearningEngine(dbHelper)
        val taskId = dbHelper.insertTask("Single Task", null)
        recordIteration(taskId, 1) { listOf(click(it, 1, "btn", "B")) }
        assertNull(engine.learnTask(taskId))

        // Four iterations with identical events are still below the minimum of five.
        val taskId4 = dbHelper.insertTask("Four Iterations", null)
        for (i in 1..4) recordIteration(taskId4, i) { listOf(click(it, 1, "btn", "B")) }
        assertNull(engine.learnTask(taskId4))
    }

    @Test
    fun `learnTask returns null when no events`() {
        val engine = LearningEngine(dbHelper)
        val taskId = dbHelper.insertTask("Empty Task", null)
        for (i in 1..5) recordIteration(taskId, i) { emptyList() }
        assertNull(engine.learnTask(taskId))
    }

    @Test
    fun `learnTask extracts common steps across iterations`() {
        val engine = LearningEngine(dbHelper)
        val taskId = dbHelper.insertTask("Common Steps Task", null)
        for (i in 1..5) {
            recordIteration(taskId, i) { rec ->
                listOf(
                    click(rec, 1, "email_field", "Email"),
                    input(rec, 2, "email_field", "user$i@test.com"),
                    click(rec, 3, "login_btn", "Login"),
                )
            }
        }

        val result = engine.learnTask(taskId)

        assertNotNull(result)
        assertTrue(result!!.description.contains("الخطوات المشتركة (3)"))
        assertTrue(result.description.contains("click → email_field"))
        assertTrue(result.description.contains("input → email_field"))
        assertTrue(result.description.contains("click → login_btn"))
        // typed text differs per iteration and must not leak into the step description
        assertTrue(!result.description.contains("@test.com"))
    }

    @Test
    fun `different text with same element id still matches semantically`() {
        val engine = LearningEngine(dbHelper)
        val taskId = dbHelper.insertTask("Semantic Task", null)
        for (i in 1..5) {
            // same element id, label text and timestamps differ per iteration
            recordIteration(taskId, i) { listOf(click(it, 1, "submit_btn", "Submit $i")) }
        }

        val result = engine.learnTask(taskId)
        assertNotNull(result)
        assertTrue(result!!.description.contains("submit_btn"))
        assertTrue(result.description.contains("الخطوات المشتركة (1)"))
    }

    @Test
    fun `optional and ambiguous steps detected when iterations diverge at a position`() {
        val engine = LearningEngine(dbHelper)
        val taskId = dbHelper.insertTask("Optional Task", null)
        // position 1: btn_a in all iterations (common).
        // position 2: btn_b in 2 of 5, three different others -> match ratio 0.4 (optional + ambiguous).
        val second = listOf("btn_b", "btn_b", "btn_c", "btn_d", "btn_e")
        for (i in 1..5) {
            recordIteration(taskId, i) {
                listOf(click(it, 1, "btn_a", "A"), click(it, 2, second[i - 1], "X"))
            }
        }

        val result = engine.learnTask(taskId)
        assertNotNull(result)
        assertTrue(result!!.description.contains("btn_a"))
        assertTrue(result.description.contains("الخطوات الاختيارية"))
        assertTrue(result.description.contains("click → btn_b"))
        assertTrue(result.description.contains("الخطوات الغامضة"))
    }

    @Test
    fun `fully ambiguous task yields no skill`() {
        val engine = LearningEngine(dbHelper)
        val taskId = dbHelper.insertTask("Ambiguous Task", null)
        val targets = listOf("btn_save", "btn_cancel", "btn_x", "btn_y", "btn_z")
        for (i in 1..5) {
            recordIteration(taskId, i) { listOf(click(it, 1, targets[i - 1], "T")) }
        }
        // No common step at the only position and confidence is below the review threshold.
        assertNull(engine.learnTask(taskId))
    }

    @Test
    fun `different step ordering produces position-aware analysis`() {
        val engine = LearningEngine(dbHelper)
        val taskId = dbHelper.insertTask("Order Task", null)
        // iteration 1: a, b, c ; iterations 2-5: a, c, b
        recordIteration(taskId, 1) {
            listOf(click(it, 1, "a", "A"), click(it, 2, "b", "B"), click(it, 3, "c", "C"))
        }
        for (i in 2..5) {
            recordIteration(taskId, i) {
                listOf(click(it, 1, "a", "A"), click(it, 2, "c", "C"), click(it, 3, "b", "B"))
            }
        }

        val result = engine.learnTask(taskId)
        assertNotNull(result)
        // dominant order wins at each position
        val d = result!!.description
        assertTrue(d.contains("1. click → a"))
        assertTrue(d.contains("2. click → c"))
        assertTrue(d.contains("3. click → b"))
    }

    @Test
    fun `learned skill is stored with the task`() {
        val engine = LearningEngine(dbHelper)
        val taskId = dbHelper.insertTask("Stored Task", null)
        for (i in 1..5) recordIteration(taskId, i) { listOf(click(it, 1, "ok_btn", "OK")) }

        val result = engine.learnTask(taskId)
        assertNotNull(result)
        val stored = dbHelper.getSkillsForTask(taskId)
        assertEquals(1, stored.size)
        assertEquals(result!!.id, stored[0].id)
    }
}
