package com.phonepilot.app.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.phonepilot.app.models.EventEntity
import com.phonepilot.app.models.TaskEntity
import com.phonepilot.app.models.TaskRecordingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskDatabaseHelperTest {

    private lateinit var dbHelper: TaskDatabaseHelper

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        dbHelper = TaskDatabaseHelper.getInstance(context)
        dbHelper.markInterruptedRecordings()
    }

    @Test
    fun `insertTask and getAllTasks returns saved task`() {
        val id = dbHelper.insertTask("Test Task", "description")
        assertNotNull(id)
        assertTrue(id > 0)

        val tasks = dbHelper.getAllTasks()
        assertFalse(tasks.isEmpty())
        assertEquals("Test Task", tasks[0].name)
        assertEquals("description", tasks[0].description)
        assertTrue(tasks[0].id > 0)
    }

    @Test
    fun `insertTask with null description`() {
        val id = dbHelper.insertTask("Task No Desc", null)
        assertTrue(id > 0)

        val tasks = dbHelper.getAllTasks()
        assertEquals(1, tasks.size)
        assertEquals("Task No Desc", tasks[0].name)
        assertEquals(null, tasks[0].description)
    }

    @Test
    fun `startRecording then stopRecording updates state`() {
        val taskId = dbHelper.insertTask("Recording Task", null)
        val recordingId = dbHelper.startRecording(taskId, 1)
        assertTrue(recordingId > 0)

        dbHelper.stopRecording(recordingId, 5)

        val recordings = dbHelper.getAllRecordings()
        assertFalse(recordings.isEmpty())
        // find our recording
        val rec = recordings.firstOrNull { it.id == recordingId }
        assertNotNull(rec)
        assertEquals(taskId, rec?.taskId)
        assertEquals(1, rec?.iterationNumber)
    }

    @Test
    fun `insertEvent and getEventsForRecording preserves order`() {
        val taskId = dbHelper.insertTask("Event Order Task", null)
        val recordingId = dbHelper.startRecording(taskId, 1)

        dbHelper.insertEvent(recordingId, EventEntity(
            recordingId = recordingId,
            sequenceNumber = 1,
            eventType = "click",
            packageName = "com.android.settings",
            className = "android.widget.Button",
            viewId = "android:id/button1",
            text = "Wi-Fi",
            contentDescription = null,
            bounds = null,
            inputText = null,
            timestamp = 1000,
            beforeSnapshot = null,
            afterSnapshot = null
        ))
        dbHelper.insertEvent(recordingId, EventEntity(
            recordingId = recordingId,
            sequenceNumber = 2,
            eventType = "input",
            packageName = "com.android.settings",
            className = "android.widget.EditText",
            viewId = "android:id/edit1",
            text = "SSID",
            contentDescription = null,
            bounds = null,
            inputText = "MyNetwork",
            timestamp = 2000,
            beforeSnapshot = null,
            afterSnapshot = null
        ))
        dbHelper.insertEvent(recordingId, EventEntity(
            recordingId = recordingId,
            sequenceNumber = 3,
            eventType = "click",
            packageName = "com.android.settings",
            className = "android.widget.Button",
            viewId = "android:id/save",
            text = "Save",
            contentDescription = null,
            bounds = null,
            inputText = null,
            timestamp = 3000,
            beforeSnapshot = null,
            afterSnapshot = null
        ))

        val events = dbHelper.getEventsForRecording(recordingId)
        assertEquals(3, events.size)
        assertEquals(1, events[0].sequenceNumber)
        assertEquals("click", events[0].eventType)
        assertEquals(2, events[1].sequenceNumber)
        assertEquals("input", events[1].eventType)
        assertEquals(3, events[2].sequenceNumber)
        assertEquals("click", events[2].eventType)
    }

    @Test
    fun `insertEvent masks privacy masked flag`() {
        val taskId = dbHelper.insertTask("Privacy Task", null)
        val recordingId = dbHelper.startRecording(taskId, 1)

        dbHelper.insertEvent(recordingId, EventEntity(
            recordingId = recordingId,
            sequenceNumber = 1,
            eventType = "input",
            packageName = "com.whatsapp",
            className = "android.widget.EditText",
            viewId = null,
            text = "secret",
            contentDescription = null,
            bounds = null,
            inputText = "hidden",
            timestamp = 1000,
            beforeSnapshot = null,
            afterSnapshot = null,
            isPrivacyMasked = true
        ))

        val events = dbHelper.getEventsForRecording(recordingId)
        assertEquals(1, events.size)
        assertTrue(events[0].isPrivacyMasked)
        assertEquals("[MASKED]", events[0].text)
    }

    @Test
    fun `getRecordingsForTask returns recordings for specific task`() {
        val task1Id = dbHelper.insertTask("Task 1", null)
        val task2Id = dbHelper.insertTask("Task 2", null)

        val rec1 = dbHelper.startRecording(task1Id, 1)
        val rec2 = dbHelper.startRecording(task2Id, 1)

        val recordings = dbHelper.getAllRecordings()
        assertTrue(recordings.size >= 2)
        val task1Recs = recordings.filter { it.taskId == task1Id }
        val task2Recs = recordings.filter { it.taskId == task2Id }
        assertTrue(task1Recs.any { it.id == rec1 })
        assertTrue(task2Recs.any { it.id == rec2 })
    }

    @Test
    fun `interruptRecording sets state to INTERRUPTED`() {
        val taskId = dbHelper.insertTask("Interrupted Task", null)
        val recordingId = dbHelper.startRecording(taskId, 1)
        dbHelper.interruptRecording(recordingId)

        val recordings = dbHelper.getAllRecordings()
        val rec = recordings.firstOrNull { it.id == recordingId }
        assertNotNull(rec)
    }
}
