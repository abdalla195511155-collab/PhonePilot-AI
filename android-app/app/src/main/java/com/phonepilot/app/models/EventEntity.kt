package com.phonepilot.app.models

data class EventEntity(
    val id: Long = -1,
    val recordingId: Long,
    val sequenceNumber: Int,
    val eventType: String,
    val packageName: String?,
    val className: String?,
    val viewId: String?,
    val text: String?,
    val contentDescription: String?,
    val bounds: String?,
    val inputText: String?,
    val timestamp: Long,
    val beforeSnapshot: String?,
    val afterSnapshot: String?,
    val isPrivacyMasked: Boolean = false
)
