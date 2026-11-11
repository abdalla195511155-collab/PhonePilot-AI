package com.phonepilot.app.testutil

import com.phonepilot.app.models.EventEntity

/** Creates event fixtures with defaults for fields added to EventEntity over time. */
fun eventEntity(
    recordingId: Long = 1L,
    sequenceNumber: Int = 1,
    eventType: String = "click",
    packageName: String? = null,
    className: String? = null,
    viewId: String? = null,
    text: String? = null,
    contentDescription: String? = null,
    bounds: String? = null,
    inputText: String? = null,
    timestamp: Long = 0L,
    beforeSnapshot: String? = null,
    afterSnapshot: String? = null,
    isPrivacyMasked: Boolean = false,
): EventEntity = EventEntity(
    recordingId = recordingId,
    sequenceNumber = sequenceNumber,
    eventType = eventType,
    packageName = packageName,
    className = className,
    viewId = viewId,
    text = text,
    contentDescription = contentDescription,
    bounds = bounds,
    inputText = inputText,
    timestamp = timestamp,
    beforeSnapshot = beforeSnapshot,
    afterSnapshot = afterSnapshot,
    isPrivacyMasked = isPrivacyMasked,
)
