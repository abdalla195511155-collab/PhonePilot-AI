package com.phonepilot.app.data.ai.chat

import com.phonepilot.app.data.ai.AiModelSpec
import com.phonepilot.app.data.ai.AiProviderSpec

/** Minimal abstraction for callers that need to inject chat behavior. */
interface ChatRepository {
    suspend fun send(
        provider: AiProviderSpec,
        model: AiModelSpec,
        apiKey: String?,
        messages: List<ChatMessage>,
    ): ChatOutcome
}
