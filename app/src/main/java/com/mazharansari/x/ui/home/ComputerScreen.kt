package com.mazharansari.x.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazharansari.x.ui.ChatViewModel
import com.mazharansari.x.ui.Route
import com.mazharansari.x.ui.components.Gk
import com.mazharansari.x.ui.components.GkIcon
import com.mazharansari.x.ui.components.ScreenHeader
import com.mazharansari.x.ui.components.rememberTap
import com.mazharansari.x.ui.theme.P
import com.mazharansari.x.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Computer (new series): one screen unifying the three real on-phone machines —
 * the headless browser, the Linux terminal and the workspace files. No mock
 * content: each card opens the screen that already does the work.
 */
@Composable
fun ComputerScreen(vm: ChatViewModel, back: () -> Unit, navigate: (Route) -> Unit = {}) {
    val p = P
    val linuxInstalled = remember { vm.g.linux.isInstalled() }
    val fileCount by produceState<Int?>(null) {
        value = withContext(Dispatchers.IO) {
            vm.g.workspace.root.walkTopDown().onEnter { it.name != "node_modules" }
                .count { it.isFile && !it.path.contains("/.") }
        }
    }

    Column(Modifier.fillMaxSize().background(p.bg)) {
        ScreenHeader("Computer", back)
        Text("Your workspace on this phone — browser, terminal and files in one place.",
            style = Type.small.copy(fontSize = 14.sp, lineHeight = 20.sp), color = p.text2,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        LazyColumn(Modifier.weight(1f).navigationBarsPadding(), contentPadding = PaddingValues(top = 10.dp, bottom = DOCK_CLEARANCE), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "browser") {
                ComputerCard(icon = Gk.globe, title = "Browser", detail = "Obscura — Axion's headless browser. It opens pages, clicks and reads for you.",
                    status = "Take control", onClick = { navigate(Route.Browser) })
            }
            item(key = "terminal") {
                ComputerCard(icon = Gk.terminal, title = "Terminal", detail = "A real shell in Linux on this phone.",
                    status = if (linuxInstalled) "Linux installed — shell ready" else "Linux is not installed yet — set up in Developer tools",
                    onClick = { navigate(Route.Terminal) })
            }
            item(key = "files") {
                ComputerCard(icon = Gk.folder, title = "Files", detail = "Your workspace files — uploads and everything Axion created.",
                    status = fileCount?.let { "$it file${if (it == 1) "" else "s"} in your workspace" } ?: "Counting files…",
                    onClick = { navigate(Route.Files) })
            }
        }
    }
}

@Composable
private fun ComputerCard(icon: Int, title: String, detail: String, status: String, onClick: () -> Unit) {
    val p = P
    val tap = rememberTap(onClick)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp)).background(p.card).clickable(onClick = tap).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(p.surface), contentAlignment = Alignment.Center) {
                GkIcon(icon, null, 22.dp, p.text)
            }
            Spacer(Modifier.width(12.dp))
            Text(title, style = Type.row.copy(fontSize = 17.sp), color = p.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            GkIcon(Gk.chevronRight, "Open $title", 20.dp, p.text3)
        }
        Text(detail, style = Type.small, color = p.text2, modifier = Modifier.padding(top = 10.dp))
        Text(status, style = Type.small.copy(fontSize = 13.sp), color = p.accent, modifier = Modifier.padding(top = 6.dp))
    }
}
