package com.mazharansari.x.ui.bots

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazharansari.x.agent.BotRun
import com.mazharansari.x.agent.BotStatus
import com.mazharansari.x.data.BotCatalog
import com.mazharansari.x.data.X_BOT_ID
import com.mazharansari.x.ui.ChatViewModel
import com.mazharansari.x.ui.Route
import com.mazharansari.x.ui.chat.AxionSheet
import com.mazharansari.x.ui.components.Gk
import com.mazharansari.x.ui.components.GkIcon
import com.mazharansari.x.ui.settings.SettingsPage
import com.mazharansari.x.ui.theme.P
import com.mazharansari.x.ui.theme.Type

/**
 * THE BOTS SECTION (navigation restructure, 2026-11-20): WhatsApp-style,
 * exactly ONE bot — X Bot, the user's own main bot. No list of many bots,
 * no store catalog any more: the 51 specialists in [BotCatalog] are the
 * roster X Bot spawns from, not bots the user chats with.
 *
 * - Tapping X Bot's row opens THAT bot's own chat ([Route.BotChat]); Back
 *   from there returns here, to the bot list — never to home or a new chat.
 * - Tapping X Bot's PROFILE (its icon) opens profile editing (name + icon:
 *   monogram, static image, 3D, video).
 * - BELOW X Bot, "Under X Bot": the sub-bots X Bot spawned for the current
 *   job, each with its roster name, task and live status. Tapping a sub-bot
 *   opens its (read-only) worker transcript; tapping its PROFILE opens the
 *   same profile editing. The user only CHATS with X Bot.
 * - "Specialists · 50" opens the full roster sheet (read-only).
 */
@Composable
fun BotStoreScreen(vm: ChatViewModel, back: () -> Unit, navigate: (Route) -> Unit) {
    val p = P
    val definitions by vm.g.botStore.definitions.collectAsState()
    val liveBots by vm.g.bots.bots.collectAsState()
    // Re-render when a worker profile is edited.
    @Suppress("UNUSED_VARIABLE")
    val profiles by vm.g.bots.workerProfiles.collectAsState()
    LaunchedEffect(Unit) { vm.g.botStore.ensureXBot() }
    val xBot = definitions.firstOrNull { it.id == X_BOT_ID }
    val xThread = if (xBot == null) null else vm.botThreadMeta(X_BOT_ID)
    val subBots = liveBots.filter { it.kind == com.mazharansari.x.agent.BotKind.WORKER && it.parentId == xThread?.id }
    var editingX by remember { mutableStateOf(false) }
    var editingWorker by remember { mutableStateOf<BotRun?>(null) }
    var rosterOpen by remember { mutableStateOf(false) }
    if (xBot == null) return

    SettingsPage("Bots", back) {
        Text("X Bot is your only bot. Tell it anything — it does the job itself, or spawns specialist sub-bots under itself, collects all their results, and answers you itself.",
            fontSize = 13.sp, color = p.text2, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp))
        // ── X Bot row ──
        Row(Modifier.fillMaxWidth().height(64.dp)
            .clickable { vm.openBotChat(xBot); navigate(Route.BotChat(xBot.id)) }
            .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            // The PROFILE (icon) opens profile editing; the row opens the chat.
            Box(Modifier.semantics { contentDescription = "X Bot profile" }.clickable { editingX = true }) {
                BotLogoView(xBot.logo, xBot.name, 44.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(xBot.name, style = Type.row, color = p.text, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (xThread != null) Text(com.mazharansari.x.ui.chat.chatTimeLabel(xThread.updatedAt), style = Type.mono, color = p.text3)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    if (xThread == null) {
                        // §H partial, fixed here: threadless bot rows carry a
                        // "New" 12sp mono muted label instead of the description.
                        Text("New", style = Type.mono, color = p.text3)
                    } else {
                        Text(xThread.preview.ifBlank { xBot.description },
                            style = Type.small, color = p.text2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        Hairline()
        // ── Specialists row ──
        Row(Modifier.fillMaxWidth().height(56.dp).clickable { rosterOpen = true }.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            GkIcon(Gk.agent, "Specialists", 20.dp, p.text)
            Spacer(Modifier.width(14.dp))
            Text("Specialists · ${BotCatalog.seeds.size}", style = Type.row, color = p.text, modifier = Modifier.weight(1f))
            Text("X Bot spawns these", style = Type.small, color = p.text2)
            Spacer(Modifier.width(8.dp))
            GkIcon(Gk.chevronRight, null, 18.dp, p.text3)
        }
        Hairline()
        // ── UNDER X BOT: the sub-bots of the current job ──
        Text("UNDER X BOT", style = Type.eyebrow, color = p.text2,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp))
        if (subBots.isEmpty()) {
            Text("No sub-bots yet — give X Bot a big job and its specialists will appear here, under X Bot. You only ever chat with X Bot.",
                style = Type.small, color = p.text3, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        } else {
            subBots.forEach { w ->
                val displayName = vm.g.bots.displayName(w)
                val displayLogo = vm.g.bots.displayLogo(w)
                Row(Modifier.fillMaxWidth().height(64.dp)
                    .clickable { vm.openWorkerChat(w.conversationId, w.parentId, xBot.name); navigate(Route.Chat) }
                    .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.semantics { contentDescription = "$displayName profile" }.clickable { editingWorker = w }) {
                        BotLogoView(displayLogo, displayName, 44.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(displayName, style = Type.row, color = p.text, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(w.status.label, style = Type.mono,
                                color = when (w.status) {
                                    BotStatus.WORKING -> p.accent
                                    BotStatus.FAILED -> p.danger
                                    else -> p.text3
                                })
                        }
                        Text(w.task.ifBlank { "Working…" }, style = Type.small, color = p.text2, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                Hairline()
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    // ── Profile editing: X Bot (persisted) ──
    if (editingX) {
        BotProfileSheet(botKey = xBot.id, currentName = xBot.name, currentLogo = xBot.logo,
            onSave = { name, logo ->
                vm.g.botStore.rename(xBot.id, name)
                vm.g.botStore.setLogo(xBot.id, logo)
                editingX = false
            },
            onDismiss = { editingX = false })
    }
    // ── Profile editing: a sub-bot (in-memory worker profile) ──
    editingWorker?.let { w ->
        val displayName = vm.g.bots.displayName(w)
        val displayLogo = vm.g.bots.displayLogo(w)
        BotProfileSheet(botKey = w.id, currentName = displayName, currentLogo = displayLogo,
            onSave = { name, logo ->
                vm.g.bots.setWorkerProfile(w.id, name, logo)
                editingWorker = null
            },
            onDismiss = { editingWorker = null })
    }
    // ── Roster sheet: all 51 specialists X Bot can spawn ──
    if (rosterOpen) {
        AxionSheet({ rosterOpen = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
                Text("Specialists · ${BotCatalog.seeds.size}", style = Type.title.copy(fontSize = 17.sp), color = p.text,
                    modifier = Modifier.padding(start = 20.dp, bottom = 4.dp))
                Text("X Bot spawns these under itself for the parts of a big job, then answers you itself.",
                    style = Type.small, color = p.text2, modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp))
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
                    BotCatalog.seeds.forEach { seed ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            BotLogoView(seed.logo, seed.name, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(seed.name, style = Type.row, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(seed.description, style = Type.small, color = p.text2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 1dp hairline separating ledger rows (inset past the tile, WhatsApp-style). */
@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().padding(start = 72.dp).height(1.dp).background(P.border))
}
