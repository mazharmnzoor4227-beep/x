package com.mazharansari.x.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazharansari.x.agent.BotKind
import com.mazharansari.x.agent.BotRun
import com.mazharansari.x.agent.BotStatus
import com.mazharansari.x.data.ConversationMeta
import com.mazharansari.x.ui.ChatViewModel
import com.mazharansari.x.ui.Route
import com.mazharansari.x.ui.bots.BotLogoView
import com.mazharansari.x.ui.bots.CreateBotSheet
import com.mazharansari.x.ui.chat.AxionSheet
import com.mazharansari.x.ui.chat.ChatListMeta
import com.mazharansari.x.ui.chat.SheetRow
import com.mazharansari.x.ui.chat.buildChatRows
import com.mazharansari.x.ui.chat.chatTimeLabel
import com.mazharansari.x.ui.components.Avatar
import com.mazharansari.x.ui.components.Gk
import com.mazharansari.x.ui.components.GkIcon
import com.mazharansari.x.ui.components.IconTap
import com.mazharansari.x.ui.theme.P
import com.mazharansari.x.ui.theme.Type

/**
 * Home (redesign round 2, spec §1.4): the app opens HERE, on a list — never
 * on an empty chat. Top to bottom: a compact 64dp settings block (user
 * monogram + name/status + Workspace grid + Settings gear), a 48dp search
 * that filters both lists, the BOTS section (WhatsApp-style, every installed
 * + created bot, tapping one opens THAT bot's own chat inside the Bots
 * section), then the CHATS section (full history, pinned first, newest
 * first). No composer, no ToolsRow, no suggestion chips, no drawer. Lists
 * start empty — no seeded rows ever.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(vm: ChatViewModel, navigate: (Route) -> Unit) {
    val p = P
    val settings by vm.g.settings.state.collectAsState()
    val connectors by vm.g.connectors.list.collectAsState()
    val bots by vm.g.botStore.definitions.collectAsState()
    val index by vm.g.chats.index.collectAsState()
    val liveBots by vm.g.bots.bots.collectAsState()
    val activeRuns by vm.g.runs.active.collectAsState()
    val runRecords by vm.g.taskRuns.records.collectAsState()
    val ideaCount by vm.g.ideas.ideas.collectAsState()
    val goalCount by vm.g.goals.goals.collectAsState()
    var q by remember { mutableStateOf("") }
    var workspaceOpen by remember { mutableStateOf(false) }
    var createBot by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<ConversationMeta?>(null) }
    var renaming by remember { mutableStateOf<ConversationMeta?>(null) }

    val userName = settings.userName.ifBlank { "You" }
    val providerName = connectors.firstOrNull { it.enabled }
        ?.let { runCatching { it.provider.name }.getOrNull() }
    val metas = index.map {
        ChatListMeta(id = it.id, title = it.title, pinned = it.pinned, updatedAt = it.updatedAt,
            preview = it.preview, section = it.section, botId = it.botId, botName = it.botName)
    }
    val workingIds = activeRuns.mapNotNull { it.ownerId }.toSet()
    val chatRows = buildChatRows(metas, workingIds)
    val filteredBots = bots.filter { q.isBlank() || it.name.contains(q, true) || it.description.contains(q, true) }
    val filteredChats = chatRows.filter { q.isBlank() || it.displayName.contains(q, true) || it.preview.contains(q, true) }
    val parents = liveBots.filter { it.kind == BotKind.PARENT }
    val workers = liveBots.filter { it.kind == BotKind.WORKER }
    val feedCount = (if (vm.pendingApproval != null) 1 else 0) + runRecords.count { it.status == "running" }

    Column(Modifier.fillMaxSize().background(p.bg)) {
        // ── Settings block (64dp): monogram + name/status + Workspace grid + Settings gear ──
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 12.dp, top = 12.dp).height(64.dp),
            verticalAlignment = Alignment.CenterVertically) {
            BotLogoView("", userName, 40.dp, Modifier.clickable { navigate(Route.Customize) })
            Column(Modifier.weight(1f).padding(start = 12.dp).clickable { navigate(Route.Customize) }) {
                Text(userName, style = Type.row, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Ready · ${providerName ?: "No key yet"}", style = Type.mono, color = p.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconTap(Gk.grid, "Workspace", 20.dp, p.text) { workspaceOpen = true }
            Spacer(Modifier.width(4.dp))
            IconTap(Gk.settings, "Settings", 20.dp, p.text) { navigate(Route.Settings) }
        }
        // ── Search (48dp): filters Bots + Chats inline ──
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp)
            .clip(RoundedCornerShape(8.dp)).background(p.surface).border(1.dp, p.border, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            GkIcon(Gk.search, null, 20.dp, p.text2)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                BasicTextField(q, { q = it }, singleLine = true, textStyle = Type.body.copy(color = p.text),
                    cursorBrush = SolidColor(p.text), modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box {
                            if (q.isEmpty()) Text("Search chats and bots", style = Type.body, color = p.text3)
                            inner()
                        }
                    })
            }
            if (q.isNotEmpty()) IconTap(Gk.close, "Clear search", 18.dp, p.text2) { q = "" }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)) {
            // ── RUNNING NOW: live parents/workers only while runs are live ──
            if (parents.isNotEmpty() || workers.isNotEmpty()) {
                item(key = "h_running") { SectionHead("Running now") }
                items(parents, key = { "run-${it.conversationId}" }) { parent ->
                    RunRow(parent, parent.name.ifBlank { "X" }, parent.task, onOpen = { vm.open(parent.conversationId); navigate(Route.Chat) })
                    Hairline()
                }
                items(workers, key = { "run-${it.conversationId}" }) { w ->
                    val parentName = parents.firstOrNull { it.conversationId == w.parentId }?.name.orEmpty()
                    RunRow(w, (w.role?.label ?: "Worker") + if (parentName.isNotBlank()) " · $parentName" else "",
                        w.task, onOpen = { vm.openWorkerChat(w.conversationId, w.parentId, parentName.ifBlank { null }); navigate(Route.Chat) })
                    Hairline()
                }
            }
            // ── BOTS ──
            item(key = "h_bots") {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("BOTS", style = Type.eyebrow, color = p.text2, modifier = Modifier.weight(1f))
                    Text("Bot Store", style = Type.label, color = p.text2,
                        modifier = Modifier.clickable { navigate(Route.BotStore) }.padding(horizontal = 8.dp, vertical = 8.dp))
                    Text("New bot", style = Type.label, color = p.accent,
                        modifier = Modifier.clickable { createBot = true }.padding(horizontal = 8.dp, vertical = 8.dp))
                }
            }
            if (filteredBots.isEmpty()) {
                item(key = "bots_empty") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("No bots yet — install one from the Bot Store, or create your own.",
                            style = Type.small, color = p.text3)
                        TextButton(onClick = { navigate(Route.BotStore) }, contentPadding = PaddingValues(0.dp)) {
                            Text("Open Bot Store", color = p.accent, style = Type.label)
                        }
                    }
                }
            } else {
                items(filteredBots, key = { "bot-${it.id}" }) { bot ->
                    val thread = vm.botThreadMeta(bot.id)
                    val working = thread != null && workingIds.contains(thread.id)
                    Row(Modifier.fillMaxWidth().height(64.dp)
                        .clickable { vm.openBotChat(bot); navigate(Route.BotChat(bot.id)) }
                        .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        BotLogoView(bot.logo, bot.name, 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(bot.name, style = Type.row, color = p.text, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                if (thread != null) Text(chatTimeLabel(thread.updatedAt), style = Type.mono, color = p.text3)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                if (working) { Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(p.accent)); Spacer(Modifier.width(6.dp)) }
                                Text(if (working) "Working…" else thread?.preview?.ifBlank { bot.description } ?: bot.description,
                                    style = Type.small, color = p.text2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    Hairline()
                }
            }
            // ── CHATS ──
            item(key = "h_chats") {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("CHATS", style = Type.eyebrow, color = p.text2, modifier = Modifier.weight(1f))
                    Text("New chat", style = Type.label, color = p.accent,
                        modifier = Modifier.clickable { vm.newChat(); navigate(Route.Chat) }.padding(horizontal = 8.dp, vertical = 8.dp))
                }
            }
            if (filteredChats.isEmpty()) {
                item(key = "chats_empty") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("No chats yet — tap New chat, or open a bot above.", style = Type.small, color = p.text3)
                        TextButton(onClick = { vm.newChat(); navigate(Route.Chat) }, contentPadding = PaddingValues(0.dp)) {
                            Text("New chat", color = p.accent, style = Type.label)
                        }
                    }
                }
            } else {
                items(filteredChats, key = { it.id }) { row ->
                    val meta = index.firstOrNull { it.id == row.id }
                    Row(Modifier.fillMaxWidth().height(64.dp)
                        .combinedClickable(
                            onClick = { vm.open(row.id); navigate(Route.Chat) },
                            onLongClick = { meta?.let { menuFor = it } })
                        .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (row.botId.isNotBlank()) BotLogoView(row.botLogo, row.botName.ifBlank { row.title }, 44.dp)
                        else Avatar(row.title.ifBlank { "A" }, 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(row.displayName, style = Type.row, color = p.text, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                if (row.pinned) { GkIcon(Gk.pin, "Pinned", 14.dp, p.text2); Spacer(Modifier.width(6.dp)) }
                                Text(chatTimeLabel(row.updatedAt), style = Type.mono, color = if (row.working) p.accent else p.text3)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                if (row.working) { Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(p.accent)); Spacer(Modifier.width(6.dp)) }
                                Text(if (row.working) "Working…" else row.preview.ifBlank { "No messages yet" },
                                    style = Type.small, color = p.text2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    Hairline()
                }
            }
        }
    }
    // ── Workspace sheet: the single home for the 7 workspace screens ──
    if (workspaceOpen) {
        AxionSheet({ workspaceOpen = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
                Text("Workspace", style = Type.title, color = p.text, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
                SheetRow(Gk.history, "Feed", subtitle = if (feedCount > 0) "$feedCount need you" else "Nothing needs you",
                    onClick = { workspaceOpen = false; navigate(Route.Feed) })
                SheetRow(Gk.sparkles, "Ideas", subtitle = if (ideaCount.size > 0) "${ideaCount.size} saved" else "No ideas yet",
                    onClick = { workspaceOpen = false; navigate(Route.Ideas) })
                SheetRow(Gk.circleCheck, "Goals", subtitle = if (goalCount.size > 0) "${goalCount.size} active" else "No goals yet",
                    onClick = { workspaceOpen = false; navigate(Route.Goals) })
                SheetRow(Gk.library, "Library", subtitle = "Files, notes and downloads",
                    onClick = { workspaceOpen = false; navigate(Route.Library) })
                SheetRow(Gk.clock, "Activity", subtitle = "Runs, tasks and history",
                    onClick = { workspaceOpen = false; navigate(Route.Activity) })
                SheetRow(Gk.terminal, "Computer", subtitle = "Projects, terminal and files",
                    onClick = { workspaceOpen = false; navigate(Route.Computer) })
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    // ── New bot sheet ──
    if (createBot) {
        CreateBotSheet(vm,
            onCreated = { created -> vm.openBotChat(created); navigate(Route.BotChat(created.id)) },
            onDismiss = { createBot = false })
    }
    // ── Long-press menu on a chat row ──
    menuFor?.let { c ->
        AxionSheet({ menuFor = null }) {
            SheetRow(Gk.trash, "Delete", color = p.danger, onClick = { vm.g.chats.delete(c.id); if (vm.conversationId == c.id) vm.newChat(); menuFor = null })
            SheetRow(Gk.pencil, "Rename", onClick = { renaming = c; menuFor = null })
            SheetRow(Gk.pin, if (c.pinned) "Unpin" else "Pin", onClick = { vm.g.chats.pin(c.id, !c.pinned); menuFor = null })
            SheetRow(Gk.share, "Share", onClick = { menuFor = null; vm.shareConversation(c.id) })
        }
    }
    renaming?.let { c ->
        var t by remember(c.id) { mutableStateOf(c.title) }
        AlertDialog(onDismissRequest = { renaming = null }, containerColor = p.card, shape = RoundedCornerShape(10.dp),
            title = { Text("Rename", style = Type.title, color = p.text) },
            text = {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.page2).padding(14.dp)) {
                    BasicTextField(t, { t = it }, singleLine = true, textStyle = Type.body.copy(color = p.text),
                        cursorBrush = SolidColor(p.text), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton({ vm.g.chats.rename(c.id, t.trim().ifBlank { c.title }); if (vm.conversationId == c.id) vm.title = t; renaming = null }) { Text("Save", color = p.text, style = Type.label) } },
            dismissButton = { TextButton({ renaming = null }) { Text("Cancel", color = p.text2, style = Type.label) } })
    }
}

@Composable
private fun SectionHead(text: String) {
    Text(text.uppercase(), style = Type.eyebrow, color = P.text2,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp))
}

/** 1dp hairline separating ledger rows (inset past the tile, WhatsApp-style). */
@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().padding(start = 72.dp).height(1.dp).background(P.border))
}

private fun statusLabel(status: BotStatus): String = when (status) {
    BotStatus.STARTING -> "Starting"
    BotStatus.WORKING -> "Working"
    BotStatus.DONE -> "Done"
    BotStatus.FAILED -> "Failed"
}

/** A live parent/worker row in RUNNING NOW: monogram, name, task, live status. */
@Composable
private fun RunRow(run: BotRun, name: String, task: String, onOpen: () -> Unit) {
    val p = P
    Row(Modifier.fillMaxWidth().height(64.dp).clickable(onClick = onOpen).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        BotLogoView("", name, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = Type.row, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("RUNNING", style = Type.mono, color = p.accent)
            }
            Text(task.ifBlank { "Working…" }, style = Type.small, color = p.text2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(
                    when (run.status) {
                        BotStatus.WORKING -> p.accent
                        BotStatus.FAILED -> p.danger
                        BotStatus.DONE -> p.text2
                        else -> p.text3
                    }))
                Spacer(Modifier.width(6.dp))
                Text("${statusLabel(run.status)} · ${run.stepsUsed} step${if (run.stepsUsed == 1) "" else "s"}",
                    style = Type.mono, color = p.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
