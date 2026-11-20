package com.mazharansari.x

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mazharansari.x.linux.DevTools
import com.mazharansari.x.linux.LinuxProgress
import com.mazharansari.x.linux.LinuxRuntime
import com.mazharansari.x.linux.ToolStatus
import com.mazharansari.x.linux.Toolchain
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Collections

/**
 * Install dispatch end to end (2026-11-20 JNI outage fix): Install on every toolchain must run
 * the right guest script and end in an installed state. The Linux side is faked — it answers the
 * run.sh protocol (@@P progress, @@V version, @@S size) instead of spawning PRoot, which a JVM
 * test cannot do — but everything above it is the real DevTools: enqueue, dependency ordering,
 * runJob, runScript, record saving and status. The hidden Antigravity CLI is covered by the pure
 * dispatch matrix in V04LogicTest (its real path downloads from the network and cannot run here).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = AxionApp::class)
class DevToolsInstallTest {
    private val app get() = ApplicationProvider.getApplicationContext<AxionApp>()
    private lateinit var fake: FakeLinux
    private lateinit var dt: DevTools

    /** Stands in for LinuxRuntime: Linux is "ready", and run.sh is answered from a version table. */
    private class FakeLinux(ctx: Context) : LinuxRuntime(ctx) {
        val commands = Collections.synchronizedList(mutableListOf<String>())
        var failTool: String? = null
        override fun isInstalled() = true
        override suspend fun ensureBase(onProgress: (LinuxProgress) -> Unit) {}
        override suspend fun run(
            command: String, timeoutMs: Long, guestCwd: String, binds: List<Pair<String, String>>,
            env: Map<String, String>, stdin: String?, fakeRoot: Boolean, onLine: (String) -> Unit,
        ): Pair<Int, String> {
            commands += command
            val parts = command.split(" ")
            val tool = parts.getOrNull(2) ?: "?"
            val action = parts.getOrNull(3) ?: "?"
            if (tool == failTool && action == "install") {
                onLine("ERROR: Could not install: $tool-test-pkg")
                return 1 to "ERROR: Could not install: $tool-test-pkg"
            }
            when (action) {
                "install", "update" -> {
                    onLine("@@P 50 Installing $tool")
                    onLine("@@V ${VERSIONS[tool] ?: "1.0"}")
                    onLine("@@S 1234")
                }
                "uninstall" -> { onLine("@@S 0"); onLine("@@P 100 Removed") }
            }
            return 0 to "done"
        }

        companion object {
            val VERSIONS = mapOf(
                "core" to "2.43.0", "web" to "Node 24.11.0", "python" to "3.12.3",
                "android" to "17.0.20 · Kotlin 2.2.21", "cpp" to "gcc 13.2.0",
                "php" to "8.4.27 · Composer 2.11.1", "browser" to "Chromium 131",
                "androidsdk" to "SDK 36 · Gradle 8.11", "go" to "go1.23.0", "rust" to "rustc 1.83.0",
            )
        }
    }

    @Before fun setUp() {
        File(app.filesDir, "devtools.json").delete()
        fake = FakeLinux(app)
        dt = DevTools(app, fake)
    }

    /** enqueue sets Queued synchronously, so Idle here means the job finished. */
    private fun awaitIdle(id: String) = runBlocking {
        withTimeout(20_000) { while (dt.status.value[id] != ToolStatus.Idle) delay(50) }
    }

    private fun awaitInstalled(id: String) = runBlocking {
        withTimeout(20_000) { while (dt.records.value[id]?.version == null) delay(50) }
        awaitIdle(id)
        assertTrue("isInstalled($id)", dt.isInstalled(id))
    }

    private fun scriptLog() = fake.commands.map { it.substringAfter("run.sh ") }

    @Test fun installsAllScriptToolchains() {
        val tools = listOf(Toolchain.WEB, Toolchain.PYTHON, Toolchain.ANDROID, Toolchain.CPP, Toolchain.PHP,
            Toolchain.BROWSER, Toolchain.ANDROIDSDK, Toolchain.GO, Toolchain.RUST)
        tools.forEach { t -> dt.install(t); awaitInstalled(t.id) }
        tools.forEach { t ->
            assertEquals(FakeLinux.VERSIONS[t.id], dt.records.value[t.id]?.version)
            assertEquals(ToolStatus.Idle, dt.status.value[t.id])
        }
        assertEquals(
            listOf("core install", "web install", "python install", "android install", "cpp install",
                "php install", "browser install", "androidsdk install", "go install", "rust install"),
            scriptLog(),
        )
    }

    @Test fun browserInstallsWebFirst() {
        dt.install(Toolchain.BROWSER)
        awaitInstalled("web"); awaitInstalled("browser")
        assertEquals(listOf("core install", "web install", "browser install"), scriptLog())
    }

    @Test fun androidSdkInstallsAndroidFirst() {
        dt.install(Toolchain.ANDROIDSDK)
        awaitInstalled("android"); awaitInstalled("androidsdk")
        assertEquals(listOf("core install", "android install", "androidsdk install"), scriptLog())
    }

    @Test fun failedScriptReportsItsError() {
        fake.failTool = "cpp"
        dt.install(Toolchain.CPP)
        runBlocking {
            withTimeout(20_000) { while (dt.status.value["cpp"] !is ToolStatus.Failed) delay(50) }
        }
        val failed = dt.status.value["cpp"] as ToolStatus.Failed
        assertTrue("message carries the script's error: ${failed.message}", failed.message.contains("Could not install: cpp-test-pkg"))
        assertNull(dt.records.value["cpp"]?.version)
        assertFalse(dt.isInstalled("cpp"))
        assertEquals(listOf("core install", "cpp install"), scriptLog())
    }

    @Test fun updateAndUninstallDispatch() {
        dt.install(Toolchain.PYTHON); awaitInstalled("python")
        dt.update(Toolchain.PYTHON); awaitIdle("python")
        assertTrue(scriptLog().contains("python update"))
        assertEquals("3.12.3", dt.records.value["python"]?.version)
        dt.uninstall(Toolchain.PYTHON); awaitIdle("python")
        assertTrue(scriptLog().contains("python uninstall"))
        assertNull(dt.records.value["python"]?.version)
        assertFalse(dt.isInstalled("python"))
    }
}
