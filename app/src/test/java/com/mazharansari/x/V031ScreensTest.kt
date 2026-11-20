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
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.mazharansari.x.agent.PdfWriter
import com.mazharansari.x.data.*
import com.mazharansari.x.ui.ChatViewModel
import com.mazharansari.x.ui.Route
import com.mazharansari.x.voice.ReadAloudPlayer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * v0.3.1 renders for the Grok side-by-side sheets: mazhar's phone is 720×1600 at 1.75x (411×914 dp); rendered here at
 * 411 dp × 2.625 = 1080 px wide with his measured status bar (41 dp) and gesture bar (17 dp).
 */
@OptIn(ExperimentalRoborazziApi::class, ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-420dpi", application = AxionApp::class)
class V031ScreensTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val shots = File(System.getProperty("user.dir"), "build/screens_v031").apply { mkdirs() }
    private fun cap(name: String) { compose.mainClock.advanceTimeBy(1200); captureScreenRoboImage(File(shots, "$name.png").path) }

    private val reply = """
PDF tayyar hai ✅ Is mein ye sab shamil hai:

- **Store overview** — Clawora Beauty ka business summary, products aur target customers
- **Sales report** — pichle 30 din ki sales, top products aur average order value
- **Stock list** — kaun se products kam hain aur kab dobara mangwane hain
- **Out-of-stock alerts** — 4 products jo abhi khatam hain
- **Next steps** — agle hafte ke 5 kaam, priority ke hisaab se

Aap PDF ko khol ke dekh sakte ho, share ya download bhi kar sakte ho. Kuch badalna ho to batao, main update kar dunga.

Mazeed kuch chahiye? Jaise Instagram ke liye post ideas ya next month ka sales target?
""".trim()

    private fun seed(): String {
        val app = ApplicationProvider.getApplicationContext<AxionApp>()
        val fd = app.filesDir
        File(fd, "settings.json").writeText("""{"userName":"Mazhar Ansari","theme":"dark","mode":"FAST"}""")
        fun conn(p: String, hint: String, models: List<String>) = Connector(java.util.UUID.randomUUID().toString(), p, "plain:" + android.util.Base64.encodeToString("test-key".toByteArray(), 2), hint, models, models, addedAt = 1)
        File(fd, "connectors.json").writeText(Json.encodeToString(ListSerializer(Connector.serializer()), listOf(conn("gemini", "AIza…x9Qk", listOf("gemini-3.8-flash")))))
        val pdf = File(fd, "workspace/outputs/Clawora Beauty Store Report.pdf")
        val md = """
## Store overview
Clawora Beauty — original Korean skincare, Cash on Delivery all over Pakistan.

| Product | Sold | Price (Rs) | Stock |
|---|---|---|---|
| COSRX Snail 92 Cream | 48 | 4,250 | 12 |
| Beauty of Joseon Sunscreen | 61 | 3,900 | 0 |
| Anua Heartleaf Toner | 37 | 4,600 | 5 |
| Some By Mi AHA BHA Serum | 29 | 3,450 | 0 |
| Round Lab Dokdo Cleanser | 22 | 2,950 | 18 |

## Sales report
- Total orders (30 days): **197**
- Average order value: **Rs 4,120**
- Best day: Friday

## Out-of-stock alerts
- Beauty of Joseon Sunscreen
- Some By Mi AHA BHA Serum

## Next steps
1. Re-order the 4 out-of-stock products
2. Post 3 reels this week
""".trim()
        // Robolectric has no real PdfDocument: write placeholder bytes, then the same page images the app draws next to every PDF it makes.
        val pages = runCatching { PdfWriter.write(pdf, "Clawora Beauty — Store Report", md) }.getOrElse {
            pdf.parentFile!!.mkdirs(); pdf.writeBytes("%PDF-1.4\n".toByteArray() + ByteArray(40_000))
            PdfWriter.writePreviews(pdf, "Clawora Beauty — Store Report", md)
            PdfWriter.previewDir(pdf).listFiles()!!.size
        }
        val t0 = System.currentTimeMillis() - 120_000
        val steps = listOf(Step("s1", StepKind.PDF, "Created PDF", pdf.name, "", StepStatus.DONE, t0, t0 + 2000))
        val conv = Conversation("v031", "Store report PDF", listOf(
            Message("u1", Role.USER, "Mere store ki report PDF bana do", createdAt = t0),
            Message("a1", Role.ASSISTANT, reply, steps = steps, artifacts = listOf(Artifact(pdf.name, "application/pdf", pdf.path, pdf.length(), pages)),
                thinkingMs = 14_000, model = "Google Gemini · gemini-3.8-flash", createdAt = t0 + 20_000),
        ), createdAt = t0, updatedAt = System.currentTimeMillis())
        File(fd, "chats").mkdirs(); File(fd, "chats/v031.json").writeText(Json.encodeToString(conv))
        return conv.id
    }

    private fun insets(sc: ActivityScenario<MainActivity>, ime: Int = 0) {
        sc.onActivity { act ->
            val root = act.window.decorView
            val b = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 108, 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 45))
            if (ime > 0) b.setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, ime)).setVisible(WindowInsetsCompat.Type.ime(), true)
            ViewCompat.dispatchApplyWindowInsets(root, b.build())
        }
    }

    private fun open(): Pair<ActivityScenario<MainActivity>, ChatViewModel> {
        val id = seed()
        val sc = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        // AppNotIdle (f545e2a, 10 screen tests): under Robolectric, waitForIdle()
        // spins the full 60 s on the Chat screen — the scroll animations plus the
        // async conversation load race the virtual clock, and the failure is
        // flaky (callScreen passes one-by-one in the targeted run). There is no
        // infinite loop in the app: the messages are DONE (no streaming ticker),
        // the gated infinite animations (DotGrid/shimmer/pulse) do not render on
        // this screen, and Home idles fine. So drive the clock by hand from here
        // on — the pattern cap()/pill()/shotNoIdle() already use elsewhere.
        compose.mainClock.autoAdvance = false
        insets(sc)
        var vm: ChatViewModel? = null
        // Navigation restructure (2026-11-20): the app opens on the chat
        // screen already — open the conversation straight into it.
        sc.onActivity { vm = ViewModelProvider(it)[ChatViewModel::class.java]; vm!!.debugStart = Route.Chat; vm!!.open(id) }
        // vm.open() loads the conversation on a real IO thread, which the virtual
        // clock cannot advance — and waitUntil's 5000 ms budget is virtual time
        // that burns in milliseconds of real time, so a slow load in the loaded
        // full-suite JVM times it out (ComposeTimeoutException on 010ad0fe,
        // while pdfCard passed — pure race). Poll in REAL time instead: sleep,
        // drive the clock, check the nodes, until the conversation lands or
        // 10 s pass. fetchSemanticsNodes() waits for idle internally, which
        // also idles the main looper so the load's Main-thread resume runs.
        var found = false
        for (i in 0 until 50) {
            compose.mainClock.advanceTimeBy(200)
            Thread.sleep(200)
            if (compose.onAllNodesWithText("Thought for", substring = true).fetchSemanticsNodes().isNotEmpty()) { found = true; break }
        }
        check(found) { "Thought for never appeared — the seeded conversation did not load" }
        return sc to vm!!
    }

    private fun pill(sc: ActivityScenario<MainActivity>, elapsed: Long, total: Long = 62_000) {
        sc.onActivity { ReadAloudPlayer.debugShow("a1", elapsed = elapsed, total = total) }
        repeat(4) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); compose.mainClock.advanceTimeBy(400) }
    }

    private fun list() = compose.onAllNodes(hasScrollToNodeAction()).onFirst()
    /** Scroll to the reply's end without dragging past it. */
    private fun toEnd() {
        list().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, 20_000f) }; compose.mainClock.advanceTimeBy(1500)
    }

    @Test fun pdfCard() {
        val (sc, _) = open()
        list().performScrollToIndex(1); compose.mainClock.advanceTimeBy(600)
        list().performTouchInput { swipeDown(startY = centerY, endY = centerY + 260f) }
        cap("01_pdf_card")
        sc.close()
    }

    @Test fun player() {
        val (sc, _) = open()
        list().performScrollToIndex(1); compose.mainClock.advanceTimeBy(400)
        toEnd()
        pill(sc, 20_000)
        cap("02_player_end")
        sc.close()
    }

    @Test fun playerScrolledUp() {
        val (sc, _) = open()
        list().performScrollToIndex(1); compose.mainClock.advanceTimeBy(400)
        toEnd()
        list().performTouchInput { swipeDown(startY = centerY, endY = centerY + 420f) }
        pill(sc, 1_000)
        cap("03_player_scrolled_up_chevron")
        sc.close()
    }

    @Test fun speedMenu() {
        val (sc, vm) = open()
        list().performScrollToIndex(1); compose.mainClock.advanceTimeBy(400)
        toEnd()
        pill(sc, 21_000)
        compose.onNodeWithContentDescription("Playback speed").performClick()
        cap("04_speed_menu")
        sc.close()
    }

    @Test fun pullNewChat() {
        val (sc, vm) = open()
        list().performScrollToIndex(1); compose.mainClock.advanceTimeBy(400)
        toEnd()
        sc.onActivity { vm.debugPull = 0.55f }
        cap("05a_pull_half")
        sc.onActivity { vm.debugPull = 1.05f }
        cap("05b_pull_new_chat")
        sc.close()
    }

    /**
     * New chat, round-2 contract: the pull-up-for-new-chat gesture is gone —
     * debugPull is unread in main code and nothing in ChatScreen implements the
     * drag — so the gesture can never start a new chat. The entry point is the
     * top-bar "New chat" button (ChatScreen, shown when no bot is active).
     */
    @Test fun newChatButtonStartsNewChat() {
        val (sc, vm) = open()
        compose.onNodeWithContentDescription("New chat").performClick()
        compose.mainClock.advanceTimeBy(500)
        var n = -1; sc.onActivity { n = vm.messages.size }
        org.junit.Assert.assertEquals("tapping New chat → empty chat", 0, n)
        cap("05c_new_chat_button")
        sc.close()
    }

    @Test fun viewer() {
        val (sc, vm) = open()
        sc.onActivity { vm.debugViewer = "Clawora Beauty Store Report.pdf" }
        // No waitForIdle here either (same AppNotIdle race as open()) — drive
        // the clock by hand and capture, the cap() pattern.
        compose.mainClock.advanceTimeBy(1200)
        captureScreenRoboImage(File(shots, "06_pdf_viewer.png").path)
        sc.close()
    }

    @Test fun keyboard() {
        val (sc, _) = open()
        list().performScrollToIndex(1); compose.mainClock.advanceTimeBy(400)
        toEnd()
        insets(sc, ime = 820)
        cap("07_keyboard")
        sc.close()
    }

    @Test fun plusMenu() {
        val (sc, _) = open()
        compose.onNodeWithContentDescription("Attach").performClick()
        cap("08_plus_imagine_build")
        sc.close()
    }
}
