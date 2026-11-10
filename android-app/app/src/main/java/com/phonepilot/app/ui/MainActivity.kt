package com.phonepilot.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.phonepilot.app.database.TaskDatabaseHelper
import com.phonepilot.app.data.SkillRepository
import com.phonepilot.app.data.SettingsRepository
import com.phonepilot.app.data.ai.AiProviderRepository
import com.phonepilot.app.data.ai.UsageStore
import com.phonepilot.app.repository.RecordingRepository
import com.phonepilot.app.ui.theme.PhonePilotTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize local repositories before composing the UI.
        TaskDatabaseHelper.getInstance(this).markInterruptedRecordings()
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
    }
}
