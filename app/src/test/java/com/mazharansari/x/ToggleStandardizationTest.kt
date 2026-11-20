package com.mazharansari.x

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
    // createComposeRule stays here: the two tests below call setContent,
    // which only the activity-backed rule exposes. The Schedules Activity
    // test lives in its own class (SchedulesGoldSwitchUiTest, V06Nav
    // pattern with createEmptyComposeRule + ActivityScenario) — the empty
    // rule has no setContent, and the activity rule binds its queries to
    // its own empty ComponentActivity, so the two cannot share a class.
    @get:Rule val compose = createComposeRule()

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

    // The Schedules Activity test moved to SchedulesGoldSwitchUiTest (own
    // class: it needs createEmptyComposeRule + ActivityScenario, and this
    // class needs createComposeRule's setContent — the two rules cannot
    // share a class).
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
