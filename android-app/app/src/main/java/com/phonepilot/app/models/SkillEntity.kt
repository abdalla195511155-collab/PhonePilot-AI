package com.phonepilot.app.models

/**
 * Contract for a learned skill. The learning engine itself is a Phase 3 concern;
 * no skill instances are fabricated before it exists.
 */
data class SkillEntity(
    val id: String,
    val title: String,
    val description: String,
    val level: String,
    val sourceRecordingId: Long? = null,
    val lifecycleState: SkillLifecycleState = SkillLifecycleState.DRAFT,
    val approvalState: String = "PENDING",
    val approvedAt: Long? = null,
    val approvedByUser: Boolean = false,
    val version: Int = 1,
)
