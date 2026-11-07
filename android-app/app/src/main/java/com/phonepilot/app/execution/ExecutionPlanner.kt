package com.phonepilot.app.execution

import com.phonepilot.app.models.SkillEntity
import com.phonepilot.app.models.SkillValidationResult

/**
 * مخطط التنفيذ (Phase 6 — Intelligent Execution).
 *
 * يأخذ مهارة معتمدة ويخطط لتنفيذها بتقسيمها إلى خطوات قابلة للتنفيذ.
 * يتحقق من أن المهارة READY ومعتمدة قبل البدء.
 */
class ExecutionPlanner {

    /**
     * يخطط لتنفيذ مهارة.
     *
     * يعيد خطة التنفيذ أو null إذا كانت المهارة غير صالحة للتنفيذ.
     */
    fun planExecution(skill: SkillEntity, validationResult: SkillValidationResult?): ExecutionPlan? {
        // التحقق من أن المهارة READY ومعتمدة
        if (skill.lifecycleState != SkillLifecycleState.READY) return null
        if (skill.approvalState != "APPROVED") return null
        if (!skill.approvedByUser) return null

        // التحقق من نتيجة التحقق إذا كانت موجودة
        if (validationResult != null && !validationResult.valid) return null

        // تقسيم المهارة إلى خطوات (بسيطة - تُستخرج من وصف المهارة)
        val steps = extractStepsFromSkill(skill)

        return ExecutionPlan(
            skillId = skill.id,
            skillVersion = skill.version,
            steps = steps,
            requiresUserConfirmation = requiresConfirmation(skill),
        )
    }

    /** استخراج الخطوات من وصف المهارة (بسيطة). */
    private fun extractStepsFromSkill(skill: SkillEntity): List<ExecutionStep> {
        val steps = mutableListOf<ExecutionStep>()
        val lines = skill.description.split("\n").filter { it.isNotBlank() }
        for ((index, line) in lines.withIndex()) {
            steps.add(
                ExecutionStep(
                    stepNumber = index + 1,
                    description = line,
                    action = inferAction(line),
                    target = inferTarget(line),
                    isOptional = line.contains("اختياري", ignoreCase = true),
                    condition = null,
                )
            )
        }
        return if (steps.isEmpty()) {
            // خطوة افتراضية إذا لم تُستخرج خطوات
            listOf(
                ExecutionStep(
                    stepNumber = 1,
                    description = skill.title,
                    action = "click",
                    target = skill.title,
                    isOptional = false,
                    condition = null,
                )
            )
        } else steps
    }

    /** استنتاج الإجراء من وصف الخطوة. */
    private fun inferAction(description: String): String {
        val lower = description.lowercase()
        return when {
            lower.contains("انقر") || lower.contains("click") || lower.contains("اضغط") -> "click"
            lower.contains("أدخل") || lower.contains("input") || lower.contains("اكتب") -> "input"
            lower.contains("مرر") || lower.contains("scroll") -> "scroll"
            lower.contains("عودة") || lower.contains("back") -> "back"
            else -> "click"
        }
    }

    /** استنتاج الهدف من وصف الخطوة. */
    private fun inferTarget(description: String): String? {
        // استخراج الهدف بين علامتي تنصيص أو بعد السهم
        val arrowIdx = description.indexOf("→")
        if (arrowIdx >= 0) {
            return description.substring(arrowIdx + 1).trim()
        }
        return null
    }

    /** تحديد ما إذا كانت المهارة تتطلب موافقة المستخدم على execution. */
    private fun requiresConfirmation(skill: SkillEntity): Boolean {
        val sensitiveKeywords = listOf("دفع", "شراء", "حذف", "إرسال", "تحويل", "تسجيل دخول")
        return sensitiveKeywords.any { skill.description.contains(it, ignoreCase = true) }
    }
}

/** خطة تنفيذ لمهارة واحدة. */
data class ExecutionPlan(
    val skillId: String,
    val skillVersion: Int,
    val steps: List<ExecutionStep>,
    val requiresUserConfirmation: Boolean = false,
)

/** خطوة واحدة في خطة التنفيذ. */
data class ExecutionStep(
    val stepNumber: Int,
    val description: String,
    val action: String,
    val target: String?,
    val isOptional: Boolean = false,
    val condition: String? = null,
)
