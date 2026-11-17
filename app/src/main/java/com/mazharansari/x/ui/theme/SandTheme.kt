package com.mazharansari.x.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * NEW SERIES — "Sand" theme, ported from the Grok design reference
 * (`~/workspace/grok-workspace/src/styles.css`, @theme + .theme-dark).
 *
 * This file is ADDITIVE ONLY: it does not touch [Theme.kt] and nothing calls
 * it yet. The existing dark ink + indigo-violet theme ([DarkPalette] /
 * [LightPalette] in Theme.kt) stays the live theme until the new-series
 * re-skin is switched on deliberately (see THEME-MAPPING.md).
 *
 * Token mapping (Grok CSS name -> Palette field):
 *   sand #F3EEE6       -> bg            sand-deep #E7E0D4 -> page2 / code
 *   paper #FFFCF8      -> surface/card/sheet/popover
 *   ink #1C1916        -> text          muted #746C63 -> text2/text3/dim
 *   line #E4DDD2       -> border/cardBorder
 *   sky #3C8FD4        -> accent/blue   on-sky #F5F9FD -> onAccent
 *   sky-soft #D7EBFA   -> chip          bubble #E8E3DA -> surface2
 *   stop #9C4038       -> danger        stop-soft #F3E4E1 -> (unused; danger tint)
 *
 * Dark theme: the Grok `.theme-dark` block does NOT override sky, on-sky or
 * stop — they keep their light values in dark mode. That is preserved here
 * on purpose (accent #3C8FD4 and danger #9C4038 are identical in both).
 *
 * FONTS — PORTED: Grok uses Fraunces (display serif) + Outfit (sans).
 * Both are bundled under res/font (OFL) and wired in Theme.kt as
 * [Fraunces] / [Outfit]; [SandTheme] uses the same type scale as Theme.kt.
 */
val SandLightPalette = Palette(
    bg = Color(0xFFF3EEE6), surface = Color(0xFFFFFCF8), surface2 = Color(0xFFE8E3DA),
    sheet = Color(0xFFFFFCF8), border = Color(0xFFE4DDD2), text = Color(0xFF1C1916),
    text2 = Color(0xFF746C63), text3 = Color(0xFF746C63), accent = Color(0xFF3C8FD4),
    onAccent = Color(0xFFF5F9FD), code = Color(0xFFE7E0D4), danger = Color(0xFF9C4038),
    isDark = false, chip = Color(0xFFD7EBFA), card = Color(0xFFFFFCF8),
    page2 = Color(0xFFE7E0D4), cardBorder = Color(0xFFE4DDD2), dim = Color(0xFF746C63),
    popover = Color(0xFFFFFCF8), blue = Color(0xFF3C8FD4),
)

val SandDarkPalette = Palette(
    bg = Color(0xFF1C1C1E), surface = Color(0xFF2A2A2C), surface2 = Color(0xFF323234),
    sheet = Color(0xFF2A2A2C), border = Color(0xFF3A3A3C), text = Color(0xFFF4F1EC),
    text2 = Color(0xFFA8A29B), text3 = Color(0xFFA8A29B), accent = Color(0xFF3C8FD4),
    onAccent = Color(0xFFF5F9FD), code = Color(0xFF111113), danger = Color(0xFF9C4038),
    isDark = true, chip = Color(0xFF1D3C52), card = Color(0xFF2A2A2C),
    page2 = Color(0xFF111113), cardBorder = Color(0xFF3A3A3C), dim = Color(0xFFA8A29B),
    popover = Color(0xFF2A2A2C), blue = Color(0xFF3C8FD4),
)

/** Mirrors the Grok reference's Look pref: System | Light | Dark. */
enum class SandLook { System, Light, Dark }

/**
 * Drop-in replacement for [AxionTheme] once the new-series re-skin is
 * switched on. Same contract: provides [LocalPalette] + MaterialTheme.
 * NOT wired into AppRoot yet — see THEME-MAPPING.md.
 */
@Composable
fun SandTheme(look: SandLook = SandLook.System, textScale: Float = 1f, content: @Composable () -> Unit) {
    val dark = when (look) { SandLook.Light -> false; SandLook.Dark -> true; SandLook.System -> isSystemInDarkTheme() }
    val p = if (dark) SandDarkPalette else SandLightPalette
    val scheme = if (dark) darkColorScheme(
        background = p.bg, surface = p.bg, surfaceContainer = p.page2, surfaceContainerLow = p.page2,
        surfaceContainerHigh = p.card, onBackground = p.text, onSurface = p.text,
        primary = p.accent, onPrimary = p.onAccent, outline = p.border, surfaceVariant = p.card,
        onSurfaceVariant = p.text2,
    ) else lightColorScheme(
        background = p.bg, surface = p.bg, surfaceContainer = p.page2, surfaceContainerLow = p.page2,
        surfaceContainerHigh = p.card, onBackground = p.text, onSurface = p.text,
        primary = p.accent, onPrimary = p.onAccent, outline = p.border, surfaceVariant = p.card,
        onSurfaceVariant = p.text2,
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme) { content() }
    }
}
