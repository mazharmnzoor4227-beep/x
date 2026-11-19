package com.mazharansari.x.voice

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Piper natural voices (2026-11-19, researched in
 * ~/workspace/custom-skills/local-masala-shop/SKILL.md).
 *
 * The user's standing first pick for read-aloud / voice input / voice calls
 * is Piper Urdu **Aegis female** (ur_PK, ~61 MB ONNX, MIT release, distilled
 * from OmniVoice); Fasih male Urdu is the second pick; Kokoro-82M Hindi is
 * the alternative (author grades its Hindi voices C — good, not perfect).
 *
 * HONEST STATE (2026-11-19): the voice files exist on disk at
 * ~/workspace/piper-voices/ (aegis.onnx 61 MB + aegis.onnx.json,
 * fasih.onnx 61 MB + fasih.onnx.json) but NO on-device Piper inference
 * engine is bundled in this app yet — that needs the sherpa-onnx Android
 * AAR (Piper engine is GPL-3.0; the Aegis voice release is MIT), which is
 * NOT in app/build.gradle.kts and was not added in this session. Until it
 * is, this file wires everything around the engine so the engine is a
 * drop-in later:
 *  - the preference (AppSettings.voicePref, Settings → Voice & Language),
 *  - the pack store (import the .onnx + .json pair into the app's private
 *    files, status shown honestly), and
 *  - the offline fallback in ReadAloudPlayer: the phone's built-in
 *    Urdu/Hindi voice reads instead, with one notice saying the Piper
 *    engine is not installed yet. Nothing is ever presented as Piper
 *    audio when it is not.
 */
enum class PiperPack(
    val id: String,
    val displayName: String,
    val onnxName: String,
    val jsonName: String,
    /** Locale the phone's built-in voice uses as the offline stand-in. */
    val phoneLocale: String,
) {
    AEGIS("piper-aegis", "Piper Urdu Aegis (female)", "ur_PK-aegis-medium.onnx", "ur_PK-aegis-medium.onnx.json", "ur-PK"),
    FASIH("piper-fasih", "Piper Urdu Fasih (male)", "ur_PK-fasih-medium.onnx", "ur_PK-fasih-medium.onnx.json", "ur-PK"),
    KOKORO_HINDI("kokoro-hindi", "Kokoro Hindi (alternative)", "kokoro-hi-IN.onnx", "kokoro-hi-IN.onnx.json", "hi-IN");

    companion object {
        fun of(id: String?): PiperPack = entries.firstOrNull { it.id == id } ?: AEGIS
        fun isPiperPick(id: String?): Boolean = id == AEGIS.id || id == FASIH.id || id == KOKORO_HINDI.id
    }
}

/** One Piper voice pack's on-disk state. [downloaded] needs BOTH files. */
data class PiperPackStatus(val pack: PiperPack, val downloaded: Boolean, val bytes: Long)

/** The voice-pack store: <filesDir>/piper-voices/<onnx + json>. */
class PiperVoiceStore(ctx: Context) {
    private val dir = File(ctx.filesDir, "piper-voices").apply { mkdirs() }

    fun status(pack: PiperPack): PiperPackStatus {
        val onnx = File(dir, pack.onnxName)
        val json = File(dir, pack.jsonName)
        val ok = onnx.isFile && onnx.length() > 1_000_000 && json.isFile
        return PiperPackStatus(pack, ok, (if (onnx.isFile) onnx.length() else 0L) + (if (json.isFile) json.length() else 0L))
    }

    fun statusFor(prefId: String?): PiperPackStatus = status(PiperPack.of(prefId))

    fun onnxFile(pack: PiperPack): File = File(dir, pack.onnxName)
    fun jsonFile(pack: PiperPack): File = File(dir, pack.jsonName)

    /**
     * Imports one file of a pack from a user-picked document (the .onnx and
     * .json are picked one at a time from Files / Downloads / a USB copy of
     * ~/workspace/piper-voices/). Returns the pack file it wrote, or null
     * when the name does not match either file of any pack.
     */
    fun importFile(ctx: Context, uri: Uri, displayName: String): File? {
        val pack = PiperPack.entries.firstOrNull { it.onnxName == displayName || it.jsonName == displayName } ?: return null
        val dest = File(dir, displayName)
        ctx.contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { out -> input.copyTo(out) } } ?: return null
        return dest.takeIf { it.isFile && it.length() > 0 }
    }

    fun remove(pack: PiperPack) {
        onnxFile(pack).delete()
        jsonFile(pack).delete()
    }
}

/**
 * The on-device Piper inference engine. Implemented by a future
 * sherpa-onnx binding; until one registers itself here, [engine] stays
 * null and ReadAloudPlayer uses the phone-voice fallback (see PiperVoice.kt
 * header). Returning false means "did not speak" — the caller falls back.
 */
interface PiperVoiceEngine {
    suspend fun speak(ctx: Context, pack: PiperPack, text: String): Boolean
}

object PiperVoiceEngines {
    /** Null until a real engine is bundled and registered. Never faked. */
    var engine: PiperVoiceEngine? = null
}
