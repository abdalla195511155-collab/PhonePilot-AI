package com.phonepilot.app.agent

import com.phonepilot.app.models.SkillEntity
import com.phonepilot.app.models.ExecutionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * حالة تنفيذ الوكيل (Phase 7 — Agent Orchestration).
 *
 * تتبع حالة الوكيل الحالي والمهام التي يعمل عليها.
 */
enum class AgentExecutionState {
    IDLE,
    INTERPRETING_INTENT,
    PLANNING_TASK,
    SELECTING_SKILL,
    WAITING_FOR_APPROVAL,
    EXECUTING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

/**
 * نتيجة تفسير النية من أمر المستخدم.
 */
data class InterpretedIntent(
    val rawCommand: String,
    val intent: String, //例如: "open_app", "fill_form", "click_button", "send_message"
    val targetApp: String?,
    val targetAction: String?,
    val parameters: Map<String, String> = emptyMap(),
    val confidence: Float = 0.0f,
    val missingInfo: List<String> = emptyList(),
    val requiresClarification: Boolean = false,
)

/**
 * خطة مهمة من المخطط.
 */
data class TaskPlan(
    val intent: InterpretedIntent,
    val steps: List<String>,
    val requiredSkills: List<String>,
    val estimatedDurationMs: Long = 0,
    val requiresUserApproval: Boolean = false,
    val sensitiveOperations: List<String> = emptyList(),
)

/**
 * نتيجة اختيار المهارة.
 */
data class SkillSelectionResult(
    val selectedSkill: SkillEntity?,
    val selectionReason: String,
    val confidence: Float = 0.0f,
    val alternatives: List<SkillEntity> = emptyList(),
)

/**
 * حالة الذاكرة الوكيلية (Phase 7 — Agent Orchestration).
 *
 * يحفظ سياق المهام ونتائجها دون تخزين بيانات حساسة.
 */
data class AgentMemoryEntry(
    val taskId: String,
    val intent: String,
    val skillId: String?,
    val executionState: ExecutionState,
    val completedAt: Long?,
    val success: Boolean,
    val notes: String? = null,
)

/**
 * أمر مستلم من أي قناة (تيليغرام، واجهة أندرويد، إلخ).
 */
data class UserCommand(
    val commandId: String,
    val rawText: String,
    val source: String, // "telegram", "android_ui", ...
    val userId: String?,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * نتيجة تنفيذ الأمر من قبل الوكيل.
 */
data class AgentCommandResult(
    val commandId: String,
    val success: Boolean,
    val message: String,
    val executionId: Long? = null,
    val requiresUserAction: Boolean = false,
    val userActionType: String? = null, // "approve", "reject", "provide_info", ...
    val data: Map<String, Any?> = emptyMap(),
)
