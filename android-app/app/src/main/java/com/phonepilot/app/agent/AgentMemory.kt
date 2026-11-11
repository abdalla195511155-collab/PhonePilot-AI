package com.phonepilot.app.agent

import com.phonepilot.app.database.TaskDatabaseHelper
import com.phonepilot.app.models.ExecutionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ذاكرة الوكيل (Phase 7 — Agent Orchestration).
 *
 * تحفظ سياق المهام ونتائجها دون تخزين بيانات حساسة (كلمات مرور، API Keys).
 * الذاكرة دائمة عند توفير [TaskDatabaseHelper]، كما يمكن استخدامها بذاكرة مؤقتة
 * فقط في الاختبارات أو السياقات التي لا تحتاج تخزيناً دائماً.
 */
class AgentMemory(
    private val scope: CoroutineScope,
    private val dbHelper: TaskDatabaseHelper? = null,
) {
    private val _memory = MutableStateFlow<List<AgentMemoryEntry>>(emptyList())
    val memory: StateFlow<List<AgentMemoryEntry>> = _memory.asStateFlow()

    init {
        if (dbHelper != null) {
            scope.launch {
                _memory.value = loadFromDb(dbHelper)
            }
        }
    }

    private suspend fun loadFromDb(helper: TaskDatabaseHelper): List<AgentMemoryEntry> =
        withContext(Dispatchers.IO) {
            val cursor = helper.readableDatabase.query(
                "agent_memory",
                null,
                null,
                null,
                null,
                null,
                "created_at ASC",
            )
            try {
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            AgentMemoryEntry(
                                taskId = cursor.getString(cursor.getColumnIndexOrThrow("task_id")),
                                intent = cursor.getString(cursor.getColumnIndexOrThrow("intent")),
                                skillId = if (cursor.isNull(cursor.getColumnIndexOrThrow("skill_id"))) null else cursor.getString(cursor.getColumnIndexOrThrow("skill_id")),
                                executionState = ExecutionState.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("execution_state"))),
                                completedAt = if (cursor.isNull(cursor.getColumnIndexOrThrow("completed_at"))) null else cursor.getLong(cursor.getColumnIndexOrThrow("completed_at")),
                                success = cursor.getInt(cursor.getColumnIndexOrThrow("success")) == 1,
                                notes = if (cursor.isNull(cursor.getColumnIndexOrThrow("notes"))) null else cursor.getString(cursor.getColumnIndexOrThrow("notes")),
                            ),
                        )
                    }
                }
            } finally {
                cursor.close()
            }
        }

    /** Record a task in memory and persist it when a database helper is configured. */
    fun recordTask(
        taskId: String,
        intent: String,
        skillId: String?,
        executionState: ExecutionState,
        completedAt: Long?,
        success: Boolean,
        notes: String? = null,
    ) {
        val entry = AgentMemoryEntry(
            taskId = taskId,
            intent = intent,
            skillId = skillId,
            executionState = executionState,
            completedAt = completedAt,
            success = success,
            notes = notes,
        )
        scope.launch {
            _memory.value = _memory.value + entry
        }
        dbHelper?.let { helper ->
            scope.launch { saveToDb(helper, entry) }
        }
    }

    private suspend fun saveToDb(helper: TaskDatabaseHelper, entry: AgentMemoryEntry) {
        withContext(Dispatchers.IO) {
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                val values = android.content.ContentValues().apply {
                    put("task_id", entry.taskId)
                    put("intent", entry.intent)
                    put("skill_id", entry.skillId)
                    put("execution_state", entry.executionState.name)
                    put("completed_at", entry.completedAt)
                    put("success", if (entry.success) 1 else 0)
                    put("notes", entry.notes)
                    put("created_at", System.currentTimeMillis())
                }
                db.insert("agent_memory", null, values)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    fun getTaskMemory(taskId: String): List<AgentMemoryEntry> =
        _memory.value.filter { it.taskId == taskId }

    fun getRecentMemory(limit: Int = 10): List<AgentMemoryEntry> =
        _memory.value.takeLast(limit)

    fun clearTaskMemory(taskId: String) {
        scope.launch {
            _memory.value = _memory.value.filter { it.taskId != taskId }
            dbHelper?.let { helper ->
                withContext(Dispatchers.IO) {
                    helper.writableDatabase.delete("agent_memory", "task_id = ?", arrayOf(taskId))
                }
            }
        }
    }

    fun hasPreviousContext(taskId: String): Boolean =
        _memory.value.any { it.taskId == taskId }
}
