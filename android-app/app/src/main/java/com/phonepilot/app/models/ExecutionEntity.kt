package com.phonepilot.app.models

/**
 * سجل تنفيذ مهارة واحدة (Phase 6 — Intelligent Execution).
 *
 * يُسجّل كل عملية تنفيذ لمهارة معتمدة (READY) بما في ذلك الخطوات المنفذة،
 * النتائج، أسباب الفشل، ومحاولات الاستعادة.
 */
data class ExecutionEntity(
    val id: Long = -1,
    val skillId: String,
    val skillVersion: Int,
    val taskId: Long?,
    val state: String, // ExecutionState name
    val startedAt: Long,
    val completedAt: Long? = null,
    val totalSteps: Int = 0,
    val completedSteps: Int = 0,
    val failedSteps: Int = 0,
    val skippedSteps: Int = 0,
    val failureReason: String? = null,
    val recoveryAttempts: Int = 0,
)

/**
 * نتيجة تنفيذ خطوة واحدة (Phase 6 — Intelligent Execution).
 *
 * يُسجّل نتيجة كل خطوة بما في ذلك ما إذا نجحت، क्या تم التحقق منه، وأسباب الفشل.
 */
data class StepExecutionResult(
    val stepNumber: Int,
    val stepDescription: String,
    val success: Boolean,
    val verified: Boolean = false,
    val elementResolved: Boolean = false,
    val elementId: String? = null,
    val actionTaken: String? = null,
    val failureReason: String? = null,
    val recoveryAttempted: Boolean = false,
    val recoverySucceeded: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val screenBefore: String? = null, // JSON snapshot before action
    val screenAfter: String? = null, // JSON snapshot after action
)
