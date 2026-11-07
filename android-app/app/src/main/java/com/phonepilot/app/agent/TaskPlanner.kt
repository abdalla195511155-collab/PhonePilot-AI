package com.phonepilot.app.agent

import com.phonepilot.app.data.SkillRepository
import com.phonepilot.app.models.SkillEntity
import com.phonepilot.app.models.SkillLifecycleState

/**
 * مخطط المهام (Phase 7 — Agent Orchestration).
 *
 * يأخذ نية المستخدم ويخطط لتنفيذها بتقسيمها إلى خطوات وتحديد المهارات المطلوبة.
 */
class TaskPlanner(
    private val skillRepository: SkillRepository = SkillRepository,
) {

    /**
     * يخطط لتنفيذ نية معينة.
     *
     * يعيد TaskPlan يحتوي على الخطوات، المهارات المطلوبة، وما إذا كانت تتطلب موافقة المستخدم.
     */
    fun plan(intent: InterpretedIntent): TaskPlan {
        val steps = mutableListOf<String>()
        val requiredSkills = mutableListOf<String>()
        var requiresUserApproval = false
        val sensitiveOperations = mutableListOf<String>()

        when (intent.intent) {
            "record_task" -> {
                steps.add("إنشاء مهمة جديدة في استديو التدريب")
                steps.add("تسجيل عروض متعددة للمهمة (5-10 عروض)")
                steps.add("تشغيل تحليل التعلم لاستخراج المهارة")
                steps.add("مراجعة المهارة واعتمادها من قبل المستخدم")
                requiredSkills.add("learning_engine")
            }
            "execute_skill" -> {
                steps.add("اختيار المهارة المناسبة من المكتبة")
                steps.add("التحقق من اعتماد المهارة وموافقة المستخدم")
                steps.add("تنفيذ الخطوات عبر محرك التنفيذ")
                steps.add("تأكيد النتيجة وإبلاغ المستخدم")
                requiredSkills.add("skill_execution_engine")
                requiresUserApproval = true
            }
            "open_app" -> {
                steps.add("فتح التطبيق المستهدف: ${intent.targetApp ?: "غير محدد"}")
                steps.add("انتظار ظهور الشاشة الرئيسية للتطبيق")
                requiredSkills.add("accessibility_service")
            }
            "click_element" -> {
                steps.add("تحديد العنصر المستهدف: ${intent.targetAction ?: "غير محدد"}")
                steps.add("التحقق من عدم وجود غموض في العنصر")
                steps.add("تنفيذ النقر على العنصر")
                requiredSkills.add("element_resolver")
                requiredSkills.add("step_executor")
            }
            "input_text" -> {
                steps.add("تحديد الحقل المستهدف")
                steps.add("إدخال النص المحدد")
                steps.add("التحقق من ظهور النص في الحقل")
                requiredSkills.add("step_executor")
                sensitiveOperations.add("text_input")
            }
            "send_message" -> {
                steps.add("تحديد جهة الاستلام")
                steps.add("كتابة محتوى الرسالة")
                steps.add("طلب تأكيد الإرسال من المستخدم")
                steps.add("إرسال الرسالة بعد التأكيد")
                requiredSkills.add("accessibility_service")
                requiresUserApproval = true
                sensitiveOperations.add("message_sending")
            }
            "delete_data" -> {
                steps.add("تحديد البيانات المراد حذفها")
                steps.add("طلب تأكيد الحذف من المستخدم")
                steps.add("تنفيذ الحذف بعد التأكيد")
                requiresUserApproval = true
                sensitiveOperations.add("data_deletion")
            }
            else -> {
                steps.add("طلب توضيح من المستخدم حول العملية المطلوبة")
            }
        }

        // تحديد المهارات المتاحة التي تطابق النية
        val availableSkills = skillRepository.skills.value
        for (skill in availableSkills) {
            if (skill.lifecycleState == SkillLifecycleState.READY) {
                requiredSkills.add(skill.id)
            }
        }

        return TaskPlan(
            intent = intent,
            steps = steps,
            requiredSkills = requiredSkills.distinct(),
            requiresUserApproval = requiresUserApproval,
            sensitiveOperations = sensitiveOperations.distinct(),
        )
    }
}
