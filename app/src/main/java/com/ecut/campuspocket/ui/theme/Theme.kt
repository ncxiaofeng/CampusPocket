package com.ecut.campuspocket.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = TealOnPrimary,
    primaryContainer = TealContainer,
    onPrimaryContainer = TealOnContainer,
    secondary = SlateSecondary,
    onSecondary = Color.White,
    secondaryContainer = SlateContainer,
    onSecondaryContainer = SlateOnContainer,
    tertiary = BlueTertiary,
    tertiaryContainer = BlueContainer,
    onTertiaryContainer = Color(0xFF160E67),
    background = AppBackground,
    onBackground = TextPrimary,
    surface = SurfaceLight,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFE1EFEC),
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF6F817D),
    outlineVariant = OutlineSoft,
    error = ErrorRed,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = TealDark,
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF115E56),
    onPrimaryContainer = TealContainer,
    secondary = Color(0xFFB9D2EA),
    onSecondary = Color(0xFF123149),
    secondaryContainer = Color(0xFF2A4357),
    onSecondaryContainer = Color(0xFFD7E8F7),
    tertiary = Color(0xFFC5C4FF),
    tertiaryContainer = Color(0xFF3A3A85),
    onTertiaryContainer = Color(0xFFE0E0FF),
    background = AppBackgroundDark,
    onBackground = Color(0xFFE0EAE7),
    surface = SurfaceDark,
    onSurface = Color(0xFFE0EAE7),
    surfaceVariant = Color(0xFF263634),
    onSurfaceVariant = Color(0xFFB6C8C3),
    outline = Color(0xFF849691),
    outlineVariant = Color(0xFF2C3E3A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun CampusPocketTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme && Build.VERSION.SDK_INT >= 26
            }
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = CampusTypography,
        shapes = AppShapes,
        content = content
    )
}
