package com.ecut.campuspocket.capture.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF263238)
val InkSoft = Color(0xFF546E7A)
val Paper = Color(0xFFF5F7F9)
val Accent = Color(0xFF00695C)
val AccentSoft = Color(0xFFB2DFDB)
val Danger = Color(0xFFBA1A1A)
val Warning = Color(0xFF8A5300)

private val Light = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentSoft,
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF37474F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFD8DC),
    onSecondaryContainer = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE6EEEC),
    onSurfaceVariant = InkSoft,
    outlineVariant = Color(0xFFDDE3E2),
    error = Danger
)

private val Dark = darkColorScheme(
    primary = Color(0xFF80CBC4),
    onPrimary = Color(0xFF00201C),
    primaryContainer = Color(0xFF005049),
    onPrimaryContainer = AccentSoft,
    background = Color(0xFF0F1413),
    onBackground = Color(0xFFDDE4E2),
    surface = Color(0xFF171D1C),
    onSurface = Color(0xFFDDE4E2),
    surfaceVariant = Color(0xFF3F4947),
    onSurfaceVariant = Color(0xFFBEC9C6),
    outlineVariant = Color(0xFF2A3230),
    error = Color(0xFFFFB4AB)
)

@Composable
fun CaptureTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content
    )
}
