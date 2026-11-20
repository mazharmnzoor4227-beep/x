package com.mazharansari.x

import androidx.test.core.app.ApplicationProvider
import com.mazharansari.x.skills.SkillRepo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * Skills download + install (Settings → Skills), against a fake GitHub — no network.
 * The fake records every URL the repo requests, so the tests prove the app asks for the
 * right URLs (built by the same companion functions the app uses) and never marks a
 * failed download as installed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = AxionApp::class)
class SkillRepoTest {

    /** Fake GitHub: serves repo/tree JSON and raw SKILL.md files, records every request. */
    private class FakeFetcher(
        val files: Map<String, String> = emptyMap(),
        val tree: String = "",
        val repoJson: String = "{\"default_branch\":\"main\"}",
        val failUrls: Map<String, String> = emptyMap(),
    ) {
        val requested = mutableListOf<String>()
        val fetch: (String) -> String = { url ->
            requested += url
            failUrls[url]?.let { throw IllegalStateException(it) }
            when {
                url.contains("/git/trees/") -> tree
                url.contains("api.github.com/repos/") -> repoJson
                else -> files[url] ?: throw IllegalStateException("Not found on GitHub (HTTP 404) — check the repo URL and branch.")
            }
        }
    }

    private fun treeJson(vararg paths: String): String {
        val items = paths.joinToString(",") { "{\"path\":\"$it\",\"type\":\"blob\"}" }
        return "{\"truncated\":false,\"tree\":[$items]}"
    }

    private fun skillMd(name: String, desc: String) =
        "---\nname: $name\ndescription: $desc\n---\n\nDo the $name thing. These are real instructions, long enough to count as a skill.\n"

    private fun newDir(): File =
        File(System.getProperty("java.io.tmpdir"), "skills-test-${UUID.randomUUID()}").apply { mkdirs() }

    private val ecc = "https://github.com/affaan-m/ECC"

    // ── URL construction + scan ──────────────────────────────────────────────

    @Test fun scanUsesOnlyTwoApiCallsAndBuildsRealUrls() {
        val tree = treeJson(
            ".agents/skills/agent-sort/SKILL.md",
            "skills/api-design/SKILL.md",
            "docs/api-design/SKILL.md",
            "skills/api-design/notes.md",
        )
        val fake = FakeFetcher(tree = tree)
        val repo = SkillRepo(null, newDir(), fake.fetch)
        val found = runBlocking { repo.scanGitHub(ecc) }

        assertEquals(listOf("agent-sort", "api-design", "api-design"), found.map { it.name })
        assertEquals(
            listOf(".agents/skills/agent-sort/SKILL.md", "skills/api-design/SKILL.md", "docs/api-design/SKILL.md"),
            found.map { it.repoPath },
        )
        // A scan is exactly two API calls — no raw file is fetched here (the old code
        // downloaded every SKILL.md during the scan, then downloaded them all again).
        assertEquals(
            listOf(SkillRepo.apiRepoUrl("affaan-m", "ECC"), SkillRepo.apiTreeUrl("affaan-m", "ECC", "main")),
            fake.requested,
        )
        // Raw URLs are well-formed and built by the same code the app uses.
        assertEquals(
            "https://raw.githubusercontent.com/affaan-m/ECC/main/skills/api-design/SKILL.md",
            SkillRepo.rawUrl("affaan-m", "ECC", "main", "skills/api-design/SKILL.md"),
        )
        assertTrue(found.all { it.rawUrl == SkillRepo.rawUrl("affaan-m", "ECC", "main", it.repoPath) })
    }

    @Test fun scanSubFolderUrlUsesBranchFromUrlAndFiltersPaths() {
        val tree = treeJson("skills/api-design/SKILL.md", "docs/api-design/SKILL.md")
        val fake = FakeFetcher(tree = tree)
        val repo = SkillRepo(null, newDir(), fake.fetch)
        val found = runBlocking { repo.scanGitHub("$ecc/tree/main/skills") }

        assertEquals(listOf("skills/api-design/SKILL.md"), found.map { it.repoPath })
        // Branch came from the URL, so no repo call was needed — just the tree.
        assertEquals(listOf(SkillRepo.apiTreeUrl("affaan-m", "ECC", "main")), fake.requested)
    }

    @Test fun scanRateLimitIsReportedAsRateLimit() {
        val fake = FakeFetcher(
            tree = treeJson("skills/api-design/SKILL.md"),
            failUrls = mapOf(
                SkillRepo.apiRepoUrl("affaan-m", "ECC") to
                    "GitHub rate limit reached (HTTP 403) — GitHub allows only 60 unauthenticated requests per hour from this network. Try again in a little while.",
            ),
        )
        val repo = SkillRepo(null, newDir(), fake.fetch)
        val e = assertThrows(IllegalStateException::class.java) { runBlocking { repo.scanGitHub(ecc) } }
        assertTrue(e.message!!.contains("rate limit", ignoreCase = true))
        assertTrue(e.message!!.contains("60"))
    }

    @Test fun scanTruncatedTreeIsAnErrorNotAPartialList() {
        val fake = FakeFetcher(tree = "{\"truncated\":true,\"tree\":[{\"path\":\"skills/a/SKILL.md\",\"type\":\"blob\"}]}")
        val repo = SkillRepo(null, newDir(), fake.fetch)
        val e = assertThrows(IllegalStateException::class.java) { runBlocking { repo.scanGitHub(ecc) } }
        assertTrue(e.message!!.contains("too large", ignoreCase = true))
    }

    // ── Install round-trip ───────────────────────────────────────────────────

    @Test fun downloadInstallListUseUninstallRoundTripSurvivesRestart() {
        val dir = newDir()
        val files = mapOf(
            SkillRepo.rawUrl("affaan-m", "ECC", "main", "skills/pdf-plus/SKILL.md") to skillMd("pdf-plus", "Reads PDFs better"),
            SkillRepo.rawUrl("affaan-m", "ECC", "main", "skills/video-plus/SKILL.md") to skillMd("video-plus", "Edits video"),
        )
        val fake = FakeFetcher(files = files, tree = treeJson("skills/pdf-plus/SKILL.md", "skills/video-plus/SKILL.md"))
        val repo = SkillRepo(null, dir, fake.fetch)

        val found = runBlocking { repo.scanGitHub(ecc) }
        val result = runBlocking { repo.install(found, ecc) }
        assertTrue(result.failed.isEmpty())
        assertEquals(listOf("pdf-plus", "video-plus"), result.added.map { it.name }.sorted())

        // Files landed on disk, one folder per skill, and the agent can load them.
        assertTrue(File(dir, "skills_pdf-plus/SKILL.md").exists())
        assertTrue(File(dir, "skills_video-plus/SKILL.md").exists())
        assertTrue(repo.body("pdf-plus")!!.contains("Reads PDFs better"))
        assertEquals(2, repo.skills.value.count { it.source == ecc })

        // The installed set (and the on/off switch) survives a store reload = app restart.
        repo.setEnabled("pdf-plus", false)
        val reloaded = SkillRepo(null, dir, fake.fetch)
        assertEquals(2, reloaded.skills.value.count { it.source == ecc })
        assertFalse(reloaded.skills.value.first { it.name == "pdf-plus" }.enabled)
        assertTrue(reloaded.skills.value.first { it.name == "video-plus" }.enabled)
        assertTrue(reloaded.body("pdf-plus")!!.contains("Reads PDFs better"))

        // Uninstall removes the list entry AND the files, and stays removed after restart.
        reloaded.remove("pdf-plus")
        assertNull(reloaded.skills.value.firstOrNull { it.name == "pdf-plus" })
        assertFalse(File(dir, "skills_pdf-plus").exists())
        assertFalse(File(dir, "skills.json").readText().contains("pdf-plus"))
        val reloadedAgain = SkillRepo(null, dir, fake.fetch)
        assertNull(reloadedAgain.skills.value.firstOrNull { it.name == "pdf-plus" })
        assertEquals(1, reloadedAgain.skills.value.count { it.source == ecc })
    }

    @Test fun builtInToggleSurvivesRestart() {
        val app = ApplicationProvider.getApplicationContext<AxionApp>()
        val dir = File(app.filesDir, "skills-toggle-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val repo = SkillRepo(app, dir)
            val pdf = repo.skills.value.firstOrNull { it.name == "pdf" }
            assertNotNull("built-in pdf skill should load from assets", pdf)
            assertTrue(pdf!!.enabled)
            repo.setEnabled("pdf", false)
            val reloaded = SkillRepo(app, dir)
            val pdf2 = reloaded.skills.value.firstOrNull { it.name == "pdf" }
            assertNotNull(pdf2)
            assertFalse("disabling a built-in must survive a restart", pdf2!!.enabled)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun installedSkillWithDeletedFilesIsNotListedAfterRestart() {
        val dir = newDir()
        val files = mapOf(
            SkillRepo.rawUrl("affaan-m", "ECC", "main", "skills/pdf-plus/SKILL.md") to skillMd("pdf-plus", "Reads PDFs better"),
        )
        val fake = FakeFetcher(files = files, tree = treeJson("skills/pdf-plus/SKILL.md"))
        val repo = SkillRepo(null, dir, fake.fetch)
        val found = runBlocking { repo.scanGitHub(ecc) }
        runBlocking { repo.install(found, ecc) }
        assertNotNull(repo.skills.value.firstOrNull { it.name == "pdf-plus" })
        File(dir, "skills_pdf-plus").deleteRecursively()
        val reloaded = SkillRepo(null, dir, fake.fetch)
        assertNull("a skill whose files are gone is not installed any more", reloaded.skills.value.firstOrNull { it.name == "pdf-plus" })
    }

    // ── Failures are failures ────────────────────────────────────────────────

    @Test fun failedDownloadIsNeverMarkedInstalled() {
        val dir = newDir()
        val good = SkillRepo.rawUrl("affaan-m", "ECC", "main", "skills/good/SKILL.md")
        val empty = SkillRepo.rawUrl("affaan-m", "ECC", "main", "skills/empty/SKILL.md")
        val html = SkillRepo.rawUrl("affaan-m", "ECC", "main", "skills/html/SKILL.md")
        val files = mapOf(
            good to skillMd("good", "The one that works"),
            empty to "",
            html to "<!DOCTYPE html><html><body>Not found</body></html>",
        )
        val fake = FakeFetcher(files = files, tree = treeJson("skills/good/SKILL.md", "skills/empty/SKILL.md", "skills/html/SKILL.md", "skills/missing/SKILL.md"))
        val repo = SkillRepo(null, dir, fake.fetch)

        val found = runBlocking { repo.scanGitHub(ecc) }
        assertEquals(4, found.size)
        val result = runBlocking { repo.install(found, ecc) }

        assertEquals(listOf("good"), result.added.map { it.name })
        assertEquals(3, result.failed.size)
        val reasons = result.failed.joinToString(" | ") { it.reason }
        assertTrue(reasons.contains("404"))
        assertTrue(reasons.contains("too short", ignoreCase = true))
        assertTrue(reasons.contains("web page", ignoreCase = true))
        // Nothing failed is listed, loadable, or persisted — even after a restart.
        assertEquals(listOf("good"), repo.skills.value.map { it.name })
        assertNull(repo.body("missing"))
        assertNull(repo.body("empty"))
        val reloaded = SkillRepo(null, dir, fake.fetch)
        assertEquals(listOf("good"), reloaded.skills.value.map { it.name })
    }

    @Test fun duplicateSkillNamesAreDeduped() {
        val dir = newDir()
        val files = mapOf(
            SkillRepo.rawUrl("affaan-m", "ECC", "main", "skills/dup-one/SKILL.md") to skillMd("same-name", "First copy"),
            SkillRepo.rawUrl("affaan-m", "ECC", "main", "docs/dup-two/SKILL.md") to skillMd("same-name", "Second copy"),
        )
        val fake = FakeFetcher(files = files, tree = treeJson("skills/dup-one/SKILL.md", "docs/dup-two/SKILL.md"))
        val repo = SkillRepo(null, dir, fake.fetch)

        val found = runBlocking { repo.scanGitHub(ecc) }
        val result = runBlocking { repo.install(found, ecc) }
        assertEquals(1, result.added.size)
        assertEquals(1, result.failed.size)
        assertTrue(result.failed[0].reason.contains("same name", ignoreCase = true))
        assertEquals(1, repo.skills.value.count { it.name == "same-name" })
        // Exactly one folder on disk: the loser's files were never written over the winner's.
        assertEquals(1, dir.listFiles()!!.count { it.isDirectory })
        assertTrue(repo.body("same-name")!!.contains("First copy"))
    }
}
