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
)

/**
 * Ideas tab (new series): the user's captured ideas, saved as one JSON file in the app's
 * private storage, in the same pattern as [MemoryStore]. Starts EMPTY (no demo rule) —
 * ideas arrive when the user adds one here or the agent captures one from chat.
 */
class IdeasStore(ctx: Context) {
    private val file = File(ctx.filesDir, "ideas.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _ideas = MutableStateFlow(runCatching { json.decodeFromString<List<Idea>>(file.readText()) }.getOrDefault(emptyList()))
    val ideas: StateFlow<List<Idea>> = _ideas.asStateFlow()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun mutate(f: (List<Idea>) -> List<Idea>) {
        _ideas.value = f(_ideas.value)
        val snap = _ideas.value
        io.launch { synchronized(file) { runCatching { file.writeText(json.encodeToString(snap)) } } }
    }

    /** Adds one idea. Returns null when the text is blank. */
    fun add(text: String, area: String): Idea? {
        val clean = text.trim()
        if (clean.isBlank()) return null
        val idea = Idea(UUID.randomUUID().toString(), clean.take(500), area.ifBlank { "You" }, System.currentTimeMillis())
        mutate { listOf(idea) + it }
        return idea
    }

    fun toggleDone(id: String) = mutate { list -> list.map { if (it.id == id) it.copy(done = !it.done) else it } }

    fun remove(id: String) = mutate { list -> list.filterNot { it.id == id } }
}
