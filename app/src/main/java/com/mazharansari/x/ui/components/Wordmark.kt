package com.mazharansari.x.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazharansari.x.ui.theme.P

/**
 * The X wordmark (redesign round 2, spec §3.5): the letter "X" in SemiBold
 * with −0.5sp tracking, followed by an 8×8dp bronze square (2dp corners) as
 * its full stop — "X▪". No image asset, no character, no face. This is the
 * only brand mark in the app; it replaces the cartoon logo everywhere
 * (chat header, empty chat, splash, About, intro).
 */
@Composable
fun Wordmark(fontSize: TextUnit = 22.sp, modifier: Modifier = Modifier) {
    val p = P
    val square = (fontSize.value * 0.36f).dp.coerceAtLeast(6.dp)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("X", color = p.text, fontSize = fontSize, lineHeight = fontSize * 1.15f,
            fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
        Spacer(Modifier.width(2.dp))
        Box(Modifier.size(square).clip(RoundedCornerShape(2.dp)).background(p.accent))
    }
}
