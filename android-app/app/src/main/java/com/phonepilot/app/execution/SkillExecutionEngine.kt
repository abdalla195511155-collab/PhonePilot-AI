package com.phonepilot.app.execution

import android.accessibilityservice.AccessibilityService
import com.phonepilot.app.database.TaskDatabaseHelper
import com.phonepilot.app.models.ExecutionEntity
import com.phonepilot.app.models.ExecutionState
import com.phonepilot.app.models.SkillEntity
import com.phonepilot.app.models.SkillValidationResult
import com.phonepilot.app.models.StepExecutionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * محرك تنفيذ المهارات (Phase 6 — Intelligent Execution).
 *
 * ينسق جميع مكونات التنفيذ:
 * - ExecutionPlanner: تخطيط الخطوات
 * - ElementResolver: حل العناصر
 * - StepExecutor: تنفيذ الخطوات
 * - ExecutionVerifier: التحقق من النتائج
 * - RecoveryManager: الاستعادة عند الفشل
 * - ExecutionRepository: التخزين الدائم
 */
class SkillExecutionEngine(
    private val context: android.content.Context,
    private val accessibilityService: AccessibilityService?,
    private val taskDatabaseHelper: TaskDatabaseHelper,
    private val coroutineScope: CoroutineScope,
) {

    private val executionRepository = ExecutionRepository(taskDatabaseHelper)
    private val elementResolver = ElementResolver(
        context = context,
        accessibilityAnalyzer = AccessibilityTreeAnalyzer(context),
        elementRecognizer = UiElementRecognizer(),
    )
    private val stepExecutor = StepExecutor(
        accessibilityService = accessibilityService,
        elementResolver = elementResolver,
        coroutineScope = coroutineScope,
    )
    private val executionVerifier = ExecutionVerifier()
    private val recoveryManager = RecoveryManager(stepExecutor = stepExecutor)

    /** حالة المحرك الحالية. */
    private var currentExecutionId: Long = -1
    private var isExecuting = false

    /** التحقق من أن المحرك لا ينفذ بالفعل. */
    fun isBusy(): Boolean = isExecuting

    /**
     * ينفذ مهارة معتمدة بشكل كامل.
     *
     * التدفق:
     * 1. التخطيط (ExecutionPlanner)
     * 2. لكل خطوة: تنفيذ → تحقق → استعادة (إذا لزم)
     * 3. إنهاء التنفيذ وتسجيل النتيجة
     */
    suspend fun executeSkill(
        skill: SkillEntity,
        validationResult: SkillValidationResult?,
        userApproved: Boolean = false,
    ): ExecutionEntity {
        if (isExecuting) {
            throw IllegalStateException("المحرك ينفذ مهارة بالفعل")
        }

        // التخطيط
        val planner = ExecutionPlanner()
        val plan = planner.planExecution(skill, validationResult)
        if (plan == null) {
            throw IllegalStateException("لا يمكن تنفيذ المهارة: غير READY أو غير معتمدة أو لم يوافق المستخدم")
        }

        // بدء التنفيذ في قاعدة البيانات
        currentExecutionId = executionRepository.startExecution(
            skillId = skill.id,
            skillVersion = plan.skillVersion,
            taskId = null, // TODO: ربط بالمهمة النشطة
        )
        isExecuting = true
        executionRepository.updateState(currentExecutionId, ExecutionState.RUNNING)

        var completedSteps = 0
        var failedSteps = 0
        var skippedSteps = 0
        var failureReason: String? = null
        var recoveryAttempts = 0

        try {
            for (step in plan.steps) {
                if (!isExecuting) break // إلغاء التنفيد

                // تنفيذ الخطوة
                val result = stepExecutor.executeStep(
                    stepNumber = step.stepNumber,
                    stepDescription = step.description,
                    action = step.action,
                    targetText = step.target,
                    targetContentDescription = null,
                    targetViewId = null,
                    targetRole = null,
                    requiresUserConfirmation = plan.requiresUserConfirmation && step.stepNumber == 1,
                    userApproved = userApproved,
                )

                // تسجيل نتيجة الخطوة
                executionRepository.recordStepResult(
                    executionId = currentExecutionId,
                    stepResult = result,
                )

                if (result.success) {
                    completedSteps++
                } else if (step.isOptional) {
                    skippedSteps++
                } else {
                    // محاولة استعادة
                    val recoveryResult = recoveryManager.attemptRecovery(
                        stepNumber = step.stepNumber,
                        stepDescription = step.description,
                        action = step.action,
                        targetText = step.target,
                        targetContentDescription = null,
                        targetViewId = null,
                        targetRole = null,
                        previousFailure = result.failureReason ?: "فشل غير محدد",
                        requiresUserConfirmation = plan.requiresUserConfirmation,
                        userApproved = userApproved,
                    )

                    if (recoveryResult != null && recoveryResult.success) {
                        completedSteps++
                        recoveryAttempts++
                    } else {
                        failedSteps++
                        failureReason = result.failureReason
                        break // التوقف عند فشل خطوة غير اختيارية
                    }
                }
            }

            // تحديد الحالة النهائية
            val finalState = when {
                failedSteps > 0 -> ExecutionState.FAILED
                plan.steps.isNotEmpty() && completedSteps == plan.steps.size -> ExecutionState.COMPLETED
                completedSteps > 0 -> ExecutionState.NEEDS_USER_ACTION // خطوات جزئية تحتاج تدخل المستخدم
                else -> ExecutionState.PENDING // لم تُنفذ أي خطوة
            }

            // إنهاء التنفيذ
            executionRepository.finishExecution(
                executionId = currentExecutionId,
                state = finalState,
                failureReason = failureReason,
                recoveryAttempts = recoveryAttempts,
            )

            // تprogress
            executionRepository.updateProgress(
                executionId = currentExecutionId,
                totalSteps = plan.steps.size,
                completedSteps = completedSteps,
                failedSteps = failedSteps,
                skippedSteps = skippedSteps,
            )

            return executionRepository.getExecution(currentExecutionId)!!
        } finally {
            isExecuting = false
        }
    }

    /** إيقاف التنفيذ الحالي. */
    suspend fun cancelExecution() {
        if (!isExecuting) return
        isExecuting = false
        if (currentExecutionId > 0) {
            executionRepository.finishExecution(
                executionId = currentExecutionId,
                state = ExecutionState.CANCELLED,
            )
        }
    }

    /** جلب آخر تنفيذ. */
    fun getCurrentExecution(): ExecutionEntity? {
        if (currentExecutionId <= 0) return null
        return executionRepository.getExecution(currentExecutionId)
    }

    /** جلب سجل تنفيذ لمهارة معينة. */
    fun getExecutionHistory(skillId: String): List<ExecutionEntity> {
        return executionRepository.getExecutionsForSkill(skillId)
    }

    /** جلب نتائج خطوات تنفيذ معين. */
    fun getStepResults(executionId: Long): List<StepExecutionResult> {
        return executionRepository.getStepResults(executionId)
    }
}
