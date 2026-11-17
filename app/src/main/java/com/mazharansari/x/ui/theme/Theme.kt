package com.mazharansari.x.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.mazharansari.x.R

/**
 * Dots design tokens — ported from OpenDots (CopilotKit/OpenDots, MIT) and
 * OpenFuse (CopilotKit/openfuse, MIT, the former OpenMuse). The DESIGN is
 * reimplemented here in Compose; no web code was copied.
 *
 * Light (OpenDots `src/client/style.css` + `theme.ts`):
 *  bg       #F8F7F4  warm off-white page    surface  #FFFFFF  white cards / composer
 *  surface2 #EEEFFA  lavender soft          sheet    #FFFFFF  sheets / popovers
 *  border   #E9E8E6  hairline               sidebar  #FBFAF8  drawer
 *  text     #333641  ink                    text2    #8C8D97  muted
 *  accent   #7285D5  periwinkle             teal     #35766F  user bubble
 *  send     #8292D6  send button (chat: #75B9B4 teal)
 *  sky      #EDF7FD  greenTint #E3F3E8  lavenderTint #F0EEFA  orangeTint #FDF0DF
 *  status: done #7A967E · running #788BC3 · failed #B17D73 · paused #AA9776
 *  danger   #AA4A45 (OpenFuse)              blue     #1473C8 (OpenFuse)
 * Dark: OpenDots barely specifies dark (`:root[data-theme='dark']` only sets
 * color-scheme; theme-color #1B1B1B), so the dark ramp is derived from the
 * same tokens: bg #1B1B1B, surface/card #242424, periwinkle lightened to
 * #8292D6, teal lightened to #75B9B4.
 *
 * Type is the system sans throughout (OpenDots: system sans 14px, headings
 * weight 550, letter-spacing −0.04em; OpenFuse: title 23/600/−0.7, heading
 * 16/600/−0.25, text 15/23, muted 14/21, small 11/17, label 10/700/1.4).
 * Radii: 9px controls, 13px composer/cards, 17px bubbles, 20px chips,
 * 24px buttons, 26px bottom sheets.
 */
@Immutable
data class Palette(
    val bg: Color, val surface: Color, val surface2: Color, val sheet: Color, val border: Color,
    val text: Color, val text2: Color, val text3: Color, val accent: Color, val onAccent: Color,
    val code: Color, val danger: Color, val isDark: Boolean,
    val chip: Color = surface2, val card: Color = surface, val page2: Color = sheet, val cardBorder: Color = border,
    val dim: Color = text3, val popover: Color = sheet, val blue: Color = Color(0xFF1473C8),
    // Dots tokens (defaulted so older constructions keep compiling).
    val teal: Color = Color(0xFF35766F),
    val accentSoft: Color = Color(0xFFEEEFFA),
    val sky: Color = Color(0xFFEDF7FD),
    val greenTint: Color = Color(0xFFE3F3E8),
    val lavenderTint: Color = Color(0xFFF0EEFA),
    val orangeTint: Color = Color(0xFFFDF0DF),
    val statusDone: Color = Color(0xFF7A967E),
    val statusRunning: Color = Color(0xFF788BC3),
    val statusFailed: Color = Color(0xFFB17D73),
    val statusPaused: Color = Color(0xFFAA9776),
    val sidebar: Color = Color(0xFFFBFAF8),
    val send: Color = Color(0xFF8292D6),
)

val DarkPalette = Palette(
    bg = Color(0xFF1B1B1B), surface = Color(0xFF242424), surface2 = Color(0xFF2C2C2E), sheet = Color(0xFF242424),
    border = Color(0xFF38383B), text = Color(0xFFF2F1EE), text2 = Color(0xFFA9AAB2), text3 = Color(0xFF7C7D85),
    accent = Color(0xFF8292D6), onAccent = Color(0xFFFFFFFF), code = Color(0xFF111113), danger = Color(0xFFD98A84), isDark = true,
    chip = Color(0xFF2C2C2E), card = Color(0xFF242424), page2 = Color(0xFF1B1B1B), cardBorder = Color(0xFF38383B),
    dim = Color(0xFF7C7D85), popover = Color(0xFF262626), blue = Color(0xFF7FB8E8),
    teal = Color(0xFF75B9B4), accentSoft = Color(0xFF2A2D45), sky = Color(0xFF1E2E3A),
    greenTint = Color(0xFF223528), lavenderTint = Color(0xFF2A2D45), orangeTint = Color(0xFF3A2E1E),
    statusDone = Color(0xFF8FB394), statusRunning = Color(0xFF93A5D8), statusFailed = Color(0xFFC99A90),
    statusPaused = Color(0xFFBFAE8E), sidebar = Color(0xFF1E1E1E), send = Color(0xFF8292D6),
)
val LightPalette = Palette(
    bg = Color(0xFFF8F7F4), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFEEEFFA), sheet = Color(0xFFFFFFFF),
    border = Color(0xFFE9E8E6), text = Color(0xFF333641), text2 = Color(0xFF8C8D97), text3 = Color(0xFFA9AAB2),
    accent = Color(0xFF7285D5), onAccent = Color(0xFFFFFFFF), code = Color(0xFF17181C), danger = Color(0xFFAA4A45), isDark = false,
    chip = Color(0xFFF1F2F3), card = Color(0xFFFFFFFF), page2 = Color(0xFFF8F7F4), cardBorder = Color(0xFFE9E8E6),
    dim = Color(0xFF8C8D97), popover = Color(0xFFFFFFFF), blue = Color(0xFF1473C8),
    teal = Color(0xFF35766F), accentSoft = Color(0xFFEEEFFA), sky = Color(0xFFEDF7FD),
    greenTint = Color(0xFFE3F3E8), lavenderTint = Color(0xFFF0EEFA), orangeTint = Color(0xFFFDF0DF),
    statusDone = Color(0xFF7A967E), statusRunning = Color(0xFF788BC3), statusFailed = Color(0xFFB17D73),
    statusPaused = Color(0xFFAA9776), sidebar = Color(0xFFFBFAF8), send = Color(0xFF8292D6),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }
val P: Palette @Composable @ReadOnlyComposable get() = LocalPalette.current

@OptIn(ExperimentalTextApi::class)
private fun code(w: Int) = Font(R.font.google_sans_code, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

val GoogleSansCode = FontFamily(code(300), code(400), code(500), code(600), code(700))

/** System sans throughout (OpenDots/OpenFuse use the system font stack, never a bundled display face). */
object Type {
    val display = TextStyle(fontFamily = FontFamily.Default, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.7).sp)
    val body = TextStyle(fontFamily = FontFamily.Default, fontSize = 15.sp, lineHeight = 23.sp, letterSpacing = (-0.25).sp)
    val small = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.sp)
    val tiny = TextStyle(fontFamily = FontFamily.Default, fontSize = 11.sp, lineHeight = 17.sp, letterSpacing = 0.sp)
    val label = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp)
    val row = TextStyle(fontFamily = FontFamily.Default, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.25).sp)
    val title = TextStyle(fontFamily = FontFamily.Default, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp)
    val header = TextStyle(fontFamily = FontFamily.Default, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp)
    val mono = TextStyle(fontFamily = GoogleSansCode, fontSize = 13.sp, lineHeight = 20.sp, letterSpacing = 0.sp)
}

private val DotsTypography: Typography = Typography().let { t ->
    fun TextStyle.g() = copy(fontFamily = FontFamily.Default, letterSpacing = 0.sp)
    Typography(
        displayLarge = t.displayLarge.g(), displayMedium = t.displayMedium.g(), displaySmall = t.displaySmall.g(),
        headlineLarge = t.headlineLarge.g(), headlineMedium = t.headlineMedium.g(), headlineSmall = t.headlineSmall.g(),
        titleLarge = t.titleLarge.g(), titleMedium = t.titleMedium.g(), titleSmall = t.titleSmall.g(),
        bodyLarge = t.bodyLarge.g(), bodyMedium = t.bodyMedium.g(), bodySmall = t.bodySmall.g(),
        labelLarge = t.labelLarge.g(), labelMedium = t.labelMedium.g(), labelSmall = t.labelSmall.g(),
    )
}

/**
 * Theme entry point. Kept under its old name because the test suite wraps
 * screens in it (V03ScreensTest); the app itself uses [DotsTheme].
 */
@Composable
fun AxionTheme(theme: String = "dark", textScale: Float = 1f, content: @Composable () -> Unit) {
    val dark = when (theme) { "light" -> false; "system" -> isSystemInDarkTheme(); else -> true }
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) darkColorScheme(
        background = p.bg, surface = p.bg, surfaceContainer = p.page2, surfaceContainerLow = p.page2, surfaceContainerHigh = p.card,
        onBackground = p.text, onSurface = p.text, primary = p.accent, onPrimary = p.onAccent, outline = p.border, surfaceVariant = p.card,
        onSurfaceVariant = p.text2,
    ) else lightColorScheme(
        background = p.bg, surface = p.bg, surfaceContainer = p.page2, surfaceContainerLow = p.page2, surfaceContainerHigh = p.card,
        onBackground = p.text, onSurface = p.text, primary = p.accent, onPrimary = p.onAccent, outline = p.border, surfaceVariant = p.card,
        onSurfaceVariant = p.text2,
    )
    val d = LocalDensity.current
    CompositionLocalProvider(LocalPalette provides p, LocalDensity provides Density(d.density, d.fontScale * textScale)) {
        MaterialTheme(colorScheme = scheme, typography = DotsTypography) {
            CompositionLocalProvider(androidx.compose.material3.LocalTextStyle provides Type.body, content = content)
        }
    }
}
