package com.phonepilot.app.repository

import android.content.Context
import com.phonepilot.app.database.TaskDatabaseHelper
import com.phonepilot.app.models.EventEntity
import com.phonepilot.app.models.RecordingState
import com.phonepilot.app.privacy.PrivacyFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

object RecordingRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    /** عدد الأحداث المقبولة للقناة (تم إسنادها للمستهلك) */
    private val _acceptedCount = MutableStateFlow(0)
    val acceptedCount: StateFlow<Int> = _acceptedCount.asStateFlow()

    /** عدد الأحداث التي أُدخلت حديثاً إلى SQLite (rowId > 0) */
    private val _newlyInsertedCount = MutableStateFlow(0)
    val newlyInsertedCount: StateFlow<Int> = _newlyInsertedCount.asStateFlow()

    /** عدد الأحداث المكررة التي تم التحقق من محتواها وتمت المطابقة (Verified Duplicate) */
    private val _verifiedDuplicateCount = MutableStateFlow(0)
    val verifiedDuplicateCount: StateFlow<Int> = _verifiedDuplicateCount.asStateFlow()

    /** عدد الأحداث التي فشلت في الحفظ (Failed) */
    private val _failedCount = MutableStateFlow(0)
    val failedCount: StateFlow<Int> = _failedCount.asStateFlow()

    /** عدد الأحداث التي تم التحقق من حفظها نهائياً (newlyInserted + verifiedDuplicate) */
    private val _verifiedPersistedCount = MutableStateFlow(0)
    val verifiedPersistedCount: StateFlow<Int> = _verifiedPersistedCount.asStateFlow()

    private val _droppedCount = MutableStateFlow(0)
    val droppedCount: StateFlow<Int> = _droppedCount.asStateFlow()

    private val _serviceConnected = MutableStateFlow(false)
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    private var dbHelper: TaskDatabaseHelper? = null
    private val writeMutex = Mutex()
    private val acceptMutex = Mutex()

    private var currentRecordingId: Long? = null
    private var eventChannel: Channel<EventEntity>? = null
    private var consumerDeferred: Deferred<ConsumerResult>? = null
    private var currentSequence = 0
    @Volatile private var isStopping = false
    @Volatile private var interruptFailed = false
    @Volatile private var pendingRecovery = false
    @Volatile private var recoveryType: RecoveryType = RecoveryType.NONE
    @Volatile private var lastDrainResult: DrainResult = DrainResult.NotStarted
    fun nextSequence(): Int = ++currentSequence
    fun resetSequence() { currentSequence = 0 }

    enum class RecoveryType { NONE, STOP, INTERRUPT }

    fun init(context: Context) {
        dbHelper = TaskDatabaseHelper.getInstance(context)
    }

    private val db: TaskDatabaseHelper
        get() = dbHelper ?: throw IllegalStateException("RecordingRepository not initialized")

    fun isInitialized(): Boolean = dbHelper != null

    fun isRecording(): Boolean = _recordingState.value is RecordingState.Recording

    fun getCurrentRecordingId(): Long? = currentRecordingId

    val hasInterruptFailure: Boolean get() = interruptFailed
    val hasPendingRecovery: Boolean get() = pendingRecovery
    val lastDrainResultValue: DrainResult get() = lastDrainResult

    /**
     * Explicit drain result — distinguishes outcomes so callers know
     * whether all accepted events were persisted.
     */
    sealed class DrainResult {
        object NotStarted : DrainResult()
        object DrainCompleted : DrainResult()
        object DrainCancelled : DrainResult()  // Structured cancellation from parent scope
        data class ConsumerFailed(val cause: Throwable?) : DrainResult()
        data class DrainTimedOut(val savedCount: Int, val acceptedCount: Int) : DrainResult()
        data class DbUpdateFailed(val cause: Throwable?) : DrainResult()
        data class DrainPartial(val savedCount: Int, val acceptedCount: Int) : DrainResult()
    }

    /**
     * Result of persisting a single event.
     * NEW: row inserted (rowId > 0)
     * DUPLICATE: row already existed with same (recording_id, sequence_number) AND matching content
     * FAILED: insert failed or content mismatch
     */
    enum class PersistResult { NEW, DUPLICATE, FAILED }

    /**
     * Explicit consumer result — distinguishes outcomes so drain coordinator
     * knows whether all accepted events were persisted. Job.join() alone is
     * NOT sufficient because it does not propagate exceptions.
     */
    sealed class ConsumerResult {
        /** جميع الأحداث المقبولة لها نتيجة حفظ مؤكدة */
        data class Success(
            val newlyInserted: Int,
            val verifiedDuplicate: Int,
            val failed: Int,
            val verifiedPersisted: Int  // newlyInserted + verifiedDuplicate
        ) : ConsumerResult()
        data class Failure(val cause: Throwable?) : ConsumerResult()
        data class Cancelled(
            val newlyInserted: Int,
            val verifiedDuplicate: Int,
            val failed: Int,
            val verifiedPersisted: Int
        ) : ConsumerResult()
    }

    /**
     * Start a new recording session. Creates a DB row first and only announces
     * Recording after the row is successfully created.
     * Returns true on success.
     */
    fun startRecording(taskId: Long, iterationNumber: Int): Boolean {
        if (!acceptMutex.tryLock()) return false
        try {
            if (isStopping || interruptFailed || pendingRecovery || currentRecordingId != null) return false
            val recordingId = db.startRecording(taskId, iterationNumber)
            if (recordingId <= 0) return false

            currentRecordingId = recordingId
            currentSequence = 0
            _recordingState.value = RecordingState.Recording(taskId, recordingId, iterationNumber, 0)
            _acceptedCount.value = 0
            _newlyInsertedCount.value = 0
            _verifiedDuplicateCount.value = 0
            _failedCount.value = 0
            _verifiedPersistedCount.value = 0
            _droppedCount.value = 0
            interruptFailed = false
            pendingRecovery = false
            recoveryType = RecoveryType.NONE
            lastDrainResult = DrainResult.NotStarted

            val channel = Channel<EventEntity>(64)
            eventChannel = channel
            consumerDeferred = scope.async {
                var newlyInserted = 0
                var verifiedDuplicate = 0
                var failed = 0
                var verifiedPersisted = 0
                try {
                    for (event in channel) {
                        val result = persistEvent(event)
                        when (result) {
                            PersistResult.NEW -> {
                                newlyInserted++
                                verifiedPersisted++
                            }
                            PersistResult.DUPLICATE -> {
                                verifiedDuplicate++
                                verifiedPersisted++
                            }
                            PersistResult.FAILED -> failed++
                        }
                    }
                    ConsumerResult.Success(newlyInserted, verifiedDuplicate, failed, verifiedPersisted)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // structured cancellation — carry current counts so drain coordinator knows
                    throw e
                } catch (e: Exception) {
                    ConsumerResult.Failure(e)
                }
            }
            return true
        } finally {
            acceptMutex.unlock()
        }
    }

    /**
     * Stop the current recording session. Drains pending events, then marks the
     * DB row as COMPLETED. Only announces Completed after success.
     */
    suspend fun stopRecording(): Boolean {
        val current = _recordingState.value
        if (current !is RecordingState.Recording) return false

        val recordingId = current.recordingId

        // Atomic: prevent re-entry and set isStopping + close channel
        var startedStop = false
        acceptMutex.withLock {
            if (isStopping) return false
            isStopping = true
            eventChannel?.close()
            startedStop = true
        }

        if (!startedStop) return false

        // Wait for consumer to drain (persistEvent still saves because state is Recording)
        var drainResult: DrainResult = DrainResult.DrainCompleted
        try {
            withTimeout(5000L) {
                val result = consumerDeferred?.await()
                if (result is ConsumerResult.Failure) {
                    throw result.cause ?: Exception("Consumer failed")
                }
                if (result is ConsumerResult.Cancelled) {
                    drainResult = DrainResult.DrainCancelled
                }
            }
        } catch (e: TimeoutCancellationException) {
            // Timeout from withTimeout — consumer didn't complete in time
            try { consumerDeferred?.cancel() } catch (_: Exception) { }
            drainResult = DrainResult.DrainTimedOut(_verifiedPersistedCount.value, _acceptedCount.value)
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Structured cancellation from parent scope — not consumer failure
            // Preserve session state, save recovery info to DB, then rethrow
            try { consumerDeferred?.cancel() } catch (_: Exception) { }
            isStopping = false
            drainResult = DrainResult.DrainCancelled
            lastDrainResult = drainResult
            // Save persistent recovery state — session unresolved
            interruptFailed = true
            pendingRecovery = true
            recoveryType = RecoveryType.STOP
            try {
                db.markRecordingFailed(recordingId, _acceptedCount.value, _verifiedPersistedCount.value, "cancellation")
            } catch (dbEx: Exception) {
                // DB save failed — in-memory flags preserved for recoverSession()
            }
            // Do NOT clear currentRecordingId — session unresolved
            // Do NOT declare DrainCompleted
            throw e
        } catch (e: Exception) {
            try { consumerDeferred?.cancel() } catch (_: Exception) { }
            drainResult = DrainResult.ConsumerFailed(e)
        }

        val verifiedPersisted = _verifiedPersistedCount.value
        val accepted = _acceptedCount.value

        // DrainCompleted requires: every accepted event has a verified persisted outcome
        // verifiedPersisted = newlyInserted + verifiedDuplicate
        // If verifiedPersisted < accepted → some accepted events were lost → DrainPartial
        if (drainResult is DrainResult.DrainCompleted && verifiedPersisted < accepted) {
            drainResult = DrainResult.DrainPartial(verifiedPersisted, accepted)
        }

        lastDrainResult = drainResult

        return when (drainResult) {
            is DrainResult.DrainCompleted -> {
                try {
                    db.stopRecording(recordingId, verifiedPersisted)
                    // Success — announce completion only after DB confirms
                    _recordingState.value = RecordingState.Completed
                    _acceptedCount.value = 0
                    _newlyInsertedCount.value = 0
                    _verifiedDuplicateCount.value = 0
                    _failedCount.value = 0
                    _verifiedPersistedCount.value = 0
                    currentRecordingId = null
                    eventChannel = null
                    consumerDeferred = null
                    isStopping = false
                    interruptFailed = false
                    pendingRecovery = false
                    recoveryType = RecoveryType.NONE
                    true
                } catch (e: Exception) {
                    // DB failed — do not announce success; preserve session
                    isStopping = false
                    interruptFailed = true
                    pendingRecovery = true
                    recoveryType = RecoveryType.STOP
                    // Do NOT clear currentRecordingId — session unresolved
                    false
                }
            }
            is DrainResult.ConsumerFailed,
            is DrainResult.DrainTimedOut,
            is DrainResult.DrainPartial,
            is DrainResult.DrainCancelled,
            is DrainResult.NotStarted -> {
                // Drain incomplete — keep state as Recording
                isStopping = false
                interruptFailed = true
                pendingRecovery = true
                recoveryType = RecoveryType.STOP
                // Do NOT clear currentRecordingId — session unresolved
                false
            }
            is DrainResult.DbUpdateFailed -> {
                isStopping = false
                interruptFailed = true
                pendingRecovery = true
                recoveryType = RecoveryType.STOP
                false
            }
        }
    }

    fun serviceConnected() {
        _serviceConnected.value = true
    }

    /**
     * Called when the accessibility service is interrupted or unbound.
     * All channel operations go through acceptMutex — no close outside the lock.
     * Drain is performed asynchronously with explicit result tracking.
     * Guaranteed retry: uses withLock (suspends) so it waits for lock availability.
     */
    fun serviceInterrupted() {
        _serviceConnected.value = false
        scope.launch {
            var startedInterrupt = false
            var interruptRecordingId: Long? = null
            var interruptTaskId: Long? = null

            acceptMutex.withLock {
                val current = _recordingState.value
                if (current !is RecordingState.Recording) {
                    // Not recording — cleanup only
                    eventChannel?.close()
                    eventChannel = null
                    consumerDeferred = null
                    isStopping = false
                    interruptFailed = false
                    pendingRecovery = false
                    recoveryType = RecoveryType.NONE
                    lastDrainResult = DrainResult.NotStarted
                    return@withLock
                }

                if (isStopping || interruptFailed || pendingRecovery) {
                    // Already in progress — ensure channel closed, don't start new drain
                    eventChannel?.close()
                    return@withLock
                }

                isStopping = true
                eventChannel?.close()
                startedInterrupt = true
                interruptRecordingId = current.recordingId
                interruptTaskId = current.taskId
            }

            if (!startedInterrupt) return@launch
            val recordingId = interruptRecordingId ?: return@launch
            val taskId = interruptTaskId ?: return@launch

            // Async drain + DB update (outside lock so await() doesn't block enqueue/stop)
            var drainResult: DrainResult = DrainResult.DrainCompleted
            try {
                withTimeout(5000L) {
                    val result = consumerDeferred?.await()
                    if (result is ConsumerResult.Failure) {
                        throw result.cause ?: Exception("Consumer failed")
                    }
                    if (result is ConsumerResult.Cancelled) {
                    drainResult = DrainResult.DrainCancelled
                }
            }
        } catch (e: TimeoutCancellationException) {
            try { consumerDeferred?.cancel() } catch (_: Exception) { }
            drainResult = DrainResult.DrainTimedOut(_verifiedPersistedCount.value, _acceptedCount.value)
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Structured cancellation from parent scope — preserve session state,
            // clean up safely, then rethrow. Do NOT declare DrainCompleted.
            try { consumerDeferred?.cancel() } catch (_: Exception) { }
            isStopping = false
            drainResult = DrainResult.DrainCancelled
            lastDrainResult = drainResult
            // Save persistent recovery state — session unresolved
            interruptFailed = true
            pendingRecovery = true
            recoveryType = RecoveryType.INTERRUPT
            try {
                db.markRecordingFailed(recordingId, _acceptedCount.value, _verifiedPersistedCount.value, "cancellation")
            } catch (dbEx: Exception) {
                // DB save failed — in-memory flags preserved for recoverSession()
            }
            // Do NOT clear currentRecordingId — session unresolved
            // Do NOT declare DrainCompleted
            throw e
        } catch (e: Exception) {
                try { consumerDeferred?.cancel() } catch (_: Exception) { }
                drainResult = DrainResult.ConsumerFailed(e)
            }

            val verifiedPersisted = _verifiedPersistedCount.value
            val accepted = _acceptedCount.value

            if (drainResult is DrainResult.DrainCompleted && verifiedPersisted < accepted) {
                drainResult = DrainResult.DrainPartial(verifiedPersisted, accepted)
            }

            lastDrainResult = drainResult

            // DB update under lock
            acceptMutex.withLock {
                val currentNow = _recordingState.value
                if (currentNow !is RecordingState.Recording) return@withLock

                when (drainResult) {
                    is DrainResult.DrainCompleted -> {
                        try {
                            db.interruptRecording(recordingId, accepted, verifiedPersisted)
                            _recordingState.value = RecordingState.Interrupted(taskId, recordingId, verifiedPersisted)
                            interruptFailed = false
                            pendingRecovery = false
                            recoveryType = RecoveryType.NONE
                            // Safe to clear — all events persisted, DB updated
                            currentRecordingId = null
                            eventChannel = null
                            consumerDeferred = null
                            isStopping = false
                        } catch (e: Exception) {
                            // DB failed — keep state as Recording, mark failure
                            // Do NOT clear currentRecordingId — session unresolved
                            interruptFailed = true
                            pendingRecovery = true
                            recoveryType = RecoveryType.INTERRUPT
                            isStopping = false
                        }
                    }
                    is DrainResult.ConsumerFailed,
                    is DrainResult.DrainTimedOut,
                    is DrainResult.DrainPartial,
                    is DrainResult.DrainCancelled,
                    is DrainResult.NotStarted -> {
                        // Drain incomplete — keep state as Recording
                        // Do NOT clear currentRecordingId — session unresolved
                        interruptFailed = true
                        pendingRecovery = true
                        recoveryType = RecoveryType.INTERRUPT
                        isStopping = false
                    }
                    is DrainResult.DbUpdateFailed -> {
                        interruptFailed = true
                        pendingRecovery = true
                        recoveryType = RecoveryType.INTERRUPT
                        isStopping = false
                    }
                }
            }
        }
    }

    fun incrementDroppedCount() {
        _droppedCount.update { it + 1 }
    }

    /**
     * Enqueue an event for persistence. Returns true if accepted.
     * The recordingId is taken from the current session, not from the event.
     */
    fun tryEnqueueEvent(event: EventEntity): Boolean {
        if (!acceptMutex.tryLock()) return false
        try {
            if (_recordingState.value !is RecordingState.Recording) return false
            if (isStopping) return false
            if (interruptFailed) return false
            if (pendingRecovery) return false
            val channel = eventChannel ?: return false
            val result = channel.trySend(event)
            if (result.isClosed) {
                return false
            }
            if (result.isFailure) {
                incrementDroppedCount()
                return false
            }
            // Track accepted count ONLY after successful enqueue
            _acceptedCount.update { it + 1 }
            return true
        } finally {
            acceptMutex.unlock()
        }
    }

    private suspend fun persistEvent(event: EventEntity): PersistResult {
        val recordingId = currentRecordingId ?: return PersistResult.FAILED
        val masked = PrivacyFilter.maskEventIfNeeded(event)
        val maskedEvent = masked.copy(recordingId = recordingId)

        return writeMutex.withLock {
            val current = _recordingState.value
            if (current !is RecordingState.Recording) return@withLock PersistResult.FAILED
            if (current.recordingId != recordingId) return@withLock PersistResult.FAILED

            try {
                val rowId = db.insertEvent(recordingId, maskedEvent)
                if (rowId > 0) {
                    // rowId > 0 means newly inserted
                    _newlyInsertedCount.update { it + 1 }
                    _verifiedPersistedCount.update { it + 1 }
                    _recordingState.update {
                        if (it is RecordingState.Recording) {
                            RecordingState.Recording(
                                it.taskId,
                                it.recordingId,
                                it.iterationNumber,
                                _newlyInsertedCount.value + _verifiedDuplicateCount.value
                            )
                        } else it
                    }
                    PersistResult.NEW
                } else {
                    // rowId == -1: CONFLICT_IGNORE — verify event actually exists AND content matches
                    // Compare maskedEvent (what was stored) against stored row, not original event
                    val contentMatches = db.eventContentMatches(
                        recordingId,
                        maskedEvent.sequenceNumber,
                        maskedEvent.eventType,
                        maskedEvent.packageName,
                        maskedEvent.className,
                        maskedEvent.viewId,
                        maskedEvent.text,
                        maskedEvent.contentDescription,
                        maskedEvent.bounds,
                        maskedEvent.inputText,
                        maskedEvent.timestamp
                    )
                    if (contentMatches) {
                        // Already persisted with same content — Verified Duplicate
                        _verifiedDuplicateCount.update { it + 1 }
                        _verifiedPersistedCount.update { it + 1 }
                        PersistResult.DUPLICATE
                    } else {
                        // Same (recording_id, sequence_number) but different content — Conflict / Failure
                        _failedCount.update { it + 1 }
                        PersistResult.FAILED
                    }
                }
            } catch (e: Exception) {
                // DB error — do not increment count, propagate to consumer
                _failedCount.update { it + 1 }
                throw e
            }
        }
    }

    fun getAcceptedCount(): Int = _acceptedCount.value
    fun getNewlyInsertedCount(): Int = _newlyInsertedCount.value
    fun getVerifiedDuplicateCount(): Int = _verifiedDuplicateCount.value
    fun getFailedCount(): Int = _failedCount.value
    fun getVerifiedPersistedCount(): Int = _verifiedPersistedCount.value
    fun getDroppedCount(): Int = _droppedCount.value
    fun isServiceConnected(): Boolean = _serviceConnected.value

    /**
     * Recovery: retry an unresolved session.
     * - For STOP failure: retry db.stopRecording() with counts
     * - For INTERRUPT failure: retry db.interruptRecording() with counts
     * Does NOT re-drain (consumer already done or cancelled).
     * Prevents duplicate inserts via unique index + INSERT OR IGNORE.
     * Returns true only if DB update succeeds AND all accepted events persisted.
     */
    fun recoverSession(): Boolean {
        if (!acceptMutex.tryLock()) return false
        try {
            if (!interruptFailed && !pendingRecovery) return false
            val recordingId = currentRecordingId ?: return false
            val current = _recordingState.value
            if (current !is RecordingState.Recording) return false

            val verifiedPersisted = _verifiedPersistedCount.value
            val acceptedCount = _acceptedCount.value

            // If not all accepted events were persisted, recovery is partial
            if (verifiedPersisted < acceptedCount) {
                // Partial data loss — cannot fully recover
                // Events in closed channel are lost (in-memory only, not persisted)
                // User must acknowledge failure via acknowledgeFailure() or abandonSession()
                return false
            }

            return try {
                when (recoveryType) {
                    RecoveryType.STOP -> db.stopRecording(recordingId, verifiedPersisted)
                    RecoveryType.INTERRUPT -> db.interruptRecording(recordingId, acceptedCount, verifiedPersisted)
                    RecoveryType.NONE -> return false
                }
                // Success
                when (recoveryType) {
                    RecoveryType.STOP -> _recordingState.value = RecordingState.Completed
                    RecoveryType.INTERRUPT -> _recordingState.value = RecordingState.Interrupted(
                        current.taskId, recordingId, verifiedPersisted
                    )
                    RecoveryType.NONE -> { }
                }
                interruptFailed = false
                pendingRecovery = false
                recoveryType = RecoveryType.NONE
                currentRecordingId = null
                eventChannel = null
                consumerDeferred = null
                isStopping = false
                _acceptedCount.value = 0
                _newlyInsertedCount.value = 0
                _verifiedDuplicateCount.value = 0
                _failedCount.value = 0
                _verifiedPersistedCount.value = 0
                true
            } catch (e: Exception) {
                false
            }
        } finally {
            acceptMutex.unlock()
        }
    }

    /**
     * User acknowledgement of a failed session.
     * Does NOT reset session data — only acknowledges the failure.
     * Session remains unresolved until recoverSession() succeeds or
     * abandonSession() is explicitly called.
     * Does NOT change state to Idle, does NOT clear currentRecordingId,
     * does NOT reset counters.
     */
    fun acknowledgeFailure() {
        // No-op marker — acknowledgement is tracked by the user calling
        // recoverSession() or abandonSession().
        // This method exists for API completeness and future UI integration.
    }

    /**
     * Abandon a failed session — persists ABANDONED status to DB first.
     * Only clears in-memory session references AFTER DB operation succeeds.
     * If DB operation fails, session is preserved and new recording blocked.
     */
    fun abandonSession(): Boolean {
        if (!acceptMutex.tryLock()) return false
        try {
            val recordingId = currentRecordingId ?: return false
            val current = _recordingState.value
            if (current !is RecordingState.Recording) return false

            val verifiedPersisted = _verifiedPersistedCount.value
            val acceptedCount = _acceptedCount.value

            return try {
                db.abandonRecording(recordingId, acceptedCount, verifiedPersisted)
                // DB succeeded — now safe to clear in-memory state
                interruptFailed = false
                pendingRecovery = false
                recoveryType = RecoveryType.NONE
                _recordingState.value = RecordingState.Idle
                currentRecordingId = null
                eventChannel = null
                consumerDeferred = null
                isStopping = false
                _acceptedCount.value = 0
                _newlyInsertedCount.value = 0
                _verifiedDuplicateCount.value = 0
                _failedCount.value = 0
                _verifiedPersistedCount.value = 0
                true
            } catch (e: Exception) {
                // DB failed — preserve session, block new recording
                false
            }
        } finally {
            acceptMutex.unlock()
        }
    }

    fun getAllRecordings() = db.getAllRecordings()
    fun getEventsForRecording(recordingId: Long) = db.getEventsForRecording(recordingId)

    fun shutdown() {
        scope.cancel()
    }
}
