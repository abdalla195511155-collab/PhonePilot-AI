package com.phonepilot.app.bot

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** تحويل تعريف البوت من وإلى JSON. يرفض أي نوع غير معروف بدل تجاهله. */
object BotJson {
    class ParseException(message: String) : Exception(message)

    fun toJson(def: BotDefinition): String = toObject(def).toString()

    fun toPrettyJson(def: BotDefinition): String =
        com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(toObject(def))

    fun parse(text: String): BotDefinition {
        val root = try {
            JsonParser.parseString(text)
        } catch (e: Exception) {
            throw ParseException("JSON غير صالح")
        }
        if (!root.isJsonObject) throw ParseException("الجذر يجب أن يكون كائنًا")
        return fromObject(root.asJsonObject)
    }

    fun toObject(def: BotDefinition): JsonObject = JsonObject().apply {
        addProperty("id", def.id)
        addProperty("name", def.name)
        add("trigger", triggerObj(def.trigger))
        add("conditions", JsonArray().also { a -> def.conditions.forEach { a.add(conditionObj(it)) } })
        add("steps", JsonArray().also { a -> def.steps.forEach { a.add(stepObj(it)) } })
        add("allowedPackages", JsonArray().also { a -> def.allowedPackages.sorted().forEach { a.add(it) } })
        addProperty("maxRunsPerDay", def.maxRunsPerDay)
        addProperty("retriesPerStep", def.retriesPerStep)
        addProperty("unattendedApproved", def.unattendedApproved)
    }

    private fun triggerObj(t: BotTrigger) = JsonObject().apply {
        when (t) {
            BotTrigger.Manual -> addProperty("type", "manual")
            is BotTrigger.EveryMinutes -> { addProperty("type", "every_minutes"); addProperty("minutes", t.minutes) }
            is BotTrigger.DailyAt -> { addProperty("type", "daily_at"); addProperty("hour", t.hour); addProperty("minute", t.minute) }
            is BotTrigger.NotificationFrom -> {
                addProperty("type", "notification")
                addProperty("package", t.packageName)
                t.titleContains?.let { addProperty("titleContains", it) }
            }
        }
    }

    private fun conditionObj(c: BotCondition) = JsonObject().apply {
        when (c) {
            is BotCondition.ForegroundPackageIs -> { addProperty("type", "foreground_package"); addProperty("package", c.packageName) }
            is BotCondition.ScreenContainsText -> { addProperty("type", "screen_contains"); addProperty("text", c.text) }
        }
    }

    private fun stepObj(s: BotStep) = JsonObject().apply {
        when (val a = s.action) {
            is BotAction.OpenApp -> { addProperty("action", "open_app"); addProperty("package", a.packageName) }
            is BotAction.TypeText -> {
                addProperty("action", "type_text"); addProperty("text", a.text)
                a.target?.let { addProperty("target", it) }
            }
            is BotAction.Click -> { addProperty("action", "click"); addProperty("target", a.target) }
            is BotAction.TapAt -> { addProperty("action", "tap"); addProperty("x", a.x); addProperty("y", a.y) }
            is BotAction.Swipe -> {
                addProperty("action", "swipe")
                addProperty("x1", a.x1); addProperty("y1", a.y1); addProperty("x2", a.x2); addProperty("y2", a.y2)
                addProperty("durationMs", a.durationMs)
            }
            BotAction.Back -> addProperty("action", "back")
            is BotAction.WaitForText -> { addProperty("action", "wait_for_text"); addProperty("text", a.text); addProperty("timeoutMs", a.timeoutMs) }
        }
        s.verifyText?.let { addProperty("verifyText", it) }
        addProperty("verifyTimeoutMs", s.verifyTimeoutMs)
        addProperty("externalEffect", s.externalEffect)
    }

    private fun JsonObject.str(k: String): String =
        get(k)?.takeIf { it.isJsonPrimitive }?.asString ?: throw ParseException("الحقل \"$k\" مطلوب")

    private fun JsonObject.optStr(k: String): String? = get(k)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.int(k: String): Int = try {
        get(k)?.takeIf { it.isJsonPrimitive }?.asInt ?: throw ParseException("الحقل \"$k\" مطلوب")
    } catch (e: NumberFormatException) { throw ParseException("الحقل \"$k\" ليس رقمًا") }

    private fun JsonObject.optLong(k: String, d: Long): Long = try {
        get(k)?.takeIf { it.isJsonPrimitive }?.asLong ?: d
    } catch (e: NumberFormatException) { throw ParseException("الحقل \"$k\" ليس رقمًا") }

    private fun JsonObject.optInt(k: String, d: Int): Int = if (has(k)) int(k) else d

    private fun JsonObject.optBool(k: String, d: Boolean): Boolean = get(k)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: d

    private fun JsonObject.objs(k: String): List<JsonObject> {
        val a = get(k) ?: return emptyList()
        if (!a.isJsonArray) throw ParseException("الحقل \"$k\" يجب أن يكون قائمة")
        return a.asJsonArray.map { if (it.isJsonObject) it.asJsonObject else throw ParseException("عنصر غير صالح في \"$k\"") }
    }

    fun fromObject(o: JsonObject): BotDefinition {
        val trigger = o.get("trigger")?.takeIf { it.isJsonObject }?.asJsonObject?.let { parseTrigger(it) } ?: BotTrigger.Manual
        val steps = o.objs("steps").map { parseStep(it) }
        val packages = o.get("allowedPackages")?.takeIf { it.isJsonArray }?.asJsonArray?.map { it.asString }?.toSet() ?: emptySet()
        return BotDefinition(
            id = o.str("id"),
            name = o.str("name"),
            trigger = trigger,
            conditions = o.objs("conditions").map { parseCondition(it) },
            steps = steps,
            allowedPackages = packages,
            maxRunsPerDay = o.optInt("maxRunsPerDay", 12),
            retriesPerStep = o.optInt("retriesPerStep", 1),
            unattendedApproved = o.optBool("unattendedApproved", false),
        )
    }

    private fun parseTrigger(o: JsonObject): BotTrigger = when (val t = o.str("type")) {
        "manual" -> BotTrigger.Manual
        "every_minutes" -> BotTrigger.EveryMinutes(o.int("minutes"))
        "daily_at" -> BotTrigger.DailyAt(o.int("hour"), o.int("minute"))
        "notification" -> BotTrigger.NotificationFrom(o.str("package"), o.optStr("titleContains"))
        else -> throw ParseException("نوع محفّز غير معروف: $t")
    }

    private fun parseCondition(o: JsonObject): BotCondition = when (val t = o.str("type")) {
        "foreground_package" -> BotCondition.ForegroundPackageIs(o.str("package"))
        "screen_contains" -> BotCondition.ScreenContainsText(o.str("text"))
        else -> throw ParseException("نوع شرط غير معروف: $t")
    }

    private fun parseStep(o: JsonObject): BotStep {
        val action = when (val a = o.str("action")) {
            "open_app" -> BotAction.OpenApp(o.str("package"))
            "type_text" -> BotAction.TypeText(o.str("text"), o.optStr("target"))
            "click" -> BotAction.Click(o.str("target"))
            "tap" -> BotAction.TapAt(o.int("x"), o.int("y"))
            "swipe" -> BotAction.Swipe(o.int("x1"), o.int("y1"), o.int("x2"), o.int("y2"), o.optLong("durationMs", 400L))
            "back" -> BotAction.Back
            "wait_for_text" -> BotAction.WaitForText(o.str("text"), o.optLong("timeoutMs", 5_000L))
            else -> throw ParseException("نوع إجراء غير معروف: $a")
        }
        return BotStep(
            action = action,
            verifyText = o.optStr("verifyText"),
            verifyTimeoutMs = o.optLong("verifyTimeoutMs", 3_000L),
            externalEffect = o.optBool("externalEffect", false),
        )
    }
}
