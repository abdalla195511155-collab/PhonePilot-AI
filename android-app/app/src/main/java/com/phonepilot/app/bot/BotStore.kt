package com.phonepilot.app.bot

import android.content.Context

/** حالة تشغيل بوت: تفعيله وآخر تشغيل وعدد مرات اليوم. */
data class BotState(
    val enabled: Boolean = false,
    val lastRunAt: Long? = null,
    val lastStatus: String? = null,
    val runsToday: Int = 0,
    val runsDay: String? = null,
)

interface BotStore {
    fun list(): List<BotDefinition>
    fun get(id: String): BotDefinition?
    fun save(def: BotDefinition)
    fun delete(id: String)
    fun state(id: String): BotState
    fun setState(id: String, state: BotState)
}

/** تخزين في الذاكرة للاختبارات. */
class InMemoryBotStore : BotStore {
    private val bots = linkedMapOf<String, BotDefinition>()
    private val states = hashMapOf<String, BotState>()
    override fun list() = bots.values.toList()
    override fun get(id: String) = bots[id]
    override fun save(def: BotDefinition) { bots[def.id] = def }
    override fun delete(id: String) { bots.remove(id); states.remove(id) }
    override fun state(id: String) = states[id] ?: BotState()
    override fun setState(id: String, state: BotState) { states[id] = state }
}

/** تخزين دائم في SharedPreferences. التعريفات تُخزَّن كـ JSON. */
class PrefsBotStore(context: Context) : BotStore {
    private val prefs = context.applicationContext.getSharedPreferences("phonepilot_bots", Context.MODE_PRIVATE)

    override fun list(): List<BotDefinition> =
        prefs.all.keys.filter { it.startsWith(DEF) }.sorted().mapNotNull { k ->
            prefs.getString(k, null)?.let { runCatching { BotJson.parse(it) }.getOrNull() }
        }

    override fun get(id: String): BotDefinition? =
        prefs.getString(DEF + id, null)?.let { runCatching { BotJson.parse(it) }.getOrNull() }

    override fun save(def: BotDefinition) {
        prefs.edit().putString(DEF + def.id, BotJson.toJson(def)).apply()
    }

    override fun delete(id: String) {
        prefs.edit().remove(DEF + id).remove(ST + id).apply()
    }

    override fun state(id: String): BotState {
        val s = prefs.getString(ST + id, null) ?: return BotState()
        val p = s.split("|")
        return BotState(
            enabled = p.getOrNull(0) == "1",
            lastRunAt = p.getOrNull(1)?.toLongOrNull(),
            lastStatus = p.getOrNull(2)?.ifEmpty { null },
            runsToday = p.getOrNull(3)?.toIntOrNull() ?: 0,
            runsDay = p.getOrNull(4)?.ifEmpty { null },
        )
    }

    override fun setState(id: String, state: BotState) {
        val v = listOf(
            if (state.enabled) "1" else "0",
            state.lastRunAt?.toString() ?: "",
            state.lastStatus ?: "",
            state.runsToday.toString(),
            state.runsDay ?: "",
        ).joinToString("|")
        prefs.edit().putString(ST + id, v).apply()
    }

    private companion object {
        const val DEF = "def_"
        const val ST = "st_"
    }
}
