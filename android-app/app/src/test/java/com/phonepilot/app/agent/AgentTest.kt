package com.phonepilot.app.agent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.phonepilot.app.database.TaskDatabaseHelper
import com.phonepilot.app.models.ExecutionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

class AgentModelsTest {

    @Test
    fun `agentExecutionStateEnumHasExpectedValues`() {
        assertEquals(8, AgentExecutionState.values().size)
        assertTrue(AgentExecutionState.IDLE.name == "IDLE")
        assertTrue(AgentExecutionState.EXECUTING.name == "EXECUTING")
        assertTrue(AgentExecutionState.COMPLETED.name == "COMPLETED")
        assertTrue(AgentExecutionState.FAILED.name == "FAILED")
        assertTrue(AgentExecutionState.CANCELLED.name == "CANCELLED")
        assertTrue(AgentExecutionState.WAITING_FOR_APPROVAL.name == "WAITING_FOR_APPROVAL")
    }

    @Test
    fun `interpretedIntentCreation`() {
        val intent = InterpretedIntent(
            rawCommand = "افتح واتساب",
            intent = "open_app",
            targetApp = "واتساب",
            targetAction = null,
            parameters = emptyMap(),
            confidence = 0.9f,
            missingInfo = emptyList(),
            requiresClarification = false,
        )
        assertEquals("افتح واتساب", intent.rawCommand)
        assertEquals("open_app", intent.intent)
        assertEquals("واتساب", intent.targetApp)
        assertFalse(intent.requiresClarification)
    }

    @Test
    fun `taskPlanCreation`() {
        val intent = InterpretedIntent(
            rawCommand = "نفذ المهمة",
            intent = "execute_skill",
            targetApp = null,
            targetAction = null,
            parameters = emptyMap(),
            confidence = 0.8f,
            missingInfo = emptyList(),
            requiresClarification = false,
        )
        val plan = TaskPlan(
            intent = intent,
            steps = listOf("اختيار المهارة", "التحقق من الموافقة", "التنفيذ"),
            requiredSkills = listOf("skill_execution_engine"),
            estimatedDurationMs = 5000,
            requiresUserApproval = true,
            sensitiveOperations = listOf("message_sending"),
        )
        assertEquals(3, plan.steps.size)
        assertTrue(plan.requiresUserApproval)
        assertEquals(1, plan.sensitiveOperations.size)
    }

    @Test
    fun `skillSelectionResultCreation`() {
        val skill = SkillEntity(
            id = "1",
            title = "Test Skill",
            description = "Test description",
            level = "أساسي",
            lifecycleState = SkillLifecycleState.READY,
            approvalState = "APPROVED",
            approvedByUser = true,
        )
        val selection = SkillSelectionResult(
            selectedSkill = skill,
            selectionReason = "متطابقة مع الهدف",
            confidence = 0.9f,
            alternatives = emptyList(),
        )
        assertNotNull(selection.selectedSkill)
        assertEquals("test skill".lowercase(), selection.selectedSkill!!.title.lowercase())
        assertEquals(0.9f, selection.confidence, 0.001f)
    }

    @Test
    fun `agentMemoryEntryCreation`() {
        val entry = AgentMemoryEntry(
            taskId = "task-1",
            intent = "execute_skill",
            skillId = "skill-1",
            executionState = ExecutionState.COMPLETED,
            completedAt = System.currentTimeMillis(),
            success = true,
            notes = "تم التنفيذ بنجاح",
        )
        assertEquals("task-1", entry.taskId)
        assertEquals("execute_skill", entry.intent)
        assertEquals("skill-1", entry.skillId)
        assertEquals(ExecutionState.COMPLETED, entry.executionState)
        assertTrue(entry.success)
        assertEquals("تم التنفيذ بنجاح", entry.notes)
    }

    @Test
    fun `userCommandCreation`() {
        val command = UserCommand(
            commandId = "cmd-1",
            rawText = "افتح التطبيق",
            source = "telegram",
            userId = "user-1",
        )
        assertEquals("cmd-1", command.commandId)
        assertEquals("افتح التطبيق", command.rawText)
        assertEquals("telegram", command.source)
        assertEquals("user-1", command.userId)
    }

    @Test
    fun `agentCommandResultCreation`() {
        val result = AgentCommandResult(
            commandId = "cmd-1",
            success = true,
            message = "تم التنفيذ",
            executionId = 1L,
            requiresUserAction = false,
        )
        assertTrue(result.success)
        assertEquals("تم التنفيذ", result.message)
        assertEquals(1L, result.executionId)
        assertFalse(result.requiresUserAction)
    }

    @Test
    fun `pendingApprovalCreation`() {
        val approval = PendingApproval(
            commandId = "cmd-1",
            skillId = "skill-1",
            skillTitle = "Test Skill",
            intent = "execute_skill",
        )
        assertEquals("cmd-1", approval.commandId)
        assertEquals("skill-1", approval.skillId)
        assertEquals("Test Skill", approval.skillTitle)
        assertEquals("execute_skill", approval.intent)
    }
}

class IntentInterpreterTest {

    private val interpreter = IntentInterpreter()

    @Test
    fun `interpretRecordTask`() {
        val intent = interpreter.interpret("سجل مهمة جديدة")
        assertEquals("record_task", intent.intent)
    }

    @Test
    fun `interpretExecuteSkill`() {
        val intent = interpreter.interpret("نفذ المهارة")
        assertEquals("execute_skill", intent.intent)
    }

    @Test
    fun `interpretOpenApp`() {
        val intent = interpreter.interpret("افتح تطبيق واتساب")
        assertEquals("open_app", intent.intent)
        assertEquals("واتساب", intent.targetApp)
    }

    @Test
    fun `interpretUnknown`() {
        val intent = interpreter.interpret("something unknown")
        assertEquals("unknown", intent.intent)
        assertTrue(intent.requiresClarification)
    }

    @Test
    fun `interpretSendMessage`() {
        val intent = interpreter.interpret("أرسل رسالة")
        assertEquals("send_message", intent.intent)
    }

    @Test
    fun `interpretDeleteData`() {
        val intent = interpreter.interpret("احذف البيانات")
        assertEquals("delete_data", intent.intent)
    }
}

class TaskPlannerTest {

    private val planner = TaskPlanner()

    @Test
    fun `planRecordTaskReturnsSteps`() {
        val intent = InterpretedIntent(
            rawCommand = "سجل مهمة",
            intent = "record_task",
            targetApp = null,
            targetAction = null,
            parameters = emptyMap(),
            confidence = 0.8f,
            missingInfo = emptyList(),
            requiresClarification = false,
        )
        val plan = planner.plan(intent)
        assertTrue(plan.steps.isNotEmpty())
        assertFalse(plan.requiresUserApproval)
    }

    @Test
    fun `planExecuteSkillRequiresApproval`() {
        val intent = InterpretedIntent(
            rawCommand = "نفذ المهارة",
            intent = "execute_skill",
            targetApp = null,
            targetAction = null,
            parameters = emptyMap(),
            confidence = 0.8f,
            missingInfo = emptyList(),
            requiresClarification = false,
        )
        val plan = planner.plan(intent)
        assertTrue(plan.requiresUserApproval)
        assertTrue(plan.sensitiveOperations.isNotEmpty())
    }
}

class SkillSelectorTest {

    private val selector = SkillSelector()

    @Test
    fun `selectSkillReturnsNullWhenNoReadySkills`() {
        // SkillRepository.skills starts empty, so no skills available
        val intent = InterpretedIntent(
            rawCommand = "نفذ",
            intent = "execute_skill",
            targetApp = null,
            targetAction = null,
            parameters = emptyMap(),
            confidence = 0.8f,
            missingInfo = emptyList(),
            requiresClarification = false,
        )
        val result = selector.selectSkill(intent)
        assertNull(result.selectedSkill)
        assertFalse(result.selectionReason.isEmpty())
    }

    @Test
    fun `isSkillExecutableReturnsFalseForNonReady`() {
        val skill = SkillEntity(
            id = "1",
            title = "Test",
            description = "Test",
            level = "أساسي",
            lifecycleState = SkillLifecycleState.DRAFT,
            approvalState = "PENDING",
            approvedByUser = false,
        )
        assertFalse(selector.isSkillExecutable(skill))
    }

    @Test
    fun `isSkillExecutableReturnsTrueForReadyApproved`() {
        val skill = SkillEntity(
            id = "1",
            title = "Test",
            description = "Test",
            level = "أساسي",
            lifecycleState = SkillLifecycleState.READY,
            approvalState = "APPROVED",
            approvedByUser = true,
        )
        assertTrue(selector.isSkillExecutable(skill))
    }
}

@RunWith(RobolectricTestRunner::class)
class AgentMemoryTest {

    @Test
    fun `agentMemoryRecordsAndRetrieves`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        val database = TaskDatabaseHelper.getInstance(context)
        val memory = AgentMemory(scope, database)

        memory.recordTask(
            taskId = "task-1",
            intent = "execute_skill",
            skillId = "skill-1",
            executionState = ExecutionState.COMPLETED,
            completedAt = System.currentTimeMillis(),
            success = true,
        )

        val taskMemory = memory.getTaskMemory("task-1")
        assertEquals(1, taskMemory.size)
        assertEquals("execute_skill", taskMemory[0].intent)

        val recent = memory.getRecentMemory(5)
        assertEquals(1, recent.size)

        memory.clearTaskMemory("task-1")
        val cleared = memory.getTaskMemory("task-1")
        assertTrue(cleared.isEmpty())
    }
}

class CommandRouterTest {

    @Test
    fun `commandRouterDetectsDuplicates`() = kotlinx.coroutines.runBlocking {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        val router = CommandRouter(scope)

        val command = UserCommand(
            commandId = "cmd-1",
            rawText = "test",
            source = "test",
            userId = "user-1",
        )

        assertTrue(router.registerCommand(command))
        assertFalse(router.registerCommand(command)) // مكرر

        router.markCommandExecuted("cmd-1")
        assertTrue(router.executedCommands.value.contains("cmd-1"))
    }

    @Test
    fun `commandRouterCancellation`() = kotlinx.coroutines.runBlocking {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        val router = CommandRouter(scope)

        val command = UserCommand(
            commandId = "cmd-1",
            rawText = "test",
            source = "test",
            userId = "user-1",
        )
        router.registerCommand(command)
        assertTrue(router.isCancellable("cmd-1"))
        assertTrue(router.cancelCommand("cmd-1"))
        assertFalse(router.isCancellable("cmd-1"))
    }
}

class ModelSelectorTest {

    private val selector = ModelSelector()

    @Test
    fun `selectModelReturnsNullWhenNoProvider`() {
        // No provider is configured in AiProviderRepository by default
        val result = selector.selectModel(requireVision = false, requireReasoning = false)
        assertNull(result)
    }

    @Test
    fun `isVisionCapableReturnsFalseForTextOnlyModel`() {
        val model = com.phonepilot.app.data.ai.AiModelSpec(
            id = "text-model",
            displayName = "Text Model",
            capabilities = emptyList(),
        )
        assertFalse(selector.isVisionCapable(model))
    }

    @Test
    fun `isVisionCapableReturnsTrueForVisionModel`() {
        val model = com.phonepilot.app.data.ai.AiModelSpec(
            id = "vision-model",
            displayName = "Vision Model",
            capabilities = listOf(
                com.phonepilot.app.data.ai.CapabilityEvidence(
                    capability = com.phonepilot.app.data.ai.ModelCapability.VISION,
                    sourceUrl = "https://example.com",
                    verifiedAtEpochMs = System.currentTimeMillis(),
                ),
            ),
        )
        assertTrue(selector.isVisionCapable(model))
    }
}

@RunWith(RobolectricTestRunner::class)
class BotTokenValidatorTest {

    @Test
    fun `validateFormatReturnsInvalidForNull`() {
        val result = TelegramTokenValidator.validateFormat(null)
        assertFalse(result.isValid)
    }

    @Test
    fun `validateFormatReturnsInvalidForEmpty`() {
        val result = TelegramTokenValidator.validateFormat("")
        assertFalse(result.isValid)
    }

    @Test
    fun `validateFormatReturnsInvalidForWrongFormat`() {
        val result = TelegramTokenValidator.validateFormat("invalid")
        assertFalse(result.isValid)
    }

    @Test
    fun `validateFormatReturnsValidForCorrectFormat`() {
        val result = TelegramTokenValidator.validateFormat("123456789:ABCdefGHIjklMNOpqrsTUVwxyz")
        assertTrue(result.isValid)
    }

    @Test
    fun `telegramBotChannelRejectsUnauthorizedUser`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = TaskDatabaseHelper.getInstance(context)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        val orchestrator = AgentOrchestrator(
            context = context,
            taskDatabaseHelper = database,
            accessibilityService = null,
            scope = scope,
        )
        val channel = TelegramBotChannel(
            botToken = "test-token",
            authorizedUserIds = setOf("authorized-user"),
            orchestrator = orchestrator,
            scope = scope,
        )
        assertFalse(channel.isAuthorizedUser(null))
        assertFalse(channel.isAuthorizedUser("unauthorized-user"))
        assertTrue(channel.isAuthorizedUser("authorized-user"))
    }
}
