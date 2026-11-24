package com.phonepilot.app.bot

/**
 * مطابقة إشعار وارد مع بوتات المحفّز "عند إشعار". المطابقة تستخدم اسم الحزمة وعنوان الإشعار فقط،
 * ولا يُخزَّن نص الإشعار ولا يُسجَّل.
 */
object BotNotifications {
    fun matches(def: BotDefinition, enabled: Boolean, packageName: String, title: String?): Boolean {
        val t = def.trigger as? BotTrigger.NotificationFrom ?: return false
        if (!enabled) return false
        if (t.packageName != packageName) return false
        val needle = t.titleContains?.takeIf { it.isNotBlank() } ?: return true
        return title?.contains(needle, ignoreCase = true) == true
    }

    /** البوتات المفعّلة التي يطابقها هذا الإشعار. */
    fun matching(store: BotStore, packageName: String, title: String?): List<BotDefinition> =
        store.list().filter { matches(it, store.state(it.id).enabled, packageName, title) }
}
