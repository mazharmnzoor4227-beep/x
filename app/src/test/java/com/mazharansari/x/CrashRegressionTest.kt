package com.mazharansari.x

import android.app.Application
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mazharansari.x.agent.Attempt
import com.mazharansari.x.build.ProjectStore
import com.mazharansari.x.build.StaticServer
import com.mazharansari.x.data.BotDefinition
import com.mazharansari.x.data.Conversation
import com.mazharansari.x.data.Message
import com.mazharansari.x.data.Role
import com.mazharansari.x.data.SettingsStore
import com.mazharansari.x.data.X_BOT_ID
import com.mazharansari.x.llm.LlmClient
import com.mazharansari.x.llm.LlmEvent
import com.mazharansari.x.llm.LlmRequest
import com.mazharansari.x.ui.ChatViewModel
import java.io.File
import java.net.ServerSocket
import java.util.Collections
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Crash regression tests (2026-11-20): the 50fa5c6f signed APK (CI green, 961 tests)
 * crashed SOMETIMES on the user's Infinix HOT 60 Pro while sending messages,
 * opening bots, messaging inside a bot chat, or doing anything. Every crash
 * found in that audit is pinned here: the test FAILS on the pre-fix code and
 * passes after the fix.
 *
 * Two parts:
 *  - VM-level (no Activity): send paths, run-in-flight, stale open(), empty /
 *    very long / repeated sends, disk-failure survival, occupied-port guard.
 *  - UI-level (ActivityScenario, same helpers as V06NavScreensTest): bot list
 *    navigation, sending inside a bot chat, bot deleted mid-chat, stale scroll
 *    restore, scroll-to-bottom after the chat is cleared.
 *
 * Gradle does not run in this sandbox; CI (gradle testDebugUnitTest) runs these.
 *
 * Fix-forward (2026-11-20, 659586c broke Verify Tests on this file only):
 * the first version imported invented APIs (agent.LlmClient/LlmStream/Text,
 * java.util.concurrent.CompletableDeferred, androidx.compose.ui.graphics
 * .GraphicsMode) and used Compose test APIs as top-level imports. Everything
 * here now uses the real APIs: llm.LlmClient.stream(LlmRequest): Flow<LlmEvent>
 * (LlmEvent.Text), kotlinx.coroutines.CompletableDeferred, the
 * org.robolectric GraphicsMode, and the androidx.compose.ui.test.* wildcard
 * that V06NavScreensTest proves compiles (assertExists / onAllNodes /
 * isDisplayed are extensions/members, not top-level functions).
 */
@RunWith(AndroidJUnit4::class)
@Config(application = AxionApp::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class CrashRegressionTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: AxionApp
        get() = ApplicationProvider.getApplicationContext<Application>() as AxionApp

    // ---- shared helpers (same shape as V052SpeedBugTest / V06NavScreensTest) ----

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun instantModel(reply: String) = object : LlmClient {
        override fun stream(req: LlmRequest): Flow<LlmEvent> =
            flow { emit(LlmEvent.Text(reply)) }
    }

    private fun waitUntil(timeoutMs: Long = 5000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            idle()
            if (condition()) return
            Thread.sleep(50)
            idle()
        }
        fail("timed out after ${timeoutMs}ms waiting for condition")
    }

    private fun waitForIdleRun(vm: ChatViewModel, timeoutMs: Long = 8000) {
        waitUntil(timeoutMs) { !vm.running }
        idle()
    }

    private fun waitForNode(matcher: androidx.compose.ui.test.SemanticsMatcher, timeoutMs: Long = 8000) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            compose.mainClock.advanceTimeBy(200)
            idle()
            if (compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(100)
            idle()
        }
        fail("timed out after ${timeoutMs}ms waiting for node $matcher")
    }

    /**
     * Copied from V06NavScreensTest: poll until exactly ONE node matches
     * [matcher] AND it is displayed. Existence is not display: after a
     * navigation tap, AnimatedContent composes the incoming screen at once,
     * translated off-screen and transparent, so a node can exist for poll
     * iterations while assertIsDisplayed still throws. The indexed [0]
     * lookup can only ever address the one node the count check just
     * observed; the runCatching is belt-and-braces for a tree change
     * between the two queries.
     */
    private fun waitForDisplayed(matcher: androidx.compose.ui.test.SemanticsMatcher, timeoutMs: Long = 8000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            compose.mainClock.advanceTimeBy(200)
            Thread.sleep(100)
            val nodes = compose.onAllNodes(matcher).fetchSemanticsNodes()
            if (nodes.size == 1 &&
                runCatching { compose.onAllNodes(matcher)[0].isDisplayed() }.getOrDefault(false)
            ) return
        }
        val nodes = compose.onAllNodes(matcher).fetchSemanticsNodes()
        val detail = nodes.joinToString("; ") { node ->
            val text = node.config.getOrNull(SemanticsProperties.Text)
                ?.joinToString("|") { it.text } ?: "?"
            "text=\"$text\" boundsInRoot=${node.boundsInRoot}"
        }
        val allTexts = compose.onAllNodes(SemanticsMatcher("any node") { true })
            .fetchSemanticsNodes()
            .mapNotNull { node ->
                node.config.getOrNull(SemanticsProperties.Text)
                    ?.joinToString("|") { it.text }
            }.filter { it.isNotBlank() }.take(40)
        fail(
            "node $matcher never displayed (matches=${nodes.size}): $detail" +
                " | ALL_TEXTS_ON_SCREEN=$allTexts",
        )
    }

    private fun seedSettings() {
        // Minimal known-good seed (mirrors V06NavScreensTest): AppSettings fields
        // are non-nullable, so a null value for any of them would fail the
        // decode and fall back to defaults (intro screen). Only real fields here.
        val f = File(app.filesDir, "settings.json")
        f.writeText(
            """{"mode":"AUTO","theme":"dark","pasteAsFile":"Always","introShown":true,"permsShown":true}""",
        )
    }

    /** Mirrors V06NavScreensTest.launch(): seed, open MainActivity, return scenario + VM. */
    private fun launch(): Pair<ActivityScenario<MainActivity>, ChatViewModel> {
        seedSettings()
        try {
            app.graph.botStore.rename(X_BOT_ID, "X Bot")
        } catch (_: Throwable) {
        }
        val sc = ActivityScenario.launch(MainActivity::class.java)
        lateinit var vm: ChatViewModel
        sc.onActivity { act ->
            vm = androidx.lifecycle.ViewModelProvider(act)[ChatViewModel::class.java]
            try {
                vm.g.botStore.ensureXBot()
            } catch (_: Throwable) {
            }
        }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(800)
        idle()
        compose.waitForIdle()
        waitForNode(hasText("Ask anything"))
        return sc to vm
    }

    private fun openMenu() {
        waitForNode(hasText("Ask anything"))
        compose.onNodeWithContentDescription("Menu").performClick()
        waitForNode(hasText("Bots"))
    }

    /** Record Throwables the default uncaught-exception handler receives while [block] runs. */
    private fun recordUncaught(block: () -> Unit): List<Throwable> {
        val caught = Collections.synchronizedList(mutableListOf<Throwable>())
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, t -> caught += t }
        try {
            block()
            Thread.sleep(800)
            idle()
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(prev)
        }
        return caught.toList()
    }

    // ================= VM-level: send paths =================

    @Test
    fun sendMessageAppearsAndReplies() {
        val vm = ChatViewModel(app)
        vm.debugAttempts = { _, _ -> listOf(Attempt("Fake", "m", instantModel("ok"))) }
        vm.send("hello")
        waitForIdleRun(vm)
        assertEquals(2, vm.messages.size)
        assertEquals("hello", vm.messages[0].text)
        assertEquals(Role.ASSISTANT, vm.messages[1].role)
        assertEquals("ok", vm.messages[1].text)
        assertTrue("a send must open a conversation", vm.conversationId != null)
    }

    @Test
    fun sendEmptyMessageDoesNothing() {
        val vm = ChatViewModel(app)
        vm.debugAttempts = { _, _ -> listOf(Attempt("Fake", "m", instantModel("ok"))) }
        vm.send("")
        vm.send("   ")
        idle()
        assertTrue(vm.messages.isEmpty())
        assertNull(vm.conversationId)
        assertTrue(!vm.running)
    }

    @Test
    fun sendVeryLongMessage() {
        val vm = ChatViewModel(app)
        vm.debugAttempts = { _, _ -> listOf(Attempt("Fake", "m", instantModel("read it"))) }
        vm.send("x".repeat(200_000))
        // A 200k-char paste becomes a file attachment, not a giant in-memory bubble.
        assertEquals(1, vm.messages.size)
        assertEquals(1, vm.messages[0].attachments.size)
        assertEquals(200_000, vm.messages[0].attachments[0].size)
        waitForIdleRun(vm)
        assertEquals(2, vm.messages.size)
        assertEquals("read it", vm.messages[1].text)
    }

    @Test
    fun rapidRepeatedSendsStartOneReply() {
        val gate = CompletableDeferred<Unit>()
        val hanging = object : LlmClient {
            override fun stream(req: LlmRequest): Flow<LlmEvent> =
                flow { gate.await(); emit(LlmEvent.Text("one")) }
        }
        val vm = ChatViewModel(app)
        vm.debugAttempts = { _, _ -> listOf(Attempt("Fake", "m", hanging)) }
        repeat(5) { vm.send("hi") }
        idle()
        // Exactly one user message and one streaming assistant message, not five runs.
        assertEquals(2, vm.messages.size)
        assertTrue(vm.running)
        assertTrue("repeated sends while busy must warn, not vanish", vm.toast != null)
        gate.complete(Unit)
        waitForIdleRun(vm)
        assertEquals(2, vm.messages.size)
        assertEquals("one", vm.messages[1].text)
        assertEquals(1, vm.messages.count { it.role == Role.USER })
    }

    @Test
    fun sendWhileRunInFlightKeepsDraftAndWarns() {
        val gate = CompletableDeferred<Unit>()
        val hanging = object : LlmClient {
            override fun stream(req: LlmRequest): Flow<LlmEvent> =
                flow { gate.await(); emit(LlmEvent.Text("first reply")) }
        }
        val vm = ChatViewModel(app)
        vm.debugAttempts = { _, _ -> listOf(Attempt("Fake", "m", hanging)) }
        vm.send("first")
        waitUntil { vm.running }
        vm.draft = "second"
        vm.send()
        idle()
        // The second send must NOT start a second run or drop the draft silently.
        assertEquals(2, vm.messages.size)
        assertEquals("second", vm.draft)
        assertEquals(
            "Still working on your last message — send again when the reply is done.",
            vm.toast,
        )
        gate.complete(Unit)
        waitForIdleRun(vm)
        assertEquals(2, vm.messages.size)
        assertEquals(1, vm.messages.count { it.role == Role.USER })
    }

    @Test
    fun regenerateAndEditOnEmptyChatDoNotCrash() {
        val vm = ChatViewModel(app)
        vm.regenerate()
        vm.editAndResend("nope")
        vm.stop()
        idle()
        assertTrue(vm.messages.isEmpty())
        assertTrue(!vm.running)
    }

    // ================= VM-level: stale / missing open() =================

    @Test
    fun staleOpenLoadDoesNotClobberNewChat() {
        // Seed a slow-to-load conversation (200 messages) so the IO load is
        // still in flight when the user bails to a new chat.
        val id = "stale-" + UUID.randomUUID()
        val msgs = (0 until 200).map { i ->
            Message("m$i", if (i % 2 == 0) Role.USER else Role.ASSISTANT, "Stale load message $i", createdAt = i.toLong())
        }
        app.graph.chats.save(Conversation(id, "stale", msgs), true)
        Thread.sleep(300)
        val vm = ChatViewModel(app)
        vm.open(id)
        vm.newChat()
        // Let the late IO load land (or be dropped by the generation guard).
        repeat(40) { Thread.sleep(50); idle() }
        // Pre-fix: the late load overwrote the new chat with conversation A.
        assertNull(vm.conversationId)
        assertTrue(vm.messages.isEmpty())
    }

    @Test
    fun openMissingConversationExplainsInsteadOfShowingStaleChat() {
        val id = "keep-" + UUID.randomUUID()
        app.graph.chats.save(Conversation(id, "keep", listOf(Message("u", Role.USER, "keep me"))), true)
        Thread.sleep(300)
        val vm = ChatViewModel(app)
        vm.open(id)
        waitUntil { vm.conversationId == id }
        vm.open("gone-id-" + UUID.randomUUID())
        // Let the (missing) load finish.
        repeat(40) { Thread.sleep(50); idle() }
        assertEquals("the missing chat must not clobber the open one", id, vm.conversationId)
        assertTrue(
            "the user must be told the chat could not be opened",
            (vm.toast ?: "").contains("couldn't be opened"),
        )
    }

    @Test
    fun renamedBotChatKeepsWorking() {
        val bot: BotDefinition = app.graph.botStore.create("Temp Bot", "desc", "persona", null)
        val vm = ChatViewModel(app)
        vm.debugAttempts = { _, _ -> listOf(Attempt("Fake", "m", instantModel("noted"))) }
        vm.openBotChat(bot)
        assertEquals(bot.id, vm.activeBot?.id)
        // The bot is renamed while its chat is open (user edits it in the store).
        app.graph.botStore.rename(bot.id, "Renamed Bot")
        assertEquals(
            "Renamed Bot",
            app.graph.botStore.definitions.value.first { it.id == bot.id }.name,
        )
        vm.send("hello")
        waitForIdleRun(vm)
        assertEquals(2, vm.messages.size)
        assertEquals("noted", vm.messages[1].text)
        // The thread still loads back under the same bot id.
        val saved = kotlinx.coroutines.runBlocking { app.graph.chats.load(vm.conversationId!!) }
        assertEquals(bot.id, saved?.botId)
        app.graph.botStore.remove(bot.id)
    }

    // ================= disk-failure survival =================

    @Test
    fun conversationSaveSurvivesUnwritableDisk() {
        val dir = File(app.filesDir, "chats").apply { mkdirs() }
        val id = "crash-" + UUID.randomUUID()
        // A directory where the temp file goes makes writeText() throw.
        val tmpDir = File(dir, "$id.json.tmp").apply { mkdir() }
        try {
            val caught = recordUncaught {
                app.graph.chats.save(
                    Conversation(id, "t", listOf(Message("u", Role.USER, "hi"))),
                    true,
                )
            }
            // Pre-fix: the throw escaped the background launch and killed the app.
            assertTrue("save must not crash the app: $caught", caught.isEmpty())
            assertEquals(id, app.graph.chats.index.value.firstOrNull { it.id == id }?.id)
        } finally {
            tmpDir.delete()
        }
    }

    @Test
    fun settingsSaveSurvivesUnwritableDisk() {
        val file = File(app.filesDir, "settings.json")
        val orig = if (file.isFile) file.readBytes() else null
        file.delete()
        file.mkdir()
        try {
            val store = SettingsStore(app)
            val caught = recordUncaught { store.update { it.copy(userName = "CrashTest") } }
            // Pre-fix: the throw escaped the background launch and killed the app.
            assertTrue("settings update must not crash the app: $caught", caught.isEmpty())
        } finally {
            file.delete()
            orig?.let { file.writeBytes(it) }
        }
    }

    @Test
    fun projectSaveSurvivesUnwritableDisk() {
        // ProjectStore.save() is synchronous from UI paths: a failed write used
        // to throw straight into the caller. Planting a directory at the temp
        // path makes writeText() throw; create() must still return the project.
        val tmpDir = File(app.filesDir, "projects.json.tmp").apply { mkdir() }
        try {
            val store = ProjectStore(app.filesDir)
            val p = store.create("Crash Project", "web")
            assertEquals("Crash Project", p.name)
            assertTrue(store.list.value.any { it.id == p.id })
        } finally {
            tmpDir.delete()
        }
    }

    @Test
    fun staticServerStartOnOccupiedPortThrows() {
        // Documents the hazard HtmlPreview now guards: start() throws when the
        // loopback port cannot be bound. The preview must show an error UI,
        // not crash during composition.
        val occupied = ServerSocket(0)
        try {
            val server = StaticServer(File(app.filesDir, "preview-root"), spaFallback = false)
            try {
                server.start(occupied.localPort)
                fail("start on an occupied port must throw")
            } catch (_: java.io.IOException) {
                // expected — the hazard the HtmlPreview guard covers
            }
        } finally {
            occupied.close()
        }
        // Sanity: a free port still starts.
        val ok = StaticServer(File(app.filesDir, "preview-root"), spaFallback = false).start()
        try {
            assertTrue(ok.port > 0)
        } finally {
            ok.stop()
        }
    }

    // ================= UI-level: bot navigation =================

    @Test
    fun openBotFromListAndBack() {
        val (sc, _) = launch()
        try {
            openMenu()
            compose.onNodeWithText("Bots").performClick()
            waitForNode(hasText("UNDER X BOT"))
            waitForDisplayed(hasText("X Bot"))
            compose.onNodeWithText("X Bot").performClick()
            waitForNode(hasText("Ask anything"))
            waitForDisplayed(hasText("X Bot"))
            compose.onNodeWithContentDescription("Back").performClick()
            waitForNode(hasText("UNDER X BOT"))
            waitForDisplayed(hasText("Specialists · 51"))
            // Rapid second round: open, back, open again.
            compose.onNodeWithText("X Bot").performClick()
            waitForNode(hasText("Ask anything"))
            compose.onNodeWithContentDescription("Back").performClick()
            waitForNode(hasText("UNDER X BOT"))
            waitForDisplayed(hasText("Specialists · 51"))
        } finally {
            sc.close()
        }
    }

    @Test
    fun sendMessageInsideBotChat() {
        val (sc, vm) = launch()
        try {
            sc.onActivity {
                vm.debugAttempts = { _, _ -> listOf(Attempt("Fake", "m", instantModel("bot-reply-ok"))) }
            }
            openMenu()
            compose.onNodeWithText("Bots").performClick()
            waitForNode(hasText("UNDER X BOT"))
            waitForDisplayed(hasText("X Bot"))
            compose.onNodeWithText("X Bot").performClick()
            waitForNode(hasText("Ask anything"))
            waitForDisplayed(hasText("X Bot"))
            compose.onNode(hasSetTextAction()).performTextInput("hello bot")
            compose.onNodeWithContentDescription("Send message").performClick()
            waitForNode(hasText("hello bot"))
            waitForNode(hasText("bot-reply-ok"))
        } finally {
            sc.close()
        }
    }

    @Test
    fun botRemovedMidChatReturnsToList() {
        val (sc, vm) = launch()
        try {
            openMenu()
            compose.onNodeWithText("Bots").performClick()
            waitForNode(hasText("UNDER X BOT"))
            waitForDisplayed(hasText("X Bot"))
            compose.onNodeWithText("X Bot").performClick()
            waitForNode(hasText("Ask anything"))
            waitForDisplayed(hasText("X Bot"))
            // The bot is deleted while its chat is open: the screen must back
            // out to the bot list instead of sticking on a dead/blank screen.
            sc.onActivity { vm.g.botStore.remove(X_BOT_ID) }
            waitForNode(hasText("UNDER X BOT"))
            waitForDisplayed(hasText("Specialists · 51"))
        } finally {
            sc.close()
        }
    }

    // ================= UI-level: scroll guards =================

    @Test
    fun staleScrollRestoreDoesNotCrash() {
        val (sc, vm) = launch()
        try {
            val id = "scroll-" + UUID.randomUUID()
            val msgs = (0 until 3).map { i ->
                Message("m$i", if (i % 2 == 0) Role.USER else Role.ASSISTANT, "Scroll check message $i", createdAt = i.toLong())
            }
            app.graph.chats.save(Conversation(id, "scroll", msgs), true)
            Thread.sleep(300)
            // A saved scroll position from before the chat was shortened to 3 messages.
            vm.saveScroll(id, 40)
            sc.onActivity { vm.open(id) }
            waitUntil { vm.conversationId == id }
            // Pre-fix: scrollToItem(40) on a 3-item list — the restore is now clamped.
            waitForNode(hasText("Scroll check message 2"))
            compose.onNodeWithText("Scroll check message 0").assertExists()
        } finally {
            sc.close()
        }
    }

    @Test
    fun scrollToBottomAfterClearDoesNotCrash() {
        val (sc, vm) = launch()
        try {
            val id = "chev-" + UUID.randomUUID()
            val msgs = (0 until 20).map { i ->
                Message(
                    "m$i",
                    if (i % 2 == 0) Role.USER else Role.ASSISTANT,
                    "Chevron check message $i — " + "long text. ".repeat(10),
                    createdAt = i.toLong(),
                )
            }
            app.graph.chats.save(Conversation(id, "chevron", msgs), true)
            Thread.sleep(300)
            sc.onActivity { vm.open(id) }
            waitUntil { vm.conversationId == id }
            waitForNode(hasText("Chevron check message 19"))
            // Scroll to the top so the scroll-to-bottom chevron appears.
            compose.onNode(
                hasScrollAction() and hasAnyDescendant(hasText("Chevron check message 0")),
            ).performScrollToIndex(0)
            waitForDisplayed(hasContentDescription("Scroll to bottom"))
            // Tap it, then clear the chat before the tap's coroutine runs
            // (paused main looper): pre-fix it called animateScrollToItem(-1)
            // and threw IllegalArgumentException ("Index should be non-negative").
            compose.onNodeWithContentDescription("Scroll to bottom").performClick()
            compose.onNodeWithContentDescription("New chat").performClick()
            compose.mainClock.advanceTimeBy(1500)
            compose.waitForIdle()
            compose.onNodeWithText("Ask anything").assertIsDisplayed()
        } finally {
            sc.close()
        }
    }
}
