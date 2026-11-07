package com.phonepilot.app.execution

import android.content.ContentValues
import android.content.Context
import com.phonepilot.app.database.TaskDatabaseHelper
import com.phonepilot.app.models.ExecutionEntity
import com.phonepilot.app.models.ExecutionState
import com.phonepilot.app.models.StepExecutionResult

/**
 * مستودع تنفيذ المهارات (Phase 6 — Intelligent Execution).
 *
 * يُحفظ سجلات التنفيذ في قاعدة بيانات SQLite عبر [TaskDatabaseHelper].
 * يدعم تسجيل بداية التنفيذ، تحديث حالة الخطوات، وتسجيل النتيجة النهائية.
 */
class ExecutionRepository(private val dbHelper: TaskDatabaseHelper) {

    /** بدء تنفيذ مهارة جديدة. يعيد معرف السجل المُدرج. */
    fun startExecution(
        skillId: String,
        skillVersion: Int,
        taskId: Long?,
    ): Long {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("skill_id", skillId)
                put("skill_version", skillVersion)
                put("task_id", taskId)
                put("state", ExecutionState.PENDING.name)
                put("started_at", System.currentTimeMillis())
                put("total_steps", 0)
                put("completed_steps", 0)
                put("failed_steps", 0)
                put("skipped_steps", 0)
                put("failure_reason", "")
                put("recovery_attempts", 0)
            }
            val id = db.insert("executions", null, values)
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }

    /** تحديث تقدم التنفيذ (عدد الخطوات المكتملة/الفاشلة). */
    fun updateProgress(
        executionId: Long,
        totalSteps: Int,
        completedSteps: Int,
        failedSteps: Int,
        skippedSteps: Int,
    ): Int {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("total_steps", totalSteps)
            put("completed_steps", completedSteps)
            put("failed_steps", failedSteps)
            put("skipped_steps", skippedSteps)
        }
        return db.update("executions", values, "id = ?", arrayOf(executionId.toString()))
    }

    /** تحديث حالة التنفيذ فقط (مثال: PENDING → RUNNING). */
    fun updateState(executionId: Long, state: ExecutionState): Int {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("state", state.name)
        }
        return db.update("executions", values, "id = ?", arrayOf(executionId.toString()))
    }

    /** إنهاء تنفيذ مع حالة نهائية وسبب الفشل (إن وجد). */
    fun finishExecution(
        executionId: Long,
        state: ExecutionState,
        failureReason: String? = null,
        recoveryAttempts: Int = 0,
    ): Int {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("state", state.name)
            put("completed_at", System.currentTimeMillis())
            put("failure_reason", failureReason)
            put("recovery_attempts", recoveryAttempts)
        }
        return db.update("executions", values, "id = ?", arrayOf(executionId.toString()))
    }

    /** تسجيل نتيجة خطوة واحدة في التنفيذ. */
    fun recordStepResult(
        executionId: Long,
        stepResult: StepExecutionResult,
    ): Long {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("execution_id", executionId)
                put("step_number", stepResult.stepNumber)
                put("step_description", stepResult.stepDescription)
                put("success", if (stepResult.success) 1 else 0)
                put("verified", if (stepResult.verified) 1 else 0)
                put("element_resolved", if (stepResult.elementResolved) 1 else 0)
                put("element_id", stepResult.elementId)
                put("action_taken", stepResult.actionTaken)
                put("failure_reason", stepResult.failureReason)
                put("recovery_attempted", if (stepResult.recoveryAttempted) 1 else 0)
                put("recovery_succeeded", if (stepResult.recoverySucceeded) 1 else 0)
                put("timestamp", stepResult.timestamp)
                put("screen_before", stepResult.screenBefore)
                put("screen_after", stepResult.screenAfter)
            }
            val id = db.insert("step_results", null, values)
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }

    /** جلب سجل تنفيذ بالمعرف. */
    fun getExecution(executionId: Long): ExecutionEntity? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "executions",
            null,
            "id = ?",
            arrayOf(executionId.toString()),
            null, null, null
        )
        try {
            if (!cursor.moveToFirst()) return null
            return readExecution(cursor)
        } finally {
            cursor.close()
        }
    }

    /** جلب جميع عمليات تنفيذ لمهارة معينة. */
    fun getExecutionsForSkill(skillId: String): List<ExecutionEntity> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "executions",
            null,
            "skill_id = ?",
            arrayOf(skillId),
            null, null, "started_at DESC"
        )
        try {
            val results = mutableListOf<ExecutionEntity>()
            while (cursor.moveToNext()) {
                results.add(readExecution(cursor))
            }
            return results
        } finally {
            cursor.close()
        }
    }

    /** جلب جميع نتائج الخطوات لتنفيذ معين. */
    fun getStepResults(executionId: Long): List<StepExecutionResult> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "step_results",
            null,
            "execution_id = ?",
            arrayOf(executionId.toString()),
            null, null, "step_number ASC"
        )
        try {
            val results = mutableListOf<StepExecutionResult>()
            while (cursor.moveToNext()) {
                results.add(readStepResult(cursor))
            }
            return results
        } finally {
            cursor.close()
        }
    }

    private fun readExecution(cursor: android.database.Cursor): ExecutionEntity {
        return ExecutionEntity(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            skillId = cursor.getString(cursor.getColumnIndexOrThrow("skill_id")),
            skillVersion = cursor.getInt(cursor.getColumnIndexOrThrow("skill_version")),
            taskId = if (cursor.isNull(cursor.getColumnIndexOrThrow("task_id"))) null else cursor.getLong(cursor.getColumnIndexOrThrow("task_id")),
            state = cursor.getString(cursor.getColumnIndexOrThrow("state")),
            startedAt = cursor.getLong(cursor.getColumnIndexOrThrow("started_at")),
            completedAt = if (cursor.isNull(cursor.getColumnIndexOrThrow("completed_at"))) null else cursor.getLong(cursor.getColumnIndexOrThrow("completed_at")),
            totalSteps = cursor.getInt(cursor.getColumnIndexOrThrow("total_steps")),
            completedSteps = cursor.getInt(cursor.getColumnIndexOrThrow("completed_steps")),
            failedSteps = cursor.getInt(cursor.getColumnIndexOrThrow("failed_steps")),
            skippedSteps = cursor.getInt(cursor.getColumnIndexOrThrow("skipped_steps")),
            failureReason = if (cursor.isNull(cursor.getColumnIndexOrThrow("failure_reason"))) null else cursor.getString(cursor.getColumnIndexOrThrow("failure_reason")),
            recoveryAttempts = cursor.getInt(cursor.getColumnIndexOrThrow("recovery_attempts")),
        )
    }

    private fun readStepResult(cursor: android.database.Cursor): StepExecutionResult {
        return StepExecutionResult(
            stepNumber = cursor.getInt(cursor.getColumnIndexOrThrow("step_number")),
            stepDescription = cursor.getString(cursor.getColumnIndexOrThrow("step_description")),
            success = cursor.getInt(cursor.getColumnIndexOrThrow("success")) == 1,
            verified = cursor.getInt(cursor.getColumnIndexOrThrow("verified")) == 1,
            elementResolved = cursor.getInt(cursor.getColumnIndexOrThrow("element_resolved")) == 1,
            elementId = if (cursor.isNull(cursor.getColumnIndexOrThrow("element_id"))) null else cursor.getString(cursor.getColumnIndexOrThrow("element_id")),
            actionTaken = if (cursor.isNull(cursor.getColumnIndexOrThrow("action_taken"))) null else cursor.getString(cursor.getColumnIndexOrThrow("action_taken")),
            failureReason = if (cursor.isNull(cursor.getColumnIndexOrThrow("failure_reason"))) null else cursor.getString(cursor.getColumnIndexOrThrow("failure_reason")),
            recoveryAttempted = cursor.getInt(cursor.getColumnIndexOrThrow("recovery_attempted")) == 1,
            recoverySucceeded = cursor.getInt(cursor.getColumnIndexOrThrow("recovery_succeeded")) == 1,
            timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
            screenBefore = if (cursor.isNull(cursor.getColumnIndexOrThrow("screen_before"))) null else cursor.getString(cursor.getColumnIndexOrThrow("screen_before")),
            screenAfter = if (cursor.isNull(cursor.getColumnIndexOrThrow("screen_after"))) null else cursor.getString(cursor.getColumnIndexOrThrow("screen_after")),
        )
    }
}
