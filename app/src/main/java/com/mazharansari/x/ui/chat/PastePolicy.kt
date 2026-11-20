package com.mazharansari.x.ui.chat

/**
 * The pure decision behind Settings → Advanced → "Paste as File".
 * Kept dependency-free so the rule is unit-testable without a device.
 *
 * Fixed 2026-11-20 (the setting looked dead): the old rule used TWO
 * thresholds — "Always" converted only over 2,000 chars and "Always ask"
 * asked only over 60,000 chars. A normal paste (a paragraph, a code
 * snippet, a few hundred to a few thousand chars) therefore behaved
 * identically under all three modes, so tapping a mode changed nothing
 * the user could see. There is now ONE threshold, [FILE_THRESHOLD]:
 * any message over 500 chars is a file candidate, and the mode decides
 * what happens to it. Short chat messages (500 chars or fewer) always
 * send inline under every mode — turning "hi" into a file attachment
 * would be absurd, and the setting is about pastes, not typing.
 *
 * - "Always": any message over [FILE_THRESHOLD] goes in as a .txt attachment.
 * - "Always ask": any message over [FILE_THRESHOLD] asks first ("Send as a file?").
 * - "Never": nothing is ever converted.
 * Anything else (including an unknown stored value) behaves like "Never" is never
 * assumed — it falls through to inline, so a corrupt setting cannot eat a message.
 */
enum class PasteAction { SEND_INLINE, CONVERT_TO_FILE, ASK_USER }

object PastePolicy {
    /** A message longer than this is a file candidate. See the KDoc above. */
    const val FILE_THRESHOLD = 500

    fun decide(mode: String, length: Int, fileThreshold: Int = FILE_THRESHOLD): PasteAction = when {
        length <= fileThreshold -> PasteAction.SEND_INLINE
        mode == "Always" -> PasteAction.CONVERT_TO_FILE
        mode == "Always ask" -> PasteAction.ASK_USER
        else -> PasteAction.SEND_INLINE
    }
}
