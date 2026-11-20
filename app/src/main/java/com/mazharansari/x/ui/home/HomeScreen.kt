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
import androidx.compose.ui.text.style.TextAlign
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
 * MENU PANEL (navigation restructure, 2026-11-20 — user order, supersedes
 * the 2026-11-19 list-first home): the app opens on the single chat screen,
 * and this panel opens from the chat screen's top button. Top to bottom,
 * exactly in this order:
 *  1. a compact 64dp header (user monogram + name/status + Close, back to chat),
 *  2. ONE top row with exactly 4 options: Bots, Library, Connectors, Skills,
 *  3. the full chat-history list (every chat, pinned first, newest first;
 *     the search field appears only when the Search row below is tapped),
 *  4. below the history, in this order: Settings, New chat, Search.
 * No composer, no ToolsRow, no suggestion chips, no drawer, no Bots list
 * here (Bots live in their own section, Route.Bots — exactly one bot, X Bot).
 * Lists start empty — no seeded rows ever.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(vm: ChatViewModel, navigate: (Route) -> Unit, back: () -> Unit) {
    val p = P
    val settings by vm.g.settings.state.collectAsState()
    val connectors by vm.g.connectors.list.collectAsState()
    val index by vm.g.chats.index.collectAsState()
    val liveBots by vm.g.bots.bots.collectAsState()
    var q by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<ConversationMeta?>(null) }
    var renaming by remember { mutableStateOf<ConversationMeta?>(null) }

    val userName = settings.userName.ifBlank { "You" }
    val providerName = connectors.firstOrNull { it.enabled }
        ?.let { runCatching { it.provider.name }.getOrNull() }
    val metas = index.map {
        ChatListMeta(id = it.id, title = it.title, pinned = it.pinned, updatedAt = it.updatedAt,
            preview = it.preview, section = it.section, botId = it.botId, botName = it.botName)
    }
    val workingIds = liveBots.filter { it.status == BotStatus.STARTING || it.status == BotStatus.WORKING }
        .mapNotNull { it.parentId ?: it.conversationId }.toSet()
    val chatRows = buildChatRows(metas, workingIds)
    val filteredChats = chatRows.filter { q.isBlank() || it.displayName.contains(q, true) || it.preview.contains(q, true) }
    val parents = liveBots.filter { it.kind == BotKind.PARENT }
    val workers = liveBots.filter { it.kind == BotKind.WORKER }

    Column(Modifier.fillMaxSize().background(p.bg)) {
        // ── Header (64dp): monogram + name/status + Close (back to the chat) ──
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 12.dp, top = 12.dp).height(64.dp),
            verticalAlignment = Alignment.CenterVertically) {
            BotLogoView("", userName, 40.dp, Modifier.clickable { navigate(Route.Customize) })
            Column(Modifier.weight(1f).padding(start = 12.dp).clickable { navigate(Route.Customize) }) {
                Text(userName, style = Type.row, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Ready · ${providerName ?: "No key yet"}", style = Type.mono, color = p.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconTap(Gk.close, "Close menu", 20.dp, p.text) { back() }
        }
        // ── Top row: exactly 4 options — Bots, Library, Connectors, Skills ──
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly) {
            MenuTopOption(Gk.agent, "Bots") { navigate(Route.Bots) }
            MenuTopOption(Gk.library, "Library") { navigate(Route.Library) }
            MenuTopOption(Gk.connectors, "Connectors") { navigate(Route.Connectors) }
            MenuTopOption(Gk.skills, "Skills") { navigate(Route.Skills) }
        }
        // ── Search field: only while the Search row below is open ──
        if (searchOpen) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(48.dp)
                .clip(RoundedCornerShape(8.dp)).background(p.surface).border(1.dp, p.border, RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                GkIcon(Gk.search, null, 20.dp, p.text2)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    BasicTextField(q, { q = it }, singleLine = true, textStyle = Type.body.copy(color = p.text),
                        cursorBrush = SolidColor(p.text), modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            Box {
                                if (q.isEmpty()) Text("Search chats", style = Type.body, color = p.text3)
                                inner()
                            }
                        })
                }
                if (q.isNotEmpty()) IconTap(Gk.close, "Clear search", 18.dp, p.text2) { q = "" }
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)) {
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
            // ── CHATS: the full history ──
            item(key = "h_chats") {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("CHATS", style = Type.eyebrow, color = p.text2, modifier = Modifier.weight(1f))
                }
            }
            if (filteredChats.isEmpty()) {
                item(key = "chats_empty") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(if (q.isBlank()) "No chats yet — tap New chat below." else "No chats match \"$q\".",
                            style = Type.small, color = p.text3)
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
            // ── Below the history, in this order: Settings, New chat, Search ──
            item(key = "m_settings") {
                MenuBottomRow(Gk.settings, "Settings") { navigate(Route.Settings) }
            }
            item(key = "m_new") {
                MenuBottomRow(Gk.newChat, "New chat") { vm.newChat(); navigate(Route.Chat) }
            }
            item(key = "m_search") {
                MenuBottomRow(Gk.search, "Search") { searchOpen = !searchOpen; if (!searchOpen) q = "" }
            }
        }
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

/** One of the 4 top options: line icon above a 12sp label, equal width. */
@Composable
private fun MenuTopOption(icon: Int, label: String, onClick: () -> Unit) {
    val p = P
    Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
        .padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        GkIcon(icon, label, 22.dp, p.text)
        Spacer(Modifier.height(6.dp))
        Text(label, style = Type.small.copy(fontSize = 12.sp), color = p.text, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/** One of the 3 bottom rows: 56dp, line icon + 15sp label, hairline below. */
@Composable
private fun MenuBottomRow(icon: Int, label: String, onClick: () -> Unit) {
    val p = P
    Row(Modifier.fillMaxWidth().height(56.dp).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        GkIcon(icon, label, 20.dp, p.text)
        Spacer(Modifier.width(14.dp))
        Text(label, style = Type.row, color = p.text, modifier = Modifier.weight(1f))
        GkIcon(Gk.chevronRight, null, 18.dp, p.text3)
    }
    Hairline()
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
