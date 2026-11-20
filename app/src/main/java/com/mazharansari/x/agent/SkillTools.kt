package com.mazharansari.x.agent

import com.mazharansari.x.data.StepKind
import com.mazharansari.x.llm.ToolSpec
import kotlinx.serialization.json.JsonObject

class LoadSkillTool : Tool {
    override val spec = ToolSpec("load_skill", "Load a skill's full instructions by name before doing that kind of task.",
        schema { str("name", "Skill name from the skills list") })
    override fun label(args: JsonObject) = StepLabel(StepKind.SKILL, "Loading skill", args.s("name").orEmpty())
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val name = args.s("name").orEmpty()
        val body = ctx.skills.body(name) ?: return ToolOutput("No skill named '$name'. Available: " + ctx.skills.skills.value.joinToString { it.name }, isError = true)
        return ToolOutput(body, display = body.clip(1500), doneTitle = "Loaded skill")
    }
}

class InstallSkillsTool : Tool {
    override val spec = ToolSpec("install_skills_from_github",
        "When the user gives a GitHub repo URL with skills, list every SKILL.md in it and install them so they can be loaded.",
        schema { str("url", "GitHub repo or folder URL") })
    override fun label(args: JsonObject) = StepLabel(StepKind.SKILL, "Learning skills from", args.s("url").orEmpty().removePrefix("https://"))
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val url = args.s("url").orEmpty()
        val found = runCatching { ctx.skills.scanGitHub(url) }.getOrElse { return ToolOutput("Could not read repo: ${it.message}", isError = true) }
        if (found.isEmpty()) return ToolOutput("No SKILL.md files found in $url", isError = true)
        val result = ctx.skills.install(found, url)
        if (result.added.isEmpty()) {
            val why = result.failed.firstOrNull()?.let { " (${it.name}: ${it.reason})" }.orEmpty()
            return ToolOutput("Could not install any skills from $url$why", isError = true)
        }
        val failedNote = if (result.failed.isNotEmpty()) "\n${result.failed.size} failed: " + result.failed.take(5).joinToString("; ") { "${it.name} — ${it.reason}" } else ""
        return ToolOutput("Installed ${result.added.size} skills:\n" + result.added.joinToString("\n") { "- ${it.name}: ${it.description}" } + failedNote, doneTitle = "Learned ${result.added.size} skills")
    }
}
