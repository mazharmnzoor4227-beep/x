package com.mazharansari.x.agent

import com.mazharansari.x.data.GoalsStore
import com.mazharansari.x.data.IdeasStore
import com.mazharansari.x.data.StepKind
import com.mazharansari.x.llm.ToolSpec
import kotlinx.serialization.json.JsonObject

/**
 * 2026-11-20 (blanket audit, fix 1): the Idea Catcher bot promised to file
 * ideas "in the Ideas tab" and goals "in the Goals tab", but no agent tool
 * wrote to either store — the tabs existed, the tools did not (STUB-MOCK).
 * These two tools close that gap: [SaveIdeaTool] writes to [IdeasStore],
 * [SetGoalTool] writes to [GoalsStore], and both tabs read the same stores,
 * so a saved idea or goal is visible in its tab immediately.
 *
 * The tools hold their store directly (constructor injection, the same
 * precedent as [GenerateImageTool] holding its [ImageGen]): [Agent] builds
 * its own [ToolContext] without ideas/goals, so the stores cannot ride in
 * through the context. They are wired at every [Agent] construction site
 * ([com.mazharansari.x.ui.ChatViewModel], scheduled runs, Telegram) via
 * [ideasGoalsTools], plus the on-device runner's allow-list
 * ([com.mazharansari.x.local.LocalToolUse.ALLOWED_TOOLS]).
 *
 * The Idea Catcher persona's guard stays true: the tool result says "saved"
 * only after [IdeasStore.add] / [GoalsStore.add] actually returned the item.
 */

/** The two tools, ready to append to an agent's base tools. */
fun ideasGoalsTools(ideas: IdeasStore, goals: GoalsStore): List<Tool> =
    listOf(SaveIdeaTool(ideas), SetGoalTool(goals))

class SaveIdeaTool(private val ideas: IdeasStore) : Tool {
    override val spec = ToolSpec("save_idea",
        "Save ONE idea the user mentioned so it appears in the Ideas tab (grouped under 'You' or 'Store') and is never lost. Use it whenever the user shares an idea, a wish, or something to try later — in any chat, not only with the Idea Catcher. The result tells you the idea is actually in the Ideas tab; only then may you say it was saved.",
        schema {
            str("text", "The idea, in the user's own words")
            str("area", "'You' or 'Store' — which area the idea belongs to (default 'You')", required = false)
        })
    override fun label(args: JsonObject) = StepLabel(StepKind.OTHER, "Saving idea", args.s("text").orEmpty().take(48))
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val text = args.s("text").orEmpty()
        if (text.isBlank()) return ToolOutput("Give the idea text to save.", isError = true)
        // The Ideas tab groups by exactly two areas; anything else folds into "You"
        // instead of opening a third group the screen never shows a chip for.
        val area = if (args.s("area")?.trim()?.lowercase() == "store") "Store" else "You"
        val saved = ideas.add(text, area)
            ?: return ToolOutput("That idea was blank, so nothing was saved.", isError = true)
        return ToolOutput("Saved to the Ideas tab ($area): \"${saved.text}\" — it is now in the Ideas tab.", doneTitle = "Saved idea")
    }
}

class SetGoalTool(private val goals: GoalsStore) : Tool {
    override val spec = ToolSpec("set_goal",
        "Turn an idea into a goal in the Goals tab, with small checkable steps and visible progress. Use it when the user says an idea should become a goal, a plan, or something to work towards. The result tells you the goal is actually in the Goals tab; only then may you say it was set.",
        schema {
            str("title", "The goal, in the user's own words")
            str("steps", "The small steps, one per line (optional — a goal with no steps yet is fine)", required = false)
        })
    override fun label(args: JsonObject) = StepLabel(StepKind.OTHER, "Setting goal", args.s("title").orEmpty().take(48))
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val title = args.s("title").orEmpty()
        if (title.isBlank()) return ToolOutput("Give the goal title to set.", isError = true)
        val steps = args.s("steps").orEmpty().lines()
        val saved = goals.add(title, steps)
            ?: return ToolOutput("That goal was blank, so nothing was set.", isError = true)
        return ToolOutput("Goal set in the Goals tab: \"${saved.title}\" with ${saved.steps.size} step${if (saved.steps.size == 1) "" else "s"} (0% · 0/${saved.steps.size}).", doneTitle = "Set goal")
    }
}
