package com.example.expressapp.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFFF4B400),
    onPrimary = Color(0xFF242000),
    primaryContainer = Color(0xFFFFE27A),
    onPrimaryContainer = Color(0xFF241A00),
    secondary = Color(0xFF3467D6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE7FF),
    onSecondaryContainer = Color(0xFF071B3E),
    tertiary = Color(0xFFD9564A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDAD5),
    onTertiaryContainer = Color(0xFF3B0805),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE2E2E6),
    onSurfaceVariant = Color(0xFF45464B),
    surfaceContainer = Color(0xFFEFF0F3),
    surfaceContainerHigh = Color(0xFFE7E8EC),
    outline = Color(0xFF76777D),
    outlineVariant = Color(0xFFC6C6CC)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFCA28),
    onPrimary = Color(0xFF3B2F00),
    primaryContainer = Color(0xFF564500),
    onPrimaryContainer = Color(0xFFFFE27A),
    secondary = Color(0xFFB4C8FF),
    onSecondary = Color(0xFF002B73),
    secondaryContainer = Color(0xFF17458F),
    onSecondaryContainer = Color(0xFFDCE7FF),
    tertiary = Color(0xFFFFB4AA),
    onTertiary = Color(0xFF690F0B),
    tertiaryContainer = Color(0xFF8B2C25),
    onTertiaryContainer = Color(0xFFFFDAD5),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    background = Color(0xFF111315),
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF191B1E),
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF45464B),
    onSurfaceVariant = Color(0xFFC6C6CC),
    surfaceContainer = Color(0xFF202225),
    surfaceContainerHigh = Color(0xFF2A2C2F),
    outline = Color(0xFF909197),
    outlineVariant = Color(0xFF45464B)
)

private val ExpressiveTypography = Typography().copy(
    displayMedium = TextStyle(fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontSize = 25.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp)
)

@Composable
fun ExpressTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ExpressiveTypography,
        content = content
    )
}

val ExpressiveCardShape = RoundedCornerShape(
    topStart = 24.dp,
    topEnd = 24.dp,
    bottomStart = 24.dp,
    bottomEnd = 8.dp
)

val ExpressivePanelShape = RoundedCornerShape(
    topStart = 28.dp,
    topEnd = 28.dp,
    bottomStart = 12.dp,
    bottomEnd = 28.dp
)

val ExpressivePillShape = RoundedCornerShape(50)
