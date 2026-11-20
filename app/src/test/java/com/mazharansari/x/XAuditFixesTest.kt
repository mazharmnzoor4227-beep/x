package com.mazharansari.x

import com.mazharansari.x.local.LocalModel
import com.mazharansari.x.ui.chat.bashCollapsedLines
import com.mazharansari.x.ui.chat.botThreadSubtitle
import com.mazharansari.x.ui.workspace.COMPUTER_NO_PROJECTS
import com.mazharansari.x.ui.workspace.FEED_EMPTY_TITLE
import com.mazharansari.x.voice.PiperPack
import org.junit.Assert.*
import org.junit.Test

/**
 * Function audit + fix round, 2026-11-20 (docs/X-AUDIT-2026-11-20.md):
 * Qwen3 wiring (hash-gated), Piper pack filenames, and the §H partials.
 * Pure JVM — no network, no downloads, no device.
 */
class XAuditFixesTest {

    // ── Qwen3 (local/LocalModels.kt) ──────────────────────────────────────────

    @Test fun qwen3EntryIsHonestInEitherState() {
        val q = LocalModel.QWEN3_1_7B
        if (q.url.isBlank()) {
            // Not verified yet: must stay undownloadable, with an honest blurb.
            assertEquals(0L, q.bytes)
            assertEquals("", q.sha256)
            assertTrue(q.blurb.contains("not confirmed", ignoreCase = true) || q.blurb.contains("being checked", ignoreCase = true))
        } else {
            // Wired only with the verified bartowski BASE file (2026-11-20).
            assertEquals("https://huggingface.co/bartowski/Qwen_Qwen3-1.7B-GGUF/resolve/main/Qwen_Qwen3-1.7B-Q4_K_M.gguf", q.url)
            assertEquals(1_282_439_584L, q.bytes)
            assertTrue("sha256 must be 64 lowercase hex", q.sha256.matches(Regex("[0-9a-f]{64}")))
            assertTrue("blurb must disclose the base-model status", q.blurb.contains("base model", ignoreCase = true))
            assertTrue("blurb must say it is NOT instruction-tuned", q.blurb.contains("not instruction-tuned", ignoreCase = true))
        }
    }

    @Test fun lfm3AndCoderEntriesUntouched() {
        assertEquals(1_099_627_808L, LocalModel.LFM3_1_7B.bytes)
        assertEquals("3793e1e6e2266919d6e015287221b41100175a98eddaba92e111aed3b05c93c4", LocalModel.LFM3_1_7B.sha256)
        assertEquals(1_117_320_768L, LocalModel.QWEN_CODER_1_5B.bytes)
        assertEquals("cc324af070c2ecbfd324a30884d2f951a7ff756aba85cb811a6ec436933bb046", LocalModel.QWEN_CODER_1_5B.sha256)
        // Was 3 before the tiered catalog (2026-11-20): the original three plus
        // six newly verified tier models (0.5B, 1.5B instruct, 2x 3B, 7B, 8B).
        assertEquals(9, LocalModel.entries.size)
    }

    // ── Piper packs (voice/PiperVoice.kt) ─────────────────────────────────────

    @Test fun aegisFilenamesAreTheRealHfFiles() {
        val a = PiperPack.AEGIS
        assertEquals("ur_PK-aegis_female-medium.onnx", a.onnxName)
        assertEquals("ur_PK-aegis_female-medium.onnx.json", a.jsonName)
        assertEquals("https://huggingface.co/mahwizzzz/piper-voice-ur-aegis-female/resolve/main/ur_PK-aegis_female-medium.onnx", a.onnxUrl())
        assertEquals("https://huggingface.co/mahwizzzz/piper-voice-ur-aegis-female/resolve/main/ur_PK-aegis_female-medium.onnx.json", a.jsonUrl())
        assertEquals(63_515_589L, a.onnxBytes)
    }

    @Test fun fasihFilenamesCarryTheModelInfix() {
        val f = PiperPack.FASIH
        assertEquals("ur_PK-fasih-medium-model.onnx", f.onnxName)
        assertEquals("ur_PK-fasih-medium-model.onnx.json", f.jsonName)
        assertEquals("https://huggingface.co/IhorShevchuk/piper-voice-ur-fasih/resolve/main/ur_PK-fasih-medium-model.onnx", f.onnxUrl())
        assertEquals("https://huggingface.co/IhorShevchuk/piper-voice-ur-fasih/resolve/main/ur_PK-fasih-medium-model.onnx.json", f.jsonUrl())
        assertEquals(0L, f.onnxBytes) // size never verified — must stay 0, never guessed
    }

    @Test fun legacyConvertedNamesStillKnownForImport() {
        // The sherpa-converted copies carry the OLD names; import must still match them.
        assertEquals("ur_PK-aegis-medium.onnx", PiperPack.AEGIS.legacyOnnxName)
        assertEquals("ur_PK-aegis-medium.onnx.json", PiperPack.AEGIS.legacyJsonName)
        assertEquals("ur_PK-fasih-medium.onnx", PiperPack.FASIH.legacyOnnxName)
        assertEquals("ur_PK-fasih-medium.onnx.json", PiperPack.FASIH.legacyJsonName)
        assertNotEquals(PiperPack.AEGIS.onnxName, PiperPack.AEGIS.legacyOnnxName)
        assertNotEquals(PiperPack.FASIH.onnxName, PiperPack.FASIH.legacyOnnxName)
    }

    @Test fun aegisStaysTheDefaultVitsVoice() {
        assertEquals(PiperPack.AEGIS, PiperPack.of(null))
        assertTrue(PiperPack.isVitsPack(PiperPack.AEGIS.id))
        assertTrue(PiperPack.isVitsPack(PiperPack.FASIH.id))
        assertFalse(PiperPack.isVitsPack(PiperPack.KOKORO_HINDI.id))
    }

    @Test fun kokoroHasNoDownloadWired() {
        // Kokoro is a different model family with no verified tarball URL —
        // it must stay import/phone-voice only, never a faked download.
        assertNull(PiperPack.KOKORO_HINDI.downloadBase)
        assertNull(PiperPack.KOKORO_HINDI.onnxUrl())
        assertNull(PiperPack.KOKORO_HINDI.jsonUrl())
    }

    // ── §H partials ──────────────────────────────────────────────────────────

    @Test fun bashCollapsedTailShowsLastThreeLines() {
        val out = (1..10).joinToString("\n") { "line $it" }
        assertEquals(listOf("line 8", "line 9", "line 10"), bashCollapsedLines("make build", out))
    }

    @Test fun bashCollapsedTailWithNoOutputShowsCommand() {
        assertEquals(listOf("ls -la"), bashCollapsedLines("ls -la", ""))
        assertEquals(listOf("echo hi"), bashCollapsedLines("echo hi", "   \n"))
    }

    @Test fun bashCollapsedTailKeepsAtMostThreeLines() {
        val lines = bashCollapsedLines("a\nb\nc\nd", "e\nf")
        assertTrue(lines.size <= 3)
        assertEquals(listOf("d", "e", "f"), lines)
    }

    @Test fun botSubtitleNewThread() {
        assertEquals("Bot · Gemini · New thread", botThreadSubtitle("Gemini", 0, 0, nowMs = 1_000_000L))
        assertEquals("Bot · Auto · New thread", botThreadSubtitle("", 0, 0, nowMs = 1_000_000L))
    }

    @Test fun botSubtitleThreadAge() {
        val now = 1_764_000_000_000L
        assertEquals("Bot · Groq · started just now", botThreadSubtitle("Groq", 2, now - 30_000, now))
        assertEquals("Bot · Groq · started 2h ago", botThreadSubtitle("Groq", 2, now - 2 * 3_600_000, now))
        assertEquals("Bot · Groq · started 3d ago", botThreadSubtitle("Groq", 2, now - 3 * 86_400_000, now))
        // Messages exist but no timestamp: plain subtitle, no invented age.
        assertEquals("Bot · Groq", botThreadSubtitle("Groq", 2, 0, now))
    }

    @Test fun feedEmptyStateWording() {
        assertEquals("Nothing needs you.", FEED_EMPTY_TITLE)
    }

    @Test fun computerEmptyStateWording() {
        assertEquals("No projects yet — ask X to build one in chat", COMPUTER_NO_PROJECTS)
    }
}
