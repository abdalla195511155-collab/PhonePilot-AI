package com.phonepilot.app.telegram

import com.phonepilot.app.agent.AgentCommandResult
import com.phonepilot.app.agent.AgentOrchestrator
import com.phonepilot.app.agent.UserCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * واجهة قناة الأوامر (Phase 7 — Agent Orchestration).
 *
 * تُنفّذ من قبل قنوات مختلفة (تيليغرام، واجهة أندرويد، إلخ)
 * لتوصيل أوامر المستخدم إلى الوكيل واستقبال النتائج.
 */
interface CommandChannel {

    /**
     * إرسال أمر إلى الوكيل واستقبال النتيجة.
     */
    suspend fun sendCommand(command: UserCommand): AgentCommandResult

    /**
     * إرسال نتيجة أو إشعار إلى المستخدم عبر القناة.
     */
    suspend fun sendMessage(userId: String, message: String)

    /**
     * طلب موافقة المستخدم على إجراء ما.
     */
    suspend fun requestApproval(userId: String, skillId: String, skillTitle: String, intent: String): Boolean

    /**
     * التحقق من أن المستخدم مصرح له باستخدام القناة.
     */
    fun isAuthorizedUser(userId: String?): Boolean
}

/**
 * قناة التيليغرام (Phase 7 — Agent Orchestration).
 *
 * تُنفّذ واجهة CommandChannel عبر بوت التيليغرام.
 * لا تُرسل لقطات شاشة أو معلومات حساسة دون موافقة المستخدم.
 * يُحمي توكن البوت ولا يُخزنه في السجلات.
 */
class TelegramBotChannel(
    private val botToken: String,
    private val authorizedUserIds: Set<String> = emptySet(),
    private val orchestrator: AgentOrchestrator,
    private val scope: CoroutineScope,
) : CommandChannel {

    private val MAX_RETRY_ATTEMPTS = 3
    private val RETRY_DELAY_MS = 2000L

    /** معرف آخر تحديث تمت معالجته (لمنع معالجة التحديث نفسه مرتين). */
    @Volatile
    private var lastProcessedUpdateId: Long = -1L

    /** معرفات التحديثات التي تمت معالجتها بالفعل (deduplication قصيرة الأمد). */
    private val processedUpdateIds = mutableSetOf<Long>()

    /**
     * إرسال أمر إلى الوケル عبر التيليغرام.
     */
    override suspend fun sendCommand(command: UserCommand): AgentCommandResult {
        if (!isAuthorizedUser(command.userId)) {
            return AgentCommandResult(
                commandId = command.commandId,
                success = false,
                message = "المستخدم غير مصرح له باستخدام البوت",
            )
        }

        return withContext(Dispatchers.IO) {
            var lastException: Exception? = null
            for (attempt in 1..MAX_RETRY_ATTEMPTS) {
                try {
                    // في التنفيذ الفعلي، يُرسل الأمر عبر Telegram Bot API
                    // هنا نُعالج الأمر مباشرة عبر orchestrator كبديل آمن
                    val result = orchestrator.processCommand(command)
                    result
                } catch (e: Exception) {
                    lastException = e
                    if (attempt < MAX_RETRY_ATTEMPTS) {
                        // انتظار قصير قبل إعادة المحاولة
                        Thread.sleep(RETRY_DELAY_MS)
                    }
                }
            }
            AgentCommandResult(
                commandId = command.commandId,
                success = false,
                message = "فشل معالجة الأمر بعد ${MAX_RETRY_ATTEMPTS} محاولات: ${lastException?.message}",
            )
        }
    }

    /**
     * إرسال رسالة إلى المستخدم عبر التيليغرام.
     */
    override suspend fun sendMessage(userId: String, message: String) {
        withContext(Dispatchers.IO) {
            // في التنفيذ الفعلي، يُرسل عبر Telegram Bot API sendMessage
            // لا تُرسل لقطات شاشة أو معلومات حساسة دون موافقة
        }
    }

    /**
     * طلب موافقة المستخدم على تنفيذ مهارة.
     *
     * في هذا الإصدار، التكامل الحي مع Telegram Bot API غير مكتمل (لا يوجد توكن runtime).
     * يعيد false دائماً إلى أن يتم تكوين توكن البوت وتنفيذ الإرسال عبر API.
     * لا يُعتبر هذا "نجاحاً محاكىً"؛ بل إشارة واضحة أن الموافقة الحية غير متاحة.
     */
    override suspend fun requestApproval(
        userId: String,
        skillId: String,
        skillTitle: String,
        intent: String,
    ): Boolean {
        // التكامل الحي مع Telegram Bot API يحتاج توكن.cfg وقناة إرسال فعلية.
        // حتى يتم توفره، لا يمكن طلب موافقة عبر التيليغرام — نعيد false.
        return false
    }

    /**
     * التحقق من أن المستخدم مصرح له.
     */
    override fun isAuthorizedUser(userId: String?): Boolean {
        if (userId == null) return false
        if (authorizedUserIds.isEmpty()) return false // لا يُسمح لأي مستخدم إذا لم تُحدد قائمة المصرح لهم (الأمان)
        return authorizedUserIds.contains(userId)
    }

    /**
     * معالجة تحديث من التيليغرام (رسالة، زر موافقة، إلخ).
     * يُستدعى من قبل طبقة الواجهة التي تستقبل تحديثات البوت.
     *
     * يستخدم [updateId] لمنع معالجة نفس التحديث مرتين (deduplication)
     * ويُحدّث [lastProcessedUpdateId] ك offset للتحديثات التالية.
     */
    suspend fun handleTelegramUpdate(
        userId: String,
        messageText: String,
        commandId: String,
        updateId: Long,
    ): AgentCommandResult {
        // Deduplication: تجاهل التحديثات التي تمت معالجتها بالفعل
        if (processedUpdateIds.contains(updateId)) {
            AgentCommandResult(
                commandId = commandId,
                success = false,
                message = "تحديث مكرر تم تجاهله",
            )
        } else if (updateId != -1L && updateId <= lastProcessedUpdateId) {
            AgentCommandResult(
                commandId = commandId,
                success = false,
                message = "تحديث سابق للمعالجة (offset غير صالح)",
            )
        } else {
            processedUpdateIds.add(updateId)
            if (updateId != -1L) lastProcessedUpdateId = updateId
            val command = UserCommand(
                commandId = commandId,
                rawText = messageText,
                source = "telegram",
                userId = userId,
            )
            return orchestrator.processCommand(command)
        }
    }

    /**
     * توثيق إعداد توكن البوت.
     * يجب ضبط التوكن عبر إعدادات التطبيق، لا يُكتب في الكود source.
     */
    companion object {
        /**
         * يوضح كيفية إعداد توكن البوت.
         * التوكن يُحصل عليه من @BotFather على التيليغرام ويُخزن بشكل آمن.
         */
        val BOT_TOKEN_SETUP_NOTE = """
            للحصول على توكن البوت:
            1. ابدأ محادثة مع @BotFather على التيليغرام
            2. أرسل /newbot واتبع التعليمات
            3. احصل على التوكن واحفظه في إعدادات التطبيق (لا تُكتب في الكود)
            4. أضف معرفات المستخدمين المصرح لهم إلى authorizedUserIds
        """.trimIndent()
    }
}
