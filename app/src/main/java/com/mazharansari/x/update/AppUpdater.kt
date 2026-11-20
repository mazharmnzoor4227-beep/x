package com.mazharansari.x.update

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.mazharansari.x.BuildConfig
import com.mazharansari.x.llm.Http
import com.mazharansari.x.llm.ParallelFetch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.UnknownHostException
import java.security.MessageDigest

/** The update feed mazhar hosts: { versionCode, versionName, url, sha256, size, notes }. */
@Serializable
data class UpdateInfo(val versionCode: Int, val versionName: String, val url: String, val sha256: String, val size: Long = 0, val notes: String = "")

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAt: Long) : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val done: Long, val total: Long) : UpdateState
    data class Verifying(val info: UpdateInfo) : UpdateState
    /** Downloaded, hash and signing certificate checked: ready for the system installer. */
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState
    data class Failed(val message: String, val info: UpdateInfo? = null) : UpdateState
}

/** One GitHub release, as the releases API returns it (only the fields X reads). */
@Serializable
data class GitHubReleaseAsset(val name: String = "", val browser_download_url: String = "", val size: Long = 0)

@Serializable
data class GitHubRelease(val tag_name: String = "", val name: String = "", val body: String = "", val assets: List<GitHubReleaseAsset> = emptyList())

/**
 * What X needs from a GitHub release to offer it as an update. [versionCode]
 * and [sha256] come from the release notes (see [UpdateRules.parseGitHubRelease]);
 * the APK itself comes from the release's attached .apk asset.
 */
data class GitHubReleaseInfo(
    val versionName: String,
    val versionCode: Int?,
    val sha256: String?,
    val apkUrl: String?,
    val apkSize: Long,
    val shaAssetUrl: String?,
    val notes: String,
)

/** Pure parts (unit-tested on the JVM): feed parsing, version compare, hashes, the certificate rule. */
object UpdateRules {
    /**
     * 2026-11-20 (blanket audit, fix 3): X's OWN update feed — its own GitHub
     * repo mazharmnzoor4227-beep/x releases, via the releases API (verified
     * 2026-11-20: the repo is real and public; it has NO releases yet, so
     * until the first release is published the check says so honestly).
     * The two Axion feeds this app inherited from the donor code
     * (axion.130-210-62-94.sslip.io and claworabeauty.store/uploads/dl/axion)
     * stay DELETED — they served Axion's updates, and that is exactly the
     * banner the user saw ("Update available" offering Axion's update).
     * Do NOT point this at any Axion URL.
     *
     * RELEASE CONTRACT (what a published release must carry for X to offer it):
     * - a tag like v1.1 (the version name; the leading "v" is dropped),
     * - the APK attached as an asset (first file ending in .apk),
     * - the release notes (body) carrying a ```json fenced block with
     *   {"versionCode": <the APK's versionCode>, "sha256": "<64 hex chars>"}
     *   (and optionally "notes"); the SHA-256 may instead ride as a .sha256
     *   asset (its first word is the hash). Without a versionCode or a
     *   SHA-256 the release is NOT offered — the check fails with a plain
     *   message saying what is missing, never a guessed version or hash.
     * The downloaded APK is verified exactly like before: SHA-256 must match
     * ([AppUpdater.download]) and it must be signed with X's key ([CERT_SHA256]).
     * The API is unauthenticated (60 requests/hour per IP); X checks at most
     * once a day on its own, plus when the user taps Check for updates.
     */
    const val GITHUB_RELEASES_URL = "https://api.github.com/repos/mazharmnzoor4227-beep/x/releases/latest"
    val FEEDS: List<String> = listOf(GITHUB_RELEASES_URL)
    /** SHA-256 of X's release signing certificate (x-release keystore). An update signed with any other key is refused. */
    const val CERT_SHA256 = "7f458cdb4e0b7e8f11a3a6be4d893f8f8c78becfeb56949a0f549bc862328b0f"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(text: String): UpdateInfo {
        val i = try { json.decodeFromString(UpdateInfo.serializer(), text) } catch (e: Exception) { throw IOException("The update feed isn't valid JSON.") }
        if (i.versionCode <= 0 || i.versionName.isBlank()) throw IOException("The update feed has no version.")
        if (!i.url.startsWith("https://")) throw IOException("The update link must be https.")
        if (!Regex("^[0-9a-fA-F]{64}$").matches(i.sha256.trim())) throw IOException("The update feed has no valid SHA-256.")
        return i.copy(sha256 = i.sha256.trim().lowercase())
    }
    fun isNewer(info: UpdateInfo, currentCode: Int) = info.versionCode > currentCode

    /**
     * Reads a GitHub releases-API response (see the [FEEDS] contract): the tag
     * becomes the version name, the .apk asset becomes the download, and the
     * versionCode / SHA-256 come from the ```json block in the release notes
     * (or, for the hash, from a .sha256 asset). Missing pieces stay null —
     * the caller refuses the release with a plain message instead of guessing.
     */
    fun parseGitHubRelease(text: String): GitHubReleaseInfo {
        val rel = try { json.decodeFromString(GitHubRelease.serializer(), text) } catch (e: Exception) { throw IOException("The X release feed isn't valid JSON.") }
        val versionName = rel.tag_name.trim().removePrefix("v").removePrefix("V")
        if (versionName.isBlank()) throw IOException("The X release has no version tag.")
        val bodyJson = runCatching {
            val fenced = Regex("```json\\s*\\n(.*?)\\n```", RegexOption.DOT_MATCHES_ALL).find(rel.body)?.groupValues?.get(1)
            val raw = fenced ?: rel.body.trim().takeIf { it.startsWith("{") }
            raw?.let { Json.parseToJsonElement(it).jsonObject }
        }.getOrNull()
        val code = bodyJson?.get("versionCode")?.jsonPrimitive?.intOrNull
        val sha = bodyJson?.get("sha256")?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()
            ?.takeIf { Regex("^[0-9a-f]{64}$").matches(it) }
        val apk = rel.assets.firstOrNull { it.name.lowercase().endsWith(".apk") }
        val shaAsset = rel.assets.firstOrNull { it.name.lowercase().endsWith(".sha256") }
        val notes = bodyJson?.get("notes")?.jsonPrimitive?.contentOrNull ?: rel.name
        return GitHubReleaseInfo(versionName, code, sha, apk?.browser_download_url?.takeIf { it.startsWith("https://") },
            apk?.size ?: 0, shaAsset?.browser_download_url?.takeIf { it.startsWith("https://") }, notes.orEmpty())
    }

    /** Turns a parsed release plus its resolved SHA-256 into the [UpdateInfo] the download path verifies (same rules as [parse]). */
    fun githubToUpdateInfo(rel: GitHubReleaseInfo, sha256: String): UpdateInfo {
        val code = rel.versionCode ?: throw IOException("The X release does not state a versionCode — add {\"versionCode\": N} to its release notes.")
        if (code <= 0) throw IOException("The X release has no valid versionCode.")
        val url = rel.apkUrl ?: throw IOException("The X release has no APK file attached.")
        if (!Regex("^[0-9a-f]{64}$").matches(sha256)) throw IOException("The X release has no valid SHA-256.")
        return UpdateInfo(code, rel.versionName, url, sha256.lowercase(), rel.apkSize, rel.notes)
    }
    fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s -> val buf = ByteArray(1 shl 16); while (true) { val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
    fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    /** Every signer must be ours (an APK can carry several; one foreign signer is enough to refuse). */
    fun certOk(signerSha256: List<String>) = signerSha256.isNotEmpty() && signerSha256.all { it.equals(CERT_SHA256, ignoreCase = true) }
    fun mb(b: Long) = "%.1f MB".format(b / 1048576.0)
    fun friendly(e: Throwable): String = when (e) {
        is UnknownHostException, is java.net.ConnectException, is java.net.NoRouteToHostException -> "No internet connection. Try again when you're online."
        is java.net.SocketTimeoutException -> "The update server didn't answer in time. Try again later."
        else -> e.message ?: "Couldn't check for updates."
    }
}

class AppUpdater(
    private val ctx: Context,
    /** P0-2 (CR-2): every feed in this list is checked; the highest versionCode wins, and the check still works when any one host is down. */
    private val feeds: List<String> = UpdateRules.FEEDS,
    private val http: OkHttpClient = Http.download,
    private val currentCode: Int = BuildConfig.VERSION_CODE,
    /** Reads the signer certificates' SHA-256 from an APK; Android's own parser unless a test swaps it. */
    private val certReader: ((File) -> List<String>)? = null,
) {
    private val prefs = ctx.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val dir = File(ctx.cacheDir, "updates")
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    /** The home banner was closed for this version. */
    var dismissedCode: Int
        get() = prefs.getInt("dismissed", 0)
        set(v) { prefs.edit().putInt("dismissed", v).apply() }

    suspend fun check(): UpdateState = withContext(Dispatchers.IO) {
        // No feed configured: never touch the network, never offer anything.
        if (feeds.isEmpty()) {
            val st = UpdateState.Failed("X does not check for updates yet — its own update feed is not set up.")
            _state.value = st; return@withContext st
        }
        _state.value = UpdateState.Checking
        val st = try {
            // P0-2: ask every feed; a dead host (or a bad feed) is skipped, not fatal — the
            // highest versionCode among the feeds that answered is what gets offered.
            val errors = ArrayList<Exception>()
            val infos = feeds.mapNotNull { url ->
                runCatching { fetchFeed(url) }.onFailure { errors += it as? Exception ?: IOException(it.message) }.getOrNull()
            }
            if (infos.isEmpty()) UpdateState.Failed(UpdateRules.friendly(errors.firstOrNull() ?: IOException("Couldn't check for updates.")))
            else {
                val info = infos.maxByOrNull { it.versionCode }!!
                prefs.edit().putLong("lastCheck", System.currentTimeMillis()).apply()
                if (!UpdateRules.isNewer(info, currentCode)) UpdateState.UpToDate(System.currentTimeMillis())
                else {
                    val f = apkFile(info)
                    if (f.isFile && f.length() == info.size.takeIf { it > 0 } && UpdateRules.sha256(f) == info.sha256) UpdateState.Ready(info, f)
                    else UpdateState.Available(info)
                }
            }
        } catch (e: Exception) { UpdateState.Failed(UpdateRules.friendly(e)) }
        _state.value = st; st
    }

    /** One feed fetch: non-200 or an invalid feed body throws, so the caller can try the next feed. */
    private fun fetchFeed(url: String): UpdateInfo {
        // X's own feed is the GitHub releases API (see UpdateRules.FEEDS) — its
        // response is a release, not the flat feed JSON, so it parses separately.
        if (url.substringBefore('?').endsWith("/releases/latest")) return fetchGitHubRelease(url)
        val (code, body) = http.newCall(Request.Builder().url(url + "?t=" + System.currentTimeMillis()).header("User-Agent", Http.UA).header("Cache-Control", "no-cache").build())
            .execute().use { it.code to it.body?.string().orEmpty() }
        if (code != 200) throw IOException("The update server answered $code. Try again later.")
        return UpdateRules.parse(body)
    }

    /** The GitHub releases feed: latest release → UpdateInfo (SHA-256 from the notes or a .sha256 asset). */
    private fun fetchGitHubRelease(url: String): UpdateInfo {
        val (code, body) = http.newCall(Request.Builder().url(url).header("User-Agent", Http.UA)
            .header("Accept", "application/vnd.github+json").header("Cache-Control", "no-cache").build())
            .execute().use { it.code to it.body?.string().orEmpty() }
        if (code == 404) throw IOException("No X release has been published yet on GitHub.")
        if (code == 403) throw IOException("GitHub's releases API refused the request (rate limit). Try again later.")
        if (code != 200) throw IOException("The update server answered $code. Try again later.")
        val rel = UpdateRules.parseGitHubRelease(body)
        val sha = rel.sha256 ?: rel.shaAssetUrl?.let { fetchShaAsset(it) }
            ?: throw IOException("The X release does not state its SHA-256 — add it to the release notes or attach a .sha256 file.")
        return UpdateRules.githubToUpdateInfo(rel, sha)
    }

    /** A .sha256 asset's content: "<hash>  <filename>" or a bare hash — the first word is the hash. */
    private fun fetchShaAsset(url: String): String {
        val (code, body) = http.newCall(Request.Builder().url(url).header("User-Agent", Http.UA).build())
            .execute().use { it.code to it.body?.string().orEmpty() }
        if (code != 200) throw IOException("The X release's checksum file could not be read ($code).")
        return body.trim().split(Regex("\\s+")).firstOrNull().orEmpty().lowercase()
            .takeIf { Regex("^[0-9a-f]{64}$").matches(it) }
            ?: throw IOException("The X release's checksum file has no valid SHA-256.")
    }

    /** On app start, at most once a day, silently; a notification when something newer is out. */
    suspend fun quietCheck() {
        if (Build.FINGERPRINT == "robolectric") return
        if (feeds.isEmpty()) return   // no feed configured: no check, no banner, no notification
        val last = prefs.getLong("lastCheck", 0)
        if (System.currentTimeMillis() - last < 24 * 3_600_000L) return
        val st = check()
        if (st is UpdateState.Available && st.info.versionCode != prefs.getInt("notified", 0)) {
            prefs.edit().putInt("notified", st.info.versionCode).apply(); notify(st.info)
        } else if (st is UpdateState.Failed) _state.value = UpdateState.Idle   // a quiet check never shows an error
    }

    private fun apkFile(info: UpdateInfo) = File(dir, "X-${info.versionName}.apk")

    suspend fun download(info: UpdateInfo): UpdateState = withContext(Dispatchers.IO) {
        dir.mkdirs(); dir.listFiles()?.filter { it.name != apkFile(info).name && it.name != apkFile(info).name + ".part" && !it.name.startsWith(apkFile(info).name + ".part.seg") }?.forEach { it.delete() }
        val part = File(dir, apkFile(info).name + ".part")
        try {
            // v0.8.0: one connection to the update host crawls on some networks (the owner's
            // 11 MB update took ~10 minutes) — pull the file over parallel ranged connections
            // when the server allows it; ParallelFetch says no and the single stream below runs.
            val parallel = info.size >= 1_048_576L && ParallelFetch.fetch(http, info.url, part, expectedTotal = info.size) { d, t ->
                _state.value = UpdateState.Downloading(info, d, t)
            }
            if (!parallel) fetchSingle(info, part)
            _state.value = UpdateState.Verifying(info)
            if (info.size > 0 && part.length() != info.size) { part.delete(); return@withContext fail("The download was incomplete (${UpdateRules.mb(part.length())} of ${UpdateRules.mb(info.size)}). Try again.", info) }
            if (UpdateRules.sha256(part) != info.sha256) { part.delete(); return@withContext fail("The downloaded file didn't match its checksum, so it was deleted. Try again.", info) }
            val certs = certReader?.invoke(part) ?: signerCerts(part)
            if (!UpdateRules.certOk(certs)) { part.delete(); return@withContext fail("This update isn't signed with X's key, so it won't be installed.", info) }
            val f = apkFile(info); f.delete(); part.renameTo(f)
            UpdateState.Ready(info, f).also { _state.value = it }
        } catch (e: Exception) { fail(UpdateRules.friendly(e), info) }
    }

    /** The classic one-stream download (with .part resume), used when ranged parallel fetch isn't possible. */
    private fun fetchSingle(info: UpdateInfo, part: File) {
        var have = if (part.isFile) part.length() else 0L
        val rb = Request.Builder().url(info.url).header("User-Agent", Http.UA)
        if (have > 0) rb.header("Range", "bytes=$have-")
        http.newCall(rb.build()).execute().use { r ->
            if (r.code == 416) { have = 0; part.delete() }
            if (!r.isSuccessful && r.code != 416) throw IOException("The download failed (${r.code}).")
            val append = r.code == 206
            if (!append) { have = 0 }
            val total = (r.body?.contentLength() ?: -1L).let { if (it > 0) it + have else info.size }
            _state.value = UpdateState.Downloading(info, have, total)
            java.io.FileOutputStream(part, append).use { out ->
                val src = r.body!!.byteStream(); val buf = ByteArray(1 shl 16); var done = have; var lastEmit = 0L
                while (true) {
                    val n = src.read(buf); if (n < 0) break
                    out.write(buf, 0, n); done += n
                    val now = System.currentTimeMillis()
                    if (now - lastEmit > 150) { lastEmit = now; _state.value = UpdateState.Downloading(info, done, total) }
                }
            }
        }
    }

    private fun fail(msg: String, info: UpdateInfo?) = UpdateState.Failed(msg, info).also { _state.value = it }

    /** SHA-256 of each signing certificate in the APK file (read by Android's own parser). */
    @SuppressLint("PackageManagerGetSignatures")
    @Suppress("DEPRECATION")
    fun signerCerts(apk: File): List<String> {
        val pm = ctx.packageManager
        return if (Build.VERSION.SDK_INT >= 28) {
            val pi = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES) ?: return emptyList()
            val si = pi.signingInfo ?: return emptyList()
            (if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory.takeLast(1).toTypedArray()).map { UpdateRules.sha256(it.toByteArray()) }
        } else {
            val pi = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNATURES) ?: return emptyList()
            pi.signatures.orEmpty().map { UpdateRules.sha256(it.toByteArray()) }
        }
    }

    fun canInstall(): Boolean = Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    /** Android's "Install unknown apps" page for X. */
    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    @Suppress("DEPRECATION")
    fun install(file: File) {
        // v0.5.2: check the signing key again right before handing the file to Android (it sat in the cache meanwhile).
        val certs = certReader?.invoke(file) ?: signerCerts(file)
        if (!UpdateRules.certOk(certs)) { file.delete(); throw SecurityException("This update isn't signed with X's key, so it won't be installed.") }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val i = Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, false)
        ctx.startActivity(i)
    }

    private fun notify(info: UpdateInfo) = runCatching {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CH, "App updates", NotificationManager.IMPORTANCE_DEFAULT))
        val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!.putExtra(EXTRA_OPEN_ABOUT, true)
        val pi = PendingIntent.getActivity(ctx, 7, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        nm.notify(NID, NotificationCompat.Builder(ctx, CH).setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle("X ${info.versionName} is available")
            .setContentText(info.notes.ifBlank { "Tap to update." }).setContentIntent(pi).setAutoCancel(true).build())
    }

    /** For the render tests. */
    fun debugSet(s: UpdateState) { _state.value = s }

    companion object {
        const val CH = "updates"; const val NID = 4107
        const val EXTRA_OPEN_ABOUT = "x.open_about"
    }
}
