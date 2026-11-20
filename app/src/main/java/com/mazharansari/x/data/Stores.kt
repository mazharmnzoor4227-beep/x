package com.mazharansari.x.data

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class AppSettings(
    val theme: String = "dark",          // system | dark | light
    val textScale: Float = 1f,
    val haptics: Boolean = true,
    val hapticsStreaming: Boolean = false,
    /** Phase 2: a soft click sound on taps. Default ON; Settings → Appearance turns it off. */
    val touchSound: Boolean = true,
    // Phase 2 (settings audit): appLanguage REMOVED — the picker saved a value no code ever
    // read, so it was a fake setting. Old settings.json files still decode (ignoreUnknownKeys).
    val speechLang: String = "ur-PK",    // dictation + read-aloud language
    /**
     * 2026-11-19, picker redesigned 2026-11-20: the read-aloud / voice-call
     * voice preference — one id from the Voice & Language picker
     * (voice/VoicePicker.kt): a Piper pack ("piper-aegis" | "piper-fasih" |
     * "piper-amy" | "kokoro-hindi") or a catalog voice ("kodex:*" |
     * "edge:*" | "gemini:*"). First pick is the user's standing natural
     * voice, Piper Urdu Aegis (offline, on-device). The old "system" value
     * is legacy: opening Voice & Language migrates it to "piper-aegis"
     * (VoicePicker.migrateVoicePref). A Piper pick reads in the phone's
     * built-in Urdu/Hindi voice until its pack + the shared espeak-ng-data
     * are downloaded — see voice/PiperVoice.kt. The Edge/Gemini chain stays
     * as the online fallback.
     */
    val voicePref: String = "piper-aegis",
    val voice: String = "Kore",          // Gemini prebuilt voice
    val naturalVoice: Boolean = false,    // Gemini TTS when a Gemini key exists, else Android TTS
    val autoRead: Boolean = false,
    val userName: String = "",
    /**
     * The user's own profile photo (2026-11-20): a [BotLogo] string — "" =
     * the monogram default, "file:<absolute path>" = a photo the user
     * picked, copied into the app's own storage (profile-photos/) so it
     * survives the gallery permission being revoked. Shown in the menu
     * header, the Settings profile card and the Customize screen.
     */
    val userPhoto: String = "",
    val assistantName: String = "X",
    val instructions: String = "",
    val replyLanguage: String = "",
    /**
     * Speed tier: AUTO (default) / FAST / EXPERT / LOCAL / SERVER / FREE.
     *
     * 2026-11-20 LFM3 default (user decision): a stored AUTO no longer means
     * "cloud FAST first". It means LFM3-1.7B answers first whenever its file is
     * downloaded on the phone — see [prefersLfm3Default] — with the cloud
     * ranking (allam-2-7b demoted to last) as the backup. No stored value is
     * rewritten for this migration: existing AUTO installs pick the new
     * default up automatically once LFM3 is downloaded. An explicit LOCAL /
     * SERVER / FREE mode, or any stored [pickedModel], is never overridden.
     */
    val mode: String = "AUTO",
    /**
     * Model chooser (2026-11-20): the model/account the user picked in the
     * chat's model selector — "api:<connectorId>:<model>" | "local:<model>" |
     * "server:<model>" | "free" | "agy:<email>". "" = Auto (the ranked
     * failover order, as before). The pick sticks until the user changes it;
     * an auto-shift re-picks the model it landed on, so the shift sticks too.
     * See [com.mazharansari.x.llm.ModelChoice].
     */
    val pickedModel: String = "",
    /** v0.6 Build: the AI's file changes wait for Approve (diff review) before they touch the project. */
    val reviewBeforeApply: Boolean = true,
    /** v0.6.1: a project too large to hold for review (over the ChangeGate limits) may run un-reviewed ONLY if the user switched this on — never silently. */
    val allowLargeUngated: Boolean = false,
    /** v0.6 Tools library: group id to on/off (missing = the group default). */
    val toolSwitches: Map<String, Boolean> = emptyMap(),
    val pasteAsFile: String = "Always",
    /** Developer tools › Free Mode: route only to free-tier providers (and Antigravity / Free (slow)). */
    val freeMode: Boolean = false,
    /** v0.11: the composer's Research toggle — while on, factual questions go through the deep_research pipeline. */
    val deepResearch: Boolean = false,
    /** v0.12: the composer's Long task toggle — while on, ACTION turns get a far bigger step/tool budget and plan-verify-finish guidance. */
    val longTask: Boolean = false,
    /** P1-3: the composer's Turbo (server) toggle — while on, shell work for this task runs on the
     * user's own server (Settings → Turbo) instead of the on-device Linux. Default OFF; the
     * server setup must also be complete, or the toggle does nothing and the run says so. */
    val turbo: Boolean = false,
    /** v0.5: the Build first-run "Install developer tools" popup was shown (addendum 2). */
    val buildIntroShown: Boolean = false,
    /** v0.5.3: the user chose "Continue as guest" on the sign-in screen (it then stays away; Settings → Account signs in). */
    val guestChosen: Boolean = false,
    /** v0.5.3 first run: the intro animation and the permissions screens were shown once. */
    val introShown: Boolean = false,
    val permsShown: Boolean = false,
    /** P2-8: the read-only status page (localhost only, no control surface). Default OFF. */
    val statusServer: Boolean = false,
    /** 0.9.6: the engine the Browser screen's "+" picker last created a tab with ("webview" / "obscura"). */
    val lastUserBrowserEngine: String = "webview",
    /**
     * 0.9.6: whether agent browsing opens the Browser screen on its own. Default
     * OFF — nothing in the app navigates to Route.Browser on the agent's behalf
     * today (only the user's tap in Settings does), so this stays a stored
     * preference until a caller ever needs the gate.
     */
    val openBrowserOnAgentBrowsing: Boolean = false,
) {
    /**
     * LFM3 default (2026-11-20): true when the stored settings mean "answer
     * with on-device LFM3-1.7B first". Only a stored AUTO with no chooser pick
     * qualifies, and only once LFM3 is actually downloaded ([lfm3Downloaded]
     * is the LocalModelStore file check, done by the caller) — until then the
     * answer is the honest cloud fallback, never allam first. ACTION turns
     * are excluded by the caller (ChatViewModel.attempts): the small model
     * cannot drive tools, so an action turn starting on LFM3 would end as
     * plain text with the work never done.
     */
    fun prefersLfm3Default(lfm3Downloaded: Boolean): Boolean =
        pickedModel.isBlank() && mode == "AUTO" && lfm3Downloaded
}

class SettingsStore(ctx: Context) {
    private val file = File(ctx.filesDir, "settings.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _s = MutableStateFlow(runCatching { json.decodeFromString<AppSettings>(file.readText()) }.getOrDefault(AppSettings()))
    val state: StateFlow<AppSettings> = _s.asStateFlow()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun update(f: (AppSettings) -> AppSettings) {
        _s.value = f(_s.value)
        val snap = _s.value
        // Crash hunt (2026-11-20): a failed write (disk full, files dir gone)
        // used to escape this launch uncaught — a SupervisorJob child still
        // delivers it to the default uncaught-exception handler, i.e. the app
        // died mid-tap. The in-memory state is already updated; the disk copy
        // is retried on the next update.
        io.launch { synchronized(file) { runCatching { file.writeText(json.encodeToString(snap)) } } }
    }
}

/** Conversations as one JSON file each; the drawer reads a small in-memory index. Writes are coalesced off the main thread. */
class ConversationStore(ctx: Context) {
    private val dir = File(ctx.filesDir, "chats").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val _index = MutableStateFlow<List<ConversationMeta>>(emptyList())
    val index: StateFlow<List<ConversationMeta>> = _index.asStateFlow()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val pending = HashMap<String, Job>()

    init {
        io.launch {
            val disk = dir.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { f ->
                runCatching { json.decodeFromString<Conversation>(f.readText()) }.getOrNull()?.let(::meta)
            }.sortedByDescending { it.updatedAt }
            // Crash hunt follow-up (2026-11-20): a save updates the
            // in-memory index synchronously but writes the file
            // asynchronously — if this listing lands after a save whose
            // write failed (disk full, chats dir gone), a blind replace
            // drops that conversation from the drawer even though the save
            // reported success. Keep in-memory entries the disk listing
            // does not know about; disk wins on conflicts (same id = the
            // same conversation, just flushed).
            val current = _index.value
            val diskIds = disk.map { it.id }.toSet()
            _index.value = (disk + current.filterNot { it.id in diskIds })
                .sortedByDescending { it.updatedAt }
        }
    }

    private fun meta(c: Conversation) = ConversationMeta(c.id, c.title, c.pinned, c.updatedAt,
        c.messages.lastOrNull { it.role == Role.ASSISTANT }?.text?.take(80).orEmpty(), c.section, c.botId, c.botName)

    suspend fun load(id: String): Conversation? = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString<Conversation>(File(dir, "$id.json").readText()) }.getOrNull()?.let(::settle)
    }

    /** v0.5.2 bug hunt: a reply that was streaming when Android killed the app stayed "Thinking…" forever on reopen. */
    internal fun settle(c: Conversation): Conversation =
        if (c.messages.none { it.status == MsgStatus.STREAMING }) c
        else c.copy(messages = c.messages.map { if (it.status == MsgStatus.STREAMING) it.copy(status = MsgStatus.INTERRUPTED, notice = "",
            steps = it.steps.map { st -> if (st.status == StepStatus.RUNNING) st.copy(status = StepStatus.ERROR) else st }) else it })

    /** Debounced save: during streaming this runs at most every 600 ms. */
    fun save(c: Conversation, immediate: Boolean = false) {
        val m = meta(c)
        _index.value = (listOf(m) + _index.value.filterNot { it.id == c.id }).sortedWith(compareByDescending<ConversationMeta> { it.updatedAt })
        synchronized(pending) {
            pending[c.id]?.cancel()
            pending[c.id] = io.launch {
                if (!immediate) delay(600)
                // Crash hunt (2026-11-20): this runs on every send and every
                // streaming flush — a failed write (disk full, chats dir gone)
                // used to escape uncaught and kill the app mid-message. The
                // in-memory index above is already updated; the file is
                // rewritten on the next save.
                runCatching { File(dir, "${c.id}.json.tmp").apply { writeText(json.encodeToString(Scrub.conversation(c))); renameTo(File(dir, "${c.id}.json")) } }
            }
        }
    }

    /**
     * v0.10 (Hermes learning loop): full-text search over every saved conversation — the agent's
     * `session_search` tool. Every keyword must appear in a message; each hit carries an excerpt
     * around the first matching keyword so the model can answer "what did we decide about …?"
     * without loading whole chats. Newest conversations first.
     */
    suspend fun search(query: String, limit: Int = 8): List<ChatSearchHit> = withContext(Dispatchers.IO) {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.length > 1 }
        if (words.isEmpty()) return@withContext emptyList()
        val hits = mutableListOf<ChatSearchHit>()
        val files = dir.listFiles { f -> f.extension == "json" }.orEmpty().sortedByDescending { it.lastModified() }
        for (f in files) {
            if (hits.size >= limit) break
            val c = runCatching { json.decodeFromString<Conversation>(f.readText()) }.getOrNull() ?: continue
            for (m in c.messages) {
                if (hits.size >= limit) break
                val text = m.text
                if (text.isBlank()) continue
                val lower = text.lowercase()
                if (!words.all { it in lower }) continue
                val at = words.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: 0
                val from = (at - 90).coerceAtLeast(0)
                val to = (at + 190).coerceAtMost(text.length)
                hits += ChatSearchHit(c.id, c.title, c.updatedAt, m.role,
                    (if (from > 0) "…" else "") + text.substring(from, to).replace('\n', ' ').trim() + if (to < text.length) "…" else "")
            }
        }
        hits
    }

    /**
     * Redesign round 2 (§3.3): the newest saved, non-worker conversation that
     * ran as bot [botId], or null when the bot has no thread yet. Old data may
     * hold several threads per bot (every old startBotChat made a fresh one);
     * the newest wins and the older ones stay visible in CHATS as real history.
     */
    fun latestForBot(botId: String): ConversationMeta? =
        _index.value.filter { it.botId == botId && it.section != "worker" }.maxByOrNull { it.updatedAt }

    fun delete(id: String) { _index.value = _index.value.filterNot { it.id == id }; io.launch { File(dir, "$id.json").delete() } }

    fun rename(id: String, title: String) = io.launch { load(id)?.let { save(it.copy(title = title), true) } }
    fun pin(id: String, pinned: Boolean) = io.launch { load(id)?.let { save(it.copy(pinned = pinned), true) } }
    fun deleteAll() { _index.value = emptyList(); io.launch { dir.listFiles()?.forEach { it.delete() } } }
}

/** v0.5.2: chats are saved with every API key / token masked (gsk_••••abcd) — text, thinking, steps, errors. */
object Scrub {
    private fun s(t: String) = com.mazharansari.x.security.SecretScanner.scrub(t)
    fun message(m: Message): Message = m.copy(
        text = s(m.text), reasoning = s(m.reasoning), error = s(m.error),
        steps = m.steps.map { st -> st.copy(detail = s(st.detail), output = s(st.output), target = s(st.target), title = s(st.title)) },
    )
    fun conversation(c: Conversation): Conversation = c.copy(title = s(c.title), messages = c.messages.map(::message))
}
