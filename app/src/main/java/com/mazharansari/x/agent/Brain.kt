package com.mazharansari.x.agent

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// BRAIN (2026-11-20) — the Hermes-level brain layer, model-independent.
//
// The same discipline for every model behind X — cloud, local, Antigravity:
// plan before acting, pick the right tool per step, check each step's result,
// self-correct a bounded number of times with a CHANGED approach, and never
// tell the user something is done until a tool has verified it.
//
// Pure Kotlin, no Android: every rule here is unit-tested (BrainTest) and the
// agent loops (Agent.run, LocalAgentRunner, the swarm worker briefs) only wire
// it in. Nothing here knows which model is running — the only model-aware rule
// is graceful degradation: a weak model gets shorter plans and one tool per
// turn, and if it cannot verify it must say so instead of guessing.
object Brain {

    // Bounded self-correction: at most this many DIFFERENT approaches per tool
    // step. The identical call is never re-run by the loop at all (the one
    // identical retry inside FaultPolicy is transient tolerance for a network
    // blip, not a model decision — see CorrectionTracker).
    const val MAX_SELF_CORRECTIONS_PER_STEP = 2

    // At most one extra verification round when the agent is about to claim
    // completion without evidence. Never a second model call per turn by
    // default — this single round only fires on a completion claim.
    const val MAX_VERIFY_ROUNDS = 1

    // The brain discipline for cloud / Antigravity paths. It rides in the
    // Agent system prompt (and through it into the Antigravity prompt, which
    // carries the system prompt — AgyEngine.promptOf), so every model gets
    // the same rules. Short on purpose: long prompts are the first thing a
    // small model drops, and the text before it in the system prompt is
    // ~1650 chars, so this block must stay under ~1300 chars to survive
    // AgyEngine.promptOf's sys.take(3000) intact — keep it that short.
    const val BRAIN_GUIDANCE = """BRAIN — how you work, whatever model you are:
- PLAN FIRST: for multi-step work, say your plan first — 2-4 short steps, each with what success looks like ("done means …"). Use plan_set when offered; otherwise state it in your first words, so the user sees it in Thinking before any tool runs.
- ONE STEP, ONE TOOL: pick the single best tool for the step and pass it the REAL output of the previous step (exact paths, contents, numbers) — never a summary. Wait for its result before the next step.
- CHECK EACH STEP: did the result produce what the plan needed? An empty result, a missing file, or a non-zero exit means the step FAILED — say so, do not move on as if it worked.
- SELF-CORRECT, BOUNDED: on failure, read the error and try ONCE differently (different arguments, or a different tool). Never repeat the exact input that already failed. After 2 changed approaches on one step, stop and tell the user plainly what failed and what you tried.
- VERIFY BEFORE YOU SAY DONE: never say something is built, fixed, downloaded, installed, or saved until a tool proved it — read the file back, re-run the check, look at the exit code or size. If you cannot verify, say "I could not verify …" and what is unchecked.
- IF STUCK: ask the user ONE short question instead of guessing or looping."""

    // The same discipline, compressed for a small on-device model (4k context,
    // one JSON object per turn). Lives in LocalToolUse.buildToolPrompt.
    const val SMALL_MODEL_GUIDANCE = """BRAIN (small model — keep it simple):
- PLAN: before the first tool call, think in 2-3 short steps: what to do first, then what, and what "done" looks like. Say the plan in one short line inside your next tool call's thinking, then do step 1 only.
- ONE TOOL PER TURN: call exactly one tool, wait for its result, then decide the next step. Never call two tools at once.
- CHECK: after each result, ask: did it work? Empty output or an error means it FAILED — try once with different arguments, never the same call twice.
- VERIFY BEFORE DONE: before your final answer says something is made, saved, fixed or downloaded, the tool result must prove it (the file was written, the command exited 0, the file reads back). If you cannot prove it, say "I could not verify it" — never guess it worked."""

    // -- Completion claims -------------------------------------------------
    // A final answer that says a concrete thing is done. Only these trigger
    // the verify gate — plain chat ("done, thanks", "sounds good") never does,
    // and neither does a claim with no artifact at all.

    enum class ClaimKind { FILE, BUILD, DOWNLOAD, FIX, OTHER }

    data class Claim(val kind: ClaimKind, val phrase: String, val path: String? = null, val artifact: String = "")

    private val ARTIFACTS = listOf(
        "pdf", "apk", "file", "files", "document", "report", "image", "picture", "photo",
        "video", "website", "web app", "webapp", "page", "app", "build", "download",
        "folder", "zip", "spreadsheet", "sheet", "code", "script", "song", "audio",
    )

    // Claim verbs, English + Roman Urdu. "done" alone is not a claim — it needs
    // an artifact nearby (checked in detectClaims).
    private val CLAIM_VERBS = Regex(
        "\\b(built|created|made|fixed|downloaded|installed|saved|written|generated|uploaded|compiled|converted|ready|complete|completed|finished|done)\\b" +
            "|\\b(ban gaya|ban gayi|ban gaye|bana diya|bana di|ho gaya|ho gayi|ho gaye|kar diya|kar di|tayyar hai|tayyar he|ready hai|download ho gaya|download ho gayi|fix ho gaya|theek ho gaya|complete ho gaya)\\b",
        RegexOption.IGNORE_CASE,
    )

    private val PATH_TOKEN = Regex("[A-Za-z0-9_\\-./]+\\.(pdf|apk|html|htm|txt|md|csv|png|jpg|jpeg|zip|docx|mp3|mp4)")

    fun detectClaims(text: String): List<Claim> {
        val out = mutableListOf<Claim>()
        for (m in CLAIM_VERBS.findAll(text)) {
            val start = maxOf(0, m.range.first - 60)
            val end = minOf(text.length, m.range.last + 61)
            val window = text.substring(start, end)
            val artifact = ARTIFACTS.firstOrNull { a -> Regex("\\b${Regex.escape(a)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(window) }
                ?: continue
            val path = PATH_TOKEN.find(window)?.value
            val verb = m.value.lowercase()
            val kind = when {
                verb.contains("download") || artifact == "download" -> ClaimKind.DOWNLOAD
                verb.contains("fix") || verb.contains("theek") || verb == "fixed" -> ClaimKind.FIX
                verb in listOf("built", "compiled") || artifact == "build" || verb.contains("ban gaya") && artifact in listOf("app", "website", "web app", "page") -> ClaimKind.BUILD
                else -> ClaimKind.FILE
            }
            // Same claim twice (verb + artifact repeated in one sentence) counts once.
            if (out.none { it.kind == kind && it.artifact == artifact }) out += Claim(kind, m.value, path, artifact)
        }
        return out
    }

    // -- Cheap per-step check ------------------------------------------------
    // Did this tool result actually produce what the plan needed? An error is
    // a failure; a "success" with nothing in it is a failure too (a tool that
    // returns an empty success has produced nothing to pass to the next step).

    fun stepProducedWhatWasNeeded(out: ToolOutput): Boolean {
        if (out.isError) return false
        if (out.exitCode != null && out.exitCode != 0) return false
        return out.forModel.isNotBlank()
    }

    fun isEmptySuccess(out: ToolOutput): Boolean = !out.isError && out.forModel.isBlank()

    // -- Verification gate ---------------------------------------------------
    // The code path that claims "done" must pass through this gate. Evidence
    // comes only from tool results recorded with noteToolCall, plus a live
    // file-exists check for file claims — never from the model's word alone.

    sealed interface Decision {
        data object Allowed : Decision
        data class NeedsVerification(val claims: List<Claim>) : Decision
    }

    class VerifyGate(private val fileExists: (String) -> Boolean = { false }) {
        private val verifiedFiles = mutableSetOf<String>()
        private val verifiedKinds = mutableSetOf<String>()
        private var verifiedCommands = 0
        private var verifiedBuildCommands = 0

        private fun norm(p: String) = p.trim().trim('`', '"', '\'').lowercase()

        fun noteToolCall(tool: String, args: JsonObject, out: ToolOutput) {
            if (out.isError) return
            when (tool) {
                "write_file" -> args.s("path")?.let { verifiedFiles += norm(it) }
                "read_file" -> args.s("path")?.let { verifiedFiles += norm(it) }
                "list_files" -> verifiedKinds += "list"
                "create_pdf" -> args.s("path")?.let { verifiedFiles += norm(it) } ?: run { verifiedKinds += "pdf" }
                "generate_image" -> verifiedKinds += "image"
                "run_command", "shell_session" -> {
                    if (out.exitCode == null || out.exitCode == 0) {
                        verifiedCommands++
                        val cmd = (args.s("command") ?: "").lowercase()
                        if (Regex("gradlew|gradle|\\bbuild\\b|assemble|npm run build|tsc |cargo build|go build").containsMatchIn(cmd)) verifiedBuildCommands++
                    }
                }
            }
        }

        fun check(answer: String): Decision {
            val claims = detectClaims(answer)
            if (claims.isEmpty()) return Decision.Allowed
            val missing = claims.filterNot { satisfied(it) }
            return if (missing.isEmpty()) Decision.Allowed else Decision.NeedsVerification(missing)
        }

        private fun satisfied(c: Claim): Boolean {
            val pathOk = c.path?.let { p -> fileExists(p) || verifiedFiles.contains(norm(p)) || verifiedFiles.any { it.endsWith("/" + norm(p).substringAfterLast('/')) } } ?: false
            return when (c.kind) {
                ClaimKind.FILE -> pathOk || verifiedFiles.isNotEmpty() || verifiedKinds.contains("pdf") || verifiedKinds.contains("image")
                ClaimKind.BUILD -> verifiedBuildCommands > 0 || verifiedFiles.isNotEmpty() || pathOk
                ClaimKind.DOWNLOAD -> pathOk || verifiedFiles.isNotEmpty() || verifiedKinds.contains("list")
                ClaimKind.FIX -> verifiedFiles.isNotEmpty() || verifiedCommands > 0 || pathOk
                ClaimKind.OTHER -> verifiedFiles.isNotEmpty() || verifiedCommands > 0
            }
        }

        fun evidenceSummary(): String =
            "verified files: ${verifiedFiles.size}, verified commands: $verifiedCommands, verified builds: $verifiedBuildCommands"
    }

    // One extra round only: tell the model to verify with a tool, naming the
    // exact claims that lack evidence. The loop must not send this twice.
    fun verificationNote(claims: List<Claim>): String =
        "(System note: before you finish, VERIFY with a tool — you claimed " +
            claims.joinToString("; ") { c -> "\"${c.phrase}\" (${c.artifact.ifBlank { "that task" }})" } +
            ". Read the file back, list the folder, or re-run the check, and only then say it is done. " +
            "If the check fails or you cannot run it, say plainly what you could not verify instead of claiming success.)"

    // Appended to the final answer when verification still failed after the
    // one round — honest, in the user's language direction is the model's job;
    // this text is English, the same language the system notes use.
    fun unverifiedDisclaimer(claims: List<Claim>): String =
        "Note: I could not verify this yet — " +
            claims.joinToString("; ") { c -> "\"${c.phrase}\" (${c.artifact.ifBlank { "that task" }})" } +
            ". Please check it before relying on it."

    // -- Self-correction tracker ---------------------------------------------
    // Records what the loop already tried, per tool. The loop refuses an
    // identical re-issue and gives up honestly after MAX_SELF_CORRECTIONS
    // DIFFERENT failed approaches on one tool — so a weak model can never
    // spin the same failing call for the whole round budget.

    class CorrectionTracker {
        private val failures = linkedMapOf<String, String>() // "tool|args" -> first error line
        private val distinctArgs = linkedMapOf<String, MutableSet<String>>() // tool -> failed args tried
        private val repeatRefusals = linkedMapOf<String, Int>() // tool -> identical re-issues refused

        fun record(tool: String, argsJson: String, ok: Boolean, error: String = "") {
            if (ok) return
            failures["$tool|$argsJson"] = error.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(160)
            distinctArgs.getOrPut(tool) { mutableSetOf() } += argsJson
        }

        fun isIdenticalRepeat(tool: String, argsJson: String): Boolean =
            failures.containsKey("$tool|$argsJson")

        fun lastError(tool: String, argsJson: String): String =
            failures["$tool|$argsJson"].orEmpty()

        // The loop refused an identical re-issue without running it. Enough
        // refusals on one tool is itself a give-up: a model that keeps
        // re-issuing the same failing call is stuck, and the round budget
        // must not be burned waiting for it to stop.
        fun noteIdenticalRepeat(tool: String, argsJson: String) {
            repeatRefusals[tool] = (repeatRefusals[tool] ?: 0) + 1
        }

        fun correctionsUsed(tool: String): Int = distinctArgs[tool]?.size ?: 0

        fun shouldGiveUp(tool: String): Boolean =
            correctionsUsed(tool) >= MAX_SELF_CORRECTIONS_PER_STEP ||
                (repeatRefusals[tool] ?: 0) >= MAX_SELF_CORRECTIONS_PER_STEP

        fun triedSummary(): String =
            if (failures.isEmpty()) "nothing tried yet"
            else failures.entries.joinToString("; ") { (k, err) ->
                val tool = k.substringBefore('|')
                "$tool failed" + if (err.isNotBlank()) " ($err)" else ""
            }
    }

    // -- Plan shape ------------------------------------------------------------
    // A plan is 2-7 short steps plus one line saying what success looks like.
    // The model forms it (plan_set, or its first words); this only validates
    // the shape so a one-word "plan" never counts as planning.

    fun isWellFormedPlan(steps: List<String>, successLine: String): Boolean =
        steps.size in 2..7 && steps.all { it.isNotBlank() } && successLine.isNotBlank()
}
