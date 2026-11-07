package com.phonepilot.app.data.ai.chat

import android.content.Context
import com.google.gson.Gson
import com.phonepilot.app.data.ai.SecureKeyStore

/**
 * Encrypted local chat history.
 *
 * The JSON is stored through [SecureKeyStore] (Android Keystore AES/GCM), so a
 * conversation is not readable as plain text even on a rooted device. History is
 * bounded and per provider+model.
 */
object ChatHistoryStore {

    private const val MAX_MESSAGES = 40
    private val gson = Gson()

    private fun key(providerId: String, modelId: String) = "chat_history|$providerId|$modelId"

    fun load(context: Context, providerId: String, modelId: String): List<ChatMessage> {
        val raw = SecureKeyStore.get(context, key(providerId, modelId)) ?: return emptyList()
        val dto = runCatching {
            gson.fromJson(raw, Array<StoredMessage>::class.java)
        }.getOrNull() ?: return emptyList()
        return dto.mapNotNull { stored ->
            val content = stored.content ?: return@mapNotNull null
            val role = runCatching { ChatRole.valueOf(stored.role ?: "") }.getOrNull()
                ?: return@mapNotNull null
            ChatMessage(
                role = role,
                content = content,
                error = stored.error == true,
            )
        }
    }

    fun save(
        context: Context,
        providerId: String,
        modelId: String,
        messages: List<ChatMessage>,
    ) {
        val trimmed = messages.takeLast(MAX_MESSAGES).map { message ->
            StoredMessage(
                role = message.role.name,
                content = message.content,
                error = message.error,
            )
        }
        runCatching {
            SecureKeyStore.put(context, key(providerId, modelId), gson.toJson(trimmed))
        }
    }

    fun clear(context: Context, providerId: String, modelId: String) {
        SecureKeyStore.remove(context, key(providerId, modelId))
    }

    private data class StoredMessage(
        val role: String? = null,
        val content: String? = null,
        val error: Boolean? = null,
    )
}
