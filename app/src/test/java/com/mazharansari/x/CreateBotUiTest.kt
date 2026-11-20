package com.mazharansari.x

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.mazharansari.x.data.X_BOT_ID
import com.mazharansari.x.ui.ChatViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Create bot — UI flow (2026-11-20): the real Bots screen, following the
 * V06NavScreensTest pattern (hand-driven clock, poll-for-displayed). The
 * pure-JVM tests in [IdentityProfilesCreateBotTest] prove the store; this
 * one proves the entry is reachable, the sheet saves, the bot appears in
 * the list, and its row opens its own chat.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi", application = AxionApp::class)
@OptIn(ExperimentalTestApi::class)
class CreateBotUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun seed() {
        val app = ApplicationProvider.getApplicationContext<AxionApp>()
        val fd = app.filesDir
        File(fd, "settings.json").writeText("""{"userName":"Mazhar Ansari","theme":"dark","mode":"AUTO"}""")
        File(fd, "bots/bots.json").delete()
    }

    private fun launch(): ActivityScenario<MainActivity> {
        seed()
        val sc = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        // The screens read the ACTIVITY's graph — reset X Bot's name
        // there (a previous test may have renamed it in memory).
        sc.onActivity { act ->
            val vm = ViewModelProvider(act)[ChatViewModel::class.java]
            vm.g.botStore.rename(X_BOT_ID, "X Bot")
        }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(800)
        return sc
    }

    private fun waitForNode(matcher: SemanticsMatcher, timeoutMs: Long = 10_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            compose.mainClock.advanceTimeBy(200)
            Thread.sleep(100)
            if (compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()) return
        }
        error("node $matcher never appeared")
    }

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
        error("node $matcher never displayed")
    }

    private fun openBots() {
        waitForNode(hasText("Ask anything"))
        compose.onNodeWithContentDescription("Menu").performClick()
        waitForNode(hasText("Bots"))
        compose.onNodeWithText("Bots").performClick()
        waitForNode(hasText("UNDER X BOT"))
        waitForDisplayed(hasText("X Bot"))
    }

    @Test fun createBotFlow_appearsInListAndOpensOwnChat() {
        val sc = launch()
        var vm: ChatViewModel? = null
        try {
            sc.onActivity { vm = ViewModelProvider(it)[ChatViewModel::class.java] }
            openBots()
            // The Create bot entry is visible in the Bots section.
            compose.onNodeWithText("Create bot").assertIsDisplayed()
            compose.onNodeWithText("Create bot").performClick()
            waitForNode(hasText("New bot"))
            // Fill the sheet exactly as the user would.
            compose.onNodeWithTag("createBotName").performTextInput("Helper Bot")
            compose.onNodeWithTag("createBotDescription").performTextInput("Helps with chores")
            compose.onNodeWithTag("createBotInstructions").performTextInput("You are Helper Bot. Be tidy.")
            compose.onNodeWithTag("createBotConfirm").performClick()
            // The new bot appears in the Bots list.
            waitForNode(hasText("Helper Bot"))
            compose.onNodeWithText("Helper Bot").assertIsDisplayed()
            // X Bot is still there, first — it stays the orchestrator.
            compose.onNodeWithText("X Bot").assertIsDisplayed()
            // Tapping the new bot opens ITS own chat (its name in the header).
            compose.onNodeWithText("Helper Bot").performClick()
            waitForNode(hasText("Ask anything"))
            waitForDisplayed(hasText("Helper Bot"))
            compose.onNodeWithText("Helper Bot").assertIsDisplayed()
            // Back returns to the bot list.
            compose.onNodeWithContentDescription("Back").performClick()
            waitForNode(hasText("UNDER X BOT"))
            waitForDisplayed(hasText("Helper Bot"))
        } finally {
            // Leave a clean store for the next test.
            runCatching {
                vm?.g?.botStore?.definitions?.value
                    ?.firstOrNull { it.name == "Helper Bot" }
                    ?.let { vm?.g?.botStore?.remove(it.id) }
            }
            sc.close()
        }
    }
}
