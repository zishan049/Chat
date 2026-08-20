package com.chat.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textMuted: Color,
    val glassHigh: Color,
    val glassMedium: Color,
    val glassLow: Color,
    val glassUltraLow: Color,
    val glassBorder: Color,
    val glassBorderSubtle: Color,
    val glassBorderBright: Color,
    val navBarBackground: Color,
    val drawerBackground: Color,
    val isDark: Boolean
)

val DarkAppColors = AppColors(
    background = Color(0xFF090A0C),
    surface = Color(0xFF111216),
    surfaceElevated = Color(0xFF18191E),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFA1A1AA),
    textTertiary = Color(0xFF71717A),
    textMuted = Color(0xFF52525B),
    glassHigh = Color(0x38FFFFFF),
    glassMedium = Color(0x1FFFFFFF),
    glassLow = Color(0x0EFFFFFF),
    glassUltraLow = Color(0x06FFFFFF),
    glassBorder = Color(0x24FFFFFF),
    glassBorderSubtle = Color(0x14FFFFFF),
    glassBorderBright = Color(0x48FFFFFF),
    navBarBackground = Color(0xF0121318),
    drawerBackground = Color(0xF2101115),
    isDark = true
)

val LightAppColors = AppColors(
    background = Color(0xFFF4F5F8),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFE5E7EB),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textTertiary = Color(0xFF64748B),
    textMuted = Color(0xFF94A3B8),
    glassHigh = Color(0x20000000),
    glassMedium = Color(0x0F000000),
    glassLow = Color(0x08000000),
    glassUltraLow = Color(0x04000000),
    glassBorder = Color(0x15000000),
    glassBorderSubtle = Color(0x0A000000),
    glassBorderBright = Color(0x30000000),
    navBarBackground = Color(0xF0FFFFFF),
    drawerBackground = Color(0xF2FAFAFB),
    isDark = false
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

object AppTheme {
    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current
}

// Fully dynamic theme tokens accessed by all Composables
val AppBackground: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.background

val AppSurface: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.surface

val AppSurfaceElevated: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.surfaceElevated

val AppTextPrimary: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.textPrimary

val AppTextSecondary: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.textSecondary

val AppTextTertiary: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.textTertiary

val AppTextMuted: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.textMuted

val AppGlassHigh: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.glassHigh

val AppGlassMedium: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.glassMedium

val AppGlassLow: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.glassLow

val AppGlassUltraLow: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.glassUltraLow

val AppGlassBorder: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.glassBorder

val AppGlassBorderSubtle: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.glassBorderSubtle

val AppGlassBorderBright: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.glassBorderBright

// Core Tokens (Dynamic)
val BackgroundBlack: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.background

val SurfaceDarkCharcoal: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.surface

val SurfaceDarkElevated: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.surfaceElevated

val GlassWhiteHigh: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.glassHigh

val GlassWhiteMedium: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.glassMedium

val GlassWhiteLow: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.glassLow

val GlassWhiteUltraLow: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.glassUltraLow

val GlassBorder: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.glassBorder

val GlassBorderSubtle: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.glassBorderSubtle

val GlassBorderBright: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.glassBorderBright

val GlassHighlight: Color
    @Composable @ReadOnlyComposable get() = if (LocalAppColors.current.isDark) Color(0x60FFFFFF) else Color(0x20000000)

val TextPrimary: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.textPrimary

val TextSecondary: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.textSecondary

val TextTertiary: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.textTertiary

val TextMuted: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.textMuted

// Accents
val AccentGreen = Color(0xFF22C55E)
val AccentWarning = Color(0xFFEAB308)
val AccentDestructive = Color(0xFFEF4444)
val AccentCyan = Color(0xFF38BDF8)
val AccentBlue = Color(0xFF3B82F6)

// Compatibility Aliases
val PrimaryBlue: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.textPrimary

val PrimaryBlueDark: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.textSecondary

val PrimaryBlueLight: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.surfaceElevated

val AccentEmerald = AccentGreen
val AccentAmber = AccentWarning
val AccentRose = AccentDestructive

val TextPrimaryDark: Color
    @Composable @ReadOnlyComposable get() = DarkAppColors.textPrimary

val TextSecondaryDark: Color
    @Composable @ReadOnlyComposable get() = DarkAppColors.textSecondary

val TextMutedDark: Color
    @Composable @ReadOnlyComposable get() = DarkAppColors.textMuted

val BackgroundLight = Color(0xFFF4F5F8)
val SurfaceLight = Color(0xFFFFFFFF)
val CardLight = Color(0xFFE5E7EB)
val BorderLight = Color(0x15000000)
val TextPrimaryLight = Color(0xFF0F172A)
val TextSecondaryLight = Color(0xFF475569)
val TextMutedLight = Color(0xFF94A3B8)
