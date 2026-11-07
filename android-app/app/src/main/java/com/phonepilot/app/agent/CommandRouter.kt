package com.phonepilot.app.agent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * موجه الأوامر (Phase 7 — Agent Orchestration).
 *
 * يوجه الأوامر من قنوات مختلفة (تيليغرام، واجهة أندرويد) إلى orchestration
 * ويتعامل مع إلغاء الأوامر والأوامر المكررة.
 *
 * جميع عمليات القراءة والكتابة متزامنة (synchronized) لمنع Race Condition عند
 * وصول أوامر متزامنة من قنوات متعددة (تيليغرام وواجهة أندرويد).
 */
class CommandRouter(
    private val scope: CoroutineScope,
) {

    private val _pendingCommands = MutableStateFlow(mutableMapOf<String, UserCommand>())
    val pendingCommands: StateFlow<Map<String, UserCommand>> = _pendingCommands.asStateFlow()

    private val _executedCommands = MutableStateFlow(mutableSetOf<String>())
    val executedCommands: StateFlow<Set<String>> = _executedCommands.asStateFlow()

    /**
     * يسجل أمراً جديداً ويطالب بالتحقق من التكرار.
     *
     * يعيد true إذا كان الأمر جديداً ويمكن معالجته، false إذا كان مكرراً.
     * العملية ذرية: التحقق والتحديث يحدثان ضمن قسم حاسم واحد.
     */
    fun registerCommand(command: UserCommand): Boolean = synchronized(this) {
        if (_executedCommands.value.contains(command.commandId)) {
            false // أمر مكرر - لا تُنفذ مرة أخرى
        } else if (_pendingCommands.value.containsKey(command.commandId)) {
            false // أمر قيد المعالجة بالفعل
        } else {
            _pendingCommands.value = _pendingCommands.value + (command.commandId to command)
            true
        }
    }

    /**
     * يُマーク أمراً كمُنفذ.
     */
    fun markCommandExecuted(commandId: String) = synchronized(this) {
        _pendingCommands.value = _pendingCommands.value - commandId
        _executedCommands.value = _executedCommands.value + commandId
    }

    /**
     * يلغي أمراً معلقاً.
     */
    fun cancelCommand(commandId: String): Boolean = synchronized(this) {
        val wasPending = _pendingCommands.value.containsKey(commandId)
        if (wasPending) {
            _pendingCommands.value = _pendingCommands.value - commandId
        }
        wasPending
    }

    /**
     * التحقق من أن الأمر يمكن إلغاؤه.
     */
    fun isCancellable(commandId: String): Boolean = synchronized(this) {
        _pendingCommands.value.containsKey(commandId)
    }

    /**
     * جلب 명령 معلق بمعرفه.
     */
    fun getPendingCommand(commandId: String): UserCommand? = synchronized(this) {
        _pendingCommands.value[commandId]
    }
}
