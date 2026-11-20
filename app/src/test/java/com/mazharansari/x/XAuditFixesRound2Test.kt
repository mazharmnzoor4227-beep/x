package com.mazharansari.x

import androidx.test.core.app.ApplicationProvider
import com.mazharansari.x.agent.IntentGate
import com.mazharansari.x.agent.SaveIdeaTool
import com.mazharansari.x.agent.SetGoalTool
import com.mazharansari.x.agent.ToolCatalog
import com.mazharansari.x.agent.ToolContext
import com.mazharansari.x.agent.Workspace
import com.mazharansari.x.agent.ideasGoalsTools
import com.mazharansari.x.data.BotCatalog
import com.mazharansari.x.data.GoalsStore
import com.mazharansari.x.data.IdeasStore
import com.mazharansari.x.data.switchesForBot
import com.mazharansari.x.livecall.LiveCallProtocol
import com.mazharansari.x.local.LocalToolUse
import com.mazharansari.x.skills.SkillRepo
import com.mazharansari.x.update.AppUpdater
import com.mazharansari.x.update.UpdateRules
import com.mazharansari.x.update.UpdateState
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket

/**
 * Blanket audit-fix round 2, 2026-11-20 (docs/X-AUDIT-2026-11-20.md addendum):
 * save_idea / set_goal tools, the 6 skill packs' support files, X's own
 * GitHub-releases update feed, the live-call WhatsApp promise, and the two
 * re-audit finds. Every test fails on the old code and passes on the new.
 * Robolectric (assets + stores need a Context); no network — the update
 * tests run against a local HTTP server, like AppUpdateTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = AxionApp::class)
class XAuditFixesRound2Test {
    private val app get() = ApplicationProvider.getApplicationContext<AxionApp>()
    private val sha = "ab".repeat(32)

    @Before fun cleanStores() {
        File(app.filesDir, "ideas.json").delete()
        File(app.filesDir, "goals.json").delete()
    }

    private fun ctx() = ToolContext(
        Workspace(File(app.filesDir, "workspace-test")),
        SkillRepo(app, File(app.filesDir, "skills-test")),
    )

    // ── 1. save_idea / set_goal ─────────────────────────────────────────────

    @Test fun saveIdeaWritesToTheIdeasStore() = runBlocking {
        val ideas = IdeasStore(app)
        val out = SaveIdeaTool(ideas).run(buildJsonObject { put("text", "Solar charger for the shop"); put("area", "Store") }, ctx())
        assertFalse(out.isError)
        assertTrue("result must say it is in the Ideas tab: ${out.forModel}", out.forModel.contains("Ideas tab"))
        assertEquals(listOf("Solar charger for the shop"), ideas.ideas.value.map { it.text })
        assertEquals("Store", ideas.ideas.value.single().area)
    }

    @Test fun saveIdeaBlankIsAnErrorAndSavesNothing() = runBlocking {
        val ideas = IdeasStore(app)
        val out = SaveIdeaTool(ideas).run(buildJsonObject { put("text", "   ") }, ctx())
        assertTrue(out.isError)
        assertTrue(ideas.ideas.value.isEmpty())
    }

    @Test fun saveIdeaUnknownAreaFoldsIntoYou() = runBlocking {
        val ideas = IdeasStore(app)
        SaveIdeaTool(ideas).run(buildJsonObject { put("text", "New kettle"); put("area", "Shop") }, ctx())
        assertEquals("You", ideas.ideas.value.single().area)
    }

    @Test fun setGoalWritesToTheGoalsStore() = runBlocking {
        val goals = GoalsStore(app)
        val out = SetGoalTool(goals).run(buildJsonObject {
            put("title", "Learn driving")
            put("steps", "Book lessons\nPractice parking")
        }, ctx())
        assertFalse(out.isError)
        assertTrue("result must say it is in the Goals tab: ${out.forModel}", out.forModel.contains("Goals tab"))
        val g = goals.goals.value.single()
        assertEquals("Learn driving", g.title)
        assertEquals(listOf("Book lessons", "Practice parking"), g.steps.map { it.text })
        assertEquals(0f, g.progress)
    }

    @Test fun setGoalBlankTitleIsAnError() = runBlocking {
        val goals = GoalsStore(app)
        assertTrue(SetGoalTool(goals).run(buildJsonObject { put("title", "  ") }, ctx()).isError)
        assertTrue(goals.goals.value.isEmpty())
    }

    @Test fun ideasGoalsFactoryReturnsBothTools() {
        val tools = ideasGoalsTools(IdeasStore(app), GoalsStore(app))
        assertEquals(listOf("save_idea", "set_goal"), tools.map { it.spec.name })
    }

    @Test fun ideasGroupIsInTheCatalogAndOnByDefault() {
        val g = ToolCatalog.groups.firstOrNull { it.id == "ideas" }
        assertNotNull("no 'ideas' group in ToolCatalog", g)
        assertEquals(setOf("save_idea", "set_goal"), g!!.tools)
        assertTrue(ToolCatalog.isEnabled(g, emptyMap()))
        assertTrue(ToolCatalog.isEnabled("save_idea", emptyMap()))
        assertTrue(ToolCatalog.isEnabled("set_goal", emptyMap()))
    }

    @Test fun ideaCatcherMayUseTheIdeasGroupAndOthersMayNot() {
        val catcher = BotCatalog.seeds.first { it.id == "store-ideacatcher" }
        assertTrue(switchesForBot(catcher)["ideas"] == true)
        val docWriter = BotCatalog.seeds.first { it.id == "store-docwriter" }
        assertTrue("a bot whose allow-list omits ideas must not get the group",
            switchesForBot(docWriter)["ideas"] == false)
    }

    @Test fun saveIdeaAndSetGoalAreOfferedInPlainChat() {
        assertTrue(IntentGate.lookupTools.contains("save_idea"))
        assertTrue(IntentGate.lookupTools.contains("set_goal"))
        assertEquals(IntentGate.lookupTools, IntentGate.chatTools)
        // The catalog-vs-gate invariant: the chat list is exactly the web +
        // browser + learning + research + schedule + ideas groups.
        val expected = listOf("web", "browser", "learning", "research", "schedule", "ideas")
            .flatMap { id -> ToolCatalog.groups.first { it.id == id }.tools }.toSet()
        assertEquals(expected, IntentGate.lookupTools)
    }

    @Test fun localRunnerAllowListIncludesTheNewTools() {
        assertTrue(LocalToolUse.ALLOWED_TOOLS.contains("save_idea"))
        assertTrue(LocalToolUse.ALLOWED_TOOLS.contains("set_goal"))
    }

    // ── 2. skill support files ──────────────────────────────────────────────

    private fun skillText(pack: String): String =
        app.assets.open("skills/$pack/SKILL.md").bufferedReader().readText()

    private fun citedFiles(pack: String): List<String> =
        Regex("""(references|scripts|lib)/[A-Za-z0-9_./-]+""").findAll(skillText(pack))
            .map { it.value }.distinct().toList()

    @Test fun everyCitedSupportFileResolvesInAssets() {
        // The 5 packs the audit named (plus every other pack, so a new dangling
        // citation can never slip in unnoticed).
        val packs = (app.assets.list("skills") ?: emptyArray()).filter { it != "HERMES-NOTICE.txt" && !it.endsWith(".txt") }
        assertTrue("expected 30 skill packs, found ${packs.size}", packs.size == 30)
        for (pack in packs) {
            for (rel in citedFiles(pack)) {
                // A Node require path (lib/helpers) resolves to its .js file.
                val path = if (rel == "lib/helpers") "lib/helpers.js" else rel
                try {
                    app.assets.open("skills/$pack/$path").close()
                } catch (e: Exception) {
                    fail("skill $pack cites $rel, but skills/$pack/$path is not in assets")
                }
            }
        }
    }

    @Test fun reverseEngineeringAndroidCitesNoSupportFiles() {
        // The audit listed it; its SKILL.md cites no sibling file at all
        // (lib/<abi>/*.so is an APK-internal path, not a skill file).
        assertTrue(citedFiles("reverse-engineering-android").isEmpty())
    }

    @Test fun guidesAreInlinedInTheSkillTextItself() {
        // Only SKILL.md is ever loaded, so the procedure must be IN it, not
        // only in the bundled file.
        assertTrue(skillText("local-frontend-seo").contains("## 0. The five core ideas"))
        assertTrue(skillText("local-game-dev").contains("## 1. LICENSE TRUTH"))
        assertTrue(skillText("local-android-dev").contains("## native-android (references/native-android.md — inlined)"))
        assertTrue(skillText("local-shopify-development").contains("### App development (references/app-development.md)"))
        assertTrue(skillText("local-playwright-skill").contains("## On X (phone) — set up first, once"))
        assertFalse("playwright must not reference a skill dir that does not exist on the phone",
            skillText("local-playwright-skill").contains("\$SKILL_DIR"))
    }

    @Test fun shopifyScriptsAreHonestlyNotedNotRunnable() {
        val t = skillText("local-shopify-development")
        assertTrue(t.contains("not directly runnable"))
        assertTrue(t.contains("shopify app init"))
    }

    // ── 3. X's own update feed (GitHub releases) ────────────────────────────

    @Test fun defaultFeedIsXsOwnGitHubReleasesAndNeverAxion() {
        assertEquals("https://api.github.com/repos/mazharmnzoor4227-beep/x/releases/latest", UpdateRules.GITHUB_RELEASES_URL)
        assertEquals(listOf(UpdateRules.GITHUB_RELEASES_URL), UpdateRules.FEEDS)
        assertFalse(UpdateRules.FEEDS.any { "axion" in it || "clawora" in it || "sslip" in it })
    }

    private fun releaseJson(
        tag: String = "v1.1",
        body: String = "```json\n{\"versionCode\": 2, \"sha256\": \"${"ab".repeat(32)}\", \"notes\": \"New things\"}\n```\n",
        apkUrl: String = "https://example.invalid/X-1.1.apk",
        shaAsset: Boolean = false,
    ): String {
        val assets = buildString {
            append("""{"name":"X-1.1.apk","browser_download_url":"$apkUrl","size":17000000}""")
            if (shaAsset) append(""",{"name":"X-1.1.apk.sha256","browser_download_url":"https://example.invalid/X-1.1.apk.sha256","size":80}""")
        }
        // The body carries real newlines (a fenced JSON block), like a real release.
        val bodyEscaped = body.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        return """{"tag_name":"$tag","name":"X 1.1","body":"$bodyEscaped","assets":[$assets]}"""
    }

    @Test fun parseGitHubReleaseReadsTagBodyAndApk() {
        val rel = UpdateRules.parseGitHubRelease(releaseJson())
        assertEquals("1.1", rel.versionName)
        assertEquals(2, rel.versionCode)
        assertEquals(sha, rel.sha256)
        assertEquals("https://example.invalid/X-1.1.apk", rel.apkUrl)
        assertEquals(17_000_000L, rel.apkSize)
        assertEquals("New things", rel.notes)
    }

    @Test fun parseGitHubReleaseFindsShaAssetWhenNotesHaveNoHash() {
        // Notes carry the versionCode but no hash; the .sha256 asset is the hash source.
        val rel = UpdateRules.parseGitHubRelease(
            releaseJson(body = "```json\n{\"versionCode\": 2}\n```\n", shaAsset = true))
        assertNull(rel.sha256)
        assertEquals("https://example.invalid/X-1.1.apk.sha256", rel.shaAssetUrl)
        assertEquals(2, rel.versionCode)
    }

    @Test fun parseGitHubReleaseRejectsBadShapes() {
        for (bad in listOf(
            """{"tag_name":"","body":""}""",
            """not json""",
            releaseJson(tag = "v1.1", body = "no json block here"),
        )) {
            // A release with no tag, or no versionCode in its notes, cannot be
            // offered — the parse either throws or leaves the code null.
            val rel = runCatching { UpdateRules.parseGitHubRelease(bad) }.getOrNull()
            if (rel != null) {
                assertNull("release without a versionCode must not yield one: $bad", rel.versionCode)
                try { UpdateRules.githubToUpdateInfo(rel, sha); fail("accepted $bad") }
                catch (_: java.io.IOException) {}
            }
        }
    }

    @Test fun githubToUpdateInfoRefusesMissingApkOrHash() {
        val rel = UpdateRules.parseGitHubRelease(releaseJson())
        try { UpdateRules.githubToUpdateInfo(rel.copy(apkUrl = null), sha); fail("accepted a release with no APK") }
        catch (_: java.io.IOException) {}
        try { UpdateRules.githubToUpdateInfo(rel, "xyz"); fail("accepted a bad SHA-256") }
        catch (_: java.io.IOException) {}
    }

    // Local HTTP server for the check() tests (same shape as AppUpdateTest).
    private lateinit var srv: ServerSocket
    private val routes = HashMap<String, Pair<Int, ByteArray>>()
    private val base get() = "http://127.0.0.1:${srv.localPort}"

    @Before fun upServer() {
        srv = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        Thread {
            while (!srv.isClosed) {
                val s = runCatching { srv.accept() }.getOrNull() ?: break
                Thread {
                    s.use { so ->
                        val r = so.getInputStream().bufferedReader(); val line = r.readLine() ?: return@use
                        while (true) { val h = r.readLine() ?: break; if (h.isEmpty()) break }
                        val path = line.split(" ")[1].substringBefore('?')
                        val (code, body) = routes[path] ?: (404 to "nope".toByteArray())
                        val os = so.getOutputStream()
                        os.write("HTTP/1.1 $code X\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray()); os.write(body); os.flush()
                    }
                }.start()
            }
        }.apply { isDaemon = true; start() }
    }
    @After fun downServer() { srv.close() }

    private fun updater(feeds: List<String>, code: Int = 1) =
        AppUpdater(app, feeds, currentCode = code, certReader = { listOf(UpdateRules.CERT_SHA256) })

    @Test fun checkHitsTheGitHubReleasesFeedAndOffersTheRelease() = runBlocking {
        routes["/releases/latest"] = 200 to releaseJson().toByteArray()
        val st = updater(listOf("$base/releases/latest"), code = 1).check()
        assertTrue("$st", st is UpdateState.Available && (st as UpdateState.Available).info.versionCode == 2)
        assertEquals("1.1", (st as UpdateState.Available).info.versionName)
        assertEquals(sha, st.info.sha256)
    }

    @Test fun checkSaysUpToDateWhenTheReleaseIsCurrent() = runBlocking {
        routes["/releases/latest"] = 200 to releaseJson().toByteArray()
        assertTrue(updater(listOf("$base/releases/latest"), code = 2).check() is UpdateState.UpToDate)
    }

    @Test fun checkSaysNoReleaseWhenGitHubHasNone() = runBlocking {
        routes.clear() // the feed path 404s, like api.github.com/releases/latest with zero releases
        val st = updater(listOf("$base/releases/latest"), code = 1).check()
        assertTrue("$st", st is UpdateState.Failed && (st as UpdateState.Failed).message.contains("No X release has been published yet"))
    }

    @Test fun checkRefusesAReleaseWithNoVersionCode() = runBlocking {
        // Notes carry a hash but no versionCode: the release cannot be compared, so no offer.
        routes["/releases/latest"] = 200 to releaseJson(body = "```json\n{\"sha256\": \"${"ab".repeat(32)}\"}\n```\n").toByteArray()
        val st = updater(listOf("$base/releases/latest"), code = 1).check()
        assertTrue("$st", st is UpdateState.Failed && (st as UpdateState.Failed).message.contains("versionCode"))
    }

    // ── 4. live-call honesty ────────────────────────────────────────────────

    @Test fun liveCallGuidanceNeverPromisesPhoneActionsOrWhatsApp() {
        val g = LiveCallProtocol.TOOL_GUIDANCE
        assertFalse("must not tell the model to send WhatsApp messages", g.contains("send a WhatsApp"))
        assertFalse("there are no phone tools in a call", g.contains("phone tools"))
        assertFalse("there is no CONFIRM flow in a call", g.contains("CONFIRM"))
        assertTrue("must say plainly it cannot do it from a call", g.contains("cannot open apps"))
        assertTrue("must offer to draft instead", g.contains("draft"))
        assertTrue("end_call guidance must stay", g.contains("end_call"))
    }

    // ── 5. re-audit finds ───────────────────────────────────────────────────
    // The two re-audit fixes are UI text / tap wiring (V04Screens "Builds" row:
    // empty {} onClick removed so the row is not tappable; OwnModelScreens
    // banner: local models do run basic file/shell tools in Local mode).
    // They are verified by reading, not by a unit test — a test asserting its
    // own literal would prove nothing, so none is faked here.
}
