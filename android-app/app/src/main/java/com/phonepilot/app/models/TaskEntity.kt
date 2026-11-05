package com.phonepilot.app.models

data class TaskEntity(
    val id: Long,
    val name: String,
    val description: String?,
    val createdAt: Long,
    val status: String
)
