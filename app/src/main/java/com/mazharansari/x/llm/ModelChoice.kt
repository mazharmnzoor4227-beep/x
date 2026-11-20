package com.mazharansari.x.llm

import com.mazharansari.x.antigravity.AgyAccount
import com.mazharansari.x.antigravity.AgyFailover
import com.mazharansari.x.data.Connector
import com.mazharansari.x.data.MyServer

/**
 * Model chooser + auto-shift (2026-11-20, user order): the chat input's model
 * selector lists EVERY model the user can actually use — every model on every
 * connected key, every connected Antigravity account, the local model, the
 * server, Free (slow) — and the pick sticks until the user changes it. When
 * the picked model/account hits its limit, the run shifts to the next
 * available model/account and says so in chat instead of stopping.
 *
 * Pure Kotlin (no Android): the chooser list, the pick, the shift order and
 * the exhaustion test are all unit-testable without a device. The Android
 * wiring lives in ChatViewModel (settings.pickedModel), the popover lives in
 * ui/chat/Sheets.kt, and the per-attempt failover loop lives in Agent.
 */
object ModelChoice {

    /**
     * LFM3-1.7B — the default local model (2026-11-20, user decision: "LFM3-1.7B
     * default kar do"). The id is LocalModel.LFM3_1_7B.id; the label is what the
     * composer chip / header subtitle show when it is the effective default.
     * Kept here (not imported from local.LocalModel) so this pure seam stays
     * free of the Android local-model store.
     */
    const val DEFAULT_LOCAL_MODEL_ID = "lfm3-1.7b"
    const val DEFAULT_LOCAL_MODEL_LABEL = "LFM3-1.7B"

    /** True for ALLaM ids ("allam-2-7b", "sdaia/allam-2-7b"). */
    fun isAllamModel(modelId: String): Boolean = "allam" in modelId.lowercase()

    /**
     * Allam demotion (2026-11-20): ALLaM is Arabic-first and must NEVER again be
     * the model that answers by default. The default ranking moves every allam
     * pair to the very end — below LFM3 and below every other ranked cloud
     * model — but keeps it as the last-resort failover, so a phone whose only
     * key only has allam still gets an answer instead of nothing. Stable: the
     * relative order of everything else is unchanged. This is the DEFAULT
     * ranking only: an explicit chooser pick reorders its model first in
     * ChatViewModel.attempts, and the Arabic-first pin
     * (ScriptHint.arabicFirstPin, OpenAiClient) still applies when allam runs.
     */
    fun <T> demoteAllam(list: List<T>, model: (T) -> String): List<T> {
        if (list.none { isAllamModel(model(it)) }) return list
        return list.filterNot { isAllamModel(model(it)) } + list.filter { isAllamModel(model(it)) }
    }

    /** One row of the chooser. [id] is what is persisted as settings.pickedModel. */
    data class ChooserEntry(
        /** "api:<connectorId>:<model>" | "local:<model>" | "server:<model>" | "free" | "agy:<email>". */
        val id: String,
        /** Section header: provider name, "Antigravity", "On this phone", "Free". */
        val group: String,
        /** Model id, or the Antigravity account's label/email. */
        val title: String,
        /** Key hint, account email, host — the grey line under the title. */
        val subtitle: String,
        /** How Agent.streamOnce labels this attempt ("Provider · model"). */
        val attemptLabel: String,
        val kind: Kind,
    )

    enum class Kind { API, LOCAL, SERVER, FREE, AGY }

    /** A parsed persisted pick. Blank/unknown ids parse to null (Auto). */
    sealed interface ModelPick {
        data class Api(val connectorId: String, val model: String) : ModelPick
        data class Local(val model: String) : ModelPick
        data class Server(val model: String) : ModelPick
        data object Free : ModelPick
        data class Agy(val email: String) : ModelPick
    }

    fun parsePick(id: String): ModelPick? {
        if (id.isBlank()) return null
        return when {
            id == "free" -> ModelPick.Free
            id.startsWith("agy:") -> id.removePrefix("agy:").trim().lowercase().takeIf { it.isNotBlank() }?.let { ModelPick.Agy(it) }
            id.startsWith("local:") -> id.removePrefix("local:").takeIf { it.isNotBlank() }?.let { ModelPick.Local(it) }
            id.startsWith("server:") -> id.removePrefix("server:").takeIf { it.isNotBlank() }?.let { ModelPick.Server(it) }
            id.startsWith("api:") -> {
                val rest = id.removePrefix("api:")
                val connectorId = rest.substringBefore(":")
                val model = rest.substringAfter(":", "")
                if (connectorId.isNotBlank() && model.isNotBlank()) ModelPick.Api(connectorId, model) else null
            }
            else -> null
        }
    }

    /**
     * Every model the user can actually use right now, grouped in chooser
     * order: Antigravity accounts first, then each connected key's models,
     * then the on-phone models, then Free (slow). Only enabled connectors
     * with models, and only Antigravity accounts that have a sign-in token
     * ([hasAgyToken]), are listed — nothing fake, nothing unusable. The
     * model ids come from the connectors' own stored lists (the providers'
     * real configured models), never from a guessed list.
     */
    fun buildChooserEntries(
        connectors: List<Connector>,
        agyAccounts: List<AgyAccount>,
        hasAgyToken: (AgyAccount) -> Boolean = { true },
    ): List<ChooserEntry> {
        val out = ArrayList<ChooserEntry>()
        for (a in agyAccounts.filter { it.enabled && hasAgyToken(it) }) {
            val title = a.label.ifBlank { a.email }
            out += ChooserEntry(
                id = "agy:${a.email.lowercase()}",
                group = "Antigravity",
                title = title,
                subtitle = if (a.tier.isNullOrBlank()) a.email else "${a.email} · ${a.tier}",
                attemptLabel = "Antigravity · $title",
                kind = Kind.AGY,
            )
        }
        for (c in connectors.filter { it.enabled && !it.own }) {
            val models = buildList {
                c.pinnedModel?.let { add(it) }
                addAll(c.models)
                addAll(c.fastModels)
            }.distinct().filter { it.isNotBlank() }
            for (m in models) {
                out += ChooserEntry(
                    id = "api:${c.id}:$m",
                    group = c.provider.name,
                    title = m,
                    subtitle = c.keyHint,
                    attemptLabel = "${c.provider.name} · $m",
                    kind = Kind.API,
                )
            }
        }
        connectors.firstOrNull { it.providerId == "local" && it.enabled }?.let { c ->
            val model = c.localModel ?: c.models.firstOrNull().orEmpty()
            if (model.isNotBlank()) out += ChooserEntry(
                id = "local:$model", group = "On this phone", title = model,
                subtitle = "Offline · on this phone", attemptLabel = "Local model · $model", kind = Kind.LOCAL,
            )
        }
        connectors.firstOrNull { it.providerId == "myserver" && it.enabled }?.let { c ->
            val model = c.pinnedModel ?: c.models.firstOrNull().orEmpty()
            if (model.isNotBlank()) out += ChooserEntry(
                id = "server:$model", group = "On this phone", title = model,
                subtitle = MyServer.host(c.baseUrl.orEmpty()), attemptLabel = "My server · $model", kind = Kind.SERVER,
            )
        }
        out += ChooserEntry(
            id = "free", group = "Free", title = FreeFallback.NAME,
            subtitle = "No key needed · about 1 reply every 45 s",
            attemptLabel = FreeFallback.NAME, kind = Kind.FREE,
        )
        return out
    }

    /** The short label the composer chip and header subtitle show for a pick. */
    fun shortLabel(entry: ChooserEntry): String = when (entry.kind) {
        Kind.AGY -> "Antigravity · ${entry.title}"
        Kind.FREE -> FreeFallback.NAME
        else -> entry.title
    }

    /**
     * What the composer chip / header subtitle shows: the picked model's
     * label, or the speed tier ("Auto" / "Fast" / "Expert" / …) when nothing
     * is picked. A stale pick (key removed since) falls back to the tier.
     * [displayLabel] builds the entries itself; this overload reuses entries
     * the caller already built (so the label stays cheap on every recompose).
     */
    fun displayLabelFromEntries(pickedId: String, mode: String, entries: List<ChooserEntry>): String {
        val tier = when (mode) {
            "FAST" -> "Fast"; "EXPERT" -> "Expert"; "LOCAL" -> "Local model"
            "SERVER" -> "My server"; "FREE" -> "Free (slow)"; else -> "Auto"
        }
        if (pickedId.isBlank()) {
            // LFM3 default (2026-11-20): no pick + AUTO + LFM3 set up as the
            // phone's local model → the chip / header names the default instead
            // of the tier. The match is the exact LocalModel id ("lfm3-1.7b"):
            // a local row for any other model still shows the tier, and when
            // no local row exists at all (LFM3 not downloaded / not set up)
            // the label stays "Auto" — never a claim LFM3 answers when it can't.
            if (mode == "AUTO") entries.firstOrNull { it.kind == Kind.LOCAL && it.id == "local:$DEFAULT_LOCAL_MODEL_ID" }?.let { return DEFAULT_LOCAL_MODEL_LABEL }
            return tier
        }
        return entries.firstOrNull { it.id == pickedId }?.let { shortLabel(it) } ?: tier
    }

    fun displayLabel(pickedId: String, mode: String, connectors: List<Connector>, agyAccounts: List<AgyAccount>, hasAgyToken: (AgyAccount) -> Boolean = { true }): String =
        displayLabelFromEntries(pickedId, mode, buildChooserEntries(connectors, agyAccounts, hasAgyToken))

    /**
     * The attempt order for a run: the picked entry first, then every other
     * entry in chooser order (the failover tail). A blank or stale pick
     * keeps the chooser order (Auto). The list is walked once per turn —
     * there is no wrap-around, so the shift chain always ends.
     */
    fun attemptOrder(entries: List<ChooserEntry>, pickedId: String): List<ChooserEntry> {
        if (pickedId.isBlank()) return entries
        val picked = entries.firstOrNull { it.id == pickedId } ?: return entries
        return listOf(picked) + entries.filterNot { it.id == pickedId }
    }

    /** First entry in [ordered] that has not been tried yet, or null when all are spent. */
    fun nextUntried(ordered: List<ChooserEntry>, triedIds: Set<String>): ChooserEntry? =
        ordered.firstOrNull { it.id !in triedIds }

    /** Same, over plain ids (for callers that only track ids). */
    fun nextUntriedId(orderedIds: List<String>, triedIds: Set<String>): String? =
        orderedIds.firstOrNull { it !in triedIds }

    /**
     * Quota / rate-limit exhaustion: the only failures that trigger an
     * auto-shift. Reuses the providers' own classification ([FailKind]) plus
     * the Antigravity wording ([AgyFailover]) — no new error strings.
     * Auth, server and network failures never shift (shifting would just
     * burn the next key on the same broken request).
     */
    fun isExhaustion(e: LlmException): Boolean =
        e.kind == FailKind.QUOTA || e.kind == FailKind.RATE || e.code == 429 ||
            AgyFailover.isQuotaFailure(e.message.orEmpty())

    /** The visible in-chat shift line. Posted as text, so it stays in the chat. */
    fun shiftNotice(fromLabel: String, toLabel: String): String =
        "Shifted to $toLabel — $fromLabel hit its limit."

    /** Honest terminal state when every model/account is spent: never a silent loop. */
    const val ALL_EXHAUSTED_MESSAGE =
        "All models hit their limit. Add another key in Connectors, or try again later."

    /**
     * Which persisted pick a shift landed on, from the attempt label Agent
     * reports ("Provider · model" / "Antigravity · <account>" / "Free
     * (slow)"). Null when the label cannot be tied to a chooser entry — the
     * pick then stays as it was (the failing entry is already cooling down,
     * so the next run skips it anyway).
     */
    fun findChoiceId(entries: List<ChooserEntry>, label: String): String? {
        entries.firstOrNull { it.attemptLabel == label }?.let { return it.id }
        if (label.startsWith("Antigravity · ")) {
            val who = label.removePrefix("Antigravity · ").trim()
            // "auto" (or a model id) names no account — the account-level
            // shift already happened inside AgyClient; the pick is unchanged.
            entries.firstOrNull { it.kind == Kind.AGY && (it.title == who || it.subtitle.startsWith(who)) }?.let { return it.id }
            return null
        }
        val parts = label.split(" · ", limit = 2)
        if (parts.size == 2) entries.firstOrNull { it.kind == Kind.API && it.group == parts[0] && it.title == parts[1] }?.let { return it.id }
        return null
    }

    /**
     * The persisted pick, held in memory and saved through [onPickSaved]
     * (ChatViewModel wires that to settings.pickedModel). Picking is one
     * call; the next run routes through the pick, and a shift re-picks the
     * model it landed on, so the shift sticks for the next message too.
     */
    class ModelRouter(initialPick: String = "", private val onPickSaved: (String) -> Unit = {}) {
        var pickedId: String = initialPick
            private set

        fun pick(id: String) {
            pickedId = id
            onPickSaved(id)
        }

        fun clear() = pick("")

        fun ordered(entries: List<ChooserEntry>): List<ChooserEntry> = attemptOrder(entries, pickedId)

        /** Record a shift: persist the new pick and return the notice line. */
        fun shiftTo(newId: String, fromLabel: String, toLabel: String): String {
            pick(newId)
            return shiftNotice(fromLabel, toLabel)
        }
    }
}
