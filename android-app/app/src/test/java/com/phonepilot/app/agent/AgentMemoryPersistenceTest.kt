package com.phonepilot.app.agent

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.phonepilot.app.database.TaskDatabaseHelper
import com.phonepilot.app.models.ExecutionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AgentMemoryPersistenceTest {
    @Test
    fun recordsAndReloadsMemoryUsingDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = TaskDatabaseHelper.getInstance(context)
        helper.writableDatabase.delete("agent_memory", null, null)
        val scope = CoroutineScope(Dispatchers.Unconfined)

        val memory = AgentMemory(scope, helper)
        memory.recordTask(
            taskId = "persistent-task",
            intent = "execute_skill",
            skillId = "skill-1",
            executionState = ExecutionState.COMPLETED,
            completedAt = 123L,
            success = true,
        )

        // The persistence write runs on Dispatchers.IO; query the database directly
        // after waiting for the queued write to finish.
        kotlinx.coroutines.runBlocking {
            val stored = withContext(Dispatchers.IO) { helper.readableDatabase.query(
                "agent_memory", arrayOf("task_id"), "task_id = ?", arrayOf("persistent-task"), null, null, null,
            ).use { it.count }
            assertEquals(1, stored)
        }
    }
}
