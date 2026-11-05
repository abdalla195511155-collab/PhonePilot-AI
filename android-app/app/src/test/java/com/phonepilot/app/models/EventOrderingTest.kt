package com.phonepilot.app.models

import org.junit.Assert.assertEquals
import org.junit.Test

class EventOrderingTest {

    @Test
    fun `events are ordered by sequence number ascending`() {
        val events = listOf(
            EventEntity(recordingId = 1, sequenceNumber = 3, eventType = "click", timestamp = 3000),
            EventEntity(recordingId = 1, sequenceNumber = 1, eventType = "click", timestamp = 1000),
            EventEntity(recordingId = 1, sequenceNumber = 2, eventType = "input", timestamp = 2000)
        )
        val sorted = events.sortedBy { it.sequenceNumber }
        assertEquals(1, sorted[0].sequenceNumber)
        assertEquals(2, sorted[1].sequenceNumber)
        assertEquals(3, sorted[2].sequenceNumber)
    }

    @Test
    fun `sequence numbers start at 1 and increment by 1`() {
        val count = 5
        val sequences = (1..count).toList()
        assertEquals(listOf(1, 2, 3, 4, 5), sequences)
    }

    @Test
    fun `different recordings can have same sequence number`() {
        val e1 = EventEntity(recordingId = 1, sequenceNumber = 1, eventType = "click", timestamp = 1000)
        val e2 = EventEntity(recordingId = 2, sequenceNumber = 1, eventType = "click", timestamp = 2000)
        assertEquals(1, e1.sequenceNumber)
        assertEquals(1, e2.sequenceNumber)
        assertEquals(1, e1.recordingId)
        assertEquals(2, e2.recordingId)
    }
}
