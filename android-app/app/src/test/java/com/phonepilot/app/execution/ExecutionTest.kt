package com.phonepilot.app.execution

import com.phonepilot.app.models.ExecutionState
import com.phonepilot.app.models.SkillEntity
import com.phonepilot.app.models.SkillLifecycleState
import com.phonepilot.app.models.SkillValidationResult
import com.phonepilot.app.models.StepExecutionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionPlannerTest {

    private val planner = ExecutionPlanner()

    @Test
    fun `planExecution returns null for non-READY skill`() {
        val skill = SkillEntity(
            id = "1",
            title = "Test",
            description = "Click button",
            level = "أساسي",
            lifecycleState = SkillLifecycleState.DRAFT,
        )
        val result = SkillValidationResult(valid = true, ambiguousSteps = emptyList(), confidenceScore = 0.8)

        assertNull(planner.planExecution(skill, result))
    }

    @Test
    fun `planExecution returns null for unapproved skill`() {
        val skill = SkillEntity(
            id = "1",
            title = "Test",
            description = "Click button",
            level = "أساسي",
            lifecycleState = SkillLifecycleState.READY,
            approvalState = "PENDING",
            approvedByUser = false,
        )
        val result = SkillValidationResult(valid = true, ambiguousSteps = emptyList(), confidenceScore = 0.8)

        assertNull(planner.planExecution(skill, result))
    }

    @Test
    fun `planExecution returns null for invalid validation`() {
        val skill = SkillEntity(
            id = "1",
            title = "Test",
            description = "Click button",
            level = "أساسي",
            lifecycleState = SkillLifecycleState.READY,
            approvalState = "APPROVED",
            approvedByUser = true,
        )
        val result = SkillValidationResult(valid = false, ambiguousSteps = emptyList(), confidenceScore = 0.0)

        assertNull(planner.planExecution(skill, result))
    }

    @Test
    fun `planExecution returns plan for approved READY skill`() {
        val skill = SkillEntity(
            id = "1",
            title = "Test Skill",
            description = "انقر على زر تسجيل الدخول\nأدخل بيانات المستخدم",
            level = "أساسي",
            lifecycleState = SkillLifecycleState.READY,
            approvalState = "APPROVED",
            approvedByUser = true,
        )
        val result = SkillValidationResult(valid = true, ambiguousSteps = emptyList(), confidenceScore = 0.8)

        val plan = planner.planExecution(skill, result)

        assertNotNull(plan)
        assertEquals("1", plan!!.skillId)
        assertTrue(plan.steps.isNotEmpty())
    }

    @Test
    fun `requiresConfirmation detects sensitive keywords`() {
        val sensitiveSkill = SkillEntity(
            id = "1",
            title = "Payment",
            description = "انقر على زر الدفع وأكد التحويل",
            level = "أساسي",
            lifecycleState = SkillLifecycleState.READY,
            approvalState = "APPROVED",
            approvedByUser = true,
        )
        val result = SkillValidationResult(valid = true, ambiguousSteps = emptyList(), confidenceScore = 0.8)

        val plan = planner.planExecution(sensitiveSkill, result)
        assertTrue(plan!!.requiresUserConfirmation)

        val normalSkill = SkillEntity(
            id = "2",
            title = "Normal",
            description = "افتح التطبيق وانتقل إلى الشاشة الرئيسية",
            level = "أساسي",
            lifecycleState = SkillLifecycleState.READY,
            approvalState = "APPROVED",
            approvedByUser = true,
        )
        val normalPlan = planner.planExecution(normalSkill, result)
        assertFalse(normalPlan!!.requiresUserConfirmation)
    }
}

class ExecutionVerifierTest {

    private val verifier = ExecutionVerifier()

    @Test
    fun `canProceed returns true for successful verified step`() {
        val result = StepExecutionResult(
            stepNumber = 1,
            stepDescription = "Click button",
            success = true,
            verified = true,
        )
        assertTrue(verifier.canProceed(result))
    }

    @Test
    fun `canProceed returns false for unsuccessful step`() {
        val result = StepExecutionResult(
            stepNumber = 1,
            stepDescription = "Click button",
            success = false,
            verified = false,
        )
        assertFalse(verifier.canProceed(result))
    }

    @Test
    fun `canProceed returns false for unverified step`() {
        val result = StepExecutionResult(
            stepNumber = 1,
            stepDescription = "Click button",
            success = true,
            verified = false,
        )
        assertFalse(verifier.canProceed(result))
    }

    @Test
    fun `requiresRecovery returns true for failed step without recovery`() {
        val result = StepExecutionResult(
            stepNumber = 1,
            stepDescription = "Click button",
            success = false,
            verified = false,
            recoveryAttempted = false,
        )
        assertTrue(verifier.requiresRecovery(result))
    }

    @Test
    fun `requiresRecovery returns false for step with recovery attempted`() {
        val result = StepExecutionResult(
            stepNumber = 1,
            stepDescription = "Click button",
            success = false,
            verified = false,
            recoveryAttempted = true,
        )
        assertFalse(verifier.requiresRecovery(result))
    }

    @Test
    fun `failureMessage returns failure reason`() {
        val result = StepExecutionResult(
            stepNumber = 3,
            stepDescription = "Click submit",
            success = false,
            verified = false,
            failureReason = "Element not found",
        )
        assertEquals("Element not found", verifier.failureMessage(result))
    }
}

class SkillExecutionEngineTest {

    @Test
    fun `executionStateEnumHasExpectedValues`() {
        assertEquals(7, ExecutionState.values().size)
        assertTrue(ExecutionState.IDLE.name == "IDLE")
        assertTrue(ExecutionState.RUNNING.name == "RUNNING")
        assertTrue(ExecutionState.COMPLETED.name == "COMPLETED")
        assertTrue(ExecutionState.FAILED.name == "FAILED")
        assertTrue(ExecutionState.CANCELLED.name == "CANCELLED")
    }

    @Test
    fun `stepExecutionResultDefaults`() {
        val result = StepExecutionResult(
            stepNumber = 1,
            stepDescription = "Test",
            success = true,
        )
        assertEquals(1, result.stepNumber)
        assertEquals("Test", result.stepDescription)
        assertTrue(result.success)
        assertFalse(result.verified)
        assertFalse(result.elementResolved)
        assertFalse(result.recoveryAttempted)
        assertFalse(result.recoverySucceeded)
        assertNull(result.failureReason)
        assertNull(result.elementId)
    }

    @Test
    fun `executionEntityDefaults`() {
        val execution = ExecutionEntity(
            skillId = "skill-1",
            skillVersion = 1,
            taskId = null,
            state = ExecutionState.IDLE.name,
            startedAt = System.currentTimeMillis(),
        )
        assertEquals("skill-1", execution.skillId)
        assertEquals(1, execution.skillVersion)
        assertNull(execution.taskId)
        assertEquals(ExecutionState.IDLE.name, execution.state)
        assertEquals(0, execution.totalSteps)
        assertEquals(0, execution.completedSteps)
    }
}
