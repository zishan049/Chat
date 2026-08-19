package com.chat.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class AppColors(
    val bg: Color,
    val surface: Color,
    val container: Color,
    val card: Color,
    val txt: Color,
    val muted: Color,
    val divider: Color,
    val accent: Color,
    val accentDark: Color,
    val positive: Color,
    val danger: Color,
    val warning: Color,
    val bubbleSent: Color,
    val bubbleRecv: Color,
)

val LocalAppColors = staticCompositionLocalOf {
    AppColors(
        bg = GrayscaleDarkBg,
        surface = GrayscaleDarkSurface,
        container = GrayscaleDarkContainer,
        card = GrayscaleDarkCard,
        txt = GrayscaleTextPrimary,
        muted = GrayscaleTextMuted,
        divider = GrayscaleDivider,
        accent = GrayscaleAccent,
        accentDark = GrayscaleAccentDark,
        positive = GrayscalePositive,
        danger = GrayscaleDanger,
        warning = GrayscaleWarning,
        bubbleSent = BubbleSent,
        bubbleRecv = BubbleRecv,
    )
}

@Composable
fun ChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        AppColors(
            bg = GrayscaleDarkBg,
            surface = GrayscaleDarkSurface,
            container = GrayscaleDarkContainer,
            card = GrayscaleDarkCard,
            txt = GrayscaleTextPrimary,
            muted = GrayscaleTextMuted,
            divider = GrayscaleDivider,
            accent = GrayscaleAccent,
            accentDark = GrayscaleAccentDark,
            positive = GrayscalePositive,
            danger = GrayscaleDanger,
            warning = GrayscaleWarning,
            bubbleSent = BubbleSent,
            bubbleRecv = BubbleRecv,
        )
    } else {
        AppColors(
            bg = LightBg,
            surface = LightSurface,
            container = LightContainer,
            card = LightCard,
            txt = LightTextPrimary,
            muted = LightTextMuted,
            divider = LightDivider,
            accent = LightTextPrimary,
            accentDark = LightCard,
            positive = GrayscalePositive,
            danger = GrayscaleDanger,
            warning = GrayscaleWarning,
            bubbleSent = LightBubbleSent,
            bubbleRecv = LightBubbleRecv,
        )
    }

    val darkColorScheme = darkColorScheme(
        primary = colors.accent,
        onPrimary = Color.White,
        background = colors.bg,
        onBackground = colors.txt,
        surface = colors.surface,
        onSurface = colors.txt,
        surfaceVariant = colors.card,
        outline = colors.divider,
    )

    val lightColorScheme = lightColorScheme(
        primary = colors.accent,
        onPrimary = Color.White,
        background = colors.bg,
        onBackground = colors.txt,
        surface = colors.surface,
        onSurface = colors.txt,
        surfaceVariant = colors.card,
        outline = colors.divider,
    )

    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as android.app.Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalAppColors provides colors
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkColorScheme else lightColorScheme,
            content = content
        )
    }
}

/** Shorthand for accessing modern Discord/Telegram app colors anywhere in composables. */
val appColors: AppColors
    @Composable get() = LocalAppColors.current
