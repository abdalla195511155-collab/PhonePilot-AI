package com.phonepilot.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.material3.TextStyle
import androidx.compose.ui.text.TextWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp

val PhonePilotTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 32.sp,
        fontWeight = TextWeight.Bold,
        fontStyle = FontStyle.Normal,
        letterSpacing = -0.25.sp,
        lineHeight = 40.sp,
        color = androidx.compose.material3.Color.Unspecified,
    ),
    displayMedium = TextStyle(
        fontSize = 28.sp,
        fontWeight = TextWeight.Bold,
        letterSpacing = 0.sp,
        lineHeight = 36.sp,
    ),
    displaySmall = TextStyle(
        fontSize = 24.sp,
        fontWeight = TextWeight.Bold,
        letterSpacing = 0.sp,
        lineHeight = 32.sp,
    ),
    headlineLarge = TextStyle(
        fontSize = 22.sp,
        fontWeight = TextWeight.Bold,
        letterSpacing = 0.sp,
        lineHeight = 28.sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 20.sp,
        fontWeight = TextWeight.SemiBold,
        letterSpacing = 0.sp,
        lineHeight = 26.sp,
    ),
    headlineSmall = TextStyle(
        fontSize = 18.sp,
        fontWeight = TextWeight.SemiBold,
        letterSpacing = 0.sp,
        lineHeight = 24.sp,
    ),
    titleLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = TextWeight.Bold,
        letterSpacing = 0.1.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = TextWeight.Medium,
        letterSpacing = 0.1.sp,
        lineHeight = 20.sp,
    ),
    titleSmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = TextWeight.Medium,
        letterSpacing = 0.2.sp,
        lineHeight = 16.sp,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = TextWeight.Normal,
        letterSpacing = 0.5.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = TextWeight.Normal,
        letterSpacing = 0.25.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = TextWeight.Normal,
        letterSpacing = 0.4.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        fontWeight = TextWeight.Medium,
        letterSpacing = 0.1.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = TextWeight.Medium,
        letterSpacing = 0.5.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontSize = 10.sp,
        fontWeight = TextWeight.Medium,
        letterSpacing = 0.5.sp,
        lineHeight = 12.sp,
    ),
)
