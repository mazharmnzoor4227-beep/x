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

/** One step of a goal, checked off as it gets done. */
@Serializable
data class GoalStep(
    val text: String,
    val done: Boolean = false,
)

/** One goal: a title plus its steps. Progress is computed from the steps — never stored. */
@Serializable
data class Goal(
    val id: String,
    val title: String,
    val steps: List<GoalStep> = emptyList(),
    val createdAt: Long = 0,
) {
    val doneCount: Int get() = steps.count { it.done }
    val progress: Float get() = if (steps.isEmpty()) 0f else doneCount.toFloat() / steps.size
}

/**
 * Goals tab (new series): the user's goals, saved as one JSON file in the app's private
 * storage, in the same pattern as [MemoryStore]. Starts EMPTY (no demo rule) — a goal
 * arrives only when the user adds one here.
 */
class GoalsStore(ctx: Context) {
    private val file = File(ctx.filesDir, "goals.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _goals = MutableStateFlow(runCatching { json.decodeFromString<List<Goal>>(file.readText()) }.getOrDefault(emptyList()))
    val goals: StateFlow<List<Goal>> = _goals.asStateFlow()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun mutate(f: (List<Goal>) -> List<Goal>) {
        _goals.value = f(_goals.value)
        val snap = _goals.value
        io.launch { synchronized(file) { runCatching { file.writeText(json.encodeToString(snap)) } } }
    }

    /** Adds one goal with its steps (blank lines ignored). Returns null when the title is blank. */
    fun add(title: String, stepTexts: List<String>): Goal? {
        val clean = title.trim()
        if (clean.isBlank()) return null
        val steps = stepTexts.map { it.trim() }.filter { it.isNotBlank() }.take(30).map { GoalStep(it.take(200)) }
        val goal = Goal(UUID.randomUUID().toString(), clean.take(200), steps, System.currentTimeMillis())
        mutate { listOf(goal) + it }
        return goal
    }

    fun toggleStep(goalId: String, stepIndex: Int) = mutate { list ->
        list.map { g ->
            if (g.id != goalId || stepIndex !in g.steps.indices) g
            else g.copy(steps = g.steps.mapIndexed { i, s -> if (i == stepIndex) s.copy(done = !s.done) else s })
        }
    }

    fun remove(goalId: String) = mutate { list -> list.filterNot { it.id == goalId } }
}
