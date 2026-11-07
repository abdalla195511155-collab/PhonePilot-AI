package com.phonepilot.app.data.ai

import android.content.Context
import com.phonepilot.app.data.AiEndpointValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Single source of truth for AI providers and models.
 *
 * - Built-in NVIDIA catalog: bundled documented seed + optional live sync.
 * - Custom providers: user-defined, stored as JSON (no schema change).
 * - API keys: Android Keystore only ([SecureKeyStore]); never in JSON or logs.
 * - Limits: only what a source declares. No default is ever applied.
 */
object AiProviderRepository {

    private const val PREFS = "phonepilot_ai_catalog"
    private const val K_CUSTOM_PROVIDERS = "custom_providers"
    private const val K_NVIDIA_MODELS = "nvidia_models"
    private const val K_NVIDIA_SYNCED_AT = "nvidia_synced_at"
    private const val K_ACTIVE_PROVIDER = "active_provider_id"
    private const val K_ACTIVE_MODEL = "active_model_id"
    private const val K_NVIDIA_ENABLED = "nvidia_enabled"
    private const val NVIDIA_KEY_ID = "nvidia_api_key"

    private fun customKeyId(providerId: String) = "custom_api_key_$providerId"

    @Volatile
    private var appContext: Context? = null

    private val _providers = MutableStateFlow<List<AiProviderSpec>>(emptyList())
    val providers: StateFlow<List<AiProviderSpec>> = _providers.asStateFlow()

    private val _activeProviderId = MutableStateFlow<String?>(null)
    val activeProviderId: StateFlow<String?> = _activeProviderId.asStateFlow()

    private val _activeModelId = MutableStateFlow<String?>(null)
    val activeModelId: StateFlow<String?> = _activeModelId.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastMessage = MutableStateFlow<String?>(null)
    val lastMessage: StateFlow<String?> = _lastMessage.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        _activeProviderId.value = prefs().getString(K_ACTIVE_PROVIDER, null)
        _activeModelId.value = prefs().getString(K_ACTIVE_MODEL, null)
        rebuild()
        if (_activeProviderId.value == null) {
            _activeProviderId.value = NvidiaCatalog.PROVIDER_ID
        }
    }

    fun selectedProvider(): AiProviderSpec? =
        _providers.value.firstOrNull { it.id == _activeProviderId.value }

    fun selectedModel(): AiModelSpec? {
        val provider = selectedProvider() ?: return null
        val modelId = _activeModelId.value ?: return null
        return provider.models.firstOrNull { it.id == modelId }
    }

    /** Selects a provider without a model (used before any model is added). */
    fun selectProvider(providerId: String) {
        prefs().edit()
            .putString(K_ACTIVE_PROVIDER, providerId)
            .remove(K_ACTIVE_MODEL)
            .apply()
        _activeProviderId.value = providerId
        _activeModelId.value = null
    }

    fun selectModel(providerId: String, modelId: String) {
        prefs().edit()
            .putString(K_ACTIVE_PROVIDER, providerId)
            .putString(K_ACTIVE_MODEL, modelId)
            .apply()
        _activeProviderId.value = providerId
        _activeModelId.value = modelId
    }

    fun hasApiKey(provider: AiProviderSpec): Boolean {
        val context = appContext ?: return false
        val id = if (provider.isBuiltIn) NVIDIA_KEY_ID else customKeyId(provider.id)
        return SecureKeyStore.has(context, id)
    }

    fun apiKeyFor(provider: AiProviderSpec): String? {
        val context = appContext ?: return null
        val id = if (provider.isBuiltIn) NVIDIA_KEY_ID else customKeyId(provider.id)
        return SecureKeyStore.get(context, id)
    }

    fun saveApiKey(providerId: String, key: String): String? {
        val context = appContext ?: return "التخزين غير مهيأ"
        val provider = _providers.value.firstOrNull { it.id == providerId }
            ?: return "المزود غير موجود"
        val storeId = if (provider.isBuiltIn) NVIDIA_KEY_ID else customKeyId(providerId)
        return try {
            SecureKeyStore.put(context, storeId, key.trim())
            rebuild()
            null
        } catch (error: Exception) {
            "تعذر حفظ المفتاح بأمان: ${error.javaClass.simpleName}"
        }
    }

    fun clearApiKey(providerId: String) {
        val context = appContext ?: return
        val provider = _providers.value.firstOrNull { it.id == providerId } ?: return
        val storeId = if (provider.isBuiltIn) NVIDIA_KEY_ID else customKeyId(providerId)
        SecureKeyStore.remove(context, storeId)
        rebuild()
    }

    /** Returns the new provider id, or an error message. */
    fun addCustomProvider(
        name: String,
        baseUrl: String,
        protocol: AiProtocol,
        limitRequests: Int?,
        limitWindow: LimitWindow,
    ): Pair<String?, String?> {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return null to "أدخل اسم المزود"
        AiEndpointValidator.validate(baseUrl)?.let { return null to it }

        val id = "custom-" + UUID.randomUUID().toString().take(8)
        val provider = AiProviderSpec(
            id = id,
            name = trimmedName,
            kind = ProviderKind.CUSTOM,
            baseUrl = baseUrl.trim(),
            protocol = protocol,
            accountRateLimit = limitRequests?.takeIf { it > 0 }?.let { requests ->
                RateLimitSpec(
                    requests = requests,
                    window = limitWindow,
                    scope = LimitScope.PROVIDER,
                    source = LimitSource.USER_DEFINED,
                    note = "حد أدخله المستخدم لهذا المزود — ليس بيانًا من المزود",
                )
            },
        )
        persistCustom(_providers.value.filter { !it.isBuiltIn } + provider)
        rebuild()
        return id to null
    }

    fun deleteCustomProvider(providerId: String) {
        val context = appContext ?: return
        persistCustom(_providers.value.filter { !it.isBuiltIn && it.id != providerId })
        SecureKeyStore.remove(context, customKeyId(providerId))
        if (_activeProviderId.value == providerId) {
            _activeProviderId.value = NvidiaCatalog.PROVIDER_ID
            _activeModelId.value = null
            prefs().edit()
                .putString(K_ACTIVE_PROVIDER, NvidiaCatalog.PROVIDER_ID)
                .remove(K_ACTIVE_MODEL)
                .apply()
        }
        rebuild()
    }

    fun setProviderEnabled(providerId: String, enabled: Boolean) {
        val provider = _providers.value.firstOrNull { it.id == providerId } ?: return
        if (provider.isBuiltIn) {
            prefs().edit().putBoolean(K_NVIDIA_ENABLED, enabled).apply()
        } else {
            persistCustom(
                _providers.value.filter { !it.isBuiltIn }.map {
                    if (it.id == providerId) it.copy(enabled = enabled) else it
                },
            )
        }
        rebuild()
    }

    /** Updates a custom provider. Returns an error message or null. */
    fun updateCustomProvider(
        providerId: String,
        name: String,
        baseUrl: String,
        protocol: AiProtocol,
        limitRequests: Int?,
        limitWindow: LimitWindow,
    ): String? {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return "أدخل اسم المزود"
        AiEndpointValidator.validate(baseUrl)?.let { return it }
        val provider = _providers.value.firstOrNull { it.id == providerId && !it.isBuiltIn }
            ?: return "المزود غير موجود"
        val updated = provider.copy(
            name = trimmedName,
            baseUrl = baseUrl.trim(),
            protocol = protocol,
            accountRateLimit = limitRequests?.takeIf { it > 0 }?.let { requests ->
                RateLimitSpec(
                    requests = requests,
                    window = limitWindow,
                    scope = LimitScope.PROVIDER,
                    source = LimitSource.USER_DEFINED,
                    note = "حد أدخله المستخدم — ليس بيانًا من المزود",
                )
            },
        )
        persistCustom(_providers.value.filter { !it.isBuiltIn && it.id != providerId } + updated)
        rebuild()
        return null
    }

    fun renameModel(providerId: String, modelId: String, displayName: String): String? {
        val trimmed = displayName.trim()
        if (trimmed.isEmpty()) return "أدخل اسمًا للنموذج"
        val provider = _providers.value.firstOrNull { it.id == providerId } ?: return "المزود غير موجود"
        val models = provider.models.map {
            if (it.id == modelId) it.copy(displayName = trimmed) else it
        }
        if (models == provider.models) return "النموذج غير موجود"
        updateModels(provider, models)
        return null
    }

    fun deleteModel(providerId: String, modelId: String): String? {
        val provider = _providers.value.firstOrNull { it.id == providerId } ?: return "المزود غير موجود"
        if (provider.isBuiltIn) return "نماذج NVIDIA المدمجة غير قابلة للحذف"
        val models = provider.models.filterNot { it.id == modelId }
        if (models.size == provider.models.size) return "النموذج غير موجود"
        if (_activeProviderId.value == providerId && _activeModelId.value == modelId) {
            prefs().edit().remove(K_ACTIVE_MODEL).apply()
            _activeModelId.value = null
        }
        updateModels(provider, models)
        return null
    }

    /** GET /models with the stored key: verifies reachability and auth without POST cost. */
    suspend fun testConnection(providerId: String): ConnectionTestResult {
        val provider = _providers.value.firstOrNull { it.id == providerId }
            ?: return ConnectionTestResult(false, "المزود غير موجود")
        val key = apiKeyFor(provider)
        if (key.isNullOrBlank() && !provider.isBuiltIn) {
            return ConnectionTestResult(false, "أضف مفتاح API أولاً")
        }
        val started = System.currentTimeMillis()
        val outcome = ModelDiscoveryClient.fetch(provider.baseUrl, key)
        val latency = System.currentTimeMillis() - started
        return if (outcome.errorMessage != null) {
            ConnectionTestResult(false, outcome.errorMessage, latency)
        } else {
            ConnectionTestResult(true, "الاتصال سليم", latency, outcome.models.size)
        }
    }

    fun addManualModel(providerId: String, modelId: String, displayName: String): String? {
        val trimmedId = modelId.trim()
        if (trimmedId.isEmpty()) return "أدخل معرّف النموذج"
        val provider = _providers.value.firstOrNull { it.id == providerId } ?: return "المزود غير موجود"
        if (provider.models.any { it.id == trimmedId }) return "النموذج موجود بالفعل"
        val model = AiModelSpec(
            id = trimmedId,
            displayName = displayName.trim().takeIf { it.isNotEmpty() } ?: trimmedId,
            capabilities = emptyList(),
            sourceUrl = "user:manual",
        )
        updateModels(provider, provider.models + model)
        return null
    }

    suspend fun sync(providerId: String): SyncResult {
        val provider = _providers.value.firstOrNull { it.id == providerId }
            ?: return SyncResult(0, 0, 0, "المزود غير موجود")
        if (_isSyncing.value) return SyncResult(0, 0, 0, "مزامنة جارية بالفعل")
        _isSyncing.value = true
        try {
            val key = apiKeyFor(provider)
            if (!provider.isBuiltIn && key.isNullOrBlank()) {
                return SyncResult(0, 0, provider.models.size, "أضف مفتاح API أولاً")
            }
            val outcome = ModelDiscoveryClient.fetch(
                baseUrl = provider.baseUrl,
                apiKey = key,
            )
            if (outcome.errorMessage != null) {
                rebuild(
                    syncedAt = provider.lastSyncedAtEpochMs,
                    error = outcome.errorMessage,
                )
                return SyncResult(0, 0, provider.models.size, outcome.errorMessage)
            }

            val fetched = if (provider.isBuiltIn) {
                NvidiaCatalog.mergeFetchedModels(outcome.models.map { it.id })
            } else {
                mergeDiscovered(provider.models, outcome.models)
            }
            val before = provider.models.size
            val updated = AiProviderSpec(
                id = provider.id,
                name = provider.name,
                kind = provider.kind,
                baseUrl = provider.baseUrl,
                protocol = provider.protocol,
                models = fetched,
                accountRateLimit = provider.accountRateLimit,
                docsUrl = provider.docsUrl,
                notes = provider.notes,
                enabled = provider.enabled,
            )
            updateModels(updated, fetched, syncedAt = System.currentTimeMillis())
            return SyncResult(
                addedModels = (fetched.size - before).coerceAtLeast(0),
                updatedModels = fetched.size,
                totalModels = fetched.size,
                errorMessage = null,
            )
        } finally {
            _isSyncing.value = false
        }
    }

    fun clearMessage() {
        _lastMessage.value = null
    }

    private fun mergeDiscovered(
        existing: List<AiModelSpec>,
        discovered: List<AiModelSpec>,
    ): List<AiModelSpec> {
        val byId = linkedMapOf<String, AiModelSpec>()
        existing.forEach { byId[it.id] = it }
        discovered.forEach { model ->
            val previous = byId[model.id]
            // Discovered metadata wins, but keep a manual display name if the
            // provider did not return one.
            byId[model.id] = model.copy(
                displayName = if (model.displayName == model.id && previous != null) {
                    previous.displayName
                } else {
                    model.displayName
                },
            )
        }
        return byId.values.toList()
    }

    private fun updateModels(
        provider: AiProviderSpec,
        models: List<AiModelSpec>,
        syncedAt: Long? = provider.lastSyncedAtEpochMs,
    ) {
        val updated = provider.copy(
            models = models,
            lastSyncedAtEpochMs = syncedAt,
            syncError = null,
        )
        if (updated.isBuiltIn) {
            prefs().edit()
                .putString(K_NVIDIA_MODELS, AiProviderJson.encodeModels(models))
                .putLong(K_NVIDIA_SYNCED_AT, syncedAt ?: 0L)
                .apply()
        } else {
            persistCustom(_providers.value.filter { !it.isBuiltIn && it.id != updated.id } + updated)
        }
        rebuild()
    }

    private fun persistCustom(customProviders: List<AiProviderSpec>) {
        prefs().edit()
            .putString(K_CUSTOM_PROVIDERS, AiProviderJson.encodeProviders(customProviders))
            .apply()
    }

    private fun rebuild(
        syncedAt: Long? = null,
        error: String? = null,
    ) {
        val context = appContext ?: return
        val custom = AiProviderJson.decodeProviders(
            prefs().getString(K_CUSTOM_PROVIDERS, null),
        )

        val storedNvidiaModels = AiProviderJson.decodeModels(
            prefs().getString(K_NVIDIA_MODELS, null),
        )
        val nvidiaModels = storedNvidiaModels.ifEmpty { NvidiaCatalog.seedModels() }
        val nvidiaSyncedAt = prefs().getLong(K_NVIDIA_SYNCED_AT, 0L).takeIf { it > 0L }

        val nvidia = NvidiaCatalog.seedProvider(
            hasKey = SecureKeyStore.has(context, NVIDIA_KEY_ID),
        ).copy(
            models = nvidiaModels,
            lastSyncedAtEpochMs = syncedAt ?: nvidiaSyncedAt,
            syncError = error,
            enabled = prefs().getBoolean(K_NVIDIA_ENABLED, true),
        )

        val customWithKeys = custom.map { provider ->
            provider.copy(hasApiKey = SecureKeyStore.has(context, customKeyId(provider.id)))
        }

        _providers.value = listOf(nvidia) + customWithKeys
        _lastMessage.value = error
        if (_activeProviderId.value == null) {
            _activeProviderId.value = NvidiaCatalog.PROVIDER_ID
        }
    }

    private fun prefs() = appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
