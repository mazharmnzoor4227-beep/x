package com.mazharansari.x

import androidx.test.core.app.ApplicationProvider
import com.mazharansari.x.data.AppSettings
import com.mazharansari.x.data.SettingsStore
import com.mazharansari.x.ui.ChatViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 2026-11-20 Advanced fix: Settings → Advanced → "Paste as File" looked dead.
 * The dialog, the setting and the send-path check all existed, but the old
 * rule only fired over 2,000 chars ("Always") / 60,000 chars ("Always ask"),
 * so a normal paste behaved identically under every mode. These tests pin
 * the fixed behaviour at ordinary paste sizes (600 chars) through the real
 * [ChatViewModel.send] path (offline, so no model is ever called), plus the
 * persistence round-trip for every option restored to Advanced / Appearance.
 *
 * Every paste test below FAILS on the old code: with the old thresholds a
 * 600-char paste sent inline under all three modes (no pendingPaste, no
 * attachment), so the Always / Always-ask assertions fail there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = AxionApp::class)
class AdvancedSettingsTest {
    private val app get() = ApplicationProvider.getApplicationContext<AxionApp>()

    private fun setPaste(mode: String) { app.graph.settings.update { it.copy(pasteAsFile = mode) } }

    /** Offline ViewModel: send() takes the honest "No internet" branch, never a model. */
    private fun vm(): ChatViewModel = ChatViewModel(app).also { it.debugOnline = false }

    @Test fun alwaysAskPromptsForOrdinaryPaste() {
        setPaste("Always ask")
        val v = vm()
        v.send("x".repeat(600))
        assertEquals("x".repeat(600), v.pendingPaste)
        assertTrue("asking must not send anything yet", v.messages.isEmpty())
        assertFalse(v.running)
    }

    @Test fun alwaysConvertsOrdinaryPaste() {
        setPaste("Always")
        val v = vm()
        v.send("x".repeat(600))
        val u = v.messages.first()
        assertTrue("converted text must be the short pointer, not the paste", u.text.length < 200)
        assertTrue(u.text.startsWith("Read the attached text"))
        assertEquals(1, u.attachments.size)
        assertEquals(600L, File(u.attachments[0].path).length())
    }

    @Test fun neverSendsOrdinaryPasteInline() {
        setPaste("Never")
        val v = vm()
        v.send("x".repeat(600))
        val u = v.messages.first()
        assertEquals(600, u.text.length)
        assertTrue(u.attachments.isEmpty())
    }

    @Test fun shortMessageStaysInlineUnderEveryMode() {
        for (mode in listOf("Always", "Always ask", "Never")) {
            setPaste(mode)
            val v = vm()
            v.send("hello")
            assertNull("short chat must never ask, even on $mode", v.pendingPaste)
            assertEquals("hello", v.messages.first().text)
            assertTrue(v.messages.first().attachments.isEmpty())
        }
    }

    @Test fun dismissPasteKeepsTextUnsent() {
        setPaste("Always ask")
        val v = vm()
        v.send("x".repeat(600))
        assertNotNull(v.pendingPaste)
        v.dismissPaste()
        assertNull(v.pendingPaste)
        assertTrue(v.messages.isEmpty())
    }

    @Test fun resolvePasteAsFileSendsFile() {
        setPaste("Always ask")
        val v = vm()
        v.send("x".repeat(600))
        v.resolvePaste(true)
        assertNull(v.pendingPaste)
        val u = v.messages.first()
        assertEquals(1, u.attachments.size)
        assertEquals(600L, File(u.attachments[0].path).length())
    }

    @Test fun resolvePasteAsTextSendsText() {
        setPaste("Always ask")
        val v = vm()
        v.send("x".repeat(600))
        v.resolvePaste(false)
        assertNull(v.pendingPaste)
        val u = v.messages.first()
        assertEquals(600, u.text.length)
        assertTrue(u.attachments.isEmpty())
    }

    @Test fun restoredOptionsPersistAndReload() {
        app.graph.settings.update {
            it.copy(pasteAsFile = "Always ask", autoRead = true, reviewBeforeApply = false,
                allowLargeUngated = true, textScale = 1.15f)
        }
        // The store writes settings.json off the main thread — wait for the flush.
        val file = File(app.filesDir, "settings.json")
        val end = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < end) {
            if (file.exists() && runCatching { file.readText() }.getOrNull()?.contains("Always ask") == true) break
            Thread.sleep(50)
        }
        val reloaded = SettingsStore(app).state.value
        assertEquals("Always ask", reloaded.pasteAsFile)
        assertTrue(reloaded.autoRead)
        assertFalse(reloaded.reviewBeforeApply)
        assertTrue(reloaded.allowLargeUngated)
        assertEquals(1.15f, reloaded.textScale)
        // Leave the shared store as found, so other test classes see defaults.
        app.graph.settings.update {
            it.copy(pasteAsFile = "Always", autoRead = false, reviewBeforeApply = true,
                allowLargeUngated = false, textScale = 1f)
        }
    }

    @Test fun defaultPasteModeIsAlways() {
        assertEquals("Always", AppSettings().pasteAsFile)
    }
}
