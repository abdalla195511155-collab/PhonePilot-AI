package com.phonepilot.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.phonepilot.app.database.TaskDatabaseHelper

/**
 * Returns a TaskDatabaseHelper bound to the current Robolectric app. The production
 * helper is a process-wide singleton, so without a reset a later test would keep a
 * connection to an earlier test's (already deleted) database file.
 */
object TestDatabase {
    fun fresh(): TaskDatabaseHelper {
        val field = TaskDatabaseHelper::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        (field.get(null) as? TaskDatabaseHelper)?.close()
        field.set(null, null)
        val context: Context = ApplicationProvider.getApplicationContext()
        return TaskDatabaseHelper.getInstance(context)
    }
}
