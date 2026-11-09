package com.phonepilot.app.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.phonepilot.app.database.TaskDatabaseHelper
import com.phonepilot.app.data.SkillRepository
import com.phonepilot.app.data.SettingsRepository
import com.phonepilot.app.data.ai.AiProviderRepository
import com.phonepilot.app.recording.PhonePilotAccessibilityService
import com.phonepilot.app.repository.RecordingRepository
import com.phonepilot.app.ui.navigation.AppNavigation
import com.phonepilot.app.ui.theme.PhonePilotTheme
import com.phonepilot.app.data.ai.UsageStore

class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Preserve existing recording recovery behavior
        TaskDatabaseHelper.getInstance(this).markInterruptedRecordings()
        // The repository must be usable from the UI even before the accessibility
        // service connects; init() only wires the database helper.
        RecordingRepository.init(applicationContext)
        SettingsRepository.init(applicationContext)
        SkillRepository.init(applicationContext)
        AiProviderRepository.init(applicationContext)
        UsageStore.init(applicationContext)

        setContent {
            PhonePilotTheme(
                darkTheme = isSystemInDarkTheme(),
                dynamicColor = false,
            ) {
                var selectedRoute by remember { mutableStateOf("dashboard") }

                com.phonepilot.app.ui.components.AppScaffold(
                    title = null,
                ) {
                    com.phonepilot.app.ui.navigation.AppNavigation(
                        onRouteChange = { route ->
                            selectedRoute = route
                        },
                    )
                }
            }
        }

        // Preserve existing accessibility service check behavior
        if (!PhonePilotAccessibilityService.isServiceEnabled(this)) {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: Exception) {
                Log.e(TAG, "Cannot open accessibility settings", e)
            }
        }
    }
}
