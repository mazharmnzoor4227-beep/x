package com.mazharansari.x

import com.mazharansari.x.agent.BotRegistry
import com.mazharansari.x.agent.BotRun
import com.mazharansari.x.agent.BotKind
import com.mazharansari.x.agent.BotStatus
import com.mazharansari.x.agent.SubagentLimits
import com.mazharansari.x.agent.SubagentOrchestrator
import com.mazharansari.x.agent.WorkerFact
import com.mazharansari.x.agent.WorkerResult
import com.mazharansari.x.agent.WorkerRole
import com.mazharansari.x.agent.XBotSpawn
import com.mazharansari.x.agent.ToolCatalog
import com.mazharansari.x.data.BotCatalog
import com.mazharansari.x.data.BotDefinitionStore
import com.mazharansari.x.data.BotLogo
import com.mazharansari.x.data.X_BOT_ID
import com.mazharansari.x.data.xBotDefinition
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Navigation restructure (2026-11-20) — X Bot + its 50-specialist roster.
 * Pure JVM tests: no model, no network, no Android framework.
 */
class XBotRosterTest {

    /** The skill packs actually bundled in app/src/main/assets/skills/. */
    private val bundledSkills = setOf(
        "agent-building", "banner-design", "deep-research", "delivery",
        "local-android-dev", "local-app-store-design", "local-frontend-design",
        "local-frontend-seo", "local-game-dev", "local-header-footer-design",
        "local-playwright-skill", "local-shopify-development",
        "local-pre-delivery-verification-gate", "local-verified-status-claims",
        "local_external_builder_brief", "local_reference_pattern_research_gate",
        "local_verify_before_recommending", "meta-astryx-design",
        "mika-content-transcribe", "mika-documents", "mika-info-research",
        "mika-messaging", "mika-skill-authoring", "pdf", "ponytail-minimal-code",
        "reverse-engineering-android", "roman-urdu", "token-efficiency",
        "video", "website-audit",
    )

    @Test fun rosterHasFiftySpecialistsPlusXBot() {
        assertEquals(50, BotCatalog.seeds.size)
        assertEquals(50, BotCatalog.seeds.map { it.id }.distinct().size)
        assertEquals(50, BotCatalog.seeds.map { it.name }.distinct().size)
        // X Bot is the orchestrator on top of the roster, not a seed in it.
        assertNull(BotCatalog.byId(X_BOT_ID))
        val x = xBotDefinition()
        assertEquals(X_BOT_ID, x.id)
        assertEquals("X Bot", x.name)
        assertTrue(x.persona.contains("subagent_swarm"))
        assertTrue(x.persona.contains("only ever talk to X Bot") || x.persona.contains("ONLY bot"))
    }

    @Test fun everySeedUsesRealToolGroupsAndSkills() {
        val groupIds = ToolCatalog.groups.map { it.id }.toSet()
        BotCatalog.seeds.forEach { def ->
            def.toolGroups.forEach { g ->
                assertTrue("${def.id} uses unknown tool group $g", g in groupIds)
            }
            def.skills.forEach { s ->
                assertTrue("${def.id} uses unbundled skill $s", s in bundledSkills)
            }
        }
    }

    @Test fun noStubSeeds() {
        // The three known stubs were removed or rewritten — none of their
        // honest-gap wording may survive in the roster.
        assertNull(BotCatalog.byId("store-docmaker"))
        assertNull(BotCatalog.byId("store-shopkeeper"))
        val haystack = BotCatalog.seeds.joinToString("\n") { it.name + " " + it.description + " " + it.persona }
        // (The Shop Watcher persona honestly discloses "No shop API is wired
        // up" — that disclosure is the rewrite, not a stub — so it is not
        // asserted here.)
        assertFalse(haystack.contains("not wired", ignoreCase = true))
        assertFalse(haystack.contains("blocked", ignoreCase = true))
        assertFalse(haystack.contains("not yet", ignoreCase = true))
        // No donor the licence gate bans.
        assertFalse(haystack.contains("Andee", ignoreCase = true))
        assertFalse(haystack.contains("Locus", ignoreCase = true))
        assertFalse(haystack.contains("Aether", ignoreCase = true))
        assertFalse(haystack.contains("Operit", ignoreCase = true))
    }

    @Test fun xBotEnsuredOnceAndFirst() {
        val dir = File("/tmp/xbot-test-${System.nanoTime()}").apply { mkdirs() }
        try {
            val store = BotDefinitionStore(dir)
            assertTrue(store.definitions.value.isEmpty())
            val first = store.ensureXBot()
            assertEquals(X_BOT_ID, first.id)
            assertEquals("X Bot", first.name)
            assertEquals(X_BOT_ID, store.definitions.value.first().id)
            // Second call is a no-op — still exactly one X Bot.
            val second = store.ensureXBot()
            assertEquals(first.id, second.id)
            assertEquals(1, store.definitions.value.count { it.id == X_BOT_ID })
            // Persist round-trip: a new store on the same folder keeps it.
            val reloaded = BotDefinitionStore(dir)
            assertEquals(X_BOT_ID, reloaded.definitions.value.first().id)
        } finally { dir.deleteRecursively() }
    }

    @Test fun xBotRenameAndLogoPersist() {
        val dir = File("/tmp/xbot-profile-test-${System.nanoTime()}").apply { mkdirs() }
        try {
            val store = BotDefinitionStore(dir)
            store.ensureXBot()
            store.rename(X_BOT_ID, "X Prime")
            store.setLogo(X_BOT_ID, BotLogo.threeD("bronze"))
            val reloaded = BotDefinitionStore(dir)
            val x = reloaded.definitions.value.first { it.id == X_BOT_ID }
            assertEquals("X Prime", x.name)
            assertEquals("3d:bronze", x.logo)
            // Blank rename is ignored; unknown ids are no-ops.
            store.rename(X_BOT_ID, "   ")
            store.rename("nobody", "Nope")
            store.setLogo("nobody", "video:/tmp/x.mp4")
            assertEquals("X Prime", store.definitions.value.first { it.id == X_BOT_ID }.name)
        } finally { dir.deleteRecursively() }
    }

    @Test fun spawnPicksRealSubBots() {
        assertEquals("store-videoeditor", XBotSpawn.pickSubBot("Trim this video and add captions")?.id)
        assertEquals("store-tester", XBotSpawn.pickSubBot("Fix the failing tests")?.id)
        assertEquals("store-pricechecker", XBotSpawn.pickSubBot("Research the price of a phone in Pakistan")?.id)
        assertEquals("store-githubber", XBotSpawn.pickSubBot("Clone the repo and open a pull request")?.id)
        assertEquals("store-prreviewer", XBotSpawn.pickSubBot("Review this pull request, file by file")?.id)
        assertEquals("store-pdfreader", XBotSpawn.pickSubBot("Summarise this PDF for me")?.id)
        assertEquals("store-emailer", XBotSpawn.pickSubBot("Draft an email to the shop owner")?.id)
        assertNull(XBotSpawn.pickSubBot("hello there"))
        assertNull(XBotSpawn.pickSubBot(""))
        // Every pick is a real roster seed, never a made-up id.
        listOf("video edit", "write code", "audit the website", "make a game", "transcribe this audio")
            .forEach { brief ->
                val picked = XBotSpawn.pickSubBot(brief)
                assertNotNull("no sub-bot for '$brief'", picked)
                assertEquals(picked!!.id, BotCatalog.byId(picked.id)?.id)
            }
    }

    @Test fun spawnTagsWorkerTasks() {
        val tasks = SubagentOrchestrator.parseWorkerSpecs(
            "CODER: write the login screen\nRESEARCHER: find the latest prices\nTester: run the suite", "Build the shop app")
        assertEquals(3, tasks.size)
        assertEquals("store-coder", tasks[0].subBotId)
        assertEquals("Coder", tasks[0].subBotName)
        assertEquals("store-pricechecker", tasks[1].subBotId)
        assertEquals("store-tester", tasks[2].subBotId)
        val angles = SubagentOrchestrator.splitAngles("topic", listOf("trim the video"))
        assertEquals("store-videoeditor", angles.single().subBotId)
    }

    @Test fun aggregateCollectsAllResultsFromItself() {
        val results = listOf(
            WorkerResult("w1", "w1", facts = listOf(WorkerFact("Fact one", "https://a.example", "w1")),
                role = WorkerRole.CODER, subBotId = "store-coder", subBotName = "Coder"),
            WorkerResult("w2", "w2", facts = listOf(WorkerFact("Fact two", "https://b.example", "w2")),
                role = WorkerRole.RESEARCHER, subBotId = "store-researcher", subBotName = "Researcher"),
            WorkerResult("w3", "w3", failed = "search failed", role = WorkerRole.TESTER, subBotId = "store-tester", subBotName = "Tester"),
        )
        val merged = SubagentOrchestrator.merge(results)
        val text = XBotSpawn.aggregate("Do the job", merged, "Verified")
        assertTrue(text.contains("Fact one"))
        assertTrue(text.contains("Fact two"))
        assertTrue(text.contains("Coder"))
        assertTrue(text.contains("Researcher"))
        assertTrue(text.contains("Verified"))
        assertTrue(text.contains("Parts not covered"))
        assertTrue(text.contains("search failed"))
    }

    @Test fun logoPrefixesParse() {
        assertTrue(BotLogo.parse("video:/tmp/clip.mp4") is BotLogo.Video)
        assertEquals("/tmp/clip.mp4", (BotLogo.parse("video:/tmp/clip.mp4") as BotLogo.Video).path)
        assertTrue(BotLogo.parse("3d:bronze") is BotLogo.ThreeD)
        assertTrue(BotLogo.parse("3d:nope") is BotLogo.None)
        assertTrue(BotLogo.parse("file:/tmp/pic.jpg") is BotLogo.Custom)
        assertTrue(BotLogo.parse("builtin:brain") is BotLogo.Builtin)
        assertTrue(BotLogo.parse("") is BotLogo.None)
        assertEquals("video:/tmp/clip.mp4", BotLogo.video("/tmp/clip.mp4"))
        assertEquals("3d:navy", BotLogo.threeD("navy"))
        assertEquals(5, BotLogo.THREE_D.size)
        assertTrue(BotLogo.THREE_D.map { it.id }.contains("bronze"))
    }

    @Test fun swarmCapsUnchanged() {
        // Safety caps are kept exactly as before — X Bot fans out, but never unbounded.
        assertEquals(4, SubagentLimits.MAX_WORKERS)
        assertEquals(8, SubagentLimits.MAX_STEPS_PER_WORKER)
        assertEquals(2, SubagentLimits.MAX_DEPTH)
        assertEquals(6, SubagentLimits.MAX_WORKERS_PER_RUN)
        assertEquals(2, SubagentLimits.MAX_SPAWN_PER_WORKER)
    }

    @Test fun workerProfileOverridesNameAndLogo() {
        val reg = BotRegistry()
        val run = BotRun("r1-w1", BotKind.WORKER, parentId = "conv-x", spawnedBy = null,
            workerId = "w1", role = WorkerRole.CODER, task = "Write the file",
            status = BotStatus.WORKING, stepsUsed = 2, summary = "", conversationId = "worker-r1-w1",
            depth = 0, startedAt = 1L, updatedAt = 1L, subBotId = "store-coder", subBotName = "Coder")
        // Roster fallback first: the specialist's own name and logo.
        assertEquals("Coder", reg.displayName(run))
        assertEquals(BotCatalog.byId("store-coder")!!.logo, reg.displayLogo(run))
        // Then the user's edit wins.
        reg.setWorkerProfile("r1-w1", "Code Boss", "video:/tmp/boss.mp4")
        assertEquals("Code Boss", reg.displayName(run))
        assertEquals("video:/tmp/boss.mp4", reg.displayLogo(run))
        // A plain role worker with no tag falls back to its role label.
        val plain = run.copy(id = "r1-w2", subBotId = "", subBotName = "", role = WorkerRole.TESTER)
        assertEquals("Tester", reg.displayName(plain))
        assertEquals("", reg.displayLogo(plain))
    }
}
