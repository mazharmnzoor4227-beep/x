package com.mazharansari.x.local

import android.app.ActivityManager
import android.content.Context
import com.mazharansari.x.agent.ScriptHint
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
 * shows (coding / chat / Hindi / base model).
 *
 *  - LFM3-1.7B (default): chat + agent + Hindi, 1.10 GB, ~1.4 GB RAM at
 *    runtime. Needs llama.cpp v0.8.0+ — the vendored copy IS v0.8.0
 *    (third_party/llama.cpp/CMakeLists.txt: MAJOR 0 MINOR 8 PATCH 0,
 *    commit dde879bc, 2026-11-01), so no upgrade was needed.
 *  - Qwen2.5-Coder-1.5B-Instruct: CODING only. HumanEval 70.7%.
 *  - Qwen3-1.7B (bartowski Q4_K_M): BASE model — verified 2026-11-20
 *    (full file downloaded + SHA-256-hashed: 1,282,439,584 bytes,
 *    sha256 72c5c3cb…0b24fb). This is the BASE Qwen3-1.7B, NOT
 *    Instruct-2507: no Qwen3-1.7B-Instruct-2507 GGUF exists anywhere on
 *    Hugging Face (official + bartowski instruct repos not found, HF
 *    search empty — all GGUF repos found are base-model quants). So it
 *    is wired ONLY as a raw-completion base model, weak for chat, and
 *    LFM3 stays the chat default. Never present it as instruction-tuned.
 *
 * REJECTED (do not re-add): Qwen3-4B and Gemma 3 4B (do not fit 4 GB RAM);
 * Qwen3-0.6B and Gemma 3 1B (too weak); SmolLM2-1.7B (superseded);
 * DeepSeek-R1-Distill-1.5B (bad chat, burns context on thinking traces).
 * CAVEAT, shown honestly in the Models screen: no candidate covers Urdu
 * properly — Hindi yes, Urdu limited.
 * Sizes and SHA-256 below are Hugging Face's own (checked 1 Nov 2026).
 *
 * TIERED CATALOG (2026-11-20): the same entries are grouped by phone in
 * ModelTiers.kt — 2 GB RAM, 4 GB / 64 GB, 4 GB / 128 GB, 6 GB, 8 GB — so
 * every phone is offered only models that actually run on it. Every new
 * entry below was verified 2026-11-20 against the live Hugging Face API
 * (file list size + resolve HEAD X-Linked-Size / X-Linked-ETag, which is
 * the file's SHA-256 — proven by the three older entries, whose ETags
 * match their earlier download-verified SHA-256s exactly, and by a full
 * 491,400,032-byte download + sha256sum of the 0.5B file, which matched
 * its ETag 74a4da8c…7a9db). No Instruct-2507 GGUF exists: HF search for
 * Qwen3-1.7B-Instruct-2507 and Qwen3-0.6B-Instruct-2507 GGUFs is empty,
 * so the honest instruct all-rounder at ~1.5B is Qwen2.5-1.5B-Instruct.
 * REJECTED in the tiering pass: official Qwen2.5-7B-Instruct Q4_K_M
 * (SPLIT into two files — the downloader handles one file only; the
 * single-file bartowski build is wired instead); Qwen3-4B Q4_K_M (BASE
 * model, verified to exist, but no chat gain over the two 3B instructs
 * on the 6 GB tier); LFM2-350M Q4_K_M (weaker than Qwen2.5-0.5B-Instruct
 * and LFM2 support in the vendored llama.cpp is unproven); Qwen3-0.6B
 * Q4_K_M (BASE model, no gain over the 0.5B instruct on the 2 GB tier).
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
        "https://huggingface.co/bartowski/Qwen_Qwen3-1.7B-GGUF/resolve/main/Qwen_Qwen3-1.7B-Q4_K_M.gguf",
        1_282_439_584, "72c5c3cb38fa32d5256e2fe30d03e7a64c6c79e668ad84057e3bd66e250b24fb", 4, 4096,
        "Base model · raw completion",
        "Base model — NOT instruction-tuned, so weak for chat. Raw completion and code experiments only; for chat, LFM3 (the default) is the right pick."),
    // --- Tiered catalog (2026-11-20, all verified against live Hugging Face, see KDoc above) ---
    QWEN_0_5B("qwen2.5-0.5b", "Qwen2.5 0.5B", "Qwen", "0.5B · Q4_K_M",
        "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
        491_400_032, "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db", 2, 4096,
        "Chat · smallest — for 2 GB phones",
        "Smallest chat model that still answers properly. Instruction-tuned, so it follows simple questions — but very small: short answers only, weak on anything hard. The only model that fits a 2 GB RAM phone."),
    QWEN_1_5B("qwen2.5-1.5b", "Qwen2.5 1.5B", "Qwen", "1.5B · Q4_K_M",
        "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
        1_117_320_736, "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e", 4, 4096,
        "Chat · all-rounder (instruct)",
        "Instruction-tuned all-rounder — the honest chat pick at this size (no Instruct-2507 GGUF exists). Good general chat and short writing; LFM3 stays the default for Hindi and agent work."),
    QWEN_3B("qwen2.5-3b", "Qwen2.5 3B", "Qwen", "3B · Q4_K_M",
        "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
        2_104_932_768, "626b4a6678b86442240e33df819e00132d3ba7dddfe1cdc4fbb18e0a9615c62d", 6, 4096,
        "Chat · stronger all-rounder",
        "Instruction-tuned, noticeably stronger than the 1.5B models at chat, explanations and longer answers. Needs a 6 GB phone — on 4 GB it will likely crash or crawl."),
    LLAMA_3B("llama-3.2-3b", "Llama 3.2 3B", "Meta", "3B · Q4_K_M",
        "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
        2_019_377_696, "6c1a2b41161032677be168d354123594c0e6e67d2b9227c84f296ad037c728ff", 6, 4096,
        "Chat · all-rounder",
        "Instruction-tuned all-rounder from Meta. Strong general chat, similar to Qwen2.5-3B — pick either. Needs a 6 GB phone."),
    QWEN_7B("qwen2.5-7b", "Qwen2.5 7B", "Qwen", "7B · Q4_K_M",
        "https://huggingface.co/bartowski/Qwen2.5-7B-Instruct-GGUF/resolve/main/Qwen2.5-7B-Instruct-Q4_K_M.gguf",
        4_683_074_240, "65b8fcd92af6b4fefa935c625d1ac27ea29dcb6ee14589c55a8f115ceaaa1423", 8, 4096,
        "Chat · strongest on-device",
        "Instruction-tuned 7B — the strongest chat model that fits on a phone. Much better answers than the small models, but slow on CPU and needs an 8 GB phone with ~5 GB free storage."),
    LLAMA_8B("llama-3.1-8b", "Llama 3.1 8B", "Meta", "8B · Q4_K_M",
        "https://huggingface.co/bartowski/Meta-Llama-3.1-8B-Instruct-GGUF/resolve/main/Meta-Llama-3.1-8B-Instruct-Q4_K_M.gguf",
        4_920_739_232, "7b064f5842bf9532c91456deda288a1b672397a54fa729aa665952863033557c", 8, 4096,
        "Chat · strongest on-device",
        "Instruction-tuned 8B from Meta — the biggest model offered. Best on-device answers, slowest on CPU. Needs an 8 GB phone with ~5 GB free storage.");
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
                // Cancel must stop the download at once, not after the next read timeout: the
                // parallel downloader polls this and cancels every chunk's live call.
                val myJob = coroutineContext[Job]
                LinuxRuntime.downloadResumable(m.url, partial(m), m.bytes, isCancelled = { myJob?.isCancelled == true }) { done, total -> set(m, DlState.Downloading(done, total)) }
                set(m, DlState.Verifying)
                val sum = sha256(partial(m))
                if (!sum.equals(m.sha256, true)) { partial(m).delete(); LinuxRuntime.manifestFile(partial(m)).delete(); error("The download was damaged (checksum). Tap download again.") }
                check(partial(m).renameTo(file(m))) { "Could not save the model" }
                set(m, DlState.Ready)
            } catch (e: CancellationException) { set(m, DlState.None); throw e
            } catch (e: Throwable) { set(m, DlState.Failed(e.message ?: "Download failed")) }
        }
    }

    fun cancel(m: LocalModel) { jobs.remove(m.id)?.cancel(); set(m, DlState.None) }
    fun delete(m: LocalModel) { cancel(m); LocalEngine.unloadIf(file(m)); file(m).delete(); partial(m).delete(); LinuxRuntime.manifestFile(partial(m)).delete(); set(m, DlState.None) }

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
            val latestUser = items.filterIsInstance<UserItem>().lastOrNull()?.text.orEmpty()
            val sys = items.filterIsInstance<SystemItem>().firstOrNull()?.text?.let { s ->
                // the full X system prompt is long; small models do better with the essentials —
                // but the language rule is NOT one of the droppable parts. Before this fix the
                // rewrite below deleted the whole LANGUAGE + SCRIPT rule (it keeps only lines
                // mentioning "User's name" / "Instructions") and the per-message SCRIPT NOW
                // hint with it, leaving a 1-word "hi" with one weak sentence on a model whose
                // Urdu is limited — and it answered in Arabic. The shared rule plus a
                // per-message script anchor now ride in every local prompt.
                "You are X, a helpful assistant running offline on the user's phone. " + ScriptHint.LANGUAGE_RULE + " " + ScriptHint.ruleFor(latestUser) + " Be concise. Use Markdown code blocks for code.\n" +
                    s.lines().filter { it.contains("User's name", true) || it.contains("Instructions", true) }.joinToString("\n").take(400)
            }
            val turns = items.mapNotNull { when (it) { is UserItem -> "user" to it.text; is AssistantItem -> it.text.takeIf { t -> t.isNotBlank() }?.let { t -> "assistant" to t }; else -> null } }
            val kept = ArrayList<Pair<String, String>>(); var used = sys?.length ?: 0
            for (t in turns.asReversed()) { val c = t.second.take(6000); if (used + c.length > budgetChars && kept.isNotEmpty()) break; kept.add(0, t.first to c); used += c.length }
            return listOfNotNull(sys?.let { "system" to it }) + kept
        }
    }
}
