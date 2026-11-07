package com.phonepilot.app.data.ai

import com.phonepilot.app.models.SkillEntity
import com.phonepilot.app.models.SkillLifecycleState
import com.phonepilot.app.models.SkillValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillValidatorTest {

    private val validator = SkillValidator()

    @Test
    fun `skill with enough common steps and no ambiguity is valid`() {
        val skill = SkillEntity(id = "1", title = "test", description = "desc", level = "أساسي")
        val result = validator.validate(skill, stepCount = 5, commonStepCount = 3, ambiguousStepCount = 0)

        assertTrue(result.valid)
        assertTrue(result.confidenceScore > 0.0)
        assertTrue(result.ambiguousSteps.isEmpty())
    }

    @Test
    fun `skill with too few common steps is invalid`() {
        val skill = SkillEntity(id = "1", title = "test", description = "desc", level = "أساسي")
        val result = validator.validate(skill, stepCount = 5, commonStepCount = 1, ambiguousStepCount = 0)

        assertFalse(result.valid)
        assertTrue(result.reasons.any { it.contains("أقل من الحد الأدنى") })
    }

    @Test
    fun `skill with ambiguous steps is invalid`() {
        val skill = SkillEntity(id = "1", title = "test", description = "desc", level = "أساسي")
        val result = validator.validate(skill, stepCount = 5, commonStepCount = 3, ambiguousStepCount = 2)

        assertFalse(result.valid)
        assertTrue(result.reasons.any { it.contains("غامضة") })
        assertEquals(2, result.ambiguousSteps.size)
    }

    @Test
    fun `canPromoteToReady requires valid and no ambiguity and confidence`() {
        val validResult = SkillValidationResult(valid = true, ambiguousSteps = emptyList(), confidenceScore = 0.8)
        assertTrue(validator.canPromoteToReady(validResult))

        val invalidResult = SkillValidationResult(valid = false, ambiguousSteps = emptyList(), confidenceScore = 0.0)
        assertFalse(validator.canPromoteToReady(invalidResult))

        val ambiguousResult = SkillValidationResult(valid = true, ambiguousSteps = listOf(1), confidenceScore = 0.8)
        assertFalse(validator.canPromoteToReady(ambiguousResult))

        val zeroConfidence = SkillValidationResult(valid = true, ambiguousSteps = emptyList(), confidenceScore = 0.0)
        assertFalse(validator.canPromoteToReady(zeroConfidence))
    }

    @Test
    fun `nextLifecycleState transitions correctly`() {
        val validator = SkillValidator()

        val validResult = SkillValidationResult(valid = true, ambiguousSteps = emptyList(), confidenceScore = 0.8)
        val invalidResult = SkillValidationResult(valid = false, ambiguousSteps = emptyList(), confidenceScore = 0.0)

        assertEquals(SkillLifecycleState.LEARNING, validator.nextLifecycleState(SkillLifecycleState.DRAFT, validResult))
        assertEquals(SkillLifecycleState.ANALYZING, validator.nextLifecycleState(SkillLifecycleState.LEARNING, validResult))
        assertEquals(SkillLifecycleState.NEEDS_REVIEW, validator.nextLifecycleState(SkillLifecycleState.ANALYZING, validResult))
        assertEquals(SkillLifecycleState.VALIDATING, validator.nextLifecycleState(SkillLifecycleState.NEEDS_REVIEW, validResult))
        assertEquals(SkillLifecycleState.READY, validator.nextLifecycleState(SkillLifecycleState.VALIDATING, validResult))
        assertEquals(SkillLifecycleState.FAILED, validator.nextLifecycleState(SkillLifecycleState.ANALYZING, invalidResult))
        assertEquals(SkillLifecycleState.FAILED, validator.nextLifecycleState(SkillLifecycleState.NEEDS_REVIEW, invalidResult))
        assertEquals(SkillLifecycleState.FAILED, validator.nextLifecycleState(SkillLifecycleState.VALIDATING, invalidResult))
        assertEquals(SkillLifecycleState.READY, validator.nextLifecycleState(SkillLifecycleState.READY, validResult))
        assertEquals(SkillLifecycleState.FAILED, validator.nextLifecycleState(SkillLifecycleState.FAILED, validResult))
        assertEquals(SkillLifecycleState.ARCHIVED, validator.nextLifecycleState(SkillLifecycleState.ARCHIVED, validResult))
    }

    @Test
    fun `nextLifecycleState does not promote to READY without user approval`() {
        val validator = SkillValidator()
        val validResult = SkillValidationResult(valid = true, ambiguousSteps = emptyList(), confidenceScore = 0.9)

        // بدون موافقة المستخدم: لا تُترقى إلى READY حتى لو كان التحقق ناجحاً
        assertEquals(SkillLifecycleState.FAILED, validator.nextLifecycleState(SkillLifecycleState.VALIDATING, validResult, userApproved = false))

        // بموافقة المستخدم: تُترقى إلى READY
        assertEquals(SkillLifecycleState.READY, validator.nextLifecycleState(SkillLifecycleState.VALIDATING, validResult, userApproved = true))
    }

    @Test
    fun `nextLifecycleState does not promote to READY when validation fails even with user approval`() {
        val validator = SkillValidator()
        val invalidResult = SkillValidationResult(valid = false, ambiguousSteps = emptyList(), confidenceScore = 0.0)

        // حتى مع موافقة المستخدم، التحقق الفاشل يمنع READY
        assertEquals(SkillLifecycleState.FAILED, validator.nextLifecycleState(SkillLifecycleState.VALIDATING, invalidResult, userApproved = true))
    }
}
