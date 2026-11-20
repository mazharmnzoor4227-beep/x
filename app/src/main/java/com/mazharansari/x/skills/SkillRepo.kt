package com.mazharansari.x.skills

import android.content.Context
import com.mazharansari.x.llm.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.Request
import java.io.File

@Serializable
data class Skill(
    val name: String,
    val description: String,
    val source: String,          // "built-in" or the GitHub URL it came from
    val path: String,            // asset path or file path of SKILL.md
    val enabled: Boolean = true,
)

/** One download that did not become an installed skill — shown to the user, never hidden. */
data class FailedSkill(val name: String, val reason: String)

/** Result of [SkillRepo.install]: what landed vs what failed, so the UI never reports a failed download as installed. */
data class InstallResult(val added: List<Skill>, val failed: List<FailedSkill>)

/** SKILL.md skills (Anthropic format: YAML front matter with name + description, then instructions). */
class SkillRepo(private val ctx: Context?, private val dir: File, private val fetcher: ((String) -> String)? = null) {
    private val index = File(dir, "skills.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val _skills = MutableStateFlow<List<Skill>>(emptyList())
    val skills: StateFlow<List<Skill>> = _skills

    init {
        dir.mkdirs()
        val persisted = runCatching { json.decodeFromString<List<Skill>>(index.readText()) }.getOrDefault(emptyList())
        val names = runCatching { ctx?.assets?.list("skills")?.toList() }.getOrNull().orEmpty().ifEmpty { BUILT_IN }
        val builtIn = names.mapNotNull { n ->
            // .use: every open is closed — the old readText() without a close
            // leaked one asset stream per built-in per SkillRepo construction,
            // and a long shared-JVM suite opens hundreds (every Agent/Graph
            // build constructs one), which can exhaust file descriptors and
            // make a later open fail so the built-in silently drops out.
            val text = runCatching { ctx!!.assets.open("skills/$n/SKILL.md").bufferedReader().use { it.readText() } }.getOrNull() ?: return@mapNotNull null
            val (name, desc) = frontMatter(text, n)
            Skill(name, desc, "built-in", "asset:skills/$n/SKILL.md")
        }
        // A built-in's on/off switch is remembered across restarts (its persisted record
        // carries the enabled flag); a user-installed skill with the same name still wins.
        val persistedByName = persisted.associateBy { it.name }
        val mergedBuiltIn = builtIn.map { b ->
            persistedByName[b.name]?.takeIf { it.source == "built-in" }?.let { b.copy(enabled = it.enabled) } ?: b
        }
        // A file-backed skill whose SKILL.md is gone (deleted outside the app) is not
        // installed any more — drop it instead of listing a skill that cannot load.
        val installed = persisted.filter { it.source != "built-in" }
            .filter { it.path.startsWith("asset:") || File(it.path).exists() }
        _skills.value = mergedBuiltIn.filter { b -> installed.none { it.name == b.name } } + installed
        if (installed.size != persisted.count { it.source != "built-in" }) persist()
    }

    fun body(name: String): String? {
        fun norm(x: String) = x.lowercase().replace(Regex("[^a-z0-9]"), "")
        val s = _skills.value.firstOrNull { it.name.equals(name, true) } ?: _skills.value.firstOrNull { norm(it.name) == norm(name) }
            ?: _skills.value.firstOrNull { norm(it.name).contains(norm(name)) || norm(name).contains(norm(it.name)) } ?: return null
        return if (s.path.startsWith("asset:")) ctx?.assets?.open(s.path.removePrefix("asset:"))?.bufferedReader()?.use { it.readText() }
        else File(s.path).takeIf { it.exists() }?.readText()
    }

    fun setEnabled(name: String, on: Boolean) { _skills.value = _skills.value.map { if (it.name == name) it.copy(enabled = on) else it }; persist() }

    /** Removes a user-installed skill (never a built-in) from the list AND deletes its folder from disk. */
    fun remove(name: String) {
        val gone = _skills.value.filter { it.name == name && it.source != "built-in" }
        if (gone.isEmpty()) return
        _skills.value = _skills.value.filterNot { it.name == name && it.source != "built-in" }
        persist()
        gone.forEach { deleteSkillFolder(it) }
    }

    /**
     * v0.10 (Hermes learning loop): the agent writes its OWN skills with the skill_create /
     * skill_update tools. Learned skills live in the same folder format as imported ones, are
     * tagged source = [LEARNED] so the UI can badge them, and are the only skills the agent may
     * edit or delete — built-in and user-installed skills are never touched by the agent.
     * [fullText] is the complete SKILL.md (front matter included). Returns the saved skill;
     * throws with a plain message when the name clashes with a skill the agent does not own.
     */
    fun saveLearned(name: String, fullText: String, updating: Boolean): Skill {
        val text = fullText.replace("\r\n", "\n").trim()
        require(text.length >= 20) { "The skill is too short — write real instructions." }
        val (parsedName, desc) = frontMatter(text, name)
        val existing = _skills.value.firstOrNull { it.name.equals(parsedName, ignoreCase = true) }
        if (existing != null && existing.source != LEARNED)
            error("A skill named '${existing.name}' already exists and was not written by you (source: ${existing.source}). Pick a different name.")
        if (existing == null && updating) error("No learned skill named '$parsedName' — create it with skill_create first.")
        if (existing != null && !updating) error("You already wrote a skill named '$parsedName' — improve it with skill_update instead of creating a second one.")
        val folder = File(dir, safe(parsedName)).apply { mkdirs() }
        val file = File(folder, "SKILL.md"); file.writeText(text)
        val skill = Skill(parsedName, desc, LEARNED, file.absolutePath, enabled = existing?.enabled ?: true)
        _skills.value = _skills.value.filterNot { it.name.equals(parsedName, ignoreCase = true) } + skill
        persist()
        return skill
    }

    /** Deletes a skill ONLY when the agent itself wrote it (source = [LEARNED]). True when deleted. */
    fun deleteLearned(name: String): Boolean {
        val s = _skills.value.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return false
        if (s.source != LEARNED) return false
        _skills.value = _skills.value.filterNot { it.name == s.name }
        runCatching { File(s.path).parentFile?.deleteRecursively() }
        persist()
        return true
    }

    fun isLearned(name: String): Boolean = _skills.value.firstOrNull { it.name.equals(name, ignoreCase = true) }?.source == LEARNED
    private fun persist() = index.writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Skill.serializer()), _skills.value))


    /**
     * v0.3: add skills from a file the user picks — a ZIP (one or many SKILL.md folders, every file in each folder kept),
     * a single SKILL.md / .md / .txt, or a PDF (its text becomes the skill's instructions).
     * Returns what was installed; throws with a plain message when nothing usable was inside.
     */
    suspend fun importFile(fileName: String, bytes: ByteArray): List<Skill> = withContext(Dispatchers.IO) {
        val lower = fileName.lowercase()
        val added = when {
            lower.endsWith(".zip") || (bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) -> importZip(fileName, bytes)
            lower.endsWith(".pdf") || (bytes.size > 4 && String(bytes, 0, 4, Charsets.ISO_8859_1) == "%PDF") -> listOf(importText(fileName.substringBeforeLast('.'), pdfText(bytes), "upload:$fileName"))
            else -> listOf(importText(fileName.substringBeforeLast('.').let { if (it.equals("SKILL", true)) "uploaded-skill" else it }, bytes.toString(Charsets.UTF_8), "upload:$fileName"))
        }
        if (added.isEmpty()) error("No skills found in $fileName")
        _skills.value = _skills.value.filterNot { s -> added.any { it.name == s.name } } + added
        persist(); added
    }

    private fun safe(n: String) = n.replace(Regex("[^A-Za-z0-9._-]"), "_").take(64).let { if (it.isBlank() || it == "." || it == "..") "skill" else it }

    /** Deletes a file-backed skill's folder, but only when it really lives under [dir] — never outside it. */
    private fun deleteSkillFolder(s: Skill) {
        if (s.path.startsWith("asset:")) return
        runCatching {
            val folder = File(s.path).parentFile?.canonicalFile ?: return
            val root = dir.canonicalFile
            if (folder == root || !folder.path.startsWith(root.path + File.separator)) return
            folder.deleteRecursively()
        }
    }

    private fun importText(fallbackName: String, rawText: String, source: String): Skill {
        val text = rawText.replace("\r\n", "\n").trim()
        if (text.length < 20) error("That file has no readable text")
        val hasFm = Regex("^---\\s*\\n.*?\\n---", RegexOption.DOT_MATCHES_ALL).containsMatchIn(text)
        val (name, desc) = frontMatter(text, fallbackName)
        val body = if (hasFm) text else "---\nname: $name\ndescription: ${desc.replace("\n", " ").take(200)}\n---\n\n$text"
        val folder = File(dir, safe(name)).apply { mkdirs() }
        val file = File(folder, "SKILL.md"); file.writeText(body)
        return Skill(name, desc, source, file.absolutePath)
    }

    private fun importZip(fileName: String, bytes: ByteArray): List<Skill> {
        val entries = LinkedHashMap<String, ByteArray>()
        java.util.zip.ZipInputStream(bytes.inputStream()).use { z ->
            var total = 0L
            while (true) {
                val e = z.nextEntry ?: break
                if (e.isDirectory) continue
                val n = e.name.replace('\\', '/').trimStart('/')
                if (n.contains("../") || n.startsWith("__MACOSX") || n.substringAfterLast('/').startsWith(".")) continue
                val data = z.readBytes(); total += data.size
                if (total > 60_000_000) error("ZIP is too big (over 60 MB unpacked)")
                entries[n] = data
            }
        }
        val skillFiles = entries.keys.filter { it.substringAfterLast('/').equals("SKILL.md", true) }
        if (skillFiles.isNotEmpty()) {
            // Parse first, then keep only the first skill per name: two folders with the same
            // front-matter name must not overwrite each other's files or list twice.
            val parsed = skillFiles.map { sk ->
                val folderPrefix = sk.substringBeforeLast('/', "")
                val text = entries[sk]!!.toString(Charsets.UTF_8)
                val (name, desc) = frontMatter(text, folderPrefix.substringAfterLast('/').ifBlank { fileName.substringBeforeLast('.') })
                Triple(sk, text, name to desc)
            }
            val seen = mutableSetOf<String>()
            return parsed.filter { (_, _, nd) -> seen.add(nd.first.lowercase()) }.map { (sk, text, nd) ->
                val (name, desc) = nd
                val folderPrefix = sk.substringBeforeLast('/', "")
                val out = File(dir, safe(name)).apply { deleteRecursively(); mkdirs() }
                entries.filterKeys { folderPrefix.isEmpty() || it.startsWith("$folderPrefix/") }.forEach { (k, v) ->
                    val rel = if (folderPrefix.isEmpty()) k else k.removePrefix("$folderPrefix/")
                    if (folderPrefix.isEmpty() && skillFiles.size > 1 && rel.contains('/')) return@forEach
                    File(out, rel).apply { parentFile?.mkdirs(); writeBytes(v) }
                }
                File(out, "SKILL.md").writeText(text)
                Skill(name, desc, "upload:$fileName", File(out, "SKILL.md").absolutePath)
            }
        }
        // No SKILL.md: every .md / .txt becomes its own skill (first file per name wins).
        val seen = mutableSetOf<String>()
        return entries.filterKeys { it.endsWith(".md", true) || it.endsWith(".txt", true) }.mapNotNull { (k, v) ->
            val n = k.substringAfterLast('/').substringBeforeLast('.')
            if (!seen.add(n.lowercase())) null
            else importText(n, v.toString(Charsets.UTF_8), "upload:$fileName")
        }
    }

    private fun pdfText(bytes: ByteArray): String {
        ctx?.let { com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(it) }
        com.tom_roush.pdfbox.pdmodel.PDDocument.load(bytes).use { doc ->
            val t = com.tom_roush.pdfbox.text.PDFTextStripper().getText(doc)
            if (t.isBlank()) error("This PDF has no text layer (it's a scan) — export it as text or .md and upload that")
            return t
        }
    }

    data class Found(val name: String, val description: String, val repoPath: String, val rawUrl: String)

    /**
     * Lists every SKILL.md in a GitHub repo (or a sub-folder URL) without installing.
     * Only two GitHub API calls (repo + tree) — the file contents are NOT fetched here,
     * so a scan stays fast and cannot burn the 60/hr unauthenticated API quota on
     * hundreds of raw downloads. [Found.name] is the skill's folder name and
     * [Found.description] its repo path, so duplicate names stay distinguishable;
     * the real front-matter name/description are read at install time.
     * Throws with a plain message on a bad URL, a missing repo, a rate limit, or a
     * repo too large for GitHub to list in one go (never a silent partial list).
     */
    suspend fun scanGitHub(url: String): List<Found> = withContext(Dispatchers.IO) {
        val m = Regex("github\\.com/([^/\\s]+)/([^/\\s#?]+)(?:/tree/([^/\\s]+)(/[^\\s#?]*)?)?").find(url) ?: error("Not a GitHub repo URL")
        val owner = m.groupValues[1]; val repo = m.groupValues[2].removeSuffix(".git")
        var branch = m.groupValues[3]
        val sub = m.groupValues[4].trim('/')
        if (branch.isEmpty()) branch = fetch(apiRepoUrl(owner, repo)).let { Json.parseToJsonElement(it).jsonObject["default_branch"]?.jsonPrimitive?.content ?: "main" }
        val treeObj = Json.parseToJsonElement(fetch(apiTreeUrl(owner, repo, branch))).jsonObject
        if ((treeObj["truncated"] as? JsonPrimitive)?.booleanOrNull == true)
            error("This repo is too large for GitHub to list in one go — paste a sub-folder URL instead, e.g. $url/tree/$branch/skills")
        val paths = treeObj["tree"]?.jsonArray.orEmpty()
            .mapNotNull { it.jsonObject["path"]?.jsonPrimitive?.content }
            .filter { it.endsWith("SKILL.md", ignoreCase = true) && (sub.isEmpty() || it.startsWith("$sub/")) }
        paths.map { p ->
            val folder = p.substringBeforeLast('/', "").substringAfterLast('/')
            Found(folder.ifBlank { repo }, p, p, rawUrl(owner, repo, branch, p))
        }
    }

    /**
     * Downloads each picked skill and installs it. Every download is validated
     * (non-empty, not an HTML error page) BEFORE it is written, downloads run in
     * small parallel batches, each skill gets its own folder keyed by repo path
     * (two same-named skills from different folders can no longer overwrite each
     * other), and a same-named skill already in the batch is reported as a
     * duplicate instead of listing twice. Failures are returned in [InstallResult.failed]
     * and never marked installed; installing a skill again updates it in place.
     */
    suspend fun install(found: List<Found>, sourceUrl: String): InstallResult = withContext(Dispatchers.IO) {
        val added = mutableListOf<Skill>()
        val failed = mutableListOf<FailedSkill>()
        val batchSeen = mutableSetOf<String>()
        found.chunked(20).forEach { chunk ->
            val fetched = coroutineScope {
                chunk.map { f -> async { f to runCatching { fetch(f.rawUrl) } } }.awaitAll()
            }
            for ((f, r) in fetched) {
                val text = r.getOrNull()
                if (text == null) { failed += FailedSkill(f.name, r.exceptionOrNull()?.message ?: "Download failed"); continue }
                val clean = text.replace("\r\n", "\n").trim()
                if (clean.length < 20) { failed += FailedSkill(f.name, "Downloaded file is empty or too short — the download may have been cut off"); continue }
                if (clean.startsWith("<!DOCTYPE", true) || clean.startsWith("<html", true)) { failed += FailedSkill(f.name, "Downloaded file is not a skill (got a web page instead)"); continue }
                val (name, desc) = frontMatter(clean, f.name)
                if (!batchSeen.add(name.lowercase())) { failed += FailedSkill(name, "Another skill in this repo has the same name — only the first one was installed"); continue }
                val folder = File(dir, folderForRepoPath(f.repoPath, name)).apply { mkdirs() }
                File(folder, "SKILL.md").writeText(clean)
                added += Skill(name, desc, sourceUrl, File(folder, "SKILL.md").absolutePath)
            }
        }
        commitInstalled(added)
        InstallResult(added, failed)
    }

    /** One folder per repo path, so same-named skills from different folders never collide on disk. */
    private fun folderForRepoPath(repoPath: String, fallbackName: String): String {
        val dirPart = repoPath.substringBeforeLast('/', "")
        return if (dirPart.isBlank()) safe(fallbackName) else safe(dirPart.replace('/', '_'))
    }

    /** Replaces any same-named skill (an update), deletes the replaced skill's folder when nothing new lives in it, then persists. */
    private fun commitInstalled(added: List<Skill>) {
        if (added.isEmpty()) return
        val old = _skills.value
        val replaced = old.filter { o -> added.any { it.name.equals(o.name, ignoreCase = true) } }
        _skills.value = old.filterNot { o -> added.any { it.name.equals(o.name, ignoreCase = true) } } + added
        persist()
        replaced.forEach { r ->
            // Delete the replaced skill's folder — unless a newly added skill lives in
            // that same folder (re-installing the same repo path overwrites in place).
            val folder = File(r.path).parentFile?.absolutePath
            if (added.none { File(it.path).parentFile?.absolutePath == folder }) deleteSkillFolder(r)
        }
    }

    /** The real network fetch, or the injected fake in tests (no network in tests). */
    private fun fetch(url: String): String = fetcher?.invoke(url) ?: get(url)

    private fun get(url: String): String {
        val b = Request.Builder().url(url).header("User-Agent", Http.UA).header("Accept", "application/vnd.github+json")
        // CI runs the live GitHub import test unauthenticated and hits the 60/hr shared-IP
        // limit (HTTP 403). When a token is available (GITHUB_TOKEN in CI), authenticate so
        // the limit is 5,000/hr. No token is ever committed; on a phone this is unset.
        if (url.contains("api.github.com")) {
            (System.getenv("GITHUB_TOKEN") ?: System.getProperty("github.token"))?.takeIf { it.isNotBlank() }?.let { b.header("Authorization", "Bearer $it") }
        }
        Http.quick.newCall(b.build()).execute().use {
            if (!it.isSuccessful) {
                val code = it.code
                if (code == 403 && url.contains("api.github.com"))
                    error("GitHub rate limit reached (HTTP 403) — GitHub allows only 60 unauthenticated requests per hour from this network. Try again in a little while.")
                if (code == 429)
                    error("GitHub is throttling requests (HTTP 429) — try again in a few minutes.")
                if (code == 404)
                    error("Not found on GitHub (HTTP 404) — check the repo URL and branch.")
                error("GitHub HTTP $code")
            }
            return it.body!!.string()
        }
    }

    companion object {
        /** Source tag for skills the agent wrote itself (Settings → Skills shows them as "Learned"). */
        const val LEARNED = "learned"
        val BUILT_IN = listOf("pdf", "website-audit", "deep-research", "roman-urdu", "video",
            "agent-building", "token-efficiency", "local_verify_before_recommending",
            "local-pre-delivery-verification-gate", "local-verified-status-claims",
            "ponytail-minimal-code", "local-android-dev", "local_reference_pattern_research_gate",
            "mika-content-transcribe", "mika-documents", "mika-info-research", "mika-messaging",
            "mika-skill-authoring", "meta-astryx-design", "local-frontend-design",
            "local-frontend-seo", "local-shopify-development", "local-game-dev",
            "local-playwright-skill", "banner-design", "local-app-store-design",
            "local_external_builder_brief", "reverse-engineering-android", "delivery",
            "local-header-footer-design")

        /** URL builders — the single place download URLs are constructed (tests assert against these). */
        fun apiRepoUrl(owner: String, repo: String) = "https://api.github.com/repos/$owner/$repo"
        fun apiTreeUrl(owner: String, repo: String, branch: String) = "https://api.github.com/repos/$owner/$repo/git/trees/$branch?recursive=1"
        fun rawUrl(owner: String, repo: String, branch: String, path: String) = "https://raw.githubusercontent.com/$owner/$repo/$branch/$path"

        fun frontMatter(text: String, fallback: String): Pair<String, String> {
            val fm = Regex("^---\\s*\\n(.*?)\\n---", RegexOption.DOT_MATCHES_ALL).find(text.trimStart())?.groupValues?.get(1).orEmpty()
            fun field(k: String) = Regex("^$k:\\s*(.+)$", RegexOption.MULTILINE).find(fm)?.groupValues?.get(1)?.trim()?.trim('"', '\'')
            return (field("name") ?: fallback) to (field("description") ?: text.lineSequence().firstOrNull { it.isNotBlank() && !it.startsWith("---") && !it.startsWith("#") }.orEmpty().take(200))
        }
    }
}
