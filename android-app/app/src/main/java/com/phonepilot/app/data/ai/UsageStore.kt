package com.phonepilot.app.data.ai

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UsageCounter(
    val success: Int = 0,
    val failure: Int = 0,
    val lastRequestAtEpochMs: Long? = null,
    val lastStatus: Int? = null,
    val lastError: String? = null,
)

/**
 * Local usage accounting and rate limiting.
 *
 * Only limits that a source actually declared are enforced. There is no default
 * of 200/hour, and a 429 from NVIDIA is never attributed to another provider or
 * to an intermediary.
 */
object UsageStore {

    private const val PREFS = "phonepilot_ai_usage"
    private const val KEY_COUNTERS = "counters"
    private const val KEY_COOLDOWNS = "cooldowns"
    private val gson = Gson()

    @Volatile
    private var appContext: Context? = null

    private val _counters = MutableStateFlow<Map<String, UsageCounter>>(emptyMap())
    val counters: StateFlow<Map<String, UsageCounter>> = _counters.asStateFlow()

    private val _cooldowns = MutableStateFlow<Map<String, Long>>(emptyMap())
    val cooldowns: StateFlow<Map<String, Long>> = _cooldowns.asStateFlow()

    /** In-memory request timestamps per provider (declared-limit windows). */
    private val requestLog = mutableMapOf<String, ArrayDeque<Long>>()

    @Synchronized
    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val prefs = prefs()
        _counters.value = decodeCounters(prefs.getString(KEY_COUNTERS, null))
        _cooldowns.value = decodeCooldowns(prefs.getString(KEY_COOLDOWNS, null))
    }

    fun counterFor(providerId: String, modelId: String): UsageCounter =
        _counters.value[counterKey(providerId, modelId)] ?: UsageCounter()

    fun cooldownUntil(providerId: String): Long? =
        _cooldowns.value[providerId]?.takeIf { it > System.currentTimeMillis() }

    /**
     * Returns a blocking error when a declared limit is reached or the provider
     * asked us (via 429/Retry-After) to wait. Returns null when the request may
     * be sent.
     */
    fun guard(provider: AiProviderSpec, now: Long = System.currentTimeMillis()): ChatLimitError? {
        val cooldown = _cooldowns.value[provider.id] ?: 0L
        if (cooldown > now) {
            val seconds = ((cooldown - now) / 1000).coerceAtLeast(1)
            return ChatLimitError(
                message = "المزود طلب الانتظار بعد 429 — أعد المحاولة بعد " + seconds + "ث",
                retryAfterSeconds = seconds,
            )
        }
        val limit = provider.accountRateLimit ?: return null
        if (limit.requests <= 0) return null
        val windowMs = when (limit.window) {
            LimitWindow.MINUTE -> 60_000L
            LimitWindow.HOUR -> 3_600_000L
            LimitWindow.DAY -> 86_400_000L
            LimitWindow.MONTH -> 30L * 86_400_000L
        }
        val log = synchronized(requestLog) {
            val entries = requestLog.getOrPut(provider.id) { ArrayDeque() }
            while (entries.isNotEmpty() && now - entries.first() > windowMs) {
                entries.removeFirst()
            }
            entries.size
        }
        if (log >= limit.requests) {
            return ChatLimitError(
                message = "بلغت الحد المعلن: ${RateLimitFormatter.describe(limit)}",
                retryAfterSeconds = null,
            )
        }
        return null
    }

    /** Records an attempt before the request so the limit counts it. */
    fun recordAttempt(providerId: String, modelId: String, now: Long = System.currentTimeMillis()) {
        synchronized(requestLog) {
            requestLog.getOrPut(providerId) { ArrayDeque() }.addLast(now)
        }
        updateCounter(providerId, modelId, now, success = null)
    }

    fun recordSuccess(providerId: String, modelId: String, now: Long = System.currentTimeMillis()) {
        updateCounter(providerId, modelId, now, success = true)
    }

    fun recordFailure(
        providerId: String,
        modelId: String,
        error: com.phonepilot.app.data.ai.chat.ChatError,
        now: Long = System.currentTimeMillis(),
    ) {
        updateCounter(providerId, modelId, now, success = false, error = error)
        if (error.kind == com.phonepilot.app.data.ai.chat.ChatErrorKind.RATE_LIMIT) {
            val retry = error.retryAfterSeconds
            if (retry != null && retry > 0) {
                val updated = _cooldowns.value + (providerId to now + retry * 1000)
                _cooldowns.value = updated
                persistCooldowns(updated)
            }
        }
    }

    fun resetUsage() {
        _counters.value = emptyMap()
        _cooldowns.value = emptyMap()
        synchronized(requestLog) { requestLog.clear() }
        if (appContext != null) {
            prefs().edit().remove(KEY_COUNTERS).remove(KEY_COOLDOWNS).apply()
        }
    }

    private fun updateCounter(
        providerId: String,
        modelId: String,
        now: Long,
        success: Boolean?,
        error: com.phonepilot.app.data.ai.chat.ChatError? = null,
    ) {
        val key = counterKey(providerId, modelId)
        val current = _counters.value[key] ?: UsageCounter()
        val updated = current.copy(
            success = if (success == true) current.success + 1 else current.success,
            failure = if (success == false) current.failure + 1 else current.failure,
            lastRequestAtEpochMs = now,
            lastStatus = error?.httpStatus ?: current.lastStatus,
            lastError = error?.userMessage ?: current.lastError,
        )
        val map = _counters.value + (key to updated)
        _counters.value = map
        persistCounters(map)
    }

    private fun persistCounters(map: Map<String, UsageCounter>) {
        if (appContext == null) return
        prefs().edit().putString(KEY_COUNTERS, gson.toJson(map)).apply()
    }

    private fun persistCooldowns(map: Map<String, Long>) {
        if (appContext == null) return
        prefs().edit().putString(KEY_COOLDOWNS, gson.toJson(map)).apply()
    }

    private fun decodeCounters(json: String?): Map<String, UsageCounter> {
        if (json.isNullOrBlank()) return emptyMap()
        val type = object : com.google.gson.reflect.TypeToken<Map<String, UsageCounter>>() {}.type
        return runCatching { gson.fromJson<Map<String, UsageCounter>>(json, type) }
            .getOrNull() ?: emptyMap()
    }

    private fun decodeCooldowns(json: String?): Map<String, Long> {
        if (json.isNullOrBlank()) return emptyMap()
        val type = object : com.google.gson.reflect.TypeToken<Map<String, Long>>() {}.type
        return runCatching { gson.fromJson<Map<String, Long>>(json, type) }.getOrNull()
            ?: emptyMap()
    }

    private fun counterKey(providerId: String, modelId: String) = "$providerId|$modelId"

    private fun prefs() = appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

data class ChatLimitError(
    val message: String,
    val retryAfterSeconds: Long?,
)
