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
 * X theme — "Ink Ledger" (2026-11-19, docs/X-REDESIGN2-SPEC-2026-11-19.md §2,
 * differentiation table §0.0). Replaces the round-1 indigo/light ChatGPT-like
 * skin AND every earlier design: no warm sand/paper, no periwinkle/teal, no
 * indigo, no serif/Outfit, no cartoon logo, no drawer. Dark-default ink canvas
 * (#14161A) with a single bronze accent (#E0A63F dark / #92400E light),
 * rectilinear shapes (4–12dp), hairline rules, mono metadata.
 * NO green anywhere: success is a neutral check icon + plain text, never a
 * green dot/pill. Diff-added lines use [accentSoft] (bronze tint), never green.
 *
 * Dark (default): bg #14161A, surface #1D2026, text #F2F3F5, accent #E0A63F.
 * Light ("Day"):   bg #F7F8FA, surface #FFFFFF, text #16181D, accent #92400E.
 *
 * Legacy alias fields (chip/card/page2/sheet/sidebar…/blue/status*) are kept
 * so existing screens keep compiling; they all resolve to the tokens above
 * ([blue] is the bronze accent now — there is no blue in this theme).
 */
@Immutable
data class Palette(
    val bg: Color, val surface: Color, val surface2: Color, val sheet: Color, val border: Color,
    val text: Color, val text2: Color, val text3: Color, val accent: Color, val onAccent: Color,
    val code: Color, val danger: Color, val isDark: Boolean,
    val chip: Color = surface2, val card: Color = surface, val page2: Color = sheet, val cardBorder: Color = border,
    val dim: Color = text3, val popover: Color = sheet, val blue: Color = Color(0xFFE0A63F),
    val accentSoft: Color = Color(0xFF3A2E18),
    val statusDone: Color = Color(0xFFA6ADB8),
    val statusRunning: Color = Color(0xFFE0A63F),
    val statusFailed: Color = Color(0xFFF87171),
    val statusPaused: Color = Color(0xFF6E7683),
    val sidebar: Color = surface,
    // Spec §2 tokens.
    val surfaceRaised: Color = Color(0xFF262A31),
    val surfaceSunken: Color = Color(0xFF0F1114),
    val textSecondary: Color = Color(0xFFA6ADB8),
    val muted: Color = Color(0xFF6E7683),
    val dangerSoft: Color = Color(0xFF3D2624),
    val userBubble: Color = Color(0xFF2A2F38),
    val codeBg: Color = Color(0xFF1E1E1E),
    val codeText: Color = Color(0xFFF5F5F5),
    val scrim: Color = Color(0x8C000000),
)

val DarkPalette = Palette(
    bg = Color(0xFF14161A), surface = Color(0xFF1D2026), surface2 = Color(0xFF262A31), sheet = Color(0xFF1D2026),
    border = Color(0xFF2E333D), text = Color(0xFFF2F3F5), text2 = Color(0xFFA6ADB8), text3 = Color(0xFF6E7683),
    accent = Color(0xFFE0A63F), onAccent = Color(0xFF14161A), code = Color(0xFF1E1E1E), danger = Color(0xFFF87171), isDark = true,
    chip = Color(0xFF262A31), card = Color(0xFF1D2026), page2 = Color(0xFF14161A), cardBorder = Color(0xFF2E333D),
    dim = Color(0xFF6E7683), popover = Color(0xFF1D2026), blue = Color(0xFFE0A63F),
    accentSoft = Color(0xFF3A2E18),
    statusDone = Color(0xFFA6ADB8), statusRunning = Color(0xFFE0A63F), statusFailed = Color(0xFFF87171), statusPaused = Color(0xFF6E7683),
    sidebar = Color(0xFF1D2026),
    surfaceRaised = Color(0xFF262A31), surfaceSunken = Color(0xFF0F1114), textSecondary = Color(0xFFA6ADB8),
    muted = Color(0xFF6E7683), dangerSoft = Color(0xFF3D2624), userBubble = Color(0xFF2A2F38),
    codeBg = Color(0xFF1E1E1E), codeText = Color(0xFFF5F5F5), scrim = Color(0x8C000000),
)
val LightPalette = Palette(
    bg = Color(0xFFF7F8FA), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFFFFFFF), sheet = Color(0xFFFFFFFF),
    border = Color(0xFFE2E5EA), text = Color(0xFF16181D), text2 = Color(0xFF565D68), text3 = Color(0xFF8A919C),
    accent = Color(0xFF92400E), onAccent = Color(0xFFFFFFFF), code = Color(0xFF1E1E1E), danger = Color(0xFFDC2626), isDark = false,
    chip = Color(0xFFEDEFF2), card = Color(0xFFFFFFFF), page2 = Color(0xFFF7F8FA), cardBorder = Color(0xFFE2E5EA),
    dim = Color(0xFF8A919C), popover = Color(0xFFFFFFFF), blue = Color(0xFF92400E),
    accentSoft = Color(0xFFFDF3E3),
    statusDone = Color(0xFF565D68), statusRunning = Color(0xFF92400E), statusFailed = Color(0xFFDC2626), statusPaused = Color(0xFF8A919C),
    sidebar = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFFFFFF), surfaceSunken = Color(0xFFEDEFF2), textSecondary = Color(0xFF565D68),
    muted = Color(0xFF8A919C), dangerSoft = Color(0xFFFEF2F2), userBubble = Color(0xFFEDEFF2),
    codeBg = Color(0xFF1E1E1E), codeText = Color(0xFFF5F5F5), scrim = Color(0x52000000),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }
val P: Palette @Composable @ReadOnlyComposable get() = LocalPalette.current

@OptIn(ExperimentalTextApi::class)
private fun code(w: Int) = Font(R.font.google_sans_code, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

val GoogleSansCode = FontFamily(code(300), code(400), code(500), code(600), code(700))

/**
 * System fonts only (spec §2.2): UI/body = FontFamily.Default, code/diff/
 * terminal/metadata = bundled mono (Google Sans Code, the one bundled mono).
 * No Fraunces/Outfit/Google Sans Flex — the rejected designs' fonts are gone.
 * Scale: title 22/28, body 15/22, row preview 13/18, meta mono 12/16,
 * eyebrow 11/16 ALL CAPS, label 12/16.
 */
object Type {
    val display = TextStyle(fontFamily = FontFamily.Default, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontFamily = FontFamily.Default, fontSize = 15.sp, lineHeight = 22.sp)
    val small = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, lineHeight = 18.sp)
    val tiny = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, lineHeight = 16.sp)
    val label = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
    val row = TextStyle(fontFamily = FontFamily.Default, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    val title = TextStyle(fontFamily = FontFamily.Default, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
    val header = TextStyle(fontFamily = FontFamily.Default, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
    val mono = TextStyle(fontFamily = GoogleSansCode, fontSize = 12.sp, lineHeight = 16.sp)
    val eyebrow = TextStyle(fontFamily = FontFamily.Default, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
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

/** The single X look: System / Light / Dark. Default is Dark (Ink) — a deliberate change from round 1 (spec §2.1). */
enum class XLook { System, Light, Dark }

/** Theme entry point for the app (AppRoot). */
@Composable
fun XTheme(look: XLook = XLook.Dark, textScale: Float = 1f, content: @Composable () -> Unit) {
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
