package com.mazharansari.x.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazharansari.x.agent.Mime
import com.mazharansari.x.data.Artifact
import com.mazharansari.x.data.Project
import com.mazharansari.x.ui.ChatViewModel
import com.mazharansari.x.ui.chat.ArtifactCard
import com.mazharansari.x.ui.components.Gk
import com.mazharansari.x.ui.components.GkIcon
import com.mazharansari.x.ui.components.ScreenHeader
import com.mazharansari.x.ui.components.rememberTap
import com.mazharansari.x.ui.theme.P
import com.mazharansari.x.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Library (new series, dock tab): Build projects on top, then the workspace
 * files (uploads + everything Axion created). A tap opens the project, or the
 * file with the existing viewers (PDF / HTML in-app, anything else in the
 * phone's own apps) — the same open path the chat uses.
 */
@Composable
fun LibraryScreen(vm: ChatViewModel, back: () -> Unit) {
    val p = P
    val ctx = LocalContext.current
    val projects by vm.g.build.store.list.collectAsState()
    val files by produceState(emptyList<File>()) {
        value = withContext(Dispatchers.IO) {
            vm.g.workspace.root.walkTopDown().onEnter { it.name != "node_modules" }
                .filter { it.isFile && !it.path.contains("/.") }.sortedByDescending { it.lastModified() }.take(200).toList()
        }
    }
    fun openArtifact(a: Artifact) {
        // Same routing as the chat: PDF / HTML open in Axion's own viewers (which
        // render over Home), anything else goes to the phone's apps.
        when {
            a.mime == "application/pdf" -> { vm.viewingPdf = a; back() }
            a.mime == "text/html" || a.name.endsWith(".html", true) -> { vm.viewingHtml = a; back() }
            else -> openFile(ctx, a)
        }
    }

    Column(Modifier.fillMaxSize().background(p.bg)) {
        ScreenHeader("Library", back)
        LazyColumn(Modifier.weight(1f).navigationBarsPadding(), contentPadding = PaddingValues(bottom = DOCK_CLEARANCE), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "projects-head") { SectionHead("Projects") }
            if (projects.isEmpty()) {
                item(key = "projects-empty") {
                    Text("No projects yet. Build creates one when you ask Axion to build an app or a site.",
                        style = Type.small, color = p.text2, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                }
            }
            items(projects, key = { it.id }) { pr -> ProjectRow(pr) { vm.openProject(pr.id); back() } }
            item(key = "files-head") { SectionHead("Files") }
            if (files.isEmpty()) {
                item(key = "files-empty") {
                    Text("Files you upload and files Axion creates (PDFs, code, sites) show up here.",
                        style = Type.small, color = p.text2, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                }
            }
            items(files, key = { it.path }) { f ->
                val a = Artifact(f.name, Mime.of(f.name), f.absolutePath, f.length())
                Box(Modifier.padding(horizontal = 16.dp)) { ArtifactCard(a, { openArtifact(a) }, { vm.toast = FileActions.download(ctx, a) }) }
            }
        }
    }
}

@Composable
private fun ProjectRow(pr: Project, onOpen: () -> Unit) {
    val p = P
    val tap = rememberTap(onOpen)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp)).background(p.card).clickable(onClick = tap).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(p.surface), contentAlignment = Alignment.Center) {
            GkIcon(Gk.projects, null, 20.dp, p.text)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(pr.name, style = Type.row.copy(fontSize = 16.sp), color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${pr.kind} · updated ${timeLabel(pr.updatedAt)}", style = Type.small, color = p.text2, modifier = Modifier.padding(top = 2.dp))
        }
        GkIcon(Gk.chevronRight, "Open project", 20.dp, p.text3)
    }
}
