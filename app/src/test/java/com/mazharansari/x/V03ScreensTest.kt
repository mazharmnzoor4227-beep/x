package com.mazharansari.x

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.mazharansari.x.data.Connector
import com.mazharansari.x.ui.components.ScreenHeader
import com.mazharansari.x.ui.components.Pill
import com.mazharansari.x.ui.components.Gk
import com.mazharansari.x.ui.Route
import com.mazharansari.x.ui.theme.AxionTheme
import com.mazharansari.x.ui.theme.P
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@OptIn(ExperimentalRoborazziApi::class, ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi", application = AxionApp::class)
class V03ScreensTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val shots = File(System.getProperty("user.dir"), "build/screens_v3").apply { mkdirs() }
    private fun shot(name: String) { compose.waitForIdle(); captureScreenRoboImage(File(shots, "$name.png").path, RoborazziOptions()) }

    /** Screenshot without waitForIdle — the synthetic IME dispatch leaves the runtime non-idle (see popoverWithKeyboard). */
    private fun shotNoIdle(name: String) { compose.mainClock.advanceTimeBy(300); captureScreenRoboImage(File(shots, "$name.png").path, RoborazziOptions()) }

    /** V051 pattern: set the ViewModel's messages directly (its setter is private). */
    private fun setMessages(vm: com.mazharansari.x.ui.ChatViewModel, list: List<com.mazharansari.x.data.Message>) {
        val f = vm.javaClass.getDeclaredField("messages\$delegate").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") (f.get(vm) as androidx.compose.runtime.MutableState<List<com.mazharansari.x.data.Message>>).value = list
    }

    private fun seed() {
        val fd = ApplicationProvider.getApplicationContext<AxionApp>().filesDir
        File(fd, "settings.json").writeText("""{"userName":"Mazhar Ansari","theme":"dark","mode":"AUTO"}""")
        fun conn(p: String, hint: String, models: List<String>) = Connector(java.util.UUID.randomUUID().toString(), p, "plain:" + android.util.Base64.encodeToString("test-key".toByteArray(), 2), hint, models, models, addedAt = 1)
        File(fd, "connectors.json").writeText(Json.encodeToString(ListSerializer(Connector.serializer()), listOf(conn("gemini", "AIza…x9Qk", listOf("gemini-3.8-flash")))))
    }

    @Config(qualifiers = "w404dp-h897dp-xxhdpi")
    @org.junit.Ignore("v0.3 script expects a lazy list on Voice Library; since v0.3.1 that page is a plain scrolling column. Covered by V04ScreensTest.voiceSettings.")
    @Test fun voiceLibrary() {
        seed()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            compose.onNodeWithContentDescription("Menu").performClick()
            compose.onNodeWithContentDescription("Settings").performClick(); compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            compose.onNodeWithText("Voice").performClick(); compose.mainClock.advanceTimeBy(900)
            captureScreenRoboImage(File(shots, "v3_voice_library_top.png").path)
            val list = compose.onAllNodes(hasScrollToNodeAction()).onFirst()
            list.performScrollToIndex(2); compose.mainClock.advanceTimeBy(500); captureScreenRoboImage(File(shots, "v3_voice_library_kodex.png").path)
            list.performScrollToIndex(4); compose.mainClock.advanceTimeBy(500); captureScreenRoboImage(File(shots, "v3_voice_library_natural.png").path)
            list.performScrollToIndex(6); compose.mainClock.advanceTimeBy(500); captureScreenRoboImage(File(shots, "v3_voice_library_gemini.png").path)
            compose.mainClock.autoAdvance = true
        }
    }

    /** The real call screen (debug hook shows a mid-call state without opening the mic). */
    @Test fun callScreen() {
        seed()
        val app = ApplicationProvider.getApplicationContext<AxionApp>()
        org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            compose.waitForIdle()
            // AppNotIdle (f545e2a): waitForIdle() spins 60 s on the Chat screen
            // under Robolectric — same flaky race as V031ScreensTest.open()
            // (callScreen passes one-by-one in the targeted run). Drive the
            // clock by hand from here on; the long-press below advances it
            // itself, and the capture uses advanceTimeBy.
            compose.mainClock.autoAdvance = false
            // Redesign round 2: the app opens on the Home list, not the chat
            // screen — navigate to a chat first (debugStart hook, no new chat).
            sc.onActivity { androidx.lifecycle.ViewModelProvider(it)[com.mazharansari.x.ui.ChatViewModel::class.java].debugStart = Route.Chat }
            compose.mainClock.advanceTimeBy(800)
            sc.onActivity { androidx.lifecycle.ViewModelProvider(it)[com.mazharansari.x.ui.ChatViewModel::class.java].debugCall =
                com.mazharansari.x.voice.SameVoiceCall.State(userText = "Aaj Karachi ka mausam kaisa hai?", replyText = "Aaj Karachi mein dhoop hai, takreeban 31 degree. Shaam ko thodi thandi hawa chalegi.") }
            // The old composer "Voice mode" button is gone in the redesign;
            // the voice call is now the composer's mic long-press
            // (Composer.kt: "Dictate — long-press for a voice call" ->
            // onVoiceCall -> Route.VoiceCall).
            compose.onNodeWithContentDescription("Dictate — long-press for a voice call")
                .performTouchInput { longClick() }
            compose.mainClock.autoAdvance = false; compose.mainClock.advanceTimeBy(900)
            captureScreenRoboImage(File(shots, "v3_call_same_voice.png").path)
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun connectorsLogos() {
        seed()
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            compose.waitForIdle()
            // Redesign round 2: the app opens on the Home list — navigate to
            // a chat first (the + popover lives on the chat screen).
            sc.onActivity { androidx.lifecycle.ViewModelProvider(it)[com.mazharansari.x.ui.ChatViewModel::class.java].debugStart = Route.Chat }
            compose.mainClock.advanceTimeBy(800)
            // The single-chat-screen menu drawer has no "Connectors" entry (the
            // banner that linked there only shows when no key is saved, and
            // seed() saves one) — reach Connectors through the + popover instead.
            compose.onNodeWithContentDescription("Attach").performClick(); compose.waitForIdle(); Thread.sleep(300)
            compose.onNodeWithText("Connectors").performClick(); compose.waitForIdle(); Thread.sleep(300)
            shot("v3_connectors_logos")
            compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("xAI Grok")); compose.waitForIdle()
            shot("v3_connectors_logos_2")
        }
    }


    @Test fun expandableSteps() {
        seed()
        val t0 = System.currentTimeMillis() - 60_000
        val html = "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n  <meta charset=\"utf-8\">\n  <title>Clawora Beauty</title>\n  <style>\n    /* mobile-first */\n    body { font-family: system-ui; margin: 0; background: #fff7f9; }\n    .hero { padding: 48px 20px; text-align: center; }\n  </style>\n</head>\n<body>\n  <section class=\"hero\">\n    <h1>Original Korean Skincare</h1>\n    <p>Cash on Delivery all over Pakistan</p>\n    <a class=\"btn\" href=\"#shop\">Shop now</a>\n  </section>\n  <script>\n    const year = new Date().getFullYear(); // footer year\n    document.title = 'Clawora ' + year;\n  </script>\n</body>\n</html>"
        fun st(k: com.mazharansari.x.data.StepKind, title: String, target: String, out: String = "", detail: String = "") =
            com.mazharansari.x.data.Step(java.util.UUID.randomUUID().toString(), k, title, target, out, com.mazharansari.x.data.StepStatus.DONE, t0, t0 + 1500, detail)
        val steps = listOf(
            st(com.mazharansari.x.data.StepKind.COMMAND, "Bash", "mkdir -p outputs/clawora && ls outputs", "clawora"),
            st(com.mazharansari.x.data.StepKind.WRITE, "Created file", "outputs/clawora/index.html", "Wrote 812 bytes", html),
        )
        val conv = com.mazharansari.x.data.Conversation("demo2", "html page", listOf(
            com.mazharansari.x.data.Message("u1", com.mazharansari.x.data.Role.USER, "mera ek kaam karo, ek html page bana do Clawora Beauty ke liye", createdAt = t0),
            com.mazharansari.x.data.Message("a1", com.mazharansari.x.data.Role.ASSISTANT, "Ho gaya! Page **outputs/clawora/index.html** mein hai — hero section, COD line aur Shop button ke saath.",
                steps = steps, reasoning = "User ne Clawora Beauty ke liye simple landing page manga hai. Pehle folder banata hoon, phir mobile-first index.html likhta hoon.",
                thinkingMs = 9_000, model = "Groq · openai/gpt-oss-120b", createdAt = t0 + 9_000),
        ), createdAt = t0, updatedAt = System.currentTimeMillis())
        // The file-load path (vm.open) is covered by V031ScreensTest.open();
        // this test is about the steps UI, and open()'s async IO load races
        // waitUntil's virtual clock under Robolectric (the 5 s budget can burn
        // before the real-time IO thread lands), so set the conversation
        // directly — the same reflection the V051 screenshot tests use.
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            compose.waitForIdle()
            // AppNotIdle (f545e2a): same Robolectric idling race as callScreen
            // above — drive the clock by hand from here on.
            compose.mainClock.autoAdvance = false
            sc.onActivity { act -> setMessages(androidx.lifecycle.ViewModelProvider(act)[com.mazharansari.x.ui.ChatViewModel::class.java], conv.messages) }
            // Redesign round 2: the app opens on the Home list — navigate to
            // the chat screen without newChat (it would clear these messages).
            sc.onActivity { act -> androidx.lifecycle.ViewModelProvider(act)[com.mazharansari.x.ui.ChatViewModel::class.java].debugStart = Route.Chat }
            compose.mainClock.advanceTimeBy(800)
            compose.waitUntil(5000) { compose.onAllNodesWithText("Thought for", substring = true).fetchSemanticsNodes().isNotEmpty() }
            shot("v3_steps_collapsed")
            compose.onNodeWithContentDescription("Expand").performClick(); compose.waitForIdle(); Thread.sleep(400)
            shot("v3_steps_file_expanded")
            compose.onNodeWithText("Thought for", substring = true).performClick(); compose.waitForIdle(); Thread.sleep(500)
            shot("v3_thoughts_sheet_whole_run")
        }
    }

    /** Model popover with the keyboard open (IME inset injected like on his phone): bottom anchored above the composer, top under the top bar. */
    @Test fun popoverWithKeyboard() {
        seed()
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            compose.waitForIdle()
            // Redesign round 2: the app opens on the Home list — navigate to
            // a chat first (the composer and its popovers live there).
            sc.onActivity { a -> androidx.lifecycle.ViewModelProvider(a)[com.mazharansari.x.ui.ChatViewModel::class.java].debugStart = Route.Chat }
            compose.mainClock.advanceTimeBy(800)
            sc.onActivity { a ->
                val root = a.window.decorView
                val ins = WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 96, 0, 0))
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 132))
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 1050))
                    .setVisible(WindowInsetsCompat.Type.ime(), true)
                    .build()
                ViewCompat.dispatchApplyWindowInsets(root, ins)
            }
            // waitForIdle() never returns after the synthetic IME dispatch
            // under Robolectric (this test failed on the donor too); every
            // other screen test advances the clock after dispatching insets
            // instead — do that here, and capture without waiting for idle
            // (V031's cap() pattern). Espresso.pressBack is gone for the same
            // reason (it waits for idle); the + button closes the model
            // popover itself (Composer onPlus sets model=false), so no back
            // press is needed to reach the plus popover.
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithText("Auto").performClick(); compose.mainClock.advanceTimeBy(400)
            shotNoIdle("v3_model_popover_keyboard")
            compose.onNodeWithContentDescription("Attach").performClick(); compose.mainClock.advanceTimeBy(400)
            shotNoIdle("v3_plus_popover_keyboard")
        }
    }
}
