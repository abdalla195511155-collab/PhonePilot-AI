package com.phonepilot.app.bot

enum class TriggerKind { MANUAL, DAILY, EVERY, NOTIFICATION }

enum class ActionKind { OPEN_APP, TYPE_TEXT, CLICK, TAP, SWIPE, BACK, WAIT_FOR_TEXT }

/**
 * عمليات نقية على [BotDefinition] يستخدمها محرر النموذج. لا تحفظ شيئًا ولا تمنح موافقة؛
 * الحفظ والتحقق والموافقة يمرّون بنفس المسار القديم ([BotValidator] و [BotApproval]).
 * القيم الافتراضية للحقول النصية فارغة عمدًا حتى يرفضها [BotValidator] إلى أن يملأها المستخدم.
 */
object BotForm {
    fun triggerKind(t: BotTrigger): TriggerKind = when (t) {
        BotTrigger.Manual -> TriggerKind.MANUAL
        is BotTrigger.DailyAt -> TriggerKind.DAILY
        is BotTrigger.EveryMinutes -> TriggerKind.EVERY
        is BotTrigger.NotificationFrom -> TriggerKind.NOTIFICATION
    }

    fun defaultTrigger(k: TriggerKind): BotTrigger = when (k) {
        TriggerKind.MANUAL -> BotTrigger.Manual
        TriggerKind.DAILY -> BotTrigger.DailyAt(9, 0)
        TriggerKind.EVERY -> BotTrigger.EveryMinutes(BotValidator.MIN_INTERVAL_MINUTES)
        TriggerKind.NOTIFICATION -> BotTrigger.NotificationFrom("", null)
    }

    /** تغيير نوع المحفّز؛ نفس النوع لا يغيّر شيئًا حتى لا تضيع القيم المكتوبة. */
    fun withTriggerKind(def: BotDefinition, k: TriggerKind): BotDefinition =
        if (triggerKind(def.trigger) == k) def else def.copy(trigger = defaultTrigger(k))

    fun actionKind(a: BotAction): ActionKind = when (a) {
        is BotAction.OpenApp -> ActionKind.OPEN_APP
        is BotAction.TypeText -> ActionKind.TYPE_TEXT
        is BotAction.Click -> ActionKind.CLICK
        is BotAction.TapAt -> ActionKind.TAP
        is BotAction.Swipe -> ActionKind.SWIPE
        BotAction.Back -> ActionKind.BACK
        is BotAction.WaitForText -> ActionKind.WAIT_FOR_TEXT
    }

    fun defaultAction(k: ActionKind): BotAction = when (k) {
        ActionKind.OPEN_APP -> BotAction.OpenApp("")
        ActionKind.TYPE_TEXT -> BotAction.TypeText("", null)
        ActionKind.CLICK -> BotAction.Click("")
        ActionKind.TAP -> BotAction.TapAt(0, 0)
        ActionKind.SWIPE -> BotAction.Swipe(0, 0, 0, 0)
        ActionKind.BACK -> BotAction.Back
        ActionKind.WAIT_FOR_TEXT -> BotAction.WaitForText("")
    }

    fun addStep(def: BotDefinition, k: ActionKind): BotDefinition =
        if (def.steps.size >= BotValidator.MAX_STEPS) def
        else def.copy(steps = def.steps + BotStep(defaultAction(k)))

    fun removeStep(def: BotDefinition, index: Int): BotDefinition =
        if (index !in def.steps.indices) def
        else def.copy(steps = def.steps.filterIndexed { i, _ -> i != index })

    fun replaceStep(def: BotDefinition, index: Int, step: BotStep): BotDefinition =
        if (index !in def.steps.indices) def
        else def.copy(steps = def.steps.mapIndexed { i, s -> if (i == index) step else s })

    /** يحرّك خطوة للأعلى (delta = -1) أو للأسفل (delta = +1). */
    fun moveStep(def: BotDefinition, index: Int, delta: Int): BotDefinition {
        val target = index + delta
        if (index !in def.steps.indices || target !in def.steps.indices) return def
        val list = def.steps.toMutableList()
        val s = list.removeAt(index)
        list.add(target, s)
        return def.copy(steps = list)
    }

    fun addCondition(def: BotDefinition, screenText: Boolean): BotDefinition =
        def.copy(
            conditions = def.conditions +
                if (screenText) BotCondition.ScreenContainsText("") else BotCondition.ForegroundPackageIs(""),
        )

    fun removeCondition(def: BotDefinition, index: Int): BotDefinition =
        if (index !in def.conditions.indices) def
        else def.copy(conditions = def.conditions.filterIndexed { i, _ -> i != index })

    fun replaceCondition(def: BotDefinition, index: Int, c: BotCondition): BotDefinition =
        if (index !in def.conditions.indices) def
        else def.copy(conditions = def.conditions.mapIndexed { i, x -> if (i == index) c else x })

    /** حقل رقمي: يقبل الأرقام فقط، والفارغ يصبح 0، ويُقصّ إلى ستة أرقام. */
    fun digitsToInt(text: String): Int = text.filter { it in '0'..'9' }.take(6).toIntOrNull() ?: 0
}
