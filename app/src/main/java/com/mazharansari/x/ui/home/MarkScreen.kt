package com.mazharansari.x.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazharansari.x.ui.ChatViewModel
import com.mazharansari.x.ui.components.ScreenHeader
import com.mazharansari.x.ui.components.rememberTap
import com.mazharansari.x.ui.settings.Orb
import com.mazharansari.x.ui.settings.OrbMode
import com.mazharansari.x.ui.theme.P
import com.mazharansari.x.ui.theme.Type

/**
 * Mark (new series): the large ThinkingOrb — the 2D orb the app already draws
 * (thinking-orbs port). Idle is a still frame, Thinking is the live composing
 * orb. No 3D/GLB: there is no mascot renderer in the app, so nothing is faked.
 */
@Composable
fun MarkScreen(vm: ChatViewModel, back: () -> Unit) {
    val p = P
    var thinking by remember { mutableStateOf(vm.running) }
    Column(Modifier.fillMaxSize().background(p.bg)) {
        ScreenHeader("Mark", back)
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(32.dp))
            Orb(240.dp, mode = if (thinking) OrbMode.Composing else OrbMode.Frozen)
            Spacer(Modifier.height(24.dp))
            Text("Axion", style = Type.title.copy(fontSize = 28.sp), color = p.text)
            Spacer(Modifier.height(8.dp))
            Text(if (thinking) "Thinking" else "Idle", style = Type.small, color = p.text2)
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MarkToggle("Idle", selected = !thinking) { thinking = false }
                MarkToggle("Thinking", selected = thinking) { thinking = true }
            }
            Spacer(Modifier.height(20.dp))
            Text("Idle is a still frame. Thinking is the live orb Axion shows while it works.",
                style = Type.small, color = p.text3, modifier = Modifier.padding(horizontal = 32.dp))
        }
        Spacer(Modifier.height(DOCK_CLEARANCE))
    }
}

@Composable
private fun MarkToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    val p = P
    val tap = rememberTap(onClick)
    Box(Modifier.height(44.dp).clip(RoundedCornerShape(50))
        .background(if (selected) p.text else p.card)
        .border(1.dp, p.border, RoundedCornerShape(50))
        .clickable(onClick = tap).padding(horizontal = 22.dp), contentAlignment = Alignment.Center) {
        Text(label, style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = if (selected) p.onAccent else p.text)
    }
}
