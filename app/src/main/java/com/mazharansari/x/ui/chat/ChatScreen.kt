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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.mazharansari.x.data.*
import com.mazharansari.x.ui.ChatViewModel
import com.mazharansari.x.ui.Route
import com.mazharansari.x.ui.components.*
import com.mazharansari.x.ui.home.FileActions
import com.mazharansari.x.ui.home.openFile
import com.mazharansari.x.ui.home.shareFile
import com.mazharansari.x.ui.mascot.MascotLogo
import com.mazharansari.x.ui.theme.P
import com.mazharansari.x.ui.theme.Type
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.launch
import java.io.File

/**
 * THE one chat screen (2026-11-17 rebuild, Grok mock ChatFrame + ChatScreen):
 * mascot header, message list, composer, menu drawer. No tabs, no dock, no
 * Build mode. The real ChatViewModel drives everything (vm.messages,
 * vm.send(), vm.running, vm.draft); the chat starts empty — no seed lines.
 *
 * Sand + Fraunces/Outfit: colours come from SandTheme (P.*), the display
 * face is Type.display (Fraunces, wired by the fonts work).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(vm: ChatViewModel, navigate: (Route) -> Unit) {
    val p = P
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val g = vm.g
    val settings by g.settings.state.collectAsState()
    val connectors by g.connectors.list.collectAsState()
    val listening by g.speech.listening.collectAsState()
    val finishing by g.speech.finishing.collectAsState()
    val partial by g.speech.partial.collectAsState()
    val dictation by g.speech.state.collectAsState()
    LaunchedEffect(dictation.error) { dictation.error?.let { vm.toast = it } }
    val speakingId by g.speaker.speakingId.collectAsState()
    var drawerOpen by remember { mutableStateOf(false) }
    var inviteOpen by remember { mutableStateOf(false) }
    var plus by remember { mutableStateOf(vm.debugPopover == 1) }
    var model by remember { mutableStateOf(vm.debugPopover == 2) }
    LaunchedEffect(vm.debugPopover) { if (vm.debugPopover == 1) plus = true; if (vm.debugPopover == 2) model = true }
    var thoughts by remember { mutableStateOf(vm.debugThoughts) }
    val list = rememberLazyListState()
    val messages = vm.messages
    val haze = remember { HazeState() }
    val density = LocalDensity.current
    var bottomH by remember { mutableIntStateOf(0) }
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
    DisposableEffect(vm.conversationId) {
        onDispose { vm.saveScroll(vm.conversationId, list.firstVisibleItemIndex) }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            if (restoredFor != vm.conversationId) {
                restoredFor = vm.conversationId
                val idx = vm.scrollFor(vm.conversationId)
                if (idx > 0 && !vm.running) { list.scrollToItem(idx); follow = false }
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
    val view = LocalView.current
    val thresholdPx = with(density) { 132.dp.toPx() }
    var pullPx by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    val debugPull = vm.debugPull
    val pullFrac = if (debugPull > 0f) debugPull else pullPx / thresholdPx
    val pullArmed = if (debugPull > 0f) debugPull >= 1f else armed
    fun tick() { if (settings.haptics) view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK) }
    val dragging by list.interactionSource.collectIsDraggedAsState()
    val draggingNow by rememberUpdatedState(dragging)
    val pullConnection = remember(messages.isEmpty()) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (pullPx > 0f && available.y > 0f && source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) {
                    val use = minOf(available.y, pullPx * 2f)
                    pullPx = (pullPx - use / 2f).coerceAtLeast(0f)
                    if (armed && pullPx < thresholdPx) { armed = false }
                    return androidx.compose.ui.geometry.Offset(0f, use)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
            override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (available.y < 0f && draggingNow && source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput && !vm.running) {
                    val resist = 0.62f * (1f - (pullPx / (thresholdPx * 2.4f)).coerceIn(0f, 0.8f))
                    pullPx += -available.y * resist
                    if (!armed && pullPx >= thresholdPx) { armed = true; tick() }
                    return available
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (pullPx <= 0f) return androidx.compose.ui.unit.Velocity.Zero
                val go = armed
                armed = false
                if (go && !vm.viewingWorkerTranscript) { vm.newChat() }
                androidx.compose.animation.core.animate(pullPx, 0f, animationSpec = tween(260, easing = FastOutSlowInEasing)) { v, _ -> pullPx = v }
                return available
            }
        }
    }

    BackHandler(drawerOpen) { drawerOpen = false }
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
                // ── Header (mock ChatFrame): menu | mascot + X + status | Invite ──
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(p.card).border(1.dp, p.border, CircleShape)
                            .clickable { drawerOpen = true }, contentAlignment = Alignment.Center) {
                            GkIcon(Gk.menu, "Menu", 20.dp, p.text)
                        }
                        Spacer(Modifier.weight(1f))
                        Column(Modifier.clickable(remember { MutableInteractionSource() }, null) { drawerOpen = true },
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            MascotLogo(size = 64.dp, thinking = vm.running)
                            Text("X", style = Type.display.copy(fontSize = 16.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold), color = p.text,
                                modifier = Modifier.padding(top = 4.dp))
                            Text(if (vm.running) "Thinking" else vm.title.ifBlank { "New chat" },
                                style = Type.small.copy(fontSize = 12.sp, lineHeight = 16.sp), color = p.text2,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp).padding(top = 1.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.height(36.dp).clip(RoundedCornerShape(50)).background(p.card).border(1.dp, p.border, RoundedCornerShape(50))
                            .clickable { inviteOpen = !inviteOpen }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                            Text("Invite", style = Type.small.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium), color = p.text)
                        }
                    }
                    if (inviteOpen) {
                        Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(p.card).padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text("Invite", style = Type.small.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = p.text)
                            Text("Share this chat when the account is connected.", style = Type.small, color = p.text2, modifier = Modifier.padding(top = 2.dp))
                            Text("Close", style = Type.small.copy(fontWeight = FontWeight.Medium), color = p.accent,
                                modifier = Modifier.padding(top = 8.dp).clickable { inviteOpen = false })
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
                        LazyColumn(state = list, modifier = Modifier.fillMaxSize().nestedScroll(pullConnection)
                            .graphicsLayer { translationY = -(if (debugPull > 0f) debugPull * thresholdPx * 0.9f else pullPx) },
                            contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp)) {
                            items(messages, key = { it.id }, contentType = { it.role }) { m ->
                                if (m.role == Role.USER) UserBubble(m)
                                else AssistantMessage(
                                    m, isLast = m.id == messages.last().id, speaking = speakingId == m.id,
                                    onOpenThoughts = { thoughts = m.id },
                                    onCopy = { vm.toast = if (com.mazharansari.x.security.SecureClipboard.copy(ctx, "Axion", MessageText.decodeIfEncoded(m.text))) "Copied — it has a key in it, so the clipboard clears in 45 s" else "Copied" },
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
                }
                // ── Bottom stack: read-aloud pill, banners, tools, composer ──
                Column(Modifier.fillMaxWidth().onSizeChanged { bottomH = it.height }.navigationBarsPadding().padding(bottom = 6.dp)) {
                    AnimatedVisibility(messages.isNotEmpty() && list.canScrollForward && !vm.running && pullPx == 0f, enter = fadeIn() + scaleIn(initialScale = 0.8f), exit = fadeOut() + scaleOut(targetScale = 0.8f),
                        modifier = Modifier.align(Alignment.End).padding(end = 12.dp, bottom = 10.dp)) {
                        CircleButton(Gk.chevronDown, "Scroll to bottom", size = 48.dp, iconSize = 22.dp, bg = p.surface) { scope.launch { list.animateScrollToItem(messages.lastIndex, 1_000_000) } }
                    }
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
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card).border(1.dp, p.border, RoundedCornerShape(16.dp)).padding(12.dp),
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
                    vm.contextUsage?.let { u ->
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
                            Text(u.label(), style = Type.tiny, color = p.text2, maxLines = 1)
                            Spacer(Modifier.height(3.dp))
                            LinearProgressIndicator(progress = { u.percent / 100f }, modifier = Modifier.fillMaxWidth().height(3.dp), color = p.accent, trackColor = p.border)
                        }
                    }
                    vm.localSpeed?.let { sp ->
                        Text(sp.label(), style = Type.tiny, color = p.text2, maxLines = 1, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
                    }
                    if (vm.viewingWorkerTranscript) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card).border(1.dp, p.border, RoundedCornerShape(16.dp)).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            GkIcon(Gk.agent, null, 18.dp, p.text2); Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Worker transcript — read-only", style = Type.small.copy(fontWeight = FontWeight.SemiBold), color = p.text)
                                Text("This is what the worker was asked and did.", style = Type.tiny, color = p.text2, maxLines = 1)
                            }
                            TextButton(onClick = { vm.closeWorkerChat() }) { Text("Back to parent chat", color = p.accent) }
                        }
                    } else {
                        vm.activeBot?.let { bot ->
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 2.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                com.mazharansari.x.ui.bots.BotLogoView(bot.logo, bot.name, 28.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Chatting as ${bot.name}", style = Type.tiny, color = p.text2, modifier = Modifier.weight(1f), maxLines = 1)
                                TextButton(onClick = { vm.exitBot() }) { Text("Exit bot", color = p.text2, style = Type.tiny) }
                            }
                        }
                        if (messages.isNotEmpty()) ToolsRow(
                            researchOn = settings.deepResearch, onResearch = onResearch,
                            longTaskOn = settings.longTask, onLongTask = onLongTask,
                            turboOn = settings.turbo, onTurbo = onTurbo,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Composer(
                            modifier = Modifier.onGloballyPositioned { composerTopPx = it.boundsInRoot().top },
                            text = vm.draft, onText = { vm.draft = it }, placeholder = "Ask anything",
                            modeLabel = settings.mode.lowercase().replaceFirstChar { it.uppercase() }, running = vm.running,
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
                    containerColor = p.card, shape = RoundedCornerShape(24.dp),
                    title = { Text("Allow this command?", style = Type.title, color = p.text) },
                    text = {
                        Column {
                            Text(pa.req.summary.take(600), style = Type.small.copy(fontSize = 14.sp, lineHeight = 20.sp), color = p.text)
                            if (pa.req.hard) {
                                Spacer(Modifier.height(10.dp))
                                Text(pa.req.hardReason + " This kind can never be remembered — Axion will ask every time.", style = Type.small, color = p.danger)
                            } else if (pa.req.pattern.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text("\"Always allow\" remembers this kind of command (${pa.req.pattern}) — revoke it any time in Settings → Approvals.", style = Type.small, color = p.text2)
                            }
                        }
                    },
                    confirmButton = {
                        Row {
                            TextButton(onClick = { vm.answerApproval(com.mazharansari.x.agent.ApprovalDecision.DENY) }) { Text("Deny", color = p.danger) }
                            if (!pa.req.hard && pa.req.pattern.isNotBlank())
                                TextButton(onClick = { vm.answerApproval(com.mazharansari.x.agent.ApprovalDecision.ALLOW_REMEMBER) }) { Text("Always allow", color = p.text) }
                            TextButton(onClick = { vm.answerApproval(com.mazharansari.x.agent.ApprovalDecision.ALLOW_ONCE) }) { Text("Allow once", color = p.accent) }
                        }
                    },
                    dismissButton = {},
                )
            }
            vm.pendingPaste?.let { pasted ->
                AlertDialog(
                    onDismissRequest = { vm.dismissPaste() },
                    containerColor = p.card, shape = RoundedCornerShape(24.dp),
                    title = { Text("Send as a file?", style = Type.title, color = p.text) },
                    text = { Text("This message is very long (${pasted.length / 1000}k characters). As a file it stays fast and the model reads it in full; as text it may fail at the provider.", style = Type.small, color = p.text2) },
                    confirmButton = { TextButton(onClick = { vm.resolvePaste(true) }) { Text("Send as file", color = p.accent) } },
                    dismissButton = { TextButton(onClick = { vm.resolvePaste(false) }) { Text("Send as text", color = p.text2) } },
                )
            }
            NewChatPull(pullFrac, pullArmed, Modifier.align(Alignment.BottomCenter).padding(bottom = with(density) { bottomH.toDp() } + 18.dp)
                .graphicsLayer { translationY = -(pullFrac.coerceIn(0f, 1.4f) * 52.dp.toPx()) })
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
            ModelPopover(model, { model = false }, bottomGap = popBottom, maxHeight = popMax, mode = settings.mode, connectors = connectors,
                onPick = { m -> g.settings.update { it.copy(mode = m) } }, onConnectors = { navigate(Route.Connectors) })
            // ── Menu drawer (mock MenuSheet): slides in from the left, 84% wide ──
            val widthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
            val drawerFrac by animateFloatAsState(if (drawerOpen) 1f else 0f, tween(280, easing = FastOutSlowInEasing), label = "drawer")
            if (drawerOpen || drawerFrac > 0f) {
                DismissKeyboard()
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f * drawerFrac)).clickable(remember { MutableInteractionSource() }, null) { drawerOpen = false })
                Box(Modifier.fillMaxSize().graphicsLayer { translationX = -widthPx * (1f - drawerFrac) }) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(0.84f).background(p.bg)) {
                        MenuDrawer(vm, onClose = { drawerOpen = false },
                            onOpen = { vm.open(it); drawerOpen = false },
                            onNew = { vm.newChat(); drawerOpen = false },
                            navigate = { r -> drawerOpen = false; navigate(r) })
                    }
                }
            }
        }
    }
    thoughts?.let { id -> messages.firstOrNull { it.id == id }?.let { ThoughtsSheet(it) { thoughts = null } } }
}

/** Mock thinking row: bubble bg + 3 animated dots + muted "Reading …". */
@Composable
private fun ThinkingRow() {
    val p = P
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 32.dp, top = 6.dp, bottom = 6.dp)
        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 24.dp))
        .background(p.surface2).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        DotGrid()
        Spacer(Modifier.width(12.dp))
        Text("Reading …", style = Type.body.copy(fontSize = 16.sp), color = p.text2)
    }
}

/** Empty chat: the mascot, a hint, and the one suggestion row (no Build chip, no seed lines). */
@Composable
private fun EmptyChat(noKeys: Boolean, researchOn: Boolean, onResearch: () -> Unit, longTaskOn: Boolean, onLongTask: () -> Unit, turboOn: Boolean, onTurbo: () -> Unit, onConnect: () -> Unit, onSkills: () -> Unit, onVoice: () -> Unit) {
    val p = P
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        MascotLogo(size = 120.dp, thinking = false)
        Text("X", style = Type.display.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold), color = p.text, modifier = Modifier.padding(top = 12.dp))
        Text("Ask anything to start", style = Type.body.copy(fontSize = 16.sp), color = p.text2, modifier = Modifier.padding(top = 6.dp))
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
    val bg = if (blue) Color(0xFF0E2A63) else p.card
    val fg = if (blue) Color(0xFF5B9BFF) else p.text
    Row(Modifier.height(44.dp).clip(RoundedCornerShape(50)).background(bg).clickable(onClick = tap).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        GkIcon(icon, null, 18.dp, fg); Spacer(Modifier.width(8.dp))
        Text(label, style = Type.small.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium), color = fg, maxLines = 1)
    }
}

/** A small "Update available" pill under the header after the daily check finds a newer build. */
@Composable
private fun BoxScope.UpdateBanner(vm: ChatViewModel, navigate: (Route) -> Unit) {
    val st by vm.g.updater.state.collectAsState()
    val info = (st as? com.mazharansari.x.update.UpdateState.Available)?.info ?: return
    if (vm.g.updater.dismissedCode == info.versionCode) return
    var hidden by remember(info.versionCode) { mutableStateOf(false) }
    if (hidden) return
    Row(Modifier.align(Alignment.TopCenter).padding(top = 8.dp).then(Modifier.zIndex(3f)).clip(RoundedCornerShape(50)).background(Color(0xFF1E3A24))
        .clickable { navigate(Route.About) }.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        GkIcon(Gk.download, null, 15.dp, Color(0xFF7CD4A0)); Spacer(Modifier.width(8.dp))
        Text("Update available · ${info.versionName}", style = Type.small.copy(fontWeight = FontWeight.SemiBold), color = Color(0xFFD6F5E0))
        Spacer(Modifier.width(4.dp))
        IconTap(Gk.close, "Dismiss", 15.dp, Color(0xFF7CD4A0)) { hidden = true; vm.g.updater.dismissedCode = info.versionCode }
    }
}

/**
 * Menu drawer (mock MenuSheet): "X" in Fraunces 36sp, real chat history with
 * search + pin/dot markers, config entries below the list (Settings, Bots,
 * Bot Store), bottom row Settings gear / Search pill / New chat pencil.
 */
@Composable
private fun MenuDrawer(vm: ChatViewModel, onClose: () -> Unit, onOpen: (String) -> Unit, onNew: () -> Unit, navigate: (Route) -> Unit) {
    val p = P
    val index by vm.g.chats.index.collectAsState()
    val connectors by vm.g.connectors.list.collectAsState()
    var q by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<ConversationMeta?>(null) }
    var renaming by remember { mutableStateOf<ConversationMeta?>(null) }
    val activeRuns by vm.g.runs.active.collectAsState()
    var collapsed by remember { mutableStateOf(false) }
    val myBots by vm.g.botStore.definitions.collectAsState()
    val logoByBot = remember(myBots) { myBots.associate { it.id to it.logo } }
    val filtered = remember(index, q) { index.filter { it.section != "worker" && (q.isBlank() || it.title.contains(q, true) || it.preview.contains(q, true)) } }
    val pinned = filtered.filter { it.pinned }; val rest = filtered.filterNot { it.pinned }
    Box(Modifier.fillMaxSize().background(p.bg)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(), bottom = 120.dp)) {
            item(key = "head") {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("X", style = Type.display.copy(fontSize = 36.sp, fontWeight = FontWeight.SemiBold), color = p.text, modifier = Modifier.weight(1f))
                    CircleButton(Gk.arrowsRight, "Close", bg = p.card, onClick = onClose)
                }
            }
            if (connectors.isEmpty()) item(key = "banner") {
                Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp).fillMaxWidth().height(60.dp).clip(RoundedCornerShape(50)).background(p.blue).clickable { navigate(Route.Connectors) }.padding(start = 18.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    GkIcon(Gk.key, null, 20.dp, Color.White); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Connect a free AI key", style = Type.small.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = Color.White)
                        Text("Gemini, Groq, OpenRouter and more", style = Type.tiny, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
                    }
                    Box(Modifier.height(38.dp).clip(RoundedCornerShape(50)).border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(50)).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        Text("Connect", style = Type.small.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = Color.White)
                    }
                }
            }
            if (pinned.isNotEmpty()) {
                item(key = "h_pinned") { DrawerHeader("Pinned", null) {} }
                items(pinned, key = { "p" + it.id }) { c -> ConvRow(c, c.id == vm.conversationId, activeRuns.any { it.ownerId == c.id }, logoByBot[c.botId].orEmpty(), { onOpen(c.id) }, { menuFor = c }) }
            }
            item(key = "h_conv") { DrawerHeader("Conversations", collapsed) { collapsed = !collapsed } }
            if (!collapsed) items(rest, key = { it.id }) { c -> ConvRow(c, c.id == vm.conversationId, activeRuns.any { it.ownerId == c.id }, logoByBot[c.botId].orEmpty(), { onOpen(c.id) }, { menuFor = c }) }
            if (index.isEmpty()) item { Text("Your conversations will appear here", style = Type.small.copy(fontSize = 15.sp), color = p.text3, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) }
            item(key = "h_config") { DrawerHeader("Settings", null) {} }
            item(key = "cfg") {
                SettingsGroup {
                    DrawerTile(Gk.settings, "Settings") { navigate(Route.Settings) }
                    DrawerTile(Gk.agent, "Bots") { navigate(Route.Bots) }
                    DrawerTile(Gk.grid, "Bot Store") { navigate(Route.BotStore) }
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(0f to Color.Transparent, 0.3f to p.bg))) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(50)).background(p.card).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    GkIcon(Gk.search, null, 22.dp, p.text2); Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        if (q.isEmpty()) Text("Search", style = Type.body.copy(fontSize = 17.sp), color = p.text2)
                        BasicTextField(q, { q = it }, singleLine = true, textStyle = Type.body.copy(fontSize = 17.sp, color = p.text), cursorBrush = SolidColor(p.text), modifier = Modifier.fillMaxWidth())
                    }
                }
                Spacer(Modifier.width(10.dp))
                CircleButton(Gk.settings, "Settings", bg = p.card) { navigate(Route.Settings) }
                Spacer(Modifier.width(10.dp))
                CircleButton(Gk.newChat, "New chat", bg = p.card, onClick = onNew)
            }
        }
    }
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
        AlertDialog(onDismissRequest = { renaming = null }, containerColor = p.card, shape = RoundedCornerShape(24.dp),
            title = { Text("Rename", style = Type.title, color = p.text) },
            text = {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.page2).padding(14.dp)) {
                    BasicTextField(t, { t = it }, singleLine = true, textStyle = Type.body.copy(color = p.text), cursorBrush = SolidColor(p.text), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton({ vm.g.chats.rename(c.id, t.trim().ifBlank { c.title }); if (vm.conversationId == c.id) vm.title = t; renaming = null }) { Text("Save", color = p.text, style = Type.label) } },
            dismissButton = { TextButton({ renaming = null }) { Text("Cancel", color = p.text2, style = Type.label) } })
    }
}

@Composable
private fun DrawerTile(icon: Int, label: String, onClick: () -> Unit) {
    val p = P; val tap = rememberTap(onClick)
    Row(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(6.dp)).background(p.card).clickable(onClick = tap).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        GkIcon(icon, null, 22.dp, p.text)
        Spacer(Modifier.width(16.dp))
        Text(label, style = Type.row, color = p.text)
    }
}

@Composable
private fun DrawerHeader(label: String, collapsed: Boolean?, onToggle: () -> Unit) {
    val p = P
    Row(Modifier.padding(top = 10.dp).fillMaxWidth().height(48.dp).background(if (p.isDark) Color(0xFF1F1F1F) else Color(0xFFEDEDED)).clickable(enabled = collapsed != null, onClick = onToggle).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Type.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium), color = p.text2, modifier = Modifier.weight(1f))
        if (collapsed != null) GkIcon(Gk.chevronDown, null, 18.dp, p.text2, Modifier.graphicsLayer { rotationZ = if (collapsed) 0f else 180f })
    }
}

/** A conversation row: logo/avatar, name, last message, time, pin marker + sky dot while its run is live. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConvRow(c: ConversationMeta, selected: Boolean, working: Boolean, botLogo: String = "", onOpen: () -> Unit, onMenu: () -> Unit) {
    val p = P
    Row(Modifier.fillMaxWidth().padding(horizontal = 9.dp).clip(RoundedCornerShape(12.dp)).background(if (selected) p.card else Color.Transparent)
        .combinedClickable(onClick = onOpen, onLongClick = onMenu).padding(start = 13.dp, end = 10.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        if (c.botId.isNotBlank()) com.mazharansari.x.ui.bots.BotLogoView(botLogo, c.botName.ifBlank { c.title }, 46.dp)
        else Avatar(c.title.ifBlank { "A" }, 46.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.title, style = Type.body.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold), color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (c.pinned) { GkIcon(Gk.pin, "Pinned", 14.dp, p.text2); Spacer(Modifier.width(6.dp)) }
                Text(chatTimeLabel(c.updatedAt), style = Type.tiny, color = if (working) p.accent else p.text3, modifier = Modifier.padding(start = 2.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                if (working) { Box(Modifier.size(8.dp).clip(CircleShape).background(p.accent)); Spacer(Modifier.width(6.dp)) }
                Text(if (working) "Working…" else c.preview.ifBlank { "No messages yet" }, style = Type.small.copy(fontSize = 14.sp), color = p.text2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Splash (mock): mascot 196dp + "X" Fraunces 48sp + glow, ~700ms, tap to
 * skip, once per app start. Shown by AppRoot after the onboarding stages.
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
        Box(Modifier.size(340.dp).background(Brush.radialGradient(listOf(p.accent.copy(alpha = 0.28f), Color.Transparent)), shape = CircleShape))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MascotLogo(size = 196.dp, thinking = false, onTap = onDone)
            Text("X", style = Type.display.copy(fontSize = 48.sp, lineHeight = 52.sp, letterSpacing = (-1.44).sp, fontWeight = FontWeight.SemiBold),
                color = p.text, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
        }
    }
}
