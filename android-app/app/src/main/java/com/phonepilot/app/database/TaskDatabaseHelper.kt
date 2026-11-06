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
        private const val DATABASE_VERSION = 4

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

        // Migration columns added in v4
        private const val ALTER_RECORDINGS_ADD_ACCEPTED_COUNT =
            "ALTER TABLE task_recordings ADD COLUMN accepted_count INTEGER NOT NULL DEFAULT 0"
        private const val ALTER_RECORDINGS_ADD_FAILURE_REASON =
            "ALTER TABLE task_recordings ADD COLUMN failure_reason TEXT"

        private const val CREATE_INDEX_EVENTS =
            "CREATE UNIQUE INDEX idx_events_recording_seq ON events(recording_id, sequence_number)"

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
        if (oldVersion < 3) {
            // Migration v2 → v3: deduplicate events then create UNIQUE index
            // Remove duplicate (recording_id, sequence_number) rows, keeping the lowest id
            db.execSQL(
                "DELETE FROM events WHERE id NOT IN (" +
                "SELECT MIN(id) FROM events GROUP BY recording_id, sequence_number" +
                ")"
            )
            // Drop old non-unique index if exists
            db.execSQL("DROP INDEX IF EXISTS idx_events_recording_seq")
            db.execSQL(CREATE_INDEX_EVENTS)
        }
        if (oldVersion < 4) {
            // Migration v3 → v4: add accepted_count and failure_reason columns
            db.execSQL(ALTER_RECORDINGS_ADD_ACCEPTED_COUNT)
            db.execSQL(ALTER_RECORDINGS_ADD_FAILURE_REASON)
        }
        db.enableWriteAheadLogging()
    }

    /** تحديث حالة التسجيلات العالقة إلى INTERRUPTED_FAILED عند بدء التطبيق.
     * لا يُفترض نجاح أي جلسة عالقة — العدادات غير متاحة عند إعادة التشغيل.
     */
    fun markInterruptedRecordings() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.update("task_recordings", ContentValues().apply {
                put("state", "INTERRUPTED_FAILED")
                put("failure_reason", "process_terminated_before_completion")
            }, "state = ?", arrayOf("RECORDING"))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Mark a specific recording as interrupted with persistence metadata.
     * Used when recovering from a failed stop/interrupt after process restart.
     */
    fun markRecordingInterruptedWithMeta(recordingId: Long, acceptedCount: Int, savedCount: Int) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val state = if (savedCount >= acceptedCount) "INTERRUPTED" else "INTERRUPTED_PARTIAL"
            val values = ContentValues().apply {
                put("state", state)
                put("accepted_count", acceptedCount)
            }
            db.update("task_recordings", values, "id = ?", arrayOf(recordingId.toString()))
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

    fun interruptRecording(recordingId: Long, acceptedCount: Int = 0, savedCount: Int = 0, failureReason: String? = null) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val state = if (failureReason == null && savedCount >= acceptedCount) "INTERRUPTED" else "INTERRUPTED_PARTIAL"
            val values = ContentValues().apply {
                put("state", state)
                put("accepted_count", acceptedCount)
                if (failureReason != null) {
                    put("failure_reason", failureReason)
                }
            }
            db.update("task_recordings", values, "id = ?", arrayOf(recordingId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun markRecordingFailed(recordingId: Long, acceptedCount: Int, savedCount: Int, failureReason: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("state", "INTERRUPTED_FAILED")
                put("accepted_count", acceptedCount)
                put("failure_reason", failureReason)
            }
            db.update("task_recordings", values, "id = ?", arrayOf(recordingId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun abandonRecording(recordingId: Long, acceptedCount: Int = 0, savedCount: Int = 0) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("state", "ABANDONED")
                put("accepted_count", acceptedCount)
            }
            db.update("task_recordings", values, "id = ?", arrayOf(recordingId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Checks whether an event with the given recording_id and sequence_number already exists
     * AND has matching content fields. Returns true only if content matches exactly.
     * If the row exists but content differs → conflict (not a verified duplicate).
     */
    fun eventContentMatches(
        recordingId: Long,
        sequenceNumber: Int,
        eventType: String,
        packageName: String?,
        className: String?,
        viewId: String?,
        text: String?,
        contentDescription: String?,
        bounds: String?,
        inputText: String?,
        timestamp: Long
    ): Boolean {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT event_type, package_name, class_name, view_id, text, content_description, bounds, input_text, timestamp " +
            "FROM events WHERE recording_id = ? AND sequence_number = ?",
            arrayOf(recordingId.toString(), sequenceNumber.toString())
        )
        try {
            if (!cursor.moveToFirst()) return false
            val existingEventType = cursor.getString(cursor.getColumnIndexOrThrow("event_type"))
            val existingPackageName = cursor.getString(cursor.getColumnIndexOrThrow("package_name"))
            val existingClassName = cursor.getString(cursor.getColumnIndexOrThrow("class_name"))
            val existingViewId = cursor.getString(cursor.getColumnIndexOrThrow("view_id"))
            val existingText = cursor.getString(cursor.getColumnIndexOrThrow("text"))
            val existingContentDescription = cursor.getString(cursor.getColumnIndexOrThrow("content_description"))
            val existingBounds = cursor.getString(cursor.getColumnIndexOrThrow("bounds"))
            val existingInputText = cursor.getString(cursor.getColumnIndexOrThrow("input_text"))
            val existingTimestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"))

            return existingEventType == eventType &&
                existingPackageName == packageName &&
                existingClassName == className &&
                existingViewId == viewId &&
                existingText == text &&
                existingContentDescription == contentDescription &&
                existingBounds == bounds &&
                existingInputText == inputText &&
                existingTimestamp == timestamp
        } finally {
            cursor.close()
        }
    }

    /**
     * Checks whether a row with the given recording_id and sequence_number exists
     * (regardless of content). Used for conflict detection.
     */
    fun eventRowExists(recordingId: Long, sequenceNumber: Int): Boolean {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM events WHERE recording_id = ? AND sequence_number = ?",
            arrayOf(recordingId.toString(), sequenceNumber.toString())
        )
        try {
            return cursor.moveToFirst() && cursor.getInt(0) > 0
        } finally {
            cursor.close()
        }
    }

    /**
     * Insert an event. Uses INSERT OR IGNORE to prevent duplicates on
     * (recording_id, sequence_number). Returns the row id, or -1 if ignored.
     */
    fun insertEvent(recordingId: Long, event: EventEntity): Long {
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
            val rowId = db.insertWithOnConflict("events", null, values, SQLiteDatabase.CONFLICT_IGNORE)
            db.setTransactionSuccessful()
            return rowId
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
