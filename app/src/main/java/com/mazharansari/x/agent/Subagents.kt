package com.mazharansari.x.agent

import com.mazharansari.x.data.StepKind
import com.mazharansari.x.llm.ToolSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject

/**
 * P1-4 SUBAGENT ORCHESTRATION — the agent fans out bounded sub-tasks to parallel worker runs,
 * then merges the results. Modelled on the Hermes maintainer-orchestrator pattern (bounded
 * workers, serialized public actions — here: workers only read the web, they never act):
 *
 *   BOUNDED   at most [SubagentLimits.MAX_WORKERS] workers per fan-out; each worker gets its
 *             own step budget ([SubagentLimits.MAX_STEPS_PER_WORKER]) and its own tool
 *             allow-list (per role — see [SubagentLimits.toolsFor]), so a worker can never
 *             wander off into unrelated tools;
 *   ISOLATED  a worker that throws is recorded as a failure and the parent run continues
 *             with the surviving workers' results — a worker failure never kills the run
 *             (a user Stop is still a cancellation and propagates, like everywhere else);
 *   MERGED    [SubagentOrchestrator.merge] dedupes by URL first, then by fact text, so two
 *             workers finding the same page or the same claim do not duplicate work in the
 *             merged result; every surviving fact keeps its worker attribution.
 *
 * Swarm (Phase 1): workers now have ROLES (researcher / coder / tester / reviewer), each with
 * its own strict allow-list, and a worker that cannot finish alone may ask for sub-workers
 * of its own ([WorkerResult.spawn]) — recursion is capped at depth [SubagentLimits.MAX_DEPTH]
 * and at [SubagentLimits.MAX_WORKERS_PER_RUN] workers per parent run, so it can never run
 * away. Only the parent agent merges results and performs public actions; workers never act
 * publicly and never ask the user for approvals.
 *
 * Pure merge/dedupe logic lives in [SubagentOrchestrator] so it is unit-testable without a
 * model; the [SubagentResearchTool] below is the one wired worker (3 parallel searchers).
 */
object SubagentLimits {
    /** Hard cap on parallel workers in one fan-out. */
    const val MAX_WORKERS = 4
    /** Max tool steps one worker may take (searches + page reads). */
    const val MAX_STEPS_PER_WORKER = 8
    /** Max facts one worker may contribute to the merged result. */
    const val MAX_FACTS_PER_WORKER = 6
    /** Max spawn depth: 0 = spawned by the parent, 1 = spawned by a worker, 2 = deepest. */
    const val MAX_DEPTH = 2
    /** Max workers in total per parent run, counting every depth (the recursion can never exceed this). */
    const val MAX_WORKERS_PER_RUN = 6
    /** Max sub-workers one worker may ask for in a single turn. */
    const val MAX_SPAWN_PER_WORKER = 2
    /** What a research worker is allowed to use: web only, never shell/files/commands. */
    val RESEARCH_TOOLS: Set<String> = setOf("web_search", "browse_url")
    /** What a coding worker is allowed to use: workspace files + shell, never the web or GitHub. */
    val CODER_TOOLS: Set<String> = setOf("read_file", "write_file", "run_command", "list_files")
    /** What a testing worker is allowed to use: shell test commands + reading files, never writes or the web. */
    val TESTER_TOOLS: Set<String> = setOf("run_command", "read_file")
    /** What a reviewing worker is allowed to use: reading files only — it never changes anything. */
    val REVIEWER_TOOLS: Set<String> = setOf("read_file")

    /** The strict tool allow-list for a role. A worker can never use a tool outside its role. */
    fun toolsFor(role: WorkerRole): Set<String> = when (role) {
        WorkerRole.RESEARCHER -> RESEARCH_TOOLS
        WorkerRole.CODER -> CODER_TOOLS
        WorkerRole.TESTER -> TESTER_TOOLS
        WorkerRole.REVIEWER -> REVIEWER_TOOLS
    }

    /** Throws when a worker tries a tool outside its role — the allow-list IS the enforcement. */
    fun checkAllowed(role: WorkerRole, tool: String) {
        require(tool in toolsFor(role)) {
            "Worker role $role may not use tool $tool (allowed: ${toolsFor(role).sorted().joinToString()})"
        }
    }
}

/** The job a worker does. Each role owns one kind of work and one strict tool allow-list. */
enum class WorkerRole(val label: String, val modelHint: String) {
    RESEARCHER("Researcher", "fast"),
    CODER("Coder", "strong"),
    TESTER("Tester", "fast"),
    REVIEWER("Reviewer", "strong");

    companion object {
        /** Parses a role name from model output; null when it is not a known role (caller keeps the full line as the brief). */
        fun fromName(name: String): WorkerRole? =
            entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
    }
}

/** Live status of one worker, streamed to the chat as its own card. */
enum class WorkerStatus { RUNNING, DONE, FAILED }

/** One live update for a worker card: role, task, status and step count. */
data class WorkerUpdate(
    /** Unique across fan-outs in this run, so two swarms never overwrite each other's cards. */
    val updateId: String,
    val workerId: String,
    val role: WorkerRole,
    val task: String,
    val status: WorkerStatus,
    val stepsUsed: Int = 0,
    val summary: String = "",
    /** The parent run's conversation id — links the worker's card to its parent bot. */
    val parentId: String? = null,
    /** Task id of the worker that spawned this one (null = the parent bot spawned it). */
    val parentWorkerId: String? = null,
    val depth: Int = 0,
    /** The roster specialist this worker runs as (a [com.mazharansari.x.data.BotCatalog] seed id, "" = plain role worker). */
    val subBotId: String = "",
    /** That specialist's display name ("" = the role label). */
    val subBotName: String = "",
)

/** One bounded sub-task handed to a worker. */
data class WorkerTask(
    val id: String,
    val topic: String,
    /** The search angle this worker owns (distinct per worker, so work is not duplicated). */
    val angle: String,
    val role: WorkerRole = WorkerRole.RESEARCHER,
    /** The concrete brief this worker owns (what to do, in full). Defaults to the angle. */
    val brief: String = "",
    /** Recursion depth: 0 = spawned by the parent, 1 = spawned by a worker, 2 = deepest. */
    val depth: Int = 0,
    val parentId: String? = null,
    val allowedTools: Set<String> = SubagentLimits.toolsFor(role),
    val maxSteps: Int = SubagentLimits.MAX_STEPS_PER_WORKER,
    val maxFacts: Int = SubagentLimits.MAX_FACTS_PER_WORKER,
    /** The roster specialist this worker runs as (a [com.mazharansari.x.data.BotCatalog] seed id, "" = plain role worker). */
    val subBotId: String = "",
    /** That specialist's display name ("" = the role label). */
    val subBotName: String = "",
)

/** One fact a worker found, attributed to the worker that found it. */
data class WorkerFact(val text: String, val sourceUrl: String, val workerId: String)

/** What one worker returned — or why it did not. `failed` is set instead of throwing. */
data class WorkerResult(
    val taskId: String,
    val workerId: String,
    val facts: List<WorkerFact> = emptyList(),
    val failed: String? = null,
    val stepsUsed: Int = 0,
    /** The angle this worker owned, so a failure can say what was not covered. */
    val angle: String = "",
    val role: WorkerRole = WorkerRole.RESEARCHER,
    val depth: Int = 0,
    /** Sub-tasks this worker asked for — run by the orchestrator when the depth and global caps allow. */
    val spawn: List<WorkerTask> = emptyList(),
    /** Results of this worker's own sub-workers (attribution only — their facts merge flat). */
    val subResults: List<WorkerResult> = emptyList(),
    /** The roster specialist this worker ran as ("" = plain role worker). */
    val subBotId: String = "",
    /** That specialist's display name. */
    val subBotName: String = "",
)

data class WorkerFailure(val workerId: String, val angle: String, val reason: String)

/** The merged outcome of a fan-out: deduped facts plus the failures that were isolated. */
data class FanOutResult(
    val facts: List<WorkerFact>,
    val failures: List<WorkerFailure>,
    val workersRun: Int,
    val workersFailed: Int,
    /** The raw per-worker results (roles included) — for verify-before-deliver. */
    val results: List<WorkerResult> = emptyList(),
)

/** Shared budget for one parent run: counts every worker at every depth, so recursion can never exceed [SubagentLimits.MAX_WORKERS_PER_RUN]. */
class WorkerBudget(val max: Int = SubagentLimits.MAX_WORKERS_PER_RUN) {
    private val used = java.util.concurrent.atomic.AtomicInteger(0)

    /** Reserve up to [n] slots; returns how many were actually granted (0 when the cap is hit). */
    fun reserve(n: Int): Int {
        while (true) {
            val cur = used.get()
            if (cur >= max) return 0
            val grant = minOf(n, max - cur)
            if (used.compareAndSet(cur, cur + grant)) return grant
        }
    }

    fun used(): Int = used.get()
}

/**
 * X BOT'S SPAWN ROUTER (navigation restructure, 2026-11-20) — how X Bot
 * turns a big job into sub-bots.
 *
 * The user only ever talks to X Bot. When a job has several parts, X Bot
 * fans it out through `subagent_swarm`; this object picks, for each part,
 * the ONE roster specialist ([com.mazharansari.x.data.BotCatalog]) whose
 * work that part is, by keyword rules over the brief (most specific first).
 * A brief that matches no keyword falls back to its engine role (a CODER
 * line runs as Coder) — the swarm never refuses work for lack of a name.
 * The tag rides on
 * [WorkerTask.subBotId]/[subBotName] into the worker card and the Bots
 * section, so the user sees which specialist did which part, under X Bot.
 *
 * [aggregate] is the other half of the contract: X Bot collects ALL worker
 * results and sends them to the user FROM ITSELF, grouped by sub-bot, with
 * the failures that were not covered and the verification mark up front.
 * Pure — unit-tested without a model.
 */
object XBotSpawn {
    /** Keyword rule → roster seed id. First match wins; order is specific-first. */
    private val rules: List<Pair<Regex, String>> = listOf(
        Regex("playwright|browser test|e2e test|end-to-end", RegexOption.IGNORE_CASE) to "store-playwright",
        Regex("clone.*website|website.*clone|clone.*site|mirror.*website|recreate.*website|remix.*site|make.*site like|website like|site like\\s", RegexOption.IGNORE_CASE) to "store-designagent",
        Regex("review.*pull request|pull request.*review", RegexOption.IGNORE_CASE) to "store-prreviewer",
        Regex("github|clone.*repo|\\brepo\\b|commit|push to", RegexOption.IGNORE_CASE) to "store-githubber",
        Regex("apk.*build|build.*apk|android.*build|sign.*apk|release.*apk", RegexOption.IGNORE_CASE) to "store-apkbuilder",
        Regex("trim|ffmpeg|reel|9:16|caption.*video|video.*edit", RegexOption.IGNORE_CASE) to "store-videoeditor",
        Regex("generate.*image|poster|product shot|make.*image|logo.*image", RegexOption.IGNORE_CASE) to "store-imagemaker",
        Regex("shopify", RegexOption.IGNORE_CASE) to "store-shopify",
        Regex("\\bgame\\b|arcade|snake\\.io", RegexOption.IGNORE_CASE) to "store-game",
        Regex("banner", RegexOption.IGNORE_CASE) to "store-banner",
        Regex("\\bseo\\b|search engine", RegexOption.IGNORE_CASE) to "store-seo",
        Regex("audit.*website|website.*audit", RegexOption.IGNORE_CASE) to "store-webauditor",
        Regex("reverse|decompile|inspect.*apk|apk.*inspect", RegexOption.IGNORE_CASE) to "store-reverse",
        Regex("transcrib|audio.*text|speech.*text", RegexOption.IGNORE_CASE) to "store-transcriber",
        Regex("gmail|send.*email|email.*draft|draft.*email", RegexOption.IGNORE_CASE) to "store-emailer",
        Regex("price|stock.*check|availability|buy.*pakistan|pakistan.*buy", RegexOption.IGNORE_CASE) to "store-pricechecker",
        Regex("fact.check|verify.*claim|check.*claim", RegexOption.IGNORE_CASE) to "store-factchecker",
        Regex("news|headlines|latest.*developments", RegexOption.IGNORE_CASE) to "store-newsreader",
        Regex("\\bpdf\\b", RegexOption.IGNORE_CASE) to "store-pdfreader",
        Regex("deliver.*link|deliver.*apk|delivery check", RegexOption.IGNORE_CASE) to "store-delivery",
        Regex("digest", RegexOption.IGNORE_CASE) to "store-digest",
        Regex("summar", RegexOption.IGNORE_CASE) to "store-summarizer",
        Regex("take.*note|write.*note", RegexOption.IGNORE_CASE) to "store-notetaker",
        Regex("remember|memory|what did i", RegexOption.IGNORE_CASE) to "store-memorykeeper",
        Regex("idea|\\bgoal\\b", RegexOption.IGNORE_CASE) to "store-ideacatcher",
        Regex("shop|clawora|whitening|tmf|store.*price|listing", RegexOption.IGNORE_CASE) to "store-shopwatcher",
        Regex("teach|guided setup|how do i|step by step", RegexOption.IGNORE_CASE) to "store-teacher",
        Regex("message|draft.*reply|reply.*draft", RegexOption.IGNORE_CASE) to "store-messenger",
        Regex("voice|call|dictat|read aloud", RegexOption.IGNORE_CASE) to "store-voice",
        Regex("token", RegexOption.IGNORE_CASE) to "store-tokensaver",
        Regex("skill", RegexOption.IGNORE_CASE) to "store-skillsmith",
        Regex("data|\\bcsv\\b|spreadsheet|analys", RegexOption.IGNORE_CASE) to "store-dataanalyst",
        Regex("script|python|node\\.js|\\bnode\\b", RegexOption.IGNORE_CASE) to "store-scriptwriter",
        Regex("terminal|shell|run.*command|command line", RegexOption.IGNORE_CASE) to "store-terminal",
        Regex("browser|click|scroll.*page|screenshot", RegexOption.IGNORE_CASE) to "store-computer",
        Regex("document|write.*report|\\bdoc\\b", RegexOption.IGNORE_CASE) to "store-docwriter",
        Regex("file|\\bzip\\b|upload", RegexOption.IGNORE_CASE) to "store-fileclerk",
        Regex("research|investigat|citation", RegexOption.IGNORE_CASE) to "store-researcher",
        Regex("test|failing", RegexOption.IGNORE_CASE) to "store-tester",
        Regex("review.*code|code.*review", RegexOption.IGNORE_CASE) to "store-reviewer",
        Regex("plan|break.*down|steps", RegexOption.IGNORE_CASE) to "store-planner",
        Regex("explor|where.*code|find.*file|map.*repo", RegexOption.IGNORE_CASE) to "store-explorer",
        Regex("antigravity|build.*app|full app", RegexOption.IGNORE_CASE) to "store-builder",
        Regex("schedul|daily|weekly|remind", RegexOption.IGNORE_CASE) to "store-scheduler",
        Regex("approv|quota|safety|guard", RegexOption.IGNORE_CASE) to "store-guard",
        Regex("money|budget|recommend.*service", RegexOption.IGNORE_CASE) to "store-money",
        Regex("code|implement|write.*function|fix.*bug", RegexOption.IGNORE_CASE) to "store-coder",
        Regex("learn", RegexOption.IGNORE_CASE) to "store-learner",
    )

    /** The roster specialist for this brief, or null for a plain role worker. */
    fun pickSubBot(brief: String): com.mazharansari.x.data.BotDefinition? {
        val text = brief.trim()
        if (text.isEmpty()) return null
        val id = rules.firstOrNull { (re, _) -> re.containsMatchIn(text) }?.second ?: return null
        return com.mazharansari.x.data.BotCatalog.byId(id)
    }

    /** Tag every task with its roster specialist (no match = plain role worker). */
    fun tagTasks(tasks: List<WorkerTask>): List<WorkerTask> = tasks.map { t ->
        // Keyword first: the brief names the specialist. Otherwise fall back
        // to the worker's engine role (a CODER line runs as Coder even when
        // its brief has no keyword in it) — never an untagged worker.
        val def = pickSubBot(t.brief.ifBlank { t.angle })
            ?: when (t.role) {
                WorkerRole.CODER -> com.mazharansari.x.data.BotCatalog.byId("store-coder")
                WorkerRole.TESTER -> com.mazharansari.x.data.BotCatalog.byId("store-tester")
                WorkerRole.REVIEWER -> com.mazharansari.x.data.BotCatalog.byId("store-reviewer")
                WorkerRole.RESEARCHER -> com.mazharansari.x.data.BotCatalog.byId("store-researcher")
            }
        if (def != null) t.copy(subBotId = def.id, subBotName = def.name) else t
    }

    /**
     * X Bot's collected answer: every sub-bot's results, grouped by sub-bot
     * (roster name + role), then the parts no worker covered. [mark] is the
     * verification mark ("Verified" / "Needs check") from [SwarmVerify].
     * [notes] maps a fact's text to a soft caveat (e.g. "(single source)").
     */
    fun aggregate(task: String, result: FanOutResult, mark: String, notes: Map<String, String> = emptyMap()): String {
        val byWorker = result.results.groupBy { it.workerId }
        return buildString {
            appendLine("Swarm: $task (${result.facts.size} results from ${result.workersRun - result.workersFailed} of ${result.workersRun} workers · $mark)")
            appendLine()
            for ((workerId, rs) in byWorker) {
                val first = rs.first()
                val facts = result.facts.filter { it.workerId == workerId }
                if (facts.isEmpty() && first.failed == null) continue
                // Name the worker as its roster specialist when tagged, else its role.
                val who = if (first.subBotName.isNotBlank()) "${first.subBotName} (${first.role.label})" else "${first.role.label} ($workerId)"
                appendLine("— $who —")
                if (first.failed != null) {
                    appendLine("Failed: ${first.failed}")
                }
                facts.forEachIndexed { i, f ->
                    appendLine("${i + 1}. ${f.text}${notes[f.text]?.let { " $it" } ?: ""}")
                    if (f.sourceUrl.isNotBlank()) appendLine("   ${f.sourceUrl}")
                }
                appendLine()
            }
            if (result.failures.isNotEmpty()) {
                appendLine("Parts not covered: " +
                    result.failures.joinToString("; ") { "${it.workerId} (\"${it.angle}\": ${it.reason})" })
            }
        }.trimEnd()
    }
}

object SubagentOrchestrator {

    /** Counts fan-outs in this process, so worker cards from two swarms never share an id. */
    private val runCounter = java.util.concurrent.atomic.AtomicLong(0)

    /** Split a topic into at most [SubagentLimits.MAX_WORKERS] distinct worker tasks. */
    fun splitAngles(topic: String, angles: List<String>): List<WorkerTask> {
        val clean = angles.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            .take(SubagentLimits.MAX_WORKERS)
        val owned = if (clean.isEmpty()) listOf(topic.trim()) else clean
        return XBotSpawn.tagTasks(owned.mapIndexed { i, angle ->
            WorkerTask(id = "w${i + 1}", topic = topic, angle = angle)
        })
    }

    /**
     * Parse model-planned worker specs: one per line, `ROLE: brief` (role optional — a line
     * without a known role name is a researcher brief in full). Caps at
     * [SubagentLimits.MAX_WORKERS]; empty or unusable input returns empty (the caller falls
     * back to researcher angles). Pure — unit-testable without a model. Each task is
     * tagged with the roster specialist ([XBotSpawn]) whose work the brief is.
     */
    fun parseWorkerSpecs(text: String, topic: String): List<WorkerTask> {
        val lines = text.lines().map { it.trim() }
            .map { it.trimStart('-', '•', '*', ' ').replace(Regex("^\\d+[.)]\\s*"), "").trim().trim('"') }
            .filter { it.length >= 3 }.distinct().take(SubagentLimits.MAX_WORKERS)
        return XBotSpawn.tagTasks(lines.mapIndexed { i, line ->
            val head = line.substringBefore(':', "").trim()
            val role = if (':' in line && head.length <= 12) WorkerRole.fromName(head) else null
            val brief = if (role != null) line.substringAfter(':').trim().ifBlank { line } else line
            WorkerTask(id = "w${i + 1}", topic = topic, angle = brief.take(120), role = role ?: WorkerRole.RESEARCHER, brief = brief)
        })
    }

    /**
     * Run every worker in parallel and merge. A worker that throws (network, timeout, bad
     * result) is recorded in [FanOutResult.failures] and the run continues with the survivors.
     * Only a user Stop ([CancellationException]) propagates — it must cancel the parent too.
     * A worker that asked for sub-workers ([WorkerResult.spawn]) gets them at depth + 1 while
     * the depth and global caps allow; their facts merge flat, with attribution kept.
     */
    suspend fun fanOut(
        tasks: List<WorkerTask>,
        runWorker: suspend (WorkerTask) -> WorkerResult,
    ): FanOutResult = fanOut(tasks, runWorker, onWorker = {})

    suspend fun fanOut(
        tasks: List<WorkerTask>,
        runWorker: suspend (WorkerTask) -> WorkerResult,
        onWorker: (WorkerUpdate) -> Unit,
        budget: WorkerBudget = WorkerBudget(),
        parentId: String? = null,
    ): FanOutResult {
        val (results, dropped) = expand(tasks, runWorker, onWorker, budget, parentId)
        val merged = merge(results)
        // Tasks the global cap refused are failures the user can see (their angle was not
        // covered) — but they never ran, so they do not count in workersRun.
        return merged.copy(
            failures = merged.failures + dropped.map {
                WorkerFailure(it.id, it.angle, "not started: swarm worker cap (${SubagentLimits.MAX_WORKERS_PER_RUN}) reached")
            },
        )
    }

    /**
     * Runs one batch and recursively expands any spawn requests, flattened for [merge].
     * Returns the results plus the tasks the global cap refused (never started).
     */
    private suspend fun expand(
        tasks: List<WorkerTask>,
        runWorker: suspend (WorkerTask) -> WorkerResult,
        onWorker: (WorkerUpdate) -> Unit,
        budget: WorkerBudget,
        parentId: String? = null,
    ): Pair<List<WorkerResult>, List<WorkerTask>> = coroutineScope {
        val runSeq = runCounter.incrementAndGet()
        val granted = budget.reserve(tasks.size)
        val started = tasks.take(granted)
        val dropped = tasks.drop(granted)
        val results = started.map { task ->
            async {
                val updateId = "r$runSeq-${task.id}"
                onWorker(WorkerUpdate(updateId, task.id, task.role, task.brief.ifBlank { task.angle }, WorkerStatus.RUNNING, parentId = parentId, parentWorkerId = task.parentId, depth = task.depth, subBotId = task.subBotId, subBotName = task.subBotName))
                val raw = try {
                    runWorker(task)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    WorkerResult(task.id, task.id, failed = e.message ?: e.javaClass.simpleName, angle = task.angle, role = task.role, depth = task.depth)
                }
                // Tag the result with its roster specialist, so the merge and
                // the Bots section can name the sub-bot that did this part.
                val r = if (raw.subBotId.isBlank() && task.subBotId.isNotBlank()) raw.copy(subBotId = task.subBotId, subBotName = task.subBotName) else raw
                onWorker(WorkerUpdate(updateId, task.id, task.role, task.brief.ifBlank { task.angle },
                    if (r.failed != null) WorkerStatus.FAILED else WorkerStatus.DONE,
                    r.stepsUsed, r.failed ?: "${r.facts.size} facts", parentId = parentId, parentWorkerId = task.parentId, depth = task.depth, subBotId = task.subBotId, subBotName = task.subBotName))
                r
            }
        }.map { it.await() }
        val out = results.toMutableList()
        val allDropped = dropped.toMutableList()
        // Recursive spawn: a worker that could not finish alone gets sub-workers at depth + 1.
        // The depth cap stops the recursion and the shared budget stops the count — never unbounded.
        for (r in results) {
            if (r.spawn.isNotEmpty() && r.depth < SubagentLimits.MAX_DEPTH) {
                val kids = r.spawn.map { it.copy(depth = r.depth + 1, parentId = r.taskId) }
                val (childResults, childDropped) = expand(kids, runWorker, onWorker, budget, parentId)
                val idx = out.indexOfFirst { it.taskId == r.taskId }
                if (idx >= 0) out[idx] = r.copy(subResults = childResults)
                out += childResults
                allDropped += childDropped
            }
        }
        out to allDropped
    }

    /** Merge worker results: dedupe by URL first, then by fact text; first worker wins. */
    fun merge(results: List<WorkerResult>): FanOutResult {
        val seenUrls = HashSet<String>()
        val seenTexts = HashSet<String>()
        val facts = mutableListOf<WorkerFact>()
        val failures = mutableListOf<WorkerFailure>()
        var failed = 0
        for (r in results) {
            if (r.failed != null) {
                failed++
                failures += WorkerFailure(r.workerId, r.angle, r.failed)
                continue
            }
            for (f in r.facts) {
                val urlKey = normaliseUrl(f.sourceUrl)
                val textKey = normaliseText(f.text)
                if (urlKey.isNotBlank() && urlKey in seenUrls) continue
                if (textKey in seenTexts) continue
                if (urlKey.isNotBlank()) seenUrls += urlKey
                seenTexts += textKey
                facts += f
            }
        }
        return FanOutResult(facts, failures, workersRun = results.size, workersFailed = failed, results = results)
    }

    /** URL dedupe key: scheme/host/path, lowercased, trailing slash and tracking params dropped. */
    fun normaliseUrl(url: String): String {
        val u = url.trim().lowercase()
            .removePrefix("https://").removePrefix("http://").removePrefix("www.")
        // Strip the query/fragment FIRST: "example.com/a/?utm=x" must lose its slash too,
        // or it never matches "example.com/a" (trimEnd before substringBefore kept the "/").
        return u.substringBefore('?').substringBefore('#').trimEnd('/')
    }

    /** Fact-text dedupe key: case/whitespace/punctuation-insensitive, capped so long texts compare cheaply. */
    fun normaliseText(text: String): String =
        text.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim().take(400)
}

/**
 * P1-4 tool: `subagent_research` — a research task fans out to 3 parallel fetchers (one search
 * angle each), then merges without duplicating work. The model may plan the angles, or the
 * fallback angles are used when no model is wired into this surface. A worker that finds
 * nothing (search unreachable) is reported as a failure of that worker only — the merged
 * result still returns the surviving workers' facts.
 */
class SubagentResearchTool : Tool {
    override val spec = ToolSpec(
        "subagent_research",
        "Research a topic with 3 parallel workers instead of one search: each worker searches a different angle at the same time, then the results are merged with duplicates removed and every fact attributed to the worker that found it. A worker that fails never stops the others. Use it for comparisons and fact-heavy questions where several angles matter. Returns merged facts with source URLs and a note about any worker that failed.",
        schema { str("topic", "The research topic or question, in full") },
    )

    override fun label(args: JsonObject) = StepLabel(StepKind.SEARCH, "Researching with 3 workers", args.s("topic").orEmpty().take(70))

    private val searchTool = WebSearchTool()

    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val topic = args.s("topic")?.trim().orEmpty()
        if (topic.isBlank()) return ToolOutput("Give a research topic or question.", isError = true)
        val ask = ctx.ask
        var angles: List<String> = emptyList()
        if (ask != null) {
            val planPrompt = """
                Break this research topic into exactly 3 different search angles that together cover it well (core facts, latest developments, comparisons or criticism).
                Topic: "$topic"
                Output ONLY the 3 search queries, one per line — no numbering, no bullets, no explanations.
            """.trimIndent()
            angles = runCatching { ask(planPrompt) }.getOrNull().orEmpty()
                .lines().map { it.trim().trimStart('-', '•', '*', ' ').replace(Regex("^\\d+[.)]\\s*"), "").trim().trim('"') }
                .filter { it.length in 3..200 }.distinct()
        }
        if (angles.isEmpty()) angles = listOf(topic, "$topic latest news", "$topic comparison alternatives")
        val tasks = SubagentOrchestrator.splitAngles(topic, angles)
        ctx.progress("Researching with ${tasks.size} parallel workers…")
        val result = SubagentOrchestrator.fanOut(tasks) { task ->
            runWorker(task)
        }
        if (result.facts.isEmpty()) {
            return ToolOutput(
                "Subagent research on \"$topic\" ran ${result.workersRun} parallel workers but none returned a usable fact" +
                    if (result.failures.isNotEmpty()) " (${result.failures.joinToString("; ") { "${it.workerId}: ${it.reason}" }})." else ".",
                doneTitle = "Research found nothing",
            )
        }
        val body = buildString {
            appendLine("Subagent research: $topic (${result.facts.size} facts from ${result.workersRun - result.workersFailed} of ${result.workersRun} workers)")
            appendLine()
            result.facts.forEachIndexed { i, f ->
                appendLine("${i + 1}. [${f.workerId}] ${f.text}")
                appendLine("   ${f.sourceUrl}")
            }
            if (result.failures.isNotEmpty()) {
                appendLine()
                appendLine("Workers that failed (their angles were not covered): " +
                    result.failures.joinToString("; ") { "${it.workerId} (\"${it.angle}\": ${it.reason})" })
            }
        }
        return ToolOutput(body, display = body.clip(2500), doneTitle = "Researched with ${result.workersRun} workers")
    }

    /** One worker: one bounded search on its own angle, capped at its own fact budget. */
    private suspend fun runWorker(task: WorkerTask): WorkerResult {
        var steps = 0
        val hits = try {
            steps++
            searchTool.hits(task.angle, task.maxFacts)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return WorkerResult(task.id, task.id, failed = e.message ?: "search failed", stepsUsed = steps, angle = task.angle)
        }
        if (hits.isEmpty()) return WorkerResult(task.id, task.id, failed = "no results for angle \"${task.angle}\"", stepsUsed = steps, angle = task.angle)
        val facts = hits.take(task.maxFacts).map { h ->
            WorkerFact(text = "${h.title} — ${h.snippet}".trim().take(600), sourceUrl = h.url, workerId = task.id)
        }
        return WorkerResult(task.id, task.id, facts = facts, stepsUsed = steps, angle = task.angle)
    }
}
