package com.mazharansari.x.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.mazharansari.x.data.*
import com.mazharansari.x.llm.ModelChoice
import com.mazharansari.x.ui.ChatViewModel
import com.mazharansari.x.ui.Route
import com.mazharansari.x.ui.components.*
import com.mazharansari.x.ui.home.FileActions
import com.mazharansari.x.ui.home.openFile
import com.mazharansari.x.ui.home.shareFile
import com.mazharansari.x.ui.theme.P
import com.mazharansari.x.ui.theme.Type
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.launch
import java.io.File

/**
 * Bot-chat subtitle: "Bot · <provider>" plus the thread state — "· New
 * thread" when the thread has no messages yet, otherwise when the thread
 * started ("· started 2h ago"). Pure so tests can pin the wording.
 * [firstMessageAt] is the first message's createdAt (0 = unknown: no suffix).
 */
fun botThreadSubtitle(provider: String, messageCount: Int, firstMessageAt: Long, nowMs: Long = System.currentTimeMillis()): String {
    val base = "Bot · ${provider.ifBlank { "Auto" }}"
    if (messageCount == 0) return "$base · New thread"
    if (firstMessageAt <= 0) return base
    val ageMin = ((nowMs - firstMessageAt).coerceAtLeast(0) / 60_000)
    val age = when {
        ageMin < 1 -> "just now"
        ageMin < 60 -> "$ageMin min ago"
        ageMin < 60 * 24 -> "${ageMin / 60}h ago"
        else -> "${ageMin / (60 * 24)}d ago"
    }
    return "$base · started $age"
}

/**
 * THE one chat screen (redesign round 2, docs/X-REDESIGN2-SPEC-2026-11-19.md):
 * menu glyph (root chat) / back chevron (bot chat) + wordmark/bot-monogram header, plain-text assistant messages,
 * right-aligned user bubbles, run-steps timeline, 12dp composer with ToolsRow,
 * NO drawer, NO token display, NO cartoon logo. The real ChatViewModel drives
 * everything (vm.messages, vm.send(), vm.running, vm.draft); the chat starts
 * empty — no seed lines. Colours come from XTheme (P.*), system sans + mono
 * throughout (Type.*). Bot mode: pass [bot] and the header shows that bot's
 * monogram + name; [onBack] pops to Home (the Bots section).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(vm: ChatViewModel, navigate: (Route) -> Unit, onBack: () -> Unit = {}, bot: BotDefinition? = null) {
    val p = P
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val g = vm.g
    val settings by g.settings.state.collectAsState()
    val connectors by g.connectors.list.collectAsState()
    val agyStore by g.agy.store.collectAsState()
    // Model chooser (2026-11-20): the rows come from the providers' real configured models/accounts; remembered
    // so the token check (a small file walk per account) does not re-run on every streaming recomposition.
    val chooserEntries = remember(connectors, agyStore.accounts) { ModelChoice.buildChooserEntries(connectors, agyStore.accounts, g.agy::hasToken) }
    val pickedLabel = remember(settings.pickedModel, settings.mode, chooserEntries) { ModelChoice.displayLabelFromEntries(settings.pickedModel, settings.mode, chooserEntries) }
    val listening by g.speech.listening.collectAsState()
    val finishing by g.speech.finishing.collectAsState()
    val partial by g.speech.partial.collectAsState()
    val dictation by g.speech.state.collectAsState()
    LaunchedEffect(dictation.error) { dictation.error?.let { vm.toast = it } }
    val speakingId by g.speaker.speakingId.collectAsState()
    var overflowOpen by remember { mutableStateOf(false) }
    var renamingChat by remember { mutableStateOf(false) }
    var plus by remember { mutableStateOf(vm.debugPopover == 1) }
    var model by remember { mutableStateOf(vm.debugPopover == 2) }
    LaunchedEffect(vm.debugPopover) { if (vm.debugPopover == 1) plus = true; if (vm.debugPopover == 2) model = true }
    var thoughts by remember { mutableStateOf(vm.debugThoughts) }
    val list = rememberLazyListState()
    val messages = vm.messages
    val haze = remember { HazeState() }
    val density = LocalDensity.current
    var boxBottomPx by remember { mutableFloatStateOf(0f) }
    var boxTopPx by remember { mutableFloatStateOf(0f) }
    var composerTopPx by remember { mutableFloatStateOf(0f) }

    val pickSkill = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) vm.importSkills(uris) }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> uris.forEach { vm.addAttachment(it) } }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris -> uris.forEach { vm.addAttachment(it) } }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) cameraFile?.let { vm.addFile(it) } }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) g.speech.start() else vm.toast = "Mic permission is needed for dictation"
    }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (!ok) vm.toast = "Camera permission is needed" }

    val last = messages.lastOrNull()
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(list.isScrollInProgress) { if (!list.isScrollInProgress) follow = !list.canScrollForward }
    var restoredFor by remember { mutableStateOf<String?>(null) }
    // Crash hunt (2026-11-20): capture the id — the old onDispose read
    // vm.conversationId live, which is already the NEW chat by dispose time,
    // so chat A's scroll position was saved under chat B's id and B reopened
    // at a stale deep position.
    val openConversationId = vm.conversationId
    DisposableEffect(openConversationId) {
        onDispose { vm.saveScroll(openConversationId, list.firstVisibleItemIndex) }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            if (restoredFor != vm.conversationId) {
                restoredFor = vm.conversationId
                val idx = vm.scrollFor(vm.conversationId)
                // Crash hunt (2026-11-20): clamp — a saved index from before the
                // chat was shortened (edit/regenerate) must never address an
                // item that no longer exists.
                if (idx > 0 && !vm.running) { list.scrollToItem(idx.coerceIn(0, messages.lastIndex)); follow = false }
                else { follow = true; list.animateScrollToItem(messages.lastIndex) }
            } else { follow = true; list.animateScrollToItem(messages.lastIndex) }
        }
    }
    LaunchedEffect(last?.text?.length, last?.steps?.size, last?.artifacts?.size) {
        if (vm.running && follow && messages.isNotEmpty()) list.scrollToItem(messages.lastIndex, 1_000_000)
    }
    val scope = rememberCoroutineScope()

    // Read-aloud pill, pull-up-for-new-chat and in-app PDF viewer
    val ra = com.mazharansari.x.voice.ReadAloudPlayer
    val imeVisible = WindowInsets.isImeVisible
    val pillVisible = speakingId != null && messages.any { it.id == speakingId } && !imeVisible
    var speedOpen by remember { mutableStateOf(vm.debugSpeedMenu) }
    LaunchedEffect(pillVisible) { if (!pillVisible) speedOpen = false }
    var speedAnchor by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    LaunchedEffect(vm.debugViewer) { vm.debugViewer?.let { n -> vm.viewingPdf = messages.flatMap { it.artifacts }.firstOrNull { it.name == n } } }
    BackHandler(vm.viewingWorkerTranscript) { vm.closeWorkerChat() }

    val onResearch: () -> Unit = {
        val s = g.settings.state.value
        if (!s.deepResearch && s.toolSwitches["research"] == false) {
            g.settings.update { it.copy(deepResearch = true, toolSwitches = it.toolSwitches + ("research" to true)) }
            vm.toast = "Deep research turned back on in Tools library."
        } else {
            val on = !s.deepResearch
            g.settings.update { it.copy(deepResearch = on) }
            vm.toast = if (on) "Deep research ON — your next message will use deep research." else "Deep research OFF."
        }
    }
    val onLongTask: () -> Unit = {
        val on = !settings.longTask
        g.settings.update { it.copy(longTask = on) }
        vm.toast = if (on) "Long task ON — your next message will run as a long task." else "Long task OFF."
    }
    val onTurbo: () -> Unit = {
        val on = !settings.turbo
        g.settings.update { it.copy(turbo = on) }
        vm.toast = if (on) "Turbo ON — your next task runs on your server." else "Turbo OFF."
    }

    CompositionLocalProvider(LocalHaze provides haze) {
        Box(Modifier.fillMaxSize().background(p.bg).imePadding().onGloballyPositioned { val b = it.boundsInRoot(); boxBottomPx = b.bottom; boxTopPx = b.top }) {
            Column(Modifier.fillMaxSize()) {
                // ── Header (round 2 §1.5): menu/back + wordmark/monogram + title + status | new chat + overflow ──
                // No hamburger, no drawer, no centred logo stack. Title tap opens the model picker, as before.
                // Root chat (plain X, bot == null): the leading glyph is a MENU — it opens the menu panel
                // (Route.Home, AppRoot's onBack). Bot chat: a back chevron pops to the bot list. The glyph
                // is icon-only; the behaviour (onBack) is unchanged.
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)) {
                    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (bot != null) IconTap(Gk.arrowLeft, "Back", 20.dp, p.text) { onBack() }
                        else IconTap(Gk.menu, "Menu", 20.dp, p.text) { onBack() }
                        Spacer(Modifier.width(4.dp))
                        if (bot != null) {
                            com.mazharansari.x.ui.bots.BotLogoView(bot.logo, bot.name, 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f).clickable(remember { MutableInteractionSource() }, null) { model = true }) {
                                Text(bot.name, style = Type.row.copy(fontSize = 16.sp), color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(botThreadSubtitle(bot.suggestedProvider ?: "", messages.size, messages.firstOrNull()?.createdAt ?: 0), style = Type.mono, color = p.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        } else {
                            Wordmark(fontSize = 22.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f).clickable(remember { MutableInteractionSource() }, null) { model = true }) {
                                Text(vm.title.ifBlank { "New chat" }, style = Type.row.copy(fontSize = 16.sp), color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (vm.running) "Thinking…" else pickedLabel, style = Type.mono, color = p.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (bot == null) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).clickable { vm.newChat() }, contentAlignment = Alignment.Center) {
                                GkIcon(Gk.newChat, "New chat", 20.dp, p.text)
                            }
                        }
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).clickable { overflowOpen = true }, contentAlignment = Alignment.Center) {
                            GkIcon(Gk.more, "More options", 20.dp, p.text)
                        }
                    }
                }
                // ── Message list ──
                Box(Modifier.weight(1f).fillMaxWidth().haze(haze)) {
                    if (messages.isEmpty()) {
                        EmptyChat(noKeys = connectors.isEmpty(), researchOn = settings.deepResearch, onResearch = onResearch,
                            longTaskOn = settings.longTask, onLongTask = onLongTask, turboOn = settings.turbo, onTurbo = onTurbo,
                            onConnect = { navigate(Route.Connectors) }, onSkills = { navigate(Route.Skills) },
                            onVoice = { navigate(Route.VoiceCall) })
                    } else {
                        LazyColumn(state = list, modifier = Modifier.fillMaxSize(),
                            // Bottom 24dp: the last line must clear the chips row
                            // with room to spare — with only 12dp it could rest
                            // half under the list's bottom edge (the "black layer"
                            // over the reply in the 2026-11-20 video).
                            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                            items(messages, key = { it.id }, contentType = { it.role }) { m ->
                                if (m.role == Role.USER) UserBubble(m)
                                else AssistantMessage(
                                    m, isLast = m.id == messages.last().id, speaking = speakingId == m.id,
                                    onOpenThoughts = { thoughts = m.id },
                                    onCopy = { vm.toast = if (com.mazharansari.x.security.SecureClipboard.copy(ctx, "X", MessageText.decodeIfEncoded(m.text))) "Copied — it has a key in it, so the clipboard clears in 45 s" else "Copied" },
                                    onSpeak = { vm.speak(m) },
                                    onRegenerate = { vm.regenerate() },
                                    onShare = { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, MessageText.decodeIfEncoded(m.text)), "Share")) },
                                    onRate = { vm.rate(m.id, it) },
                                    onOpenFile = { a -> if (a.mime == "application/pdf") vm.viewingPdf = a else if (a.mime == "text/html" || a.name.endsWith(".html", true)) vm.viewingHtml = a else openFile(ctx, a) },
                                    onDownload = { a -> vm.toast = FileActions.download(ctx, a) },
                                    onOpenWorker = { vm.openWorkerChat(it, vm.conversationId) },
                                )
                            }
                            // Thinking row: the run has started but its assistant message is not on screen yet.
                            if (vm.running && messages.lastOrNull()?.role == Role.USER) {
                                item(key = "thinking") { ThinkingRow() }
                            }
                        }
                    }
                    UpdateBanner(vm, navigate)
                    // ── Scroll-to-bottom: a bare chevron floating alone at the
                    // list's bottom-right — NO tile, NO background, NO border,
                    // NO layer behind or above it, just the arrow (IconTap
                    // draws no background at all; the 48dp box is only the
                    // touch target). It used to be an in-flow CircleButton
                    // (p.surface tile + border) inside the bottom stack:
                    // every time it appeared the stack grew 58dp, the list
                    // viewport shrank by the same 58dp, and the last visible
                    // message line was shoved half under the list's bottom
                    // edge — the "black layer" climbing over the reply in
                    // the 2026-11-20 video. As an overlay on the list it
                    // never moves the list or the chips row, and with no
                    // background the text behind it stays fully visible.
                    androidx.compose.animation.AnimatedVisibility(messages.isNotEmpty() && list.canScrollForward && !vm.running, enter = fadeIn() + scaleIn(initialScale = 0.8f), exit = fadeOut() + scaleOut(targetScale = 0.8f),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 12.dp)) {
                        // Crash hunt (2026-11-20): capture the target and guard it —
                        // the tap's coroutine runs later, and if the chat was cleared
                        // meanwhile (New chat / delete) lastIndex is -1, which
                        // animateScrollToItem rejects with IllegalArgumentException.
                        IconTap(Gk.chevronDown, "Scroll to bottom", 22.dp, p.text, 48.dp) { scope.launch { val target = messages.lastIndex; if (target >= 0) list.animateScrollToItem(target, 1_000_000) } }
                    }
                }
                // ── Bottom stack: read-aloud pill, banners, tools, composer ──
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 6.dp)) {
                    AnimatedVisibility(pillVisible, modifier = Modifier.align(Alignment.CenterHorizontally),
                        enter = fadeIn(tween(180)) + slideInVertically(tween(240, easing = FastOutSlowInEasing)) { it / 2 } + expandVertically(tween(240), expandFrom = Alignment.Bottom),
                        exit = fadeOut(tween(150)) + slideOutVertically(tween(200)) { it / 2 } + shrinkVertically(tween(220), shrinkTowards = Alignment.Bottom)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ra.provenance?.let { Text("Voice: $it", style = Type.tiny, color = p.text2, modifier = Modifier.padding(bottom = 6.dp)) }
                            ReadAloudPill(
                                paused = ra.paused, elapsedMs = ra.elapsedMs, progress = ra.progress, speed = ra.speed, speedOpen = speedOpen,
                                onPlayPause = { ra.togglePause() }, onBack = { ra.seekBy(-15_000) }, onForward = { ra.seekBy(15_000) },
                                onSpeed = { speedOpen = !speedOpen }, onClose = { speedOpen = false; g.speaker.stop() },
                                onSpeedAnchor = { speedAnchor = it },
                                modifier = Modifier.padding(bottom = 16.dp),
                            )
                        }
                    }
                    vm.interruptedTask?.let { rec ->
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.card).border(1.dp, p.border, RoundedCornerShape(10.dp)).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            GkIcon(Gk.history, null, 18.dp, p.text2); Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(when (rec.kind) { "build" -> "A build was interrupted"; "long" -> "A long task was interrupted"; else -> "A chat was interrupted" }, style = Type.small.copy(fontWeight = FontWeight.SemiBold), color = p.text)
                                Text(rec.goal.take(90), style = Type.tiny, color = p.text2, maxLines = 1)
                            }
                            TextButton(onClick = { vm.resumeInterruptedTask() }) { Text("Open chat", color = p.accent) }
                            TextButton(onClick = { vm.dismissInterruptedTask() }) { Text("Dismiss", color = p.text2) }
                        }
                    }
                    if (vm.viewingWorkerTranscript) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.card).border(1.dp, p.border, RoundedCornerShape(10.dp)).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            GkIcon(Gk.agent, null, 18.dp, p.text2); Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Worker transcript — read-only", style = Type.small.copy(fontWeight = FontWeight.SemiBold), color = p.text)
                                Text("This is what the worker was asked and did.", style = Type.tiny, color = p.text2, maxLines = 1)
                            }
                            TextButton(onClick = { vm.closeWorkerChat() }) { Text("Back to parent chat", color = p.accent) }
                        }
                    } else {
                        vm.pendingReview?.let { review ->
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.surface).border(1.dp, p.border, RoundedCornerShape(10.dp)).padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                GkIcon(Gk.codeBlock, null, 20.dp, p.accent); Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("${review.set.changes.size} file change${if (review.set.changes.size == 1) "" else "s"} waiting for review", style = Type.small.copy(fontWeight = FontWeight.SemiBold), color = p.text)
                                    Text("+${review.set.totalAdded} −${review.set.totalRemoved} · Nothing is applied until you approve.", style = Type.tiny, color = p.text2)
                                }
                                TextButton(onClick = { navigate(Route.CodeDiff(review.projectId)) }) { Text("Review", color = p.accent) }
                                TextButton(onClick = { vm.approveReview(review.set.changes.map { it.path }.toSet()) }) { Text("Apply", color = p.text) }
                            }
                        }
                        if (vm.apkBuildPid == vm.projectId) vm.apkBuild?.let { st ->
                            ApkBuildCard(st, onShare = { a -> shareFile(ctx, a) }, onRetry = { vm.buildApkInChat() })
                        }
                        if (messages.isNotEmpty()) ToolsRow(
                            researchOn = settings.deepResearch, onResearch = onResearch,
                            longTaskOn = settings.longTask, onLongTask = onLongTask,
                            turboOn = settings.turbo, onTurbo = onTurbo,
                            modelLabel = pickedLabel, onModel = { plus = false; model = true },
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Composer(
                            modifier = Modifier.onGloballyPositioned { composerTopPx = it.boundsInRoot().top },
                            text = vm.draft, onText = { vm.draft = it }, placeholder = "Ask anything",
                            modeLabel = pickedLabel, running = vm.running,
                            attachments = vm.pending, onRemoveAttachment = { vm.pending.remove(it) },
                            listening = listening, level = { g.speech.level() }, partial = partial,
                            onPlus = { model = false; plus = !plus }, onMode = { plus = false; model = !model },
                            onMic = {
                                if (finishing) {
                                    // The final words are still coming back — ignore the tap.
                                } else if (listening) g.speech.finish { t -> if (t.isNotBlank()) vm.draft = (vm.draft + " " + t).trim() }
                                else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) micPerm.launch(Manifest.permission.RECORD_AUDIO)
                                else if (!g.speech.available()) vm.toast = "No speech recogniser on this phone"
                                else g.speech.start()
                            },
                            onMicCancel = { g.speech.cancel() },
                            finishing = finishing,
                            onMicSend = {
                                if (listening && !finishing) {
                                    g.speech.finish { t ->
                                        if (t.isNotBlank()) {
                                            vm.draft = (vm.draft + " " + t).trim()
                                            vm.send()
                                        }
                                    }
                                }
                            },
                            softMessage = dictation.reconnectMessage ?: dictation.softMessage ?: "",
                            onSend = { vm.send() }, onStop = { vm.stop() }, onVoiceCall = { navigate(Route.VoiceCall) },
                            plusOpen = plus, modeOpen = model, onFocus = { vm.prewarm() },
                        )
                    }
                }
            }
            // ── Overlays ──
            vm.viewingHtml?.let { a -> HtmlPreview(a, onClose = { vm.viewingHtml = null }) }
            vm.viewingPdf?.let { a ->
                PdfViewer(a, onClose = { vm.viewingPdf = null; vm.debugViewer = null }, onShare = { shareFile(ctx, a) }, onDownload = { vm.toast = FileActions.download(ctx, a) })
            }
            vm.pendingApproval?.let { pa ->
                AlertDialog(
                    onDismissRequest = { vm.answerApproval(com.mazharansari.x.agent.ApprovalDecision.DENY) },
                    containerColor = p.card, shape = RoundedCornerShape(10.dp),
                    title = { Text("Allow this command?", style = Type.title, color = p.text) },
                    text = {
                        Column {
                            Text(pa.req.summary.take(600), style = Type.small.copy(fontSize = 14.sp, lineHeight = 20.sp), color = p.text)
                            if (pa.req.hard) {
                                Spacer(Modifier.height(10.dp))
                                Text(pa.req.hardReason + " This kind can never be remembered — X will ask every time.", style = Type.small, color = p.danger)
                            } else if (pa.req.pattern.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text("\"Always allow\" remembers this kind of command (${pa.req.pattern}) — revoke it any time in Settings → Approvals. \"This chat\" allows it only until you leave this chat, and is never stored.", style = Type.small, color = p.text2)
                            }
                        }
                    },
                    confirmButton = {
                        Column {
                            Row {
                                TextButton(onClick = { vm.answerApproval(com.mazharansari.x.agent.ApprovalDecision.ALLOW_ONCE) }) { Text("Allow once", color = p.accent) }
                                if (!pa.req.hard && pa.req.pattern.isNotBlank())
                                    TextButton(onClick = { vm.answerApproval(com.mazharansari.x.agent.ApprovalDecision.ALLOW_CHAT) }) { Text("This chat", color = p.text) }
                            }
                            Row {
                                if (!pa.req.hard && pa.req.pattern.isNotBlank())
                                    TextButton(onClick = { vm.answerApproval(com.mazharansari.x.agent.ApprovalDecision.ALLOW_REMEMBER) }) { Text("Always allow", color = p.text) }
                                TextButton(onClick = { vm.answerApproval(com.mazharansari.x.agent.ApprovalDecision.DENY) }) { Text("Decline", color = p.danger) }
                            }
                        }
                    },
                    dismissButton = {},
                )
            }
            vm.pendingPaste?.let { pasted ->
                AlertDialog(
                    onDismissRequest = { vm.dismissPaste() },
                    containerColor = p.card, shape = RoundedCornerShape(10.dp),
                    title = { Text("Send as a file?", style = Type.title, color = p.text) },
                    text = { Text("This message is long (${pasted.length} characters). As a file it stays fast and the model reads it in full; as text it may fail at the provider.", style = Type.small, color = p.text2) },
                    confirmButton = { TextButton(onClick = { vm.resolvePaste(true) }) { Text("Send as file", color = p.accent) } },
                    dismissButton = { TextButton(onClick = { vm.resolvePaste(false) }) { Text("Send as text", color = p.text2) } },
                )
            }
            if (pillVisible && speedAnchor != androidx.compose.ui.geometry.Rect.Zero) {
                val pillTopPx = speedAnchor.top - with(density) { 9.dp.toPx() }
                if (speedOpen) Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { speedOpen = false })
                Box(Modifier.align(Alignment.BottomStart)
                    .padding(bottom = with(density) { (boxBottomPx - pillTopPx).coerceAtLeast(0f).toDp() })
                    .offset(x = with(density) { speedAnchor.left.toDp() } - 2.dp)) {
                    SpeedMenu(speedOpen, ra.speed, com.mazharansari.x.voice.VoicePrefs.SPEEDS, onPick = { v -> ra.setSpeed(ctx, v); speedOpen = false })
                }
            }
            val popBottom = with(density) { (boxBottomPx - composerTopPx).coerceAtLeast(0f).toDp() } + 8.dp
            val statusTopPx = WindowInsets.statusBars.getTop(density).toFloat()
            val topLimitPx = maxOf(boxTopPx, statusTopPx + with(density) { 64.dp.toPx() })
            val popMax = (with(density) { (composerTopPx - topLimitPx).coerceAtLeast(0f).toDp() } - 16.dp).coerceAtLeast(120.dp)
            PlusPopover(plus, { plus = false }, bottomGap = popBottom, maxHeight = popMax,
                onCamera = {
                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) camPerm.launch(Manifest.permission.CAMERA)
                    else {
                        val f = File(g.workspace.uploads, "photo_${System.currentTimeMillis()}.jpg"); cameraFile = f
                        takePhoto.launch(FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f))
                    }
                },
                onGallery = { pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onFiles = { pickFiles.launch(arrayOf("*/*")) },
                onSkills = { navigate(Route.Skills) },
                onConnectors = { navigate(Route.Connectors) },
                onUploadSkill = { pickSkill.launch(arrayOf("*/*")) })
            ModelPopover(model, { model = false }, bottomGap = popBottom, maxHeight = popMax, entries = chooserEntries, pickedId = settings.pickedModel,
                onPick = { id -> vm.pickModel(id) }, onConnectors = { navigate(Route.Connectors) })
            if (overflowOpen) {
                AxionSheet({ overflowOpen = false }) {
                    SheetRow(Gk.pencil, "Rename", onClick = { overflowOpen = false; renamingChat = true })
                    SheetRow(Gk.rocket, "Build APK", onClick = { overflowOpen = false; vm.buildApkInChat() })
                    SheetRow(Gk.share, "Share", onClick = { overflowOpen = false; vm.conversationId?.let { vm.shareConversation(it) } ?: run { vm.toast = "Nothing to share yet" } })
                    SheetRow(Gk.trash, "Delete", color = p.danger, onClick = { overflowOpen = false; vm.conversationId?.let { vm.g.chats.delete(it) }; if (bot != null) onBack() else vm.newChat() })
                }
            }
            if (renamingChat) {
                var t by remember { mutableStateOf(vm.title) }
                AlertDialog(onDismissRequest = { renamingChat = false }, containerColor = p.card, shape = RoundedCornerShape(10.dp),
                    title = { Text("Rename", style = Type.title, color = p.text) },
                    text = {
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.page2).padding(14.dp)) {
                            BasicTextField(t, { t = it }, singleLine = true, textStyle = Type.body.copy(color = p.text), cursorBrush = SolidColor(p.text), modifier = Modifier.fillMaxWidth())
                        }
                    },
                    confirmButton = { TextButton({ vm.conversationId?.let { vm.g.chats.rename(it, t.trim().ifBlank { vm.title }); vm.title = t }; renamingChat = false }) { Text("Save", color = p.text, style = Type.label) } },
                    dismissButton = { TextButton({ renamingChat = false }) { Text("Cancel", color = p.text2, style = Type.label) } })
            }


        }
    }
    thoughts?.let { id -> messages.firstOrNull { it.id == id }?.let { ThoughtsSheet(it) { thoughts = null } } }
}

/**
 * In-chat APK build card (Addendum B): the live Gradle build for this chat's
 * project, rendered inline above the composer. Running shows the title + the
 * last 3 log lines; Done shows the APK as a file card with Share; Failed
 * shows the error with a Retry button. Rendered only while
 * vm.apkBuildPid == vm.projectId (see the call site in ChatScreen).
 */
@Composable
private fun ApkBuildCard(state: com.mazharansari.x.build.JobState, onShare: (com.mazharansari.x.data.Artifact) -> Unit, onRetry: () -> Unit) {
    val p = P
    val ctx = LocalContext.current
    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.surface).border(1.dp, p.border, RoundedCornerShape(10.dp)).padding(12.dp)) {
        when (state) {
            is com.mazharansari.x.build.JobState.Running -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DotGrid()
                    Spacer(Modifier.width(10.dp))
                    Text(state.title, style = Type.row, color = p.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Building", style = Type.mono, color = p.accent)
                }
                state.lines.takeLast(3).forEach { line ->
                    Text(line, style = Type.mono, color = p.text2, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                }
            }
            is com.mazharansari.x.build.JobState.Done -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GkIcon(Gk.rocket, null, 20.dp, p.accent); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(state.title, style = Type.row, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(state.result, style = Type.mono, color = p.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                state.file?.let { path ->
                    val f = File(path)
                    if (f.exists()) {
                        val apk = com.mazharansari.x.data.Artifact(name = f.name, mime = "application/vnd.android.package-archive", path = f.absolutePath, size = f.length())
                        Spacer(Modifier.height(10.dp))
                        ArtifactCard(apk, onOpen = { openFile(ctx, apk) }, onDownload = { FileActions.download(ctx, apk) })
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { onShare(apk) }) { Text("Share APK", color = p.accent, style = Type.label) }
                        }
                    }
                }
            }
            is com.mazharansari.x.build.JobState.Failed -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GkIcon(Gk.close, null, 20.dp, p.danger); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(state.title, style = Type.row, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(state.message, style = Type.small, color = p.danger, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = onRetry) { Text("Retry", color = p.accent, style = Type.label) }
                }
                state.lines.takeLast(3).forEach { line ->
                    Text(line, style = Type.mono, color = p.text2, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                }
            }
            com.mazharansari.x.build.JobState.Idle -> {}
        }
    }
}

/** X thinking row (spec §7.4): plain, no bubble — animated dots only, no text. */
@Composable
private fun ThinkingRow() {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        DotGrid()
    }
}

/** Empty chat (round 2): wordmark + one hint line + the suggestion row. No cartoon logo, no seed lines. */
@Composable
private fun EmptyChat(noKeys: Boolean, researchOn: Boolean, onResearch: () -> Unit, longTaskOn: Boolean, onLongTask: () -> Unit, turboOn: Boolean, onTurbo: () -> Unit, onConnect: () -> Unit, onSkills: () -> Unit, onVoice: () -> Unit) {
    val p = P
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Wordmark(fontSize = 22.sp)
        Text("Start typing below.", style = Type.body.copy(fontSize = 15.sp), color = p.text2, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 8.dp, end = 8.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (noKeys) SuggestionChip(Gk.key, "Connect a free AI key", blue = true, onClick = onConnect)
            ResearchChip(researchOn, onResearch, height = 44.dp, horizontalPadding = 16.dp)
            LongTaskChip(longTaskOn, onLongTask, height = 44.dp, horizontalPadding = 16.dp)
            TurboChip(turboOn, onTurbo, height = 44.dp, horizontalPadding = 16.dp)
            SuggestionChip(Gk.connectors, "Try Connectors", onClick = onConnect)
            SuggestionChip(Gk.skills, "Skills", onClick = onSkills)
            SuggestionChip(Gk.voice, "Voice", onClick = onVoice)
        }
    }
}

@Composable
private fun SuggestionChip(icon: Int, label: String, blue: Boolean = false, onClick: () -> Unit) {
    val p = P; val tap = rememberTap(onClick)
    val bg = if (blue) p.accentSoft else p.card
    val fg = if (blue) p.accent else p.text
    Row(Modifier.height(44.dp).clip(RoundedCornerShape(6.dp)).background(bg).border(1.dp, if (blue) p.accent.copy(alpha = 0.35f) else p.border, RoundedCornerShape(6.dp)).clickable(onClick = tap).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        GkIcon(icon, null, 18.dp, fg); Spacer(Modifier.width(8.dp))
        Text(label, style = Type.small.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium), color = fg, maxLines = 1)
    }
}

/** A small "Update available" pill under the header after the daily check finds a newer build. */
@Composable
private fun BoxScope.UpdateBanner(vm: ChatViewModel, navigate: (Route) -> Unit) {
    val p = P
    val st by vm.g.updater.state.collectAsState()
    val info = (st as? com.mazharansari.x.update.UpdateState.Available)?.info ?: return
    if (vm.g.updater.dismissedCode == info.versionCode) return
    var hidden by remember(info.versionCode) { mutableStateOf(false) }
    if (hidden) return
    Row(Modifier.align(Alignment.TopCenter).padding(top = 8.dp).then(Modifier.zIndex(3f)).clip(RoundedCornerShape(6.dp)).background(p.accentSoft)
        .clickable { navigate(Route.About) }.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        GkIcon(Gk.download, null, 15.dp, p.accent); Spacer(Modifier.width(8.dp))
        Text("Update available · ${info.versionName}", style = Type.small.copy(fontWeight = FontWeight.SemiBold), color = p.accent)
        Spacer(Modifier.width(4.dp))
        IconTap(Gk.close, "Dismiss", 15.dp, p.accent) { hidden = true; vm.g.updater.dismissedCode = info.versionCode }
    }
}





/**
 * Splash (round 2): centred wordmark 28sp on bg, ~700ms, tap to skip,
 * once per app start. No logo image, no cartoon character, no tagline.
 * Shown by AppRoot after the onboarding stages.
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val p = P
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(700)
        onDone()
    }
    Box(Modifier.fillMaxSize().background(p.bg).clickable(remember { MutableInteractionSource() }, null) { onDone() },
        contentAlignment = Alignment.Center) {
        Wordmark(fontSize = 28.sp)
    }
}
