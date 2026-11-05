package com.phonepilot.app.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.phonepilot.app.models.EventEntity
import com.phonepilot.app.models.TaskEntity
import com.phonepilot.app.models.TaskRecordingEntity

class TaskDatabaseHelper private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "phonepilot.db"
        private const val DATABASE_VERSION = 2

        private const val CREATE_TASKS =
            "CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, description TEXT, created_at INTEGER NOT NULL, status TEXT NOT NULL)"

        private const val CREATE_RECORDINGS_V1 =
            "CREATE TABLE task_recordings (id INTEGER PRIMARY KEY AUTOINCREMENT, task_id INTEGER NOT NULL, iteration_number INT NOT NULL, recorded_at INTEGER NOT NULL, raw_events TEXT NOT NULL)"

        private const val CREATE_EVENTS =
            "CREATE TABLE events (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "recording_id INTEGER NOT NULL, " +
            "sequence_number INTEGER NOT NULL, " +
            "event_type TEXT NOT NULL, " +
            "package_name TEXT, " +
            "class_name TEXT, " +
            "view_id TEXT, " +
            "text TEXT, " +
            "content_description TEXT, " +
            "bounds TEXT, " +
            "input_text TEXT, " +
            "timestamp INTEGER NOT NULL, " +
            "before_snapshot TEXT, " +
            "after_snapshot TEXT, " +
            "is_privacy_masked INTEGER NOT NULL DEFAULT 0, " +
            "FOREIGN KEY(recording_id) REFERENCES task_recordings(id))"

        private const val CREATE_INDEX_EVENTS =
            "CREATE INDEX idx_events_recording_seq ON events(recording_id, sequence_number)"

        private const val ALTER_RECORDINGS_ADD_STATE =
            "ALTER TABLE task_recordings ADD COLUMN state TEXT NOT NULL DEFAULT 'COMPLETED'"

        @Volatile
        private var INSTANCE: TaskDatabaseHelper? = null

        fun getInstance(context: Context): TaskDatabaseHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TaskDatabaseHelper(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TASKS)
        db.execSQL(CREATE_RECORDINGS_V1)
        db.execSQL(ALTER_RECORDINGS_ADD_STATE)
        db.execSQL(CREATE_EVENTS)
        db.execSQL(CREATE_INDEX_EVENTS)
        db.enableWriteAheadLogging()
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Migration v1 → v2: add state column and events table (preserve existing data)
            db.execSQL(ALTER_RECORDINGS_ADD_STATE)
            db.execSQL(CREATE_EVENTS)
            db.execSQL(CREATE_INDEX_EVENTS)
        }
        db.enableWriteAheadLogging()
    }

    /** تحديث حالة التسجيلات العالقة إلى INTERRUPTED عند بدء التطبيق */
    fun markInterruptedRecordings() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.update("task_recordings", ContentValues().apply {
                put("state", "INTERRUPTED")
            }, "state = ?", arrayOf("RECORDING"))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun startRecording(taskId: Long, iterationNumber: Int): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("task_id", taskId)
                put("iteration_number", iterationNumber)
                put("recorded_at", System.currentTimeMillis())
                put("raw_events", "")
                put("state", "RECORDING")
            }
            val recordingId = db.insert("task_recordings", null, values)
            db.setTransactionSuccessful()
            return recordingId
        } finally {
            db.endTransaction()
        }
    }

    fun stopRecording(recordingId: Long, eventCount: Int) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("state", "COMPLETED")
            }
            db.update("task_recordings", values, "id = ?", arrayOf(recordingId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun interruptRecording(recordingId: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("state", "INTERRUPTED")
            }
            db.update("task_recordings", values, "id = ?", arrayOf(recordingId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun insertEvent(recordingId: Long, event: EventEntity) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("recording_id", recordingId)
                put("sequence_number", event.sequenceNumber)
                put("event_type", event.eventType)
                put("package_name", event.packageName)
                put("class_name", event.className)
                put("view_id", event.viewId)
                put("text", event.text)
                put("content_description", event.contentDescription)
                put("bounds", event.bounds)
                put("input_text", event.inputText)
                put("timestamp", event.timestamp)
                put("before_snapshot", event.beforeSnapshot)
                put("after_snapshot", event.afterSnapshot)
                put("is_privacy_masked", if (event.isPrivacyMasked) 1 else 0)
            }
            db.insert("events", null, values)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getAllTasks(): List<TaskEntity> {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM tasks ORDER BY created_at DESC", null)
        val tasks = mutableListOf<TaskEntity>()
        try {
            while (cursor.moveToNext()) {
                tasks.add(TaskEntity(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    description = cursor.getString(cursor.getColumnIndexOrThrow("description")),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                    status = cursor.getString(cursor.getColumnIndexOrThrow("status"))
                ))
            }
        } finally {
            cursor.close()
        }
        return tasks
    }

    fun getAllRecordings(): List<TaskRecordingEntity> {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM task_recordings ORDER BY recorded_at DESC", null)
        val recordings = mutableListOf<TaskRecordingEntity>()
        try {
            while (cursor.moveToNext()) {
                recordings.add(TaskRecordingEntity(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    taskId = cursor.getLong(cursor.getColumnIndexOrThrow("task_id")),
                    iterationNumber = cursor.getInt(cursor.getColumnIndexOrThrow("iteration_number")),
                    recordedAt = cursor.getLong(cursor.getColumnIndexOrThrow("recorded_at")),
                    rawEvents = cursor.getString(cursor.getColumnIndexOrThrow("raw_events"))
                ))
            }
        } finally {
            cursor.close()
        }
        return recordings
    }

    fun getEventsForRecording(recordingId: Long): List<EventEntity> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM events WHERE recording_id = ? ORDER BY sequence_number ASC",
            arrayOf(recordingId.toString())
        )
        val events = mutableListOf<EventEntity>()
        try {
            while (cursor.moveToNext()) {
                events.add(EventEntity(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    recordingId = cursor.getLong(cursor.getColumnIndexOrThrow("recording_id")),
                    sequenceNumber = cursor.getInt(cursor.getColumnIndexOrThrow("sequence_number")),
                    eventType = cursor.getString(cursor.getColumnIndexOrThrow("event_type")),
                    packageName = cursor.getString(cursor.getColumnIndexOrThrow("package_name")),
                    className = cursor.getString(cursor.getColumnIndexOrThrow("class_name")),
                    viewId = cursor.getString(cursor.getColumnIndexOrThrow("view_id")),
                    text = cursor.getString(cursor.getColumnIndexOrThrow("text")),
                    contentDescription = cursor.getString(cursor.getColumnIndexOrThrow("content_description")),
                    bounds = cursor.getString(cursor.getColumnIndexOrThrow("bounds")),
                    inputText = cursor.getString(cursor.getColumnIndexOrThrow("input_text")),
                    timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                    beforeSnapshot = cursor.getString(cursor.getColumnIndexOrThrow("before_snapshot")),
                    afterSnapshot = cursor.getString(cursor.getColumnIndexOrThrow("after_snapshot")),
                    isPrivacyMasked = cursor.getInt(cursor.getColumnIndexOrThrow("is_privacy_masked")) == 1
                ))
            }
        } finally {
            cursor.close()
        }
        return events
    }

    val db: SQLiteDatabase
        get() = writableDatabase
    val values: ContentValues
        get() = ContentValues()
}
