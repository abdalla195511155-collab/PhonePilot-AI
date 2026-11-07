package com.phonepilot.app.models

/**
 * حالة تنفيذ المهارة (Phase 6 — Intelligent Execution).
 *
 * تسلسل الحالات المعتمد:
 * PENDING → RUNNING → VERIFYING → COMPLETED / FAILED / CANCELLED / NEEDS_USER_ACTION
 *
 * عند حدوث خطأ أثناء التنفيذ، قد تنتقل الحالة إلى FAILED أو تُ.trigger عملية استعادة (Recovery).
 * IDLE و PAUSED و INTERRUPTED محفوظة للتوافق مع الإصدارات الأقدم فقط.
 */
enum class ExecutionState {
    PENDING,
    IDLE,
    RUNNING,
    VERIFYING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
    INTERRUPTED,
    NEEDS_USER_ACTION,
}
