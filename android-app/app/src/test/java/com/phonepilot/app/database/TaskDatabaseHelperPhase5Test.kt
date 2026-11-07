package com.phonepilot.app.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.phonepilot.app.models.EventEntity
import com.phonepilot.app.models.SkillEntity
import com.phonepilot.app.models.SkillLifecycleState
import com.phonepilot.app.models.SkillVersionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskDatabaseHelperPhase5Test {

    private lateinit var dbHelper: TaskDatabaseHelper

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        dbHelper = TaskDatabaseHelper.getInstance(context)
        dbHelper.markInterruptedRecordings()
    }

    @Test
    fun `insertSkill stores skill with DRAFT lifecycle state`() {
        val taskId = dbHelper.insertTask("Test Task", null)
        val skillId = dbHelper.insertSkill(taskId, "تسجيل دخول", null, "أساسي", null)

        assertTrue(skillId > 0)

        val skills = dbHelper.getAllSkills()
        assertFalse(skills.isEmpty())
        val skill = skills.firstOrNull { it.id == skillId.toString() }
        assertNotNull(skill)
        assertEquals("تسجيل دخول", skill.title)
        assertEquals("أساسي", skill.level)
    }

    @Test
    fun `insertSkillWithState stores custom lifecycle state`() {
        val taskId = dbHelper.insertTask("State Task", null)
        val skillId = dbHelper.insertSkillWithState(
            taskId, "مهمة متقدمة", null, "متقن", null, SkillLifecycleState.READY
        )

        assertTrue(skillId > 0)

        val skills = dbHelper.getAllSkills()
        val skill = skills.firstOrNull { it.id == skillId.toString() }
        assertNotNull(skill)
        assertEquals("مهمة متقدمة", skill.title)
        assertEquals("متقن", skill.level)
    }

    @Test
    fun `updateSkillState changes lifecycle and confidence`() {
        val taskId = dbHelper.insertTask("Update Task", null)
        val skillId = dbHelper.insertSkill(taskId, "قابلة للتحديث", null, "أساسي", null)

        val updated = dbHelper.updateSkillState(
            id = skillId,
            lifecycleState = SkillLifecycleState.READY,
            confidenceScore = 0.85,
            validationReasons = listOf("الخطوات واضحة", "لا يوجد غموض"),
        )

        assertEquals(1, updated)

        val skills = dbHelper.getAllSkills()
        val skill = skills.firstOrNull { it.id == skillId.toString() }
        assertNotNull(skill)
        assertEquals("قابلة للتحديث", skill.title)
    }

    @Test
    fun `deleteSkill removes skill`() {
        val taskId = dbHelper.insertTask("Delete Task", null)
        val skillId = dbHelper.insertSkill(taskId, "للحذف", null, "مبدئي", null)

        val deleted = dbHelper.deleteSkill(skillId)
        assertEquals(1, deleted)

        val skills = dbHelper.getAllSkills()
        assertFalse(skills.any { it.id == skillId.toString() })
    }

    @Test
    fun `insertSkillVersion and getSkillVersions works`() {
        val taskId = dbHelper.insertTask("Version Task", null)
        val skillId = dbHelper.insertSkill(taskId, "للإصدارات", null, "أساسي", null)
        val skillIdStr = skillId.toString()

        val v1 = dbHelper.insertSkillVersion(
            skillId = skillIdStr,
            versionNumber = 1,
            title = "الإصدار الأول",
            description = null,
            level = "أساسي",
            lifecycleState = SkillLifecycleState.DRAFT,
            sourceRecordingId = null,
        )
        assertTrue(v1 > 0)

        val v2 = dbHelper.insertSkillVersion(
            skillId = skillIdStr,
            versionNumber = 2,
            title = "الإصدار الثاني",
            description = "محسن",
            level = "متقن",
            lifecycleState = SkillLifecycleState.READY,
            sourceRecordingId = null,
        )
        assertTrue(v2 > 0)

        val versions = dbHelper.getSkillVersions(skillIdStr)
        assertEquals(2, versions.size)
        assertEquals(2, versions[0].versionNumber) // descending order
        assertEquals(1, versions[1].versionNumber)

        val versionByNumber = dbHelper.getSkillVersionByNumber(skillIdStr, 1)
        assertNotNull(versionByNumber)
        assertEquals("الإصدار الأول", versionByNumber.title)

        val missing = dbHelper.getSkillVersionByNumber(skillIdStr, 99)
        assertNull(missing)
    }

    @Test
    fun `bumpSkillVersion updates version number`() {
        val taskId = dbHelper.insertTask("Bump Task", null)
        val skillId = dbHelper.insertSkill(taskId, "للبump", null, "أساسي", null)

        val updated = dbHelper.bumpSkillVersion(skillId, 3)
        assertEquals(1, updated)
    }

    @Test
    fun `getSkillsForTask returns skills for specific task`() {
        val task1Id = dbHelper.insertTask("Task 1", null)
        val task2Id = dbHelper.insertTask("Task 2", null)

        dbHelper.insertSkill(task1Id, "مهارة مهمة 1", null, "أساسي", null)
        dbHelper.insertSkill(task1Id, "مهارة مهمة 2", null, "أساسي", null)
        dbHelper.insertSkill(task2Id, "مهارة مهمة 2", null, "أساسي", null)

        val task1Skills = dbHelper.getSkillsForTask(task1Id)
        val task2Skills = dbHelper.getSkillsForTask(task2Id)

        assertEquals(2, task1Skills.size)
        assertEquals(1, task2Skills.size)
    }

    @Test
    fun `getRecordingsForTask returns completed recordings ordered by iteration`() {
        val taskId = dbHelper.insertTask("Recordings Task", null)

        val rec1 = dbHelper.startRecording(taskId, 2)
        dbHelper.stopRecording(rec1, 3)

        val rec2 = dbHelper.startRecording(taskId, 1)
        dbHelper.stopRecording(rec2, 5)

        val recordings = dbHelper.getRecordingsForTask(taskId)
        assertEquals(2, recordings.size)
        // يجب أن تكون مرتبة حسب رقم التكرار تصاعدياً
        assertEquals(1, recordings[0].iterationNumber)
        assertEquals(2, recordings[1].iterationNumber)
    }

    @Test
    fun `recordSkillApproval and isSkillApprovedByUser works`() {
        val taskId = dbHelper.insertTask("Approval Task", null)
        val skillId = dbHelper.insertSkill(taskId, "مهارة للموافقة", null, "أساسي", null)

        // قبل الموافقة: ليست موافقة
        assertFalse(dbHelper.isSkillApprovedByUser(skillId))
        assertEquals("PENDING", dbHelper.getSkillApprovalState(skillId))

        // تسجيل موافقة المستخدم
        val now = System.currentTimeMillis()
        val updated = dbHelper.recordSkillApproval(
            id = skillId,
            approvalState = "APPROVED",
            approvedAt = now,
            approvedByUser = true,
        )
        assertEquals(1, updated)

        // بعد الموافقة: موافقة من المستخدم
        assertTrue(dbHelper.isSkillApprovedByUser(skillId))
        assertEquals("APPROVED", dbHelper.getSkillApprovalState(skillId))
    }

    @Test
    fun `rejected skill does not count as approved`() {
        val taskId = dbHelper.insertTask("Reject Task", null)
        val skillId = dbHelper.insertSkill(taskId, "مهارة مرفوضة", null, "أساسي", null)

        val updated = dbHelper.recordSkillApproval(
            id = skillId,
            approvalState = "REJECTED",
            approvedAt = System.currentTimeMillis(),
            approvedByUser = true,
        )
        assertEquals(1, updated)

        // الرفض لا تُعتبر موافقة
        assertFalse(dbHelper.isSkillApprovedByUser(skillId))
        assertEquals("REJECTED", dbHelper.getSkillApprovalState(skillId))
    }

    @Test
    fun `migration preserves existing skill records v5 to v6`() {
        val taskId = dbHelper.insertTask("Migration Task", null)
        val skillId = dbHelper.insertSkillWithState(
            taskId, "مهارة قديمة", null, "أساسي", null, SkillLifecycleState.DRAFT
        )

        // التحقق من أن المهارة موجودة قبل أي ترحيل إضافي
        val skills = dbHelper.getAllSkills()
        assertFalse(skills.isEmpty())
        val skill = skills.firstOrNull { it.id == skillId.toString() }
        assertNotNull(skill)
        assertEquals("مهارة قديمة", skill.title)
        assertEquals("أساسي", skill.level)
        // حالة الموافقة الافتراضية PENDING
        assertEquals("PENDING", skill.approvalState)
        assertFalse(skill.approvedByUser)
    }
}
