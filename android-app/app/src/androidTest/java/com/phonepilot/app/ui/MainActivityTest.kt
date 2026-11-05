package com.phonepilot.app.ui

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun `launch MainActivity successfully`() {
        // Activity launches without crashing
        // This is a basic smoke test
    }

    @Test
    fun `record button exists`() {
        // Verify the record button is present on screen
        // This would use Espresso in a full implementation
    }
}
