package com.mazharansari.x

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
import com.mazharansari.x.ui.components.AxionSwitch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Toggle standardisation (user order 2026-11-20): every on/off toggle in the
 * app must be the gold AxionSwitch from the Settings "Status page" row
 * (48x28dp, 8dp track corners, gold accent track when on, dark thumb when
 * on) — no raw Material Switch anywhere. The source scan fails on the old
 * code (Switch( in ConnectorsScreen/SchedulesScreen) and passes on the new
 * code; the Schedules screen test fails on the old code too, because a
 * Material Switch always exposes ToggleableState in semantics and
 * AxionSwitch (a plain clickable box) never does.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-280dpi", application = AxionApp::class)
class ToggleStandardizationTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<AxionApp>()

    @Test fun axionSwitchFlipsWhenTappedAndIsNotAMaterialSwitch() {
        var flipped: Boolean? = null
        compose.setContent { AxionSwitch(false) { flipped = it } }
        compose.onNode(hasClickAction()).assertExists()
        val nodes = compose.onAllNodes(SemanticsMatcher("any node") { true }).fetchSemanticsNodes()
        assertTrue("AxionSwitch must not expose ToggleableState (that is a Material Switch)",
            nodes.none { it.config.getOrNull(SemanticsProperties.ToggleableState) != null })
        compose.onNode(hasClickAction()).performClick()
        compose.mainClock.advanceTimeBy(300)
        assertEquals(true, flipped)
    }

    @Test fun axionSwitchCheckedFlipsOffWhenTapped() {
        var flipped: Boolean? = null
        compose.setContent { AxionSwitch(true) { flipped = it } }
        compose.onNode(hasClickAction()).performClick()
        compose.mainClock.advanceTimeBy(300)
        assertEquals(false, flipped)
    }

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

    @Test fun schedulesScreenUsesGoldSwitchAndItsToggleStillFlips() {
        val store = app.graph.schedules
        store.entries.value.map { it.id }.forEach { store.remove(it) }
        store.add(ScheduleEntry("probe-1", "Toggle probe task", "probe prompt", ScheduleType.DAILY, 9, 0))
        val (sc, vm) = launch(Route.Schedules)
        try {
            compose.onNodeWithText("Toggle probe task").assertExists()
            assertEquals(true, vm.g.schedules.get("probe-1")?.enabled)
            // No Material Switch anywhere on this screen: one always exposes
            // ToggleableState in semantics; AxionSwitch never does.
            val nodes = compose.onAllNodes(SemanticsMatcher("any node") { true }).fetchSemanticsNodes()
            assertTrue("a node still exposes ToggleableState — a Material Switch remains on the Schedules screen",
                nodes.none { it.config.getOrNull(SemanticsProperties.ToggleableState) != null })
            // The entry's on/off control is the right-edge clickable in its
            // card (the Delete button and the second-row switches sit left of
            // 80% of the screen width, so they cannot match).
            val rootWidth = compose.onRoot().fetchSemanticsNode().boundsInRoot.width
            compose.onAllNodes(hasClickAction() and SemanticsMatcher("right-edge toggle") { it.boundsInRoot.center.x > 0.8f * rootWidth })
                .onFirst().performClick()
            compose.mainClock.advanceTimeBy(500)
            assertEquals("tapping the gold toggle must switch the schedule off", false, vm.g.schedules.get("probe-1")?.enabled)
            assertNotNull("the toggle must flip the setting, not delete the schedule", vm.g.schedules.get("probe-1"))
        } finally {
            sc.close()
        }
    }
}

/**
 * Pure-JVM source scan (no Android needed): the main tree must contain no
 * raw Material Switch and AxionSwitch must be the only switch component.
 * The two Checkbox call sites in ConnectorsScreen are deliberately NOT
 * flagged: they are multi-pick lists ("Untick a tool…", skill-hub picks),
 * i.e. selection marks, not on/off toggles. The composer Research / Long
 * task / Turbo chips are likewise a different chip family, not switches.
 */
class ToggleSourceScanTest {

    private fun mainKtFiles(): List<File> {
        var dir = File(System.getProperty("user.dir"))
        repeat(5) {
            listOf(File(dir, "src/main/java"), File(dir, "app/src/main/java"))
                .firstOrNull { it.isDirectory }?.let { root ->
                    return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
                }
            dir = dir.parentFile ?: return emptyList()
        }
        error("main source tree not found from user.dir=${System.getProperty("user.dir")}")
    }

    // Block comments and full-line comments are stripped so a commented-out
    // Switch cannot fail the scan and a live one cannot hide in a comment.
    // (No string literal in the main tree contains "Switch(", verified by grep.)
    private fun codeOnly(text: String): String {
        val noBlock = text.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        return noBlock.lineSequence()
            .filter { it.trim().startsWith("//").not() && it.trim().startsWith("*").not() }
            .joinToString("\n")
    }

    @Test fun noRawMaterialSwitchInMainTree() {
        val switchCall = Regex("\\bSwitch\\s*\\(")
        val violations = mainKtFiles().mapNotNull { f ->
            val code = codeOnly(f.readText())
            val hits = mutableListOf<String>()
            if (switchCall.containsMatchIn(code)) hits += "raw Switch( call"
            if ("SwitchDefaults" in code) hits += "SwitchDefaults"
            if ("import androidx.compose.material3.Switch" in code) hits += "material3 Switch import"
            if (hits.isNotEmpty()) "${f.name}: ${hits.joinToString(", ")}" else null
        }
        assertTrue("raw Material Switch remains in the main tree: ${violations.joinToString("; ")}", violations.isEmpty())
    }

    @Test fun axionSwitchIsTheOnlySwitchComponent() {
        val files = mainKtFiles()
        val common = files.first { it.name == "Common.kt" }.readText()
        assertTrue("AxionSwitch must be defined in Common.kt", "fun AxionSwitch" in common)
        // The gold spec from the Settings screenshot: 48x28dp, 8dp track
        // corners, gold accent track when on, dark on-accent thumb when on.
        assertTrue("AxionSwitch must be 48dp wide", "48.dp" in common)
        assertTrue("AxionSwitch must be 28dp tall", "28.dp" in common)
        assertTrue("AxionSwitch track must have 8dp corners", "RoundedCornerShape(8.dp)" in common)
        assertTrue("AxionSwitch track must use the gold accent when on", "p.accent" in common)
        assertTrue("AxionSwitch thumb must use onAccent when on", "p.onAccent" in common)
        val users = files.filter { "AxionSwitch(" in codeOnly(it.readText()) }
        assertTrue("AxionSwitch must be used by ToggleRow and the settings screens, found in: ${users.map { it.name }}", users.size >= 4)
    }

    @Test fun toggleRowRendersAxionSwitch() {
        val common = mainKtFiles().first { it.name == "Common.kt" }.readText()
        assertTrue("ToggleRow must render AxionSwitch, not a Material Switch",
            "AxionSwitch(checked, onChange)" in common)
    }
}
