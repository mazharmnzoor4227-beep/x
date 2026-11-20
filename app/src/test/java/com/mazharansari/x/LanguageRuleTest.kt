package com.mazharansari.x

import com.mazharansari.x.agent.ScriptHint
import com.mazharansari.x.llm.AssistantItem
import com.mazharansari.x.llm.SystemItem
import com.mazharansari.x.llm.UserItem
import com.mazharansari.x.local.LocalLlmClient
import com.mazharansari.x.local.LocalToolUse
import org.junit.Assert.*
import org.junit.Test

/**
 * Agent-language bug (user report 2026-11-20): the in-app agent answered in
 * Arabic / gibberish to "hi" / "hello" / anything. Root cause, proven in code:
 * the LANGUAGE + SCRIPT rule existed only in the main-agent prompt
 * (Agent.kt systemPrompt). The local-model path REWROTE the system prompt
 * (LocalLlmClient.toMessages) and dropped that rule — and the per-message
 * SCRIPT NOW hint never fired on the FIRST message of a fresh chat
 * (ScriptHint.note returned "" when history was empty), so a 1-word "hi"
 * reached a small Urdu-weak local model with no language anchor at all.
 * These tests pin the fix at the actual code paths, not a re-implementation:
 * the first-message / local-prompt tests FAIL on the old code (empty hint,
 * rule-less local prompt) and pass on the new code.
 */
class LanguageRuleTest {

    // ── ScriptHint.note: the FIRST message must get an anchor ────────────────

    @Test fun firstHiGetsScriptHint() {
        val note = ScriptHint.note("hi", emptyList(), false)
        assertTrue("first 'hi' must get a SCRIPT NOW line, got: '$note'", note.contains("SCRIPT NOW"))
        assertTrue(note.contains("English letters"))
        assertTrue(note.contains("NEVER reply in Arabic"))
    }

    @Test fun devanagariHiDemandsDevanagari() {
        val note = ScriptHint.note("नमस्ते", emptyList(), false)
        assertTrue("Devanagari greeting must demand Devanagari, got: '$note'", note.contains("Devanagari"))
        assertTrue(note.contains("NEVER reply in Arabic"))
    }

    @Test fun romanUrduSalamDemandsRoman() {
        val note = ScriptHint.note("salam", emptyList(), false)
        assertTrue("Roman Urdu greeting must demand English letters / Roman Urdu, got: '$note'",
            note.contains("English letters") && note.contains("Roman Urdu"))
        assertTrue(note.contains("NEVER reply in Arabic"))
    }

    @Test fun englishHelloDemandsEnglish() {
        val note = ScriptHint.note("hello", emptyList(), false)
        assertTrue("English greeting must demand English letters, got: '$note'", note.contains("English letters"))
        assertTrue(note.contains("NEVER reply in Arabic"))
    }

    @Test fun scriptSwitchStillFiresNote() {
        // Regression guard: the pre-existing switch case (Urdu-script answer, then
        // Roman Urdu "dusra do") must keep firing exactly as before.
        val history = listOf(AssistantItem("یہ ایک کہانی ہے"))
        val note = ScriptHint.note("dusra do", history, false)
        assertTrue("script switch must still emit SCRIPT NOW, got: '$note'", note.contains("SCRIPT NOW"))
        assertTrue(note.contains("English letters"))
    }

    // ── ScriptHint.of / ruleFor: classification + per-message anchor ─────────

    @Test fun scriptHintOfClassifiesGreetings() {
        assertEquals(ScriptHint.Script.LATIN, ScriptHint.of("hi"))
        assertEquals(ScriptHint.Script.LATIN, ScriptHint.of("hello"))
        assertEquals(ScriptHint.Script.LATIN, ScriptHint.of("salam"))
        assertEquals(ScriptHint.Script.DEVANAGARI, ScriptHint.of("नमस्ते"))
        assertEquals(ScriptHint.Script.DEVANAGARI, ScriptHint.of("हाय"))
    }

    @Test fun ruleForNamesEachScriptAndBansArabic() {
        assertTrue(ScriptHint.ruleFor("hi").contains("English letters"))
        assertTrue(ScriptHint.ruleFor("नमस्ते").contains("Devanagari Hindi"))
        assertTrue(ScriptHint.ruleFor("salam").contains("Roman Urdu"))
        for (t in listOf("hi", "नमस्ते", "salam", "hello")) {
            assertTrue("ruleFor('$t') must ban Arabic", ScriptHint.ruleFor(t).contains("NEVER reply in Arabic"))
        }
    }

    @Test fun sharedRuleBansArabic() {
        assertTrue(ScriptHint.LANGUAGE_RULE.contains("NEVER reply in Arabic"))
        assertTrue(ScriptHint.LANGUAGE_RULE.contains("Roman Urdu in English letters"))
        assertTrue(ScriptHint.INSTRUCTION_RULE.contains("Do EXACTLY what the user asked"))
    }

    // ── Local model: the rewritten system prompt must KEEP the rule ──────────

    @Test fun localPromptKeepsNeverArabicRule() {
        val fullPrompt = "You are X, a friendly personal AI assistant on the user's Android phone, made for Mazhar.\n" +
            "LANGUAGE + SCRIPT: always reply in the user's language AND the same script they typed.\n" +
            "The user's custom instructions:\nBe brief."
        val msgs = LocalLlmClient.toMessages(listOf(SystemItem(fullPrompt), UserItem("hi")), 12_000)
        val sys = msgs.firstOrNull { it.first == "system" }?.second.orEmpty()
        assertTrue("local system prompt must carry the NEVER-Arabic ban, got: '$sys'", sys.contains("NEVER reply in Arabic"))
        assertTrue("local system prompt must anchor 'hi' to English letters, got: '$sys'", sys.contains("English letters"))
        assertTrue("local system prompt must keep the Roman Urdu mapping, got: '$sys'", sys.contains("Roman Urdu"))
        assertTrue("local system prompt must not smuggle Arabic-script primer characters in",
            sys.none { it.code in 0x0600..0x06FF || it.code in 0xFB50..0xFDFF || it.code in 0xFE70..0xFEFF })
    }

    @Test fun localPromptAnchorsDevanagariHi() {
        val msgs = LocalLlmClient.toMessages(listOf(SystemItem("You are X."), UserItem("नमस्ते")), 12_000)
        val sys = msgs.firstOrNull { it.first == "system" }?.second.orEmpty()
        assertTrue("local system prompt must anchor a Devanagari greeting to Devanagari, got: '$sys'", sys.contains("Devanagari Hindi"))
        assertTrue(sys.contains("NEVER reply in Arabic"))
    }

    // ── Local tool loop: its prompt carried NO language rule at all ───────────

    @Test fun localToolPromptHasNeverArabicRule() {
        val prompt = LocalToolUse.buildToolPrompt(emptyList())
        assertTrue("local tool prompt must carry the NEVER-Arabic ban, got: '$prompt'", prompt.contains("NEVER reply in Arabic"))
        assertTrue("local tool prompt must stay short for a 4k model: ${prompt.length}", prompt.length < 4000)
    }
}
