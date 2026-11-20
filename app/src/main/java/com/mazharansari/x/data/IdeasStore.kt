package com.mazharansari.x.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** One captured idea: free text, grouped by area (Store / You), done when acted on. */
@Serializable
data class Idea(
    val id: String,
    val text: String,
    val area: String = "You",
    val createdAt: Long = 0,
    val done: Boolean = false,
    /**
     * Who the idea came from: [IdeaFeed.SOURCE_USER] for the user's own
     * ideas (typed in, or saved by the agent from chat) and
     * [IdeaFeed.SOURCE_STORE] for daily-feed ideas. Defaults to "user", so
     * ideas saved before the feed existed stay the user's own.
     */
    val source: String = IdeaFeed.SOURCE_USER,
)

/**
 * Daily-refresh state for the idea store feed (idea-feed-state.json).
 * Kept apart from the ideas list so a failed refresh can be recorded
 * without touching any idea. The screen reads this: it shows the last
 * successful refresh, and a failed refresh's reason in plain words.
 */
@Serializable
data class FeedRefreshState(
    val lastAttemptAtMs: Long = 0,
    val lastSuccessAtMs: Long = 0,
    val lastFailureAtMs: Long = 0,
    val lastError: String = "",
    val lastAddedCount: Int = 0,
    val everSucceeded: Boolean = false,
)

/**
 * Ideas tab (new series): the user's captured ideas, saved as one JSON file in the app's
 * private storage, in the same pattern as [MemoryStore]. Starts EMPTY (no demo rule) —
 * ideas arrive when the user adds one here or the agent captures one from chat.
 */
class IdeasStore(ctx: Context) {
    private val file = File(ctx.filesDir, "ideas.json")
    private val stateFile = File(ctx.filesDir, "idea-feed-state.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _ideas = MutableStateFlow(runCatching { json.decodeFromString<List<Idea>>(file.readText()) }.getOrDefault(emptyList()))
    val ideas: StateFlow<List<Idea>> = _ideas.asStateFlow()
    private val _refreshState = MutableStateFlow(runCatching { json.decodeFromString<FeedRefreshState>(stateFile.readText()) }.getOrDefault(FeedRefreshState()))
    val refreshState: StateFlow<FeedRefreshState> = _refreshState.asStateFlow()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Bounded feed history: drop stale store ideas on load (user ideas
        // are never pruned). The file is rewritten only when something went.
        val pruned = IdeaFeed.prune(_ideas.value, System.currentTimeMillis())
        if (pruned.size != _ideas.value.size) {
            _ideas.value = pruned
            val snap = pruned
            io.launch { synchronized(file) { runCatching { file.writeText(json.encodeToString(snap)) } } }
        }
    }

    private fun mutateState(f: (FeedRefreshState) -> FeedRefreshState) {
        _refreshState.value = f(_refreshState.value)
        val snap = _refreshState.value
        io.launch { synchronized(stateFile) { runCatching { stateFile.writeText(json.encodeToString(snap)) } } }
    }

    /** A refresh attempt started — the screen shows "Getting new ideas…" until a terminal state lands. */
    fun recordRefreshStarted(nowMs: Long = System.currentTimeMillis()) = mutateState { it.copy(lastAttemptAtMs = nowMs) }

    /** A refresh landed [added] new store ideas. */
    fun recordRefreshSuccess(added: Int, nowMs: Long = System.currentTimeMillis()) =
        mutateState { it.copy(lastSuccessAtMs = nowMs, lastAddedCount = added, lastError = "", everSucceeded = true) }

    /**
     * A refresh failed honestly: yesterday's ideas stay untouched, and the
     * reason is stored so the screen can show it instead of failing silently.
     */
    fun recordRefreshFailure(error: String, nowMs: Long = System.currentTimeMillis()) =
        mutateState { it.copy(lastFailureAtMs = nowMs, lastError = error.take(300)) }

    private fun mutate(f: (List<Idea>) -> List<Idea>) {
        _ideas.value = f(_ideas.value)
        val snap = _ideas.value
        io.launch { synchronized(file) { runCatching { file.writeText(json.encodeToString(snap)) } } }
    }

    /** Adds one idea. Returns null when the text is blank. Source is always "user" — the user's own idea, never a store-feed item. */
    fun add(text: String, area: String): Idea? {
        val clean = text.trim()
        if (clean.isBlank()) return null
        val idea = Idea(UUID.randomUUID().toString(), clean.take(500), area.ifBlank { "You" }, System.currentTimeMillis())
        mutate { listOf(idea) + it }
        return idea
    }

    /**
     * Adds daily-feed ideas (source "store") from a generated batch.
     * Each candidate is quality-checked again and deduped against EVERY
     * idea already in the store — user ideas included — so a generated
     * idea can never duplicate (or overwrite) one of the user's own. The
     * list is then pruned to the bounded feed history. Returns how many
     * were actually added (0 when the batch held nothing new).
     */
    fun addStoreIdeas(candidates: List<IdeaFeed.ParsedIdea>, nowMs: Long = System.currentTimeMillis()): Int {
        if (candidates.isEmpty()) return 0
        val seen = _ideas.value.map { IdeaFeed.normalize(it.text) }.toHashSet()
        val fresh = ArrayList<Idea>()
        for (c in candidates) {
            val text = c.text.trim().take(IdeaFeed.MAX_IDEA_CHARS)
            if (!IdeaFeed.isQualityIdea(text)) continue
            val n = IdeaFeed.normalize(text)
            if (n in seen) continue
            seen += n
            fresh += Idea(UUID.randomUUID().toString(), text, c.area, nowMs, source = IdeaFeed.SOURCE_STORE)
        }
        if (fresh.isEmpty()) return 0
        mutate { list -> IdeaFeed.prune(fresh + list, nowMs) }
        return fresh.size
    }

    fun toggleDone(id: String) = mutate { list -> list.map { if (it.id == id) it.copy(done = !it.done) else it } }

    fun remove(id: String) = mutate { list -> list.filterNot { it.id == id } }
}
