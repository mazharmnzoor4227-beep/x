package com.mazharansari.x

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.mazharansari.x.ui.ChatViewModel
import com.mazharansari.x.ui.Route
import com.mazharansari.x.voice.PickerSection
import com.mazharansari.x.voice.PiperPack
import com.mazharansari.x.voice.ReadAloudPlayer
import com.mazharansari.x.voice.VoiceCatalog
import com.mazharansari.x.voice.VoicePicker
import com.mazharansari.x.voice.VoicePreview
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import org.junit.Assert.*

/**
 * Voice & Language picker redesign (2026-11-20): professional cards,
 * tap-to-preview in the tapped voice, no filler entries.
 *
 * Fail-on-old-code: [catalogHasNoFillerEntries] fails on the old catalog
 * (Rounded's tag was literally "Default voice", the other Kodex tags were
 * "Phone voice"), and the picker/preview tests reference the new
 * [VoicePicker]/[VoicePreview] model the old code has no equivalent of.
 * The UI tests drive the real screen (V03ScreensTest pattern: launch
 * MainActivity, navigate with vm.debugStart) and assert the tap writes
 * the tapped voice into voicePref and starts a preview under that voice's
 * own id — never the old default.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = AxionApp::class)
class VoicePickerPreviewTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<AxionApp>()
    private var scenario: ActivityScenario<MainActivity>? = null

    @After fun tearDown() {
        ReadAloudPlayer.stop()
        scenario?.close()
        scenario = null
    }

    // (1) The catalog contains no filler entries.
    @Test fun catalogHasNoFillerEntries() {
        val banned = listOf("Default voice", "Phone voice", "System phone voice")
        VoiceCatalog.all.forEach { v ->
            assertFalse("${v.id} tag '${v.tag}' is a filler entry", v.tag in banned)
            assertFalse("${v.id} name is a filler entry", v.name in banned)
        }
        assertFalse("no tag may call a voice the default", VoiceCatalog.all.any { it.tag.contains("Default", ignoreCase = true) })
        // The engine fallback id is unchanged (the fallback chain + V03Test depend on it) — it is NOT the picker default.
        assertEquals("kodex:rounded", VoiceCatalog.DEFAULT_ID)
        assertEquals("piper-aegis", VoicePicker.PICKER_DEFAULT_ID)
    }

    @Test fun pickerHasNoFillerOrPlaceholderEntries() {
        val ids = VoicePicker.voices.map { it.id }
        assertEquals("no voice may appear twice in the picker", ids.size, ids.toSet().size)
        assertFalse("the removed 'system' filler row must not be in the picker", "system" in ids)
        VoicePicker.voices.forEach { v ->
            assertTrue("${v.id} must have a name", v.name.isNotBlank())
            assertTrue("${v.id} must have a language", v.language.isNotBlank())
            assertTrue("${v.id} must have a description", v.description.isNotBlank())
            assertTrue("${v.id} must have a preview sample", v.sampleText.isNotBlank())
            assertFalse("${v.id} description is filler: '${v.description}'",
                listOf("default voice", "phone voice", "system phone voice", "placeholder", "filler")
                    .any { v.description.lowercase().contains(it) })
        }
    }

    // (2) Aegis female is first / default and Kokoro Hindi is present as the alternative.
    @Test fun aegisFirst_kokoroIsTheAlternative() {
        assertEquals("piper-aegis", VoicePicker.PICKER_DEFAULT_ID)
        assertEquals("piper-aegis", VoicePicker.voices.first().id)
        assertEquals("piper-fasih", VoicePicker.voices[1].id)
        assertEquals("kokoro-hindi", VoicePicker.voices[2].id)
        assertEquals(PickerSection.RECOMMENDED, VoicePicker.voices[2].section)
        assertEquals("Aegis · Urdu (Pakistan)", VoicePicker.displayNameFor(null))
        assertEquals("Aegis · Urdu (Pakistan)", VoicePicker.displayNameFor("piper-aegis"))
        assertEquals("Kokoro Hindi · Hindi", VoicePicker.displayNameFor("kokoro-hindi"))
    }

    @Test fun amyAddedWithVerifiedSource() {
        val amy = PiperPack.AMY
        assertEquals("piper-amy", amy.id)
        assertEquals("en_US-amy-medium.onnx", amy.onnxName)
        assertEquals("en_US-amy-medium.onnx.json", amy.jsonName)
        assertEquals(63_221_984L, amy.onnxBytes)
        assertEquals(
            "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/amy/medium/en_US-amy-medium.onnx",
            amy.onnxUrl())
        assertTrue(PiperPack.isPiperPick("piper-amy"))
        assertTrue(PiperPack.isVitsPack("piper-amy"))
        assertEquals("piper-amy", VoicePicker.byId("piper-amy")?.id)
        assertEquals(PickerSection.RECOMMENDED, VoicePicker.byId("piper-amy")?.section)
    }

    @Test fun kokoroPresentButDownloadUnwired() {
        assertEquals("kokoro-hindi", VoicePicker.byId("kokoro-hindi")?.id)
        assertNull("Kokoro has no verified download URL — never guessed", PiperPack.KOKORO_HINDI.downloadBase)
        assertNull(PiperPack.KOKORO_HINDI.onnxUrl())
        assertNull(PiperPack.KOKORO_HINDI.jsonUrl())
        assertFalse("Kokoro is not a VITS pack", PiperPack.isVitsPack(PiperPack.KOKORO_HINDI.id))
    }

    @Test fun migrationRule_systemAndUnknownBecomeAegis_knownPicksKept() {
        assertEquals("piper-aegis", VoicePicker.migrateVoicePref(null))
        assertEquals("piper-aegis", VoicePicker.migrateVoicePref(""))
        assertEquals("piper-aegis", VoicePicker.migrateVoicePref("system"))
        assertEquals("piper-aegis", VoicePicker.migrateVoicePref("nonsense-old-value"))
        assertEquals("piper-fasih", VoicePicker.migrateVoicePref("piper-fasih"))
        assertEquals("piper-amy", VoicePicker.migrateVoicePref("piper-amy"))
        assertEquals("edge:ava", VoicePicker.migrateVoicePref("edge:ava"))
        assertEquals("kokoro-hindi", VoicePicker.migrateVoicePref("kokoro-hindi"))
    }

    // (3) Tapping a voice card selects it and starts a preview in THAT voice.
    @Test fun previewTargetsTheTappedVoice_notTheOldDefault() {
        VoicePicker.voices.forEach { v ->
            assertEquals("preview:${v.id}", VoicePreview.messageId(v.id))
            assertEquals(v.id, VoicePreview.voiceIdOf(VoicePreview.messageId(v.id)))
            assertTrue(VoicePreview.isPreviewId(VoicePreview.messageId(v.id)))
            assertEquals("preview request for ${v.id} must carry that voice's own sample",
                v.sampleText, VoicePreview.sampleTextFor(v.id))
        }
        // The samples are in the voice's own language — not the old English default for every voice.
        assertTrue(VoicePreview.sampleTextFor("piper-aegis").contains("Assalam-o-Alaikum"))
        assertTrue(VoicePreview.sampleTextFor("piper-fasih").contains("Assalam-o-Alaikum"))
        assertTrue("Kokoro Hindi sample must be Devanagari Hindi",
            VoicePreview.sampleTextFor("kokoro-hindi").any { it.code in 0x0900..0x097F })
        assertTrue("Uzma sample must be Urdu script",
            VoicePreview.sampleTextFor("edge:uzma").any { it.code in 0x0600..0x06FF })
        assertEquals(VoiceCatalog.PREVIEW_TEXT, VoicePreview.sampleTextFor("kodex:rounded"))
        assertFalse(VoicePreview.isPreviewId("some-read-aloud-id"))
        assertNull(VoicePreview.voiceIdOf("some-read-aloud-id"))
        assertNull(VoicePreview.voiceIdOf(null))
    }

    @Test fun everyPickerVoiceIsResolvableByThePlayer() {
        VoicePicker.voices.forEach { v ->
            if (v.isCatalog) {
                assertEquals("catalog voice ${v.id} must resolve to itself, not fall back to Rounded",
                    v.id, VoiceCatalog.byId(v.id).id)
                assertTrue(VoicePicker.isCatalogVoice(v.id))
            } else {
                assertTrue("piper voice ${v.id} must be a Piper pick", PiperPack.isPiperPick(v.id))
            }
        }
    }

    // (4) Preview state: playing indicator shows while playing and clears on stop/switch.
    @Test fun previewStateMachine_toggleAndSwitch() {
        assertEquals("piper-aegis", VoicePreview.toggle(null, "piper-aegis"))
        assertNull("tapping the playing card stops it", VoicePreview.toggle("piper-aegis", "piper-aegis"))
        assertEquals("tapping another card switches to it", "edge:ava", VoicePreview.toggle("piper-aegis", "edge:ava"))
        assertEquals("piper-fasih", VoicePreview.toggle("edge:ava", "piper-fasih"))
    }

    // ---- UI: the real screen, V03ScreensTest pattern ----

    private fun seedVoicePref(pref: String) {
        File(app.filesDir, "settings.json")
            .writeText("""{"voicePref":"$pref","theme":"dark","mode":"AUTO"}""")
    }

    private fun openLanguageScreen(): ChatViewModel {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var vm: ChatViewModel
        scenario!!.onActivity {
            vm = ViewModelProvider(it)[ChatViewModel::class.java]
            vm.debugStart = Route.Language
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Voice: Aegis").assertExists()
        return vm
    }

    @Test fun tappingAVoiceCard_selectsItAndStartsItsPreview() {
        seedVoicePref("piper-fasih")   // Aegis is NOT the pick yet
        val vm = openLanguageScreen()
        compose.onNodeWithContentDescription("Voice: Aegis").performClick()
        compose.waitForIdle()
        assertEquals("tapping the card must select that voice", "piper-aegis", vm.g.settings.state.value.voicePref)
        assertEquals("the preview must run under the tapped voice's id, not the old default",
            "preview:piper-aegis", ReadAloudPlayer.playingMessageId)
        compose.onNodeWithText("Playing").assertExists()
    }

    @Test fun tappingThePlayingCardStopsIt_tappingAnotherSwitches() {
        seedVoicePref("piper-aegis")
        val vm = openLanguageScreen()
        compose.onNodeWithContentDescription("Voice: Aegis").performClick()
        compose.waitForIdle()
        assertEquals("preview:piper-aegis", ReadAloudPlayer.playingMessageId)
        compose.onNodeWithText("Playing").assertExists()
        // Tap again: the preview stops and the indicator clears.
        compose.onNodeWithContentDescription("Voice: Aegis").performClick()
        compose.waitForIdle()
        assertNull("tapping the playing card must stop the preview", ReadAloudPlayer.playingMessageId)
        compose.onNodeWithText("Playing").assertDoesNotExist()
        // Tap another card: the preview switches to that voice, and the selection follows.
        compose.onNodeWithContentDescription("Voice: Fasih").performClick()
        compose.waitForIdle()
        assertEquals("preview:piper-fasih", ReadAloudPlayer.playingMessageId)
        assertEquals("piper-fasih", vm.g.settings.state.value.voicePref)
        compose.onNodeWithText("Playing").assertExists()
    }
}
