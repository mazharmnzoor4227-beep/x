package com.mazharansari.x.ui.bots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazharansari.x.ui.theme.P

/**
 * Monogram tiles (redesign round 2, spec §2.5): the ONLY avatar in the app.
 * Every bot — and the user — is a rounded-square tile (8dp corners) with the
 * first TWO initials, uppercase, on a deterministic muted fill picked from
 * the name's hash. No cartoon face, no glyph, no gallery photo: the stored
 * [logo] string is deliberately ignored (the field stays in storage, so
 * nothing is lost if the decision is revisited). The "Change logo" picker
 * and the photo feature are deleted with BotsScreen.
 *
 * Fills (dark theme; light theme uses the same hues at 12% tint on white
 * with dark text — see [monogramFill]):
 * slate #3A4150, bronze-deep #6B4A1F, plum #4A3542, navy #24344D, stone #4A4438.
 */
private val MONOGRAM_FILLS_DARK = listOf(
    Color(0xFF3A4150), Color(0xFF6B4A1F), Color(0xFF4A3542), Color(0xFF24344D), Color(0xFF4A4438),
)
private val MONOGRAM_FILLS_LIGHT = listOf(
    Color(0xFFE8EAF0), Color(0xFFF3E8D2), Color(0xFFF0E6EC), Color(0xFFE2E9F2), Color(0xFFEDEAE2),
)

/** Deterministic fill for a name: same name, same tile, on every screen.
 * [dark] is passed in by the caller (a plain function cannot read the
 * @Composable theme palette [P] itself). */
fun monogramFill(name: String, dark: Boolean): Color {
    val fills = if (dark) MONOGRAM_FILLS_DARK else MONOGRAM_FILLS_LIGHT
    val idx = (name.lowercase().hashCode() and 0x7FFFFFFF) % fills.size
    return fills[idx]
}

/** The two initials for a name: first letters of the first two words, else first two letters. */
fun monogramInitials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.size >= 2 -> (words[0].first() + "" + words[1].first()).uppercase()
        words.size == 1 -> words[0].take(2).uppercase()
        else -> "X"
    }
}

@Composable
fun BotLogoView(logo: String, name: String, size: Dp, modifier: Modifier = Modifier) {
    val p = P
    val dark = p.bg.red < 0.5f
    Box(
        modifier.size(size).clip(RoundedCornerShape(8.dp)).background(monogramFill(name, dark)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            monogramInitials(name),
            color = if (dark) Color.White else Color(0xFF16181D),
            fontSize = (size.value * 0.36f).sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
