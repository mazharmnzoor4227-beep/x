package com.mazharansari.x.local

import android.app.ActivityManager
import android.content.Context
import com.mazharansari.x.linux.LinuxRuntime
import com.mazharansari.x.llm.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.io.File
import java.security.MessageDigest

/** JNI over llama.cpp (app/src/main/cpp/axion_llama.cpp). Loaded only when the user actually uses a local model. */
object LlamaNative {
    interface TokenCallback { fun onToken(piece: String): Boolean }
    @Volatile private var loaded = false
    /** Loads the JNI library (+ llama.cpp's CPU backend variants from [libDir]). Tests pass the x86 build's folder. */
    @Synchronized fun ensure(libDir: String, libPath: String? = null) {
        if (loaded) return
        if (libPath != null) System.load(libPath) else System.loadLibrary("axionllama")
        init(libDir); loaded = true
    }
    @JvmStatic external fun init(libDir: String)
    @JvmStatic external fun lastError(): String
    @JvmStatic external fun systemInfo(): String
    @JvmStatic external fun load(path: String, nCtx: Int, nThreads: Int): Long
    @JvmStatic external fun free(handle: Long)
    @JvmStatic external fun stop(handle: Long)
    @JvmStatic external fun contextSize(handle: Long): Int
    @JvmStatic external fun applyTemplate(handle: Long, roles: Array<String>, contents: Array<String>): String
    @JvmStatic external fun generate(handle: Long, prompt: String, maxTokens: Int, temperature: Float, cb: TokenCallback): Int
}

/**
 * Downloadable on-device models — the curated shortlist (redesign round 2,
 * Addendum G, research 2026-11-19). Exactly three, all ~1–2 GB, all fitting
 * a 4 GB RAM phone; nothing is bundled in the APK, each downloads only on
 * tap and is SHA-256-checked. [purpose] is the label the Models screen
 * shows (coding / chat / Hindi / all-rounder).
 *
 *  - LFM3-1.7B (default): chat + agent + Hindi, 1.10 GB, ~1.4 GB RAM at
 *    runtime. Needs llama.cpp v0.8.0+ — the vendored copy IS v0.8.0
 *    (third_party/llama.cpp/CMakeLists.txt: MAJOR 0 MINOR 8 PATCH 0,
 *    commit dde879bc, 2026-11-01), so no upgrade was needed.
 *  - Qwen2.5-Coder-1.5B-Instruct: CODING only. HumanEval 70.7%.
 *  - Qwen3-1.7B-Instruct-2507: ALL-ROUNDER, ~1.28 GB. Its exact GGUF
 *    filename/size/SHA-256 could NOT be confirmed (the Hugging Face API
 *    fetch was refused, 401; web search confirmed only the naming
 *    convention, not the file), so its [url] is blank on purpose and the
 *    store refuses to download it until the file is confirmed — never a
 *    guessed URL. Fill in url/bytes/sha256 here when the file is verified.
 *
 * REJECTED (do not re-add): Qwen3-4B and Gemma 3 4B (do not fit 4 GB RAM);
 * Qwen3-0.6B and Gemma 3 1B (too weak); SmolLM2-1.7B (superseded);
 * DeepSeek-R1-Distill-1.5B (bad chat, burns context on thinking traces).
 * CAVEAT, shown honestly in the Models screen: no candidate covers Urdu
 * properly — Hindi yes, Urdu limited.
 * Sizes and SHA-256 below are Hugging Face's own (checked 1 Nov 2026).
 */
enum class LocalModel(val id: String, val title: String, val family: String, val params: String, val url: String, val bytes: Long, val sha256: String,
                      val minRamGb: Int, val ctx: Int, val purpose: String, val blurb: String) {
    LFM3_1_7B("lfm3-1.7b", "LFM3 1.7B", "Liquid AI", "1.7B · Q4_K_M",
        "https://huggingface.co/LiquidAI/LFM3-1.7B-GGUF/resolve/main/LFM3-1.7B-Q4_K_M.gguf",
        1_099_627_808, "3793e1e6e2266919d6e015287221b41100175a98eddaba92e111aed3b05c93c4", 4, 8192,
        "Chat · Agent · Hindi — default",
        "Default. Chat + agent + Hindi. Fast offline chat, summaries, translation and quick answers with no internet — Hindi works well, Urdu is limited. Long memory (8k)."),
    QWEN_CODER_1_5B("qwen2.5-coder-1.5b", "Qwen2.5-Coder 1.5B", "Qwen", "1.5B · Q4_K_M",
        "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
        1_117_320_768, "cc324af070c2ecbfd324a30884d2f951a7ff756aba85cb811a6ec436933bb046", 4, 4096,
        "Coding only",
        "Coding only. Short scripts, explaining code, small fixes. HumanEval 70.7%."),
    QWEN3_1_7B("qwen3-1.7b", "Qwen3 1.7B", "Qwen", "1.7B · Q4_K_M",
        "", 0, "", 4, 4096,
        "All-rounder",
        "All-rounder — chat and code in one model. Download link not confirmed yet: the exact file is being checked, so this one cannot download until the next update.");
    companion object { fun byId(id: String?) = entries.firstOrNull { it.id == id } }
}

sealed interface DlState {
    data object None : DlState
    data class Downloading(val done: Long, val total: Long) : DlState
    data object Verifying : DlState
    data object Ready : DlState
    data class Failed(val message: String) : DlState
}

/** Downloads (on tap only), verifies, lists and deletes local models; checks RAM before loading. */
class LocalModelStore(private val ctx: Context) {
    val dir = File(ctx.filesDir, "models").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = HashMap<String, Job>()
    private val _state = MutableStateFlow(LocalModel.entries.associate { it.id to (if (file(it).isFile) DlState.Ready else DlState.None) as DlState })
    val state: StateFlow<Map<String, DlState>> = _state.asStateFlow()

    fun file(m: LocalModel) = File(dir, m.url.substringAfterLast('/').ifBlank { ".${m.id}.missing" })
    private fun partial(m: LocalModel) = File(dir, m.url.substringAfterLast('/') + ".part")
    private fun set(m: LocalModel, s: DlState) { _state.value = _state.value + (m.id to s) }
    fun downloaded(): List<LocalModel> = LocalModel.entries.filter { file(it).isFile }
    fun freeBytes(): Long = runCatching { android.os.StatFs(dir.absolutePath).availableBytes }.getOrDefault(0L)

    /** Phone RAM in GB (total) and what's free right now. */
    fun ram(): Pair<Double, Double> {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo(); am.getMemoryInfo(mi)
        return mi.totalMem / 1073741824.0 to mi.availMem / 1073741824.0
    }

    fun download(m: LocalModel) {
        if (m.url.isBlank()) { set(m, DlState.Failed("Download link not confirmed yet — the exact file is being checked. Try again after the next update.")); return }
        if (jobs[m.id]?.isActive == true) return
        jobs[m.id] = scope.launch {
            try {
                val need = m.bytes - (if (partial(m).exists()) partial(m).length() else 0) + 200L * 1024 * 1024
                check(freeBytes() >= need) { "Not enough space: needs ${need / 1048576} MB free." }
                set(m, DlState.Downloading(partial(m).takeIf { it.exists() }?.length() ?: 0, m.bytes))
                LinuxRuntime.downloadResumable(m.url, partial(m), m.bytes) { done, total -> set(m, DlState.Downloading(done, total)) }
                set(m, DlState.Verifying)
                val sum = sha256(partial(m))
                if (!sum.equals(m.sha256, true)) { partial(m).delete(); error("The download was damaged (checksum). Tap download again.") }
                check(partial(m).renameTo(file(m))) { "Could not save the model" }
                set(m, DlState.Ready)
            } catch (e: CancellationException) { set(m, DlState.None); throw e
            } catch (e: Throwable) { set(m, DlState.Failed(e.message ?: "Download failed")) }
        }
    }

    fun cancel(m: LocalModel) { jobs.remove(m.id)?.cancel(); set(m, DlState.None) }
    fun delete(m: LocalModel) { cancel(m); LocalEngine.unloadIf(file(m)); file(m).delete(); partial(m).delete(); set(m, DlState.None) }

    companion object {
        fun sha256(f: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            f.inputStream().use { s -> val b = ByteArray(1 shl 20); while (true) { val n = s.read(b); if (n < 0) break; md.update(b, 0, n) } }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
        /** Honest RAM check: the model file + ~0.6 GB for context/runtime must fit in what's free now. */
        fun ramVerdict(m: LocalModel, totalGb: Double, availGb: Double): String? = when {
            totalGb + 0.3 < m.minRamGb -> "This phone has %.0f GB RAM; ${m.title} needs ${m.minRamGb} GB+. It will likely crash or be very slow.".format(totalGb)
            availGb < m.bytes / 1073741824.0 + 0.6 -> "Only %.1f GB RAM free right now — close other apps first, or pick a smaller model.".format(availGb)
            else -> null
        }
    }
}

/** One model loaded at a time (they're big). Thread-safe; unloads when another model is picked or deleted. */
object LocalEngine {
    private var handle = 0L
    private var path: String? = null
    private val lock = Any()
    var libDir: String = ""
    var libPath: String? = null

    fun session(file: File, ctx: Int): Long = synchronized(lock) {
        if (handle != 0L && path == file.absolutePath) return handle
        if (handle != 0L) { LlamaNative.free(handle); handle = 0 }
        LlamaNative.ensure(libDir, libPath)
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
        val h = LlamaNative.load(file.absolutePath, ctx, threads)
        if (h == 0L) throw LlmException("The local model couldn't load: " + LlamaNative.lastError().trim().ifBlank { "not enough memory?" }, FailKind.SERVER)
        handle = h; path = file.absolutePath; h
    }
    fun unloadIf(file: File) = synchronized(lock) { if (path == file.absolutePath && handle != 0L) { LlamaNative.free(handle); handle = 0; path = null } }
    fun stop() { val h = handle; if (h != 0L) LlamaNative.stop(h) }
}

/**
 * Ask provider "Local model": runs the GGUF on the phone's CPU, offline. No tools (the small models can't drive them), short
 * history (4k context). Honest label everywhere: slower and weaker than API models.
 */
class LocalLlmClient(private val model: File, private val ctx: Int = 4096, private val maxTokens: Int = 768) : LlmClient {
    override fun stream(req: LlmRequest): Flow<LlmEvent> = callbackFlow {
        val job = launch(Dispatchers.Default) {
            try {
                val h = LocalEngine.session(model, ctx)
                val msgs = toMessages(req.items, budgetChars = (LlamaNative.contextSize(h) - maxTokens) * 3)
                val prompt = LlamaNative.applyTemplate(h, msgs.map { it.first }.toTypedArray(), msgs.map { it.second }.toTypedArray())
                // P2-4: honour an explicit temperature (the local tool-use loop asks for 0.2 so the
                // small model emits strict JSON); otherwise the usual quick/standard values.
                val temp = req.temperature?.toFloat()?.coerceIn(0f, 2f) ?: if (req.quick) 0.6f else 0.4f
                val n = LlamaNative.generate(h, prompt, maxTokens, temp, object : LlamaNative.TokenCallback {
                    override fun onToken(piece: String): Boolean = trySend(LlmEvent.Text(piece)).isSuccess && isActive
                })
                if (n == -1) throw LlmException("This chat is too long for the local model's memory (4k). Start a new chat.", FailKind.BAD_REQUEST)
                if (n == -2) throw LlmException("The local model failed while answering.", FailKind.SERVER)
                trySend(LlmEvent.Finished("stop"))
                close()
            } catch (e: CancellationException) { close() } catch (e: Throwable) { close(e as? LlmException ?: LlmException(e.message ?: "Local model error", FailKind.SERVER)) }
        }
        awaitClose { LocalEngine.stop(); job.cancel() }
    }

    companion object {
        /** System + the most recent turns that fit in [budgetChars] (~3 chars per token). */
        fun toMessages(items: List<ChatItem>, budgetChars: Int): List<Pair<String, String>> {
            val sys = items.filterIsInstance<SystemItem>().firstOrNull()?.text?.let { s ->
                // the full X system prompt is long; small models do better with the essentials
                "You are X, a helpful assistant running offline on the user's phone. Reply in the user's own language and script (Roman Urdu stays Roman Urdu). Be concise. Use Markdown code blocks for code.\n" +
                    s.lines().filter { it.contains("User's name", true) || it.contains("Instructions", true) }.joinToString("\n").take(400)
            }
            val turns = items.mapNotNull { when (it) { is UserItem -> "user" to it.text; is AssistantItem -> it.text.takeIf { t -> t.isNotBlank() }?.let { t -> "assistant" to t }; else -> null } }
            val kept = ArrayList<Pair<String, String>>(); var used = sys?.length ?: 0
            for (t in turns.asReversed()) { val c = t.second.take(6000); if (used + c.length > budgetChars && kept.isNotEmpty()) break; kept.add(0, t.first to c); used += c.length }
            return listOfNotNull(sys?.let { "system" to it }) + kept
        }
    }
}
