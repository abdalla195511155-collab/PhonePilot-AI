package com.phonepilot.app.bot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BotFormTest {
    private fun template() = BotTemplates.scheduledWhatsApp("b1", "جهة", "نص", 9, 0)

    @Test fun triggerKindRoundTrip() {
        TriggerKind.values().forEach { k ->
            assertEquals(k, BotForm.triggerKind(BotForm.defaultTrigger(k)))
        }
    }

    @Test fun sameTriggerKindKeepsValues() {
        val def = template().copy(trigger = BotTrigger.DailyAt(21, 30))
        assertEquals(def, BotForm.withTriggerKind(def, TriggerKind.DAILY))
    }

    @Test fun changingTriggerKindUsesDefault() {
        val def = BotForm.withTriggerKind(template(), TriggerKind.EVERY)
        assertEquals(BotTrigger.EveryMinutes(BotValidator.MIN_INTERVAL_MINUTES), def.trigger)
    }

    @Test fun actionKindRoundTrip() {
        ActionKind.values().forEach { k ->
            assertEquals(k, BotForm.actionKind(BotForm.defaultAction(k)))
        }
    }

    @Test fun newStepsWithTextFieldsAreInvalidUntilFilled() {
        val base = template()
        listOf(ActionKind.OPEN_APP, ActionKind.TYPE_TEXT, ActionKind.CLICK, ActionKind.WAIT_FOR_TEXT).forEach { k ->
            val def = BotForm.addStep(base, k)
            assertEquals(base.steps.size + 1, def.steps.size)
            assertFalse("kind $k", BotValidator.validate(def.copy(unattendedApproved = true)).isEmpty())
        }
    }

    @Test fun addedStepIsNeverExternalByDefault() {
        val def = BotForm.addStep(template(), ActionKind.BACK)
        assertFalse(def.steps.last().externalEffect)
    }

    @Test fun removeAndReplaceAndMove() {
        var def = BotForm.addStep(BotForm.addStep(template().copy(steps = emptyList()), ActionKind.BACK), ActionKind.TAP)
        assertEquals(2, def.steps.size)
        def = BotForm.moveStep(def, 0, 1)
        assertEquals(BotAction.TapAt(0, 0), def.steps[0].action)
        assertEquals(BotAction.Back, def.steps[1].action)
        assertEquals(def, BotForm.moveStep(def, 0, -1))
        assertEquals(def, BotForm.moveStep(def, 1, 1))
        def = BotForm.replaceStep(def, 0, def.steps[0].copy(action = BotAction.TapAt(5, 6)))
        assertEquals(BotAction.TapAt(5, 6), def.steps[0].action)
        def = BotForm.removeStep(def, 0)
        assertEquals(listOf(BotAction.Back), def.steps.map { it.action })
        assertEquals(def, BotForm.removeStep(def, 7))
        assertEquals(def, BotForm.replaceStep(def, -1, BotStep(BotAction.Back)))
    }

    @Test fun stepLimitRespected() {
        var def = template().copy(steps = emptyList())
        repeat(BotValidator.MAX_STEPS + 5) { def = BotForm.addStep(def, ActionKind.BACK) }
        assertEquals(BotValidator.MAX_STEPS, def.steps.size)
    }

    @Test fun conditionsAddReplaceRemove() {
        var def = BotForm.addCondition(template(), true)
        def = BotForm.addCondition(def, false)
        assertEquals(2, def.conditions.size)
        assertFalse(BotValidator.validate(def.copy(unattendedApproved = true)).isEmpty())
        def = BotForm.replaceCondition(def, 0, BotCondition.ScreenContainsText("مرحبا"))
        def = BotForm.replaceCondition(def, 1, BotCondition.ForegroundPackageIs("com.whatsapp"))
        assertTrue(BotValidator.validate(def.copy(unattendedApproved = true)).isEmpty())
        def = BotForm.removeCondition(def, 0)
        assertEquals(listOf<BotCondition>(BotCondition.ForegroundPackageIs("com.whatsapp")), def.conditions)
    }

    @Test fun digitsToInt() {
        assertEquals(0, BotForm.digitsToInt(""))
        assertEquals(0, BotForm.digitsToInt("abc"))
        assertEquals(12, BotForm.digitsToInt("1a2"))
        assertEquals(5, BotForm.digitsToInt("05"))
        assertEquals(123456, BotForm.digitsToInt("12345678"))
    }

    @Test fun switchingToScheduledNeverKeepsOldApproval() {
        val manual = template().copy(trigger = BotTrigger.Manual)
        val scheduled = BotForm.withTriggerKind(manual, TriggerKind.DAILY).copy(unattendedApproved = true)
        // لم يلمس المستخدم مربع الموافقة: لا موافقة
        assertFalse(BotApproval.resolve(scheduled, manual, checkbox = false, checkboxTouched = false))
        assertFalse(BotApproval.resolve(scheduled, manual, checkbox = true, checkboxTouched = false))
        assertTrue(BotApproval.resolve(scheduled, manual, checkbox = true, checkboxTouched = true))
    }

    @Test fun formEditedDefinitionSurvivesJsonRoundTrip() {
        var def = BotForm.withTriggerKind(template(), TriggerKind.NOTIFICATION)
        def = def.copy(trigger = BotTrigger.NotificationFrom("com.whatsapp", "عنوان"))
        def = BotForm.addStep(def, ActionKind.SWIPE)
        def = BotForm.addCondition(def, true)
        assertEquals(def, BotJson.parse(BotJson.toPrettyJson(def)))
    }
}
