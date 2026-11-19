package com.mazharansari.x.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazharansari.x.build.ChangeGate
import com.mazharansari.x.build.PatchEngine
import com.mazharansari.x.build.ProjectFiles
import com.mazharansari.x.data.TaskRunRecord
import com.mazharansari.x.ui.ChatViewModel
import com.mazharansari.x.ui.Route
import com.mazharansari.x.ui.components.*
import com.mazharansari.x.ui.theme.P
import com.mazharansari.x.ui.theme.Type
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Workspace stack destinations (2026-11-19, X-NEW-UI-SPEC §6). Reached from
 * the drawer → Workspace, or from chat. No dock, no home tiles. Every list
 * starts empty and shows only real data from the stores — no demo rows,
 * no fake counts. Every screen is a standard subpage: back chevron + 22sp
 * title + the same status-bar gap (ScreenHeader) as everywhere else.
 */

private fun fmtTime(ts: Long): String =
    SimpleDateFormat("d MMM yyyy · HH:mm", Locale.ENGLISH).format(Date(ts))

@Composable
private fun WorkspaceEmpty(icon: Int, title: String, line: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    val p = P
    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        GkIcon(icon, null, 48.dp, p.muted)
        Spacer(Modifier.height(16.dp))
        Text(title, style = Type.header.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium), color = p.text)
        Spacer(Modifier.height(6.dp))
        Text(line, style = Type.small.copy(fontSize = 14.sp, lineHeight = 20.sp), color = p.textSecondary)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Box(Modifier.height(48.dp).clip(RoundedCornerShape(8.dp)).background(p.accent).clickable(onClick = onAction).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                Text(actionLabel, style = Type.small.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium), color = p.onAccent)
            }
        }
    }
}

@Composable
private fun WorkspaceCard(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val p = P
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp)).background(p.surface).border(1.dp, p.border, RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp), content = content,
    )
}

@Composable
private fun AddRow(hint: String, button: String, onAdd: (String) -> Unit) {
    val p = P
    var t by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(10.dp)).background(p.surface).border(1.dp, p.border, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
            if (t.isEmpty()) Text(hint, style = Type.small.copy(fontSize = 15.sp), color = p.muted)
            BasicTextField(t, { t = it }, singleLine = true, textStyle = Type.small.copy(fontSize = 15.sp, color = p.text), cursorBrush = SolidColor(p.text), modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.height(48.dp).clip(RoundedCornerShape(8.dp)).background(if (t.isBlank()) p.surfaceSunken else p.accent).clickable(enabled = t.isNotBlank()) { onAdd(t); t = "" }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(button, style = Type.small.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium), color = if (t.isBlank()) p.muted else p.onAccent)
        }
    }
}

// ── Feed ────────────────────────────────────────────────────────────────────

@Composable
fun FeedScreen(vm: ChatViewModel, back: () -> Unit, navigate: (Route) -> Unit) {
    val p = P
    val runs by vm.g.taskRuns.records.collectAsState()
    val pending = vm.pendingApproval
    val interrupted = vm.interruptedTask
    Column(Modifier.fillMaxSize().background(p.bg)) {
        ScreenHeader("Feed", back)
        if (pending == null && interrupted == null && runs.isEmpty()) {
            WorkspaceEmpty(Gk.history, "Nothing yet", "Completed runs and items needing you will appear here.")
        } else {
            LazyColumn(Modifier.weight(1f).navigationBarsPadding(), contentPadding = PaddingValues(bottom = 32.dp)) {
                if (pending != null || interrupted != null) {
                    item {
                        Text("NEEDS YOU", style = Type.label.copy(letterSpacing = 0.08.sp), color = p.textSecondary, modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp))
                    }
                    if (pending != null) {
                        item {
                            WorkspaceCard {
                                Text("Approval needed", style = Type.small.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = p.text)
                                Text(pending.req.summary.take(200), style = Type.small.copy(fontSize = 14.sp, lineHeight = 20.sp), color = p.textSecondary, modifier = Modifier.padding(top = 4.dp))
                                TextButton(onClick = { back() }) { Text("Review in chat", color = p.accent) }
                            }
                        }
                    }
                    if (interrupted != null) {
                        item {
                            WorkspaceCard(onClick = { vm.resumeInterruptedTask(); back() }) {
                                Text("Interrupted task", style = Type.small.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = p.text)
                                Text(interrupted.goal.take(200), style = Type.small.copy(fontSize = 14.sp, lineHeight = 20.sp), color = p.textSecondary, modifier = Modifier.padding(top = 4.dp))
                                Text(fmtTime(interrupted.updatedAt), style = Type.tiny, color = p.muted, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
                if (runs.isNotEmpty()) {
                    item {
                        Text("WHILE YOU WERE OUT", style = Type.label.copy(letterSpacing = 0.08.sp), color = p.textSecondary, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp))
                    }
                    items(runs, key = { it.kind + it.conversationId + it.startedAt }) { r: TaskRunRecord ->
                        WorkspaceCard(onClick = { vm.open(r.conversationId); back() }) {
                            Text(r.goal.ifBlank { "Task" }, style = Type.small.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = p.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${r.status.replaceFirstChar { it.uppercase() }} · ${r.stepsDone} steps · ${r.kind}", style = Type.small.copy(fontSize = 13.sp), color = p.textSecondary, modifier = Modifier.padding(top = 4.dp))
                            Text(fmtTime(r.updatedAt), style = Type.tiny, color = p.muted, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── Ideas ───────────────────────────────────────────────────────────────────

@Composable
fun IdeasScreen(vm: ChatViewModel, back: () -> Unit, navigate: (Route) -> Unit) {
    val p = P
    val ideas by vm.g.ideas.ideas.collectAsState()
    var area by remember { mutableStateOf("You") }
    Column(Modifier.fillMaxSize().background(p.bg)) {
        ScreenHeader("Ideas", back)
        AddRow("Save an idea…", "Save") { vm.g.ideas.add(it, area) }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("You", "Store").forEach { a ->
                Box(Modifier.height(32.dp).clip(RoundedCornerShape(6.dp)).background(if (area == a) p.accentSoft else p.surface).border(1.dp, if (area == a) p.accent else p.border, RoundedCornerShape(6.dp)).clickable { area = a }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Text(a, style = Type.label, color = if (area == a) p.accent else p.text)
                }
            }
        }
        if (ideas.isEmpty()) {
            WorkspaceEmpty(Gk.sparkles, "No ideas yet", "Ask X to save an idea from any chat.", "New chat") { vm.newChat(); back() }
        } else {
            LazyColumn(Modifier.weight(1f).navigationBarsPadding(), contentPadding = PaddingValues(bottom = 32.dp)) {
                ideas.groupBy { it.area }.forEach { (group, list) ->
                    item { Text(group.uppercase(), style = Type.label.copy(letterSpacing = 0.08.sp), color = p.textSecondary, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)) }
                    items(list, key = { it.id }) { idea ->
                        WorkspaceCard(onClick = { vm.newChat(); vm.draft = idea.text; back() }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(idea.text, style = Type.small.copy(fontSize = 15.sp), color = if (idea.done) p.textSecondary else p.text, modifier = Modifier.weight(1f))
                                TextButton(onClick = { vm.g.ideas.toggleDone(idea.id) }) { Text(if (idea.done) "Done" else "Mark done", color = p.accent) }
                                TextButton(onClick = { vm.g.ideas.remove(idea.id) }) { Text("Delete", color = p.danger) }
                            }
                            Text(fmtTime(idea.createdAt), style = Type.tiny, color = p.muted, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── Goals ───────────────────────────────────────────────────────────────────

@Composable
fun GoalsScreen(vm: ChatViewModel, back: () -> Unit, navigate: (Route) -> Unit) {
    val p = P
    val goals by vm.g.goals.goals.collectAsState()
    var title by remember { mutableStateOf("") }
    var stepsText by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(p.bg)) {
        ScreenHeader("Goals", back)
        if (!adding) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Box(Modifier.height(48.dp).clip(RoundedCornerShape(8.dp)).background(p.accent).clickable { adding = true }.padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                    Text("New goal", style = Type.small.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium), color = p.onAccent)
                }
            }
        } else {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.surface).border(1.dp, p.border, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
                    if (title.isEmpty()) Text("Goal title", style = Type.small.copy(fontSize = 15.sp), color = p.muted)
                    BasicTextField(title, { title = it }, singleLine = true, textStyle = Type.small.copy(fontSize = 15.sp, color = p.text), cursorBrush = SolidColor(p.text), modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.surface).border(1.dp, p.border, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
                    if (stepsText.isEmpty()) Text("Steps — one per line", style = Type.small.copy(fontSize = 15.sp), color = p.muted)
                    BasicTextField(stepsText, { stepsText = it }, textStyle = Type.small.copy(fontSize = 15.sp, lineHeight = 22.sp, color = p.text), cursorBrush = SolidColor(p.text), modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.height(48.dp).clip(RoundedCornerShape(8.dp)).background(if (title.isBlank()) p.surfaceSunken else p.accent).clickable(enabled = title.isNotBlank()) {
                        vm.g.goals.add(title, stepsText.lines()); title = ""; stepsText = ""; adding = false
                    }.padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                        Text("Save goal", style = Type.small.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium), color = if (title.isBlank()) p.muted else p.onAccent)
                    }
                    TextButton(onClick = { adding = false }) { Text("Cancel", color = p.textSecondary) }
                }
            }
        }
        if (goals.isEmpty()) {
            WorkspaceEmpty(Gk.circleCheck, "No goals yet", "Tell X a goal and it will track it here.", "New chat") { vm.newChat(); back() }
        } else {
            LazyColumn(Modifier.weight(1f).navigationBarsPadding(), contentPadding = PaddingValues(bottom = 32.dp)) {
                items(goals, key = { it.id }) { goal ->
                    WorkspaceCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(goal.title, style = Type.small.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = p.text, modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.g.goals.remove(goal.id) }) { Text("Delete", color = p.danger) }
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { goal.progress }, modifier = Modifier.fillMaxWidth().height(4.dp), color = p.accent, trackColor = p.surfaceSunken)
                        Text("${goal.doneCount} of ${goal.steps.size} steps · ${(goal.progress * 100).toInt()}%", style = Type.tiny, color = p.muted, modifier = Modifier.padding(top = 6.dp))
                        goal.steps.forEachIndexed { i, step ->
                            Row(Modifier.fillMaxWidth().height(48.dp).clickable { vm.g.goals.toggleStep(goal.id, i) }, verticalAlignment = Alignment.CenterVertically) {
                                GkIcon(if (step.done) Gk.circleCheck else Gk.clock, null, 20.dp, if (step.done) p.accent else p.textSecondary)
                                Spacer(Modifier.width(12.dp))
                                Text(step.text, style = Type.small.copy(fontSize = 15.sp), color = if (step.done) p.textSecondary else p.text)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Library ─────────────────────────────────────────────────────────────────

@Composable
fun LibraryScreen(vm: ChatViewModel, back: () -> Unit, navigate: (Route) -> Unit) {
    val p = P
    val projects by vm.g.build.store.list.collectAsState()
    val files = remember {
        vm.g.workspace.root.walkTopDown().filter { it.isFile }.sortedByDescending { it.lastModified() }.take(100).toList()
    }
    Column(Modifier.fillMaxSize().background(p.bg)) {
        ScreenHeader("Library", back)
        if (projects.isEmpty() && files.isEmpty()) {
            WorkspaceEmpty(Gk.library, "No files yet", "Files X creates or opens will appear here.")
        } else {
            LazyColumn(Modifier.weight(1f).navigationBarsPadding(), contentPadding = PaddingValues(bottom = 32.dp)) {
                if (projects.isNotEmpty()) {
                    item { Text("PROJECTS", style = Type.label.copy(letterSpacing = 0.08.sp), color = p.textSecondary, modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp)) }
                    items(projects, key = { it.id }) { project ->
                        WorkspaceCard(onClick = { vm.openProject(project.id); back() }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GkIcon(Gk.projects, null, 20.dp, p.textSecondary)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(project.name, style = Type.small.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${project.kind} · ${fmtTime(project.updatedAt)}", style = Type.tiny, color = p.muted)
                                }
                                GkIcon(Gk.chevronRight, null, 20.dp, p.textSecondary)
                            }
                        }
                    }
                }
                if (files.isNotEmpty()) {
                    item { Text("WORKSPACE FILES", style = Type.label.copy(letterSpacing = 0.08.sp), color = p.textSecondary, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)) }
                    items(files, key = { it.path }) { f ->
                        WorkspaceCard(onClick = { navigate(Route.Files) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GkIcon(Gk.file, null, 20.dp, p.textSecondary)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(f.name, style = Type.mono.copy(fontSize = 13.sp), color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${f.length() / 1024} KB · ${fmtTime(f.lastModified())}", style = Type.tiny, color = p.muted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Activity ────────────────────────────────────────────────────────────────

@Composable
fun ActivityScreen(vm: ChatViewModel, back: () -> Unit) {
    val p = P
    val runs by vm.g.taskRuns.records.collectAsState()
    val jobs by vm.g.jobRecords.records.collectAsState()
    val lines = remember(runs, jobs) {
        val out = ArrayList<Triple<Long, String, String>>()
        runs.forEach { out += Triple(it.updatedAt, "${it.kind} · ${it.status}", it.goal.ifBlank { "Task" }) }
        jobs.forEach { out += Triple(it.startedAt, "job · ${it.state}${it.exitCode?.let { c -> " · exit $c" } ?: ""}", it.command.take(200)) }
        out.sortedByDescending { it.first }
    }
    Column(Modifier.fillMaxSize().background(p.bg)) {
        ScreenHeader("Activity", back)
        if (lines.isEmpty()) {
            WorkspaceEmpty(Gk.clock, "No activity yet", "Runs and jobs will appear here as they happen.")
        } else {
            LazyColumn(Modifier.weight(1f).navigationBarsPadding(), contentPadding = PaddingValues(bottom = 32.dp)) {
                items(lines, key = { it.first.toString() + it.second + it.third.take(40) }) { (at, kind, text) ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Text(SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date(at)), style = Type.mono.copy(fontSize = 12.sp), color = p.muted, modifier = Modifier.width(52.dp))
                        Column {
                            Text(kind, style = Type.tiny.copy(fontWeight = FontWeight.Medium), color = p.textSecondary)
                            Text(text, style = Type.small.copy(fontSize = 14.sp, lineHeight = 20.sp), color = p.text)
                        }
                    }
                }
            }
        }
    }
}

// ── Computer ────────────────────────────────────────────────────────────────

@Composable
fun ComputerScreen(vm: ChatViewModel, back: () -> Unit, navigate: (Route) -> Unit) {
    val p = P
    val linuxReady = remember { runCatching { vm.g.linux.isInstalled() }.getOrDefault(false) }
    val fileCount = remember { runCatching { ProjectFiles.fileCount(vm.g.workspace.root) }.getOrDefault(0) }
    Column(Modifier.fillMaxSize().background(p.bg)) {
        ScreenHeader("Computer", back)
        Text("Workspace on this phone", style = Type.small.copy(fontSize = 14.sp), color = p.textSecondary, modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 12.dp))
        LazyColumn(Modifier.weight(1f).navigationBarsPadding(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                WorkspaceCard(onClick = { navigate(Route.Browser) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GkIcon(Gk.globe, null, 20.dp, p.textSecondary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Browser", style = Type.small.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = p.text)
                            Text("X's own browser — real pages, forms, screenshots", style = Type.small.copy(fontSize = 13.sp), color = p.textSecondary)
                        }
                        Text("Take control", style = Type.small.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium), color = p.accent)
                    }
                }
            }
            item {
                WorkspaceCard(onClick = { navigate(Route.Files) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GkIcon(Gk.folder, null, 20.dp, p.textSecondary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Files", style = Type.small.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = p.text)
                            Text(if (fileCount == 0) "No workspace files yet" else "$fileCount workspace files", style = Type.small.copy(fontSize = 13.sp), color = p.textSecondary)
                        }
                        GkIcon(Gk.chevronRight, null, 20.dp, p.textSecondary)
                    }
                }
            }
            item {
                WorkspaceCard(onClick = { navigate(Route.Terminal) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GkIcon(Gk.terminal, null, 20.dp, p.textSecondary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Terminal", style = Type.small.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = p.text)
                            Text(if (linuxReady) "PRoot shell — Linux is installed" else "PRoot shell — Linux is not installed yet", style = Type.mono.copy(fontSize = 13.sp), color = p.textSecondary)
                        }
                        GkIcon(Gk.chevronRight, null, 20.dp, p.textSecondary)
                    }
                }
            }
        }
    }
}

// ── Code diff ───────────────────────────────────────────────────────────────

@Composable
fun CodeDiffScreen(vm: ChatViewModel, projectId: String, back: () -> Unit) {
    val p = P
    val project = vm.g.build.store.get(projectId)
    val set = remember(projectId) { vm.changeSetFor(projectId) }
    var selected by remember(projectId) { mutableStateOf(set?.changes?.map { it.path }?.toSet().orEmpty()) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var applied by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(p.bg)) {
        ScreenHeader("Code changes", back)
        when {
            project == null -> WorkspaceEmpty(Gk.codeBlock, "Project not found", "This project was deleted.")
            set == null || set.isEmpty -> WorkspaceEmpty(Gk.codeBlock, "No changes waiting", "Nothing is waiting for review in ${project.name}.")
            applied -> WorkspaceEmpty(Gk.check, "Applied", "The selected changes are now in ${project.name}.")
            else -> {
        Text("${project.name} · +${set.totalAdded} −${set.totalRemoved} · ${set.changes.size} files", style = Type.small.copy(fontSize = 14.sp), color = p.textSecondary, modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp))
        LazyColumn(Modifier.weight(1f).navigationBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(set.changes, key = { it.path }) { change ->
                val isOpen = expanded == change.path
                val diffLines = remember(change.path, isOpen) {
                    if (!isOpen) emptyList()
                    else {
                        val (old, new) = vm.diffTextsFor(projectId, change)
                        if (old == null && new == null) emptyList()
                        else PatchEngine.diff(old.orEmpty(), new.orEmpty()).take(300)
                    }
                }
                WorkspaceCard(onClick = { expanded = if (isOpen) null else change.path }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(if (selected.contains(change.path)) p.accentSoft else p.surfaceSunken).clickable { selected = if (selected.contains(change.path)) selected - change.path else selected + change.path }, contentAlignment = Alignment.Center) {
                            if (selected.contains(change.path)) GkIcon(Gk.check, "Selected", 20.dp, p.accent)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(change.path, style = Type.mono.copy(fontSize = 13.sp), color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${change.status.name.lowercase()} · +${change.added} −${change.removed}", style = Type.tiny, color = p.textSecondary)
                        }
                        GkIcon(if (isOpen) Gk.chevronDown else Gk.chevronRight, null, 20.dp, p.textSecondary)
                    }
                    if (isOpen) {
                        Spacer(Modifier.height(12.dp))
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.codeBg).padding(vertical = 8.dp)) {
                            if (change.binary) {
                                Text("Binary file — no text diff.", style = Type.mono.copy(fontSize = 13.sp), color = p.codeText, modifier = Modifier.padding(horizontal = 12.dp))
                            } else if (diffLines.isEmpty()) {
                                Text("No text to show.", style = Type.mono.copy(fontSize = 13.sp), color = p.codeText, modifier = Modifier.padding(horizontal = 12.dp))
                            } else {
                                diffLines.forEach { line ->
                                    Row(Modifier.fillMaxWidth().background(when (line.op) { PatchEngine.Op.ADD -> p.accentSoft; PatchEngine.Op.DEL -> p.dangerSoft; PatchEngine.Op.SAME -> androidx.compose.ui.graphics.Color.Transparent }).padding(horizontal = 12.dp, vertical = 2.dp)) {
                                        Text(when (line.op) { PatchEngine.Op.ADD -> "+"; PatchEngine.Op.DEL -> "−"; PatchEngine.Op.SAME -> " " }, style = Type.mono.copy(fontSize = 13.sp), color = when (line.op) { PatchEngine.Op.ADD -> p.accent; PatchEngine.Op.DEL -> p.danger; PatchEngine.Op.SAME -> p.muted })
                                        Spacer(Modifier.width(8.dp))
                                        Text(line.text, style = Type.mono.copy(fontSize = 13.sp), color = p.codeText)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text("Tests pass. Nothing is committed.", style = Type.small.copy(fontSize = 13.sp), color = p.muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(8.dp)).background(if (selected.isEmpty()) p.surfaceSunken else p.accent).clickable(enabled = selected.isNotEmpty()) { vm.approveReviewFor(projectId, selected); applied = true }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                        Text("Apply ${selected.size} file${if (selected.size == 1) "" else "s"}", style = Type.small.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium), color = if (selected.isEmpty()) p.muted else p.onAccent)
                    }
                    TextButton(onClick = { vm.rejectReviewFor(projectId); back() }) { Text("Discard", color = p.danger) }
                }
            }
        }
            }
        }
    }
}
