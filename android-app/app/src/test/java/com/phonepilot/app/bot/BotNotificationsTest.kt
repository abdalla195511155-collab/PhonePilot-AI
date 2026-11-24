package com.phonepilot.app.bot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BotNotificationsTest {
    private fun bot(id: String, pkg: String, title: String? = null) = BotDefinition(
        id = id, name = id, trigger = BotTrigger.NotificationFrom(pkg, title),
        steps = listOf(BotStep(BotAction.OpenApp(BotTemplates.WHATSAPP))),
    )

    @Test fun matchesPackageOnly() {
        assertTrue(BotNotifications.matches(bot("a", "com.whatsapp"), true, "com.whatsapp", "أي عنوان"))
        assertTrue(BotNotifications.matches(bot("a", "com.whatsapp"), true, "com.whatsapp", null))
    }

    @Test fun otherPackageDoesNotMatch() {
        assertFalse(BotNotifications.matches(bot("a", "com.whatsapp"), true, "com.other", "x"))
    }

    @Test fun disabledBotDoesNotMatch() {
        assertFalse(BotNotifications.matches(bot("a", "com.whatsapp"), false, "com.whatsapp", "x"))
    }

    @Test fun titleFilterIsCaseInsensitiveAndNeedsTitle() {
        val b = bot("a", "com.whatsapp", "فاتورة")
        assertTrue(BotNotifications.matches(b, true, "com.whatsapp", "مجموعة المحاسبة: فاتورة جديدة"))
        assertFalse(BotNotifications.matches(b, true, "com.whatsapp", "مرحبا"))
        assertFalse(BotNotifications.matches(b, true, "com.whatsapp", null))
        assertTrue(BotNotifications.matches(bot("c", "p.q", "Invoice"), true, "p.q", "new INVOICE"))
    }

    @Test fun nonNotificationTriggersNeverMatch() {
        val manual = BotTemplates.blankManual("m")
        assertFalse(BotNotifications.matches(manual, true, "com.whatsapp", "x"))
    }

    @Test fun matchingUsesStoreEnabledState() {
        val store = InMemoryBotStore()
        store.save(bot("on", "com.whatsapp")); store.save(bot("off", "com.whatsapp")); store.save(bot("other", "com.x"))
        store.setState("on", store.state("on").copy(enabled = true))
        assertEquals(listOf("on"), BotNotifications.matching(store, "com.whatsapp", "t").map { it.id })
    }
}
