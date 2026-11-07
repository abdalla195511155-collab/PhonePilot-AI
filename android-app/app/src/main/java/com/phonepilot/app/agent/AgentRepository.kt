package com.phonepilot.app.agent

import com.phonepilot.app.database.TaskDatabaseHelper
import com.phonepilot.app.models.AgentMemoryEntry
import com.phonepilot.app.models.ExecutionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * مستودع الوكيل (Phase 7 — Agent Orchestration).
 *
 * يحفظ بيانات الوكيل في قاعدة بيانات SQLite:
 * - ذاكرة المهام ونتائجها
 * - حالة التنفيذ الحالية
 * - سجل الأوامر
 */
class AgentRepository(
    private val dbHelper: TaskDatabaseHelper,
    private val scope: CoroutineScope,
) {

    private val _agentState = MutableStateFlow<AgentExecutionState>(AgentExecutionState.IDLE)
    val agentState: StateFlow<AgentExecutionState> = _agentState.asStateFlow()

    /**
     * حفظ إدخال ذاكرة في قاعدة البيانات.
     */
    suspend fun saveMemoryEntry(entry: AgentMemoryEntry) {
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
     * جلب ذاكرة مهمة معينة من قاعدة البيانات.
     */
    suspend fun getTaskMemoryFromDb(taskId: String): List<AgentMemoryEntry> {
        return withContext(Dispatchers.IO) {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                "agent_memory",
                null,
                "task_id = ?",
                arrayOf(taskId),
                null, null, "created_at ASC"
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
     * تحديث حالة الوكيل الحالية.
     */
    fun updateAgentState(state: AgentExecutionState) {
        _agentState.value = state
    }

    /**
     * جلب حالة الوكيل الحالية.
     */
    fun getAgentState(): AgentExecutionState {
        return _agentState.value
    }
}
