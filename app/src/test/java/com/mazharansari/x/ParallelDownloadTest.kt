package com.mazharansari.x

import com.mazharansari.x.linux.LinuxRuntime
import kotlinx.coroutines.CancellationException
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Parallel chunked downloads (2026-11-20): the old path was one connection, one stream, 64 KB
 * buffer. These tests prove the mechanism against a local server that throttles EACH connection
 * (2 MB/s, or 512 KB/s for the cancel test), the way a per-connection-throttled CDN behaves:
 * one stream is capped by the throttle, 8 ranges are not.
 *
 * What this proves: the parallel path is really faster than the single-stream path through the
 * same throttled server, byte-identical (SHA-256), resumable, and falls back when Range is
 * unsupported. What it does NOT prove: any speed on the user's phone or network — the sandbox
 * proxy caps at ~2-6 MB/s either way, and only his phone on his network can show the real
 * 1-2 GB download time. The test would fail on the old code: it calls downloadResumable with
 * connections = 8, which the old signature does not have, and a parallel path that ignored
 * that argument would fail the faster-than-single assertion.
 *
 * Plain JUnit (no Robolectric): the download path is pure JVM — OkHttp, files, threads.
 */
class ParallelDownloadTest {
    private lateinit var tmp: File
    private val servers = mutableListOf<ThrottledServer>()

    @Before fun up() {
        tmp = File(System.getProperty("java.io.tmpdir"), "parallel-dl-${System.nanoTime()}").apply { mkdirs() }
    }

    @After fun down() {
        servers.forEach { it.close() }
        tmp.deleteRecursively()
    }

    private fun content(mb: Int): ByteArray = ByteArray(mb * 1024 * 1024) { (it * 31 % 251).toByte() }

    private fun sha256Bytes(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun timedMs(block: () -> Unit): Long {
        val t0 = System.nanoTime()
        block()
        return (System.nanoTime() - t0) / 1_000_000
    }

    /**
     * Downloads with progress recorded. Progress must be monotonic (never backwards) and end
     * exactly at the total; any violation is reported after the download returns, so an assertion
     * failure on a worker thread cannot be swallowed by the downloader's retry handling.
     */
    private fun download(url: String, dest: File, expected: Long, connections: Int, isCancelled: () -> Boolean = { false }) {
        val seen = mutableListOf<Long>()
        val lock = Any()
        val violation = AtomicReference<String?>(null)
        LinuxRuntime.downloadResumable(url, dest, expected, connections = connections, isCancelled = isCancelled) { done, total ->
            synchronized(lock) {
                if (seen.isNotEmpty() && done < seen.last()) violation.compareAndSet(null, "progress went backwards: $done after ${seen.last()}")
                if (done > total) violation.compareAndSet(null, "progress $done exceeded total $total")
                seen += done
            }
        }
        assertNull(violation.get())
        assertEquals("final progress should be the total", expected, seen.lastOrNull() ?: -1L)
    }

    @Test(timeout = 120_000) fun parallelIsFasterThanSingleAndByteIdentical() {
        val bytes = content(16)
        val expectedSha = sha256Bytes(bytes)
        val server = ThrottledServer(bytes, bytesPerSecPerConnection = 2L * 1024 * 1024)
        servers += server
        val url = "${server.base}/model.gguf"

        // The old path: one connection, one stream, capped by the per-connection throttle.
        val singleDest = File(tmp, "single.part")
        val singleMs = timedMs { download(url, singleDest, bytes.size.toLong(), connections = 1) }
        assertEquals(expectedSha, LinuxRuntime.sha256(singleDest))

        // The new path: 8 ranges in parallel through the same throttle.
        val parDest = File(tmp, "parallel.part")
        val parMs = timedMs { download(url, parDest, bytes.size.toLong(), connections = 8) }
        assertEquals(expectedSha, LinuxRuntime.sha256(parDest))

        val singleMBs = bytes.size / 1048576.0 / (singleMs / 1000.0)
        val parMBs = bytes.size / 1048576.0 / (parMs / 1000.0)
        println("parallel-download: single ${"%.2f".format(singleMBs)} MB/s (${singleMs} ms) vs parallel ${"%.2f".format(parMBs)} MB/s (${parMs} ms)")
        assertTrue("parallel ${parMs} ms should beat single ${singleMs} ms by a clear margin", parMs < singleMs * 0.6)
    }

    @Test(timeout = 120_000) fun cancelThenResumeIsByteIdentical() {
        val bytes = content(16)
        val expectedSha = sha256Bytes(bytes)
        val server = ThrottledServer(bytes, bytesPerSecPerConnection = 512L * 1024)
        servers += server
        val url = "${server.base}/model.gguf"
        val dest = File(tmp, "resume.part")

        // Cancel once half the file has been reported, from the progress callback itself.
        val cancelAt = AtomicBoolean(false)
        var cancelled = false
        try {
            LinuxRuntime.downloadResumable(url, dest, bytes.size.toLong(), connections = 8,
                isCancelled = { cancelAt.get() }) { done, _ -> if (done >= bytes.size / 2) cancelAt.set(true) }
            fail("expected CancellationException")
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue("the download should have been cancelled", cancelled)
        assertTrue("a resumable partial must be left behind", dest.exists())
        assertTrue("the chunk manifest must be left behind for resume", LinuxRuntime.manifestFile(dest).isFile)
        assertNotEquals("the partial must not already be complete", expectedSha, LinuxRuntime.sha256(dest))

        // Resume: finished chunks must not be re-downloaded, and the result must be byte-identical.
        val servedBefore = server.servedBytes.get()
        download(url, dest, bytes.size.toLong(), connections = 8)
        assertEquals(expectedSha, LinuxRuntime.sha256(dest))
        assertFalse("the manifest is deleted once the download completes", LinuxRuntime.manifestFile(dest).exists())
        val servedSecondRun = server.servedBytes.get() - servedBefore
        println("parallel-download: resume served ${servedSecondRun / 1048576.0} MB of ${bytes.size / 1048576} MB on the second run")
        assertTrue("resume should not re-download finished chunks (served $servedSecondRun of ${bytes.size})", servedSecondRun < bytes.size * 0.9)
    }

    @Test(timeout = 120_000) fun rangeUnsupportedFallsBackToSingleStream() {
        val bytes = content(4)
        val expectedSha = sha256Bytes(bytes)
        val server = ThrottledServer(bytes, bytesPerSecPerConnection = 8L * 1024 * 1024, supportRange = false)
        servers += server
        val url = "${server.base}/model.gguf"

        val dest = File(tmp, "fallback.part")
        download(url, dest, bytes.size.toLong(), connections = 8)
        assertEquals(expectedSha, LinuxRuntime.sha256(dest))
        assertEquals("no 206 was ever served", 0, server.rangeRequests.get())

        // A legacy prefix partial also completes: the server ignores Range, so it restarts from 0.
        val dest2 = File(tmp, "fallback2.part")
        dest2.writeBytes(bytes.copyOfRange(0, 1024 * 1024))
        download(url, dest2, bytes.size.toLong(), connections = 8)
        assertEquals(expectedSha, LinuxRuntime.sha256(dest2))
    }

    @Test(timeout = 60_000) fun smallFileUsesSingleStream() {
        val bytes = content(1)
        val expectedSha = sha256Bytes(bytes)
        val server = ThrottledServer(bytes, bytesPerSecPerConnection = 8L * 1024 * 1024)
        servers += server
        val dest = File(tmp, "small.part")
        download("${server.base}/model.gguf", dest, bytes.size.toLong(), connections = 8)
        assertEquals(expectedSha, LinuxRuntime.sha256(dest))
        assertEquals("a 1 MB file needs no probe and no ranges: one plain GET", 1, server.requests.get())
        assertEquals(0, server.rangeRequests.get())
    }

    @Test(timeout = 120_000) fun legacyPrefixResumeSkipsDownloadedBytes() {
        val bytes = content(16)
        val expectedSha = sha256Bytes(bytes)
        val server = ThrottledServer(bytes, bytesPerSecPerConnection = 8L * 1024 * 1024)
        servers += server
        val dest = File(tmp, "legacy.part")
        // The first 6 MB are exactly the first 3 of 8 chunks (2 MB each): they must not be fetched again.
        dest.writeBytes(bytes.copyOfRange(0, 6 * 1024 * 1024))
        val servedBefore = server.servedBytes.get()
        download("${server.base}/model.gguf", dest, bytes.size.toLong(), connections = 8)
        assertEquals(expectedSha, LinuxRuntime.sha256(dest))
        val served = server.servedBytes.get() - servedBefore
        assertTrue("served $served bytes, expected about 10 MB of chunks plus a 1-byte probe", served in (10L * 1024 * 1024)..(11L * 1024 * 1024))
    }

    @Test fun chunkPlanCoversFileExactly() {
        val plan = LinuxRuntime.chunkPlan(1_099_627_808L, 8)
        assertEquals(8, plan.ranges.size)
        assertEquals(0L, plan.ranges.first().first)
        assertEquals(1_099_627_808L, plan.ranges.last().second)
        for (i in 1 until plan.ranges.size) assertEquals(plan.ranges[i - 1].second, plan.ranges[i].first)
        val small = LinuxRuntime.chunkPlan(16L * 1024 * 1024, 8)
        assertEquals(8, small.ranges.size)
        assertTrue(small.ranges.all { (it.second - it.first) == 2L * 1024 * 1024 })
    }

    /**
     * A tiny HTTP server on localhost: answers Range with 206 + Content-Range when
     * [supportRange] is set, 200 otherwise, and paces every connection to
     * [bytesPerSecPerConnection] so one stream is measurably slower than 8 in parallel.
     * Counts body bytes served, requests seen, and 206 answers, so the tests can prove what
     * was (and was not) re-downloaded.
     */
    private class ThrottledServer(
        private val bytes: ByteArray,
        private val bytesPerSecPerConnection: Long,
        private val supportRange: Boolean = true,
    ) {
        private val srv = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val base: String get() = "http://127.0.0.1:${srv.localPort}"
        val servedBytes = AtomicLong(0)
        val rangeRequests = AtomicInteger(0)
        val requests = AtomicInteger(0)

        init {
            Thread {
                while (!srv.isClosed) {
                    val sock = runCatching { srv.accept() }.getOrNull() ?: break
                    Thread { handle(sock) }.apply { isDaemon = true; start() }
                }
            }.apply { isDaemon = true; start() }
        }

        fun close() { runCatching { srv.close() } }

        private fun handle(sock: Socket) {
            try {
                sock.use { so ->
                    val input = BufferedInputStream(so.getInputStream())
                    fun readLine(): String? {
                        val b = ByteArrayOutputStream()
                        while (true) {
                            val c = input.read()
                            if (c < 0) return if (b.size() == 0) null else b.toString(Charsets.UTF_8)
                            if (c == '\n'.code) return b.toString(Charsets.UTF_8).trimEnd('\r')
                            b.write(c)
                        }
                    }
                    val reqLine = readLine() ?: return
                    if (reqLine.isBlank()) return
                    val headers = mutableMapOf<String, String>()
                    while (true) {
                        val h = readLine() ?: return
                        if (h.isEmpty()) break
                        headers[h.substringBefore(':').lowercase()] = h.substringAfter(':').trim()
                    }
                    requests.incrementAndGet()
                    val range = headers["range"]
                    var start = 0L
                    var end = bytes.size.toLong() - 1
                    var code = 200
                    var status = "OK"
                    if (supportRange && range != null) {
                        val m = Regex("bytes=(\\d+)-(\\d*)").find(range)
                        if (m != null) {
                            start = m.groupValues[1].toLong()
                            val endStr = m.groupValues[2]
                            end = if (endStr.isNotEmpty()) endStr.toLong() else bytes.size.toLong() - 1
                            if (start >= bytes.size || start > end) {
                                val out = so.getOutputStream()
                                out.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */${bytes.size}\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                                out.flush()
                                return
                            }
                            code = 206
                            status = "Partial Content"
                            rangeRequests.incrementAndGet()
                        }
                    }
                    val length = (end - start + 1).toInt()
                    val out = so.getOutputStream()
                    val head = StringBuilder()
                    head.append("HTTP/1.1 $code $status\r\n")
                    head.append("Content-Type: application/octet-stream\r\n")
                    head.append("Content-Length: $length\r\n")
                    head.append("Connection: close\r\n")
                    if (supportRange) head.append("Accept-Ranges: bytes\r\n")
                    if (code == 206) head.append("Content-Range: bytes $start-$end/${bytes.size}\r\n")
                    head.append("\r\n")
                    out.write(head.toString().toByteArray())
                    out.flush()
                    var sent = 0
                    val t0 = System.nanoTime()
                    while (sent < length) {
                        val n = minOf(64 * 1024, length - sent)
                        out.write(bytes, (start + sent).toInt(), n)
                        out.flush()
                        servedBytes.addAndGet(n.toLong())
                        sent += n
                        val expectedMs = sent * 1000.0 / bytesPerSecPerConnection
                        val elapsedMs = (System.nanoTime() - t0) / 1_000_000.0
                        val waitMs = (expectedMs - elapsedMs).toLong()
                        if (waitMs > 0) Thread.sleep(waitMs)
                    }
                }
            } catch (_: Exception) {
                // The client going away mid-body (cancel, fallback) is expected in these tests.
            }
        }
    }
}
