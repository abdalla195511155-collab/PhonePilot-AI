package com.phonepilot.app.data.ai

import com.phonepilot.app.database.TaskDatabaseHelper
import com.phonepilot.app.models.EventEntity
import com.phonepilot.app.models.SkillEntity
import com.phonepilot.app.models.SkillLifecycleState
import com.phonepilot.app.models.SkillValidationResult

/**
 * محرك التعلم المحسّن (Phase 5 — Learning & Skill Engine).
 *
 * يقارن أحداث التسجيلات المكتملة لمهمة واحدة عبر تكرارات متعددة باستخدام
 * المقارنة الدلالية (ليست مجرد نسبة تطابق)، ويستخرج الخطوات المشتركة،
 * يحدد الاستثناءات والخطوات الغامضة، وينتج [SkillEntity] مع درجة ثقة محسوبة
 * من مؤشرات فعلية وأسباب التقييم.
 *
 * لا يُستدعى نموذج AI إلا عند الحاجة (عبر [AiSkillExtractor])؛ المحرك الأساسي
 * قواعدية ومحلي بالكامل ولا يرسل صوراً ولا بيانات حساسة.
 */
class LearningEngine(private val db: TaskDatabaseHelper) {

    /** أقل عدد من التكرارات المكتملة المطلوب لبدء التعلم (المتطلب: 5–10 عروض). */
    private const val MIN_ITERATIONS = 5

    /** عتبة الحد الأدنى للثقة لاعتبار المهارة قابلة للمراجعة (ليست جاهزة للتنفيذ). */
    private const val MIN_CONFIDENCE_FOR_REVIEW = 0.5

    /** عتبة الثقة لاعتبار المهارة "متماسكة" (كثير من الخطوات مشتركة). */
    private const val COHERENT_CONFIDENCE = 0.75

    /**
     * يتعلّم من [taskId] من خلال مقارنة تسجيلاته المكتملة.
     *
     * يعيد [SkillEntity] مع [SkillLifecycleState.ANALYZING] إذا وجدت بيانات كافية،
     * أو [null] إذا لم تتوفر بيانات كافية للتعلم.
     *
     * لا تُعتمد المهارة هنا؛ تُترك في حالة ANALYGING لمراحل لاحقة (التحقق والمراجعة).
     */
    fun learnTask(taskId: Long): SkillEntity? {
        val recordings = db.getRecordingsForTask(taskId)
        if (recordings.size < MIN_ITERATIONS) return null

        // أحداث كل تكرار مرتبة حسب رقم التسلسل
        val iterations = recordings.map { recording ->
            recording.id to db.getEventsForRecording(recording.id)
                .sortedBy { it.sequenceNumber }
        }

        val maxLength = iterations.map { it.second.size }.maxOrNull() ?: 0
        if (maxLength == 0) return null

        // تحليل كل موقع في التسلسل
        val stepAnalysis = mutableListOf<StepAnalysis>()
        for (pos in 0 until maxLength) {
            val eventsAtPos = iterations
                .filter { it.second.size > pos }
                .map { it.second[pos] }
            if (eventsAtPos.isEmpty()) continue

            stepAnalysis.add(analyzePosition(pos, eventsAtPos, iterations.size))
        }

        val commonSteps = stepAnalysis.filter { it.isCommon }.map { it.stepDescription }
        val optionalSteps = stepAnalysis.filter { it.isOptional }.map { it.stepDescription }
        val ambiguousSteps = stepAnalysis.filter { it.isAmbiguous }.map { it.stepDescription }
        val exceptions = stepAnalysis.filter { it.isException }.map { it.exceptionDescription }

        if (commonSteps.isEmpty() && optionalSteps.isEmpty()) return null

        // حساب الثقة من مؤشرات فعلية، bukan نسبة واحدة
        val confidence = calculateConfidence(
            commonSteps = commonSteps.size,
            optionalSteps = optionalSteps.size,
            ambiguousSteps = ambiguousSteps.size,
            exceptionCount = exceptions.size,
            iterationCount = iterations.size,
            totalPositions = maxLength,
        )

        // إذا كانت الثقة منخفضة جداً، لا نستخرج مهارة
        if (confidence < MIN_CONFIDENCE_FOR_REVIEW && commonSteps.isEmpty()) return null

        val title = "مهمة #$taskId"
        val description = buildPlanDescription(
            commonSteps, optionalSteps, ambiguousSteps, exceptions, iterations.size
        )

        // مستوى المهارة يعتمد على الثقة والخطوات المشتركة
        val level = when {
            confidence >= COHERENT_CONFIDENCE && commonSteps.size >= 3 -> "متقن"
            confidence >= MIN_CONFIDENCE_FOR_REVIEW && commonSteps.isNotEmpty() -> "أساسي"
            else -> "مبدئي"
        }

        val state = when {
            confidence < MIN_CONFIDENCE_FOR_REVIEW -> SkillLifecycleState.FAILED
            ambiguousSteps.isNotEmpty() || exceptions.isNotEmpty() -> SkillLifecycleState.NEEDS_REVIEW
            else -> SkillLifecycleState.ANALYZING
        }

        val sourceRecordingId = recordings.last().id

        val skill = SkillEntity(
            id = "",
            title = title,
            description = description,
            level = level,
            sourceRecordingId = sourceRecordingId
        )

        val newId = db.insertSkillWithState(
            taskId = taskId,
            title = skill.title,
            description = skill.description,
            level = skill.level,
            sourceRecordingId = skill.sourceRecordingId,
            state = state,
        )
        return if (newId > 0) {
            db.updateSkillState(newId, confidenceScore = confidence)
            skill.copy(id = newId.toString())
        } else null
    }

    /**
     * تحليل موقع واحد في التسلسل عبر جميع العروض.
     *
     * يحدد ما إذا كان الموقع يمثل خطوة مشتركة، اختيارية، غامضة، أو استثناء.
     */
    private data class StepAnalysis(
        val position: Int,
        val isCommon: Boolean,
        val isOptional: Boolean,
        val isAmbiguous: Boolean,
        val isException: Boolean,
        val stepDescription: String,
        val exceptionDescription: String?,
        val matchRatio: Float,
    )

    private fun analyzePosition(
        pos: Int,
        eventsAtPos: List<EventEntity>,
        totalIterations: Int
    ): StepAnalysis {
        // تجميع الأحداث بمفتاح دلالي (نوع التفاعل + هوية العنصر + الدور)
        val groups = eventsAtPos.groupBy { semanticKey(it) }
        val (dominantKey, dominantGroup) = groups.maxByOrNull { it.value.size } ?: run {
            return StepAnalysis(
                position = pos,
                isCommon = false,
                isOptional = false,
                isAmbiguous = true,
                isException = true,
                stepDescription = "الخطوة ${pos + 1}: عناصر متضاربة",
                exceptionDescription = "لا يمكن تحديد هدف موحد",
                matchRatio = 0f,
            )
        }

        val matchRatio = dominantGroup.size / eventsAtPos.size.toFloat()
        val isCommon = matchRatio >= 0.6f && dominantGroup.size >= 2
        val isOptional = matchRatio >= 0.2f && matchRatio < 0.6f && dominantGroup.size >= 1
        val isAmbiguous = groups.size > 1 && matchRatio < 0.6f && dominantGroup.size >= 1
        val isException = !isCommon && !isOptional && !isAmbiguous

        val stepDescription = formatStep(dominantKey)
        val exceptionDescription = if (isAmbiguous || isException) {
            "الخطوة ${pos + 1}: تباين عبر ${groups.size} أنماط مختلفة"
        } else null

        return StepAnalysis(
            position = pos,
            isCommon = isCommon,
            isOptional = isOptional,
            isAmbiguous = isAmbiguous,
            isException = isException,
            stepDescription = stepDescription,
            exceptionDescription = exceptionDescription,
            matchRatio = matchRatio,
        )
    }

    /**
     * مفتاح دلالي للمقارنة: (نوع التفاعل، هوية العنصر، الدور، الوصف).
     *
     * لا نعتمد على الإحداثيات وحدها لأن موقع العنصر قد يتغير بين العروض
     * بينما يبقى الدلالي ثابتاً.
     */
    private fun semanticKey(event: EventEntity): Triple<String, String, String> {
        val role = event.contentDescription
            ?: event.viewId
            ?: event.text
            ?: event.className
            ?: "غير محدد"
        return Triple(event.eventType, role, event.className ?: "غير معروف")
    }

    private fun formatStep(key: Triple<String, String, String>): String {
        return "${key.first} → ${key.second} (${key.third})"
    }

    /**
     * حساب درجة الثقة من مؤشرات فعلية، وليس من نسبة واحدة.
     *
     * المؤشرات:
     * - نسبة الخطوات المشتركة إلى إجمالي المواقع
     * - عدد التكرارات (أكثر تكرار = ثقة أعلى)
     * - غياب الخطوات الغامضة والاستثناءات
     * - وجود خطوات اختيارية يُقلل الثقة قليلاً (تعددية المسارات)
     */
    private fun calculateConfidence(
        commonSteps: Int,
        optionalSteps: Int,
        ambiguousSteps: Int,
        exceptionCount: Int,
        iterationCount: Int,
        totalPositions: Int,
    ): Double {
        if (totalPositions == 0) return 0.0

        // 기초: نسبة الخطوات المشتركة
        val commonRatio = commonSteps / totalPositions.toDouble()

        // مكافأة لعدد التكرارات (تصل إلى حد أقصى عند 5 تكرارات)
        val iterationBonus = minOf(iterationCount, 5) / 5.0 * 0.2

        // عقوبة للخطوات الغامضة والاستثناءات
        val ambiguityPenalty = (ambiguousSteps + exceptionCount) / totalPositions.toDouble() * 0.3

        // عقوبة خفيفة للخطوات الاختيارية (تعددية المسارات غير مؤكدة)
        val optionalPenalty = optionalSteps / totalPositions.toDouble() * 0.1

        var confidence = commonRatio * 0.5 + iterationBonus - ambiguityPenalty - optionalPenalty
        confidence = coerceIn(confidence, 0.0, 1.0)

        return confidence
    }

    private fun buildPlanDescription(
        commonSteps: List<String>,
        optionalSteps: List<String>,
        ambiguousSteps: List<String>,
        exceptions: List<String>,
        iterationCount: Int
    ): String {
        val sb = StringBuilder()
        sb.append("تعلّم من $iterationCount تكرار.\n")
        sb.append("الخطوات المشتركة (${commonSteps.size}):\n")
        commonSteps.forEachIndexed { index, step ->
            sb.append("  ${index + 1}. $step\n")
        }
        if (optionalSteps.isNotEmpty()) {
            sb.append("الخطوات الاختيارية (${optionalSteps.size}):\n")
            optionalSteps.forEachIndexed { index, step ->
                sb.append("  ${index + 1}. $step (اختيارية)\n")
            }
        }
        if (ambiguousSteps.isNotEmpty()) {
            sb.append("الخطوات الغامضة (${ambiguousSteps.size}):\n")
            ambiguousSteps.forEach { sb.append("  - $it\n") }
        }
        if (exceptions.isNotEmpty()) {
            sb.append("الاستثناءات:\n")
            exceptions.forEach { sb.append("  - $it\n") }
        }
        return sb.toString()
    }

    /**
     * تحويل نتيجة التعلم إلى نتيجة تحقق أولية للعرض على المستخدم.
     * لا تُعتمد المهارة؛ تُرجع فقط أسباب التقييم.
     */
    fun evaluateSkillReadiness(skillId: String, db: TaskDatabaseHelper): SkillValidationResult {
        // يتم استدعاء SkillValidator من außerhalb مع البيانات الفعلية
        return SkillValidationResult(
            valid = false,
            reasons = listOf("المهارة بحاجة للتحقق اليدوي قبل الاعتماد"),
            confidenceScore = 0.0,
        )
    }
}
