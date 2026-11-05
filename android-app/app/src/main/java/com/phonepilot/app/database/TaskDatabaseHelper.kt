package com.phonepilot.app.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.phonepilot.app.models.TaskEntity
import com.phonepilot.app.models.TaskRecordingEntity

class TaskDatabaseHelper private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "phonepilot.db"
        private const val DATABASE_VERSION = 1
        @Volatile private var INSTANCE: TaskDatabaseHelper? = null

        fun getInstance(context: Context): TaskDatabaseHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TaskDatabaseHelper(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TASKS)
        db.execSQL(CREATE_RECORDINGS)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS task_recordings")
        db.execSQL("DROP TABLE IF EXISTS tasks")
        onCreate(db)
    }

    fun insertTask(name: String, description: String?): Long {
        val db = writableDatabase
        return db.insert("tasks", null, mapOf(
            "name" to name,
            "description" to description,
            "created_at" to System.currentTimeMillis(),
            "status" to "idle"
        ))
    }

    fun insertRecording(taskId: Long, iterationNumber: Int, rawEvents: String): Long {
        val db = writableDatabase
        return db.insert("task_recordings", null, mapOf(
            "task_id" to taskId,
            "iteration_number" to iterationNumber,
            "recorded_at" to System.currentTimeMillis(),
            "raw_events" to rawEvents
        ))
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

    fun getRecordingsForTask(taskId: Long): List<TaskRecordingEntity> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM task_recordings WHERE task_id = ? ORDER BY recorded_at DESC",
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

    companion object {
        private const val CREATE_TASKS =
            "CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, description TEXT, created_at INTEGER NOT NULL, status TEXT NOT NULL)"

        private const val CREATE_RECORDINGS =
            "CREATE TABLE task_recordings (id INTEGER PRIMARY KEY AUTOINCREMENT, task_id INTEGER NOT NULL, iteration_number INTEGER NOT NULL, recorded_at INTEGER NOT NULL, raw_events TEXT NOT NULL, FOREIGN KEY(task_id) REFERENCES tasks(id) ON DELETE CASCADE)"
    }
}
