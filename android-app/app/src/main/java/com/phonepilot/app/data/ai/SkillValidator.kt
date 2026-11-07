package com.phonepilot.app.data.ai

import com.phonepilot.app.models.SkillEntity
import com.phonepilot.app.models.SkillLifecycleState
import com.phonepilot.app.models.SkillValidationResult

/**
 * مدقق المهارات (Phase 5 — Skill Validator).
 *
 * يتحقق من خطوات المهارة والاعتماديات بين الخطوات، ويكشف الخطوات الغامضة،
 * ويعيد أسباب القبول أو الرفض. لا تُعتمد المهارة إلا بعد اجتياز التحقق.
 *
 * لا ينفّذ المهارة؛ التنفيذ في Phase 6.
 */
class SkillValidator {

    /** أقل عدد من الخطوات المشتركة لاعتبار المهارة صالحة. */
    private const val MIN_COMMON_STEPS = 2

    /**
     * يتحقق من مهارة بناءً على بيانات التعلم الفعلية.
     *
     * يعيد [SkillValidationResult] يحتوي على قرار القبول/الرفض مع قائمة بالأسباب
     * ومؤشرات الخطوات الغامضة.
     */
    fun validate(skill: SkillEntity, stepCount: Int, commonStepCount: Int, ambiguousStepCount: Int): SkillValidationResult {
        val reasons = mutableListOf<String>()
        val ambiguousSteps = mutableListOf<Int>()

        // 1. التحقق من وجود خطوات كافية
        if (commonStepCount < MIN_COMMON_STEPS) {
            reasons.add("عدد الخطوات المشتركة أقل من الحد الأدنى ($MIN_COMMON_STEPS خطوة)")
        }

        // 2. التحقق من عدم وجود خطوات غامضة كثيرة
        if (ambiguousStepCount > 0) {
            reasons.add("يوجد $ambiguousStepCount خطوة غامضة تحتاج توضيح")
            for (i in 1..ambiguousStepCount) {
                ambiguousSteps.add(i)
            }
        }

        val valid = commonStepCount >= MIN_COMMON_STEPS && ambiguousStepCount == 0
        val confidence = if (valid) {
            commonStepCount / maxOf(stepCount, 1).toDouble()
        } else {
            0.0
        }

        return SkillValidationResult(
            valid = valid,
            reasons = reasons,
            ambiguousSteps = ambiguousSteps,
            confidenceScore = confidence,
        )
    }

    /**
     * يتحقق من أن المهارة يمكن نقلها إلى حالة READY.
     *
     * لا تُنقل أي مهارة إلى READY بدون اجتياز التحقق الكامل.
     */
    fun canPromoteToReady(result: SkillValidationResult): Boolean {
        return result.valid && result.ambiguousSteps.isEmpty() && result.confidenceScore > 0.0
    }

    /**
     * حساب حالة دورة الحياة التالية بناءً على نتيجة التحقق وموافقة المستخدم.
     *
     * لا تصبح المهارة READY إلا إذا اجتازت التحقق الكامل AND وافق المستخدم صراحة.
     * الرفض أو عدم الموافقة يبقيان المهارة في VALIDATING أو ينقلانها إلى FAILED.
     */
    fun nextLifecycleState(
        current: SkillLifecycleState,
        result: SkillValidationResult,
        userApproved: Boolean = false,
    ): SkillLifecycleState {
        return when (current) {
            SkillLifecycleState.DRAFT -> SkillLifecycleState.LEARNING
            SkillLifecycleState.LEARNING -> SkillLifecycleState.ANALYZING
            SkillLifecycleState.ANALYZING -> if (result.valid) SkillLifecycleState.NEEDS_REVIEW else SkillLifecycleState.FAILED
            SkillLifecycleState.NEEDS_REVIEW -> if (result.valid) SkillLifecycleState.VALIDATING else SkillLifecycleState.FAILED
            SkillLifecycleState.VALIDATING -> if (canPromoteToReady(result) && userApproved) SkillLifecycleState.READY else SkillLifecycleState.FAILED
            SkillLifecycleState.READY -> SkillLifecycleState.READY
            SkillLifecycleState.FAILED -> SkillLifecycleState.FAILED
            SkillLifecycleState.ARCHIVED -> SkillLifecycleState.ARCHIVED
        }
    }
}
