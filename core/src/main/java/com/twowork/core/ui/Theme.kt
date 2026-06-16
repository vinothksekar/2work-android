package com.twowork.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Blue = Color(0xFF365BFF)
val BlueDark = Color(0xFF2344D1)
val Navy = Color(0xFF0C1229)
val Ink = Color(0xFF10182D)
val Muted = Color(0xFF66728B)
val Gold = Color(0xFFF5AC45)
val Green = Color(0xFF087D64)
val Danger = Color(0xFFBD3F4F)

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E8FF),
    onPrimaryContainer = BlueDark,
    secondary = Gold,
    onSecondary = Ink,
    secondaryContainer = Color(0xFFFFF2DA),
    onSecondaryContainer = Color(0xFF5A4410),
    tertiary = Green,
    background = Color(0xFFF4F7FC),
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEAF0FB),
    onSurfaceVariant = Muted,
    error = Danger,
    outline = Color(0xFFC9D4E7)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB2FF),
    onPrimary = Navy,
    primaryContainer = Color(0xFF20336E),
    onPrimaryContainer = Color(0xFFDDE4FF),
    secondary = Gold,
    onSecondary = Ink,
    background = Color(0xFF0B0F1E),
    onBackground = Color(0xFFE6EAF3),
    surface = Color(0xFF131A2E),
    onSurface = Color(0xFFE6EAF3),
    surfaceVariant = Color(0xFF26304A),
    onSurfaceVariant = Color(0xFFAAB6CE),
    error = Color(0xFFF2B8B5),
    outline = Color(0xFF3A4664)
)

@Composable
fun TwoWorkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
