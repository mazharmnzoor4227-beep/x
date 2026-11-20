package com.mazharansari.x.linux

import android.content.Context
import android.net.ConnectivityManager
import android.os.StatFs
import android.system.Os
import com.mazharansari.x.llm.Http
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Call
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPInputStream

/** Where the private Ubuntu lives and what the first-use download is. Numbers are the real file sizes (checked 1 Nov 2026). */
object LinuxBase {
    const val UBUNTU = "Ubuntu 24.04"
    const val ROOTFS_URL = "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-arm64.tar.gz"
    const val ROOTFS_SHA256 = "a91d5a93010193712d346d761372b7c9db6dfcf093893161c64ca107f05914f2"
    const val ROOTFS_BYTES = 29_936_675L
    /** Ubuntu base unpacked (~81 MB) + base tools from apt (~55 MB) + headroom for apt's own downloads. */
    const val NEEDS_FREE_BYTES = 350L * 1024 * 1024
    /** What the user downloads before anything works: the rootfs + the base tools (certificates, curl, git) from apt. */
    const val FIRST_DOWNLOAD_MB = 80
    const val ROOTFS_MARKER = ".axion-rootfs"
    const val SCRIPTS_MARKER = "opt/axion/.scripts-version"
}

/**
 * v0.5.3 smaller Linux: like Ubuntu's own "minimized" images, dpkg is told not to unpack documentation, man pages and
 * translations (all English UI), and what Ubuntu base and earlier installs already put there is removed once.
 * Nothing a program needs at run time lives in these folders. Pure file work, unit-tested on a temp folder.
 */
object LinuxSlim {
    const val MARKER = "opt/axion/.slim-v1"
    const val DPKG_CFG = "etc/dpkg/dpkg.cfg.d/01-axion-nodoc"
    val DPKG_TEXT = """
        # X: keep Linux small on the phone (same idea as Ubuntu minimized images)
        path-exclude=/usr/share/doc/*
        path-include=/usr/share/doc/*/copyright
        path-exclude=/usr/share/man/*
        path-exclude=/usr/share/info/*
        path-exclude=/usr/share/groff/*
        path-exclude=/usr/share/lintian/*
        path-exclude=/usr/share/linda/*
        path-exclude=/usr/share/locale/*
        path-include=/usr/share/locale/locale.alias
        path-include=/usr/share/locale/en*
    """.trimIndent() + "\n"

    /** Writes the dpkg rule (every time, cheap) and does the one-time cleanup. Returns the bytes freed by the cleanup. */
    fun apply(rootfs: File): Long {
        if (!File(rootfs, "usr").isDirectory) return 0
        File(rootfs, DPKG_CFG).let { if (it.readTextOrNull() != DPKG_TEXT.trim()) { it.parentFile?.mkdirs(); it.writeText(DPKG_TEXT) } }
        val marker = File(rootfs, MARKER)
        if (marker.exists()) return 0
        var freed = 0L
        fun wipe(dir: File, keep: (File) -> Boolean = { false }) {
            dir.listFiles()?.forEach { f -> if (!keep(f)) { freed += StorageScan.bytes(f); deleteNoFollow(f) } }
        }
        wipe(File(rootfs, "usr/share/doc"))
        listOf("man", "info", "groff", "lintian", "linda").forEach { wipe(File(rootfs, "usr/share/$it")) }
        wipe(File(rootfs, "usr/share/locale")) { it.name == "locale.alias" || it.name.startsWith("en") }
        marker.parentFile?.mkdirs(); marker.writeText("1")
        return freed
    }

    /** Deletes a file or folder without ever following a symlink out of it (rootfs is full of absolute links). */
    fun deleteNoFollow(f: File) {
        val p = f.toPath()
        if (java.nio.file.Files.isDirectory(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)) f.listFiles()?.forEach { deleteNoFollow(it) }
        runCatching { java.nio.file.Files.deleteIfExists(p) }
    }
}

data class LinuxProgress(val message: String, val fraction: Float, val indeterminate: Boolean = false)

sealed interface LinuxState {
    data object NotInstalled : LinuxState
    data class Installing(val p: LinuxProgress) : LinuxState
    data object Ready : LinuxState
    data class Failed(val message: String) : LinuxState
}

/**
 * The PRoot Ubuntu sandbox, ported from Kodex's RuntimeInstaller (process(), writeResolver(), ensureRootfsCompatibilityLinks(),
 * tar extraction with budget checks) and cut down to what X needs. Nothing is downloaded until the user asks for a tool.
 * Open so unit tests can fake the spawn/run path (DevToolsInstallTest): the real class still does all the work on the phone.
 */
open class LinuxRuntime(private val ctx: Context) {
    val home = File(ctx.filesDir, "linux")
    val rootfs = File(home, "ubuntu")
    private val download = File(home, "download/ubuntu-base.tar.gz")
    val workspaceHost: File get() = File(ctx.filesDir, "workspace").apply { mkdirs() }
    /** v0.5 Build projects: files/projects/<slug> on the phone = /root/projects/<slug> in Linux. */
    val projectsHost: File get() = File(ctx.filesDir, "projects").apply { mkdirs() }
    private val nativeDir get() = File(ctx.applicationInfo.nativeLibraryDir)
    val prootBinary: File get() = File(nativeDir, "libproot.so")

    private val _state = MutableStateFlow<LinuxState>(if (isInstalled()) LinuxState.Ready else LinuxState.NotInstalled)
    val state: StateFlow<LinuxState> = _state.asStateFlow()
    private val installLock = kotlinx.coroutines.sync.Mutex()

    open fun isInstalled(): Boolean = File(rootfs, LinuxBase.ROOTFS_MARKER).isFile && File(rootfs, "usr/bin/bash").exists()
    fun freeBytes(): Long = runCatching { StatFs(ctx.filesDir.absolutePath).availableBytes }.getOrDefault(0L)
    fun diskUsageBytes(): Long = if (rootfs.exists()) runCatching { duBytes(rootfs) }.getOrDefault(0L) else 0L

    /** First use: download Ubuntu base (resumable), check its SHA-256, unpack, prepare. Safe to call again; returns at once when ready. */
    open suspend fun ensureBase(onProgress: (LinuxProgress) -> Unit = {}) = installLock.withLockIfFree {
        if (isInstalled()) { prepare(); _state.value = LinuxState.Ready; return@withLockIfFree }
        val report: (LinuxProgress) -> Unit = { _state.value = LinuxState.Installing(it); onProgress(it) }
        try {
            withContext(Dispatchers.IO) {
                val free = freeBytes()
                check(free >= LinuxBase.NEEDS_FREE_BYTES) {
                    "Not enough space: Linux needs about ${LinuxBase.NEEDS_FREE_BYTES / 1048576} MB free, the phone has ${free / 1048576} MB."
                }
                report(LinuxProgress("Downloading ${LinuxBase.UBUNTU} (${LinuxBase.ROOTFS_BYTES / 1048576} MB)", 0f))
                downloadResumable(LinuxBase.ROOTFS_URL, download, LinuxBase.ROOTFS_BYTES) { done, total ->
                    report(LinuxProgress("Downloading ${LinuxBase.UBUNTU} · ${done / 1048576} of ${total / 1048576} MB", 0.6f * done / total.coerceAtLeast(1)))
                }
                report(LinuxProgress("Checking the download", 0.62f))
                val sum = sha256(download)
                if (!sum.equals(LinuxBase.ROOTFS_SHA256, true)) { download.delete(); error("The Ubuntu download was damaged (checksum). Tap install again.") }
                report(LinuxProgress("Unpacking ${LinuxBase.UBUNTU}", 0.65f))
                val staging = File(home, "ubuntu.staging").apply { deleteRecursively(); mkdirs() }
                // Absolute links are rewritten inside the rootfs (see extractTarGz); the
                // ones skipped pointed outside it and can never be load-bearing inside it.
                // (No Log call here: the security test forbids logging anywhere in app code.)
                extractTarGz(download, staging, onCount = { n -> report(LinuxProgress("Unpacking ${LinuxBase.UBUNTU} · $n files", 0.65f + 0.3f * (n / 3500f).coerceAtMost(1f))) })
                rootfs.deleteRecursively()
                check(staging.renameTo(rootfs)) { "Could not move the Linux files into place" }
                File(rootfs, LinuxBase.ROOTFS_MARKER).writeText(LinuxBase.ROOTFS_URL.substringAfterLast('/'))
                download.delete()
                prepare()
                report(LinuxProgress("Linux is ready", 1f))
            }
            _state.value = LinuxState.Ready
        } catch (e: CancellationException) { _state.value = if (isInstalled()) LinuxState.Ready else LinuxState.NotInstalled; throw e
        } catch (e: Throwable) { _state.value = LinuxState.Failed(e.message ?: "Linux setup failed"); throw e }
    }

    /** Remove everything (rootfs + all toolchains). Antigravity sign-ins live outside the rootfs and survive. */
    suspend fun uninstallAll() = withContext(Dispatchers.IO) {
        rootfs.deleteRecursively(); File(home, "ubuntu.staging").deleteRecursively(); download.delete()
        _state.value = LinuxState.NotInstalled
    }

    /** Per-launch fix-ups: DNS from the CURRENT network (Kodex's "works only on Wi-Fi" fix), hosts, compatibility links, our scripts. */
    fun prepare() {
        runCatching { writeResolver() }
        runCatching { writeHosts() }
        runCatching { writeCaBundle() }
        runCatching { fakeProcFiles() }
        ensureCompatibilityLinks()
        installScripts()
        runCatching { LinuxSlim.apply(rootfs) }   // v0.5.3: no docs/man pages/other languages in Linux (smaller install)
        File(rootfs, "workspace").mkdirs(); File(rootfs, "root/.gemini/antigravity-cli").mkdirs()
    }

    private fun installScripts() {
        val version = "${com.mazharansari.x.BuildConfig.VERSION_CODE}-" + runCatching { ctx.assets.list("devtools")!!.sorted().joinToString { it } .hashCode() }.getOrDefault(0)
        val marker = File(rootfs, LinuxBase.SCRIPTS_MARKER)
        if (marker.readTextOrNull() == version) return
        val dir = File(rootfs, "opt/axion/devtools").apply { mkdirs() }
        ctx.assets.list("devtools")?.forEach { name ->
            ctx.assets.open("devtools/$name").use { i -> File(dir, name).outputStream().use { i.copyTo(it) } }
            runCatching { Os.chmod(File(dir, name).absolutePath, "755".toInt(8)) }
        }
        marker.writeText(version)
        linkAxionTools()
    }

    /** axion-check / axion-shot / axion-serve on PATH for the Build agent. */
    private fun linkAxionTools() {
        val bin = File(rootfs, "usr/local/bin").apply { mkdirs() }
        listOf("axion-check", "axion-shot", "axion-serve").forEach { n ->
            val link = File(bin, n)
            runCatching { if (!link.exists() && !java.nio.file.Files.isSymbolicLink(link.toPath())) Os.symlink("/opt/axion/devtools/$n", link.absolutePath) }
        }
    }

    /**
     * v0.5.2 (real-phone "Could not install: ca-certificates curl git …", reproduced with DNS broken inside PRoot): the network's
     * own DNS first (IPv4 preferred), then 8.8.8.8 and 1.1.1.1 — glibc reads at most 3 — with short timeouts so a dead one is skipped.
     */
    private fun writeResolver() {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val dns = runCatching { cm.getLinkProperties(cm.activeNetwork)?.dnsServers.orEmpty() }.getOrDefault(emptyList())
        File(rootfs, "etc/resolv.conf").apply { delete() }.writeText(resolverText(dns.mapNotNull { it.hostAddress }))
    }

    /**
     * The package servers (and the Antigravity download host) as Android itself resolves them, pinned in /etc/hosts: apt
     * works even when DNS inside Linux doesn't. Refreshed at every start; resolution never runs on the main thread.
     */
    private fun writeHosts() {
        val pins = if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) lastPins else resolvePins().also { if (it.isNotEmpty()) lastPins = it }
        File(rootfs, "etc/hosts").writeText(hostsText(pins))
    }
    @Volatile private var lastPins: Map<String, String> = emptyMap()
    private fun resolvePins(): Map<String, String> {
        val out = java.util.concurrent.ConcurrentHashMap<String, String>()
        val threads = PIN_HOSTS.map { h -> Thread { runCatching { java.net.InetAddress.getAllByName(h).let { a -> (a.firstOrNull { it is java.net.Inet4Address } ?: a.firstOrNull())?.hostAddress?.let { out[h] = it } } } }.apply { isDaemon = true; start() } }
        val until = System.currentTimeMillis() + 4000
        threads.forEach { it.join((until - System.currentTimeMillis()).coerceAtLeast(1)) }
        return out.toMap()
    }

    /**
     * Android's trusted CAs as a PEM bundle, so apt can use https mirrors BEFORE the ca-certificates package exists (plain http
     * is what carrier proxies break). Once ca-certificates is installed it owns the file and this leaves it alone.
     */
    private fun writeCaBundle() {
        val dest = File(rootfs, "etc/ssl/certs/ca-certificates.crt")
        val ours = File(rootfs, "opt/axion/cacert.pem")
        if (dest.length() > 0 && ours.exists()) return
        val ks = java.security.KeyStore.getInstance("AndroidCAStore").apply { load(null) }
        val pem = StringBuilder()
        for (alias in ks.aliases()) {
            if (!alias.startsWith("system:")) continue   // system CAs only, never user-added ones
            val c = ks.getCertificate(alias) ?: continue
            pem.append("-----BEGIN CERTIFICATE-----\n").append(android.util.Base64.encodeToString(c.encoded, android.util.Base64.NO_WRAP).chunked(64).joinToString("\n")).append("\n-----END CERTIFICATE-----\n")
        }
        if (pem.isEmpty()) return
        ours.parentFile?.mkdirs(); ours.writeText(pem.toString())
        if (dest.length() == 0L) { dest.parentFile?.mkdirs(); dest.writeText(pem.toString()) }
    }

    /** Android hides some /proc files from apps; tools inside Linux (procps, Node, Python) read them. Fakes like proot-distro's. */
    private fun fakeProcFiles() {
        val dir = File(rootfs, "opt/axion/proc").apply { mkdirs() }
        FAKE_PROC.forEach { (name, text) -> File(dir, name.replace('/', '_')).let { if (!it.exists()) it.writeText(text) } }
    }
    /** -b binds for /proc files the app cannot read, plus /dev/shm (Android has none) → the guest's /tmp. */
    private fun extraBinds(): List<Pair<String, String>> = buildList {
        add(File(rootfs, "tmp").absolutePath to "/dev/shm")
        FAKE_PROC.keys.forEach { name -> if (!readable("/proc/$name")) add(File(rootfs, "opt/axion/proc/" + name.replace('/', '_')).absolutePath to "/proc/$name") }
    }
    private fun readable(path: String) = runCatching { File(path).inputStream().use { it.read() }; true }.getOrDefault(false)

    private fun ensureCompatibilityLinks() {
        for ((name, target) in listOf("bin" to "usr/bin", "lib" to "usr/lib", "sbin" to "usr/sbin")) {
            val link = File(rootfs, name)
            if (!link.exists() && !java.nio.file.Files.isSymbolicLink(link.toPath())) runCatching { Os.symlink(target, link.absolutePath) }
        }
    }

    /**
     * Start a command inside Ubuntu. [binds] are extra host:guest mounts (an Antigravity account folder → ~/.gemini/antigravity-cli).
     * Same argv and environment as Kodex's process(): fake root, /dev /proc /sys and Android's /system (Go and Java find the CA store there).
     */
    fun spawn(
        guestCommand: List<String>, guestCwd: String = "/workspace", binds: List<Pair<String, String>> = emptyList(),
        env: Map<String, String> = emptyMap(), pty: Boolean = false, outputFile: File = File(ctx.cacheDir, "linux-out-${System.nanoTime()}.log"),
        fakeRoot: Boolean = true,
    ): NativeSpawnProcess {
        check(isInstalled()) { "Linux is not installed yet" }
        prepare()
        val allBinds = listOf(projectsHost.absolutePath to "/root/projects") + binds + runCatching { extraBinds() }.getOrDefault(emptyList())
        allBinds.forEach { (h, g) -> File(h).mkdirs(); File(rootfs, g.removePrefix("/")).mkdirs() }
        // v0.5.2: the app's own LD_LIBRARY_PATH / LD_PRELOAD (needed by proot itself) must not leak into Ubuntu's programs.
        val cmd = if (File(rootfs, "usr/bin/env").exists()) listOf("/usr/bin/env", "-u", "LD_LIBRARY_PATH", "-u", "LD_PRELOAD") + guestCommand else guestCommand
        val args = prootArgs(prootBinary.absolutePath, rootfs.absolutePath, workspaceHost.absolutePath, allBinds, guestCwd,
            listOf("/system", "/apex", "/vendor", "/product").filter { File(it).exists() }, cmd, fakeRoot)
        val tmp = File(ctx.cacheDir, "proot-tmp").apply { mkdirs() }
        val certs = if (File(rootfs, "opt/axion/cacert.pem").length() > 0 && !File(rootfs, "usr/sbin/update-ca-certificates").exists()) mapOf("SSL_CERT_FILE" to "/opt/axion/cacert.pem") else emptyMap()
        val environment = baseEnv(nativeDir.absolutePath, tmp.absolutePath) + certs + androidEnv(rootfs) + env
        return NativeSpawnProcess.start(args, environment, ctx.filesDir.absolutePath, outputFile, pty)
    }

    /** Run `bash -lc command` and stream its output lines. Returns (exit code, tail of output). Exit -1 = timed out. */
    open suspend fun run(
        command: String, timeoutMs: Long = 120_000, guestCwd: String = "/workspace", binds: List<Pair<String, String>> = emptyList(),
        env: Map<String, String> = emptyMap(), stdin: String? = null, fakeRoot: Boolean = true, onLine: (String) -> Unit = {},
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val out = File(ctx.cacheDir, "linux-run-${System.nanoTime()}.log")
        val proc = spawn(listOf("/bin/bash", "-lc", command), guestCwd, binds, env, outputFile = out, fakeRoot = fakeRoot)
        try {
            if (stdin != null) proc.outputStream.write(stdin.toByteArray())
            runCatching { proc.outputStream.close() }
            val tail = StringBuilder()
            var offset = 0L; val partial = StringBuilder()
            val deadline = System.currentTimeMillis() + timeoutMs
            var code: Int? = null
            while (true) {
                ensureActive()
                val len = out.length()
                if (len > offset) {
                    val bytes = RandomAccessFile(out, "r").use { f -> f.seek(offset); ByteArray(minOf(len - offset, 65536L).toInt()).also { f.readFully(it) } }
                    offset += bytes.size
                    partial.append(bytes.decodeToString())
                    var nl = partial.indexOf("\n")
                    while (nl >= 0) {
                        val line = partial.substring(0, nl).trimEnd('\r'); partial.delete(0, nl + 1)
                        onLine(line); tail.append(line).append('\n'); if (tail.length > 60_000) tail.delete(0, tail.length - 40_000)
                        nl = partial.indexOf("\n")
                    }
                    continue
                }
                if (code != null) break
                if (!proc.isAlive) { code = proc.waitFor(); continue }
                if (System.currentTimeMillis() > deadline) { proc.destroyForcibly(); code = -1; continue }
                delay(120)
            }
            if (partial.isNotEmpty()) { onLine(partial.toString()); tail.append(partial) }
            code!! to tail.toString()
        } catch (e: CancellationException) { proc.destroyForcibly(); throw e
        } finally { out.delete() }
    }

    companion object {
        /** Pure, unit-tested: the PRoot argv (Kodex's process() minus the Android-SDK parts). */
        fun prootArgs(
            proot: String, rootfs: String, workspaceHost: String, binds: List<Pair<String, String>>, guestCwd: String,
            hostSystemDirs: List<String>, guestCommand: List<String>, fakeRoot: Boolean = true,
        ): List<String> = buildList {
            // v0.5: Chromium crashes (SIGTRAP) under PRoot's fake root (-0) — found on the test box — so screenshots run without it.
            // v0.5.2: same flags as Kodex v3.2.55 (which runs on mazhar's phone) plus --kill-on-exit; same proot source (diffed).
            add(proot); add("--link2symlink"); add("--kill-on-exit"); if (fakeRoot) add("-0"); add("-r"); add(rootfs)
            listOf("/dev", "/proc", "/sys").forEach { add("-b"); add(it) }
            // Android's bionic linker, CA store (/system/etc/security/cacerts) and APEX libraries.
            hostSystemDirs.forEach { add("-b"); add(it) }
            add("-b"); add("$workspaceHost:/workspace")
            binds.forEach { (h, g) -> add("-b"); add("$h:$g") }
            add("-w"); add(guestCwd)
            addAll(guestCommand)
        }

        /** v0.5 APK builder: Android SDK + Gradle on PATH for every process (agy's own shell isn't a login shell). */
        fun androidEnv(rootfs: File): Map<String, String> {
            val sdk = File(rootfs, "root/android-sdk")
            if (!File(sdk, "platforms").isDirectory) return emptyMap()
            val bt = File(sdk, "build-tools").listFiles()?.filter { File(it, "aapt2").exists() }?.maxByOrNull { it.name }?.name
            val gradle = File(rootfs, "opt/gradle").listFiles()?.maxByOrNull { it.name }?.name
            val path = listOfNotNull(gradle?.let { "/opt/gradle/$it/bin" }, bt?.let { "/root/android-sdk/build-tools/$it" }).joinToString(":")
            return mapOf("ANDROID_HOME" to "/root/android-sdk", "ANDROID_SDK_ROOT" to "/root/android-sdk", "GRADLE_USER_HOME" to "/root/.gradle",
                "PATH" to "$path:/root/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
        }

        /** Hosts pinned in /etc/hosts (apt mirrors in common.sh's order + the Antigravity download host). */
        val PIN_HOSTS = listOf("ports.ubuntu.com", "mirror.sg.gs", "mirrors.ustc.edu.cn", "mirrors.aliyun.com", "storage.googleapis.com")

        /** Pure, unit-tested. */
        fun resolverText(network: List<String>): String {
            val net = network.filter { it.isNotBlank() && !it.contains('%') }.sortedBy { if (it.contains(':')) 1 else 0 }
            val servers = (net.take(1) + listOf("8.8.8.8", "1.1.1.1")).distinct().take(3)
            return "# written by X at each start\n" + servers.joinToString("") { "nameserver $it\n" } + "options timeout:3 attempts:2\n"
        }
        fun hostsText(pins: Map<String, String>): String = "127.0.0.1 localhost\n::1 localhost ip6-localhost\n" +
            if (pins.isEmpty()) "" else "# X: package servers as Android resolved them (refreshed at each start)\n" + pins.entries.sortedBy { it.key }.joinToString("") { "${it.value} ${it.key}\n" }

        val FAKE_PROC = linkedMapOf(
            "loadavg" to "0.12 0.07 0.02 2/165 765\n",
            "stat" to "cpu  1957 0 2877 93280 262 342 254 87 0 0\ncpu0 31 0 226 12027 82 10 4 9 0 0\nintr 0\nctxt 0\nbtime 0\nprocesses 1\nprocs_running 1\nprocs_blocked 0\n",
            "uptime" to "124.08 932.80\n",
            "version" to "Linux version 6.2.1-PRoot-Distro (proot@termux) (gcc 12) #1 SMP PREEMPT_DYNAMIC\n",
            "vmstat" to "nr_free_pages 1743136\nnr_zone_inactive_anon 179281\nnr_zone_active_anon 7183\n",
            "sys/kernel/cap_last_cap" to "40\n",
            "sys/fs/inotify/max_user_watches" to "4096\n",
        )

        fun baseEnv(nativeLibDir: String, prootTmp: String): Map<String, String> = mapOf(
            "HOME" to "/root",
            "PATH" to "/root/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "LANG" to "C.UTF-8", "TERM" to "xterm-256color", "TMPDIR" to "/tmp",
            "LD_LIBRARY_PATH" to nativeLibDir,
            "PROOT_NO_SECCOMP" to "1",
            "PROOT_TMP_DIR" to prootTmp,
            "PROOT_LOADER" to "$nativeLibDir/libprootloader.so",
            "GLIBC_TUNABLES" to "glibc.pthread.rseq=0",
            "PUPPETEER_EXECUTABLE_PATH" to "/usr/local/bin/chromium", "PLAYWRIGHT_BROWSERS_PATH" to "/opt/ms-playwright",
            "COMPOSER_ALLOW_SUPERUSER" to "1", "PIP_ROOT_USER_ACTION" to "ignore",
        )

        // Parallel downloads (2026-11-20, model downloads were very slow): the old path was one
        // connection, one stream, 64 KB buffer. A 1.1 GB model on a throttled connection crawls on
        // one stream; 8 ranges in parallel use the connection properly. Tuning, all measured in
        // ParallelDownloadTest against a per-connection-throttled local server:
        // 8 connections, 256 KB buffers (8 x 256 KB = 2 MB heap, safe on a 4 GB phone).
        const val DOWNLOAD_CONNECTIONS = 8
        const val DOWNLOAD_BUFFER_BYTES = 256 * 1024
        // Below this size one stream is fine (a probe + 8 connections would cost more than it saves).
        const val PARALLEL_MIN_BYTES = 16L * 1024 * 1024
        // A chunk smaller than this is not worth its own connection.
        const val MIN_CHUNK_BYTES = 2L * 1024 * 1024
        const val MAX_DOWNLOAD_ATTEMPTS = 6
        const val CHUNK_MANIFEST_SUFFIX = ".chunks"
        private val MANIFEST_MAGIC = byteArrayOf(0x58, 0x44, 0x4C, 0x31) // "XDL1"
        private const val MANIFEST_HEADER_BYTES = 24L // magic(4) + total(8) + chunkSize(8) + count(4)

        /** The chunk-manifest sidecar for a partial download: [dest] + ".chunks". */
        fun manifestFile(dest: File): File = File(dest.absolutePath + CHUNK_MANIFEST_SUFFIX)

        /** How a file of [total] bytes splits into at most [connections] ranges. Pure, unit-tested. */
        internal data class ChunkPlan(val total: Long, val chunkSize: Long, val ranges: List<Pair<Long, Long>>)
        internal fun chunkPlan(total: Long, connections: Int): ChunkPlan {
            val n = connections.coerceAtMost(maxOf(1, (total / MIN_CHUNK_BYTES).toInt())).coerceAtLeast(1)
            val size = (total + n - 1) / n
            val ranges = (0 until n).map { i -> val s = i * size; s to minOf(total, s + size) }
            return ChunkPlan(total, size, ranges)
        }

        /**
         * Per-chunk progress for a parallel download. [doneThrough] is the absolute file offset up
         * to which chunk i's bytes are on disk; chunk i owns [start(i), end(i)) and only ever
         * writes inside it, sequentially from its resume point. A chunk counts as done only when
         * its bytes are flushed to disk (fd.sync) and its manifest entry is persisted after that,
         * so a crash mid-chunk re-downloads that chunk and never corrupts the file. The manifest
         * is the only record of which ranges are valid: a parallel partial's file LENGTH is not
         * (later chunks write at high offsets first, so length can read near-total with holes).
         * The whole-file SHA-256 the callers check afterwards is the final backstop.
         */
        private class ChunkManifest(val total: Long, val chunkSize: Long, val doneThrough: LongArray) {
            val count: Int get() = doneThrough.size
            fun start(i: Int): Long = i * chunkSize
            fun end(i: Int): Long = minOf(total, (i + 1) * chunkSize)
            @Synchronized fun getDone(i: Int): Long = doneThrough[i]
            @Synchronized fun setDone(i: Int, pos: Long) { doneThrough[i] = pos }
            @Synchronized fun isComplete(): Boolean = (0 until count).all { doneThrough[it] >= end(it) }
            @Synchronized fun doneBytes(): Long = (0 until count).sumOf { (doneThrough[it] - start(it)).coerceAtLeast(0) }
        }

        /** One open handle on a manifest file; entry writes are single 8-byte writes, fsync only on chunk completion. */
        private class ManifestStore(file: File, private val manifest: ChunkManifest) : java.io.Closeable {
            private val raf = RandomAccessFile(file, "rw")
            @Synchronized fun update(i: Int, pos: Long) {
                raf.seek(MANIFEST_HEADER_BYTES + 8L * i)
                raf.writeLong(pos)
            }
            @Synchronized fun sync() { raf.fd.sync() }
            override fun close() { runCatching { raf.close() } }
        }

        private fun readManifest(dest: File): ChunkManifest? {
            val f = manifestFile(dest)
            if (!f.isFile) return null
            return try {
                DataInputStream(f.inputStream().buffered()).use { d ->
                    val magic = ByteArray(4).also { d.readFully(it) }
                    if (!magic.contentEquals(MANIFEST_MAGIC)) return null
                    val total = d.readLong()
                    val chunkSize = d.readLong()
                    val count = d.readInt()
                    if (total <= 0 || chunkSize <= 0 || count < 1 || count > 64) return null
                    if ((count - 1) * chunkSize >= total || count * chunkSize < total) return null
                    if (f.length() != MANIFEST_HEADER_BYTES + 8L * count) return null
                    val done = LongArray(count) { i ->
                        val v = d.readLong()
                        val s = i * chunkSize
                        val e = minOf(total, s + chunkSize)
                        if (v < s || v > e) return null
                        v
                    }
                    ChunkManifest(total, chunkSize, done)
                }
            } catch (_: Exception) { null }
        }

        private fun writeManifestFile(file: File, man: ChunkManifest) {
            file.parentFile?.mkdirs()
            DataOutputStream(file.outputStream().buffered()).use { d ->
                d.write(MANIFEST_MAGIC)
                d.writeLong(man.total); d.writeLong(man.chunkSize); d.writeInt(man.count)
                for (i in 0 until man.count) d.writeLong(man.doneThrough[i])
                d.flush()
            }
        }

        /**
         * Loads the manifest for a parallel resume, or builds one from a legacy single-stream
         * partial (a prefix: every byte below its length is valid). A manifest whose entries
         * claim bytes past the file's end (external truncation) is discarded the same way.
         */
        private fun loadOrCreateManifest(dest: File, plan: ChunkPlan): ChunkManifest {
            readManifest(dest)?.let { old ->
                if (old.total == plan.total && old.chunkSize == plan.chunkSize && old.count == plan.ranges.size) {
                    val len = if (dest.exists()) dest.length() else 0L
                    if ((0 until old.count).all { old.getDone(it) <= len }) return old
                }
            }
            val have = if (dest.exists()) dest.length() else 0L
            val fresh = ChunkManifest(plan.total, plan.chunkSize, LongArray(plan.ranges.size) { i ->
                minOf(maxOf(have, plan.ranges[i].first), plan.ranges[i].second)
            })
            writeManifestFile(manifestFile(dest), fresh)
            return fresh
        }

        private class ProbeResult(val resolvedUrl: String, val total: Long, val rangeSupported: Boolean)

        /**
         * One cheap request that answers three questions: where does this URL really point
         * (the Hugging Face resolve URL 302-redirects to a signed CDN URL, and re-resolving
         * costs ~1 s plus resolver rate-limit budget per chunk, so chunks reuse this URL),
         * how big is the file, and does the server honour Range. A 1-byte Range GET: the
         * CDN answers 206 with Content-Range "bytes 0-0/<total>"; anything else means no Range.
         */
        private fun probe(url: String, expected: Long): ProbeResult {
            val call = Http.download.newCall(
                Request.Builder().url(url).header("User-Agent", Http.UA).header("Range", "bytes=0-0").build()
            )
            call.execute().use { r ->
                check(r.isSuccessful) { "Download failed (HTTP ${r.code})" }
                val resolved = r.request.url.toString()
                if (r.code == 206) {
                    val total = r.header("Content-Range")?.substringAfter('/', "")?.toLongOrNull() ?: -1L
                    return ProbeResult(resolved, total, true)
                }
                return ProbeResult(resolved, expected, false)
            }
        }

        /** A failed chunk attempt waits this long (times the attempt number); interruptible by cancel. */
        private fun sleepBackoff(attempt: Int, isCancelled: () -> Boolean) {
            val budgetMs = 1500L * attempt
            var slept = 0L
            while (slept < budgetMs) {
                if (isCancelled()) throw CancellationException("Download cancelled")
                try { Thread.sleep(minOf(100L, budgetMs - slept)) } catch (_: InterruptedException) { Thread.currentThread().interrupt(); throw CancellationException("Download cancelled") }
                slept += 100
            }
        }

        /** The server stopped honouring Range mid-download: abort the chunks, fall back to one stream. */
        private class FallbackToSingleStream : Exception("Server stopped honouring Range requests")

        /**
         * Downloads [index]'s range from [urlRef] (the probe's resolved CDN URL) into [dest] at the
         * right offset, resuming from the manifest. Up to 6 attempts per chunk, same discipline as
         * the old whole-download retries, but a failed chunk retries only its own range: finished
         * chunks are never re-downloaded. A 401/403 means the signed CDN URL expired, so the URL
         * is re-resolved once and the chunk retries. A non-206 answer means Range went away: every
         * other chunk's call is cancelled and the whole download falls back to one stream.
         */
        private fun downloadChunk(
            urlRef: AtomicReference<String>, refreshUrl: () -> Unit, dest: File,
            manifest: ChunkManifest, store: ManifestStore, index: Int,
            isCancelled: () -> Boolean, calls: ConcurrentHashMap<Int, Call>,
            rangeUnsupported: AtomicBoolean, report: () -> Unit,
        ) {
            val start = manifest.start(index)
            val end = manifest.end(index)
            var attempt = 0
            while (true) {
                if (isCancelled()) throw CancellationException("Download cancelled")
                val from = manifest.getDone(index)
                if (from >= end) return
                try {
                    val call = Http.download.newCall(
                        Request.Builder().url(urlRef.get()).header("User-Agent", Http.UA)
                            .header("Range", "bytes=$from-${end - 1}").build()
                    )
                    calls[index] = call
                    try {
                        call.execute().use { r ->
                            if (r.code == 401 || r.code == 403) {
                                refreshUrl()
                                throw IOException("CDN link expired (HTTP ${r.code}); re-resolved")
                            }
                            if (r.code != 206) {
                                rangeUnsupported.set(true)
                                calls.values.forEach { runCatching { it.cancel() } }
                                throw FallbackToSingleStream()
                            }
                            val range = r.header("Content-Range").orEmpty()
                            if (range.isNotEmpty() && !range.startsWith("bytes $from-")) throw IOException("Server answered the wrong range ($range)")
                            RandomAccessFile(dest, "rw").use { f ->
                                f.seek(from)
                                var pos = from
                                val src = r.body!!.byteStream()
                                val buf = ByteArray(DOWNLOAD_BUFFER_BYTES)
                                while (true) {
                                    if (isCancelled()) throw CancellationException("Download cancelled")
                                    val n = src.read(buf)
                                    if (n < 0) break
                                    f.write(buf, 0, n)
                                    pos += n
                                    manifest.setDone(index, pos)
                                    store.update(index, pos)
                                    report()
                                }
                                if (pos != end) throw IOException("Chunk ended early ($pos of $end bytes)")
                                f.fd.sync()
                            }
                            manifest.setDone(index, end)
                            store.update(index, end)
                            store.sync()
                            report()
                        }
                    } finally {
                        calls.remove(index)
                    }
                    return
                } catch (e: CancellationException) { throw e }
                  catch (e: FallbackToSingleStream) { throw e }
                  catch (e: IOException) {
                    if (isCancelled()) throw CancellationException("Download cancelled")
                    if (++attempt >= MAX_DOWNLOAD_ATTEMPTS) throw e
                    sleepBackoff(attempt, isCancelled)
                }
            }
        }

        /**
         * The parallel half of [downloadResumable]. Returns true on success, false when the server
         * stopped honouring Range (the caller falls back to one stream). Throws CancellationException
         * on cancel — every chunk's live call is cancelled first, so a blocked read aborts at once —
         * and IllegalStateException when a chunk exhausts its 6 attempts. The partial file plus its
         * manifest stay behind on failure, so the next run resumes the unfinished chunks.
         */
        private fun downloadParallel(
            url: String, dest: File, total: Long, connections: Int,
            isCancelled: () -> Boolean, progress: (Long, Long) -> Unit,
        ): Boolean {
            val plan = chunkPlan(total, connections)
            val manifest = loadOrCreateManifest(dest, plan)
            val calls = ConcurrentHashMap<Int, Call>()
            val rangeUnsupported = AtomicBoolean(false)
            val urlRef = AtomicReference(url)
            val resolveLock = Any()
            fun refreshUrl() {
                synchronized(resolveLock) {
                    val fresh = probe(url, total)
                    if (fresh.total != total) throw IOException("Server file changed size while downloading")
                    urlRef.set(fresh.resolvedUrl)
                }
            }
            val progressLock = Any()
            var lastReported = 0L
            fun report() {
                val done = manifest.doneBytes()
                synchronized(progressLock) {
                    if (done > lastReported) { lastReported = done; progress(done, total) }
                }
            }
            val pool = Executors.newFixedThreadPool(plan.ranges.size) { r -> Thread(r, "x-dl-chunk").apply { isDaemon = true } }
            val store = ManifestStore(manifestFile(dest), manifest)
            try {
                val futures = plan.ranges.indices.map { i ->
                    pool.submit(Callable {
                        downloadChunk(urlRef, ::refreshUrl, dest, manifest, store, i, isCancelled, calls, rangeUnsupported, ::report)
                    })
                }
                pool.shutdown()
                while (true) {
                    if (isCancelled()) {
                        calls.values.forEach { runCatching { it.cancel() } }
                        pool.shutdownNow()
                        throw CancellationException("Download cancelled")
                    }
                    if (rangeUnsupported.get()) {
                        calls.values.forEach { runCatching { it.cancel() } }
                        pool.shutdownNow()
                        return false
                    }
                    if (futures.all { it.isDone }) break
                    try { Thread.sleep(50) } catch (_: InterruptedException) { Thread.currentThread().interrupt(); throw CancellationException("Download cancelled") }
                }
                var failure: Throwable? = null
                for (f in futures) {
                    try { f.get() } catch (e: java.util.concurrent.ExecutionException) { if (failure == null) failure = e.cause ?: e }
                }
                if (failure != null) {
                    if (failure is CancellationException) throw failure
                    throw IllegalStateException("Download stopped: ${failure.message}. Check the internet and tap install again — it resumes.")
                }
                if (dest.length() != total) throw IllegalStateException("Download stopped: got ${dest.length()} of $total bytes. Tap install again — it resumes.")
                manifestFile(dest).delete()
                progress(total, total)
                return true
            } finally {
                runCatching { store.close() }
                pool.shutdownNow()
            }
        }

        /**
         * Downloads a file, resuming from a partial file when one exists. The entry point keeps its
         * contract: same arguments (plus optional [connections] / [isCancelled], both defaulted so
         * every existing caller is unchanged), same progress callback (monotonic, ends at the total),
         * same resume behaviour, same final size check, same 6-retry discipline, same error text.
         * The one fix callers can feel: cancel now stops the download at once (the old blocking
         * read could stall up to the 120 s read timeout), via [isCancelled] — LocalModels passes
         * its job's state; the default is the thread's interrupt flag.
         *
         * What made model downloads slow, measured 2026-11-20: this was one connection, one
         * stream, 64 KB buffer. The Hugging Face resolve URL 302-redirects (measured 0.7–1.1 s
         * per resolve from this sandbox) to a signed CloudFront URL on us.aws.cdn.hf.co, which
         * answers 206 / Accept-Ranges: bytes — so the file CAN be fetched in parallel ranges,
         * and the resolve only needs paying once. There is no verified faster host for these
         * files (no documented HF mirror; hf-mirror.com is unofficial and unverified, so it is
         * NOT used) — the speedup is parallelism plus resolve-once, not a different host. The
         * sandbox proxy caps at ~2–6 MB/s either way, so sandbox speed is not phone speed and
         * no phone speedup is claimed here; ParallelDownloadTest proves the mechanism against a
         * per-connection-throttled local server instead.
         */
        fun downloadResumable(
            url: String, dest: File, expected: Long,
            connections: Int = DOWNLOAD_CONNECTIONS,
            isCancelled: () -> Boolean = { Thread.currentThread().isInterrupted },
            progress: (Long, Long) -> Unit,
        ) {
            dest.parentFile?.mkdirs()
            // A chunk manifest from an earlier parallel attempt describes which byte ranges are on
            // disk. If it belongs to a different file (the expected size changed) or its file is
            // gone, it is stale: drop it rather than trusting it.
            val prior = readManifest(dest)
            if (prior != null && !dest.exists()) manifestFile(dest).delete()
            else if (prior != null && expected > 0 && prior.total != expected) { dest.delete(); manifestFile(dest).delete() }
            if (expected > 0 && dest.exists()) {
                val len = dest.length()
                if (len > expected) { dest.delete(); manifestFile(dest).delete() }
                // A parallel partial can reach the full length while still having holes, so the
                // length alone only proves completion when there is no manifest or it agrees.
                else if (len == expected && (prior == null || prior.isComplete())) return
            }
            if (expected >= PARALLEL_MIN_BYTES && connections > 1) {
                var attempt = 0
                while (true) {
                    try {
                        val probe = probe(url, expected)
                        if (probe.rangeSupported && probe.total == expected) {
                            if (downloadParallel(probe.resolvedUrl, dest, expected, connections, isCancelled, progress)) return
                            break // the server stopped honouring Range mid-download: one stream instead
                        }
                        break // no Range support (or a different size): the old single-stream path
                    } catch (e: IOException) {
                        if (isCancelled()) throw CancellationException("Download cancelled")
                        if (++attempt >= MAX_DOWNLOAD_ATTEMPTS) throw IllegalStateException("Download stopped: ${e.message}. Check the internet and tap install again — it resumes.")
                        sleepBackoff(attempt, isCancelled)
                    }
                }
            }
            downloadSingleStream(url, dest, expected, isCancelled, progress)
        }

        /**
         * The old single-stream path, kept for small files, unknown sizes and servers without
         * Range support. Same resume (Range: bytes=have-), same 6 retries, same error text — plus
         * a 256 KB buffer (was 64 KB), a fail-fast check when the server's stated size disagrees
         * with [expected] (the old code downloaded the whole file up to 3 times before noticing),
         * and a guard against the old endless loop on a persistent size mismatch. A partial
         * written by the parallel path is NOT a prefix (later ranges may already be on disk), so
         * resuming it as one stream would leave holes: it is deleted and started over instead.
         * Cancel is prompt here too: a watcher thread cancels the live call as soon as
         * [isCancelled] turns true, so the blocked read fails at once instead of after 120 s.
         */
        private fun downloadSingleStream(
            url: String, dest: File, expected: Long,
            isCancelled: () -> Boolean, progress: (Long, Long) -> Unit,
        ) {
            dest.parentFile?.mkdirs()
            val man = readManifest(dest)
            if (man != null && !man.isComplete()) { dest.delete(); manifestFile(dest).delete() }
            var attempt = 0
            var sizeMismatches = 0
            val callRef = AtomicReference<Call?>(null)
            val stopWatcher = AtomicBoolean(false)
            val watcher = Thread {
                while (!stopWatcher.get()) {
                    if (isCancelled()) { callRef.get()?.cancel(); return@Thread }
                    try { Thread.sleep(100) } catch (_: InterruptedException) { return@Thread }
                }
            }.apply { isDaemon = true; start() }
            try {
                while (true) {
                    if (isCancelled()) throw CancellationException("Download cancelled")
                    val have = if (dest.exists()) dest.length() else 0L
                    if (expected > 0 && have == expected) { manifestFile(dest).delete(); return }
                    if (expected > 0 && have > expected) dest.delete()
                    try {
                        val rb = Request.Builder().url(url).header("User-Agent", Http.UA)
                        if (have > 0) rb.header("Range", "bytes=$have-")
                        val call = Http.download.newCall(rb.build())
                        callRef.set(call)
                        call.execute().use { r ->
                            check(r.isSuccessful) { "Download failed (HTTP ${r.code})" }
                            val append = r.code == 206
                            // Fail fast when the server's file is a different size than expected,
                            // instead of downloading it and looping (the old behaviour).
                            val serverTotal = when {
                                r.code == 206 -> r.header("Content-Range")?.substringAfter('/', "")?.toLongOrNull() ?: -1L
                                else -> r.body?.contentLength() ?: -1L
                            }
                            if (expected > 0 && serverTotal > 0 && serverTotal != expected) {
                                throw IllegalStateException("Download stopped: the file on the server is $serverTotal bytes, expected $expected.")
                            }
                            val total = if (expected > 0) expected else (r.body!!.contentLength() + if (append) have else 0)
                            var done = if (append) have else 0L
                            FileOutputStream(dest, append).use { o ->
                                val src = r.body!!.byteStream(); val buf = ByteArray(DOWNLOAD_BUFFER_BYTES); var last = 0L
                                while (true) {
                                    val n = src.read(buf); if (n < 0) break
                                    o.write(buf, 0, n); done += n
                                    if (done - last > 512 * 1024) {
                                        if (isCancelled()) throw CancellationException("Download cancelled")
                                        progress(done, total); last = done
                                    }
                                }
                            }
                            progress(done, total)
                        }
                        callRef.set(null)
                        if (expected <= 0 || dest.length() == expected) { manifestFile(dest).delete(); return }
                        if (++sizeMismatches >= 3) throw IllegalStateException("Download stopped: the file on the server is ${dest.length()} bytes, expected $expected.")
                    } catch (e: CancellationException) { throw e }
                      catch (e: IOException) {
                        if (isCancelled()) throw CancellationException("Download cancelled")
                        if (++attempt >= MAX_DOWNLOAD_ATTEMPTS) throw IllegalStateException("Download stopped: ${e.message}. Check the internet and tap install again — it resumes.")
                        sleepBackoff(attempt, isCancelled)
                    }
                }
            } finally {
                stopWatcher.set(true)
            }
        }

        fun sha256(f: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            f.inputStream().use { i -> val b = ByteArray(1 shl 16); while (true) { val n = i.read(b); if (n < 0) break; md.update(b, 0, n) } }
            return md.digest().joinToString("") { "%02x".format(it) }
        }

        /**
         * The directory a [name] entry's bytes (or link) must be created in,
         * resolving symlinks in the parent chain by hand. File.canonicalPath
         * does NOT follow a symlink whose target does not exist yet (verified
         * with a standalone repro: canonicalPath of dest/evil/pwned.txt stays
         * lexical when dest/evil -> a not-yet-created dir), so mkdirs() on the
         * canonical parent silently no-ops and the write fails with
         * FileNotFoundException — the TarExtractSecurityTest case (a rewritten
         * absolute link pointing at a path inside dest that no entry created
         * yet). Files.readSymbolicLink works on dangling links, so walk the
         * components and follow them. The result stays inside [dest] because
         * every planted link was validated to stay inside it.
         */
        private fun resolvedParentDir(dest: File, name: String): File {
            var cur = dest.toPath().toAbsolutePath().normalize()
            for (part in name.split('/').dropLast(1)) {
                if (part.isEmpty() || part == ".") continue
                cur = if (part == "..") cur.parent ?: cur else cur.resolve(part)
                var hops = 0
                while (java.nio.file.Files.isSymbolicLink(cur) && hops++ < 40) {
                    val t = java.nio.file.Files.readSymbolicLink(cur)
                    cur = if (t.isAbsolute) t.normalize() else cur.parent.resolve(t).normalize()
                }
            }
            return cur.toFile()
        }

        /** tar.gz → dir with symlinks, hard links and modes kept.
         * Symlink policy (redesign round 2, Addendum A): an absolute target is REWRITTEN to a
         * path relative to the link's parent inside [dest] — rootfs tarballs legitimately
         * contain rootfs-absolute links (e.g. /usr/bin/foo -> /etc/alternatives/foo), and
         * resolving them against the host used to abort the whole install with "Unsafe symlink
         * target in archive". A target that still escapes [dest] after normalisation (absolute
         * or relative) cannot be load-bearing inside the rootfs, so the link is SKIPPED and
         * reported via [onSkipped] instead of aborting — nothing is ever written outside
         * [dest]. Entry paths that escape [dest] and hard-link targets that escape are still
         * refused outright (the whole extraction fails). */
        fun extractTarGz(archive: File, dest: File, onCount: (Int) -> Unit = {}, onSkipped: (String) -> Unit = {}) {
            val canonical = dest.canonicalPath
            // A path is inside the destination only on a real boundary: "/tmp/x2" is not inside "/tmp/x".
            fun inside(path: String) = path == canonical || path.startsWith(canonical + File.separator)
            val hardLinks = mutableListOf<Pair<File, File>>()
            var n = 0
            TarArchiveInputStream(GZIPInputStream(BufferedInputStream(archive.inputStream(), 1 shl 16))).use { tar ->
                var e: TarArchiveEntry? = tar.nextEntry
                while (e != null) {
                    val name = e.name.removePrefix("./").trimEnd('/')
                    if (name.isNotEmpty() && !name.startsWith("dev/")) {
                        val target = File(dest, name)
                        // P0-8 (HI-6): symlinks are NO LONGER exempt from the path check — an entry
                        // named "../escape" was waved through before. target.canonicalPath also
                        // resolves symlinks planted by EARLIER entries, so a file written through
                        // such a link is caught here as well.
                        require(inside(target.canonicalPath)) { "Unsafe path in archive: $name" }
                        when {
                            e.isDirectory -> target.mkdirs()
                            e.isSymbolicLink -> {
                                // The link TARGET (e.linkName) is an arbitrary path in the archive.
                                // Absolute targets are rewritten to dest-relative (see the KDoc);
                                // escaping targets are skipped and reported, never planted.
                                val raw = e.linkName
                                val canonDest = java.nio.file.Paths.get(canonical)
                                val linkParent = canonDest.resolve(name).parent
                                val stored: String? = if (File(raw).isAbsolute) {
                                    val abs = canonDest.resolve(raw.trimStart('/')).normalize()
                                    if (!inside(abs.toString())) {
                                        onSkipped("Skipped symlink $name -> $raw (target escapes the extraction directory)"); null
                                    } else linkParent.relativize(abs).toString().ifEmpty { "." }
                                } else {
                                    val resolved = linkParent.resolve(raw).normalize()
                                    if (!inside(resolved.toString())) {
                                        onSkipped("Skipped symlink $name -> $raw (target escapes the extraction directory)"); null
                                    } else raw
                                }
                                if (stored != null) {
                                    // A parent reached through a rewritten symlink may not
                                    // exist yet — resolve it by hand (see resolvedParentDir).
                                    resolvedParentDir(dest, name).mkdirs(); target.delete()
                                    // Os.symlink is a silent no-op in JVM unit tests (returnDefaultValues),
                                    // so verify the link really exists and fall back to the NIO call.
                                    val linked = runCatching { Os.symlink(stored, target.absolutePath) }.isSuccess &&
                                        java.nio.file.Files.isSymbolicLink(target.toPath())
                                    if (!linked) java.nio.file.Files.createSymbolicLink(target.toPath(), java.nio.file.Paths.get(stored))
                                }
                            }
                            e.isLink -> {
                                // Hard-link targets are archive-root-relative; an absolute one used
                                // to escape File(dest, …) entirely. Same rule as symlinks.
                                // NOTE: java.io.File(dest, "/abs") does NOT anchor — it concatenates
                                // the absolute child onto the parent (dest/abs) — so an absolute
                                // target must be resolved directly, or the escape check is blind.
                                val src = if (File(e.linkName).isAbsolute) File(e.linkName) else File(dest, e.linkName.removePrefix("./"))
                                require(inside(src.canonicalPath)) { "Unsafe hard link target in archive: $name -> ${e.linkName}" }
                                hardLinks += target to src
                            }
                            e.isFile -> {
                                // Same as the symlink branch: a parent reached through a
                                // rewritten symlink may not exist yet — resolve it by hand
                                // (see resolvedParentDir), or the write fails with
                                // FileNotFoundException even though the path is safely
                                // inside dest (TarExtractSecurityTest).
                                resolvedParentDir(dest, name).mkdirs()
                                FileOutputStream(target).use { tar.copyTo(it, 1 shl 16) }
                                runCatching { Os.chmod(target.absolutePath, e.mode and "7777".toInt(8)) }.onFailure { target.setExecutable(e.mode and 0b001001001 != 0, false) }
                            }
                        }
                        if (++n % 200 == 0) onCount(n)
                    }
                    e = tar.nextEntry
                }
            }
            hardLinks.forEach { (link, src) ->
                // Re-check now that every entry exists: the source's real location (through any symlink) must still be inside dest.
                require(inside(src.canonicalPath)) { "Unsafe hard link target in archive: ${src.path}" }
                resolvedParentDir(dest, link.toRelativeString(dest)).mkdirs(); link.delete()
                // Os.link is a silent no-op in JVM unit tests (returnDefaultValues) — verify
                // the link really exists and fall back to copying the source's bytes.
                val linked = runCatching { Os.link(src.absolutePath, link.absolutePath) }.isSuccess && link.exists()
                if (!linked) { src.copyTo(link, true); link.setExecutable(src.canExecute(), false) }
            }
            onCount(n)
        }

        fun duBytes(f: File): Long {
            var total = 0L
            val stack = ArrayDeque<File>().apply { add(f) }
            while (stack.isNotEmpty()) {
                val x = stack.removeLast()
                if (java.nio.file.Files.isSymbolicLink(x.toPath())) continue
                if (x.isDirectory) x.listFiles()?.forEach { stack.add(it) } else total += x.length()
            }
            return total
        }
    }
}

internal fun File.readTextOrNull(): String? = runCatching { if (isFile) readText().trim() else null }.getOrNull()

/** Run [block] holding the mutex; if another caller holds it, wait for it instead of running twice. */
internal suspend fun <T> kotlinx.coroutines.sync.Mutex.withLockIfFree(block: suspend () -> T): T = lock().let { try { block() } finally { unlock() } }
