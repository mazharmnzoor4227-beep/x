package com.mazharansari.x

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.mazharansari.x.data.ScheduleEntry
import com.mazharansari.x.data.ScheduleType
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
 * Schedules screen gold-toggle test — split out of ToggleStandardizationTest
 * (2026-11-20 fix-forward): that class needs createComposeRule because its
 * AxionSwitch unit tests call setContent, but the empty rule is the only one
 * that binds its queries to a MainActivity launched via ActivityScenario —
 * createComposeRule binds to its own empty ComponentActivity, whose tree
 * never contains the app's screens, so "Toggle probe task" was never found.
 * The two rules cannot share a class, so this Activity test lives here
 * (V06Nav pattern: createEmptyComposeRule + ActivityScenario.launch).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-280dpi", application = AxionApp::class)
class SchedulesGoldSwitchUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<AxionApp>()

    private fun launch(route: Route): Pair<ActivityScenario<MainActivity>, ChatViewModel> {
        File(app.filesDir, "settings.json").writeText("""{"theme":"dark","mode":"AUTO","buildIntroShown":true,"guestChosen":true,"introShown":true,"permsShown":true}""")
        app.graph.settings.update { it.copy(buildIntroShown = true, guestChosen = true, introShown = true, permsShown = true, toolSwitches = emptyMap()) }
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

    /** Poll for a node without requiring Compose idle (V06Nav pattern). If
     * the node is below the fold in a LazyColumn it is not composed yet, so
     * each empty poll scrolls the list one item further before retrying. */
    private fun waitForNode(matcher: SemanticsMatcher, timeoutMs: Long = 10_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var scrolled = 0
        while (System.currentTimeMillis() < deadline) {
            compose.mainClock.advanceTimeBy(200)
            Thread.sleep(100)
            if (compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()) return
            runCatching { compose.onNode(hasScrollAction()).performScrollToIndex(scrolled++) }
        }
        error("node $matcher never appeared")
    }

    @Test fun schedulesScreenUsesGoldSwitchAndItsToggleStillFlips() {
        val (sc, vm) = launch(Route.Schedules)
        try {
            // Reset on the ACTIVITY's own ViewModel graph — the instance the
            // screens actually read. The pre-launch app.graph is a different
            // Application instance under Robolectric (V06NavScreensTest
            // launch() documents this): a probe added to app.graph.schedules
            // before launch never reaches vm.g.schedules, and the check below
            // proved it — "probe entry missing from the schedules store".
            val store = vm.g.schedules
            store.entries.value.map { it.id }.forEach { store.remove(it) }
            store.add(ScheduleEntry("probe-1", "Toggle probe task", "probe prompt", ScheduleType.DAILY, 9, 0))
            checkNotNull(vm.g.schedules.get("probe-1")) { "probe entry missing from the schedules store" }
            // The header proves the debugStart navigation landed on Schedules.
            waitForNode(hasText("Schedules"))
            // Polled, not assertExists: the entry renders below the briefing
            // card and the first composition may not have it yet.
            waitForNode(hasText("Toggle probe task"))
            assertEquals(true, vm.g.schedules.get("probe-1")?.enabled)
            // No Material Switch anywhere on this screen: one always exposes
            // ToggleableState in semantics; AxionSwitch never does.
            val nodes = compose.onAllNodes(SemanticsMatcher("any node") { true }).fetchSemanticsNodes()
            assertTrue("a node still exposes ToggleableState — a Material Switch remains on the Schedules screen",
                nodes.none { it.config.getOrNull(SemanticsProperties.ToggleableState) != null })
            // The entry's on/off control is the right-edge clickable in ITS OWN
            // card. The Daily-briefing card above it has an identical right-edge
            // AxionSwitch, and it comes first — so onFirst() over the whole
            // screen taps the briefing switch, which with no briefing scheduled
            // just creates one, and the probe never flips. Tree-shape scoping
            // cannot fix this: hasParent(hasAnyDescendant(title)) was tried on
            // the merged tree AND on the unmerged tree (useUnmergedTree=true)
            // and both still tapped the briefing switch. The reason is that
            // the card Rows/Columns carry no semantics of their own, so in
            // EITHER tree a switch's nearest semantics parent is the shared
            // LazyColumn, whose descendants include BOTH cards' titles — the
            // parent matcher therefore matches both switches and onFirst()
            // picks the briefing one. So target by BOUNDS instead, independent
            // of tree shape: among the right-edge clickables (centre-x past
            // 80% of the width — the probe switch's centre is ~357dp of 411dp;
            // the Delete button's centre is ~300dp and the second-row
            // "Needs network"/"Exact time" switches sit near ~160dp/~250dp,
            // so none of them qualify), the probe's switch is the FIRST one
            // BELOW the probe title — the briefing switch is above it, and
            // nothing else right-edge sits below it on this screen. The
            // probe switch's centre-y is below the title's centre-y because
            // its Row is vertically centred on a five-line column while the
            // title is the column's first line.
            val rootWidth = compose.onRoot().fetchSemanticsNode().boundsInRoot.width
            val titleY = compose.onNodeWithText("Toggle probe task").fetchSemanticsNode().boundsInRoot.center.y
            val rightEdge = compose.onAllNodes(
                hasClickAction() and
                SemanticsMatcher("right-edge clickable") { it.boundsInRoot.center.x > 0.8f * rootWidth },
                useUnmergedTree = true
            )
            val edgeNodes = rightEdge.fetchSemanticsNodes()
            val idx = edgeNodes.indexOfFirst { it.boundsInRoot.center.y > titleY }
            check(idx >= 0) { "no right-edge clickable below the probe title (right-edge nodes: ${edgeNodes.map { it.boundsInRoot }})" }
            rightEdge[idx].performClick()
            compose.mainClock.advanceTimeBy(500)
            assertEquals("tapping the gold toggle must switch the schedule off", false, vm.g.schedules.get("probe-1")?.enabled)
            assertNotNull("the toggle must flip the setting, not delete the schedule", vm.g.schedules.get("probe-1"))
        } finally {
            sc.close()
        }
    }
}
