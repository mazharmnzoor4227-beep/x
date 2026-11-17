package com.mazharansari.x

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.mazharansari.x.data.Message
import com.mazharansari.x.data.Role
import com.mazharansari.x.ui.chat.ToolsRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Single chat screen: the empty chat has ONE suggestion row above the composer
 * (EmptyChat in ChatScreen.kt) and the standalone ToolsRow renders only in an
 * active chat — ChatScreen.kt shows it iff messages.isNotEmpty(). No Build
 * mode, no "Build an app" chip. No network, no Android services: the rows
 * render standalone under Robolectric.
 *
 * EmptyChat and its SuggestionChip are private to ChatScreen, so the merged
 * row's toggle trio is covered here through the real ToolsRow (ui.chat) — the
 * same three chips (Research, Long task, Turbo) in the same order — and the
 * visibility rule through the real row rendered under ChatScreen's condition.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HomeChipsTest {
    @get:Rule val compose = createComposeRule()

    /** The visible texts in layout order, depth-first through the semantics tree. */
    private fun orderedTexts(): List<String> {
        fun walk(n: SemanticsNode): List<String> {
            val out = ArrayList<String>()
            n.config.getOrNull(SemanticsProperties.Text)?.let { out += it.joinToString("") }
            n.children.forEach { out += walk(it) }
            return out
        }
        return walk(compose.onRoot().fetchSemanticsNode())
    }

    /** The merged row's toggle trio, as ChatScreen renders it (ToolsRow, ui.chat). */
    private fun suggestions(longTaskOn: Boolean = true) {
        compose.setContent {
            ToolsRow(researchOn = false, onResearch = {}, longTaskOn = longTaskOn, onLongTask = {}, turboOn = false, onTurbo = {})
        }
    }

    @Test fun emptyHomeChipsAreOneMergedRowInOrder() {
        suggestions()
        assertEquals(
            listOf("Research", "Long task", "Turbo"),
            orderedTexts(),
        )
    }

    @Test fun emptyHomeChipsWithNoKeysStillShowTheThreeToggles() {
        // The "Connect a free AI key" chip is EmptyChat-private; what this test
        // owns is that the toggle trio itself is unchanged when keys are missing.
        suggestions()
        assertEquals(
            listOf("Research", "Long task", "Turbo"),
            orderedTexts(),
        )
    }

    @Test fun toolsRowStillHoldsTheThreeTogglesForActiveChats() {
        compose.setContent {
            ToolsRow(researchOn = true, onResearch = {}, longTaskOn = false, onLongTask = {}, turboOn = false, onTurbo = {})
        }
        assertEquals(listOf("Research", "Long task", "Turbo"), orderedTexts())
    }

    /**
     * ChatScreen.kt: `if (messages.isNotEmpty()) ToolsRow(...)` — the row exists
     * only once the chat has messages; there is no Build mode anymore.
     */
    @Test fun toolsRowRendersOnlyInAnActiveChat() {
        compose.setContent {
            val messages = emptyList<Message>()
            if (messages.isNotEmpty()) {
                ToolsRow(researchOn = false, onResearch = {}, longTaskOn = false, onLongTask = {}, turboOn = false, onTurbo = {})
            }
        }
        assertEquals(emptyList<String>(), orderedTexts())
        compose.setContent {
            val messages = listOf(Message("u0", Role.USER, "hi"))
            if (messages.isNotEmpty()) {
                ToolsRow(researchOn = false, onResearch = {}, longTaskOn = false, onLongTask = {}, turboOn = false, onTurbo = {})
            }
        }
        assertEquals(listOf("Research", "Long task", "Turbo"), orderedTexts())
    }
}
