package com.phonepilot.app.data

import android.content.Context
import com.phonepilot.app.database.TaskDatabaseHelper
import com.phonepilot.app.models.SkillEntity
import com.phonepilot.app.models.SkillLifecycleState
import com.phonepilot.app.models.SkillVersionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * مصدر المهارات المتعلّمة (Phase 5 — Learning & Skill Engine).
 *
 * تُPersistence المهارات عبر [TaskDatabaseHelper] وتُعرض عبر [skills] so
 * شاشة [com.phonepilot.app.ui.screens.SkillLibraryScreen] تُعرض المهارات
 * الحقيقية بدلاً من حالة فارغة دائمية.
 *
 * يدعم الآن حالات دورة الحياة، الإصدارات، والتحقق قبل الاعتماد.
 */
object SkillRepository {

    private val _skills = MutableStateFlow<List<SkillEntity>>(emptyList())
    val skills: StateFlow<List<SkillEntity>> = _skills.asStateFlow()

    @Volatile
    private var dbHelper: TaskDatabaseHelper? = null

    /** يربط المستودع بقاعدة البيانات ويحمّل المهارات المخزّنة. يُستدعى مرة واحدة عند بدء التطبيق. */
    fun init(context: Context) {
        if (dbHelper != null) return
        dbHelper = TaskDatabaseHelper.getInstance(context.applicationContext)
        _skills.value = dbHelper!!.getAllSkills()
    }

    /** المحرك متاح الآن بعد تنفيذ مرحلة التعلم (Phase 5). */
    fun isEngineAvailable(): Boolean = true

    /** يضيف مهارة متعلّمة جديدة بحالة DRAFT ويحدث الحالة العرضية. يعيد معرف الصف أو -1 عند الفشل. */
    fun addSkill(
        taskId: Long,
        title: String,
        description: String?,
        level: String,
        sourceRecordingId: Long?,
    ): Long {
        val db = dbHelper ?: return -1
        val id = db.insertSkillWithState(taskId, title, description, level, sourceRecordingId, SkillLifecycleState.DRAFT)
        if (id > 0) {
            _skills.value = db.getAllSkills()
        }
        return id
    }

    /** يعيد تحميل المهارات من قاعدة البيانات. */
    fun refreshSkills() {
        val db = dbHelper ?: return
        _skills.value = db.getAllSkills()
    }

    /** مهارات مهمة معينة (Phase 5). */
    fun getSkillsForTask(taskId: Long): List<SkillEntity> {
        val db = dbHelper ?: return emptyList()
        return db.getSkillsForTask(taskId)
    }

    /** تحديث حالة دورة الحياة والثقة وأسباب التحقق لمهارة (Phase 5). */
    fun updateSkillState(
        id: Long,
        lifecycleState: SkillLifecycleState? = null,
        confidenceScore: Double? = null,
        validationReasons: List<String>? = null,
    ): Int {
        val db = dbHelper ?: return 0
        val updated = db.updateSkillState(id, lifecycleState, confidenceScore, validationReasons)
        if (updated > 0) {
            _skills.value = db.getAllSkills()
        }
        return updated
    }

    /** إنشاء إصدار جديد لمهارة وحفظه في جدول الإصدارات (Phase 5). */
    fun createSkillVersion(
        skillId: String,
        title: String,
        description: String?,
        level: String?,
        lifecycleState: SkillLifecycleState,
        sourceRecordingId: Long?,
    ): Long {
        val db = dbHelper ?: return -1
        val versions = db.getSkillVersions(skillId)
        val nextVersion = if (versions.isEmpty()) 1 else versions.maxOf { it.versionNumber } + 1
        val id = db.insertSkillVersion(
            skillId = skillId,
            versionNumber = nextVersion,
            title = title,
            description = description,
            level = level,
            lifecycleState = lifecycleState,
            sourceRecordingId = sourceRecordingId,
        )
        if (id > 0) {
            // تحديث رقم الإصدار في الجدول الرئيسيّ (المعرّف هو skillId وهو String، نستخدمه كما هو)
            db.bumpSkillVersion(skillId.toLongOrNull() ?: -1, nextVersion)
        }
        return id
    }

    /** جميع إصدارات مهارة معينة مرتبة حسب الإصدار تنازلياً (Phase 5). */
    fun getSkillVersions(skillId: String): List<SkillVersionEntity> {
        val db = dbHelper ?: return emptyList()
        return db.getSkillVersions(skillId)
    }

    /** الإصدار الأحدث من مهارة (Phase 5). */
    fun getLatestSkillVersion(skillId: String): SkillVersionEntity? {
        val db = dbHelper ?: return null
        val versions = db.getSkillVersions(skillId)
        return versions.firstOrNull()
    }

    /** التراجع إلى إصدار سابق لمهارة (Phase 5). يعيد true عند النجاح. */
    fun rollbackToVersion(skillId: String, versionNumber: Int): Boolean {
        val db = dbHelper ?: return false
        val version = db.getSkillVersionByNumber(skillId, versionNumber) ?: return false
        val updated = db.updateSkillState(
            id = skillId.toLongOrNull() ?: -1,
            lifecycleState = version.lifecycleState,
            confidenceScore = null,
            validationReasons = null,
        )
        if (updated > 0) {
            _skills.value = db.getAllSkills()
        }
        return updated > 0
    }

    /** تسجيل قرار موافقة المستخدم على مهارة (Phase 5).
     * يحفظ حالة الموافقة (APPROVED/REJECTED)، توقيت القرار، وهل القرار من المستخدم أم لا.
     * يعيد عدد الصفوف المحدثة. */
    fun recordSkillApproval(
        skillId: String,
        approvalState: String,
        approvedAt: Long?,
        approvedByUser: Boolean,
    ): Int {
        val db = dbHelper ?: return 0
        val id = skillId.toLongOrNull() ?: return 0
        val updated = db.recordSkillApproval(id, approvalState, approvedAt, approvedByUser)
        if (updated > 0) {
            _skills.value = db.getAllSkills()
        }
        return updated
    }

    /** إرجاع حالة الموافقة لمهارة معينة (Phase 5). */
    fun getSkillApprovalState(skillId: String): String? {
        val db = dbHelper ?: return null
        val id = skillId.toLongOrNull() ?: return null
        return db.getSkillApprovalState(id)
    }

    /** التحقق من أن مهارة ما موافقة من المستخدم وجاهزة للاعتماد (Phase 5). */
    fun isSkillApprovedByUser(skillId: String): Boolean {
        val db = dbHelper ?: return false
        val id = skillId.toLongOrNull() ?: return false
        return db.isSkillApprovedByUser(id)
    }
}
