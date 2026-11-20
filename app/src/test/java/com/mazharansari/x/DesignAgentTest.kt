package com.mazharansari.x

import com.mazharansari.x.agent.CloneProjects
import com.mazharansari.x.agent.DesignTokens
import com.mazharansari.x.agent.ToolCatalog
import com.mazharansari.x.agent.XBotSpawn
import com.mazharansari.x.data.BotCatalog
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * DESIGN AGENT (2026-11-20) — website cloning, ported from
 * Jane-xiaoer/claude-skill-web-clone (MIT) via the English fork
 * monjurkuet/web-clone. Pure JVM tests: no model, no network, no Android
 * framework. The skill pack is read from the repo tree (the test walks up
 * from the working directory to find app/src/main/assets/skills/).
 */
class DesignAgentTest {

    private fun skillPackDir(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "app/src/main/assets/skills/web-clone-design")
            if (File(candidate, "SKILL.md").exists()) return candidate
            val direct = File(dir, "src/main/assets/skills/web-clone-design")
            if (File(direct, "SKILL.md").exists()) return direct
            dir = dir.parentFile
        }
        fail("web-clone-design skill pack not found under the repo tree")
        return File(".") // unreachable
    }

    private fun skillText(): String = File(skillPackDir(), "SKILL.md").readText()

    // ── Skill pack ────────────────────────────────────────────────────────

    @Test fun skillPackExistsWithFrontMatter() {
        val dir = skillPackDir()
        assertTrue(File(dir, "SKILL.md").exists())
        assertTrue(File(dir, "references/design-dna-template.json").exists())
        val text = skillText()
        assertTrue(text.contains("name: web-clone-design"))
        assertTrue(text.contains("claude-skill-web-clone"))
        assertTrue(text.contains("monjurkuet/web-clone"))
    }

    @Test fun skillPackStatesIronRuleSourceFirst() {
        val text = skillText()
        assertTrue(text.contains("real source first", ignoreCase = true))
        assertTrue(text.contains("never AI-guessed code", ignoreCase = true))
    }

    @Test fun skillPackRequiresPlanBeforeAnyBuild() {
        val text = skillText()
        assertTrue(text.contains("Verdict + plan, SHOWN FIRST"))
        assertTrue(text.contains("build prototype / not now"))
        assertTrue(text.contains("Do NOT build before the user"))
        assertTrue(text.contains("prototype 1 day"))
    }

    @Test fun skillPackStatesOwnBrandingAndAssetBoundary() {
        val text = skillText()
        assertTrue(text.contains("OWN BRANDING ALWAYS", ignoreCase = true))
        assertTrue(text.contains("never copy the reference site's logo", ignoreCase = true))
        assertTrue(text.contains("never hotlink", ignoreCase = true))
        assertTrue(text.contains("copyrighted images"))
        assertTrue(text.contains("no public redeploy"))
    }

    @Test fun skillPackRequiresTokensReadBeforeEdit() {
        val text = skillText()
        assertTrue(text.contains("design-dna.json"))
        assertTrue(text.contains("Before ANY edit"))
        assertTrue(text.contains("read") && text.contains("FIRST"))
        assertTrue(text.contains("never restyled from scratch") || text.contains("never restyle"))
        assertTrue(text.contains("byte-identical"))
    }

    @Test fun skillPackCoversMethodology() {
        val text = skillText()
        // Decision tree, complexity grading, modes, QA.
        assertTrue(text.contains("Decision tree"))
        assertTrue(text.contains("L1"))
        assertTrue(text.contains("L6"))
        assertTrue(text.contains("visual clone"))
        assertTrue(text.contains("content remix"))
        assertTrue(text.contains("faithful clone"))
        assertTrue(text.contains("Strip tracking"))
        assertTrue(text.contains("Verify in a real browser"))
        assertTrue(text.contains("design-dna.json"))
    }

    @Test fun designDnaTemplateHasTokenPaths() {
        val template = File(skillPackDir(), "references/design-dna-template.json").readText()
        assertTrue(template.contains("design_system"))
        assertTrue(template.contains("\"primary\""))
        assertTrue(template.contains("\"font_families\""))
        assertTrue(template.contains("\"max_content_width\""))
        assertTrue(template.contains("source_url"))
        // Balanced braces — the template must be loadable JSON, not a fragment.
        assertEquals(template.count { it == '{' }, template.count { it == '}' })
    }

    // ── Bot seed ──────────────────────────────────────────────────────────

    @Test fun designAgentSeedExists() {
        val def = BotCatalog.byId("store-designagent")
        assertNotNull(def)
        assertEquals("Design Agent", def!!.name)
        assertEquals("https://github.com/Jane-xiaoer/claude-skill-web-clone", def.sourceUrl)
        assertEquals("MIT", def.license)
        assertTrue(def.skills.contains("web-clone-design"))
        assertTrue(def.skills.contains("local-frontend-design"))
        val groupIds = ToolCatalog.groups.map { it.id }.toSet()
        def.toolGroups.forEach { g -> assertTrue("unknown tool group $g", g in groupIds) }
        assertTrue(def.toolGroups.contains("web"))
        assertTrue(def.toolGroups.contains("browser"))
        assertTrue(def.toolGroups.contains("files"))
    }

    @Test fun designAgentPromptHasPlanFirstBrandingAndTheme() {
        val def = BotCatalog.byId("store-designagent")!!
        val prompt = def.promptBlock()
        assertTrue(prompt.contains(def.name))
        assertTrue(prompt.contains("design-dna.json"))
        assertTrue(prompt.contains("NEVER build before the user says yes"))
        assertTrue(prompt.contains("OWN BRANDING ALWAYS"))
        assertTrue(prompt.contains("never copy the reference site's logo"))
        assertTrue(prompt.contains("never hotlink"))
        assertTrue(prompt.contains("read its design-dna.json FIRST"))
    }

    @Test fun spawnPicksDesignAgentForCloneBriefs() {
        assertEquals("store-designagent", XBotSpawn.pickSubBot("Clone this website https://example.com")?.id)
        assertEquals("store-designagent", XBotSpawn.pickSubBot("make a website like example.com")?.id)
        assertEquals("store-designagent", XBotSpawn.pickSubBot("Mirror this website for my shop")?.id)
        // A repo clone is still the GitHub bot's job, not the Design Agent's.
        assertEquals("store-githubber", XBotSpawn.pickSubBot("Clone the repo and open a pull request")?.id)
    }

    // ── CloneProjects helper: project record + theme tokens ───────────────

    private fun tempHub(): File =
        File("/tmp/cloneprojects-test-${System.nanoTime()}").apply { mkdirs() }

    private val sampleTokens = DesignTokens(
        primary = "#1a2a4a", secondary = "#e8e4da", accent = "#c9a227",
        background = "#ffffff", surface = "#f6f4ef", text = "#1c1c1c",
        fontHeading = "Fraunces", fontBody = "Inter", fontMono = "IBM Plex Mono",
        spacingBase = "8px", radiusMedium = "12px", maxContentWidth = "1200px",
    )

    @Test fun createWritesProjectRecordTokensAndNotes() {
        val hub = tempHub()
        try {
            val project = CloneProjects.create(
                hub, "Acme Store", "https://acme.example",
                mode = "content-remix", complexity = "L2", tokens = sampleTokens,
            )
            assertNotNull(project)
            assertEquals("acme-store", project!!.slug)
            assertEquals("https://acme.example", project.sourceUrl)
            assertEquals("content-remix", project.mode)
            assertEquals("L2", project.complexity)
            val dir = CloneProjects.projectDir(hub, "acme-store")
            assertTrue(File(dir, "project.json").exists())
            assertTrue(File(dir, "design-dna.json").exists())
            assertTrue(File(dir, "NOTES.md").exists())
            val reloaded = CloneProjects.loadProject(dir)
            assertEquals(project, reloaded)
            assertEquals(sampleTokens, CloneProjects.loadTokens(dir))
        } finally { hub.deleteRecursively() }
    }

    @Test fun createRefusesSecondProjectForSameSlug() {
        val hub = tempHub()
        try {
            assertNotNull(CloneProjects.create(hub, "Acme Store", "https://acme.example"))
            // A follow-up change reuses the existing project — never a second folder.
            assertNull(CloneProjects.create(hub, "Acme Store", "https://acme.example"))
        } finally { hub.deleteRecursively() }
    }

    @Test fun loadTokensReturnsNullWithoutTokensFile() {
        val hub = tempHub()
        try {
            val project = CloneProjects.create(hub, "Acme Store", "https://acme.example")!!
            val dir = CloneProjects.projectDir(hub, project.slug)
            assertTrue(CloneProjects.tokensFile(dir).delete())
            assertNull(CloneProjects.loadTokens(dir))
        } finally { hub.deleteRecursively() }
    }

    @Test fun tokensUnchangedEmptyForContentOnlyChange() {
        // A follow-up change that touches no token (new section, new copy)
        // must report zero token changes.
        val before = sampleTokens
        val after = sampleTokens.copy()
        assertTrue(CloneProjects.tokensUnchanged(before, after).isEmpty())
    }

    @Test fun tokensUnchangedNamesExactlyWhatMoved() {
        // "Make the header blue" may move the accent token — and nothing else.
        val before = sampleTokens
        val after = sampleTokens.copy(accent = "#2563eb")
        assertEquals(listOf("color.accent"), CloneProjects.tokensUnchanged(before, after))
        val restyled = sampleTokens.copy(primary = "#000000", fontBody = "Arial", spacingBase = "4px")
        assertEquals(
            listOf("color.primary", "typography.fontBody", "spacing.base"),
            CloneProjects.tokensUnchanged(before, restyled),
        )
    }

    @Test fun saveTokensPreservesOtherDnaSections() {
        val hub = tempHub()
        try {
            val project = CloneProjects.create(hub, "Acme Store", "https://acme.example")!!
            val dir = CloneProjects.projectDir(hub, project.slug)
            // Simulate a full DNA document the agent filled during recon.
            CloneProjects.tokensFile(dir).writeText(
                "{\"meta\":{\"name\":\"Acme\",\"source_url\":\"https://acme.example\"}," +
                    "\"design_style\":{\"aesthetic\":{\"mood\":[\"calm\"]}}," +
                    "\"visual_effects\":{\"overview\":{\"effect_intensity\":\"subtle-accent\"}}," +
                    "\"design_system\":{\"color\":{\"primary\":{\"hex\":\"#000000\"}}}}",
            )
            CloneProjects.saveTokens(dir, sampleTokens)
            val saved = CloneProjects.tokensFile(dir).readText()
            assertTrue(saved.contains("\"calm\""))
            assertTrue(saved.contains("subtle-accent"))
            assertTrue(saved.contains("https://acme.example"))
            assertEquals(sampleTokens, CloneProjects.loadTokens(dir))
        } finally { hub.deleteRecursively() }
    }

    @Test fun loadTokensReadsSkillPackTemplate() {
        // The bundled template is a valid tokens document: empty tokens,
        // never a crash, and the helper can fill it in.
        val hub = tempHub()
        try {
            val project = CloneProjects.create(hub, "Acme Store", "https://acme.example")!!
            val dir = CloneProjects.projectDir(hub, project.slug)
            File(skillPackDir(), "references/design-dna-template.json")
                .copyTo(CloneProjects.tokensFile(dir), overwrite = true)
            val tokens = CloneProjects.loadTokens(dir)
            assertNotNull(tokens)
            assertEquals("", tokens!!.primary)
            assertEquals("8px", tokens.spacingBase)
            CloneProjects.saveTokens(dir, sampleTokens)
            assertEquals(sampleTokens, CloneProjects.loadTokens(dir))
        } finally { hub.deleteRecursively() }
    }

    @Test fun slugOfHandlesUrlsAndNames() {
        assertEquals("acme-store", CloneProjects.slugOf("Acme Store"))
        assertEquals("acme-example", CloneProjects.slugOf("https://acme.example/"))
        assertEquals("clone", CloneProjects.slugOf("!!!"))
    }
}
