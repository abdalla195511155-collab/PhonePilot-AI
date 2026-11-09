package com.phonepilot.app.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.phonepilot.app.models.EventEntity
import com.phonepilot.app.models.SkillEntity
import com.phonepilot.app.models.SkillLifecycleState
import com.phonepilot.app.models.SkillVersionEntity
import com.phonepilot.app.models.TaskEntity
import com.phonepilot.app.models.TaskRecordingEntity

class TaskDatabaseHelper private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "phonepilot.db"
        private const val DATABASE_VERSION = 9

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

        private const val CREATE_SKILLS =
            "CREATE TABLE skills (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "task_id INTEGER NOT NULL, " +
            "title TEXT NOT NULL, " +
            "description TEXT, " +
            "level TEXT NOT NULL, " +
            "source_recording_id INTEGER, " +
            "created_at INTEGER NOT NULL, " +
            "lifecycle_state TEXT NOT NULL DEFAULT 'DRAFT', " +
            "confidence_score REAL DEFAULT 0.0, " +
            "validation_reasons TEXT, " +
            "version INTEGER NOT NULL DEFAULT 1, " +
            "approval_state TEXT NOT NULL DEFAULT 'PENDING', " +
            "approved_at INTEGER, " +
            "approved_by_user INTEGER NOT NULL DEFAULT 0, " +
            "FOREIGN KEY(task_id) REFERENCES tasks(id), " +
            "FOREIGN KEY(source_recording_id) REFERENCES task_recordings(id))"

        /** إنشاء جدول المهارات بالحد الأدنى من الأعمدة للترحيل v4 → v5.
         * الأعمدة المتبقية (الحالة، الثقة، الإصدار، الموافقة) تُضاف في ترحيلات v5→v6 و v6→v7
         * لتجنب خطأ "عمود مكرر" عند الترقية المباشرة من إصدارات قديمة. */
        private const val CREATE_SKILLS_V4_TO_V5 =
            "CREATE TABLE skills (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "task_id INTEGER NOT NULL, " +
            "title TEXT NOT NULL, " +
            "description TEXT, " +
            "level TEXT NOT NULL, " +
            "source_recording_id INTEGER, " +
            "created_at INTEGER NOT NULL, " +
            "FOREIGN KEY(task_id) REFERENCES tasks(id), " +
            "FOREIGN KEY(source_recording_id) REFERENCES task_recordings(id))"

        private const val CREATE_SKILL_VERSIONS =
            "CREATE TABLE skill_versions (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "skill_id TEXT NOT NULL, " +
            "version_number INTEGER NOT NULL, " +
            "title TEXT NOT NULL, " +
            "description TEXT, " +
            "level TEXT, " +
            "lifecycle_state TEXT NOT NULL, " +
            "source_recording_id INTEGER, " +
            "created_at INTEGER NOT NULL, " +
            "FOREIGN KEY(skill_id) REFERENCES skills(id))"

        private const val CREATE_EXECUTIONS =
            "CREATE TABLE executions (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "skill_id TEXT NOT NULL, " +
            "skill_version INTEGER NOT NULL, " +
            "task_id INTEGER, " +
            "state TEXT NOT NULL, " +
            "started_at INTEGER NOT NULL, " +
            "completed_at INTEGER, " +
            "total_steps INTEGER NOT NULL DEFAULT 0, " +
            "completed_steps INTEGER NOT NULL DEFAULT 0, " +
            "failed_steps INTEGER NOT NULL DEFAULT 0, " +
            "skipped_steps INTEGER NOT NULL DEFAULT 0, " +
            "failure_reason TEXT, " +
            "recovery_attempts INTEGER NOT NULL DEFAULT 0, " +
            "FOREIGN KEY(skill_id) REFERENCES skills(id))"

        private const val CREATE_STEP_RESULTS =
            "CREATE TABLE step_results (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "execution_id INTEGER NOT NULL, " +
            "step_number INTEGER NOT NULL, " +
            "step_description TEXT NOT NULL, " +
            "success INTEGER NOT NULL DEFAULT 0, " +
            "verified INTEGER NOT NULL DEFAULT 0, " +
            "element_resolved INTEGER NOT NULL DEFAULT 0, " +
            "element_id TEXT, " +
            "action_taken TEXT, " +
            "failure_reason TEXT, " +
            "recovery_attempted INTEGER NOT NULL DEFAULT 0, " +
            "recovery_succeeded INTEGER NOT NULL DEFAULT 0, " +
            "timestamp INTEGER NOT NULL, " +
            "screen_before TEXT, " +
            "screen_after TEXT, " +
            "FOREIGN KEY(execution_id) REFERENCES executions(id))"

        private const val CREATE_AGENT_MEMORY =
            "CREATE TABLE agent_memory (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "task_id TEXT NOT NULL, " +
            "intent TEXT NOT NULL, " +
            "skill_id TEXT, " +
            "execution_state TEXT NOT NULL, " +
            "completed_at INTEGER, " +
            "success INTEGER NOT NULL DEFAULT 0, " +
            "notes TEXT, " +
            "created_at INTEGER NOT NULL)"

        private const val ALTER_SKILLS_ADD_LIFECYCLE =
            "ALTER TABLE skills ADD COLUMN lifecycle_state TEXT NOT NULL DEFAULT 'DRAFT'"

        private const val ALTER_SKILLS_ADD_CONFIDENCE =
            "ALTER TABLE skills ADD COLUMN confidence_score REAL DEFAULT 0.0"

        private const val ALTER_SKILLS_ADD_VALIDATION_REASONS =
            "ALTER TABLE skills ADD COLUMN validation_reasons TEXT"

        private const val ALTER_SKILLS_ADD_VERSION =
            "ALTER TABLE skills ADD COLUMN version INTEGER NOT NULL DEFAULT 1"

        private const val ALTER_SKILLS_ADD_APPROVAL_STATE =
            "ALTER TABLE skills ADD COLUMN approval_state TEXT NOT NULL DEFAULT 'PENDING'"

        private const val ALTER_SKILLS_ADD_APPROVED_AT =
            "ALTER TABLE skills ADD COLUMN approved_at INTEGER"

        private const val ALTER_SKILLS_ADD_APPROVED_BY_USER =
            "ALTER TABLE skills ADD COLUMN approved_by_user INTEGER NOT NULL DEFAULT 0"

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
        // جداول المراحل 5 و 6 و 7 تُنشَز بالكامل عند التثبيت الجديد (الإصدار 9)
        db.execSQL(CREATE_SKILLS)
        db.execSQL(CREATE_SKILL_VERSIONS)
        db.execSQL(CREATE_EXECUTIONS)
        db.execSQL(CREATE_STEP_RESULTS)
        db.execSQL(CREATE_AGENT_MEMORY)
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
        if (oldVersion < 5) {
            // Migration v4 → v5: add skills table with base columns only (Phase 5).
            // الأعمدة الإضافية تُضاف في v5→v6 و v6→v7 لتجنب التكرار.
            db.execSQL(CREATE_SKILLS_V4_TO_V5)
        }
        if (oldVersion < 6) {
            // Migration v5 → v6: add lifecycle state, confidence, validation reasons, version columns
            // and skill_versions table for skill versioning (Phase 5 completion)
            db.execSQL(ALTER_SKILLS_ADD_LIFECYCLE)
            db.execSQL(ALTER_SKILLS_ADD_CONFIDENCE)
            db.execSQL(ALTER_SKILLS_ADD_VALIDATION_REASONS)
            db.execSQL(ALTER_SKILLS_ADD_VERSION)
            db.execSQL(CREATE_SKILL_VERSIONS)
        }
        if (oldVersion < 7) {
            // Migration v6 → v7: add user approval fields to skills table (Phase 5 approval gate)
            db.execSQL(ALTER_SKILLS_ADD_APPROVAL_STATE)
            db.execSQL(ALTER_SKILLS_ADD_APPROVED_AT)
            db.execSQL(ALTER_SKILLS_ADD_APPROVED_BY_USER)
        }
        if (oldVersion < 8) {
            // Migration v7 → v8: add executions and step_results tables (Phase 6 Intelligent Execution)
            db.execSQL(CREATE_EXECUTIONS)
            db.execSQL(CREATE_STEP_RESULTS)
        }
        if (oldVersion < 9) {
            // Migration v8 → v9: add agent_memory table (Phase 7 Agent Orchestration)
            db.execSQL(CREATE_AGENT_MEMORY)
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
                put("state", "INTERRUPTED")
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
                put("state", "INTERRUPTED")
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

    fun insertTask(name: String, description: String? = null): Long {
        val values = ContentValues().apply {
            put("name", name)
            put("description", description)
            put("created_at", System.currentTimeMillis())
            put("status", "ACTIVE")
        }
        return writableDatabase.insert("tasks", null, values)
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

    /**
     * يعيد التسجيلات المكتملة لمهمة معينة مرتبة حسب رقم التكرار، قابلة للمقارنة في محرك التعلم.
     * Phase 5: التعلم من تكرارات متعددة للمهمة نفسها.
     */
    fun getRecordingsForTask(taskId: Long): List<TaskRecordingEntity> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM task_recordings WHERE task_id = ? AND state = 'COMPLETED' ORDER BY iteration_number ASC, recorded_at ASC",
            arrayOf(taskId.toString())
        )
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

    /** إدراج مهارة متعلّمة جديدة (Phase 5) مع حالة دورة الحياة الافتراضية DRAFT. يعيد معرف الصف أو -1 عند الفشل. */
    fun insertSkill(taskId: Long, title: String, description: String?, level: String, sourceRecordingId: Long?): Long {
        return insertSkillWithState(taskId, title, description, level, sourceRecordingId, SkillLifecycleState.DRAFT)
    }

    /** إدراج مهارة بحالة دورة حياة محددة (Phase 5). */
    fun insertSkillWithState(taskId: Long, title: String, description: String?, level: String, sourceRecordingId: Long?, state: SkillLifecycleState): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("task_id", taskId)
                put("title", title)
                put("description", description)
                put("level", level)
                put("source_recording_id", sourceRecordingId)
                put("lifecycle_state", state.name)
                put("confidence_score", 0.0)
                put("version", 1)
                put("approval_state", "PENDING")
                put("approved_by_user", 0)
                put("created_at", System.currentTimeMillis())
            }
            val id = db.insert("skills", null, values)
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }

    /** تسجيل قرار موافقة المستخدم على مهارة (Phase 5).
     * يحفظ حالة الموافقة (APPROVED/REJECTED)، توقيت القرار، وهل القرار من المستخدم أم لا.
     * يعيد عدد الصفوف المحدثة. */
    fun recordSkillApproval(
        id: Long,
        approvalState: String,
        approvedAt: Long?,
        approvedByUser: Boolean,
    ): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("approval_state", approvalState)
            approvedAt?.let { values.put("approved_at", it) }
            values.put("approved_by_user", if (approvedByUser) 1 else 0)
        }
        return db.update("skills", values, "id = ?", arrayOf(id.toString()))
    }

    /** إرجاع حالة الموافقة لمهارة معينة (Phase 5). */
    fun getSkillApprovalState(id: Long): String? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT approval_state FROM skills WHERE id = ?",
            arrayOf(id.toString())
        )
        try {
            if (!cursor.moveToFirst()) return null
            return cursor.getString(cursor.getColumnIndexOrThrow("approval_state"))
        } finally {
            cursor.close()
        }
    }

    /** التحقق من أن مهارة ما موافقة من المستخدم وجاهزة للاعتماد (Phase 5). */
    fun isSkillApprovedByUser(id: Long): Boolean {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT approval_state, approved_by_user FROM skills WHERE id = ?",
            arrayOf(id.toString())
        )
        try {
            if (!cursor.moveToFirst()) return false
            val state = cursor.getString(cursor.getColumnIndexOrThrow("approval_state"))
            val byUser = cursor.getInt(cursor.getColumnIndexOrThrow("approved_by_user")) == 1
            return state == "APPROVED" && byUser
        } finally {
            cursor.close()
        }
    }

    /** تحديث حالة دورة الحياة والثقة وأسباب التحقق لمهارة (Phase 5). يعيد عدد الصفوف المحدثة. */
    fun updateSkillState(
        id: Long,
        lifecycleState: SkillLifecycleState? = null,
        confidenceScore: Double? = null,
        validationReasons: List<String>? = null,
    ): Int {
        val db = writableDatabase
        val values = ContentValues()
        lifecycleState?.let { values.put("lifecycle_state", it.name) }
        confidenceScore?.let { values.put("confidence_score", it) }
        validationReasons?.let { values.put("validation_reasons", validationReasons.joinToString(";")) }
        if (values.isEmpty()) return 0
        return db.update("skills", values, "id = ?", arrayOf(id.toString()))
    }

    /** إدراج إصدار جديد لمهارة (Phase 5). يعيد معرف الصف أو -1 عند الفشل. */
    fun insertSkillVersion(
        skillId: String,
        versionNumber: Int,
        title: String,
        description: String?,
        level: String?,
        lifecycleState: SkillLifecycleState,
        sourceRecordingId: Long?,
    ): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("skill_id", skillId)
                put("version_number", versionNumber)
                put("title", title)
                put("description", description)
                put("level", level)
                put("lifecycle_state", lifecycleState.name)
                put("source_recording_id", sourceRecordingId)
                put("created_at", System.currentTimeMillis())
            }
            val id = db.insert("skill_versions", null, values)
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }

    /** جميع إصدارات مهارة معينة مرتبة حسب الإصدار تنازلياً (Phase 5). */
    fun getSkillVersions(skillId: String): List<SkillVersionEntity> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM skill_versions WHERE skill_id = ? ORDER BY version_number DESC",
            arrayOf(skillId)
        )
        val versions = mutableListOf<SkillVersionEntity>()
        try {
            while (cursor.moveToNext()) {
                versions.add(readSkillVersion(cursor))
            }
        } finally {
            cursor.close()
        }
        return versions
    }

    /** إرجاع إصدار مهارة برقم محدد (Phase 5). */
    fun getSkillVersionByNumber(skillId: String, versionNumber: Int): SkillVersionEntity? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM skill_versions WHERE skill_id = ? AND version_number = ?",
            arrayOf(skillId, versionNumber.toString())
        )
        try {
            if (!cursor.moveToFirst()) return null
            return readSkillVersion(cursor)
        } finally {
            cursor.close()
        }
    }

    /** تحديث رقم الإصدار في جدول المهارة principale (Phase 5). */
    fun bumpSkillVersion(id: Long, newVersion: Int): Int {
        return writableDatabase.update("skills", ContentValues().apply { put("version", newVersion) }, "id = ?", arrayOf(id.toString()))
    }

    /** كل المهارات المتعلّمة مرتبة حسب وقت الإنشاء تنازلياً (Phase 5). */
    fun getAllSkills(): List<SkillEntity> {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM skills ORDER BY created_at DESC", null)
        val skills = mutableListOf<SkillEntity>()
        try {
            while (cursor.moveToNext()) {
                skills.add(readSkill(cursor))
            }
        } finally {
            cursor.close()
        }
        return skills
    }

    /** مهارات مهمة معينة (Phase 5). */
    fun getSkillsForTask(taskId: Long): List<SkillEntity> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM skills WHERE task_id = ? ORDER BY created_at DESC",
            arrayOf(taskId.toString())
        )
        val skills = mutableListOf<SkillEntity>()
        try {
            while (cursor.moveToNext()) {
                skills.add(readSkill(cursor))
            }
        } finally {
            cursor.close()
        }
        return skills
    }

    /** حذف مهارة بمعرفها (Phase 5). يعيد عدد الصفوف المحذوفة. */
    fun deleteSkill(id: Long): Int {
        return writableDatabase.delete("skills", "id = ?", arrayOf(id.toString()))
    }

    private fun readSkill(cursor: android.database.Cursor): SkillEntity {
        return SkillEntity(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")).toString(),
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
            description = cursor.getString(cursor.getColumnIndexOrThrow("description")),
            level = cursor.getString(cursor.getColumnIndexOrThrow("level")),
            sourceRecordingId = if (cursor.isNull(cursor.getColumnIndexOrThrow("source_recording_id"))) null else cursor.getLong(cursor.getColumnIndexOrThrow("source_recording_id")),
            lifecycleState = SkillLifecycleState.entries.firstOrNull { it.name == cursor.getString(cursor.getColumnIndexOrThrow("lifecycle_state")) } ?: SkillLifecycleState.DRAFT,
            approvalState = cursor.getString(cursor.getColumnIndexOrThrow("approval_state")),
            approvedAt = if (cursor.isNull(cursor.getColumnIndexOrThrow("approved_at"))) null else cursor.getLong(cursor.getColumnIndexOrThrow("approved_at")),
            approvedByUser = cursor.getInt(cursor.getColumnIndexOrThrow("approved_by_user")) == 1,
        )
    }

    private fun readSkillVersion(cursor: android.database.Cursor): SkillVersionEntity {
        return SkillVersionEntity(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            skillId = cursor.getString(cursor.getColumnIndexOrThrow("skill_id")),
            versionNumber = cursor.getInt(cursor.getColumnIndexOrThrow("version_number")),
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
            description = cursor.getString(cursor.getColumnIndexOrThrow("description")),
            level = cursor.getString(cursor.getColumnIndexOrThrow("level")),
            lifecycleState = SkillLifecycleState.entries.firstOrNull { it.name == cursor.getString(cursor.getColumnIndexOrThrow("lifecycle_state")) } ?: SkillLifecycleState.DRAFT,
            sourceRecordingId = if (cursor.isNull(cursor.getColumnIndexOrThrow("source_recording_id"))) null else cursor.getLong(cursor.getColumnIndexOrThrow("source_recording_id")),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
        )
    }

    val db: SQLiteDatabase
        get() = writableDatabase
    val values: ContentValues
        get() = ContentValues()
}
