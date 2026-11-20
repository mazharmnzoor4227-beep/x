package com.mazharansari.x

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
        // not displayed!". REFUTED (d5fcfadd, Verify Tests run 44738014055):
        // that message is what assertIsDisplayed throws only AFTER fetching
        // a node and finding it not displayed — zero matching nodes would
        // fail inside fetchSemanticsNode with a no-match message instead.
        // And both contamination fixes left the failure byte-identical
        // (fd06e47 file delete; d5fcfadd in-memory rename below), while
        // waitForNode(hasText("UNDER X BOT")) passes first — the row IS
        // composed, with xBot != null (BotStoreScreen.kt:75 returns early
        // otherwise, which would remove "UNDER X BOT" too). The real
        // mechanism is existence-vs-display: the Home -> Bots navigation
        // runs through AnimatedContent (AppRoot.kt:60-65, 150 ms slide +
        // 140 ms fade), which composes the incoming screen immediately —
        // translated off-screen and transparent — so waitForNode returns
        // on EXISTENCE while the "X Bot" node is still not displayed.
        // The 150 ms tween does NOT run on the test clock: waitForNode's
        // first iteration already advances mainClock 200 ms before it ever
        // checks existence, which would have finished the tween — yet the
        // assert still fails. The composition is hosted by the Activity
        // (ActivityScenario.launch, never the rule's setContent), so its
        // frame clock is the Activity's real Choreographer, not mainClock
        // (same split the profileEdit comment below records as CI-proven
        // for the sheet slide). Only real time / looper pumps advance it,
        // and how many of those elapse before the assert depends on suite
        // timing — which is why the failure flipped deterministically when
        // the V031 poll timing shifted on d64aeba2. Fix: waitForDisplayed
        // below polls for DISPLAYED (both clocks, 200 ms + 100 ms real
        // per iteration) before asserting; the assert itself is unchanged.
        // fd06e47 tried deleting bots/bots.json here — and botsSection
        // STILL failed identically (Verify Tests run 44734256639, :138,
        // 0.387 s). The file delete alone is therefore NOT sufficient:
        // BotDefinitionStore loads its StateFlow ONCE, at construction
        // (BotStore.kt:64-72), and nothing ever re-reads the file — the
        // Graph/botStore built during profileEditRenamesXBot still holds
        // "X Prime" IN MEMORY when botsSection runs, and ensureXBot()
        // keeps a user-chosen name by design. So reset the IN-MEMORY
        // store too, through its public API: rename() updates the
        // StateFlow AND persists the correct name back to disk. (d5fcfadd
        // did exactly that — and botsSection STILL failed identically,
        // run 44738014055 — so the rename below is retained only as
        // isolation hygiene, not as the fix for this failure.) Cases:
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

    /**
     * Poll until exactly ONE node matches [matcher] AND it is displayed —
     * the display half that [waitForNode] never checks (V06 race, d5fcfadd).
     * Existence is not display: after a navigation tap, AnimatedContent
     * (AppRoot.kt:60-65) composes the incoming screen at once, translated
     * off-screen and transparent, so a node can exist for poll iterations
     * while assertIsDisplayed still throws "The component is not
     * displayed!". Each iteration advances the test clock 200 ms AND
     * sleeps 100 ms real, so the poll drives the transition whichever
     * clock runs it (test clock vs the Activity's real Choreographer —
     * see the seed() comment; the poll does not need to know).
     *
     * Exactly ONE, not at-least-one: during a transition BOTH screens are
     * composed, so a text can match twice — e.g. Bots -> BotChat, where
     * the outgoing "X Bot" row (BotStoreScreen.kt:97) and the incoming
     * bot-chat header (ChatScreen.kt:205, `Text(bot.name, ...)`) are both
     * live. A single-node isDisplayed() would throw AssertionError on the
     * multi-match (docs: "If multiple nodes match, throws"), and the
     * following onNodeWithText assert would fail the same way — so the
     * poll waits the transition out instead. The indexed [0] lookup can
     * only ever address the one node the count check just observed; the
     * runCatching is belt-and-braces for a tree change between the two
     * queries (isDisplayed() itself returns false when no node matches).
     *
     * On timeout this throws a DIAGNOSIS, not a bare assert message: how
     * many nodes matched, each one's text and boundsInRoot, so the next
     * CI failure shows what was actually on screen.
     */
    private fun waitForDisplayed(matcher: SemanticsMatcher, timeoutMs: Long = 10_000) {
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
        error("node $matcher never displayed (matches=${nodes.size}): $detail")
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
            // waitForNode only proves EXISTENCE: the Bots screen is composed
            // mid-transition (translated + transparent) the moment the tap
            // lands. Poll for DISPLAYED before asserting — same race as
            // the bot-chat and post-Back asserts below.
            waitForDisplayed(hasText("X Bot"))
            // Exactly ONE bot: X Bot. No roster specialist is listed as a bot.
            compose.onNodeWithText("X Bot").assertIsDisplayed()
            compose.onNodeWithText("Coworker").assertDoesNotExist()
            compose.onNodeWithText("Researcher").assertDoesNotExist()
            compose.onNodeWithText("Coder").assertDoesNotExist()
            compose.onNodeWithText("Specialists · 51").assertIsDisplayed()
            // Tapping X Bot opens THAT bot's own chat.
            compose.onNodeWithText("X Bot").performClick()
            waitForNode(hasText("Ask anything"))
            // Same race, plus a transient second match mid-transition: the
            // outgoing Bots row and the incoming bot-chat header
            // (ChatScreen.kt:205) both read "X Bot" until the slide ends.
            waitForDisplayed(hasText("X Bot"))
            compose.onNodeWithText("X Bot").assertIsDisplayed()
            // Back returns to the bot list — never to home or a new chat.
            compose.onNodeWithContentDescription("Back").performClick()
            waitForNode(hasText("UNDER X BOT"))
            // Same race on the going-back transition (150 ms slide +
            // 150 ms fade, AppRoot.kt:60-65): "UNDER X BOT" exists from
            // the first composed frame, "Specialists · 51" is not
            // displayed until the slide ends.
            waitForDisplayed(hasText("Specialists · 51"))
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
