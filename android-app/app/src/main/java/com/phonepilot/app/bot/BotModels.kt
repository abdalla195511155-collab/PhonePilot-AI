package com.phonepilot.app.bot

/**
 * تعريف بوت: محفّز + شروط + خطوات + حدود.
 *
 * البوت لا يقرر شيئًا بنفسه أثناء التشغيل. الخطوات ثابتة يكتبها أو يراجعها المستخدم،
 * ويُنفّذها [BotRunner] بشكل حتمي مع تحقق بعد كل خطوة.
 */
sealed class BotTrigger {
    /** يشغّله المستخدم يدويًا. */
    object Manual : BotTrigger()

    /** كل [minutes] دقيقة (الحد الأدنى 15). */
    data class EveryMinutes(val minutes: Int) : BotTrigger()

    /** يوميًا في وقت محلي محدد. */
    data class DailyAt(val hour: Int, val minute: Int) : BotTrigger()

    /** عند وصول إشعار من تطبيق معين (واختياريًا عنوانه يحوي نصًا). */
    data class NotificationFrom(val packageName: String, val titleContains: String? = null) : BotTrigger()
}

sealed class BotCondition {
    data class ForegroundPackageIs(val packageName: String) : BotCondition()
    data class ScreenContainsText(val text: String) : BotCondition()
}

sealed class BotAction {
    data class OpenApp(val packageName: String) : BotAction()
    /** يكتب [text] في الحقل الذي عليه التركيز، أو في الحقل الذي نصه/وصفه [target] إن حُدد. */
    data class TypeText(val text: String, val target: String? = null) : BotAction()
    data class Click(val target: String) : BotAction()
    data class TapAt(val x: Int, val y: Int) : BotAction()
    data class Swipe(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val durationMs: Long = 400L) : BotAction()
    object Back : BotAction()
    data class WaitForText(val text: String, val timeoutMs: Long = 5_000L) : BotAction()
}

/**
 * خطوة واحدة.
 *
 * @param verifyText إن حُدد، يجب أن يظهر هذا النص على الشاشة بعد الخطوة وإلا تُعدّ فاشلة.
 * @param externalEffect الخطوة لها أثر خارجي لا يُلغى (مثل إرسال رسالة). لا تُعاد تلقائيًا أبدًا.
 */
data class BotStep(
    val action: BotAction,
    val verifyText: String? = null,
    val verifyTimeoutMs: Long = 3_000L,
    val externalEffect: Boolean = false,
)

data class BotDefinition(
    val id: String,
    val name: String,
    val trigger: BotTrigger = BotTrigger.Manual,
    val conditions: List<BotCondition> = emptyList(),
    val steps: List<BotStep>,
    /** حزم إضافية مسموح ظهورها أثناء التشغيل (غير الحزم التي تفتحها الخطوات). */
    val allowedPackages: Set<String> = emptySet(),
    val maxRunsPerDay: Int = 12,
    /** عدد المحاولات الإضافية للخطوات التي ليس لها أثر خارجي. */
    val retriesPerStep: Int = 1,
    /** موافقة المستخدم على التشغيل بدون حضوره لبوت له خطوات بأثر خارجي. */
    val unattendedApproved: Boolean = false,
)
