package com.mazharansari.x.ui.bots

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.mazharansari.x.data.BotLogo
import com.mazharansari.x.ui.theme.P
import java.io.File

/**
 * Bot avatars (navigation restructure, 2026-11-20): the default is still the
 * professional monogram tile (redesign round 2, spec §2.5) — rounded-square
 * tile (8dp corners) with the first TWO initials, uppercase, on a
 * deterministic muted fill picked from the name's hash. No cartoon face, no
 * glyph, no gallery photo unless the USER picks one in profile editing.
 *
 * What [BotLogo.parse] can carry, and how each renders here:
 * - "" / builtin → monogram tile (the default; a builtin id never changes it).
 * - "file:" → the user's static image, loaded with Coil, cropped to the tile.
 *   A missing file or a load error falls back to the monogram — never blank.
 * - "video:" → the user's short clip in a looping, MUTED [VideoView]
 *   (framework player, no new dependency; `isLooping` + volume 0). A missing
 *   file falls back to the monogram. Clips are capped at 8 MB at pick time
 *   (see BotProfileSheet), so the tile stays lightweight.
 * - "3d:" → a 3D-style drawn tile: a two-tone vertical gradient with a soft
 *   top highlight, giving a raised look, plus the monogram initials. There
 *   is NO .glb / 3D-model renderer wired in-tree, so this drawn tile is the
 *   honest 3D look the app can render today.
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

/** 3D-style tile shading: top colour → bottom colour per style id. */
private val THREE_D_SHADES: Map<String, Pair<Color, Color>> = mapOf(
    "bronze" to (Color(0xFF9A6A2E) to Color(0xFF5C3A14)),
    "slate" to (Color(0xFF5A6A80) to Color(0xFF2D3748)),
    "plum" to (Color(0xFF7A4E64) to Color(0xFF472A38)),
    "navy" to (Color(0xFF3D5C85) to Color(0xFF1D2F47)),
    "stone" to (Color(0xFF7D7261) to Color(0xFF453E32)),
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
    when (val parsed = BotLogo.parse(logo)) {
        is BotLogo.Video -> {
            if (File(parsed.path).isFile) VideoTile(parsed.path, size, modifier)
            else MonogramTile(name, size, modifier)
        }
        is BotLogo.Custom -> {
            if (File(parsed.path).isFile) ImageTile(parsed.path, name, size, modifier)
            else MonogramTile(name, size, modifier)
        }
        is BotLogo.ThreeD -> ThreeDTile(parsed.id, name, size, modifier)
        else -> MonogramTile(name, size, modifier)
    }
}

@Composable
private fun MonogramTile(name: String, size: Dp, modifier: Modifier = Modifier) {
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

@Composable
private fun ImageTile(path: String, name: String, size: Dp, modifier: Modifier = Modifier) {
    var failed by remember(path) { mutableStateOf(false) }
    if (failed) {
        MonogramTile(name, size, modifier)
    } else {
        AsyncImage(
            model = File(path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { failed = true },
            modifier = modifier.size(size).clip(RoundedCornerShape(8.dp)),
        )
    }
}

/** Looping, muted video avatar. Framework VideoView — no new dependency. */
@Composable
private fun VideoTile(path: String, size: Dp, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                setVideoURI(Uri.fromFile(File(path)))
                setOnPreparedListener { mp ->
                    mp.isLooping = true
                    mp.setVolume(0f, 0f) // video avatars never play sound
                    start()
                }
            }
        },
        modifier = modifier.size(size).clip(RoundedCornerShape(8.dp)),
    )
}

@Composable
private fun ThreeDTile(id: String, name: String, size: Dp, modifier: Modifier = Modifier) {
    val (top, bottom) = THREE_D_SHADES[id] ?: THREE_D_SHADES.getValue("bronze")
    Box(
        modifier.size(size).clip(RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(listOf(top, bottom))),
        contentAlignment = Alignment.Center,
    ) {
        // Soft top highlight: the raised look.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent))
            )
        )
        Text(
            monogramInitials(name),
            color = Color.White,
            fontSize = (size.value * 0.36f).sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
