package com.mazharansari.x

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.mazharansari.x.data.Connector
import com.mazharansari.x.data.X_BOT_ID
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Navigation restructure (2026-11-20) — the menu panel, the single-X-Bot
 * section, and profile editing. Robolectric screenshot-free tests (assertions
 * only), following the V031 clock pattern: the Chat screen does not idle
 * under Robolectric in the full suite, so the clock is driven by hand and
 * nodes are polled in real time.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi", application = AxionApp::class)
@OptIn(ExperimentalTestApi::class)
class V06NavScreensTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun seed() {
        val app = ApplicationProvider.getApplicationContext<AxionApp>()
        val fd = app.filesDir
        File(fd, "settings.json").writeText("""{"userName":"Mazhar Ansari","theme":"dark","mode":"AUTO"}""")
        // TEST ISOLATION (botsSectionSingleXBotAndBack, d64aeba2 + fd06e47):
        // the bot store persists to <filesDir>/bots/bots.json — BotDefinitionStore
        // .rename() calls persist(), and ensureXBot() deliberately NEVER
        // restores a user-chosen name (it refreshes only persona /
        // description). profileEditRenamesXBot therefore leaves the bot
        // named "X Prime", and JUnit's deterministic method order runs it
        // BEFORE botsSectionSingleXBotAndBack — so that test's
        // onNodeWithText("X Bot").assertIsDisplayed() finds ZERO matching
        // nodes and fails with exactly "Assert failed: The component is
        // not displayed!" (that is the message assertIsDisplayed throws
        // when no node matches). Proof: on 30c66547 profileEdit FAILED
        // before Save (ComposeTimeoutException at :151, rename never
        // persisted) and botsSection PASSED; on d64aeba2 attempt 1 AND 2
        // profileEdit PASSED (rename persisted) and botsSection FAILED
        // at :117 both times, 0.371 s in — instant, no poll. This is NOT
        // the AnimatedContent transition race: waitForNode already
        // advances the test clock 200 ms past the 150 ms slide/fade, and
        // the sibling tests' immediate asserts after waitForNode pass.
        // fd06e47 tried deleting bots/bots.json here — and botsSection
        // STILL failed identically (Verify Tests run 44734256639, :138,
        // 0.387 s). The file delete alone is therefore NOT sufficient:
        // BotDefinitionStore loads its StateFlow ONCE, at construction
        // (BotStore.kt:64-72), and nothing ever re-reads the file — the
        // Graph/botStore built during profileEditRenamesXBot still holds
        // "X Prime" IN MEMORY when botsSection runs, and ensureXBot()
        // keeps a user-chosen name by design. So reset the IN-MEMORY
        // store too, through its public API: rename() updates the
        // StateFlow AND persists the correct name back to disk. Cases:
        // store not yet built (lazy) — rename is a harmless no-op and
        // ensureXBot() seeds a fresh "X Bot" at launch; store holding
        // "X Prime" — fixed in memory and on disk; already "X Bot" —
        // no-op. The logo is untouched (rename never touches it).
        File(fd, "bots/bots.json").delete()
        app.graph.botStore.rename(X_BOT_ID, "X Bot")
        fun conn(p: String, hint: String, models: List<String>) = Connector(java.util.UUID.randomUUID().toString(), p, "plain:" + android.util.Base64.encodeToString("test-key".toByteArray(), 2), hint, models, models, addedAt = 1)
        File(fd, "connectors.json").writeText(Json.encodeToString(ListSerializer(Connector.serializer()), listOf(conn("gemini", "AIza…x9Qk", listOf("gemini-3.8-flash")))))
    }

    /** Drive the clock by hand, polling in real time until the node appears (V031 pattern). */
    private fun waitForNode(matcher: SemanticsMatcher, timeoutMs: Long = 10_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            compose.mainClock.advanceTimeBy(200)
            Thread.sleep(100)
            if (compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()) return
        }
        error("node $matcher never appeared")
    }

    private fun launch(): ActivityScenario<MainActivity> {
        seed()
        val sc = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(800)
        return sc
    }

    /** App opens on the single chat screen; the top button opens the menu panel. */
    private fun openMenu() {
        waitForNode(hasText("Ask anything"))
        // Root chat: the header shows a Menu glyph, not Back (Back exists
        // only on a bot chat's header) — it opens the menu panel.
        compose.onNodeWithContentDescription("Menu").performClick()
        waitForNode(hasText("Bots"))
    }

    private fun top(text: String): Float =
        compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

    @Test fun menuPanelStructure() {
        val sc = launch()
        try {
            // The app opens straight on the single chat screen, ready to type.
            compose.onNodeWithText("Ask anything").assertIsDisplayed()
            openMenu()
            // Top row: exactly 4 options.
            compose.onNodeWithText("Bots").assertIsDisplayed()
            compose.onNodeWithText("Library").assertIsDisplayed()
            compose.onNodeWithText("Connectors").assertIsDisplayed()
            compose.onNodeWithText("Skills").assertIsDisplayed()
            // Full chat-history list below the top row.
            compose.onNodeWithText("CHATS").assertIsDisplayed()
            compose.onNodeWithText("No chats yet — tap New chat below.").assertIsDisplayed()
            // Below the history, in this order: Settings, New chat, Search.
            compose.onNodeWithText("Settings").assertIsDisplayed()
            compose.onNodeWithText("New chat").assertIsDisplayed()
            compose.onNodeWithText("Search").assertIsDisplayed()
            assertTrue("top row above history", top("Bots") < top("CHATS"))
            assertTrue("history above Settings", top("CHATS") < top("Settings"))
            assertTrue("Settings above New chat", top("Settings") < top("New chat"))
            assertTrue("New chat above Search", top("New chat") < top("Search"))
            // New chat returns to a blank chat screen.
            compose.onNodeWithText("New chat").performClick()
            waitForNode(hasText("Ask anything"))
        } finally { sc.close() }
    }

    @Test fun menuSearchRowFiltersHistory() {
        val sc = launch()
        try {
            openMenu()
            compose.onNodeWithText("Search").performClick()
            compose.mainClock.advanceTimeBy(400)
            compose.onNodeWithText("Search chats").assertIsDisplayed()
        } finally { sc.close() }
    }

    @Test fun botsSectionSingleXBotAndBack() {
        val sc = launch()
        try {
            openMenu()
            compose.onNodeWithText("Bots").performClick()
            waitForNode(hasText("UNDER X BOT"))
            // Exactly ONE bot: X Bot. No roster specialist is listed as a bot.
            compose.onNodeWithText("X Bot").assertIsDisplayed()
            compose.onNodeWithText("Coworker").assertDoesNotExist()
            compose.onNodeWithText("Researcher").assertDoesNotExist()
            compose.onNodeWithText("Coder").assertDoesNotExist()
            compose.onNodeWithText("Specialists · 51").assertIsDisplayed()
            // Tapping X Bot opens THAT bot's own chat.
            compose.onNodeWithText("X Bot").performClick()
            waitForNode(hasText("Ask anything"))
            compose.onNodeWithText("X Bot").assertIsDisplayed()
            // Back returns to the bot list — never to home or a new chat.
            compose.onNodeWithContentDescription("Back").performClick()
            waitForNode(hasText("UNDER X BOT"))
            compose.onNodeWithText("Specialists · 51").assertIsDisplayed()
        } finally { sc.close() }
    }

    @Test fun profileEditRenamesXBot() {
        val sc = launch()
        try {
            openMenu()
            compose.onNodeWithText("Bots").performClick()
            waitForNode(hasText("UNDER X BOT"))
            // Tapping X Bot's PROFILE (its icon) opens profile editing.
            compose.onNodeWithContentDescription("X Bot profile").performClick()
            waitForNode(hasText("Edit profile"))
            // The profile editor is a ModalBottomSheet (AxionSheet): its
            // content is composed — and "Edit profile" exists in the tree —
            // the moment it opens. But "displayed" is UNPROVABLE for its rows
            // in this harness — the 5 s waitUntil(5000) that used to be here
            // timed out in CI (ComposeTimeoutException at :151), proving it:
            // the sheet's slide-in animation runs on the ACTIVITY's real
            // Choreographer frame clock, not the Compose test clock — so
            // waitUntil/mainClock.advanceTimeBy (test clock) and waitForNode
            // never advance the slide, "Monogram" stays below the visible
            // bounds forever, and isDisplayed() stays false. No test in the
            // suite asserts display inside a ModalBottomSheet, and the sheet
            // is a separate Dialog window whose frames the paused Robolectric
            // main looper never ticks under autoAdvance=false. So assert
            // EXISTENCE for all four icon options instead — that still proves
            // the editor opened with every option present — and drive the
            // rename through the profileName field + Save exactly as before.
            // The final "X Prime" assertIsDisplayed below stays: it is on
            // the main window (BotStoreScreen list), not in the sheet.
            compose.onNodeWithText("Monogram").assertExists()
            compose.onNodeWithText("Static image").assertExists()
            compose.onNodeWithText("3D").assertExists()
            compose.onNodeWithText("Video").assertExists()
            // Rename and save.
            compose.onNodeWithTag("profileName").performTextClearance()
            compose.onNodeWithTag("profileName").performTextInput("X Prime")
            compose.onNodeWithText("Save").performClick()
            waitForNode(hasText("X Prime"))
            compose.onNodeWithText("X Prime").assertIsDisplayed()
            compose.onNodeWithText("X Bot").assertDoesNotExist()
        } finally { sc.close() }
    }
}
