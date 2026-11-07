package com.phonepilot.app.agent

import com.phonepilot.app.data.SkillRepository
import com.phonepilot.app.models.SkillEntity
import com.phonepilot.app.models.SkillLifecycleState

/**
 * مSelector المهارات (Phase 7 — Agent Orchestration).
 *
 * يختار المهارة الأنسب للنية المحددة.
 *
 * يعيد SkillSelectionResult يحتوي على المهارة المختارة وسبب الاختيار.
 * لا تُختار مهارة غير READY أو غير معتمدة.
 */
class SkillSelector(
    private val skillRepository: SkillRepository = SkillRepository,
) {

    /**
     * يختار المهارة الأنسب للنية المحددة.
     *
     * يعيد SkillSelectionResult يحتوي على المهارة المختارة وسبب الاختيار.
     * لا تُختار مهارة غير READY أو غير معتمدة.
     */
    fun selectSkill(intent: InterpretedIntent, context: String? = null): SkillSelectionResult {
        val availableSkills = skillRepository.skills.value
            .filter { it.lifecycleState == SkillLifecycleState.READY.name }
            .filter { it.approvalState == "APPROVED" }
            .filter { it.approvedByUser == true }

        if (availableSkills.isEmpty()) {
            return SkillSelectionResult(
                selectedSkill = null,
                selectionReason = "لا توجد مهارات معتمدة وجاهزة للتنفيذ",
                confidence = 0.0f,
            )
        }

        // تصفية المهارات بناءً على النية
        val matchingSkills = availableSkills.filter { skill ->
            val text = "${skill.title} ${skill.description}".lowercase()
            intent.intent.lowercase().split("_").any { keyword ->
                text.contains(keyword)
            } || intent.targetApp != null && text.contains(intent.targetApp!!.lowercase())
        }

        val selected = if (matchingSkills.isNotEmpty()) {
            matchingSkills.first() // اختيار أول مهارة مطابقة
        } else {
            // لا تُفرض مهارة غير مطابقة كبديل — الإرجاع null يجبر على مراجعة النية
            return SkillSelectionResult(
                selectedSkill = null,
                selectionReason = "لا توجد مهارة معتمدة ومطابقة للنية المطلوبة",
                confidence = 0.0f,
                alternatives = availableSkills.take(3),
            )
        }

        return SkillSelectionResult(
            selectedSkill = selected,
            selectionReason = "تم اختيار المهارة بناءً على تطابق الهدف والسياق",
            confidence = 0.8f,
            alternatives = availableSkills.filter { it.id != selected.id }.take(3),
        )
    }

    /**
     * يتحقق من أن مهارة معينة صالحة للتنفيذ.
     */
    fun isSkillExecutable(skill: SkillEntity): Boolean {
        return skill.lifecycleState == SkillLifecycleState.READY.name
            && skill.approvalState == "APPROVED"
            && skill.approvedByUser == true
    }
}
