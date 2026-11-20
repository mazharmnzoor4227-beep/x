package com.mazharansari.x

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.mazharansari.x.data.Message
import com.mazharansari.x.data.MsgStatus
import com.mazharansari.x.data.Role
import com.mazharansari.x.data.X_BOT_ID
import com.mazharansari.x.ui.ChatViewModel
import com.mazharansari.x.ui.Route
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Light ("Day") theme renders — the 2026-11-20 white-on-white fix, eyeballed
 * for real. Follows the V06ScreensTest / V06AssistantBubbleTest pattern: a
 * real MainActivity under Robolectric, the real ChatViewModel, Roborazzi
 * capture to app/build/light-theme-shots/ (uploaded as the `light-theme-shots`
 * artifact by .github/workflows/verify.yml). No mockups: every PNG is a
 * render of the real Compose screen in the LIGHT theme.
 *
 * What each test proves: the node assertions prove the screen rendered with
 * its content (not a blank/white wash); the PNG is the visual proof that the
 * bubbles, cards and rows read against the grey canvas. The numeric contrast
 * is owned by LightThemeTest; this file owns the pixels.
 */
@OptIn(ExperimentalRoborazziApi::class, ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-280dpi", application = AxionApp::class)
class LightThemeScreensTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val shots = File(System.getProperty("user.dir"), "build/light-theme-shots").apply { mkdirs() }
    private val app get() = ApplicationProvider.getApplicationContext<AxionApp>()
    private fun cap(name: String) { compose.mainClock.advanceTimeBy(1200); compose.onAllNodes(isRoot())[0].captureRoboImage(File(shots, "$name.png").path) }

    private fun launch(route: Route): Pair<ActivityScenario<MainActivity>, ChatViewModel> {
        File(app.filesDir, "settings.json").writeText("""{"theme":"light","mode":"AUTO","buildIntroShown":true,"guestChosen":true,"introShown":true,"permsShown":true}""")
        // The file above seeds "light", but the store was already constructed
        // (dark) during app start — this update re-persists the in-memory
        // state, so the theme must be set HERE or the file's light value is
        // overwritten with dark before the Activity starts.
        app.graph.settings.update { it.copy(theme = "light", buildIntroShown = true, guestChosen = true, introShown = true, permsShown = true, toolSwitches = emptyMap()) }
        val sc = ActivityScenario.launch(MainActivity::class.java); compose.waitForIdle()
        sc.onActivity { act ->
            val b = WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 63, 0, 0)).setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 30))
            ViewCompat.dispatchApplyWindowInsets(act.window.decorView, b.build())
        }
        var vm: ChatViewModel? = null
        sc.onActivity { vm = ViewModelProvider(it)[ChatViewModel::class.java]; it.let { vm!!.debugStart = route } }
        compose.mainClock.advanceTimeBy(1200)
        return sc to vm!!
    }

    /** Same reflection seam as V06AssistantBubbleTest: seed the transcript. */
    private fun setMessages(vm: ChatViewModel, list: List<Message>) {
        val f = vm.javaClass.getDeclaredField("messages\$delegate").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") (f.get(vm) as androidx.compose.runtime.MutableState<List<Message>>).value = list
    }

    private fun sampleMessages(): List<Message> {
        val now = System.currentTimeMillis()
        return listOf(
            Message("u1", Role.USER, "What is the weather in Karachi tomorrow?", createdAt = now - 60_000),
            Message("a1", Role.ASSISTANT, "Tomorrow in Karachi looks warm and dry — a high near 29°C with a light sea breeze in the evening. No rain expected.", status = MsgStatus.DONE, model = "gemini-3.1-flash-lite",
                firstWordMs = 200, createdAt = now - 50_000),
            Message("u2", Role.USER, "And should I carry an umbrella?", createdAt = now - 40_000),
            Message("a2", Role.ASSISTANT, "No need — with no rain in the forecast an umbrella would just be extra weight.", status = MsgStatus.DONE, model = "gemini-3.1-flash-lite",
                firstWordMs = 180, createdAt = now - 30_000),
        )
    }

    @Test fun homeMenuPanelInLightTheme() {
        val (sc, _) = launch(Route.Home)
        // The menu panel: header, top options, CHATS section.
        compose.onNodeWithText("CHATS").assertExists()
        compose.onNodeWithText("Bots").assertExists()
        compose.onNodeWithText("Settings").assertExists()
        cap("01_home_light")
        sc.close()
    }

    @Test fun chatBubblesInLightTheme() {
        val (sc, vm) = launch(Route.Chat)
        sc.onActivity { setMessages(vm, sampleMessages()) }
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("What is the weather in Karachi tomorrow?").assertExists()
        compose.onNodeWithText("Tomorrow in Karachi looks warm and dry", substring = true).assertExists()
        // The sample carries TWO assistant replies, so there are two bubbles.
        compose.onAllNodesWithContentDescription("Assistant reply bubble").assertCountEquals(2)
        compose.onNodeWithText("Ask anything").assertExists()
        cap("02_chat_light")
        sc.close()
    }

    /** Poll for a node without requiring Compose idle (V06Nav pattern). */
    private fun waitForNode(matcher: SemanticsMatcher, timeoutMs: Long = 10_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            compose.mainClock.advanceTimeBy(200)
            Thread.sleep(100)
            if (compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()) return
        }
        error("node $matcher never appeared")
    }

    @Test fun botChatInLightTheme() {
        val (sc, vm) = launch(Route.BotChat(X_BOT_ID))
        sc.onActivity { setMessages(vm, sampleMessages()) }
        compose.mainClock.advanceTimeBy(800)
        // X Bot's own header + the same transcript/composer as plain chat.
        // Polled, not assertExists: the BotChat route does not reach Compose
        // idle under Robolectric (60s AppNotIdleException on the first
        // assertion), while the same transcript on the plain Chat route
        // idles fine. The nodes below + the PNG are the render proof; the
        // idle hang itself is an open product question, not something this
        // test fixes.
        waitForNode(hasText("X Bot"))
        waitForNode(hasText("What is the weather in Karachi tomorrow?"))
        waitForNode(hasContentDescription("Assistant reply bubble"))
        waitForNode(hasText("Ask anything"))
        cap("03_bot_chat_light")
        sc.close()
    }

    @Test fun botStoreInLightTheme() {
        val (sc, _) = launch(Route.BotStore)
        compose.onNodeWithText("X Bot is your main bot", substring = true).assertExists()
        compose.onNodeWithText("Specialists", substring = true).assertExists()
        cap("04_bot_store_light")
        sc.close()
    }

    @Test fun settingsInLightTheme() {
        val (sc, _) = launch(Route.Settings)
        compose.onNodeWithText("Settings").assertExists()
        cap("05_settings_light")
        sc.close()
    }

    @Test fun chatOverflowSheetInLightTheme() {
        val (sc, vm) = launch(Route.Chat)
        sc.onActivity { setMessages(vm, sampleMessages()) }
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithContentDescription("More options").performClick()
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("Rename").assertExists()
        compose.onNodeWithText("Delete").assertExists()
        cap("06_chat_overflow_light")
        sc.close()
    }
}
