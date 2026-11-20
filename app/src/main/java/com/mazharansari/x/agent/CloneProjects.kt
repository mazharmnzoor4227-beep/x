package com.mazharansari.x.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/**
 * DESIGN AGENT — clone-project store (2026-11-20).
 *
 * A website clone is a long-lived project, not a one-shot answer: the user
 * pastes a URL, the Design Agent builds the clone, and then the user keeps
 * asking for changes inside THAT SAME website while KEEPING the same theme.
 * This helper is the file-backed mechanism that makes theme preservation
 * real instead of a prompt wish:
 *
 * - one folder per clone under the workspace clone hub `website-clones/`
 *   (`website-clones/<slug>/`), with a `project.json` record and a
 *   `design-dna.json` tokens file at its root;
 * - the agent MUST read `design-dna.json` before ANY edit to an existing
 *   clone project (the `web-clone-design` skill pack says so, and
 *   [loadTokens] is the call that does it);
 * - [tokensUnchanged] proves a follow-up change kept the theme: it lists
 *   every token field that differs between the tokens before and after an
 *   edit — empty means the theme survived, non-empty names exactly what
 *   moved (allowed only when the user named that token change).
 *
 * The tokens file is the nested design-DNA schema (same shape as the skill
 * pack's `references/design-dna-template.json`: `design_system.color.*.hex`,
 * `design_system.typography.font_families.*`, `spacing.base_unit`,
 * `shape.border_radius.medium`, `layout.max_content_width`). [loadTokens]
 * reads those nested paths and [saveTokens] writes them back into the same
 * nested document, preserving every other section (`design_style`,
 * `visual_effects`, `meta`) untouched — a token save never drops DNA.
 *
 * Pure Kotlin over plain files (same pattern as BotDefinitionStore and
 * Project): no Android framework, no model, no network. The agent reaches
 * these files through the existing `read_file` / `write_file` tools; no new
 * agent tool was needed — the upstream probe scripts map onto tools that
 * already exist (see the `web-clone-design` skill pack's tool-mapping
 * table). Methodology ported from Jane-xiaoer/claude-skill-web-clone
 * (MIT); the design-DNA schema is adapted from zanwei/design-dna (MIT).
 */
@Serializable
data class CloneProject(
    val id: String,
    val name: String,
    val slug: String,
    /** The reference site the clone was built from. Never the clone's own brand. */
    val sourceUrl: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    /** faithful-clone | visual-clone | content-remix | technical-teardown */
    val mode: String = "visual-clone",
    /** L1..L6 complexity grade from the pre-clone assessment. */
    val complexity: String = "L1",
    /** Relative path of the project folder inside the workspace clone hub. */
    val projectDir: String = "website-clones/$slug",
    /** Relative path of the theme-tokens file inside the project folder. */
    val tokensPath: String = "design-dna.json",
)

/**
 * The theme tokens a clone project keeps. A trimmed, enforceable subset of
 * the full design-DNA schema: these are the fields the agent must preserve
 * across follow-up edits. Every value comes from recon of the real
 * reference site — never invented, never empty on a built project.
 */
@Serializable
data class DesignTokens(
    val primary: String = "",
    val secondary: String = "",
    val accent: String = "",
    val background: String = "",
    val surface: String = "",
    val text: String = "",
    val fontHeading: String = "",
    val fontBody: String = "",
    val fontMono: String = "",
    val spacingBase: String = "8px",
    val radiusMedium: String = "",
    val maxContentWidth: String = "",
) {
    /** Every token field as (path, value) pairs, for the unchanged-check. */
    fun fields(): List<Pair<String, String>> = listOf(
        "color.primary" to primary,
        "color.secondary" to secondary,
        "color.accent" to accent,
        "color.background" to background,
        "color.surface" to surface,
        "color.text" to text,
        "typography.fontHeading" to fontHeading,
        "typography.fontBody" to fontBody,
        "typography.fontMono" to fontMono,
        "spacing.base" to spacingBase,
        "shape.radiusMedium" to radiusMedium,
        "layout.maxContentWidth" to maxContentWidth,
    )
}

object CloneProjects {
    const val HUB_DIR = "website-clones"
    const val PROJECT_FILE = "project.json"
    const val TOKENS_FILE = "design-dna.json"
    const val NOTES_FILE = "NOTES.md"

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun slugOf(name: String): String = name.lowercase()
        .replace(Regex("https?://"), "")
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(40).trim('-')
        .ifBlank { "clone" }

    fun projectDir(hub: File, slug: String): File = File(File(hub, HUB_DIR), slug)

    fun tokensFile(projectDir: File): File = File(projectDir, TOKENS_FILE)

    fun projectFile(projectDir: File): File = File(projectDir, PROJECT_FILE)

    /**
     * Scaffold a new clone project: folder + `project.json` record +
     * `design-dna.json` from the given tokens + `NOTES.md` stub. Returns
     * null when a project with the same slug already exists — a follow-up
     * change must reuse the existing project, never scaffold a second one
     * for the same source URL.
     */
    fun create(
        hub: File,
        name: String,
        sourceUrl: String,
        mode: String = "visual-clone",
        complexity: String = "L1",
        tokens: DesignTokens = DesignTokens(),
        createdAt: Long = System.currentTimeMillis(),
    ): CloneProject? {
        val slug = slugOf(name)
        val dir = projectDir(hub, slug)
        if (dir.exists()) return null
        dir.mkdirs()
        val project = CloneProject(
            id = "clone-$slug",
            name = name,
            slug = slug,
            sourceUrl = sourceUrl,
            createdAt = createdAt,
            mode = mode,
            complexity = complexity,
            projectDir = "$HUB_DIR/$slug",
        )
        projectFile(dir).writeText(json.encodeToString(project))
        saveTokens(dir, tokens)
        File(dir, NOTES_FILE).writeText(
            "# $name · Clone notes\n\n" +
                "- Original URL: $sourceUrl\n" +
                "- Mode: $mode\n" +
                "- Complexity: $complexity\n" +
                "- Created: $createdAt\n" +
                "- Theme tokens: $TOKENS_FILE (read before every edit)\n",
        )
        return project
    }

    fun loadProject(dir: File): CloneProject? = runCatching {
        json.decodeFromString<CloneProject>(projectFile(dir).readText())
    }.getOrNull()

    /** Read the nested design-dna.json document, or null when it is missing or unparseable. */
    fun loadDna(dir: File): JsonObject? = runCatching {
        json.parseToJsonElement(tokensFile(dir).readText()).jsonObject
    }.getOrNull()

    private fun nestedString(obj: JsonObject, path: String): String {
        var current: JsonObject? = obj
        val parts = path.split(".")
        for (part in parts.dropLast(1)) {
            current = current?.get(part) as? JsonObject ?: return ""
        }
        return runCatching { current?.get(parts.last())?.jsonPrimitive?.content }.getOrDefault("") ?: ""
    }

    /**
     * Load the theme tokens from the nested design-dna.json. Null when the
     * project has no tokens file — the agent must stop and rebuild it
     * before editing, never edit blind.
     */
    fun loadTokens(dir: File): DesignTokens? {
        val dna = loadDna(dir) ?: return null
        fun at(path: String): String = nestedString(dna, path)
        return DesignTokens(
            primary = at("design_system.color.primary.hex"),
            secondary = at("design_system.color.secondary.hex"),
            accent = at("design_system.color.accent.hex"),
            background = at("design_system.color.surface.background.hex"),
            surface = at("design_system.color.surface.card.hex"),
            text = at("design_system.color.text.hex"),
            fontHeading = at("design_system.typography.font_families.heading"),
            fontBody = at("design_system.typography.font_families.body"),
            fontMono = at("design_system.typography.font_families.mono"),
            spacingBase = at("design_system.spacing.base_unit").ifBlank { "8px" },
            radiusMedium = at("design_system.shape.border_radius.medium"),
            maxContentWidth = at("design_system.layout.max_content_width"),
        )
    }

    private fun setNested(obj: JsonObject, path: String, value: String): JsonObject {
        val parts = path.split(".")
        if (parts.size == 1) return buildJsonObject {
            obj.forEach { (k, v) -> put(k, v) }
            put(parts[0], JsonPrimitive(value))
        }
        val head = parts[0]
        val child = (obj[head] as? JsonObject) ?: buildJsonObject {}
        return buildJsonObject {
            obj.forEach { (k, v) -> if (k != head) put(k, v) }
            put(head, setNested(child, parts.drop(1).joinToString("."), value))
        }
    }

    /**
     * Write tokens into the nested design-dna.json, preserving every other
     * section of the existing document. The `text` token has no dedicated
     * hex slot in the template schema, so it lives at
     * `design_system.color.text.hex`, where [loadTokens] reads it back.
     */
    fun saveTokens(dir: File, tokens: DesignTokens) {
        var dna = loadDna(dir) ?: buildJsonObject {}
        // Token paths that map 1:1 onto the nested template schema.
        val direct = listOf(
            "design_system.color.primary.hex" to tokens.primary,
            "design_system.color.secondary.hex" to tokens.secondary,
            "design_system.color.accent.hex" to tokens.accent,
            "design_system.color.surface.background.hex" to tokens.background,
            "design_system.color.surface.card.hex" to tokens.surface,
            "design_system.typography.font_families.heading" to tokens.fontHeading,
            "design_system.typography.font_families.body" to tokens.fontBody,
            "design_system.typography.font_families.mono" to tokens.fontMono,
            "design_system.spacing.base_unit" to tokens.spacingBase,
            "design_system.shape.border_radius.medium" to tokens.radiusMedium,
            "design_system.layout.max_content_width" to tokens.maxContentWidth,
        )
        for ((path, value) in direct) dna = setNested(dna, path, value)
        // Text colour has no dedicated hex slot in the template schema, so
        // it lives at design_system.color.text.hex (see loadTokens).
        dna = setNested(dna, "design_system.color.text.hex", tokens.text)
        tokensFile(dir).writeText(json.encodeToString(dna))
    }

    /**
     * Theme-preservation check: the token fields that differ between the
     * tokens loaded BEFORE an edit and the tokens AFTER it. Empty = the
     * theme survived the edit. Non-empty = exactly which tokens moved; that
     * is only acceptable when the user explicitly asked to change those
     * tokens (e.g. "make the header blue" may move `color.accent`, nothing
     * else).
     */
    fun tokensUnchanged(before: DesignTokens, after: DesignTokens): List<String> =
        before.fields().zip(after.fields())
            .filter { (a, b) -> a.second != b.second }
            .map { (a, _) -> a.first }
}
