package com.phonepilot.app.ui.ai

import android.content.Context
import com.phonepilot.app.data.ai.AiModelSpec
import com.phonepilot.app.data.ai.AiProviderRepository
import com.phonepilot.app.data.ai.AiProviderSpec
import com.phonepilot.app.data.ai.ModelCapability
import com.phonepilot.app.data.ai.UsageCounter
import com.phonepilot.app.data.ai.UsageStore
import com.phonepilot.app.data.ai.chat.AiChatRepository
import com.phonepilot.app.data.ai.chat.ChatError
import com.phonepilot.app.data.ai.chat.ChatErrorKind
import com.phonepilot.app.data.ai.chat.ChatHistoryStore
import com.phonepilot.app.data.ai.chat.ChatMessage
import com.phonepilot.app.data.ai.chat.ChatOutcome
import com.phonepilot.app.data.ai.chat.ChatRole
import com.phonepilot.app.data.ai.chat.ChatStreamEvent
import com.phonepilot.app.data.ai.chat.TokenUsage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiAssistantUiState(
    val provider: AiProviderSpec? = null,
    val model: AiModelSpec? = null,
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    val streamingText: String = "",
    val error: ChatError? = null,
    val notice: String? = null,
    val streamingSupported: Boolean = false,
    val useStreaming: Boolean = true,
    val lastUsage: TokenUsage? = null,
    val usage: UsageCounter = UsageCounter(),
) {
    val canSend: Boolean get() = input.isNotBlank() && !isSending && model != null && provider != null
}

/**
 * Real chat state: history (encrypted), streaming, cancellation, manual retry,
 * usage. No synthetic assistant content is ever produced here.
 */
class AiAssistantStateHolder(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private var sendJob: Job? = null
    private var loadedKey: String? = null

    private val _uiState = MutableStateFlow(AiAssistantUiState())
    val uiState: StateFlow<AiAssistantUiState> = _uiState.asStateFlow()

    init {
        AiProviderRepository.init(appContext)
        UsageStore.init(appContext)
        observeSelection()
        observeUsage()
    }

    private fun observeSelection() {
        scope.launch {
            combine(
                AiProviderRepository.providers,
                AiProviderRepository.activeProviderId,
                AiProviderRepository.activeModelId,
            ) { providers, providerId, modelId ->
                Selection(providers, providerId, modelId)
            }.collect { selection ->
                val provider = selection.providers.firstOrNull { it.id == selection.providerId }
                val model = provider?.models?.firstOrNull { it.id == selection.modelId }
                val key = "${provider?.id}|${model?.id}"
                if (key != loadedKey) {
                    loadedKey = key
                    val history = if (provider != null && model != null) {
                        ChatHistoryStore.load(appContext, provider.id, model.id)
                    } else {
                        emptyList()
                    }
                    _uiState.update {
                        it.copy(
                            provider = provider,
                            model = model,
                            messages = history,
                            streamingSupported = model != null && AiChatRepository.supportsStreaming(model),
                            error = null,
                            streamingText = "",
                            usage = if (provider != null && model != null) {
                                UsageStore.counterFor(provider.id, model.id)
                            } else {
                                UsageCounter()
                            },
                        )
                    }
                } else {
                    _uiState.update { it.copy(provider = provider, model = model) }
                }
            }
        }
    }

    private fun observeUsage() {
        scope.launch {
            UsageStore.counters.collect { counters ->
                val state = _uiState.value
                val key = "${state.provider?.id}|${state.model?.id}"
                val counter = counters[key] ?: UsageCounter()
                _uiState.update { it.copy(usage = counter) }
            }
        }
    }

    fun setInput(value: String) {
        _uiState.update { it.copy(input = value, notice = null) }
    }

    fun toggleStreaming() {
        if (!_uiState.value.streamingSupported) return
        _uiState.update { it.copy(useStreaming = !it.useStreaming) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun send() {
        val state = _uiState.value
        if (state.isSending) return
        val text = state.input.trim()
        if (text.isEmpty()) return
        val provider = state.provider ?: return
        val model = state.model ?: return

        if (!provider.enabled) {
            _uiState.update {
                it.copy(error = ChatError(ChatErrorKind.CONFIG, "المزود معطّل — فعّله من شاشة الإعدادات"))
            }
            return
        }
        val last = state.messages.lastOrNull()
        if (last?.role == ChatRole.USER && last.content == text) {
            _uiState.update { it.copy(notice = "نفس الرسالة أُرسلت للتو — لم يُكرَّر الطلب") }
            return
        }

        val apiKey = AiProviderRepository.apiKeyFor(provider)
        val history = state.messages + ChatMessage(role = ChatRole.USER, content = text)
        persist(provider, model, history)
        _uiState.update {
            it.copy(
                messages = history,
                input = "",
                isSending = true,
                error = null,
                notice = null,
                streamingText = "",
            )
        }
        dispatch(provider, model, apiKey, history)
    }

    /** Resends the last user turn manually (never automatic). */
    fun retry() {
        val state = _uiState.value
        if (state.isSending) return
        val provider = state.provider ?: return
        val model = state.model ?: return
        val lastUser = state.messages.lastOrNull { it.role == ChatRole.USER } ?: return
        val apiKey = AiProviderRepository.apiKeyFor(provider)
        _uiState.update {
            it.copy(isSending = true, error = null, streamingText = "")
        }
        dispatch(provider, model, apiKey, state.messages)
    }

    fun cancel() {
        sendJob?.cancel()
        sendJob = null
        _uiState.update {
            it.copy(isSending = false, streamingText = "", notice = "أُلغي الطلب")
        }
    }

    fun clearConversation() {
        val state = _uiState.value
        val provider = state.provider ?: return
        val model = state.model ?: return
        ChatHistoryStore.clear(appContext, provider.id, model.id)
        _uiState.update {
            it.copy(messages = emptyList(), error = null, streamingText = "", notice = "تم مسح المحادثة")
        }
    }

    private fun dispatch(
        provider: AiProviderSpec,
        model: AiModelSpec,
        apiKey: String?,
        history: List<ChatMessage>,
    ) {
        val requestMessages = buildRequestMessages(history)
        val streaming = _uiState.value.useStreaming && AiChatRepository.supportsStreaming(model)

        sendJob = scope.launch {
            try {
                if (streaming) {
                    runStreaming(provider, model, apiKey, requestMessages)
                } else {
                    val outcome = AiChatRepository.send(provider, model, apiKey, requestMessages)
                    finishWithOutcome(provider, model, outcome)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isSending = false,
                        streamingText = "",
                        error = ChatError(
                            ChatErrorKind.NETWORK,
                            "فشل غير متوقع: ${error.javaClass.simpleName}",
                        ),
                    )
                }
            } finally {
                sendJob = null
            }
        }
    }

    private suspend fun runStreaming(
        provider: AiProviderSpec,
        model: AiModelSpec,
        apiKey: String?,
        messages: List<ChatMessage>,
    ) {
        val builder = StringBuilder()
        var failed: ChatError? = null
        var usage: TokenUsage? = null

        AiChatRepository.stream(provider, model, apiKey, messages).collect { event ->
            when (event) {
                is ChatStreamEvent.Delta -> {
                    builder.append(event.text)
                    _uiState.update { it.copy(streamingText = builder.toString()) }
                }

                is ChatStreamEvent.Completed -> usage = event.usage
                is ChatStreamEvent.Failed -> failed = event.error
            }
        }

        val content = builder.toString()
        if (content.isBlank() && failed != null) {
            _uiState.update {
                it.copy(isSending = false, streamingText = "", error = failed)
            }
            return
        }
        val assistant = ChatMessage(
            role = ChatRole.ASSISTANT,
            content = content,
            error = failed != null,
        )
        val updated = _uiState.value.messages + assistant
        persist(provider, model, updated)
        _uiState.update {
            it.copy(
                messages = updated,
                isSending = false,
                streamingText = "",
                error = failed,
                lastUsage = usage,
            )
        }
    }

    private fun finishWithOutcome(
        provider: AiProviderSpec,
        model: AiModelSpec,
        outcome: ChatOutcome,
    ) {
        when (outcome) {
            is ChatOutcome.Success -> {
                val updated = _uiState.value.messages +
                    ChatMessage(role = ChatRole.ASSISTANT, content = outcome.content)
                persist(provider, model, updated)
                _uiState.update {
                    it.copy(
                        messages = updated,
                        isSending = false,
                        error = null,
                        lastUsage = outcome.usage,
                    )
                }
            }

            is ChatOutcome.Failure -> {
                _uiState.update {
                    it.copy(isSending = false, streamingText = "", error = outcome.error)
                }
            }
        }
    }

    private fun buildRequestMessages(history: List<ChatMessage>): List<ChatMessage> {
        val system = ChatMessage(
            role = ChatRole.SYSTEM,
            content = "أنت مساعد داخل تطبيق PhonePilot AI. أجب بالعربية بإيجاز ودقة، " +
                "ولا تدّعِ تنفيذ إجراءات على الجهاز لم تُنفَّذ فعليًا.",
        )
        return listOf(system) + history.filterNot { it.role == ChatRole.SYSTEM }
    }

    private fun persist(
        provider: AiProviderSpec,
        model: AiModelSpec,
        messages: List<ChatMessage>,
    ) {
        ChatHistoryStore.save(
            appContext,
            provider.id,
            model.id,
            messages.filter { it.role != ChatRole.SYSTEM },
        )
    }

    private data class Selection(
        val providers: List<AiProviderSpec>,
        val providerId: String?,
        val modelId: String?,
    )
}
