package com.phonepilot.app.ui.theme

import androidx.compose.material3.Color
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

private val darkPrimary = Color(0xFFBB86FC)
private val darkSecondary = Color(0xFF03DAC6)
private val darkSurface = Color(0xFF121212)
private val darkBackground = Color(0xFF0A0A0A)
private val darkOnSurface = Color(0xFFE0E0E0)
private val darkOnBackground = Color(0xFFFFFFFF)
private val darkPrimaryContainer = Color(0xFF3700B3)
private val darkError = Color(0xFFCF6679)

private val lightPrimary = Color(0xFF6200EE)
private val lightSecondary = Color(0xFF03DAC6)
private val lightSurface = Color(0xFFFFFFFF)
private val lightBackground = Color(0xFFF5F5F5)
private val lightOnSurface = Color(0xFF1A1A1A)
private val lightOnBackground = Color(0xFF000000)
private val lightPrimaryContainer = Color(0xFFE8EAF6)
private val lightError = Color(0xFFB3261E)

val DarkColorScheme = darkColorScheme(
    primary = darkPrimary,
    secondary = darkSecondary,
    surface = darkSurface,
    background = darkBackground,
    onSurface = darkOnSurface,
    onBackground = darkOnBackground,
    primaryContainer = darkPrimaryContainer,
    error = darkError,
    onError = Color.Black,
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFC0C0C0),
    outline = Color(0xFF333333),
    inverseSurface = Color(0xFF333333),
    inverseOnSurface = Color(0xFFBBBBBB),
    inversePrimary = Color(0xFF6200EE),
)

val LightColorScheme = lightColorScheme(
    primary = lightPrimary,
    secondary = lightSecondary,
    surface = lightSurface,
    background = lightBackground,
    onSurface = lightOnSurface,
    onBackground = lightOnBackground,
    primaryContainer = lightPrimaryContainer,
    error = lightError,
    onError = Color.White,
    surfaceVariant = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFF4A4A4A),
    outline = Color(0xFFBDBDBD),
    inverseSurface = Color(0xFF333333),
    inverseOnSurface = Color(0xFFEEEEEE),
    inversePrimary = Color(0xFFBB86FC),
)

val DarkDynamicColorScheme = dynamicDarkColorScheme()
val LightDynamicColorScheme = dynamicLightColorScheme()
