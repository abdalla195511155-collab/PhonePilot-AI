package com.phonepilot.app.models

/**
 * حالة دورة حياة المهارة (Phase 5).
 *
 * DRAFT — مسودة أولية لم تبدأ فيها عملية التعلم بعد.
 * LEARNING — يتم تسجيل العروض (términaciones) للمهمة.
 * ANALYZING — يتم تحليل العروض ومقارنة الخطوات.
 * NEEDS_REVIEW — المهارة بحاجة لمراجعة يدوية من المستخدم قبل الاعتماد.
 * VALIDATING — يتم تشغيل التحقق التلقائي على الخطوات والاعتماديات.
 * READY — المهارة معتمدة وجاهزة للاستخدام (لا يتم تنفيذها في Phase 5).
 * FAILED — فشل التعلم أو التحقق ولا يمكن الاعتماد عليها.
 * ARCHIVED — مهارة مؤرشفة غير نشطة.
 */
enum class SkillLifecycleState {
    DRAFT,
    LEARNING,
    ANALYZING,
    NEEDS_REVIEW,
    VALIDATING,
    READY,
    FAILED,
    ARCHIVED;
}

/**
 * نتيجة التحقق من مهارة (Phase 5).
 *
 * يحتوي على قرار القبول/الرفض مع قائمة بالأسباب، حتى يمكن عرضها
 * على المستخدم في شاشة المراجعة.
 */
data class SkillValidationResult(
    val valid: Boolean,
    val reasons: List<String> = emptyList(),
    val ambiguousSteps: List<Int> = emptyList(), // مؤشرات الخطوات الغامضة (1-based)
    val missingDependencies: List<String> = emptyList(),
    val confidenceScore: Double = 0.0,
)

/**
 * وصف خطوة واحدة داخل عرض (Demonstration) (Phase 5).
 *
 * يحتفظ بكل المؤشرات الدلالية المطلوبة للمقارنة بين العروض:
 * نوع التفاعل، هوية العنصر، الوصف، الدور، الحدود، وحالة الشاشة قبل وبعد.
 */
data class StepEntity(
    val sequenceNumber: Int,
    val eventType: String,
    val elementId: String?,
    val elementText: String?,
    val contentDescription: String?,
    val elementRole: String?,
    val bounds: String?,
    val beforeSnapshot: String?,
    val afterSnapshot: String?,
    val isPrivacyMasked: Boolean = false,
    val inputText: String?,
)

/**
 * تمثيل لعرض واحد (Demonstration) لمهمة (Phase 5).
 *
 * كل عرض هو تسجيل مكتمل للمستخدم ينفّذ المهمة. يتم ربطه بالتسجيل
 * الموجود في قاعدة البيانات (TaskRecordingEntity) ويحتفظ بخطواته
 * المُستخلصة للمقارنة الدلالية.
 */
data class DemonstrationEntity(
    val id: Long,
    val taskId: Long,
    val iterationNumber: Int,
    val recordedAt: Long,
    val eventCount: Int,
    val steps: List<StepEntity> = emptyList(),
)

/**
 * إصدار من مهارة (Phase 5).
 *
 * يسمح بالرجوع إلى إصدار سابق عند الحاجة، ويتبع التغييرات التي تطرأ
 * على المهارة أثناء دورة حياتها.
 */
data class SkillVersionEntity(
    val id: Long = -1,
    val skillId: String,
    val versionNumber: Int,
    val title: String,
    val description: String?,
    val level: String?,
    val lifecycleState: SkillLifecycleState,
    val sourceRecordingId: Long?,
    val createdAt: Long,
)
