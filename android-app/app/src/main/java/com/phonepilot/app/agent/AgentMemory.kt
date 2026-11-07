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
 * الذاكرة دائمة: تُحفظ في [agent_memory] عبر [TaskDatabaseHelper] وتُحمّل عند الإنشاء،
 * لذا لا تُفقد عند إعادة تشغيل التطبيق.
 */
class AgentMemory(
    private val scope: CoroutineScope,
    private val dbHelper: TaskDatabaseHelper,
) {

    private val _memory = MutableStateFlow(mutableListOf<AgentMemoryEntry>())
    val memory: StateFlow<List<AgentMemoryEntry>> = _memory.asStateFlow()

    init {
        // تحميل الذاكرة المحفوظة من SQLite عند الإنشاء
        scope.launch {
            val saved = loadFromDb()
            _memory.update { saved }
        }
    }

    private suspend fun loadFromDb(): List<AgentMemoryEntry> {
        return withContext(Dispatchers.IO) {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                "agent_memory",
                null,
                null, null, null, null, "created_at ASC"
            )
            try {
                val results = mutableListOf<AgentMemoryEntry>()
                while (cursor.moveToNext()) {
                    results.add(AgentMemoryEntry(
                        taskId = cursor.getString(cursor.getColumnIndexOrThrow("task_id")),
                        intent = cursor.getString(cursor.getColumnIndexOrThrow("intent")),
                        skillId = if (cursor.isNull(cursor.getColumnIndexOrThrow("skill_id"))) null else cursor.getString(cursor.getColumnIndexOrThrow("skill_id")),
                        executionState = ExecutionState.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("execution_state"))),
                        completedAt = if (cursor.isNull(cursor.getColumnIndexOrThrow("completed_at"))) null else cursor.getLong(cursor.getColumnIndexOrThrow("completed_at")),
                        success = cursor.getInt(cursor.getColumnIndexOrThrow("success")) == 1,
                        notes = if (cursor.isNull(cursor.getColumnIndexOrThrow("notes"))) null else cursor.getString(cursor.getColumnIndexOrThrow("notes")),
                    ))
                }
                results
            } finally {
                cursor.close()
            }
        }
    }

    /**
     * تسجل إدخال ذاكرة جديد لمهمة.
     *
     * لا تُخزن كلمات المرور أو API Keys أو أي بيانات حساسة.
     * يُحفظ فوراً في SQLite وفي الذاكرة الفورية للشاشة.
     */
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
        // تحديث الذاكرة الفورية للشاشة
        scope.launch {
            _memory.update { it + entry }
        }
        // التخزين الدائم في SQLite
        scope.launch {
            saveToDb(entry)
        }
    }

    private suspend fun saveToDb(entry: AgentMemoryEntry) {
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
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

    /**
     * جلب ذاكرة لمهمة معينة (من الذاكرة الفورية).
     */
    fun getTaskMemory(taskId: String): List<AgentMemoryEntry> {
        return _memory.value.filter { it.taskId == taskId }
    }

    /**
     * جلب آخر N إدخالات ذاكرة (من الذاكرة الفورية).
     */
    fun getRecentMemory(limit: Int = 10): List<AgentMemoryEntry> {
        return _memory.value.takeLast(limit)
    }

    /**
     * مسح ذاكرة مهمة معينة (احترام الحذف والخصوصية).
     * يُحذف من SQLite والذاكرة الفورية.
     */
    fun clearTaskMemory(taskId: String) {
        scope.launch {
            _memory.update { it.filter { it.taskId != taskId } }
            withContext(Dispatchers.IO) {
                val db = dbHelper.writableDatabase
                db.delete("agent_memory", "task_id = ?", arrayOf(taskId))
            }
        }
    }

    /**
     * التحقق من أن هناك سياقاً سابقاً لمهمة يمكن الاعتماد عليه.
     */
    fun hasPreviousContext(taskId: String): Boolean {
        return _memory.value.any { it.taskId == taskId }
    }
}
