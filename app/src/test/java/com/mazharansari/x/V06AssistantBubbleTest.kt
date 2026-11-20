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
import com.mazharansari.x.data.*
import com.mazharansari.x.ui.ChatViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Assistant-reply bubble (2026-11-20, user order): the agent's reply must render
 * inside a box exactly like the user's bubble, mirrored to the left — not as bare
 * text on the background. Follows the V05/V06 screenshot-test pattern: a real
 * ChatScreen under Robolectric, messages seeded on the view model, Roborazzi
 * capture to build/screens_v06bubble.
 *
 * What each test proves (honest scope): the node assertions prove the bubble
 * container exists in the semantics tree AND the reply text's bounds sit fully
 * inside the bubble's bounds — i.e. the text renders inside the box, not beside
 * it. The saved PNG is the visual proof to eyeball (Robolectric pixels are not
 * asserted colour-by-colour); the bubble background itself is the p.card theme
 * token, the same token the in-chat cards already use.
 */
@OptIn(ExperimentalRoborazziApi::class, ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-280dpi", application = AxionApp::class)
class V06AssistantBubbleTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val shots = File(System.getProperty("user.dir"), "build/screens_v06bubble").apply { mkdirs() }
    private val app get() = ApplicationProvider.getApplicationContext<AxionApp>()
    private fun cap(name: String) { compose.mainClock.advanceTimeBy(1200); compose.onAllNodes(isRoot())[0].captureRoboImage(File(shots, "$name.png").path) }

    private fun launch(): Pair<ActivityScenario<MainActivity>, ChatViewModel> {
        File(app.filesDir, "settings.json").writeText("""{"theme":"dark","mode":"AUTO","buildIntroShown":true,"guestChosen":true,"introShown":true,"permsShown":true}""")
        app.graph.settings.update { it.copy(buildIntroShown = true, guestChosen = true, introShown = true, permsShown = true, toolSwitches = emptyMap()) }
        val sc = ActivityScenario.launch(MainActivity::class.java); compose.waitForIdle()
        sc.onActivity { act ->
            val b = WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 63, 0, 0)).setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 30))
            ViewCompat.dispatchApplyWindowInsets(act.window.decorView, b.build())
        }
        var vm: ChatViewModel? = null
        sc.onActivity { vm = ViewModelProvider(it)[ChatViewModel::class.java] }
        compose.mainClock.advanceTimeBy(1200)
        return sc to vm!!
    }

    private fun setMessages(vm: ChatViewModel, list: List<Message>) {
        val f = vm.javaClass.getDeclaredField("messages\$delegate").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") (f.get(vm) as androidx.compose.runtime.MutableState<List<Message>>).value = list
    }

    private fun show(messages: List<Message>): Pair<ActivityScenario<MainActivity>, ChatViewModel> {
        val (sc, vm) = launch()
        sc.onActivity { setMessages(vm, messages) }
        compose.mainClock.advanceTimeBy(800)
        return sc to vm
    }

    /** The reply text's bounds must sit fully inside the bubble's bounds. */
    private fun assertTextInsideBubble(text: String) {
        val bubble = compose.onNodeWithContentDescription("Assistant reply bubble").fetchSemanticsNode().boundsInRoot
        val node = compose.onNodeWithText(text, substring = true).fetchSemanticsNode().boundsInRoot
        assertTrue("reply text [$text] is outside the bubble: text=$node bubble=$bubble",
            bubble.left <= node.left && bubble.top <= node.top && bubble.right >= node.right && bubble.bottom >= node.bottom)
    }

    @Test fun assistantReplyRendersInABubbleLikeTheUserMessage() {
        val now = System.currentTimeMillis()
        val reply = "Hey! Kaise ho aap? Main yahan hoon, batao kya madad chahiye?"
        val (sc, _) = show(listOf(
            Message("u1", Role.USER, "hi", createdAt = now - 60_000),
            Message("a1", Role.ASSISTANT, reply, status = MsgStatus.DONE, model = "gemini-3.1-flash-lite",
                firstWordMs = 200, createdAt = now - 50_000),
        ))
        compose.onNodeWithText("hi").assertExists()
        compose.onNodeWithText(reply, substring = true).assertExists()
        // The bubble exists, the reply sits inside it, and the action row is still
        // there below it (Copy glyph + model label).
        compose.onNodeWithContentDescription("Assistant reply bubble").assertExists()
        assertTextInsideBubble(reply)
        compose.onNodeWithContentDescription("Copy").assertExists()
        compose.onNodeWithText("gemini-3.1-flash-lite", substring = true).assertExists()
        cap("01_assistant_bubble")
        sc.close()
    }

    @Test fun streamingReplyRendersInsideTheBubble() {
        val now = System.currentTimeMillis()
        val partial = "Hey! Kaise ho aap? Main"
        val (sc, _) = show(listOf(
            Message("u1", Role.USER, "hi", createdAt = now - 60_000),
            Message("a1", Role.ASSISTANT, partial, status = MsgStatus.STREAMING,
                model = "gemini-3.1-flash-lite", createdAt = now - 50_000, thinkingStartedAt = now - 50_000),
        ))
        // Streaming text appears inside the box as it arrives, not outside it.
        compose.onNodeWithContentDescription("Assistant reply bubble").assertExists()
        assertTextInsideBubble(partial)
        cap("02_streaming_bubble")
        sc.close()
    }

    @Test fun assistantBubbleHoldsMarkdownCodeBlock() {
        val now = System.currentTimeMillis()
        val reply = "Here is a quick script:\n```bash\necho hello-this-is-a-very-long-command-line-that-must-not-break-out-of-the-bubble\n```\nRun it and tell me what you see."
        val (sc, _) = show(listOf(
            Message("u1", Role.USER, "Give me a bash one-liner", createdAt = now - 60_000),
            Message("a1", Role.ASSISTANT, reply, status = MsgStatus.DONE, model = "gemini-3.1-flash-lite", createdAt = now - 50_000),
        ))
        compose.onNodeWithContentDescription("Assistant reply bubble").assertExists()
        compose.onNodeWithText("echo hello", substring = true).assertExists()
        assertTextInsideBubble("Run it and tell me")
        cap("03_bubble_markdown_code")
        sc.close()
    }

    @Test fun emptyStreamingReplyShowsNoBubble() {
        val now = System.currentTimeMillis()
        val (sc, _) = show(listOf(
            Message("u1", Role.USER, "hi", createdAt = now - 60_000),
            Message("a1", Role.ASSISTANT, "", status = MsgStatus.STREAMING,
                createdAt = now - 50_000, thinkingStartedAt = now - 50_000),
        ))
        // Thinking-only streaming: no empty box on screen.
        compose.onNodeWithContentDescription("Assistant reply bubble").assertDoesNotExist()
        sc.close()
    }
}
