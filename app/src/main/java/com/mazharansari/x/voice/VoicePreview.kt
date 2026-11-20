package com.mazharansari.x.voice

/**
 * Tap-to-preview for the Voice & Language picker (redesign 2026-11-20).
 * Pure logic — no Android, no audio — so the picker tests can prove the
 * behaviour without a phone:
 *
 *  - Every preview is a read-aloud under the message id
 *    "preview:<voiceId>" ([messageId]). The card's playing indicator reads
 *    exactly that id from [ReadAloudPlayer.playing], so the indicator is
 *    on for the voice that is actually speaking and for no other card.
 *  - [toggle] is the state machine: tapping the playing card stops it
 *    (null), tapping another card switches to it, tapping a stopped card
 *    starts it.
 *  - The preview speaks [sampleTextFor] — a short sample in that voice's
 *    own language (Roman Urdu for Aegis/Fasih, Devanagari Hindi for
 *    Kokoro, Urdu script for Uzma/Asad, English for the English voices).
 *  - The audio itself goes through the ONE existing voice path
 *    ([ReadAloudPlayer.preview]): a Piper pack speaks on the sherpa-onnx
 *    Piper engine (or the phone's Urdu/Hindi voice when the pack is not
 *    downloaded yet — the same honest fallback as read-aloud), and a
 *    catalog voice speaks through the normal chain with that voice forced
 *    — never a generic default. A preview is stopped cleanly by
 *    [ReadAloudPlayer.stop] (tap again / tap another card / leave the
 *    page), and a real read-aloud stops a preview the same way, because
 *    [ReadAloudPlayer.speak] stops first.
 */
object VoicePreview {
    /** Prefix for every preview's read-aloud message id. */
    const val PREFIX = "preview:"

    fun messageId(voiceId: String): String = PREFIX + voiceId

    fun isPreviewId(messageId: String?): Boolean = messageId?.startsWith(PREFIX) == true

    fun voiceIdOf(messageId: String?): String? = messageId?.takeIf { isPreviewId(it) }?.removePrefix(PREFIX)

    fun sampleTextFor(voiceId: String): String = VoicePicker.byId(voiceId)?.sampleText ?: VoiceCatalog.PREVIEW_TEXT

    /**
     * The preview state machine, as the UI drives it from
     * [ReadAloudPlayer.playing]: [currentPreviewingId] is the voice id
     * currently previewing (null when none), [tappedId] the card just
     * tapped. Returns the voice id that should be previewing afterwards
     * (null = stopped). Tapping the playing card stops it; tapping any
     * other card switches to it.
     */
    fun toggle(currentPreviewingId: String?, tappedId: String): String? =
        if (currentPreviewingId == tappedId) null else tappedId
}
