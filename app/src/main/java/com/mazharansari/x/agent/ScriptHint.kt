package com.mazharansari.x.agent

import com.mazharansari.x.llm.AssistantItem
import com.mazharansari.x.llm.ChatItem
import com.mazharansari.x.llm.UserItem

/**
 * v0.5.1 (mazhar 3:36 PM): after an answer in Urdu script, "dusra do" (Roman Urdu) must get a Roman Urdu reply.
 * The system prompt already says "follow the script of the latest message"; when the conversation so far is in a
 * different script, or the message is a short follow-up, we add one explicit line so the model can't drift.
 */
object ScriptHint {
    enum class Script { LATIN, ARABIC, DEVANAGARI, OTHER }

    /**
     * The ONE shared language rule, pinned in every system prompt that produces
     * user-visible replies (main agent, local-model prompt, local tool prompt,
     * swarm reviewer, research synthesis, voice-call instructions). One wording
     * so the rule cannot drift between prompts again. Written in Latin letters
     * only on purpose: the old main-agent prompt showed the model literal
     * Arabic-script ("اردو") and Devanagari ("हिंदी") examples, which a small
     * model can copy into its own reply.
     */
    const val LANGUAGE_RULE = "Always reply in the exact language AND script the user used in that message — Devanagari Hindi → Devanagari Hindi; Roman Urdu/Hinglish → Roman Urdu in English letters; English → English. NEVER reply in Arabic (the Arabic language). Never switch to Perso-Arabic/Urdu script when the user wrote Devanagari or Roman."

    /** Pinned alongside [LANGUAGE_RULE] in the main agent system prompt. */
    const val INSTRUCTION_RULE = "Do EXACTLY what the user asked — no unsolicited extras, no nonsense, no filler talk. If a request is unclear, ask ONE short clarifying question instead of guessing and rambling."

    /** Per-message anchor for prompts that cannot carry the full rule (small local models): the script of THIS message, named concretely, plus the Arabic ban. */
    fun ruleFor(text: String): String = when (of(text)) {
        Script.DEVANAGARI -> "The user's latest message is in Devanagari Hindi: reply in Devanagari Hindi. NEVER reply in Arabic (the Arabic language)."
        Script.ARABIC -> "The user's latest message is in Urdu script: reply in Urdu script. NEVER reply in Arabic (the Arabic language)."
        Script.LATIN -> "The user's latest message is in English letters: reply in English letters — Roman Urdu if they wrote Urdu or Hindi words in English letters, English if they wrote English. NEVER reply in Arabic (the Arabic language)."
        Script.OTHER -> LANGUAGE_RULE
    }

    fun of(text: String): Script {
        var lat = 0; var ar = 0; var dev = 0
        for (c in text) when {
            c in 'a'..'z' || c in 'A'..'Z' -> lat++
            c in '\u0600'..'\u06FF' || c in '\u0750'..'\u077F' || c in '\uFB50'..'\uFDFF' || c in '\uFE70'..'\uFEFF' -> ar++
            c in '\u0900'..'\u097F' -> dev++
        }
        val max = maxOf(lat, ar, dev)
        return when { max == 0 -> Script.OTHER; max == ar -> Script.ARABIC; max == dev -> Script.DEVANAGARI; else -> Script.LATIN }
    }

    private fun name(s: Script) = when (s) {
        Script.LATIN -> "English letters (Roman Urdu if they write Urdu words in English letters, e.g. \"dusra do\")"
        Script.ARABIC -> "Urdu script (اردو)"
        Script.DEVANAGARI -> "Devanagari"
        Script.OTHER -> "the user's script"
    }

    fun note(latest: String, history: List<ChatItem>, followUp: Boolean): String {
        val script = of(latest)
        val recent = history.takeLast(6).joinToString(" ") { when (it) { is AssistantItem -> it.text.take(600); is UserItem -> it.text.take(300); else -> "" } }
        val prior = if (recent.isBlank()) script else of(recent)
        val sb = StringBuilder()
        if (followUp && history.any { it is AssistantItem }) {
            sb.append("\nFOLLOW-UP: the user's latest message (\"").append(latest.trim().take(40)).append("\") is a short follow-up to the conversation above. ")
            sb.append("Do what they asked last time again or continue it (e.g. \"dusra do\" / \"ek aur\" = give another one of the same kind, \"aur\" = continue), at full length — not a one-line reply.")
        }
        // The first message of a fresh chat gets a SCRIPT NOW line too: before this
        // fix, an empty history made prior == script, so the very first "hi" got NO
        // script anchor at all — only the system-prompt rule — and a weak model drifted.
        if (script != Script.OTHER && (prior != script || followUp || recent.isBlank())) {
            sb.append("\nSCRIPT NOW: the latest message is written in ").append(name(script)).append(". Write your whole reply in that script, even if earlier messages or your earlier answers used another script. NEVER reply in Arabic (the Arabic language).")
        }
        return sb.toString()
    }
}
