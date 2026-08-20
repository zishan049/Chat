package com.chat.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val MonochromeDarkColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = DarkAppColors.background,
    primaryContainer = DarkAppColors.surfaceElevated,
    onPrimaryContainer = DarkAppColors.textPrimary,
    secondary = AccentGreen,
    onSecondary = DarkAppColors.background,
    background = DarkAppColors.background,
    onBackground = DarkAppColors.textPrimary,
    surface = DarkAppColors.surface,
    onSurface = DarkAppColors.textPrimary,
    surfaceVariant = DarkAppColors.surfaceElevated,
    onSurfaceVariant = DarkAppColors.textSecondary,
    outline = DarkAppColors.glassBorder,
    error = AccentDestructive
)

private val MonochromeLightColorScheme = lightColorScheme(
    primary = LightAppColors.textPrimary,
    onPrimary = Color.White,
    primaryContainer = LightAppColors.surfaceElevated,
    onPrimaryContainer = LightAppColors.textPrimary,
    secondary = AccentGreen,
    onSecondary = Color.White,
    background = LightAppColors.background,
    onBackground = LightAppColors.textPrimary,
    surface = LightAppColors.surface,
    onSurface = LightAppColors.textPrimary,
    surfaceVariant = LightAppColors.surfaceElevated,
    onSurfaceVariant = LightAppColors.textSecondary,
    outline = LightAppColors.glassBorder,
    error = AccentDestructive
)

@Composable
fun ChatTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MonochromeDarkColorScheme else MonochromeLightColorScheme
    val appColors = if (darkTheme) DarkAppColors else LightAppColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val statusBarColor = if (darkTheme) DarkAppColors.background else LightAppColors.background
            window.statusBarColor = statusBarColor.toArgb()
            window.navigationBarColor = statusBarColor.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalAppColors provides appColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

