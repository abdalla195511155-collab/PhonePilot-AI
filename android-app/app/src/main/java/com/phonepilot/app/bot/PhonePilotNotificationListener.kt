package com.phonepilot.app.bot

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * يستمع للإشعارات (بعد أن يمنح المستخدم إذن "الوصول للإشعارات" يدويًا من إعدادات النظام)
 * ويشغّل بوتات محفّز الإشعارات المطابقة عبر WorkManager. لا يقرأ نص الإشعار ولا يخزّنه.
 * التشغيل يمر بنفس حدود البوت: الحد اليومي، وموافقة التشغيل بدون حضور.
 */
class PhonePilotNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val title = n.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val store = PrefsBotStore(applicationContext)
        val wm = WorkManager.getInstance(applicationContext)
        for (def in BotNotifications.matching(store, sbn.packageName, title)) {
            val request = OneTimeWorkRequestBuilder<BotWorker>()
                .setInputData(Data.Builder().putString(BotWorker.KEY_BOT_ID, def.id).build())
                .build()
            // KEEP: إشعارات متتابعة أثناء وجود تشغيل منتظر لنفس البوت لا تضاعف التشغيل.
            wm.enqueueUniqueWork("phonepilot_botnotif_${def.id}", ExistingWorkPolicy.KEEP, request)
        }
    }
}
