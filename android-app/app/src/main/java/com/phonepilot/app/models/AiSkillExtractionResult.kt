package com.phonepilot.app.models

/**
 * نموذج استخراج مهارة عبر نموذج AI (Phase 5).
 *
 * يحدّد شكل الاستجابة المتوقعة من النموذج عند طلب استخراج خطوات المهارة
 * من عروض متعددة. يتم التحقق منه محلياً قبل التخزين، ولا يُسمح له بتجاوز
 * [PrivacyFilter].
 */
data class AiSkillExtractionResult(
    val steps: List<AiExtractedStep>,
    val exceptions: List<AiExtractedException> = emptyList(),
    val confidence: Double,
    val reasoning: String?,
)

/**
 * خطوة مستخرجة من نموذج AI.
 */
data class AiExtractedStep(
    val stepNumber: Int,
    val action: String, // click, input, scroll, back, ...
    val target: String, // وصف العنصر المستهدف
    val elementRole: String?,
    val verification: String?, // ما يتحقق منه بعد الخطوة
    val isOptional: Boolean = false,
    val condition: String?, // شرط تنفيذ الخطوة (مثل "إذا ظهرت نافذة منبثقة")
)

/**
 * استثناء مستخرج من نموذج AI.
 */
data class AiExtractedException(
    val condition: String,
    val action: String,
)
