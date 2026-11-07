package com.phonepilot.app.data.ai

import com.phonepilot.app.data.ai.chat.ChatError
import com.phonepilot.app.data.ai.chat.ChatErrorKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class UsageStoreTest {

    @Before
    fun resetBefore() = UsageStore.resetUsage()

    @After
    fun resetAfter() = UsageStore.resetUsage()

    private fun provider(id: String = "p1", limit: RateLimitSpec? = null) = AiProviderSpec(
        id = id,
        name = "Provider $id",
        kind = ProviderKind.CUSTOM,
        baseUrl = "https://api.example.com/v1",
        protocol = AiProtocol.OPENAI_COMPATIBLE,
        accountRateLimit = limit,
        hasApiKey = true,
    )

    @Test
    fun `no declared limit means no blocking and no invented 200 per hour`() {
        val target = provider()
        repeat(250) {
            assertNull(UsageStore.guard(target))
            UsageStore.recordAttempt(target.id, "m1")
        }
        assertNull(UsageStore.guard(target))
    }

    @Test
    fun `declared limit is enforced inside its window`() {
        val limit = RateLimitSpec(
            requests = 2,
            window = LimitWindow.HOUR,
            scope = LimitScope.PROVIDER,
            source = LimitSource.USER_DEFINED,
        )
        val target = provider(limit = limit)
        val now = 10_000L

        assertNull(UsageStore.guard(target, now))
        UsageStore.recordAttempt(target.id, "m1", now)
        assertNull(UsageStore.guard(target, now))
        UsageStore.recordAttempt(target.id, "m1", now)

        val blocked = UsageStore.guard(target, now + 1)
        assertNotNull(blocked)
        assertEquals(2, limit.requests)
    }

    @Test
    fun `limits are isolated per provider`() {
        val limit = RateLimitSpec(
            requests = 1,
            window = LimitWindow.HOUR,
            scope = LimitScope.PROVIDER,
            source = LimitSource.USER_DEFINED,
        )
        val first = provider("p1", limit)
        val second = provider("p2", limit)

        UsageStore.recordAttempt(first.id, "m1", 1_000L)
        assertNotNull(UsageStore.guard(first, 2_000L))
        assertNull(UsageStore.guard(second, 2_000L))
    }

    @Test
    fun `429 cooldown is respected then expires`() {
        val target = provider()
        UsageStore.recordFailure(
            providerId = target.id,
            modelId = "m1",
            error = ChatError(
                kind = ChatErrorKind.RATE_LIMIT,
                userMessage = "429",
                httpStatus = 429,
                retryAfterSeconds = 10,
            ),
            now = 1_000L,
        )

        val during = UsageStore.guard(target, 1_500L)
        assertNotNull(during)

        assertNull(UsageStore.guard(target, 12_000L))
    }

    @Test
    fun `counters track success and failure`() {
        UsageStore.recordAttempt("p1", "m1", 1_000L)
        UsageStore.recordSuccess("p1", "m1", 2_000L)
        UsageStore.recordFailure(
            "p1",
            "m1",
            ChatError(ChatErrorKind.SERVER, "boom", httpStatus = 503),
            3_000L,
        )

        val counter = UsageStore.counterFor("p1", "m1")
        assertEquals(1, counter.success)
        assertEquals(1, counter.failure)
        assertEquals(503, counter.lastStatus)
        assertEquals(3_000L, counter.lastRequestAtEpochMs)
    }
}
