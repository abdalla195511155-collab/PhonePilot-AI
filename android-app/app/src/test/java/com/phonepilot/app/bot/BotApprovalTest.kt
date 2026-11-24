package com.phonepilot.app.bot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BotApprovalTest {
    private fun template(id: String = "b1") =
        BotTemplates.scheduledWhatsApp(id, "جهة", "نص", 9, 0)

    @Test fun scheduledExternalBotNeedsApproval() {
        assertTrue(BotApproval.needsApproval(template()))
    }

    @Test fun manualBotDoesNotNeedApproval() {
        val manual = template().copy(trigger = BotTrigger.Manual)
        assertFalse(BotApproval.needsApproval(manual))
        assertFalse(BotApproval.resolve(manual, null, checkbox = true, checkboxTouched = true))
    }

    @Test fun jsonFieldAloneNeverGrantsApproval() {
        val edited = template().copy(unattendedApproved = true)
        assertFalse(BotApproval.resolve(edited, null, checkbox = false, checkboxTouched = false))
        assertFalse(BotApproval.resolve(edited, null, checkbox = false, checkboxTouched = true))
    }

    @Test fun explicitCheckboxGrantsApproval() {
        assertTrue(BotApproval.resolve(template(), null, checkbox = true, checkboxTouched = true))
    }

    @Test fun approvalKeptWhenNothingChanged() {
        val saved = template().copy(unattendedApproved = true)
        val edited = saved.copy(name = "اسم جديد")
        assertTrue(BotApproval.resolve(edited, saved, checkbox = true, checkboxTouched = false))
    }

    @Test fun approvalDroppedWhenStepsChangedAndBoxUntouched() {
        val saved = template().copy(unattendedApproved = true)
        val edited = saved.copy(steps = saved.steps.dropLast(1))
        assertFalse(BotApproval.resolve(edited, saved, checkbox = true, checkboxTouched = false))
    }

    @Test fun approvalDroppedWhenTriggerChangedAndBoxUntouched() {
        val saved = template().copy(unattendedApproved = true)
        val edited = saved.copy(trigger = BotTrigger.EveryMinutes(15))
        assertFalse(BotApproval.resolve(edited, saved, checkbox = true, checkboxTouched = false))
    }

    @Test fun reTickingAfterChangeGrants() {
        val saved = template().copy(unattendedApproved = true)
        val edited = saved.copy(trigger = BotTrigger.EveryMinutes(15))
        assertEquals(true, BotApproval.resolve(edited, saved, checkbox = true, checkboxTouched = true))
    }
}
