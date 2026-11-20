package com.mazharansari.x

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Rect
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
import kotlin.math.abs

// Scroll-down arrow / scrim (2026-11-20, user order with video evidence): a dark
// tile sat above the chips row and the last reply line was half-cut by the
// list's bottom edge. The cause was the scroll control itself — an in-flow
// CircleButton (p.surface tile + border) inside the bottom stack — so every
// time it appeared the stack grew 58dp, the list viewport shrank by the same
// 58dp, and the last visible line was shoved half under the list edge. The
// fix: the arrow is a bare chevron (IconTap, no background) floating as an
// overlay on the list, and the LazyColumn bottom padding is 24dp.
//
// What each test proves (honest scope):
// - scrollArrowHasNoTileBackground: the control is a 22dp chevron in a 48dp
//   touch target, and the rendered pixels at the four edge-midpoints of its
//   box are NOT the old tile colour p.surface #1D2026 (29,32,38) nor its
//   border p.border #2E333D (46,51,61). Edge-midpoints (not corners) are used
//   because the old tile's 8dp rounded corners leave the corner pixels bare.
//   The seed keeps assistant bubbles off the arrow zone (their right edge is
//   48dp from the screen edge, the arrow box starts 12dp from it), and a
//   user bubble (p.userBubble #2A2F38) matches neither colour, so a failure
//   here means a tile is really drawn. The saved PNG is the visual proof to
//   eyeball; the pixel check is the machine proof.
// - chipsRowDoesNotMoveWhenScrollArrowAppears: the ToolsRow ("Research" chip)
//   top is identical with the arrow hidden (scrolled to bottom) and visible
//   (scrolled up) — i.e. the arrow no longer steals 58dp from the list, so
//   no message line gets shoved under the list edge when it appears. This
//   test FAILS on the old in-flow tile and passes on the overlay.
// - lastMessageLineClearsTheChipsRowAtBottom: at the bottom the last line
//   sits fully above the chips row (24dp bottom padding), and the arrow is
//   hidden there. This one is a guard, not a regression test: it also passed
//   with the old 12dp padding.
@OptIn(ExperimentalRoborazziApi::class, ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-280dpi", application = AxionApp::class)
class V06ScrollScrimTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val shots = File(System.getProperty("user.dir"), "build/screens_v06scrim").apply { mkdirs() }
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
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        return sc to vm
    }

    // Fifteen short messages: tall enough to scroll, and every assistant reply
    // is one short line, so no assistant bubble ever reaches the arrow zone at
    // the screen's right edge.
    private fun seedShort(): List<Message> {
        val now = System.currentTimeMillis()
        val replies = listOf(
            "Hey! Kaise ho aap?",
            "Main theek hoon, aap batao.",
            "Sab kaisa chal raha hai?",
            "Bilkul theek, shukriya.",
            "Aur batao, kya chahiye?",
            "Bas yahi poochna tha.",
            "Theek hai, phir milte hain.",
        )
        val list = mutableListOf<Message>()
        list += Message("u0", Role.USER, "First question about the shop", createdAt = now - 140_000)
        replies.forEachIndexed { i, r ->
            list += Message("a$i", Role.ASSISTANT, r, status = MsgStatus.DONE, model = "gemini-3.1-flash-lite", createdAt = now - 130_000 + i * 10_000)
            list += Message("u${i + 1}", Role.USER, "hi", createdAt = now - 125_000 + i * 10_000)
        }
        return list
    }

    private fun scrollUpToFirst() {
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("First question about the shop"))
        compose.mainClock.advanceTimeBy(1200)
        compose.waitForIdle()
    }

    // The 48dp touch-target box around the chevron: the clickable ancestor of
    // the icon node. Falls back to the icon bounds expanded by the 13dp margin
    // on each side (the icon is centred in its box by IconTap).
    private fun arrowBox(): Rect {
        val icon = compose.onNodeWithContentDescription("Scroll to bottom").fetchSemanticsNode().boundsInRoot
        runCatching {
            val parent = compose.onNode(hasClickAction() and hasAnyDescendant(hasContentDescription("Scroll to bottom")))
                .fetchSemanticsNode().boundsInRoot
            if (parent.width > 0f && parent.height > 0f) return parent
        }
        val pad = 13f * app.resources.displayMetrics.density
        return Rect(icon.left - pad, icon.top - pad, icon.right + pad, icon.bottom + pad)
    }

    private fun pixel(bmp: Bitmap, x: Int, y: Int): Triple<Int, Int, Int> {
        val c = bmp.getPixel(x.coerceIn(0, bmp.width - 1), y.coerceIn(0, bmp.height - 1))
        return Triple((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
    }

    // Old tile fill p.surface #1D2026 (29,32,38) or its border p.border #2E333D
    // (46,51,61). The colours are opaque, so +/-2 is a safe match window; the
    // user bubble #2A2F38 (42,47,56) and the background #14161A (20,22,26) both
    // fall outside it.
    private fun isTileOrBorder(rgb: Triple<Int, Int, Int>): Boolean {
        fun near(t: Triple<Int, Int, Int>) =
            abs(rgb.first - t.first) <= 2 && abs(rgb.second - t.second) <= 2 && abs(rgb.third - t.third) <= 2
        return near(Triple(29, 32, 38)) || near(Triple(46, 51, 61))
    }

    @Test fun scrollArrowHasNoTileBackground() {
        val (sc, _) = show(seedShort())
        scrollUpToFirst()
        compose.onNodeWithContentDescription("Scroll to bottom").assertExists()
        // Bare 22dp chevron inside a 48dp touch target.
        val d = app.resources.displayMetrics.density
        val icon = compose.onNodeWithContentDescription("Scroll to bottom").fetchSemanticsNode().boundsInRoot
        assertEquals(22f * d, icon.width, 3f)
        assertEquals(22f * d, icon.height, 3f)
        val box = arrowBox()
        assertEquals(48f * d, box.width, 3f)
        assertEquals(48f * d, box.height, 3f)
        cap("01_scroll_arrow_no_tile")
        val bmp = BitmapFactory.decodeFile(File(shots, "01_scroll_arrow_no_tile.png").path)
        assertNotNull("Roborazzi screenshot missing — the pixel check cannot run", bmp)
        val root = compose.onAllNodes(isRoot())[0].fetchSemanticsNode().boundsInRoot
        val sx = bmp!!.width / root.width
        val sy = bmp.height / root.height
        // Edge-midpoints of the box, 3px inside the edge: inside the old tile's
        // fill, clear of its rounded corners, and clear of the centred chevron.
        val points = listOf(
            "top" to (box.centerX to box.top + 3f),
            "bottom" to (box.centerX to box.bottom - 4f),
            "left" to (box.left + 3f to box.centerY),
            "right" to (box.right - 4f to box.centerY),
        )
        for ((name, p) in points) {
            val rgb = pixel(bmp, (p.first * sx).toInt(), (p.second * sy).toInt())
            assertFalse("scroll-arrow box edge [$name] is the old tile/border colour $rgb — a tile is still drawn behind the chevron", isTileOrBorder(rgb))
        }
        sc.close()
    }

    @Test fun chipsRowDoesNotMoveWhenScrollArrowAppears() {
        val (sc, _) = show(seedShort())
        // At the bottom the arrow is hidden and the chips row sits in place.
        compose.onNodeWithContentDescription("Scroll to bottom").assertDoesNotExist()
        val top0 = compose.onNodeWithText("Research").fetchSemanticsNode().boundsInRoot.top
        scrollUpToFirst()
        compose.onNodeWithContentDescription("Scroll to bottom").assertExists()
        val top1 = compose.onNodeWithText("Research").fetchSemanticsNode().boundsInRoot.top
        assertEquals("chips row moved when the scroll arrow appeared — the old in-flow tile grew the bottom stack and shrank the list by 58dp", top0, top1, 1f)
        cap("02_chips_row_stable")
        sc.close()
    }

    @Test fun lastMessageLineClearsTheChipsRowAtBottom() {
        val now = System.currentTimeMillis()
        val tail = "The shop opens at nine, the masala is fresh, and that is the whole story. ENDMARK"
        val messages = seedShort() + Message("aLast", Role.ASSISTANT, tail, status = MsgStatus.DONE,
            model = "gemini-3.1-flash-lite", createdAt = now - 5_000)
        val (sc, _) = show(messages)
        // At the bottom the arrow is hidden and the last line sits fully above
        // the chips row — never half under the list's bottom edge.
        compose.onNodeWithContentDescription("Scroll to bottom").assertDoesNotExist()
        val last = compose.onNodeWithText("ENDMARK", substring = true).fetchSemanticsNode().boundsInRoot
        val chipsTop = compose.onNodeWithText("Research").fetchSemanticsNode().boundsInRoot.top
        assertTrue("last line bottom=${last.bottom} is under the chips row top=$chipsTop", last.bottom < chipsTop)
        cap("03_last_line_clears_chips")
        sc.close()
    }
}
