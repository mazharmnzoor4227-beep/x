package com.mazharansari.x.voice

/**
 * The Voice & Language picker model (redesign 2026-11-20).
 *
 * The old page offered four plain rows ("Piper Urdu Aegis — female",
 * "Piper Urdu Fasih — male", "Kokoro Hindi — alternative",
 * "System phone voice") and the rest of the voices X can actually speak
 * (the Kodex phone voices, the Edge neural voices, the Gemini voices in
 * [VoiceCatalog]) were unreachable from Settings at all. The picker is now
 * one curated list over those same voices, in sections, with Aegis female
 * first and Kokoro Hindi as the alternative — the user's standing
 * preference. Tapping a card writes its [PickerVoice.id] straight into
 * AppSettings.voicePref (the same key as before, so a stored pick
 * survives the redesign) and, for a catalog voice, into
 * [VoicePrefs] too, so read-aloud and voice calls use the pick at once.
 *
 * VOICE SOURCING (verified 2026-11-20, nothing guessed):
 *  - Axion's voice tree (~/workspace/axion-roadmap/work/axion-app/.../voice/)
 *    has NO Piper code at all — no PiperVoice.kt, no Sherpa engine, no
 *    Aegis/Fasih mention. Its VoiceCatalog is identical to X's. So there
 *    was nothing to port from Axion: the good Piper Urdu packs (Aegis
 *    female, Fasih male) already live in X, with sherpa-converted copies
 *    on disk at ~/workspace/piper-voices/converted/.
 *  - Hugging Face API search for Piper Urdu voices returns exactly two
 *    repos: mahwizzzz/piper-voice-ur-aegis-female and
 *    IhorShevchuk/piper-voice-ur-fasih. No third Urdu Piper pack exists.
 *  - Hugging Face API search for Piper Hindi voices returns no verified
 *    VITS pack (only pronoobie/piper-voices-hindi — 22 downloads, 0 likes,
 *    GGUF-tagged, provenance unverified — NOT added).
 *  - One extra free offline voice IS verified and added: Piper English
 *    Amy (female, US), rhasspy/piper-voices,
 *    en/en_US/amy/medium/en_US-amy-medium.onnx, 63,221,984 bytes
 *    (+ .onnx.json, 6,289 bytes), verified via the Hugging Face API file
 *    tree 2026-11-20. See [PiperPack.AMY].
 *  - Kokoro-82M Hindi stays in the picker as the alternative, but its
 *    download is NOT wired (see [PiperPack.KOKORO_HINDI]): the sherpa-onnx
 *    tts-models tarballs (kokoro-multi-lang-v1_0.tar.bz2 /
 *    kokoro-int8-multi-lang-v1_0.tar.bz2) are confirmed to exist, but no
 *    verified size / SHA-256 / Hindi voice-sid mapping could be confirmed
 *    from this sandbox, so no URL is guessed and no broken download is
 *    shipped. Until it is wired, the Kokoro pick reads in the phone's
 *    built-in Hindi voice — the card says so plainly.
 */
enum class PickerSection(val title: String) {
    RECOMMENDED("Recommended — offline, on-device"),
    PHONE("Phone voices — on-device"),
    ONLINE("Online neural voices — needs internet"),
    GEMINI("Gemini voices — needs a Gemini key"),
}

/** One card in the Voice & Language picker. */
data class PickerVoice(
    /** The id written to AppSettings.voicePref on tap: a PiperPack id or a VoiceCatalog id. */
    val id: String,
    val name: String,
    val language: String,
    /** Short professional description shown under the language. Never a filler ("Default voice", "Phone voice"). */
    val description: String,
    val section: PickerSection,
    /** Short sample spoken by tap-to-preview, in this voice's language. */
    val sampleText: String,
    /** True when [id] is a [VoiceCatalog] id (also written to [VoicePrefs] on tap). */
    val isCatalog: Boolean = false,
    /** The Piper pack behind this card, if any (for the live download status line). */
    val piperPack: PiperPack? = null,
    /** Extra live status shown under the description (null when the description already says it). */
    val engineNote: String? = null,
)

object VoicePicker {
    /** The standing first pick — and the default for every fresh or migrated install. */
    const val PICKER_DEFAULT_ID = "piper-aegis"

    /** Roman Urdu greeting, female. Spoken, never shown as Urdu script (the user reads Roman Urdu). */
    const val SAMPLE_ROMAN_URDU_FEMALE = "Assalam-o-Alaikum! Main Aegis hoon, aapki X voice. Bataiye, aaj main aapki kya madad kar sakti hoon?"
    /** Roman Urdu greeting, male. */
    const val SAMPLE_ROMAN_URDU_MALE = "Assalam-o-Alaikum! Main Fasih hoon, aapki X voice. Bataiye, aaj main aapki kya madad kar sakta hoon?"
    /** Urdu-script greeting, female (Uzma / Urdu-script voices). */
    const val SAMPLE_URDU_SCRIPT_FEMALE = "السلام علیکم! میں آپ کی X آواز ہوں۔ بتائیے، آج میں آپ کی کیا مدد کر سکتی ہوں؟"
    /** Urdu-script greeting, male (Asad / Urdu-script voices). */
    const val SAMPLE_URDU_SCRIPT_MALE = "السلام علیکم! میں آپ کی X آواز ہوں۔ بتائیے، آج میں آپ کی کیا مدد کر سکتا ہوں؟"

    val voices: List<PickerVoice> = buildList {
        // Recommended first: Aegis female, Fasih male, Kokoro Hindi (alternative), Amy (offline English).
        add(PickerVoice(PiperPack.AEGIS.id, "Aegis", "Urdu (Pakistan)",
            "Female · offline Piper voice — the natural first pick",
            PickerSection.RECOMMENDED, SAMPLE_ROMAN_URDU_FEMALE, piperPack = PiperPack.AEGIS))
        add(PickerVoice(PiperPack.FASIH.id, "Fasih", "Urdu (Pakistan)",
            "Male · offline Piper voice",
            PickerSection.RECOMMENDED, SAMPLE_ROMAN_URDU_MALE, piperPack = PiperPack.FASIH))
        add(PickerVoice(PiperPack.KOKORO_HINDI.id, "Kokoro Hindi", "Hindi",
            "Alternative · Kokoro-82M Hindi — download not available yet",
            PickerSection.RECOMMENDED, VoiceCatalog.PREVIEW_URDU, piperPack = PiperPack.KOKORO_HINDI))
        add(PickerVoice(PiperPack.AMY.id, "Amy", "English (United States)",
            "Female · offline Piper voice",
            PickerSection.RECOMMENDED, VoiceCatalog.PREVIEW_TEXT, piperPack = PiperPack.AMY))
        // Phone voices: the Kodex on-device Google voices, professional descriptions only.
        VoiceCatalog.kodex.forEach { v ->
            add(PickerVoice(v.id, v.name, v.language, v.tag, PickerSection.PHONE,
                VoiceCatalog.PREVIEW_TEXT, isCatalog = true, engineNote = "Offline · on-device"))
        }
        // Online neural voices (Edge, free, no key — needs internet).
        VoiceCatalog.natural.forEach { v ->
            add(PickerVoice(v.id, v.name, v.language, "${v.tag} · needs internet", PickerSection.ONLINE,
                sampleTextForNatural(v), isCatalog = true))
        }
        // Gemini Live voices (need a Gemini key in Connectors).
        VoiceCatalog.gemini.forEach { v ->
            add(PickerVoice(v.id, v.name, v.language, "${v.tag} · needs a Gemini key", PickerSection.GEMINI,
                VoiceCatalog.PREVIEW_TEXT, isCatalog = true))
        }
    }

    private fun sampleTextForNatural(v: AxVoice): String = when (v.id) {
        "edge:swara" -> SAMPLE_ROMAN_URDU_FEMALE
        "edge:madhur" -> SAMPLE_ROMAN_URDU_MALE
        "edge:uzma" -> SAMPLE_URDU_SCRIPT_FEMALE
        "edge:asad" -> SAMPLE_URDU_SCRIPT_MALE
        else -> VoiceCatalog.PREVIEW_TEXT
    }

    fun sections(): List<PickerSection> = PickerSection.entries.toList()

    fun voicesIn(section: PickerSection): List<PickerVoice> = voices.filter { it.section == section }

    fun byId(id: String?): PickerVoice? = voices.firstOrNull { it.id == id }

    /** A catalog voice is one [VoiceCatalog] knows (its id resolves instead of falling back to Rounded). */
    fun isCatalogVoice(id: String?): Boolean = id != null && VoiceCatalog.all.any { it.id == id }

    /**
     * Migration rule for stored voicePref values (2026-11-20 redesign):
     *  - blank / unknown / the removed "system" filler row → [PICKER_DEFAULT_ID]
     *    (Piper Aegis female). "system" only ever meant "the phone's voice",
     *    and that behaviour is still one tap away in the Phone voices
     *    section — nobody is left on a voice the picker no longer shows.
     *  - any id the picker knows (a Piper pack or a catalog voice) → kept as-is.
     * Fresh installs already default to "piper-aegis" (AppSettings.voicePref).
     */
    fun migrateVoicePref(stored: String?): String {
        if (stored.isNullOrBlank()) return PICKER_DEFAULT_ID
        if (stored == "system") return PICKER_DEFAULT_ID
        return if (byId(stored) != null) stored else PICKER_DEFAULT_ID
    }

    /** What the Settings "Voice & Language" row shows for a stored pick (migrated first, so it never shows a removed row). */
    fun displayNameFor(stored: String?): String {
        val v = byId(migrateVoicePref(stored)) ?: return "Aegis"
        return "${v.name} · ${v.language}"
    }
}
