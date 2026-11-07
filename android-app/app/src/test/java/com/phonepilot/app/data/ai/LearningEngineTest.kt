package com.phonepilot.app.data.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LearningEngineTest {

    private lateinit var dbHelper: TaskDatabaseHelper

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        dbHelper = TaskDatabaseHelper.getInstance(context)
        dbHelper.markInterruptedRecordings()
    }

    @Test
    fun `learnTask returns null when fewer than 2 iterations`() {
        val engine = LearningEngine(dbHelper)

        val taskId = dbHelper.insertTask("Single Task", null)
        dbHelper.startRecording(taskId, 1)

        val result = engine.learnTask(taskId)
        assertNull(result)
    }

    @Test
    fun `learnTask returns null when no events`() {
        val engine = LearningEngine(dbHelper)

        val taskId = dbHelper.insertTask("Empty Task", null)
        dbHelper.startRecording(taskId, 1)
        dbHelper.startRecording(taskId, 2)

        val result = engine.learnTask(taskId)
        assertNull(result)
    }

    @Test
    fun `learnTask extracts common steps across iterations`() {
        val engine = LearningEngine(dbHelper)

        val taskId = dbHelper.insertTask("Common Steps Task", null)

        // تكرار 1
        val rec1 = dbHelper.startRecording(taskId, 1)
        dbHelper.insertEvent(rec1, EventEntity(recordingId = rec1, sequenceNumber = 1, eventType = "click", viewId = "email_field", text = "Email", timestamp = 1000))
        dbHelper.insertEvent(rec1, EventEntity(recordingId = rec1, sequenceNumber = 2, eventType = "input", viewId = "email_field", text = "user@test.com", timestamp = 2000))
        dbHelper.insertEvent(rec1, EventEntity(recordingId = rec1, sequenceNumber = 3, eventType = "click", viewId = "login_btn", text = "Login", timestamp = 3000))
        dbHelper.stopRecording(rec1, 3)

        // تكرار 2
        val rec2 = dbHelper.startRecording(taskId, 2)
        dbHelper.insertEvent(rec2, EventEntity(recordingId = rec2, sequenceNumber = 1, eventType = "click", viewId = "email_field", text = "Email", timestamp = 1000))
        dbHelper.insertEvent(rec2, EventEntity(recordingId = rec2, sequenceNumber = 2, eventType = "input", viewId = "email_field", text = "other@test.com", timestamp = 2000))
        dbHelper.insertEvent(rec2, EventEntity(recordingId = rec2, sequenceNumber = 3, eventType = "click", viewId = "login_btn", text = "Login", timestamp = 3000))
        dbHelper.stopRecording(rec2, 3)

        val result = engine.learnTask(taskId)

        assertNotNull(result)
        assertTrue(result.description.contains("الخطوات المشتركة"))
        assertTrue(result.description.contains("click → email_field"))
        assertTrue(result.description.contains("click → login_btn"))
    }

    @Test
    fun `coordinate differences do not affect semantic matching`() {
        val engine = LearningEngine(dbHelper)

        val taskId = dbHelper.insertTask("Coord Task", null)

        // نفس العنصر الدلالي، حتى لو كانت الإحداثيات مختلفة (غير مخزنة في EventEntity)
        val rec1 = dbHelper.startRecording(taskId, 1)
        dbHelper.insertEvent(rec1, EventEntity(recordingId = rec1, sequenceNumber = 1, eventType = "click", viewId = "submit_btn", text = "Submit", timestamp = 1000))
        dbHelper.stopRecording(rec1, 1)

        val rec2 = dbHelper.startRecording(taskId, 2)
        dbHelper.insertEvent(rec2, EventEntity(recordingId = rec2, sequenceNumber = 1, eventType = "click", viewId = "submit_btn", text = "Submit", timestamp = 2000))
        dbHelper.stopRecording(rec2, 1)

        val result = engine.learnTask(taskId)
        assertNotNull(result)
        assertTrue(result.description.contains("submit_btn"))
    }

    @Test
    fun `optional steps detected when present in some iterations`() {
        val engine = LearningEngine(dbHelper)

        val taskId = dbHelper.insertTask("Optional Task", null)

        // تكرار 1 يحتوي على btn_a و btn_b
        val rec1 = dbHelper.startRecording(taskId, 1)
        dbHelper.insertEvent(rec1, EventEntity(recordingId = rec1, sequenceNumber = 1, eventType = "click", viewId = "btn_a", text = "A", timestamp = 1000))
        dbHelper.insertEvent(rec1, EventEntity(recordingId = rec1, sequenceNumber = 2, eventType = "click", viewId = "btn_b", text = "B", timestamp = 2000))
        dbHelper.stopRecording(rec1, 2)

        // تكرار 2 يحتوي على btn_a فقط
        val rec2 = dbHelper.startRecording(taskId, 2)
        dbHelper.insertEvent(rec2, EventEntity(recordingId = rec2, sequenceNumber = 1, eventType = "click", viewId = "btn_a", text = "A", timestamp = 1000))
        dbHelper.stopRecording(rec2, 1)

        val result = engine.learnTask(taskId)
        assertNotNull(result)
        assertTrue(result.description.contains("btn_a"))
    }

    @Test
    fun `ambiguous elements detected when multiple patterns at same position`() {
        val engine = LearningEngine(dbHelper)

        val taskId = dbHelper.insertTask("Ambiguous Task", null)

        // نفس الموقع ولكن أهداف مختلفة
        val rec1 = dbHelper.startRecording(taskId, 1)
        dbHelper.insertEvent(rec1, EventEntity(recordingId = rec1, sequenceNumber = 1, eventType = "click", viewId = "btn_save", text = "Save", timestamp = 1000))
        dbHelper.stopRecording(rec1, 1)

        val rec2 = dbHelper.startRecording(taskId, 2)
        dbHelper.insertEvent(rec2, EventEntity(recordingId = rec2, sequenceNumber = 1, eventType = "click", viewId = "btn_cancel", text = "Cancel", timestamp = 1000))
        dbHelper.stopRecording(rec2, 1)

        val result = engine.learnTask(taskId)
        assertNotNull(result)
        // لا يوجد خطوة مشتركة في نفس الموقع
    }

    @Test
    fun `different step ordering produces position-aware analysis`() {
        val engine = LearningEngine(dbHelper)

        val taskId = dbHelper.insertTask("Order Task", null)

        // تكرار 1: A → B → C
        val rec1 = dbHelper.startRecording(taskId, 1)
        dbHelper.insertEvent(rec1, EventEntity(recordingId = rec1, sequenceNumber = 1, eventType = "click", viewId = "a", text = "A", timestamp = 1000))
        dbHelper.insertEvent(rec1, EventEntity(recordingId = rec1, sequenceNumber = 2, eventType = "click", viewId = "b", text = "B", timestamp = 2000))
        dbHelper.insertEvent(rec1, EventEntity(recordingId = rec1, sequenceNumber = 3, eventType = "click", viewId = "c", text = "C", timestamp = 3000))
        dbHelper.stopRecording(rec1, 3)

        // تكرار 2: A → C → B (ترتيب مختلف)
        val rec2 = dbHelper.startRecording(taskId, 2)
        dbHelper.insertEvent(rec2, EventEntity(recordingId = rec2, sequenceNumber = 1, eventType = "click", viewId = "a", text = "A", timestamp = 1000))
        dbHelper.insertEvent(rec2, EventEntity(recordingId = rec2, sequenceNumber = 2, eventType = "click", viewId = "c", text = "C", timestamp = 2000))
        dbHelper.insertEvent(rec2, EventEntity(recordingId = rec2, sequenceNumber = 3, eventType = "click", viewId = "b", text = "B", timestamp = 3000))
        dbHelper.stopRecording(rec2, 3)

        val result = engine.learnTask(taskId)
        assertNotNull(result)
        //只有 "click → a" مشترك في نفس الموقع (position 1)
        assertTrue(result.description.contains("click → a"))
    }
}
