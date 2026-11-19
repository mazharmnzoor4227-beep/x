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
 * X theme (2026-11-19, docs/X-NEW-UI-SPEC.md §2). Replaces both rejected
 * designs: the Grok sand palette and the OpenDots/OpenFuse port (periwinkle
 * #8292D6 / teal #35766F). Single indigo accent, neutral black/white/grey
 * base, system fonts only. NO green anywhere: success is a neutral check
 * icon + plain text, never a green dot/pill. Diff-added lines use
 * [accentSoft] (indigo tint), never green.
 *
 * Light: bg #FFFFFF, surface #F7F7F8, text #171717, accent #4F46E5.
 * Dark:  bg #171717, surface #212121, text #ECECEC, accent #818CF8.
 *
 * Legacy alias fields (chip/card/page2/sheet/sidebar…/blue/status*) are kept
 * so existing screens keep compiling; they all resolve to the tokens above.
 */
@Immutable
data class Palette(
    val bg: Color, val surface: Color, val surface2: Color, val sheet: Color, val border: Color,
    val text: Color, val text2: Color, val text3: Color, val accent: Color, val onAccent: Color,
    val code: Color, val danger: Color, val isDark: Boolean,
    val chip: Color = surface2, val card: Color = surface, val page2: Color = sheet, val cardBorder: Color = border,
    val dim: Color = text3, val popover: Color = sheet, val blue: Color = Color(0xFF4F46E5),
    val accentSoft: Color = Color(0xFFEDEBFE),
    val statusDone: Color = Color(0xFF5F5F5F),
    val statusRunning: Color = Color(0xFF4F46E5),
    val statusFailed: Color = Color(0xFFDC2626),
    val statusPaused: Color = Color(0xFF8A8A8E),
    val sidebar: Color = surface,
    // Spec §2 tokens.
    val surfaceRaised: Color = Color(0xFFFFFFFF),
    val surfaceSunken: Color = Color(0xFFEFEFF1),
    val textSecondary: Color = Color(0xFF5F5F5F),
    val muted: Color = Color(0xFF8A8A8E),
    val dangerSoft: Color = Color(0xFFFEF2F2),
    val userBubble: Color = Color(0xFFF0F0F0),
    val codeBg: Color = Color(0xFF1E1E1E),
    val codeText: Color = Color(0xFFF5F5F5),
    val scrim: Color = Color(0x52000000),
)

val DarkPalette = Palette(
    bg = Color(0xFF171717), surface = Color(0xFF212121), surface2 = Color(0xFF2A2A2A), sheet = Color(0xFF212121),
    border = Color(0xFF2E2E2E), text = Color(0xFFECECEC), text2 = Color(0xFFA3A3A3), text3 = Color(0xFF737373),
    accent = Color(0xFF818CF8), onAccent = Color(0xFF171717), code = Color(0xFF1E1E1E), danger = Color(0xFFF87171), isDark = true,
    chip = Color(0xFF2A2A2A), card = Color(0xFF2A2A2A), page2 = Color(0xFF171717), cardBorder = Color(0xFF2E2E2E),
    dim = Color(0xFF737373), popover = Color(0xFF212121), blue = Color(0xFF818CF8),
    accentSoft = Color(0xFF26264A),
    statusDone = Color(0xFFA3A3A3), statusRunning = Color(0xFF818CF8), statusFailed = Color(0xFFF87171), statusPaused = Color(0xFF737373),
    sidebar = Color(0xFF212121),
    surfaceRaised = Color(0xFF2A2A2A), surfaceSunken = Color(0xFF101010), textSecondary = Color(0xFFA3A3A3),
    muted = Color(0xFF737373), dangerSoft = Color(0xFF3D2624), userBubble = Color(0xFF2F2F2F),
    codeBg = Color(0xFF1E1E1E), codeText = Color(0xFFF5F5F5), scrim = Color(0x8C000000),
)
val LightPalette = Palette(
    bg = Color(0xFFFFFFFF), surface = Color(0xFFF7F7F8), surface2 = Color(0xFFEFEFF1), sheet = Color(0xFFF7F7F8),
    border = Color(0xFFE5E5E5), text = Color(0xFF171717), text2 = Color(0xFF5F5F5F), text3 = Color(0xFF8A8A8E),
    accent = Color(0xFF4F46E5), onAccent = Color(0xFFFFFFFF), code = Color(0xFF1E1E1E), danger = Color(0xFFDC2626), isDark = false,
    chip = Color(0xFFEFEFF1), card = Color(0xFFFFFFFF), page2 = Color(0xFFF7F7F8), cardBorder = Color(0xFFE5E5E5),
    dim = Color(0xFF8A8A8E), popover = Color(0xFFFFFFFF), blue = Color(0xFF4F46E5),
    accentSoft = Color(0xFFEDEBFE),
    statusDone = Color(0xFF5F5F5F), statusRunning = Color(0xFF4F46E5), statusFailed = Color(0xFFDC2626), statusPaused = Color(0xFF8A8A8E),
    sidebar = Color(0xFFF7F7F8),
    surfaceRaised = Color(0xFFFFFFFF), surfaceSunken = Color(0xFFEFEFF1), textSecondary = Color(0xFF5F5F5F),
    muted = Color(0xFF8A8A8E), dangerSoft = Color(0xFFFEF2F2), userBubble = Color(0xFFF0F0F0),
    codeBg = Color(0xFF1E1E1E), codeText = Color(0xFFF5F5F5), scrim = Color(0x52000000),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }
val P: Palette @Composable @ReadOnlyComposable get() = LocalPalette.current

@OptIn(ExperimentalTextApi::class)
private fun code(w: Int) = Font(R.font.google_sans_code, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

val GoogleSansCode = FontFamily(code(300), code(400), code(500), code(600), code(700))

/**
 * System fonts only (spec §3): UI/body = FontFamily.Default, code/diff/
 * terminal = FontFamily.Monospace (Google Sans Code, the one bundled mono).
 * No Fraunces/Outfit/Google Sans Flex — the rejected designs' fonts are gone.
 */
object Type {
    val display = TextStyle(fontFamily = FontFamily.Default, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp, lineHeight = 24.sp)
    val small = TextStyle(fontFamily = FontFamily.Default, fontSize = 15.sp, lineHeight = 22.sp)
    val tiny = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, lineHeight = 18.sp)
    val label = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
    val row = TextStyle(fontFamily = FontFamily.Default, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
    val title = TextStyle(fontFamily = FontFamily.Default, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
    val header = TextStyle(fontFamily = FontFamily.Default, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
    val mono = TextStyle(fontFamily = GoogleSansCode, fontSize = 13.sp, lineHeight = 20.sp)
}

private val XTypography: Typography = Typography().let { t ->
    fun TextStyle.g() = copy(fontFamily = FontFamily.Default, letterSpacing = 0.sp)
    Typography(
        displayLarge = t.displayLarge.g(), displayMedium = t.displayMedium.g(), displaySmall = t.displaySmall.g(),
        headlineLarge = t.headlineLarge.g(), headlineMedium = t.headlineMedium.g(), headlineSmall = t.headlineSmall.g(),
        titleLarge = t.titleLarge.g(), titleMedium = t.titleMedium.g(), titleSmall = t.titleSmall.g(),
        bodyLarge = t.bodyLarge.g(), bodyMedium = t.bodyMedium.g(), bodySmall = t.bodySmall.g(),
        labelLarge = t.labelLarge.g(), labelMedium = t.labelMedium.g(), labelSmall = t.labelSmall.g(),
    )
}

/** The single X look: System / Light / Dark. Follows system by default. */
enum class XLook { System, Light, Dark }

/** Theme entry point for the app (AppRoot). */
@Composable
fun XTheme(look: XLook = XLook.System, textScale: Float = 1f, content: @Composable () -> Unit) {
    AxionTheme(when (look) { XLook.Light -> "light"; XLook.Dark -> "dark"; XLook.System -> "system" }, textScale, content)
}

/**
 * Theme entry point. Kept under its old name because the test suite wraps
 * screens in it (V03ScreensTest); the app itself uses [XTheme].
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
        MaterialTheme(colorScheme = scheme, typography = XTypography) {
            CompositionLocalProvider(androidx.compose.material3.LocalTextStyle provides Type.body, content = content)
        }
    }
}
