package com.phonepilot.app.ui.ai

import android.content.Context
import com.phonepilot.app.data.ai.AiModelSpec
import com.phonepilot.app.data.ai.AiProtocol
import com.phonepilot.app.data.ai.AiProviderRepository
import com.phonepilot.app.data.ai.AiProviderSpec
import com.phonepilot.app.data.ai.LimitWindow
import com.phonepilot.app.data.ai.ModelCapability
import com.phonepilot.app.data.ai.UsageCounter
import com.phonepilot.app.data.ai.UsageStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiProvidersUiState(
    val providers: List<AiProviderSpec> = emptyList(),
    val activeProviderId: String? = null,
    val activeModelId: String? = null,
    val isSyncing: Boolean = false,
    val message: String? = null,
    val query: String = "",
    val capabilityFilter: Set<ModelCapability> = emptySet(),
    val counters: Map<String, UsageCounter> = emptyMap(),
) {
    val activeProvider: AiProviderSpec? get() = providers.firstOrNull { it.id == activeProviderId }
    val activeModel: AiModelSpec?
        get() = activeProvider?.models?.firstOrNull { it.id == activeModelId }

    val visibleModels: List<AiModelSpec>
        get() {
            val provider = activeProvider ?: return emptyList()
            val text = query.trim()
            return provider.models.filter { model ->
                val matchesQuery = text.isEmpty() ||
                    model.id.contains(text, ignoreCase = true) ||
                    model.displayName.contains(text, ignoreCase = true)
                val matchesCapabilities = capabilityFilter.isEmpty() ||
                    capabilityFilter.all { model.hasCapability(it) }
                matchesQuery && matchesCapabilities
            }
        }

    val customProviders: List<AiProviderSpec> get() = providers.filter { !it.isBuiltIn }
}

/**
 * UI state for the unified provider/model screen. All data comes from
 * [AiProviderRepository]; this holder only adds screen-local view state.
 */
class AiProvidersStateHolder(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext

    private val _uiState = MutableStateFlow(AiProvidersUiState())
    val uiState: StateFlow<AiProvidersUiState> = _uiState.asStateFlow()

    init {
        AiProviderRepository.init(appContext)
        UsageStore.init(appContext)
        observeRepository()
        observeUsage()
    }

    private fun observeUsage() {
        scope.launch {
            UsageStore.counters.collect { counters ->
                _uiState.update { it.copy(counters = counters) }
            }
        }
    }

    private fun observeRepository() {
        scope.launch {
            combine(
                AiProviderRepository.providers,
                AiProviderRepository.activeProviderId,
                AiProviderRepository.activeModelId,
                AiProviderRepository.isSyncing,
                AiProviderRepository.lastMessage,
            ) { providers, providerId, modelId, syncing, message ->
                RepositorySnapshot(providers, providerId, modelId, syncing, message)
            }.collect { snapshot ->
                _uiState.update {
                    it.copy(
                        providers = snapshot.providers,
                        activeProviderId = snapshot.providerId,
                        activeModelId = snapshot.modelId,
                        isSyncing = snapshot.syncing,
                        message = snapshot.message,
                    )
                }
            }
        }
    }

    fun selectProvider(providerId: String) {
        val provider = _uiState.value.providers.firstOrNull { it.id == providerId } ?: return
        val modelId = provider.models.firstOrNull()?.id ?: ""
        if (modelId.isNotEmpty()) {
            AiProviderRepository.selectModel(providerId, modelId)
        } else {
            AiProviderRepository.selectProvider(providerId)
        }
    }

    fun selectModel(providerId: String, modelId: String) {
        AiProviderRepository.selectModel(providerId, modelId)
    }

    fun saveKey(providerId: String, key: String): Boolean {
        val error = AiProviderRepository.saveApiKey(providerId, key)
        _uiState.update { it.copy(message = error ?: "تم حفظ المفتاح مشفّرًا على الجهاز") }
        return error == null
    }

    fun clearKey(providerId: String) {
        AiProviderRepository.clearApiKey(providerId)
        _uiState.update { it.copy(message = "تم حذف المفتاح") }
    }

    fun addProvider(
        name: String,
        baseUrl: String,
        protocol: AiProtocol,
        limitRequests: Int?,
        limitWindow: LimitWindow,
    ): String? {
        val (id, error) = AiProviderRepository.addCustomProvider(
            name = name,
            baseUrl = baseUrl,
            protocol = protocol,
            limitRequests = limitRequests,
            limitWindow = limitWindow,
        )
        if (error != null) {
            _uiState.update { it.copy(message = error) }
            return null
        }
        if (id != null) {
            AiProviderRepository.selectProvider(id)
        }
        _uiState.update { it.copy(message = "تمت إضافة المزود") }
        return id
    }

    fun deleteProvider(providerId: String) {
        AiProviderRepository.deleteCustomProvider(providerId)
        _uiState.update { it.copy(message = "تم حذف المزود ومفتاحه") }
    }

    fun setEnabled(providerId: String, enabled: Boolean) {
        AiProviderRepository.setProviderEnabled(providerId, enabled)
        _uiState.update { it.copy(message = if (enabled) "تم تفعيل المزود" else "تم تعطيل المزود") }
    }

    fun testConnection(providerId: String) {
        scope.launch {
            val result = AiProviderRepository.testConnection(providerId)
            val text = if (result.ok) {
                "الاتصال سليم (${result.latencyMs ?: 0}ms • ${result.modelCount} نموذج)"
            } else {
                result.message
            }
            _uiState.update { it.copy(message = text) }
        }
    }

    fun updateProvider(
        providerId: String,
        name: String,
        baseUrl: String,
        protocol: AiProtocol,
        limitRequests: Int?,
        limitWindow: LimitWindow,
    ): Boolean {
        val error = AiProviderRepository.updateCustomProvider(
            providerId = providerId,
            name = name,
            baseUrl = baseUrl,
            protocol = protocol,
            limitRequests = limitRequests,
            limitWindow = limitWindow,
        )
        _uiState.update { it.copy(message = error ?: "تم تحديث المزود") }
        return error == null
    }

    fun renameModel(providerId: String, modelId: String, displayName: String): Boolean {
        val error = AiProviderRepository.renameModel(providerId, modelId, displayName)
        _uiState.update { it.copy(message = error ?: "تم تحديث اسم النموذج") }
        return error == null
    }

    fun deleteModel(providerId: String, modelId: String) {
        val error = AiProviderRepository.deleteModel(providerId, modelId)
        _uiState.update { it.copy(message = error ?: "تم حذف النموذج") }
    }

    fun addManualModel(providerId: String, modelId: String, displayName: String): Boolean {
        val error = AiProviderRepository.addManualModel(providerId, modelId, displayName)
        _uiState.update { it.copy(message = error ?: "أُضيف النموذج يدويًا") }
        return error == null
    }

    fun sync(providerId: String) {
        scope.launch {
            try {
                val result = AiProviderRepository.sync(providerId)
                _uiState.update {
                    it.copy(
                        message = result.errorMessage
                            ?: "تمت المزامنة: ${result.totalModels} نموذج",
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(message = "تعذرت المزامنة: ${error.javaClass.simpleName}")
                }
            }
        }
    }

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun toggleCapability(capability: ModelCapability) {
        _uiState.update { current ->
            val next = if (current.capabilityFilter.contains(capability)) {
                current.capabilityFilter - capability
            } else {
                current.capabilityFilter + capability
            }
            current.copy(capabilityFilter = next)
        }
    }

    fun clearFilters() {
        _uiState.update { it.copy(capabilityFilter = emptySet(), query = "") }
    }

    fun clearMessage() {
        AiProviderRepository.clearMessage()
        _uiState.update { it.copy(message = null) }
    }

    private data class RepositorySnapshot(
        val providers: List<AiProviderSpec>,
        val providerId: String?,
        val modelId: String?,
        val syncing: Boolean,
        val message: String?,
    )
}
