package com.mazharansari.x.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mazharansari.x.AxionApp
import com.mazharansari.x.agent.*
import com.mazharansari.x.data.*
import com.mazharansari.x.livecall.ApiKeyCredential
import com.mazharansari.x.livecall.LiveCallController
import com.mazharansari.x.livecall.LiveCallUiState
import com.mazharansari.x.livecall.LiveToolBridge
import com.mazharansari.x.llm.*
import com.mazharansari.x.work.BotRunInfo
import com.mazharansari.x.work.BotRunService
import com.mazharansari.x.work.BotRunState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.UUID

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    val g = (app as AxionApp).graph

    var conversationId by mutableStateOf<String?>(null); private set
    /** Swarm (Phase 1): the open conversation is a worker transcript — read-only, with a way back to its parent chat. */
    var viewingWorkerTranscript by mutableStateOf(false); private set
    private var workerReturnToId: String? = null
    /** Bot Store (Phase 1): the parent bot's given name when returning from a worker chat. */
    private var workerReturnToName: String? = null
    /** Bot Store (Phase 1): the bot this chat runs as (installed/created bot); null = plain X. Any bot can spawn workers; the name is what makes it the parent for its run. */
    var activeBot by mutableStateOf<com.mazharansari.x.data.BotDefinition?>(null); private set
    var title by mutableStateOf("")
    var messages by mutableStateOf<List<Message>>(emptyList()); private set
    var running by mutableStateOf(false); private set
    val pending = mutableStateListOf<Attachment>()
    var draft by mutableStateOf("")
    /** Phase 2 (WhatsApp-style chats): one input draft per conversation id — leaving a
     * chat saves what was typed, opening it again restores it (the old single draft
     * leaked into the wrong chat and New chat wiped it). */
    private val drafts = com.mazharansari.x.data.ConversationDrafts()
    /** Phase 2: per-conversation scroll position (first visible item), best-effort restore. */
    private val scrollPositions = HashMap<String, Int>()
    fun saveScroll(id: String?, index: Int) { if (id != null && index > 0) scrollPositions[id] = index }
    fun scrollFor(id: String?): Int = if (id == null) 0 else scrollPositions[id] ?: 0
    /** The bot a saved conversation was running as, if any (restored on open). */
    private fun botFor(botId: String): com.mazharansari.x.data.BotDefinition? =
        botId.ifBlank { null }?.let { id -> g.botStore.definitions.value.firstOrNull { it.id == id } }
    var toast by mutableStateOf<String?>(null)
    /** v0.5.3 tests/screenshots: Storage screen with fixed numbers (items, free bytes). */
    var debugStorage by mutableStateOf<Pair<List<com.mazharansari.x.linux.StorageItem>, Long>?>(null)
    /** Screenshot-test hooks: 1 = + popover open, 2 = model popover open; non-null opens the Thoughts sheet with that text. */
    var debugPopover by mutableStateOf(0)
    var debugThoughts by mutableStateOf<String?>(null)
    /** Screenshot-test hook: non-null shows the call screen in this state without opening the mic. */
    var debugCall by mutableStateOf<com.mazharansari.x.voice.SameVoiceCall.State?>(null)
    // v0.3.1 render hooks (tests only): speed list open, pull-up progress (0..1+), PDF viewer open on an artifact name
    var debugSpeedMenu by mutableStateOf(false)
    var debugPull by mutableStateOf(0f)
    var debugViewer by mutableStateOf<String?>(null)
    // v0.4 render hooks (tests only): open a screen at start, a toolchain sheet, the Antigravity sign-in sheet in a given state
    var debugStart by mutableStateOf<com.mazharansari.x.ui.Route?>(null)
    var debugToolSheet by mutableStateOf<com.mazharansari.x.linux.Toolchain?>(null)
    var debugAgyLogin by mutableStateOf<com.mazharansari.x.antigravity.AgyLoginState?>(null)

    // v0.5: quiet update check on app start (at most once a day; a notification only when something newer is out)
    init { viewModelScope.launch { runCatching { g.updater.quietCheck() } } }

    // ── v0.12 approvals: a gated shell action waits here until the user answers the dialog ─────────────────────────
    data class PendingApproval(val req: GateRequest, val deferred: CompletableDeferred<ApprovalDecision>)
    var pendingApproval by mutableStateOf<PendingApproval?>(null); private set
    fun answerApproval(d: ApprovalDecision) { pendingApproval?.deferred?.complete(d) }
    /** 2026-11-19 (spec §9.1): "This chat" approvals — command kinds allowed for
     * the open chat only. In-memory only, never written to ApprovalsStore, and
     * cleared whenever the user leaves the chat (newChat / open / openProject). */
    private val chatApprovedPatterns = mutableSetOf<String>()
    private val approver = object : Approver {
        override suspend fun decide(req: GateRequest): ApprovalDecision {
            if (!req.hard && req.pattern.isNotBlank() && req.pattern in chatApprovedPatterns) return ApprovalDecision.ALLOW_ONCE
            val deferred = CompletableDeferred<ApprovalDecision>()
            withContext(Dispatchers.Main.immediate) { pendingApproval = PendingApproval(req, deferred) }
            try {
                val d = deferred.await()
                if (d == ApprovalDecision.ALLOW_CHAT && req.pattern.isNotBlank()) chatApprovedPatterns.add(req.pattern)
                return if (d == ApprovalDecision.ALLOW_CHAT) ApprovalDecision.ALLOW_ONCE else d
            }
            finally { withContext(NonCancellable + Dispatchers.Main.immediate) { if (pendingApproval?.deferred === deferred) pendingApproval = null } }
        }
    }

    // ── v0.12 long tasks: a run the app lost mid-way (killed / crashed) is surfaced, not silently gone ─────────────
    var interruptedTask by mutableStateOf<com.mazharansari.x.data.TaskRunRecord?>(null); private set
    // Redesign round 2 (§3.4): the measured context usage / local speed are no
    // longer shown anywhere in chat. The measurement code stays (ContextUsage
    // still drives auto-compaction in the agent; LocalSpeed still exists) —
    // only the chat display and this ViewModel state are gone.
    init {
        viewModelScope.launch {
            // Phase 2 (background): EVERY run still marked running died with the process
            // (chat turns and Build turns too, not just long tasks) — surface the most
            // recent one with a one-tap resume; the rest keep their partial reply in
            // their own chat and resume by opening it.
            val interrupted = g.taskRuns.markAllRunningInterrupted()
            interruptedTask = interrupted.maxByOrNull { it.updatedAt }
        }
    }
    fun dismissInterruptedTask() { interruptedTask?.let { g.taskRuns.remove(it) }; interruptedTask = null }
    /** P2-2: reopen the interrupted chat AND pre-fill the continue prompt — the user taps Send to resume from the TaskRuns record. */
    fun resumeInterruptedTask() {
        // Phase 2: the resume prompt goes into the drafts store BEFORE opening, so the
        // async load's draft restore brings it back instead of overwriting it.
        interruptedTask?.let { rec ->
            if (rec.goal.isNotBlank()) drafts.save(rec.conversationId, com.mazharansari.x.agent.ContextBudget.resumeDraft(rec.goal))
            open(rec.conversationId)
        }
        interruptedTask = null
    }
    // v0.5.2 render hooks (tests only): the "before you save" warning on key export, the Delete-all-data dialog
    var debugExportWarning by mutableStateOf(false)
    var debugDeleteAll by mutableStateOf(false)
    /** Tests: wipe files but don't ask Android to clear the app (that would end the test process). */
    var debugNoClear = false
    var debugNoField = false
    /** Tests: open the install-log dialog for this tool at start. */
    var debugToolLog: String? = null
    var debugTyped = ""
    /** Speed report: true = the pre-0.5.2 way (a client and a key decrypt for every key × model, up front). */
    var debugEagerClients = false
    /** The PDF open in X's full-screen viewer (drawn over the whole Home, top bar included). */
    var viewingPdf by mutableStateOf<com.mazharansari.x.data.Artifact?>(null)
    /** axion-p1.3: an .html the agent wrote, previewed inside X. */
    var viewingHtml by mutableStateOf<com.mazharansari.x.data.Artifact?>(null)

    fun shareConversation(id: String) = viewModelScope.launch {
        val c = g.chats.load(id) ?: return@launch
        val body = buildString {
            append(c.title).append("\n\n")
            c.messages.forEach { m -> append(if (m.role == Role.USER) "Me: " else "X: ").append(m.text.trim()).append("\n\n") }
        }.trim()
        val ctx = getApplication<Application>()
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_SUBJECT, c.title).putExtra(android.content.Intent.EXTRA_TEXT, body)
        runCatching { ctx.startActivity(android.content.Intent.createChooser(send, "Share conversation").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    // Imagine tab
    val images = mutableStateListOf<File>()
    var imagining by mutableStateOf(false); private set
    var imagineNote by mutableStateOf<String?>(null)

    fun loadImages() = viewModelScope.launch(Dispatchers.IO) {
        val l = File(g.workspace.outputs, "images").listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }.orEmpty()
        withContext(Dispatchers.Main) { images.clear(); images.addAll(l) }
    }

    fun imagine(prompt: String, aspect: String) {
        if (prompt.isBlank() || imagining) return
        imagining = true; imagineNote = null
        viewModelScope.launch {
            runCatching { g.imageGen.generate(prompt.trim(), aspect) }
                .onSuccess { r -> images.add(0, r.file); imagineNote = "Made with ${r.provider}" }
                .onFailure { imagineNote = it.message }
            imagining = false
        }
    }

    /**
     * v0.8.0 (Phase C): every run in flight, keyed by its conversation id. A run owns its
     * conversation's messages end to end (see RunSession), so leaving a chat or a project —
     * or the app being pushed into the background — never stops or moves its work.
     */
    private val sessions = LinkedHashMap<String, RunSession>()

    /** [running] always means: the OPEN conversation has a live session. */
    private fun refreshRunning() { running = conversationId?.let { sessions.containsKey(it) } == true }

    /** The session belonging to the open conversation, if one is in flight. */
    private fun openSession(): RunSession? = conversationId?.let { sessions[it] }
    private var createdAt = 0L

    // ── Background bot runs: the running state the bot chat shows ────────────────────────────────────────────────
    /**
     * Every bot run in flight, keyed by run id. This is the state
     * [com.mazharansari.x.ui.chat.BotRunningBanner] reads: it is derived
     * from the app-scoped [com.mazharansari.x.work.RunRegistry] (via
     * [BotRunService]'s bot-run map), so a run keeps showing as running
     * after the user leaves its chat for another chat, backgrounds the
     * app, or the chat screen is recreated — the state is never tied to
     * this ViewModel's [sessions] map or to the open conversation.
     */
    private val _botRuns = MutableStateFlow<List<BotRunState>>(emptyList())
    val botRuns: StateFlow<List<BotRunState>> = _botRuns

    init {
        viewModelScope.launch {
            g.runs.active.collect { handles ->
                _botRuns.value = handles.mapNotNull { h ->
                    val info = BotRunService.info(h.id) ?: return@mapNotNull null
                    BotRunState(info.runId, info.botId, info.botName, info.title, info.conversationId, info.startedAt,
                        workerCount = g.bots.liveWorkerCount(info.conversationId))
                }
            }
        }
    }

    /** The live bot run for one conversation, or null when it has none in flight. */
    fun botRunState(conversationId: String?): BotRunState? =
        if (conversationId == null) null else botRuns.value.firstOrNull { it.conversationId == conversationId }

    /** True when a bot run is in flight for this conversation (any screen, background included). */
    fun isBotRunning(conversationId: String?): Boolean = botRunState(conversationId) != null

    /**
     * Stop ONE bot run by its run id — from its chat's banner, or from the
     * notification. A run in the open chat freezes through [stopSession]
     * (partial reply kept); a run in another chat is cancelled through its
     * registry job, and its own cleanup settles it the same way. Every
     * other run in flight is untouched, and the run's Linux jobs go with it.
     */
    fun stopBotRun(runId: String) {
        val sess = sessions.values.firstOrNull { "run-${it.aid}" == runId }
        if (sess != null) {
            stopSession(sess)
            return
        }
        // The ViewModel was recreated (or the run outlived its session):
        // the registry job keyed by this run id IS the run — cancel it.
        g.runs.active.value.firstOrNull { it.id == runId }?.job?.cancel()
        BotRunService.noteRunFinished(runId)
        g.jobs.killForRun(runId)
        BotRunService.refresh(getApplication())
    }

    /**
     * One chat screen, no modes: a new chat is always a plain Ask chat.
     * There is no Build section to switch into any more — the agent still
     * does build work through chat when asked (see runBuildTurn), but the
     * user never picks a Build mode.
     */
    fun newChat() {
        // v0.8.0: no stop() — a run in the chat being left keeps working in the background.
        // Phase 2: the half-typed draft stays with the chat being left, not the new one.
        // Crash hunt (2026-11-20): invalidate any conversation load still in flight.
        openGeneration++
        drafts.save(conversationId, draft)
        conversationId = null; title = ""; messages = emptyList(); pending.clear(); draft = ""; section = Section.ASK
        viewingWorkerTranscript = false; workerReturnToId = null; workerReturnToName = null; activeBot = null
        projectId = null
        chatApprovedPatterns.clear()   // "This chat" approvals end with the chat
        refreshRunning()
    }

    /**
     * Bot Store (Phase 1): start a chat AS an installed/created bot — its name, persona and
     * tool allow-list drive the run (see runAgent). The user types first; installing a bot
     * never auto-runs it. This is the create-blank primitive only — UI must call
     * [openBotChat] instead, never this directly (redesign round 2 §3.3).
     */
    fun startBotChat(bot: com.mazharansari.x.data.BotDefinition) {
        newChat()
        activeBot = bot
    }

    /**
     * Redesign round 2 (§3.3): the ONLY entry point for opening a bot chat.
     * Reopens the bot's newest thread when it has one, else starts its first
     * blank thread bound to the bot (persisted with botId on first send).
     * Leaving a bot chat is just Back — there is no exitBot any more.
     */
    fun openBotChat(bot: com.mazharansari.x.data.BotDefinition) {
        val existing = g.chats.latestForBot(bot.id)
        if (existing != null) open(existing.id) else startBotChat(bot)
    }

    /** The bot's newest thread (for Home rows: last-message preview + time), or null. */
    fun botThreadMeta(botId: String): ConversationMeta? = g.chats.latestForBot(botId)

    // ── Addendum B (2026-11-19): builds inside chat ──────────────────────────────────────────────────────────────
    /**
     * The APK build for the open project, as the chat screen shows it live
     * (null = no APK build started in this chat yet). The chat screen renders
     * [com.mazharansari.x.build.JobState] Running lines, the Done APK card
     * (install / share) and the Failed error + Retry — all inside the chat.
     */
    var apkBuild by mutableStateOf<com.mazharansari.x.build.JobState?>(null); private set
    /** The project the [apkBuild] card belongs to — the chat only renders it while that project is open. */
    var apkBuildPid by mutableStateOf<String?>(null); private set
    private var apkWatchJob: Job? = null

    /**
     * Attach the open chat to a fresh Android project WITHOUT wiping the
     * conversation (unlike [openProject], which opens a project's own empty
     * chat). From here on, messages in this chat run build turns
     * ([runBuildTurn]) and [buildApkInChat] compiles the project. The project
     * is linked to this conversation so reopening it re-attaches the project.
     */
    fun attachProjectForBuild(name: String): String {
        val clean = name.trim().take(48).ifBlank { "My app" }
        val proj = g.build.create(clean, "android")
        if (conversationId == null) {
            conversationId = java.util.UUID.randomUUID().toString()
            createdAt = System.currentTimeMillis()
            title = clean
        }
        g.build.store.setConversation(proj.id, conversationId!!)
        projectId = proj.id
        return proj.id
    }

    /**
     * Build the APK for the open project, from chat. A plain chat gets a
     * project attached first ([attachProjectForBuild]); a project chat builds
     * as-is. The Gradle output streams into [apkBuild] live, and on success
     * the built APK is attached to the chat as a message artifact (install /
     * share from the chat) — the build engine itself ([BuildHub.buildApk])
     * is reused untouched.
     */
    fun buildApkInChat() {
        val pid = projectId ?: attachProjectForBuild(title.ifBlank { "My app" })
        val proj = g.build.store.get(pid) ?: run { toast = "That project is gone."; return }
        if (!g.build.apkReady()) {
            toast = "Install \"Android APK builder\" in Developer tools first (about 210 MB download)."
            return
        }
        if (apkBuildPid == pid && (apkBuild as? com.mazharansari.x.build.JobState.Running) != null) return   // already building
        apkBuildPid = pid
        apkBuild = com.mazharansari.x.build.JobState.Running("Building APK", listOf("Starting Gradle — the first build downloads libraries and can take 5–15 minutes on a phone."))
        g.build.buildApk(proj)
        apkWatchJob?.cancel()
        apkWatchJob = viewModelScope.launch {
            g.build.jobs.collect { jobs ->
                val st = jobs["$pid:apk"] ?: return@collect
                apkBuild = st
                if (st is com.mazharansari.x.build.JobState.Done && st.file != null) attachApkArtifact(java.io.File(st.file), pid)
            }
        }
    }

    /** Puts the built APK on the chat as a message artifact (install / share from the chat). */
    private fun attachApkArtifact(apk: java.io.File, pid: String) {
        if (projectId != pid) return   // the user opened another chat meanwhile — never write into it
        if (!apk.isFile || apk.length() == 0L) return
        if (messages.any { m -> m.artifacts.any { it.path == apk.absolutePath } }) return
        val art = com.mazharansari.x.data.Artifact(apk.name, "application/vnd.android.package-archive", apk.absolutePath, apk.length())
        val last = messages.lastOrNull()
        messages = if (last != null && last.role == Role.ASSISTANT) {
            messages.dropLast(1) + last.copy(artifacts = last.artifacts + art)
        } else {
            messages + Message(java.util.UUID.randomUUID().toString(), Role.ASSISTANT,
                "APK ready: ${apk.name} (${apk.length() / 1024} KB). Tap it to install, or share it from the chat.",
                artifacts = listOf(art), createdAt = System.currentTimeMillis())
        }
        persist(true)
    }

    // ── v0.5 Build projects ────────────────────────────────────────────────────────────────────────────────────────
    /** The Build project whose chat is open (null = no project). Build turns run inside its folder. */
    var projectId by mutableStateOf<String?>(null); private set
    /** Per project: every file's text before the last Build turn (Undo). */
    private val undoPoints = HashMap<String, Map<String, String>>()
    var undoAvailable by mutableStateOf(false); private set

    // ── axion-p1.2 diff review gate: the AI's changes wait here until the user approves them ─────────────────────────
    data class Review(val projectId: String, val set: com.mazharansari.x.build.ChangeGate.ChangeSet)
    var pendingReview by mutableStateOf<Review?>(null); private set
    /** Bumped when a change is approved so the Preview tab reloads. */
    var previewEpoch by mutableIntStateOf(0); private set
    /** Bumped to ask the Build screen to open the review sheet. */
    var reviewRequested by mutableIntStateOf(0); private set
    private var reviewNote: String? = null
    private fun pendingDir(p: com.mazharansari.x.data.Project) = com.mazharansari.x.build.ChangeGate.pendingDir(g.build.store.dir, p.slug)
    fun reviewTexts(c: com.mazharansari.x.build.ChangeGate.FileChange): Pair<String?, String?> {
        val p = g.build.store.get(pendingReview?.projectId) ?: return null to null
        return com.mazharansari.x.build.ChangeGate.texts(pendingDir(p), c)
    }
    /** Code-diff screen (2026-11-19): the waiting ChangeSet for one project, read from disk. */
    fun changeSetFor(projectId: String): com.mazharansari.x.build.ChangeGate.ChangeSet? {
        val p = g.build.store.get(projectId) ?: return null
        return com.mazharansari.x.build.ChangeGate.load(pendingDir(p))
    }
    fun diffTextsFor(projectId: String, c: com.mazharansari.x.build.ChangeGate.FileChange): Pair<String?, String?> {
        val p = g.build.store.get(projectId) ?: return null to null
        return com.mazharansari.x.build.ChangeGate.texts(pendingDir(p), c)
    }
    /** Code-diff screen: apply one project's waiting changes (all of [selected], or all when null). */
    fun approveReviewFor(projectId: String, selected: Set<String>? = null) {
        val p = g.build.store.get(projectId) ?: return
        val set = pendingReview?.takeIf { it.projectId == projectId }?.set ?: changeSetFor(projectId) ?: return
        val sel = selected ?: set.changes.map { it.path }.toSet()
        val dir = pendingDir(p); val folder = g.build.folder(p)
        viewModelScope.launch(Dispatchers.IO) {
            val before = com.mazharansari.x.build.ProjectFiles.snapshot(folder)
            val rejected = set.changes.map { it.path }.filter { it !in sel }
            val applied = runCatching { com.mazharansari.x.build.ChangeGate.apply(folder, dir, set, sel) }
            if (applied.isFailure) { withContext(Dispatchers.Main) { toast = "Could not apply the changes: ${applied.exceptionOrNull()?.message}" }; return@launch }
            com.mazharansari.x.build.ChangeGate.discard(dir)
            withContext(Dispatchers.Main) {
                if (pendingReview?.projectId == projectId) pendingReview = null
                if (applied.getOrThrow().isNotEmpty()) { undoPoints[p.id] = before; undoAvailable = projectId == p.id; previewEpoch++ }
                reviewNote = if (rejected.isEmpty()) null else "(The user did NOT approve your changes to: ${rejected.take(12).joinToString()}. Those files are exactly as they were before your last turn; re-read them before editing.)"
                toast = "Applied ${applied.getOrThrow().size} file${if (applied.getOrThrow().size == 1) "" else "s"}"
            }
        }
    }
    /** Code-diff screen: throw one project's waiting changes away. */
    fun rejectReviewFor(projectId: String) {
        val p = g.build.store.get(projectId) ?: return
        val dir = pendingDir(p)
        viewModelScope.launch(Dispatchers.IO) {
            com.mazharansari.x.build.ChangeGate.discard(dir)
            withContext(Dispatchers.Main) {
                if (pendingReview?.projectId == projectId) pendingReview = null
                reviewNote = "(The user REJECTED your last changes. The project is exactly as it was before that turn; nothing you wrote was kept. Re-read files before editing.)"
                toast = "Changes rejected — project unchanged"
            }
        }
    }
    /** Opening a project: restore it if a turn was cut off mid-way, and bring back a review that was still waiting. */
    private fun loadPending(p: com.mazharansari.x.data.Project) = viewModelScope.launch(Dispatchers.IO) {
        val dir = pendingDir(p)
        val restored = com.mazharansari.x.build.ChangeGate.recoverIfInterrupted(g.build.folder(p), dir)
        val set = com.mazharansari.x.build.ChangeGate.load(dir)
        withContext(Dispatchers.Main) {
            if (restored) toast = "A Build turn was interrupted — your project is back to how it was."
            if (set != null && !set.isEmpty && projectId == p.id) { pendingReview = Review(p.id, set); reviewRequested++ } else if (projectId == p.id) pendingReview = null
        }
    }
    fun approveReview(selected: Set<String>) {
        val r = pendingReview ?: return; val p = g.build.store.get(r.projectId) ?: return
        val dir = pendingDir(p); val folder = g.build.folder(p)
        viewModelScope.launch(Dispatchers.IO) {
            val before = com.mazharansari.x.build.ProjectFiles.snapshot(folder)
            val rejected = r.set.changes.map { it.path }.filter { it !in selected }
            val applied = runCatching { com.mazharansari.x.build.ChangeGate.apply(folder, dir, r.set, selected) }
            if (applied.isFailure) { withContext(Dispatchers.Main) { toast = "Could not apply the changes: ${applied.exceptionOrNull()?.message}" }; return@launch }
            com.mazharansari.x.build.ChangeGate.discard(dir)
            withContext(Dispatchers.Main) {
                pendingReview = null
                if (applied.getOrThrow().isNotEmpty()) { undoPoints[p.id] = before; undoAvailable = projectId == p.id; previewEpoch++ }
                reviewNote = if (rejected.isEmpty()) null else "(The user did NOT approve your changes to: ${rejected.take(12).joinToString()}. Those files are exactly as they were before your last turn; re-read them before editing.)"
                toast = "Applied ${applied.getOrThrow().size} file${if (applied.getOrThrow().size == 1) "" else "s"}"
            }
        }
    }
    fun rejectReview() {
        val r = pendingReview ?: return; val p = g.build.store.get(r.projectId) ?: return
        val dir = pendingDir(p)
        viewModelScope.launch(Dispatchers.IO) {
            com.mazharansari.x.build.ChangeGate.discard(dir)
            withContext(Dispatchers.Main) {
                pendingReview = null
                reviewNote = "(The user REJECTED your last changes. The project is exactly as it was before that turn; nothing you wrote was kept. Re-read files before editing.)"
                toast = "Changes rejected — project unchanged"
            }
        }
    }

    fun openProject(id: String) {
        val p = g.build.store.get(id) ?: return
        // Crash hunt (2026-11-20): invalidate any conversation load still in flight.
        openGeneration++
        // v0.8.0: no stop() — the project being left keeps working (its row shows "Working").
        // Phase 2: the draft stays with the chat being left; Build chats are never bot chats.
        drafts.save(conversationId, draft)
        activeBot = null
        projectId = id; section = Section.ASK; undoAvailable = undoPoints.containsKey(id); pendingReview = null; chatApprovedPatterns.clear(); loadPending(p)   // 2026-11-19: no Build mode — project chats are normal chats; build turns key off projectId
        conversationId = null; title = p.name; messages = emptyList(); pending.clear(); draft = ""
        refreshRunning()
        // v0.8.0: this project's turn is still running — show its live messages, not a stale saved copy.
        val sess = p.conversationId?.let { sessions[it] }
        if (sess != null) {
            conversationId = sess.convId; title = p.name; messages = sess.msgs; createdAt = sess.createdAt
            draft = drafts.restore(sess.convId)
            refreshRunning()
            return
        }
        p.conversationId?.let { cid ->
            viewModelScope.launch {
                val c = g.chats.load(cid) ?: return@launch
                if (projectId != id) return@launch
                conversationId = c.id; title = p.name; messages = c.messages; createdAt = c.createdAt
                draft = drafts.restore(c.id)
                refreshRunning()
            }
        }
    }

    fun closeProject() { openGeneration++; drafts.save(conversationId, draft); activeBot = null; projectId = null; conversationId = null; messages = emptyList(); title = ""; draft = ""; refreshRunning() }   // v0.8.0: leaving never stops a run

    /** Creates a project and opens its chat. [firstPrompt] (from a template) is sent right away. */
    fun createProject(name: String, kind: String, firstPrompt: String? = null): String {
        val p = g.build.create(name, kind)
        openProject(p.id)
        if (!firstPrompt.isNullOrBlank()) send(firstPrompt)
        return p.id
    }

    /** Puts every file back the way it was before the last Build turn. */
    fun undoLastBuild() {
        val id = projectId ?: return
        val snap = undoPoints.remove(id) ?: return
        val p = g.build.store.get(id) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val n = com.mazharansari.x.build.ProjectFiles.restore(g.build.folder(p), snap)
            withContext(Dispatchers.Main) { undoAvailable = false; toast = "Undid the last turn ($n file${if (n == 1) "" else "s"})" }
        }
    }

    /** Build can run: Linux + the agy CLI installed and at least one signed-in Antigravity account. Kept for the chat-driven build engine (runBuildTurn); there is no Build UI entry point any more. */
    internal fun agyBuildReady(): Boolean = runCatching { g.agyReady() }.getOrDefault(false)

    /**
     * Crash hunt (2026-11-20): generation counter for async conversation loads.
     * Every [open]/[newChat]/[openProject]/[closeProject] bumps it, and a load
     * only applies its result while its generation is still current — so a load
     * that finishes after the user moved on (Back, New chat, another chat) can
     * never overwrite the conversation now on screen, and a message can never
     * be sent into the wrong conversation. The old `opening` id check only
     * guarded against a second open(), never against leaving the chat.
     */
    private var openGeneration = 0

    fun open(id: String) {
        if (id == conversationId) return
        // v0.8.0: no stop() — the chat being left keeps working in the background.
        // Phase 2: its half-typed draft stays with it.
        drafts.save(conversationId, draft)
        chatApprovedPatterns.clear()   // "This chat" approvals end with the chat
        val gen = ++openGeneration
        val sess = sessions[id]
        if (sess != null) {
            // This chat is still working — show its live messages, not the last saved copy.
            conversationId = sess.convId; title = sess.title; messages = sess.msgs; createdAt = sess.createdAt
            section = Section.ASK   // 2026-11-19: no Build mode — a live project run keeps its projectId below, not a section
            viewingWorkerTranscript = false
            // Phase 2: a live run keeps the bot it started as; a saved chat restores its bot below.
            activeBot = sess.bot
            draft = drafts.restore(sess.convId)
            projectId = sess.pid
            undoAvailable = projectId?.let { undoPoints.containsKey(it) } == true
            pendingReview = null; projectId?.let { pid -> g.build.store.get(pid)?.let { loadPending(it) } }   // axion-p1.2
            refreshRunning()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            // The load + state assignment run on IO, NOT on the viewModelScope
            // Main dispatcher (2026-11-20, V031 9/9): under Robolectric with a
            // Compose test rule the paused main looper never pumps a Main
            // continuation posted back from Dispatchers.IO — the store load
            // itself succeeds (runBlocking load returns the conversation) yet
            // conversationId/messages stay empty after 8 s of waitUntil, and
            // per-iteration shadowOf(mainLooper).idle() does not drain it
            // either. On a real phone the Main resume works, so this was
            // invisible there — but the assignment is equally correct here:
            // every write below is snapshot state (safe to write from any
            // thread; the runtime schedules the recomposition) or a StateFlow
            // read, and the load's own withContext(IO) is just a no-op hop.
            // The fast path above (live session) stays synchronous on Main.
            val c = g.chats.load(id)
            // Crash hunt (2026-11-20): a load that finished after the user moved
            // on (Back / New chat / another chat) is dropped silently — the
            // conversation now on screen stays put.
            if (gen != openGeneration) return@launch   // v0.5.2: two quick taps in the drawer — the last one wins
            if (c == null) { toast = "That chat couldn't be opened — it may have been deleted."; return@launch }
            conversationId = c.id; title = c.title; messages = c.messages; createdAt = c.createdAt
            section = Section.ASK   // 2026-11-19: saved "build" conversations open as normal chats; the project (if any) is re-attached below
            // A worker transcript (section "worker") opens read-only; a normal chat clears it.
            viewingWorkerTranscript = c.section == "worker"
            // Phase 2: the bot this chat ran as is restored from the saved conversation,
            // so reopening a bot chat keeps working as that bot (its logo shows in the row).
            activeBot = if (viewingWorkerTranscript) null else botFor(c.botId)
            draft = drafts.restore(c.id)
            projectId = g.build.store.projectFor(c.id)?.id
            undoAvailable = projectId?.let { undoPoints.containsKey(it) } == true
            pendingReview = null; projectId?.let { id -> g.build.store.get(id)?.let { loadPending(it) } }   // axion-p1.2
            refreshRunning()
        }
    }

    /**
     * Swarm (Phase 1): open a worker's own chat — its full transcript (brief, each step,
     * result). The parent chat it came from is remembered, so Back returns to it.
     */
    fun openWorkerChat(workerConversationId: String, parentConversationId: String?, parentName: String? = null) {
        workerReturnToId = parentConversationId; workerReturnToName = parentName
        open(workerConversationId)
    }

    /** Leave a worker transcript: back to its parent chat, or a fresh chat when the parent is gone. */
    fun closeWorkerChat() {
        val back = workerReturnToId
        workerReturnToId = null; workerReturnToName = null; viewingWorkerTranscript = false
        if (back != null) open(back) else newChat()
    }

    /** The parent bot's given name for the read-only banner above a worker transcript ("" = plain X). */
    fun workerParentName(): String = workerReturnToName.orEmpty()

    fun addAttachment(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        val cr = getApplication<Application>().contentResolver
        var name = "file"; var size = 0L
        cr.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
            }
        }
        val mime = cr.getType(uri) ?: Mime.of(name)
        val dest = uniqueFile(g.workspace.uploads, name.replace('/', '_'))
        runCatching { cr.openInputStream(uri)?.use { i -> dest.outputStream().use { o -> i.copyTo(o) } } }
            .onFailure { withContext(Dispatchers.Main) { toast = "Couldn't read that file" }; return@launch }
        val a = Attachment(dest.name, mime, dest.absolutePath, dest.length().takeIf { it > 0 } ?: size)
        withContext(Dispatchers.Main) { pending += a }
    }

    /** + popover → Upload skill: ZIP / SKILL.md / .md / .txt / PDF. */
    fun importSkills(uris: List<Uri>) = viewModelScope.launch {
        val ctx = getApplication<Application>()
        var n = 0; var err: String? = null
        for (uri in uris) runCatching {
            val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null } ?: "skill"
            val bytes = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() } }
            n += g.skills.importFile(name, bytes).size
        }.onFailure { err = it.message }
        toast = (if (n > 0) "Added $n skill${if (n == 1) "" else "s"}" else "") + (err?.let { (if (n > 0) " · " else "") + it } ?: "")
    }

    fun addFile(f: File) { pending += Attachment(f.name, Mime.of(f.name), f.absolutePath, f.length()) }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name); var n = 1
        while (f.exists()) { f = File(dir, name.substringBeforeLast('.') + "_$n" + name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }); n++ }
        return f
    }

    /** Phase 2 (settings audit): a paste waiting on the user's "Paste as File" choice (Advanced → "Always ask"). */
    var pendingPaste by mutableStateOf<String?>(null); private set

    /** Write a long pasted text to uploads/ and return the short "read the attachment" text. */
    private fun pasteToFile(text: String): String {
        val f = uniqueFile(g.workspace.uploads.apply { mkdirs() }, "pasted_${System.currentTimeMillis()}.txt")
        runCatching { f.writeText(text) }.onSuccess {
            pending += Attachment(f.name, "text/plain", f.absolutePath, f.length())
            return "Read the attached text (${text.length} characters)."
        }
        return text
    }

    /** The user answered the "Send as a file?" dialog for [pendingPaste]. */
    fun resolvePaste(asFile: Boolean) {
        val t = pendingPaste ?: return
        pendingPaste = null
        if (asFile) send(pasteToFile(t)) else send(t, skipPasteCheck = true)
    }

    /** Dismiss the "Send as a file?" dialog — the text stays in the draft for editing. */
    fun dismissPaste() { pendingPaste = null }

    fun send(textIn: String = draft, skipPasteCheck: Boolean = false) {
        if (viewingWorkerTranscript) return   // a worker transcript is read-only
        var text = textIn.trim()
        if (text.isEmpty() && pending.isEmpty()) return
        // Crash hunt (2026-11-20): a send while this chat's run is in flight used
        // to vanish silently — the tap did nothing and the text just sat there.
        // The draft is kept (it is only cleared below), and the user is told why.
        if (running) { toast = "Still working on your last message — send again when the reply is done."; return }
        if (projectId != null && pendingReview != null) { toast = "Review the waiting changes first — Approve or Reject."; reviewRequested++; return }   // 2026-11-19: keyed off the open project, not a Build section
        sendTappedAt = System.currentTimeMillis()
        // v0.5.2 security: an API key pasted into the chat is never sent to the AI or saved — it's masked (gsk_••••abcd).
        if (com.mazharansari.x.security.SecretScanner.contains(text)) {
            text = com.mazharansari.x.security.SecretScanner.scrub(text)
            toast = "X hid an API key in your message. Keys are never sent in chat — add them in Settings → Connectors."
        }
        // Phase 2 (settings audit) + fixed 2026-11-20: "Paste as File" (Settings → Advanced)
        // used to be a dead control twice over — first the setting was never read, then the
        // rule that replaced it only fired over 2,000 chars ("Always") / 60,000 chars
        // ("Always ask"), so a normal paste behaved the same under every mode. Now the
        // rule is one threshold (PastePolicy.FILE_THRESHOLD = 500 chars): over it,
        // "Always" converts, "Always ask" asks, "Never" sends inline; at or under it,
        // every mode sends inline, so short chat is untouched.
        if (!skipPasteCheck) {
            val pasteMode = g.settings.state.value.pasteAsFile
            when (com.mazharansari.x.ui.chat.PastePolicy.decide(pasteMode, text.length)) {
                com.mazharansari.x.ui.chat.PasteAction.ASK_USER -> { pendingPaste = text; return }
                com.mazharansari.x.ui.chat.PasteAction.CONVERT_TO_FILE -> text = pasteToFile(text)
                com.mazharansari.x.ui.chat.PasteAction.SEND_INLINE -> { /* as typed */ }
            }
        }
        // v0.5.2 bug hunt: offline → say so at once instead of walking every key (each one would be cooled down for nothing).
        if (section == Section.ASK && !isOnline() && g.settings.state.value.mode != "LOCAL") {
            val u = Message(UUID.randomUUID().toString(), Role.USER, text, attachments = pending.toList(), createdAt = System.currentTimeMillis())
            pending.clear(); draft = ""
            if (conversationId == null) { conversationId = UUID.randomUUID().toString(); createdAt = System.currentTimeMillis(); title = text.ifBlank { "New chat" }.lineSequence().first().take(48) }
            drafts.clear(conversationId)
            messages = messages + u + Message(UUID.randomUUID().toString(), Role.ASSISTANT, status = MsgStatus.ERROR, error = "No internet connection. Check Wi-Fi or mobile data, then tap Regenerate.", createdAt = System.currentTimeMillis())
            persist(true); return
        }
        val user = Message(UUID.randomUUID().toString(), Role.USER, text, attachments = pending.toList(), createdAt = System.currentTimeMillis())
        pending.clear(); draft = ""
        drafts.clear(conversationId)
        if (conversationId == null) {
            conversationId = UUID.randomUUID().toString(); createdAt = System.currentTimeMillis()
            title = text.ifBlank { user.attachments.firstOrNull()?.name ?: "New chat" }.lineSequence().first().take(48)
            projectId?.let { pid -> g.build.store.get(pid)?.let { title = it.name }; g.build.store.setConversation(pid, conversationId!!) }
        }
        messages = messages + user
        runAgent()
    }

    fun regenerate() {
        if (running || viewingWorkerTranscript) return
        val lastUser = messages.indexOfLast { it.role == Role.USER }
        if (lastUser < 0) return
        messages = messages.subList(0, lastUser + 1)
        runAgent()
    }

    fun rate(id: String, r: Int) { if (viewingWorkerTranscript) return; messages = messages.map { if (it.id == id) it.copy(rating = if (it.rating == r) 0 else r) else it }; persist(true) }

    fun editAndResend(id: String) {
        val i = messages.indexOfFirst { it.id == id }; if (i < 0 || running || viewingWorkerTranscript) return
        draft = messages[i].text; messages = messages.subList(0, i); persist(true)
    }

    /**
     * v0.4 Stop (Grok behaviour): the reply freezes where it is the instant ■ is tapped — partial text kept, marked
     * stopped, read-aloud silenced — while the cancelled job unwinds behind it (its HTTP socket is closed at once, see
     * [com.mazharansari.x.llm.cancelHook]). Nothing that arrives after the tap is added.
     */
    fun stop() {
        val sess = openSession() ?: return
        stopSession(sess)
    }

    /**
     * v0.8.0: freeze ONE run where it stands — its own conversation keeps the partial reply,
     * marked stopped; every other run in flight is untouched. The stop button acts on the open
     * conversation's session.
     */
    private fun stopSession(sess: RunSession) {
        sessions.remove(sess.convId)
        val buf = sess.buf
        synchronized(buf) { buf.closed = true; buf.dirty = true }
        flush(sess, force = true)
        sess.msgs = sess.msgs.map { if (it.id == sess.aid) it.copy(status = MsgStatus.INTERRUPTED, notice = "", thinkingMs = System.currentTimeMillis() - it.createdAt,
            steps = it.steps.map { st -> if (st.status == StepStatus.RUNNING) st.copy(status = StepStatus.ERROR, endedAt = System.currentTimeMillis()) else st }) else it }
        if (conversationId == sess.convId) messages = sess.msgs
        refreshRunning()
        sess.job?.cancel()
        BotRunService.noteRunFinished("run-${sess.aid}")
        g.jobs.killForRun("run-${sess.aid}")
        BotRunService.refresh(getApplication())
        g.speaker.stop()
        g.bots.parentFinished("run-${sess.aid}", BotStatus.FAILED, "Stopped")
        persist(sess, immediate = true)
    }

    /**
     * v0.4 (mazhar 2:28 PM): Ask and Build are two separate engines. Ask = API-key providers + Free (slow), never
     * Antigravity. Build = Antigravity only (agy in Linux, multi-account auto-switch), never API keys.
     */
    var section by mutableStateOf(Section.ASK)


    /** Test hook: null = ask Android. */
    var debugOnline: Boolean? = null
    internal fun isOnline(): Boolean = debugOnline ?: if (KeyVault.isTestRuntime) true else runCatching {
        val cm = getApplication<Application>().getSystemService(android.net.ConnectivityManager::class.java)
        val n = cm.activeNetwork ?: return@runCatching false
        cm.getNetworkCapabilities(n)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(true)

    // ── v0.5.2 speed ────────────────────────────────────────────────────────────────────────────────────────────────
    private var sendTappedAt = 0L
    /** Last measured turn (tests + report): Send tap → request leaving the phone, and → first word on screen. */
    var lastTiming: Pair<Long, Long>? = null; private set
    private var lastWarm = 0L

    /** Composer focused / first character typed: open the connection to the model that will answer first. */
    fun prewarm() {
        if (section != Section.ASK) return
        val now = System.currentTimeMillis()
        if (now - lastWarm < 20_000) return
        lastWarm = now
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val s = g.settings.state.value
                val mode = runCatching { Mode.valueOf(s.mode) }.getOrDefault(Mode.AUTO)
                val c = if (s.mode == "SERVER") g.connectors.server() else g.connectors.attempts(if (mode == Mode.AUTO) Mode.FAST else mode).firstOrNull()?.first
                val url = when {
                    c == null -> null
                    c.providerId == "myserver" -> com.mazharansari.x.data.MyServer.normalize(c.baseUrl.orEmpty())
                    c.providerId == "local" -> null
                    else -> c.provider.baseUrl
                }
                url?.let { Http.prewarm(it) }
            }
        }
    }

    private fun persist(immediate: Boolean = false) {
        val id = conversationId ?: return
        // Phase 2: the bot this chat runs as is saved with the conversation, so reopening
        // it restores the bot (and its logo shows in the chat list).
        g.chats.save(Conversation(id, title, messages, createdAt = createdAt, updatedAt = System.currentTimeMillis(), section = section.id,
            botId = activeBot?.id.orEmpty(), botName = activeBot?.name.orEmpty()), immediate)
    }

    /** v0.8.0: save a run's OWN conversation (never the one that happens to be open). */
    private fun persist(sess: RunSession, immediate: Boolean = false) {
        g.chats.save(Conversation(sess.convId, sess.title, sess.msgs, createdAt = sess.createdAt, updatedAt = System.currentTimeMillis(), section = sess.section.id,
            botId = sess.bot?.id.orEmpty(), botName = sess.bot?.name.orEmpty()), immediate)
    }

    private fun runAgent() {
        val s = g.settings.state.value
        val mode = runCatching { Mode.valueOf(s.mode) }.getOrDefault(Mode.AUTO)
        val history = messages.dropLast(1).mapNotNull { m ->
            when (m.role) {
                Role.USER -> UserItem(m.text + attachmentNote(m.attachments))
                Role.ASSISTANT -> m.text.takeIf { it.isNotBlank() }?.let { AssistantItem(it) }
            }
        }
        val u = messages.lastOrNull() ?: return   // crash hunt: an empty list must never throw here (send/regenerate guard it; this is the belt)
        val intent = com.mazharansari.x.agent.IntentGate.classify(u.text, u.attachments.isNotEmpty())
        // Auto: short chat goes to the fast models first (a greeting should never wait on a big reasoning model).
        val runMode = if (mode == Mode.AUTO && intent != com.mazharansari.x.agent.Intent.ACTION) Mode.FAST else mode
        val sec = section
        // v0.11: Research mode (composer toggle) — a research turn is never "quick" small talk.
        val researchOn = sec == Section.ASK && s.deepResearch
        // v0.12: Long task mode (composer toggle) — bigger budgets + a persisted run record.
        val longTaskOn = sec == Section.ASK && s.longTask
        // Phase 2 (background): EVERY run gets a durable record (kind long/ask/build),
        // so a swipe-away or process death leaves a one-tap resume instead of silence.
        val runKind = if (longTaskOn) "long" else if (projectId != null) "build" else "ask"
        // P1-3 Turbo (server): this task's shell work runs on the user's own server when set up.
        val turboOn = sec == Section.ASK && s.turbo
        val quick = sec == Section.ASK && !researchOn && com.mazharansari.x.agent.IntentGate.isSmallTalk(u.text, intent)
        val userItem = UserItem(u.text.ifBlank { "Look at the attached file(s)." } + attachmentNote(u.attachments), media(u.attachments))
        val aid = UUID.randomUUID().toString()
        val started = System.currentTimeMillis()
        // v0.8.0 (Phase C): capture EVERYTHING this run needs right now — its conversation, its
        // messages, its project. From here on the run works only on its own session, so the user
        // can open other chats and projects while it works; nothing is re-read later.
        val convId = conversationId ?: return
        val sess = RunSession(
            aid = aid, convId = convId,
            kind = if (projectId != null) com.mazharansari.x.work.RunRegistry.Kind.BUILD else com.mazharansari.x.work.RunRegistry.Kind.ASK,
            ownerId = if (projectId != null) projectId else convId,
            title = title, section = sec, createdAt = createdAt, pid = projectId,
            bot = activeBot,
            buf = StreamBuffer(),
            msgs = messages + Message(aid, Role.ASSISTANT, status = MsgStatus.STREAMING, thinkingStartedAt = started, createdAt = started),
        )
        sessions[convId] = sess
        messages = sess.msgs
        refreshRunning()
        persist(sess, immediate = true)

        val buf = sess.buf
        val tapped = sendTappedAt.takeIf { it > 0 && started - it < 5_000 } ?: started
        sendTappedAt = 0
        var requestAt = 0L; var firstAt = 0L
        sess.job = viewModelScope.launch {
            // Bot Store (Phase 1): the bot this run answers as, captured at send time — its
            // given name is the parent bot's name for this run ("X" when no bot is active).
            // Phase 2: read from the session, not the live activeBot — the user may have
            // switched chats (and bots) while this run works in the background.
            val bot = sess.bot
            val botName = bot?.name ?: s.assistantName.ifBlank { "X" }
            // v0.8.0 (Phase C): register this run so WorkService keeps the process alive in the
            // background and the projects list / chat drawer can show it as Working.
            // Background bot runs: note the bot run BEFORE registering it, so
            // the botRuns flow (which maps registry handles through the bot-run
            // map) sees the bot identity on the register edge — and so the
            // run survives leaving its chat: it is keyed by run id, never by
            // the open conversation.
            if (bot != null) BotRunService.noteRunStarted(BotRunInfo("run-$aid", bot.id, bot.name, convId, sess.title.ifBlank { bot.name }, started))
            g.runs.register("run-$aid", sess.kind, sess.ownerId, sess.title.ifBlank { "Working" }, coroutineContext[kotlinx.coroutines.Job]!!, started)
            com.mazharansari.x.work.WorkService.refresh(getApplication())   // the notification now lists this run too
            if (bot != null) BotRunService.startForRun(getApplication(), "run-$aid")   // the bot foreground service keeps this run alive in the background
            // Swarm (Phase 1): the parent bot's card appears in the Bots section the moment the run starts.
            g.bots.registerParent("run-$aid", convId, botName, sess.title)
            var lastWorkerCount = -1   // last worker count shown in the run title
            g.taskRuns.start(runKind, u.text, convId)   // Phase 2: survives an app kill; see interruptedTask
            // UI ticker: at most ~16 updates/s however fast tokens arrive, so long answers never jank the list.
            val ticker = launch { while (isActive) { delay(45); flush(sess) } }   // v0.4: ~22 UI updates/s while streaming
            val sink = object : AgentSink {
                override fun reasoning(delta: String) { synchronized(buf) { if (buf.closed) return; buf.reasoning.append(delta); buf.dirty = true } }
                override fun textToThoughts() = synchronized(buf) {
                    val t = buf.text.substring(buf.roundStart); buf.text.setLength(buf.roundStart)
                    if (t.isNotBlank()) buf.reasoning.append(if (buf.reasoning.isEmpty()) "" else "\n\n").append(t.trim())
                    buf.dirty = true
                }
                override fun step(step: Step) {
                    synchronized(buf) {
                        val i = buf.steps.indexOfFirst { it.id == step.id }
                        if (i >= 0) buf.steps[i] = step else buf.steps += step
                        buf.roundStart = buf.text.length; buf.dirty = true
                    }
                    // Swarm (Phase 1): the parent card counts its own steps; a WORKER step re-titles
                    // the run with the live worker count, so the foreground notification shows
                    // the swarm ("… · 3 workers") while it works in the background.
                    if (step.kind == StepKind.WORKER) {
                        val n = g.bots.liveWorkerCount(convId)
                        if (n != lastWorkerCount) {
                            lastWorkerCount = n
                            g.runs.updateTitle("run-$aid", if (n > 0) "${sess.title.ifBlank { "Working" }} · $n worker${if (n == 1) "" else "s"}" else sess.title.ifBlank { "Working" })
                            com.mazharansari.x.work.WorkService.refresh(getApplication())
                        }
                    } else g.bots.parentStep("run-$aid")
                    if (longTaskOn && step.status != StepStatus.RUNNING)
                        g.taskRuns.progress(synchronized(buf) { buf.steps.count { it.status != StepStatus.RUNNING } })   // v0.12
                }
                override fun artifact(a: Artifact) = synchronized(buf) { buf.artifacts += a; buf.dirty = true }
                override fun plan(steps: List<PlanStep>) = synchronized(buf) { buf.plan = steps; buf.dirty = true }   // axion-p2
                override fun usedTool(title: String) = synchronized(buf) { buf.used += title; buf.dirty = true }      // axion-p3
                override fun model(label: String) = synchronized(buf) { buf.model = label; buf.dirty = true }
                override fun switching(label: String) = synchronized(buf) { buf.notice = "Switching to $label\u2026"; buf.dirty = true }
                /**
                 * Auto-shift (2026-11-20): the work moved to another model/account IN THIS TURN because the
                 * previous one hit its limit. The line is kept (unlike [switching], which the first answer
                 * text clears) and the new pick is persisted at once, so the next message keeps using it.
                 */
                override fun shifted(fromLabel: String, toLabel: String) {
                    val line = com.mazharansari.x.llm.ModelChoice.shiftNotice(fromLabel, toLabel)
                    synchronized(buf) {
                        if (line !in buf.shifts) buf.shifts += line
                        buf.notice = line; buf.dirty = true
                    }
                    runCatching {
                        val entries = com.mazharansari.x.llm.ModelChoice.buildChooserEntries(
                            g.connectors.list.value, g.agy.store.value.accounts, g.agy::hasToken)
                        com.mazharansari.x.llm.ModelChoice.findChoiceId(entries, toLabel)?.let { id ->
                            g.settings.update { it.copy(pickedModel = id) }
                        }
                    }
                }
                override fun allExhausted() { synchronized(buf) { buf.allExhausted = true; buf.dirty = true } }
                override fun discardRound() = synchronized(buf) { buf.text.setLength(buf.roundStart); buf.dirty = true }
                override fun text(delta: String) { synchronized(buf) { if (buf.closed) return; buf.text.append(delta); buf.notice = ""; buf.dirty = true } }
                override fun requestStart() { if (requestAt == 0L) requestAt = System.currentTimeMillis() }
                // v0.5.2 speed: the first word goes on screen now, not at the next 45 ms tick.
                override fun firstToken() {
                    if (firstAt != 0L) return
                    firstAt = System.currentTimeMillis()
                    viewModelScope.launch(Dispatchers.Main.immediate) { flush(sess) }
                }
            }
            var status = MsgStatus.DONE; var error = ""
            try {
                val pid = sess.pid
                if (pid != null) withContext(Dispatchers.Default) { runBuildTurn(pid, u, sink) }   // 2026-11-19: a project chat runs build turns; no Build mode
                else withContext(Dispatchers.Default) {
                    // P2-2: the run measures its context usage against the real window (local model ctx
                    // when LOCAL mode is on) and reports it so the chat shows a context indicator.
                    val ctxBudget = if (s.mode == "LOCAL") {
                        val localId = runCatching { g.connectors.local()?.localModel }.getOrNull()
                        com.mazharansari.x.local.LocalModel.byId(localId)?.ctx?.let { com.mazharansari.x.agent.ContextBudget.forLocal(it) }
                            ?: com.mazharansari.x.agent.ContextBudget.DEFAULT_BUDGET
                    } else com.mazharansari.x.agent.ContextBudget.DEFAULT_BUDGET
                    if (s.mode == "LOCAL" && intent == com.mazharansari.x.agent.Intent.ACTION) {
                        // P2-4: the on-device model drives file/shell tools itself (JSON tool calls)
                        // instead of answering chat-only. The final answer streams through the sink.
                        sink.text(runLocalToolTurn(history, userItem, sink))
                    } else {
                        Agent(g.workspace, g.skills, baseTools = Agent.defaultTools() + GenerateImageTool(g.imageGen) + com.mazharansari.x.agent.ideasGoalsTools(g.ideas, g.goals), extraTools = { g.mcp.tools() }, toolSwitches = { bot?.let { com.mazharansari.x.data.switchesForBot(it) } ?: g.settings.state.value.toolSwitches }, memory = g.memory, conversations = g.chats, researchMode = researchOn,
                            approvals = g.approvals, approver = approver, longTask = longTaskOn, turbo = turboOn, schedules = g.schedules, appCtx = getApplication(),
                            contextBudget = ctxBudget).run(
                            history, userItem, attempts = { if (pid != null) buildAttempts() else run {
                                // LFM3 default (2026-11-20): ACTION turns are the one exception — the
                                // small model cannot drive tools, so they stay cloud-first (allam demoted).
                                val list = attempts(runMode, quick, onFreeWait = { secs -> freeWaitNotice(buf, secs) }, forAction = intent == com.mazharansari.x.agent.Intent.ACTION)
                                // Model chooser (2026-11-20): an explicit pick outranks a bot's provider suggestion — the pick sticks.
                                if (g.settings.state.value.pickedModel.isBlank()) com.mazharansari.x.data.preferProvider(list, bot?.suggestedProvider) else list
                            } }, sink = sink,
                            profile = Profile(botName, s.userName, s.replyLanguage, s.instructions),
                            intent = if (pid != null) com.mazharansari.x.agent.Intent.CHAT else intent,   // agy brings its own tools
                            swarmParentId = convId, fastAttempts = { attempts(Mode.FAST, quick) },
                            systemExtra = bot?.promptBlock(),
                        )
                    }
                }
            } catch (e: CancellationException) { status = MsgStatus.INTERRUPTED }
            catch (e: NoModelException) { status = MsgStatus.ERROR; error = e.message.orEmpty() }
            catch (e: LlmException) { status = MsgStatus.ERROR; error = friendly(e) }
            catch (e: Throwable) { status = MsgStatus.ERROR; error = e.message ?: e.toString() }
            ticker.cancel()
            if (buf.closed) { g.bots.parentFinished("run-$aid", BotStatus.FAILED, "Stopped"); g.taskRuns.finish(runKind, convId, "interrupted"); sessions.remove(convId); refreshRunning(); BotRunService.noteRunFinished("run-$aid"); BotRunService.refresh(getApplication()); return@launch }   // Stop already froze this reply into its own conversation
            withContext(NonCancellable) {
                buf.dirty = true; flush(sess)
                // Auto-shift (2026-11-20): the shift lines stay in the chat as the first lines of this answer
                // (or of the error, when the run failed) — a visible notice, not a silent switch. The pick was
                // already persisted when the shift happened, so the next message keeps the new model/account.
                val shiftLines = synchronized(buf) { buf.shifts.toList() }
                val shiftPrefix = shiftLines.takeIf { it.isNotEmpty() }?.joinToString("\n")?.plus("\n\n")
                val finalError = com.mazharansari.x.security.SecretScanner.scrub(error).let { e ->
                    if (buf.allExhausted && e.isNotBlank()) e + "\n\n" + com.mazharansari.x.llm.ModelChoice.ALL_EXHAUSTED_MESSAGE else e
                }
                sess.msgs = sess.msgs.map {
                    if (it.id == aid) it.copy(status = status,
                        text = if (shiftPrefix != null && it.text.isNotBlank()) shiftPrefix + it.text else it.text,
                        error = if (shiftPrefix != null && it.text.isBlank() && finalError.isNotBlank()) shiftPrefix + finalError else finalError,
                        notice = "", thinkingMs = System.currentTimeMillis() - started,
                        firstWordMs = if (firstAt > 0) firstAt - tapped else 0, prepMs = if (requestAt > 0) requestAt - tapped else 0,
                        steps = it.steps.map { st -> if (st.status == StepStatus.RUNNING) st.copy(status = StepStatus.ERROR, endedAt = System.currentTimeMillis()) else st },
                        plan = it.plan.map { ps -> if (ps.status == PlanStatus.RUNNING) ps.copy(status = PlanStatus.FAILED, note = ps.note.ifBlank { "stopped" }) else ps })   // axion-p2
                    else it
                }
                if (conversationId == convId) messages = sess.msgs
                // Memory fix (2026-11-20): deterministic post-chat save pass. An explicit "yaad
                // rakhna / remember this" is saved even when the model answered without calling
                // memory_add — the old code honoured it in words and lost it in fact. Only fires
                // for explicit save instructions (MemorySavePass.extract is narrow by design);
                // skipped when the agent already saved something this turn, and the store's
                // duplicate + budget checks still apply to everything it adds.
                if (status == MsgStatus.DONE) {
                    val agentSaved = sess.msgs.firstOrNull { it.id == aid }?.steps?.any { it.title == "Remembered" || it.title == "Memory updated" } == true
                    if (!agentSaved) runCatching { com.mazharansari.x.agent.MemorySavePass.saveAll(g.memory, u.text) }
                }
                g.bots.parentFinished("run-$aid", if (status == MsgStatus.DONE) BotStatus.DONE else BotStatus.FAILED,
                    sess.msgs.firstOrNull { it.id == aid }?.text.orEmpty().take(120))
                sessions.remove(convId)
                refreshRunning()
                BotRunService.noteRunFinished("run-$aid")
                BotRunService.refresh(getApplication())
                if (requestAt > 0) lastTiming = (requestAt - tapped) to (if (firstAt > 0) firstAt - tapped else -1L)
                persist(sess, immediate = true)
                g.taskRuns.finish(runKind, convId, if (status == MsgStatus.DONE) "done" else "interrupted")   // Phase 2: every run settles its record
                if (status == MsgStatus.DONE && s.autoRead) sess.msgs.firstOrNull { it.id == aid }?.let { speak(it) }
            }
        }
    }

    /**
     * P2-4: LOCAL mode + an action request — the on-device model runs the tool loop itself
     * (LocalAgentRunner: JSON tool calls for file/shell tools) instead of the cloud agent.
     * Returns the final answer text.
     */
    private suspend fun runLocalToolTurn(history: List<ChatItem>, user: UserItem, sink: AgentSink): String {
        val modelId = runCatching { g.connectors.local()?.localModel }.getOrNull()
            ?: throw NoModelException("Local mode is on but no local model is set up. Download one in Connectors → Local model.")
        val model = com.mazharansari.x.local.LocalModel.byId(modelId)
            ?: throw NoModelException("Local model \"$modelId\" is not known. Pick one in Connectors → Local model.")
        val file = g.localModels.file(model)
        if (!file.isFile) throw NoModelException("The local model file is missing. Download it again in Connectors → Local model.")
        val client = com.mazharansari.x.local.LocalLlmClient(file, ctx = model.ctx)
        val tools = Agent.defaultTools().filter { it.spec.name in com.mazharansari.x.local.LocalToolUse.ALLOWED_TOOLS } + com.mazharansari.x.agent.ideasGoalsTools(g.ideas, g.goals)
        val ctx = ToolContext(g.workspace, g.skills, memory = g.memory, chats = g.chats, schedules = g.schedules, appCtx = getApplication())
        val runner = com.mazharansari.x.local.LocalAgentRunner(
            generate = { items ->
                val t0 = System.currentTimeMillis()
                val sb = StringBuilder()
                client.stream(LlmRequest(modelId, items, temperature = 0.2)).collect { ev ->
                    if (ev is LlmEvent.Text) sb.append(ev.delta)
                }
                val ms = System.currentTimeMillis() - t0
                com.mazharansari.x.local.LocalGeneration(sb.toString(), ms, com.mazharansari.x.local.LocalToolUse.approxTokens(sb.length))
            },
            tools = tools, ctx = ctx, approvals = g.approvals, sink = sink,
        )
        val result = runner.run(history, user)
        // Redesign round 2 (§3.4): the measured speed is no longer shown in chat.
        sink.model("${model.title} · ${"%.1f".format(result.speed.tokPerSec)} tok/s")
        return result.answer
    }

    /**
     * v0.5 Build turn: Antigravity works inside the project folder (plan → files → run), X verifies (site check, build,
     * screenshot + console errors) and sends any errors back for a fix, up to 2 rounds. Antigravity only — never API keys.
     */
    private suspend fun runBuildTurn(pid: String, u: Message, sink: AgentSink) {
        val p = g.build.store.get(pid) ?: throw NoModelException("This project was deleted.")
        if (!agyBuildReady()) throw NoModelException("Build runs on Antigravity. Install Developer tools and add an Antigravity account first.")
        val folder = g.build.folder(p)
        // files the user attached go into the project's uploads/ folder
        val note = if (u.attachments.isEmpty()) "" else {
            val up = File(folder, "uploads").apply { mkdirs() }
            u.attachments.forEach { a -> runCatching { File(a.path).copyTo(File(up, a.name), overwrite = true) } }
            "\n\n[Files the user attached, now in the project: " + u.attachments.joinToString { "uploads/${it.name}" } + "]"
        }
        sink.model("Antigravity" + g.agy.store.value.model.takeIf { it.isNotBlank() && it != "auto" }?.let { " · $it" }.orEmpty())
        val loop = com.mazharansari.x.build.BuildLoop(
            com.mazharansari.x.build.AgyBrain(g.linux, g.agy, g.build.store, g.quota), g.build.verifier(), folder, g.build.guest(p), sink,
        )
        // axion-p1.2: with review on, the project folder is backed up first and put back after the turn; the change waits for Approve.
        val dir = pendingDir(p)
        var gated = false
        if (g.settings.state.value.reviewBeforeApply) {
            gated = try { withContext(Dispatchers.IO) { com.mazharansari.x.build.ChangeGate.backup(folder, dir) }; true }
            catch (e: com.mazharansari.x.build.ChangeGate.TooLargeException) {
                // v0.6.1: never slip into un-reviewed changes quietly — the user must have switched this on for large projects.
                if (!g.settings.state.value.allowLargeUngated) throw NoModelException("This project is too large to hold changes for review (${e.message}), and un-reviewed changes are off. Either reduce the project size, or switch on \"Allow un-reviewed changes in very large projects\" in Settings → Advanced — then ask again.")
                sink.reasoning("This project is too big to hold changes for review (${e.message}); you allowed un-reviewed changes for large projects, so changes apply directly.\n"); false
            }
        }
        val rn = reviewNote.also { reviewNote = null }
        val out = try { loop.run(p, (rn?.let { it + "\n\n" } ?: "") + u.text + note, fresh = p.agyConversation == null) }
        finally {
            if (gated) withContext(NonCancellable + Dispatchers.IO) {
                val set = com.mazharansari.x.build.ChangeGate.stage(folder, dir)
                withContext(Dispatchers.Main) {
                    if (set.isEmpty) { com.mazharansari.x.build.ChangeGate.discard(dir) }
                    else { pendingReview = Review(pid, set); reviewRequested++; sink.text("\n\n**${set.changes.size} file change${if (set.changes.size == 1) "" else "s"} waiting for your review** (+${set.totalAdded} −${set.totalRemoved}). Nothing is applied until you tap Approve.") }
                }
            }
        }
        if (!gated && out.changedFiles.isNotEmpty()) { undoPoints[pid] = loop.before; withContext(Dispatchers.Main) { undoAvailable = projectId == pid } }
        if (out.error != null && out.changedFiles.isEmpty()) throw NoModelException(out.error)
        if (out.problems.isNotEmpty()) sink.text("\n\n**Still not fixed after ${out.rounds} rounds:**\n" + out.problems.take(6).joinToString("\n") { "- " + it.lines().first() })
    }

    /**
     * v0.8.0 (Phase C): one run in flight. It owns its conversation's message list end to end:
     * streaming writes [msgs] (through [buf]) and completion saves [msgs] — the screen's
     * [messages] is only a mirror while this conversation is open. That split is what lets a
     * chat turn and a project turn run at the same time without ever writing into each other.
     */
    private class RunSession(
        val aid: String,
        val convId: String,
        val kind: com.mazharansari.x.work.RunRegistry.Kind,
        /** Project id for a BUILD run, the conversation id for an ASK run. */
        val ownerId: String?,
        val title: String,
        val section: Section,
        val createdAt: Long,
        /** The Build project this run works in (null for Ask). Captured at send — never re-read. */
        val pid: String?,
        /** The bot this run answers as (null = plain X). Captured at send — never re-read. */
        val bot: com.mazharansari.x.data.BotDefinition?,
        val buf: StreamBuffer,
        var msgs: List<Message>,
    ) {
        var job: Job? = null
    }

    private class StreamBuffer {
        val text = StringBuilder(); val reasoning = StringBuilder(); val steps = ArrayList<Step>(); val artifacts = ArrayList<Artifact>()
        @Volatile var plan: List<PlanStep> = emptyList()   // axion-p2
        val used = LinkedHashSet<String>()                 // axion-p3: tool groups used (Used tool line)
        var model = ""; var notice = ""; var dirty = false; var roundStart = 0
        @Volatile var closed = false
        /** Auto-shift (2026-11-20): visible "Shifted to …" lines for this turn, kept in the answer text at completion. */
        val shifts = ArrayList<String>()
        /** Auto-shift (2026-11-20): every attempt failed on a limit — the honest all-exhausted state, not a loop. */
        @Volatile var allExhausted = false
    }

    /**
     * v0.8.0: copy a run's buffer into ITS OWN conversation's messages; the screen mirrors it
     * only while that conversation is open. (Before Phase C this wrote into whatever chat was
     * open — the reason a run could not outlive its screen.)
     */
    private fun flush(sess: RunSession, force: Boolean = false) {
        val b = sess.buf
        val snap = synchronized(b) {
            if (!b.dirty || (b.closed && !force)) return
            b.dirty = false
            Triple(b.text.toString(), b.reasoning.toString(), Triple(b.steps.toList(), b.artifacts.toList(), b.model)) to b.notice
        }
        val (s0, notice) = snap
        sess.msgs = sess.msgs.map {
            if (it.id == sess.aid) it.copy(text = s0.first, reasoning = s0.second, steps = s0.third.first, artifacts = s0.third.second, model = s0.third.third, notice = notice, plan = b.plan, usedTools = b.used.toList()) else it
        }
        if (conversationId == sess.convId) messages = sess.msgs
        persist(sess)
    }

    /**
     * Ask (v0.4, mazhar 2:28 PM): API keys only — every model on a key before the next key — then "Free (slow)"
     * (Pollinations, no key) only when nothing else is left. Antigravity joins the chat only when the user
     * picked an Antigravity account in the model chooser, or set Antigravity to "first"/"fallback" for chat
     * (2026-11-20) — never by default, since its store ships "off".
     */
    /** "Free model is busy — retrying in 12 s…" under Thinking while Free (slow) waits out its limit. */
    private fun freeWaitNotice(b: StreamBuffer, secs: Int) {
        synchronized(b) { if (b.closed) return; b.notice = if (secs > 0) "Free model is busy — retrying in $secs s…" else ""; b.dirty = true }
    }

    /** Tests: replaces the model list (a fake model) while everything else in the send path stays real. */
    internal var debugAttempts: ((Mode, Boolean) -> List<Attempt>)? = null

    /**
     * Model chooser (2026-11-20): the user picks a model/account in the chat's
     * model selector and the pick sticks (settings.pickedModel) until changed.
     * "" = Auto: the old behaviour — the user picked nothing. Picking an
     * Antigravity account also turns Antigravity on for chat (it ships "off"
     * by default), so the pick actually routes. The pick never invents a
     * model: an id that matches nothing (key removed since) falls back to Auto.
     */
    fun pickModel(choiceId: String) {
        val pick = com.mazharansari.x.llm.ModelChoice.parsePick(choiceId)
        if (choiceId.isNotBlank() && pick == null) return   // unknown id — never persist a pick that routes nowhere
        g.settings.update { it.copy(pickedModel = choiceId) }
        if (pick is com.mazharansari.x.llm.ModelChoice.ModelPick.Agy) g.agy.setUseInChat("first")
    }

    internal fun attempts(mode: Mode, quick: Boolean = false, onFreeWait: ((Int) -> Unit)? = null, forAction: Boolean = false): List<Attempt> {
        debugAttempts?.let { return it(mode, quick) }
        val s = g.settings.state.value
        val pick = com.mazharansari.x.llm.ModelChoice.parsePick(s.pickedModel)
        // Exclusive picks keep their old guarantees: Free only, or ONLY the picked own model (P0-4: never the API keys, never Free).
        if (pick is com.mazharansari.x.llm.ModelChoice.ModelPick.Free) return freeAttempts(onFreeWait)
        if (pick is com.mazharansari.x.llm.ModelChoice.ModelPick.Local) return ownAttempts("LOCAL", pick.model)
        if (pick is com.mazharansari.x.llm.ModelChoice.ModelPick.Server) return ownAttempts("SERVER", pick.model)
        // The old speed-tier picks still work when nothing is picked in the chooser.
        if (pick == null) {
            if (s.mode == "FREE") return freeAttempts(onFreeWait)
            if (s.mode == "LOCAL" || s.mode == "SERVER") return ownAttempts(s.mode)
        }
        // LFM3 default (2026-11-20, user decision) — the ONE default mechanism:
        // no pick + stored AUTO + the LFM3 file on the phone → the on-device
        // model answers first, before any cloud ranking. ACTION turns are the
        // one exception ([forAction]): the small model cannot drive tools, so
        // an action turn starting on LFM3 would end as plain text with the work
        // never done — actions stay cloud-first (allam demoted, as everywhere).
        val lfm3 = com.mazharansari.x.local.LocalModel.LFM3_1_7B
        val lfm3Downloaded = runCatching { g.localModels.file(lfm3).isFile }.getOrDefault(false)
        // A local connector the user switched OFF is an explicit opt-out of
        // on-device answering: the default does not route around the switch.
        val lfm3Default = !forAction && s.prefersLfm3Default(lfm3Downloaded) && g.connectors.local()?.enabled != false
        if (lfm3Default && g.connectors.local() == null) {
            // One-time migration: LFM3 is downloaded but was never made the
            // phone's local model, so no chooser row or chip exists for it yet.
            // Activating it here — only when NO local model is set, so an
            // explicitly chosen local model is never replaced — makes the
            // default visible on every surface (chooser, chip, Local Models
            // screen). A failure here never breaks the send: the attempt
            // below is still built straight from the downloaded file.
            runCatching { g.connectors.setLocal(lfm3.id, lfm3.title) }
        }
        // Allam demotion, second application point: ConnectorRepo.order already
        // demoted allam, but quickOrder (small talk: non-reasoning first) could
        // lift it back ahead of the reasoning models — so it is demoted again
        // after the quick sort, as the last step on the cloud list.
        val pairs = com.mazharansari.x.llm.ModelChoice.demoteAllam(
            g.connectors.attempts(mode).let { if (quick) quickOrder(it) { p -> p.second } else it }
        ) { p -> p.second }
        // v0.5.2 speed: one lazily-made client per key (was: a key decrypt for every model on every key, before the first request).
        val clients = HashMap<String, LlmClient>()
        val keys = pairs.map { (c, model) ->
            val cl = if (debugEagerClients) g.connectors.clientFor(c) else clients.getOrPut(c.id) { LazyClient { g.connectors.clientFor(c) } }
            Attempt(c.provider.name, model, cl, onFail = { e -> g.connectors.coolDown(c, model, e) }, connectorId = c.id)
        }
        // v0.5.2: labelled "Free (slow)" (was "openai"); its 45 s rate limit is waited out with a visible countdown.
        val free = freeAttempts(onFreeWait)
        // v0.5 (addendum 6): the user's own models. Picked in the model menu → they go first; otherwise they're the backup
        // after the API keys and before Free (slow).
        val own = listOfNotNull(g.connectors.server()?.takeIf { it.enabled }, g.connectors.local()?.takeIf { it.enabled })
            .mapNotNull { c -> runCatching { g.connectors.clientFor(c) }.getOrNull()?.let { cl -> Attempt(c.provider.name, c.pinnedModel ?: c.localModel ?: c.models.firstOrNull().orEmpty(), cl, patientMs = if (c.providerId == "local") 150_000 else 90_000, connectorId = c.id) } }
        // The LFM3 attempt: the active local model when it already is LFM3,
        // else one built straight from the downloaded file (never persisted
        // here — see the one-time activation above), so a downloaded-but-never-
        // activated LFM3 still answers first on this very turn.
        val lfm3Attempt: Attempt? = if (!lfm3Default) null else
            own.firstOrNull { it.provider == "Local model" && it.model == com.mazharansari.x.llm.ModelChoice.DEFAULT_LOCAL_MODEL_ID }
                ?: runCatching { lfm3AttemptFromFile(lfm3) }.getOrNull()
        val ownTail = if (lfm3Attempt == null) own else own.filterNot { it == lfm3Attempt }
        // Antigravity in chat: only when the user picked an Antigravity
        // account in the model chooser (the ModelPick.Agy branch below).
        // The stored useInChat mode ("first"/"fallback"/"off") no longer
        // places it in Ask at all — v0.4: Ask = API keys only.
        val agyAttempt = if (runCatching { g.agyReady() }.getOrDefault(false))
            Attempt("Antigravity", g.agy.store.value.model.ifBlank { "auto" }, g.agyClient, patientMs = 180_000) else null
        g.agyClient.pinnedAccountEmail = (pick as? com.mazharansari.x.llm.ModelChoice.ModelPick.Agy)?.email
        if (pick is com.mazharansari.x.llm.ModelChoice.ModelPick.Agy) {
            // Picked account first, then the keys as the failover tail (account A spent → next account inside
            // AgyClient, then the next provider model here). A stale pick (account gone) falls back to Auto.
            return if (agyAttempt != null) listOf(agyAttempt) + keys + ownTail + free else keys + ownTail + free
        }
        if (pick is com.mazharansari.x.llm.ModelChoice.ModelPick.Api) {
            val reordered = (keys + ownTail + free).let { base ->
                val i = base.indexOfFirst { it.connectorId == pick.connectorId && it.model == pick.model }
                if (i > 0) listOf(base[i]) + base.filterIndexed { j, _ -> j != i } else base
            }
            return reordered
        }
        // v0.4 (mazhar 2:28 PM): Ask = API keys only — Antigravity is the
        // build engine, never an Ask model. A stored useInChat of "first" /
        // "fallback" is a leftover from the v0.4 draft (the setting has no UI
        // any more, and V04Screens notes Ask never uses it), so it must not
        // leak Antigravity into Ask. (An explicitly picked Antigravity
        // account — the ModelPick.Agy branch above — still goes first.)
        return listOfNotNull(lfm3Attempt) + keys + ownTail + free
    }

    /** The LFM3-1.7B attempt, built straight from the downloaded file (no local connector needed). */
    private fun lfm3AttemptFromFile(m: com.mazharansari.x.local.LocalModel): Attempt {
        val c = Connector(id = "local-lfm3-default", providerId = "local", keyBlob = "", keyHint = m.title,
            models = listOf(m.id), fastModels = listOf(m.id), localModel = m.id)
        return Attempt(c.provider.name, m.id, g.connectors.clientFor(c), patientMs = 150_000, connectorId = c.id)
    }

    /** The built-in no-key model, as its own attempt (shared by the FREE mode and a Free pick). */
    private fun freeAttempts(onFreeWait: ((Int) -> Unit)? = null): List<Attempt> =
        listOf(Attempt(FreeFallback.NAME, FreeFallback.NAME, FreeFallback.Client(onWait = { secs -> onFreeWait?.invoke(secs) }), patientMs = FreeFallback.PATIENT_MS))

    /**
     * P0-4 (HI-2): LOCAL / SERVER mode uses ONLY the picked own model — never the API keys, never Free (slow).
     * The old code built the own client inside runCatching{...}.getOrNull() + mapNotNull, so a deleted local model
     * file or a broken server client silently emptied `own` and the turn was still answered by cloud keys. Now a
     * missing, disabled or un-buildable own model throws [NoModelException], which the send path catches and shows
     * as a visible error on the message (ChatViewModel send catch, MsgStatus.ERROR) — and nothing is sent anywhere.
     */
    private fun ownAttempts(pick: String, modelOverride: String? = null): List<Attempt> {
        val c = (if (pick == "LOCAL") g.connectors.local() else g.connectors.server())?.takeIf { it.enabled }
            ?: throw NoModelException(
                if (pick == "LOCAL") "Local mode is on but no local model is set up. Download one in Connectors → Local model."
                else "Server mode is on but no server is set up. Add one in Connectors → My server."
            )
        val cl = runCatching { g.connectors.clientFor(c) }.getOrElse { e ->
            throw NoModelException(
                if (pick == "LOCAL") "Local mode is on but the local model could not start (${e.message ?: e.javaClass.simpleName}). Nothing was sent to your API keys."
                else "Server mode is on but the server could not start (${e.message ?: e.javaClass.simpleName}). Nothing was sent to your API keys."
            )
        }
        return listOf(Attempt(c.provider.name, modelOverride ?: c.pinnedModel ?: c.localModel ?: c.models.firstOrNull().orEmpty(), cl, patientMs = if (c.providerId == "local") 150_000 else 90_000, connectorId = c.id))
    }

    /** Build: Antigravity only (the client itself switches accounts on a limit). No API key, no Free (slow). */
    internal fun buildAttempts(): List<Attempt> {
        if (!agyBuildReady()) throw NoModelException("Build runs on Antigravity — add an Antigravity account (and Developer tools) first.")
        g.agyClient.pinnedAccountEmail = null   // a chat pick never steers a build — builds use the active account + auto-switch
        // v0.5.1: the stall watchdog is for API models; agy starts a CLI inside Linux and may take a while to speak.
        return listOf(Attempt("Antigravity", g.agy.store.value.model.ifBlank { "auto" }, g.agyClient, patientMs = 180_000))
    }

    private fun attachmentNote(a: List<Attachment>) = if (a.isEmpty()) "" else
        "\n\n[Attached files, saved in the workspace: " + a.joinToString { "uploads/${it.name} (${it.mime}, ${it.size / 1024} KB)" } +
            ". Use read_file / extract_zip / run_command on them.]"

    private fun media(a: List<Attachment>): List<ImagePart> = a.filter {
        (it.mime.startsWith("image/") && it.size < 6_000_000) || (it.mime == "application/pdf" && it.size < 15_000_000)
    }.mapNotNull { runCatching { ImagePart(it.mime, Base64.encodeToString(File(it.path).readBytes(), Base64.NO_WRAP)) }.getOrNull() }

    /** One friendly line + the provider's words (scrubbed of org ids, keys and links) behind "Details". */
    private fun friendly(e: LlmException): String {
        val line = when {
            e.message == FreeFallback.BUSY -> FreeFallback.BUSY
            (e.message ?: "").startsWith(FreeFallback.NAME) -> "The free model didn't answer in time (it's slow and limited). Try again, or add a free Gemini or Groq key in Connectors for fast replies."
            else -> friendlyKind(e)
        }
        val details = (e.message ?: "").replace(Regex("org_[A-Za-z0-9]+"), "org_…").replace(Regex("https?://\\S+"), "").replace(Regex("\\S*\\*{3,}\\S*"), "").take(240)
        return line + "\u0001" + details
    }

    private fun friendlyKind(e: LlmException): String {
        return when (e.kind) {
            FailKind.RATE -> "The free models are busy for a moment (per-minute limit)" + (if (e.retryAfterMs > 0) " — try again in ${(e.retryAfterMs / 1000).coerceAtLeast(1)} s." else " — try again in a few seconds.")
            FailKind.QUOTA -> "Your connected keys have hit their limit for now. Add another free key in Connectors, or try later."
            FailKind.AUTH -> "A key was rejected — check it in Connectors."
            FailKind.NETWORK -> "No connection to the AI right now. Check your internet and try again."
            else -> "The AI didn't answer this time. Try again."
        }
    }

    fun speak(m: Message) {
        if (g.speaker.speakingId.value == m.id) { g.speaker.stop(); return }
        // Phase 2: read aloud the decoded text — never raw %XX escapes.
        g.speaker.speak(m.id, com.mazharansari.x.ui.chat.MessageText.decodeIfEncoded(m.text))
    }

    /** One spoken turn of the Live call ("same voice" mode): a short spoken answer, web search allowed, fast models first. */
    suspend fun callReply(history: List<Pair<String, String>>, text: String, onDelta: ((String) -> Unit)? = null): String = withContext(Dispatchers.Default) {
        val s = g.settings.state.value
        val hist = history.takeLast(12).flatMap { listOf(UserItem(it.first), AssistantItem(it.second)) }
        val out = StringBuilder()
        val sink = object : AgentSink {
            override fun text(delta: String) { synchronized(out) { out.append(delta) }; onDelta?.invoke(delta) }
            override fun reasoning(delta: String) {}
            override fun textToThoughts() { synchronized(out) { out.setLength(0) }; onDelta?.invoke(RESET) }
            override fun step(step: Step) {}
            override fun artifact(a: Artifact) {}
            override fun model(label: String) {}
            override fun discardRound() { synchronized(out) { out.setLength(0) }; onDelta?.invoke(RESET) }
        }
        val voiceRules = "\n\nThis is a LIVE VOICE CALL: your words are spoken aloud. Reply in one to three short, natural spoken sentences " +
            "in the language the user speaks (Urdu, Hindi, English or a mix). NEVER reply in Arabic (the Arabic language). No markdown, lists, emojis, links or code."
        val answer = Agent(g.workspace, g.skills, baseTools = listOf(WebSearchTool()), maxRounds = 4, memory = g.memory).run(
            hist, UserItem(text), attempts = { attempts(Mode.FAST) }, sink = sink,
            profile = Profile(s.assistantName.ifBlank { "X" }, s.userName, s.replyLanguage, s.instructions + voiceRules),
        )
        answer.ifBlank { synchronized(out) { out.toString() } }
    }

    companion object {
        /** callReply's onDelta gets this when text already streamed turned out to be pre-tool "thinking". */ const val RESET = "\u0000reset"
        /**
         * v0.4 small talk: models that answer without reasoning move ahead of the reasoning ones (stable sort — the
         * order is otherwise unchanged), so "hi bhai" starts streaming at once. Normal messages keep the full rotation.
         */
        fun <T> quickOrder(list: List<T>, model: (T) -> String): List<T> =
            list.sortedBy { if (com.mazharansari.x.agent.IntentGate.isReasoningModel(model(it))) 1 else 0 }
    }

    // ---- v0.8.3 Floating call (owner, 2026-11-07): the Gemini Live call lives HERE, at activity
    // scope, not inside the call screen — leaving the call screen used to hang up, but he wants to
    // keep talking while he works in the chat. The call screen is now only a view of this call;
    // AppRoot floats a small draggable orb over the other screens while a call is held here.
    var liveCall: LiveCallController? = null
        private set
    private val _liveCallActive = MutableStateFlow(false)
    val liveCallActive: StateFlow<Boolean> = _liveCallActive
    fun startLiveCall(ctx: android.content.Context, creds: List<ApiKeyCredential>, voiceName: String): LiveCallController {
        liveCall?.let { return it }
        val settings = g.settings.state.value
        val bridge = LiveToolBridge.build(g.workspace, g.skills, settings.toolSwitches)
        val controller = LiveCallController(
            ctx, credentials = creds, autoSwitch = true, restingKeyIds = emptySet(), voiceName = voiceName,
            onTurn = { u, a -> if (!u.isNullOrBlank() || !a.isNullOrBlank()) appendCallTurn(u.orEmpty(), a.orEmpty()) },
            onSystemLine = {}, onKeyExhausted = { _, _ -> }, onKeyActivated = {},
            toolBridge = bridge,
            contextBlock = com.mazharansari.x.livecall.CallContext.build(messages),
        )
        controller.start()
        liveCall = controller
        _liveCallActive.value = true
        // However the call ends on its own (the end_call tool, a hang-up from the screen), the holder lets go.
        viewModelScope.launch {
            controller.uiState.collect { st -> if (st.phase == LiveCallUiState.Phase.ENDED) clearLiveCall() }
        }
        return controller
    }

    /** Hang up from anywhere (the call screen's End, the floating orb's badge). */
    fun endLiveCall() {
        liveCall?.stop()
        clearLiveCall()
    }

    private fun clearLiveCall() {
        liveCall = null
        _liveCallActive.value = false
    }

    /** Saves a spoken call turn into the open chat, like a typed exchange (Kodex does the same). */
    fun appendCallTurn(userText: String, assistantText: String) {
        viewModelScope.launch(Dispatchers.Main) {
            if (userText.isBlank() && assistantText.isBlank()) return@launch
            val now = System.currentTimeMillis()
            if (conversationId == null) {
                conversationId = UUID.randomUUID().toString(); createdAt = now
                title = userText.ifBlank { "Voice call" }.lineSequence().first().take(48)
            }
            val add = buildList {
                if (userText.isNotBlank()) add(Message(UUID.randomUUID().toString(), Role.USER, userText, createdAt = now))
                if (assistantText.isNotBlank()) add(Message(UUID.randomUUID().toString(), Role.ASSISTANT, assistantText, status = MsgStatus.DONE, createdAt = now + 1, model = "Voice call"))
            }
            messages = messages + add
            persist(true)
        }
    }

    override fun onCleared() { sessions.values.forEach { it.job?.cancel() }; g.speaker.stop() }
}
