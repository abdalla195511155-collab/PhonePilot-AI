package com.phonepilot.app.models

data class EventEntity(
    val id: Long = -1,
    val recordingId: Long,
    val sequenceNumber: Int,
    val eventType: String,
    val packageName: String? = null,
    val className: String? = null,
    val viewId: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val bounds: String? = null,
    val inputText: String? = null,
    val timestamp: Long,
    val beforeSnapshot: String? = null,
    val afterSnapshot: String? = null,
    val isPrivacyMasked: Boolean = false
)
