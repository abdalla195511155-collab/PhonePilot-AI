package com.phonepilot.app.models

data class TaskRecordingEntity(
    val id: Long,
    val taskId: Long,
    val iterationNumber: Int,
    val recordedAt: Long,
    val rawEvents: String
)
