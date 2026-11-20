package com.mazharansari.x.local

import com.mazharansari.x.agent.AgentSink
import com.mazharansari.x.agent.ApprovalGate
import com.mazharansari.x.agent.ScriptHint
import com.mazharansari.x.agent.Tool
import com.mazharansari.x.agent.ToolContext
import com.mazharansari.x.agent.ToolOutput
import com.mazharansari.x.agent.clip
import com.mazharansari.x.data.ApprovalsStore
import com.mazharansari.x.data.Step
import com.mazharansari.x.data.StepStatus
import com.mazharansari.x.llm.AssistantItem
import com.mazharansari.x.llm.ChatItem
import com.mazharansari.x.llm.SystemItem
import com.mazharansari.x.llm.ToolResultItem
import com.mazharansari.x.llm.ToolSpec
import com.mazharansari.x.llm.UserItem
import com.mazharansari.x.security.SecretScanner
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.util.UUID

/** One generation from the local model: text, wall time, and the token estimate (≈ chars / 4, same as HistoryCompressor). */
data class LocalGeneration(val text: String, val ms: Long, val approxTokens: Int) {
    val tokPerSec: Double get() = if (ms <= 0) 0.0 else approxTokens * 1000.0 / ms
}

/** Measured local-model speed, shown in the chat UI (P2-4). */
data class LocalSpeed(val tokens: Int, val ms: Long) {
    val tokPerSec: Double get() = if (ms <= 0) 0.0 else tokens * 1000.0 / ms
    fun label(): String = "Local model · ${"%.1f".format(tokPerSec)} tok/s · $tokens tokens"
}

data class LocalToolStep(val tool: String, val arguments: String, val result: String, val ok: Boolean)

data class LocalAgentResult(val answer: String, val steps: List<LocalToolStep>, val generations: List<LocalGeneration>) {
    val totalTokens: Int get() = generations.sumOf { it.approxTokens }
    val totalMs: Long get() = generations.sumOf { it.ms }
    val speed: LocalSpeed get() = LocalSpeed(totalTokens, totalMs)
}

/** What one local-model reply parses to. */
sealed interface ParsedCall {
    data class Call(val tool: String, val args: JsonObject) : ParsedCall
    data class Final(val text: String) : ParsedCall
    data class Invalid(val reason: String) : ParsedCall
}

/**
 * P2-4: function calling for the on-device model (LFM3 1.7B and the other local models).
 *
 * Honest limit, stated here and in the report: the llama.cpp JNI in this repo
 * (app/src/main/cpp/axion_llama.cpp, `generate`) samples with a plain top-k / top-p /
 * temperature chain — it exposes NO grammar parameter, so true grammar-constrained
 * JSON (llama.cpp GBNF) is NOT active. What this file does instead, entirely in
 * Kotlin without touching the native library:
 *   1. a strict tool-call prompt (low temperature, one JSON object, nothing else),
 *   2. a tolerant JSON extractor + validator (fenced or bare JSON, balanced braces),
 *   3. a bounded tool loop (file + shell tools only) that feeds each result back,
 *   4. measured tok/s on every generation, shown in the chat UI.
 * A grammar-constrained sampler would need a new JNI entry point (`generateWithGrammar`)
 * plus a GBNF schema builder in C++ — a native change, deliberately not done here.
 */
object LocalToolUse {
    /**
     * Tools the on-device model may call. File + shell are the original set (the small model
     * cannot drive the rest safely); memory_* joined in the 2026-11-20 memory fix, because
     * LOCAL-mode action turns otherwise had no memory at all — no snapshot in the prompt and
     * no way to save — while the same user in a cloud chat could remember things. The runner
     * below injects the snapshot and a one-line save rule next to the tool list.
     */
    val ALLOWED_TOOLS = setOf("read_file", "write_file", "list_files", "run_command", "shell_session",
        "memory_add", "memory_replace", "memory_remove", "memory_read",
        // 2026-11-20 (blanket audit): the on-device model files ideas and goals too — the same
        // save_idea / set_goal tools the cloud agent gets, so LOCAL-mode chats can file them.
        "save_idea", "set_goal")

    fun approxTokens(chars: Int): Int = (chars + 3) / 4

    /** The tool-call system prompt: short (small models, 4k context) and strict about the format. */
    fun buildToolPrompt(tools: List<ToolSpec>): String = buildString {
        appendLine("You are X, running offline on the user's phone. You can use tools. Reply with exactly ONE JSON object and nothing else.")
        // The final {"answer": ...} is user-visible: without this line the local tool loop
        // carried NO language rule at all, and a 1-word greeting drifted to Arabic.
        appendLine("LANGUAGE: ${ScriptHint.LANGUAGE_RULE}")
        appendLine("TOOLS:")
        for (t in tools) {
            appendLine("- ${t.name} ${t.parameters} — ${t.description.lineSequence().firstOrNull().orEmpty().take(120)}")
        }
        appendLine("FORMAT — a tool call: {\"tool\": \"read_file\", \"arguments\": {\"path\": \"notes.txt\"}}")
        appendLine("FORMAT — a final answer (no more tools needed): {\"answer\": \"your reply here\"}")
        appendLine("RULES: one JSON object only, no text outside it. Use tools for real work; answer directly when no tool is needed. Never invent file contents — read the file first.")
        // BRAIN (2026-11-20): the same brain discipline, compressed for a small
        // on-device model — short plan, one tool per turn, check each step,
        // verify before saying done (or say "could not verify").
        appendLine(com.mazharansari.x.agent.Brain.SMALL_MODEL_GUIDANCE)
    }

    /** Pulls the first balanced JSON object out of the reply (fenced ```json or bare, prose around it allowed). */
    fun extractJson(text: String): String? {
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(text.trim())?.groupValues?.get(1)?.trim()
        val src = fenced ?: text
        val start = src.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inStr = false
        var esc = false
        for (i in start until src.length) {
            val c = src[i]
            if (esc) { esc = false; continue }
            when (c) {
                '\\' -> if (inStr) esc = true
                '"' -> inStr = !inStr
                '{' -> if (!inStr) depth++
                '}' -> if (!inStr) {
                    depth--
                    if (depth == 0) return src.substring(start, i + 1)
                }
            }
        }
        return null
    }

    fun parse(text: String, allowed: Set<String>): ParsedCall {
        val json = extractJson(text) ?: return ParsedCall.Invalid("no JSON object found")
        val obj = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull()
            ?: return ParsedCall.Invalid("not valid JSON")
        (obj["answer"] as? JsonPrimitive)?.contentOrNull?.let { return ParsedCall.Final(it) }
        val tool = (obj["tool"] as? JsonPrimitive)?.contentOrNull
            ?: return ParsedCall.Invalid("no \"tool\" field")
        if (tool !in allowed) return ParsedCall.Invalid("unknown tool \"$tool\"")
        val args = obj["arguments"] as? JsonObject
            ?: return ParsedCall.Invalid("\"arguments\" must be an object")
        return ParsedCall.Call(tool, args)
    }
}

/**
 * The on-device agent loop: prompt → generate → parse → run the tool → feed the result back,
 * up to [maxSteps] tool calls, then a final answer. Every generation is timed, so the chat
 * can show real tok/s. Tool steps are reported through [sink] exactly like the cloud agent's,
 * so the UI shows them the same way. Approval-gated shell work follows the same rule as the
 * cloud agent in a headless run: a remembered rule passes, anything else is refused here
 * (there is no dialog inside this loop — the user approves from the chat instead).
 */
class LocalAgentRunner(
    private val generate: suspend (items: List<ChatItem>) -> LocalGeneration,
    tools: List<Tool>,
    private val ctx: ToolContext,
    private val approvals: ApprovalsStore? = null,
    private val sink: AgentSink? = null,
    private val maxSteps: Int = 6,
) {
    private val byName = tools.filter { it.spec.name in LocalToolUse.ALLOWED_TOOLS }.associateBy { it.spec.name }

    suspend fun run(history: List<ChatItem>, user: UserItem): LocalAgentResult {
        val items = ArrayList<ChatItem>()
        // Memory fix (2026-11-20): the local loop built its prompt from the tool list alone, so
        // it never saw saved memory and never knew it could save. The snapshot rides here (read),
        // and the save rule rides with it — the small model only saves when the user states a
        // durable preference/fact or says "remember this", same rule as the cloud agent.
        val memBlock = ctx.memory?.promptBlock().orEmpty()
        val memRule = if (byName.containsKey("memory_add"))
            "\nMEMORY: your saved memory is below (use it, personalise with it). If the user states a durable preference or fact, or says 'remember this', call memory_add first (target 'user' for who they are and how they like things, 'memory' for facts and lessons), then answer. Never save passwords, keys or one-off small talk." else ""
        items += SystemItem(LocalToolUse.buildToolPrompt(byName.values.map { it.spec }) + "\n" + ScriptHint.ruleFor(user.text) + memRule + memBlock)
        items += history.takeLast(6)
        items += user
        val steps = ArrayList<LocalToolStep>()
        val gens = ArrayList<LocalGeneration>()
        var invalid = 0
        // BRAIN (2026-11-20): same discipline as the cloud loop — the
        // self-correction tracker refuses an identical re-issue of a failed
        // call, and the verify gate stops a false "done" in the final answer.
        // One verification round at most; the repeat() bound still caps the run.
        val tracker = com.mazharansari.x.agent.Brain.CorrectionTracker()
        val verifyGate = com.mazharansari.x.agent.Brain.VerifyGate { p ->
            runCatching { ctx.workspace.resolve(p) }.getOrNull()?.let { it.isFile && it.length() > 0 } ?: false
        }
        var verificationPrompted = false
        repeat(maxSteps + 1) { round ->
            val gen = generate(items)
            gens += gen
            when (val p = LocalToolUse.parse(gen.text, byName.keys)) {
                is ParsedCall.Final -> {
                    // BRAIN — verify-before-deliver: a final answer that claims a
                    // file was made/saved/fixed/downloaded with no tool evidence
                    // behind it gets one round to verify with a tool; after that
                    // the honest "could not verify" note rides along instead of
                    // a false success.
                    val decision = verifyGate.check(p.text)
                    if (decision is com.mazharansari.x.agent.Brain.Decision.NeedsVerification && !verificationPrompted) {
                        verificationPrompted = true
                        items += AssistantItem(gen.text)
                        items += UserItem(com.mazharansari.x.agent.Brain.verificationNote(decision.claims))
                    } else {
                        val finalText = if (decision is com.mazharansari.x.agent.Brain.Decision.NeedsVerification)
                            p.text + "\n\n" + com.mazharansari.x.agent.Brain.unverifiedDisclaimer(decision.claims) else p.text
                        return LocalAgentResult(finalText, steps, gens)
                    }
                }
                is ParsedCall.Invalid -> {
                    invalid++
                    if (invalid >= 2 || round == maxSteps) {
                        return LocalAgentResult(
                            "The local model could not format a tool call (${p.reason}). Try a shorter request, or switch to a cloud model for this task.",
                            steps, gens,
                        )
                    }
                    items += AssistantItem(gen.text)
                    items += UserItem("That was not a valid tool call (${p.reason}). Reply with exactly one JSON object: {\"tool\": ..., \"arguments\": {...}} or {\"answer\": ...}.")
                }
                is ParsedCall.Call -> {
                    val tool = byName[p.tool]!!
                    // BRAIN — never re-run the exact call that already failed:
                    // refuse it without executing and make the model change
                    // arguments or tool. After MAX_SELF_CORRECTIONS_PER_STEP
                    // different failed approaches on this tool, stop honestly.
                    // (No `continue` here: a repeat{} lambda is not a loop body,
                    // so the refused branch just does nothing more this round.)
                    if (tracker.isIdenticalRepeat(p.tool, p.args.toString())) {
                        tracker.noteIdenticalRepeat(p.tool, p.args.toString())
                        items += AssistantItem(gen.text)
                        if (tracker.shouldGiveUp(p.tool)) {
                            return LocalAgentResult(
                                "I could not finish this — ${p.tool} kept failing in this run. What I tried: ${tracker.triedSummary()}.",
                                steps, gens,
                            )
                        }
                        items += ToolResultItem(UUID.randomUUID().toString(), p.tool,
                            "That exact call already failed (${tracker.lastError(p.tool, p.args.toString())}). Do NOT repeat it: use different arguments or a different tool, or say what is blocking you.")
                    } else {
                    val gate = ApprovalGate.requestFor(p.tool, p.args)
                    if (gate != null && !( !gate.hard && approvals?.matches(gate.pattern) == true)) {
                        val msg = "The command was NOT run: it needs the user's approval (${gate.summary}). Ask again from the chat and approve it there."
                        steps += LocalToolStep(p.tool, p.args.toString(), msg, false)
                        return LocalAgentResult("I could not run that — it needs your approval first. ${gate.summary}", steps, gens)
                    }
                    val label = tool.label(p.args)
                    val step = Step(id = UUID.randomUUID().toString(), kind = label.kind, title = label.title, target = label.target, status = StepStatus.RUNNING, startedAt = System.currentTimeMillis())
                    sink?.step(step)
                    val out = runCatching { tool.run(p.args, ctx) }.getOrElse { ToolOutput("Tool error: ${it.message}", isError = true) }
                    sink?.step(step.copy(status = if (out.isError) StepStatus.ERROR else StepStatus.DONE, output = out.display.clip(20000), endedAt = System.currentTimeMillis(), exitCode = out.exitCode))
                    steps += LocalToolStep(p.tool, p.args.toString(), out.forModel, !out.isError)
                    // BRAIN — record the attempt (tracker + verify gate) and feed
                    // the model the real result. An empty "success" produced
                    // nothing, so the model is told to check it, not move on.
                    tracker.record(p.tool, p.args.toString(), !out.isError, out.forModel)
                    verifyGate.noteToolCall(p.tool, p.args, out)
                    val resultText = if (com.mazharansari.x.agent.Brain.isEmptySuccess(out))
                        out.forModel + "\n[System note: that step returned no usable output — check what it actually produced before moving on; an empty result is not a done step.]"
                    else out.forModel
                    items += AssistantItem(gen.text)
                    items += ToolResultItem(UUID.randomUUID().toString(), p.tool, SecretScanner.scrub(resultText))
                    }
                }
            }
        }
        return LocalAgentResult(
            "The local model used all $maxSteps tool steps without a final answer. What it did:\n" +
                steps.joinToString("\n") { "- ${it.tool}: ${it.result.take(200)}" },
            steps, gens,
        )
    }
}
