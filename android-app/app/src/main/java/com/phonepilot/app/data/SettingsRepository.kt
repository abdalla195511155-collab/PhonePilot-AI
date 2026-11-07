package com.phonepilot.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dependency-free settings store backed by SharedPreferences.
 *
 * Every value is exposed as a StateFlow so screens update reactively when a
 * setting changes, and is persisted immediately so it survives restarts.
 *
 * API keys are deliberately NOT stored here: they stay in memory only until a
 * secure storage mechanism exists (Phase 3).
 */
object SettingsRepository {

    private const val PREFS_NAME = "phonepilot_settings"
    private const val KEY_ACTIVE_TASK_ID = "active_task_id"
    private const val KEY_PRIVACY_FILTER = "privacy_filter_enabled"

    @Volatile
    private var prefs: SharedPreferences? = null

    private val _activeTaskId = MutableStateFlow<Long?>(null)
    val activeTaskId: StateFlow<Long?> = _activeTaskId.asStateFlow()

    private val _privacyFilterEnabled = MutableStateFlow(true)
    val privacyFilterEnabled: StateFlow<Boolean> = _privacyFilterEnabled.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val stored = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = stored

        _activeTaskId.value = stored.getLong(KEY_ACTIVE_TASK_ID, 0L).takeIf { it > 0L }
        _privacyFilterEnabled.value = stored.getBoolean(KEY_PRIVACY_FILTER, true)
    }

    /** Used by [com.phonepilot.app.privacy.PrivacyFilter] as a single choke point. */
    fun isPrivacyFilterEnabled(): Boolean = _privacyFilterEnabled.value

    fun setActiveTaskId(taskId: Long) {
        if (taskId <= 0L) return
        prefs?.edit()?.putLong(KEY_ACTIVE_TASK_ID, taskId)?.apply()
        _activeTaskId.value = taskId
    }

    fun clearActiveTaskId() {
        prefs?.edit()?.remove(KEY_ACTIVE_TASK_ID)?.apply()
        _activeTaskId.value = null
    }

    fun setPrivacyFilterEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_PRIVACY_FILTER, enabled)?.apply()
        _privacyFilterEnabled.value = enabled
    }
}
