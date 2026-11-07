package com.phonepilot.app.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramBotChannelTest {

    @Test
    fun `isAuthorizedUserReturnsFalseForNull`() {
        val channel = TelegramBotChannel(
            botToken = "test",
            authorizedUserIds = setOf("user1"),
            orchestrator = null!!,
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        )
        assertFalse(channel.isAuthorizedUser(null))
    }

    @Test
    fun `isAuthorizedUserReturnsFalseForUnauthorized`() {
        val channel = TelegramBotChannel(
            botToken = "test",
            authorizedUserIds = setOf("user1"),
            orchestrator = null!!,
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        )
        assertFalse(channel.isAuthorizedUser("user2"))
    }

    @Test
    fun `isAuthorizedUserReturnsTrueForAuthorized`() {
        val channel = TelegramBotChannel(
            botToken = "test",
            authorizedUserIds = setOf("user1"),
            orchestrator = null!!,
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        )
        assertTrue(channel.isAuthorizedUser("user1"))
    }

    @Test
    fun `isAuthorizedUserReturnsTrueWhenNoAuthorizedList`() {
        val channel = TelegramBotChannel(
            botToken = "test",
            authorizedUserIds = emptySet(),
            orchestrator = null!!,
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        )
        // When no authorized users are specified, all users are allowed (for development)
        assertTrue(channel.isAuthorizedUser("any-user"))
    }

    @Test
    fun `botTokenValidationNotesNotEmpty`() {
        assertFalse(TelegramBotChannel.BOT_TOKEN_SETUP_NOTE.isEmpty())
        assertTrue(TelegramBotChannel.BOT_TOKEN_SETUP_NOTE.contains("@BotFather"))
    }
}
