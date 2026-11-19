package com.mazharansari.x.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The sherpa-onnx Piper engine (redesign round 2, Addendum F).
 *
 * Engine: sherpa-onnx v1.10.42 (JitPack `com.github.k2-fsa:sherpa-onnx`,
 * Apache-2.0) — the same API shape as sherpa-onnx's own Android TTS
 * example (OfflineTts + getOfflineTtsConfig + generateWithCallback).
 * Registered once at app start (AxionApp) as [PiperVoiceEngines.engine].
 *
 * What it needs before it can speak a pack (all checked in [ttsFor], false
 * otherwise — the player then falls back to the phone voice with an honest
 * notice, never silence and never a fake Piper voice):
 *  1. the pack's .onnx + .json imported (Settings → Voice & Language),
 *     and the .onnx must be the sherpa-onnx-converted file (Piper metadata:
 *     model_type=vits, comment=piper, has_espeak=1 — converted copies at
 *     ~/workspace/piper-voices/converted/; raw Piper files are refused);
 *  2. tokens.txt next to the .onnx (generated from the .json by
 *     [PiperVoiceStore.ensureTokens] in the official sherpa-onnx format);
 *  3. espeak-ng-data downloaded once ([PiperVoiceStore.downloadEspeakData]).
 *
 * Only the VITS Piper packs (Aegis, Fasih) run here — Kokoro is a different
 * model family and needs its own sherpa-onnx config, so [speak] refuses it
 * and the player falls back. Pace (VoicePrefs.speed) does not apply to
 * Piper: the pack speaks at its natural rate (speed 1.0).
 *
 * Threading: generation is a blocking JNI call, so [speak] runs on
 * Dispatchers.IO. A [Mutex] serialises generations (OfflineTts is not
 * thread-safe); [stop] sets the flag the audio callback checks, so an
 * in-flight generation aborts at the next chunk instead of playing on.
 * The loaded model is cached per pack (model load is ~1 s); the native
 * object is never explicitly released — switching packs drops the reference.
 * CI is the compile proof for the sherpa-onnx call shapes used here.
 */
object SherpaPiperEngine : PiperVoiceEngine {

    private val stopped = AtomicBoolean(false)
    private val genMutex = Mutex()
    private val lock = Any()
    private var cachedPackId: String? = null
    private var cachedTts: OfflineTts? = null

    override fun stop() {
        stopped.set(true)
    }

    private fun ttsFor(store: PiperVoiceStore, pack: PiperPack): OfflineTts? {
        synchronized(lock) {
            if (cachedPackId == pack.id && cachedTts != null) return cachedTts
            cachedTts = null
            cachedPackId = null
            if (!store.status(pack).downloaded) return null
            if (!store.ensureTokens(pack)) return null
            if (!store.espeakReady()) return null
            val config = getOfflineTtsConfig(
                modelDir = store.packDir(pack).absolutePath,
                modelName = pack.onnxName,
                lexicon = "",
                dataDir = store.espeakDir().absolutePath,
                dictDir = "",
                ruleFsts = "",
                ruleFars = "",
            )
            val tts = runCatching { OfflineTts(assetManager = null, config = config) }.getOrNull() ?: return null
            cachedPackId = pack.id
            cachedTts = tts
            return tts
        }
    }

    override suspend fun speak(ctx: Context, pack: PiperPack, text: String): Boolean = withContext(Dispatchers.IO) {
        val store = ReadAloudPlayer.piperStore() ?: return@withContext false
        if (!PiperPack.isVitsPack(pack.id)) return@withContext false
        if (text.isBlank()) return@withContext false
        val tts = ttsFor(store, pack) ?: return@withContext false
        genMutex.withLock {
            stopped.set(false)
            try {
                val sampleRate = tts.sampleRate()
                val bufLength = AudioTrack.getMinBufferSize(
                    sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT,
                )
                val attr = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
                val format = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setSampleRate(sampleRate)
                    .build()
                val track = AudioTrack(
                    attr, format, bufLength, AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE,
                )
                try {
                    track.play()
                    val audio = tts.generateWithCallback(text = text, sid = 0, speed = 1.0f) { samples ->
                        if (stopped.get()) 0 else {
                            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                            1
                        }
                    }
                    audio.samples.isNotEmpty()
                } finally {
                    runCatching { track.stop() }
                    runCatching { track.release() }
                }
            } catch (e: Exception) {
                false
            }
        }
    }
}
