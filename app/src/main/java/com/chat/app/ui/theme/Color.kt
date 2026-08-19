package com.chat.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── Grayscale Monochrome Palette (Nothing OS / Graphite Dark Aesthetic) ─────────────
val GrayscaleDarkBg        = Color(0xFF0E1116) // Deep charcoal backdrop
val GrayscaleDarkSurface   = Color(0xFF16191F) // Elevation 1 (Top bars, headers)
val GrayscaleDarkContainer = Color(0xFF22262E) // Squircle containers / input bars
val GrayscaleDarkCard      = Color(0xFF2C303A) // Cards, squircle tiles & buttons

val GrayscaleTextPrimary   = Color(0xFFFFFFFF) // Crisp white
val GrayscaleTextMuted     = Color(0xFF9CA3AF) // Sophisticated silver/gray
val GrayscaleDivider       = Color(0xFF2C303A) // Subtle clean divider

val GrayscaleAccent        = Color(0xFFFFFFFF) // High-contrast pure white
val GrayscaleAccentDark    = Color(0xFF3F4450) // Graphite accent

val GrayscalePositive      = Color(0xFFE2E8F0) // Clean silver online indicator
val GrayscaleDanger        = Color(0xFFEF4444) // Danger red for deletes / block
val GrayscaleWarning       = Color(0xFFF59E0B) // Warning amber

// ── Chat Bubble Colors ───────────────────────────────────────────────────────────
val BubbleSent             = Color(0xFF383C46) // Graphite sent bubble
val BubbleRecv             = Color(0xFF20232A) // Deep charcoal received bubble


// ── Aliases for backwards compatibility across existing components ──────────────
val DiscordBlurple         = GrayscaleAccent
val DiscordBlurpleDark     = GrayscaleAccentDark
val DeepBlack              = GrayscaleDarkBg
val DiscordDarkBase        = GrayscaleDarkBg
val DiscordSurface         = GrayscaleDarkSurface
val DiscordContainer       = GrayscaleDarkContainer
val DiscordCard            = GrayscaleDarkCard
val DiscordTextPrimary     = GrayscaleTextPrimary
val DiscordTextMuted       = GrayscaleTextMuted
val DiscordDivider         = GrayscaleDivider
val DiscordGreen           = GrayscalePositive
val DiscordRed             = GrayscaleDanger
val DiscordYellow          = GrayscaleWarning

// ── Light Theme Monochrome Palette ───────────────────────────────────────────────
val LightBg                = Color(0xFFF3F4F6)
val LightSurface           = Color(0xFFFFFFFF)
val LightContainer         = Color(0xFFE5E7EB)
val LightCard              = Color(0xFFE2E8F0)
val LightTextPrimary       = Color(0xFF111827)
val LightTextMuted         = Color(0xFF6B7280)
val LightDivider           = Color(0xFFE5E7EB)
val LightBubbleSent        = Color(0xFF374151)
val LightBubbleRecv        = Color(0xFFE5E7EB)

