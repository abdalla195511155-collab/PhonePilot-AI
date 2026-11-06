package com.phonepilot.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.LocalContext
import androidx.compose.ui.graphics.Color
import android.view.WindowInsetsController

data class UiThemeState(
    val isDark: Boolean = false,
    val useDynamicColor: Boolean = false,
)

object ThemeRepository {
    var currentTheme by mutableStateOf(UiThemeState())
        private set

    fun setTheme(isDark: Boolean, useDynamicColor: Boolean) {
        currentTheme = UiThemeState(isDark, useDynamicColor)
    }
}

@Composable
fun PhonePilotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && darkTheme -> DarkDynamicColorScheme
        dynamicColor -> LightDynamicColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val typography = PhonePilotTypography

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content,
    )
}

@Composable
fun ThemeSwitcher(onThemeChange: (Boolean) -> Unit) {
    var isDark by remember { mutableStateOf(ThemeRepository.currentTheme.isDark) }
    androidx.compose.material3.Switch(
        checked = isDark,
        onCheckedChange = { checked ->
            isDark = checked
            ThemeRepository.setTheme(checked, ThemeRepository.currentTheme.useDynamicColor)
            onThemeChange(checked)
        },
        colors = androidx.compose.material3.SwitchDefaults.colors(
            uncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            checkedColor = MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = MaterialTheme.colorScheme.surface,
            checkedThumbColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
fun StatusBarColor(color: Color = MaterialTheme.colorScheme.background) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity ?: return
    val controller = activity.window.insetsController
    if (controller != null) {
        controller.isAppearanceLightStatusBars = color.computeLuminance() > 0.5f
    }
    activity.window.statusBarColor = color.toArgb()
    activity.window.navigationBarColor = color.toArgb()
}
