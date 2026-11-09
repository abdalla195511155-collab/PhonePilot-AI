package com.phonepilot.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowInsetsControllerCompat

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
        dynamicColor && darkTheme -> PhonePilotDynamicDarkColorScheme()
        dynamicColor -> PhonePilotDynamicLightColorScheme()
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
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            checkedThumbColor = MaterialTheme.colorScheme.primary,
            uncheckedTrackColor = MaterialTheme.colorScheme.surface,
            checkedTrackColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
fun StatusBarColor(color: Color = MaterialTheme.colorScheme.background) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity ?: return
    val isLight = ColorUtils.calculateLuminance(color.toArgb()) > 0.5
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).isAppearanceLightStatusBars = isLight
    }
    @Suppress("DEPRECATION")
    activity.window.statusBarColor = color.toArgb()
    @Suppress("DEPRECATION")
    activity.window.navigationBarColor = color.toArgb()
}
