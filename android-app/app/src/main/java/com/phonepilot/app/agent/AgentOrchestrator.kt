package com.phonepilot.app.agent

import com.phonepilot.app.data.SkillRepository
import com.phonepilot.app.data.ai.AiSkillExtractor
import com.phonepilot.app.data.ai.LearningEngine
import com.phonepilot.app.database.TaskDatabaseHelper
import com.phonepilot.app.execution.SkillExecutionEngine
import com.phonepilot.app.models.ExecutionState
import com.phonepilot.app.models.SkillEntity
import com.phonepilot.app.models.SkillLifecycleState
import com.phonepilot.app.models.SkillValidationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * منسق الوكيل الرئيسي (Phase 7 — Agent Orchestration).
 *
 * ينسق جميع مكونات الوكيل:
 * - IntentInterpreter: تفسير الأوامر
 * - TaskPlanner: التخطيط للمهام
 * - SkillSelector: اختيار المهارات
 * - ModelSelector: اختيار النماذج
 * - AgentMemory: ذاكرة الوكيل
 * - CommandRouter: توجيه الأوامر
 * - SkillExecutionEngine: تنفيذ المهارات
 *
 * لا يرتبط بأي قناة محددة (تيليغرام، واجهة أندرويد، إلخ).
 * 모든 القنوات تمر عبر CommandRouter.
 */
class AgentOrchestrator(
    private val context: android.content.Context,
    private val taskDatabaseHelper: TaskDatabaseHelper,
    private val accessibilityService: android.accessibilityservice.AccessibilityService?,
    private val scope: CoroutineScope,
    private val skillRepository: SkillRepository = SkillRepository,
    private val intentInterpreter: IntentInterpreter = IntentInterpreter(),
    private val taskPlanner: TaskPlanner = TaskPlanner(skillRepository),
    private val skillSelector: SkillSelector = SkillSelector(skillRepository),
    private val modelSelector: ModelSelector = ModelSelector(),
    private val agentMemory: AgentMemory = AgentMemory(scope, taskDatabaseHelper),
    private val commandRouter: CommandRouter = CommandRouter(scope),
    private val agentRepository: AgentRepository = AgentRepository(taskDatabaseHelper, scope),
    private val skillExecutionEngine: SkillExecutionEngine = SkillExecutionEngine(
        context = context,
        accessibilityService = accessibilityService,
        taskDatabaseHelper = taskDatabaseHelper,
        coroutineScope = scope,
    ),
    private val aiSkillExtractor: AiSkillExtractor = AiSkillExtractor(),
    private val learningEngine: LearningEngine = LearningEngine(taskDatabaseHelper),
) {

    /**
     * يعالج أمر مستخدم من أي قناة.
     *
     * التدفق الكامل:
     * 1. تسجيل الأمر في CommandRouter (التحقق من التكرار)
     * 2. تفسير النية (IntentInterpreter)
     * 3. تخطيط المهمة (TaskPlanner)
     * 4. اختيار المهارة (SkillSelector)
     * 5. طلب الموافقة إذا لزم الأمر
     * 6. تنفيذ المهارة (SkillExecutionEngine)
     * 7. حفظ النتيجة في الذاكرة والمستودع
     *
     * يعيد AgentCommandResult يحتوي على نتيجة المعالجة.
     */
    suspend fun processCommand(command: UserCommand): AgentCommandResult {
        // 1. التحقق من التكرار
        if (!commandRouter.registerCommand(command)) {
            return AgentCommandResult(
                commandId = command.commandId,
                success = false,
                message = "الأمر مكرر أو قيد المعالجة بالفعل",
            )
        }

        try {
            // تحديث حالة الوكيل
            agentRepository.updateAgentState(AgentExecutionState.INTERPRETING_INTENT)

            // 2. تفسير النية
            val intent = intentInterpreter.interpret(command.rawText)

            if (intent.requiresClarification) {
                agentRepository.updateAgentState(AgentExecutionState.IDLE)
                return AgentCommandResult(
                    commandId = command.commandId,
                    success = false,
                    message = "يحتاج الأمر إلى توضيح: ${intent.missingInfo.joinToString(", ")}",
                    requiresUserAction = true,
                    userActionType = "provide_info",
                    data = mapOf("missing_info" to intent.missingInfo),
                )
            }

            // 3. تخطيط المهمة
            agentRepository.updateAgentState(AgentExecutionState.PLANNING_TASK)
            val plan = taskPlanner.plan(intent)

            // 4. اختيار المهارة
            agentRepository.updateAgentState(AgentExecutionState.SELECTING_SKILL)
            val selection = skillSelector.selectSkill(intent)

            if (selection.selectedSkill == null) {
                agentRepository.updateAgentState(AgentExecutionState.IDLE)
                return AgentCommandResult(
                    commandId = command.commandId,
                    success = false,
                    message = selection.selectionReason,
                )
            }

            val skill = selection.selectedSkill!!

            // 5. التحقق من الموافقة إذا لزم الأمر
            if (plan.requiresUserApproval && !skill.approvedByUser) {
                agentRepository.updateAgentState(AgentExecutionState.WAITING_FOR_APPROVAL)
                return AgentCommandResult(
                    commandId = command.commandId,
                    success = false,
                    message = "المهارة ${skill.title} تتطلب موافقة المستخدم قبل التنفيذ",
                    requiresUserAction = true,
                    userActionType = "approve",
                    data = mapOf("skill_id" to skill.id, "skill_title" to skill.title),
                )
            }

            // 6. التحقق من المهارة باستخدام البيانات الفعلية من قاعدة البيانات (ليست محاكاة)
            agentRepository.updateAgentState(AgentExecutionState.EXECUTING)

            val validationResult = SkillValidationResult(
                valid = skill.lifecycleState == SkillLifecycleState.READY
                    && skill.approvalState == "APPROVED"
                    && skill.approvedByUser == true,
                ambiguousSteps = emptyList(),
                confidenceScore = if (skill.approvedByUser) 1.0 else 0.0,
            )

            if (!validationResult.valid) {
                agentRepository.updateAgentState(AgentExecutionState.IDLE)
                return AgentCommandResult(
                    commandId = command.commandId,
                    success = false,
                    message = "المهارة ${skill.title} غير جاهزة للتنفيذ (غير READY أو غير معتمدة)",
                )
            }

            val execution = skillExecutionEngine.executeSkill(
                skill = skill,
                validationResult = validationResult,
                userApproved = skill.approvedByUser,
            )

            // 7. حفظ النتيجة في الذاكرة الدائمة (SQLite) — لا تُفقد عند إعادة التشغيل
            val success = execution.state == ExecutionState.COMPLETED.name
            agentRepository.saveMemoryEntry(
                AgentMemoryEntry(
                    taskId = command.commandId,
                    intent = intent.intent,
                    skillId = skill.id,
                    executionState = ExecutionState.valueOf(execution.state),
                    completedAt = execution.completedAt,
                    success = success,
                    notes = execution.failureReason,
                )
            )
            // أيضًا في ذاكرة التشغيل الفورية للشاشة
            agentMemory.recordTask(
                taskId = command.commandId,
                intent = intent.intent,
                skillId = skill.id,
                executionState = ExecutionState.valueOf(execution.state),
                completedAt = execution.completedAt,
                success = success,
                notes = execution.failureReason,
            )

            commandRouter.markCommandExecuted(command.commandId)
            val finalAgentState = if (success) AgentExecutionState.COMPLETED else AgentExecutionState.FAILED
            agentRepository.updateAgentState(finalAgentState)

            return AgentCommandResult(
                commandId = command.commandId,
                success = success,
                message = if (success) "تم تنفيذ المهارة ${skill.title} بنجاح" else "فشل تنفيذ المهارة: ${execution.failureReason}",
                executionId = execution.id,
                requiresUserAction = false,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            agentRepository.updateAgentState(AgentExecutionState.FAILED)
            commandRouter.markCommandExecuted(command.commandId)
            return AgentCommandResult(
                commandId = command.commandId,
                success = false,
                message = "خطأ في معالجة الأمر: ${e.message}",
            )
        }
    }

    /**
     * الموافقة على مهارة وتنفيذ الأمر المعلق.
     */
    suspend fun approveSkillAndExecute(
        commandId: String,
        skillId: String,
    ): AgentCommandResult {
        val pendingCommand = commandRouter.getPendingCommand(commandId)
        if (pendingCommand == null) {
            return AgentCommandResult(
                commandId = commandId,
                success = false,
                message = "لا يوجد أمر معلق بهذا المعرف",
            )
        }

        val skill = skillRepository.skills.value.firstOrNull { it.id == skillId }
        if (skill == null) {
            return AgentCommandResult(
                commandId = commandId,
                success = false,
                message = "المهارة غير موجودة",
            )
        }

        // تحديث حالة الموافقة في المستودع
        // في التنفيذ الفعلي، تُستخدم recordSkillApproval من SkillRepository

        return processCommand(pendingCommand.copy(userApproved = true))
    }

    /**
     * إلغاء أمر معلق.
     */
    fun cancelCommand(commandId: String): Boolean {
        return commandRouter.cancelCommand(commandId)
    }

    /**
     * جلب حالة الوكيل الحالية.
     */
    fun getAgentState(): AgentExecutionState {
        return agentRepository.getAgentState()
    }

    /**
     * جلب سجل الذاكرة الأخير.
     */
    fun getRecentMemory(limit: Int = 10): List<AgentMemoryEntry> {
        return agentMemory.getRecentMemory(limit)
    }
}

/**
 * أمر مستخدم مع إمكانية الموافقة (للاستخدام من واجهة الموافقة).
 */
data class PendingApproval(
    val commandId: String,
    val skillId: String,
    val skillTitle: String,
    val intent: String,
)
