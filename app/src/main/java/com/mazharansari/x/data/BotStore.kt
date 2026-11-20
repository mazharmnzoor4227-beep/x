package com.mazharansari.x.data

import com.mazharansari.x.agent.Attempt
import com.mazharansari.x.agent.ToolCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * PHASE 1 SWARM — the Bot Store's unit: a bot is a DEFINITION bundle, not code.
 *
 * A bot is a persona + skills + tool allow-list + suggested provider, stored in the app's
 * own storage (bots.json) and run through X's OWN agent loop — no foreign code is ever
 * executed. Installing a bot only stores its definition; it never runs until the user
 * starts a chat with it from the Bots section.
 *
 * Any bot can create another bot, and the user (or the creating bot) NAMES it — "CEO" is
 * just one possible name, not a built-in role. There is no special CEO class: the
 * parent/child relationship plus the given name is what makes a bot the parent for its
 * run. The bots in the store seed are definitions written here from the PATTERNS of the
 * verified sources below (X's own Kotlin); no source code was copied from any of them.
 * Original X code.
 */
@Serializable
data class BotDefinition(
    val id: String,
    val name: String,
    val description: String,
    val persona: String,
    val skills: List<String> = emptyList(),
    /** ToolCatalog group ids this bot may use; empty = no restriction (all groups on). */
    val toolGroups: List<String> = emptyList(),
    /**
     * Preferred FREE provider — a provider NAME, e.g. "Google Gemini", "Groq", "OpenRouter",
     * "xAI Grok". It only reorders X's existing failover chain (see [preferProvider]);
     * it never pins a provider or bypasses failover. Null = Auto. Antigravity is the Build
     * engine, not an Ask provider, so it is never offered here. Never "OpenAI".
     */
    val suggestedProvider: String? = null,
    /** Where this bot's pattern comes from — shown in the store, with its licence. */
    val sourceName: String = "",
    val sourceUrl: String = "",
    val license: String = "",
    /**
     * Phase 2: the bot's logo, chosen by the user — "builtin:<id>" or
     * "file:<path>" (see [BotLogo]); "" = the bot's initial. Stored in bots.json.
     */
    val logo: String = "",
) {
    /** The system-prompt addition for a chat run as this bot (X's own agent loop). */
    fun promptBlock(): String = buildString {
        append("You are $name — $description. ")
        if (persona.isNotBlank()) append(persona.trim()).append(' ')
        if (skills.isNotEmpty()) append("Your skills: ${skills.joinToString(", ")} — load the matching skill (load_skill) before specialised work. ")
        if (toolGroups.isNotEmpty()) append("You may use only these tool groups: ${toolGroups.joinToString(", ")}. If a task needs anything else, say so instead of working around it. ")
        append("Verify before you deliver: never present a result as done until it is checked. If a check fails, say what failed and what is still unverified.")
    }
}

/** Installed + user-created bot definitions, persisted as bots.json (same pattern as SkillRepo). */
class BotDefinitionStore(private val dir: File) {
    private val index = File(dir, "bots.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val _definitions = MutableStateFlow<List<BotDefinition>>(load())
    val definitions: StateFlow<List<BotDefinition>> = _definitions

    private fun load(): List<BotDefinition> = runCatching {
        json.decodeFromString<List<BotDefinition>>(index.readText())
    }.getOrDefault(emptyList())

    fun isInstalled(id: String): Boolean = _definitions.value.any { it.id == id }

    /** Install a store bot: store its definition only. Returns false when already installed. Never runs anything. */
    fun install(def: BotDefinition): Boolean {
        if (isInstalled(def.id)) return false
        _definitions.value = _definitions.value + def
        persist()
        return true
    }

    /** The user creates a bot and names it anything — "CEO" is just the owner's example name. */
    fun create(name: String, description: String, persona: String, suggestedProvider: String?): BotDefinition {
        val def = BotDefinition(
            id = "user-" + UUID.randomUUID(),
            name = name.trim().take(40).ifBlank { "My bot" },
            description = description.trim().take(200),
            persona = persona.trim().take(2000),
            suggestedProvider = suggestedProvider?.takeIf { it.isNotBlank() },
            sourceName = "Created by you",
        )
        _definitions.value = _definitions.value + def
        persist()
        return def
    }

    /** Uninstall (store bot) or delete (user-created bot). */
    fun remove(id: String) {
        _definitions.value = _definitions.value.filterNot { it.id == id }
        persist()
    }

    /** Phase 2: change a bot's logo (a builtin id string or a "file:" path). No-op for an unknown id. */
    fun setLogo(id: String, logo: String) {
        if (_definitions.value.none { it.id == id }) return
        _definitions.value = _definitions.value.map { if (it.id == id) it.copy(logo = logo) else it }
        persist()
    }

    /** Rename a bot (profile editing). The name is capped at 40 chars, like [create]. No-op for an unknown id. */
    fun rename(id: String, name: String) {
        val clean = name.trim().take(40)
        if (clean.isBlank() || _definitions.value.none { it.id == id }) return
        _definitions.value = _definitions.value.map { if (it.id == id) it.copy(name = clean) else it }
        persist()
    }

    /**
     * Guarantee X Bot exists, first in the list. Returns the definition.
     * Refreshes only the orchestrator persona/description on later launches —
     * the user's own name and logo choices are never overwritten.
     */
    fun ensureXBot(): BotDefinition {
        val fresh = xBotDefinition()
        val existing = _definitions.value.firstOrNull { it.id == X_BOT_ID }
        if (existing == null) {
            _definitions.value = listOf(fresh) + _definitions.value
            persist()
            return fresh
        }
        if (existing.persona != fresh.persona || existing.description != fresh.description) {
            _definitions.value = _definitions.value.map {
                if (it.id == X_BOT_ID) it.copy(persona = fresh.persona, description = fresh.description) else it
            }
            persist()
        }
        return _definitions.value.first { it.id == X_BOT_ID }
    }

    private fun persist() {
        runCatching { dir.mkdirs(); index.writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(BotDefinition.serializer()), _definitions.value)) }
    }
}

/**
 * A bot's tool allow-list as the [ToolCatalog] group switches the agent loop already reads.
 * An empty allow-list means unrestricted (every group on) — that is the default for a
 * user-created bot, which the user can narrow by editing its definition later.
 */
fun switchesForBot(def: BotDefinition): Map<String, Boolean> =
    ToolCatalog.groups.associate { it.id to (def.toolGroups.isEmpty() || it.id in def.toolGroups) }

/**
 * Navigation restructure (2026-11-20): X Bot is the user's ONE bot — the
 * Bots section shows exactly this definition and nothing else. The 50
 * specialists in [BotCatalog.seeds] are not bots the user chats with: they
 * are the roster X Bot spawns from (see [com.mazharansari.x.agent.XBotSpawn]).
 * [ensureXBot] guarantees the definition exists (seeded on first launch,
 * kept first in the list); it refreshes only the orchestrator persona and
 * description, never the user's own name or logo choices.
 */
const val X_BOT_ID = "x-bot"

fun xBotDefinition(): BotDefinition = BotDefinition(
    id = X_BOT_ID,
    name = "X Bot",
    description = "Your own main bot. Tell it anything — it does the job itself, or spawns specialist sub-bots under itself, collects all their results, and answers you itself. You only ever talk to X Bot.",
    persona = "You are X Bot, the user's own main bot — the ONLY bot the user ever talks to. " +
        "Do whatever the user tells you. When a job has several independent parts, fan it out with " +
        "subagent_swarm: pick a specialist sub-bot from your roster for each part by name " +
        "(Researcher, Coder, Tester, Reviewer, GitHub Coder, Video Editor, PDF Reader, and the rest — " +
        "50 specialists, each an expert in its own work), give each one a complete brief, and let " +
        "them work in parallel. Then collect ALL of their results yourself, verify each one before " +
        "you use it (sources cited, files read back, test exit codes checked), and send everything " +
        "to the user FROM YOURSELF, in one answer — the user never talks to a sub-bot, and a sub-bot " +
        "never replies to the user directly. Anything still unverified is marked 'Needs check', never " +
        "hidden and never claimed as done. Never send the user to another bot, and never say a job " +
        "is done until its results are checked.",
    skills = listOf("agent-building", "deep-research", "token-efficiency", "delivery"),
    sourceName = "X built-in",
    license = "X",
)

/**
 * Soft provider preference: attempts from the suggested provider go first; the rest keep
 * their ranked order, and the existing failover still applies if the preferred one fails.
 * The free-provider set the owner uses is Gemini, Grok, OpenRouter (Ask) + Antigravity
 * (Build only); never OpenAI.
 */
fun preferProvider(attempts: List<Attempt>, suggestedProvider: String?): List<Attempt> {
    if (suggestedProvider.isNullOrBlank()) return attempts
    return attempts.sortedByDescending { it.provider.equals(suggestedProvider, ignoreCase = true) }
}
