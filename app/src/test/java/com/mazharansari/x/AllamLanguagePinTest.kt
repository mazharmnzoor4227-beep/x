package com.mazharansari.x

import com.mazharansari.x.agent.IntentGate
import com.mazharansari.x.agent.ScriptHint
import com.mazharansari.x.llm.AssistantItem
import com.mazharansari.x.llm.LlmRequest
import com.mazharansari.x.llm.Mode
import com.mazharansari.x.llm.ModelRanker
import com.mazharansari.x.llm.OpenAiClient
import com.mazharansari.x.llm.Providers
import com.mazharansari.x.llm.SystemItem
import com.mazharansari.x.llm.UserItem
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

/**
 * ALLAM language bug (user report 2026-11-20, video evidence): the same Roman Urdu
 * messages ("hi", "Kya Hal he") got correct Roman Urdu replies from
 * "gemini-3.1-flash-lite" but Arabic replies from "allam-2-7b" (0.2 s).
 *
 * WHERE allam comes from, proven in code (no "allam" string exists anywhere in the
 * app): Groq's LIVE model list. KeyDetector.probe (Connectors.kt) GETs
 * https://api.groq.com/openai/v1/models and returns every id; ConnectorRepo.addKey
 * ranks them and stores the top 12 per mode. Groq serves "allam-2-7b" (SDAIA's
 * Arabic-first ALLaM-2-7B, 4k context, ~1800 tok/s on Groq — the 0.2 s reply).
 * On a realistic Groq live list it ranks #8 FAST, inside the stored top 12, and
 * for small talk ChatViewModel.quickOrder moves non-reasoning models ahead of the
 * reasoning ones — allam is non-reasoning, gpt-oss / qwen3 / deepseek-r1 are — so
 * for "hi" it is #3 inside the Groq key, first provider tried in FAST/AUTO, and
 * answers first whenever the models ahead of it are cooling down on Groq's free
 * per-minute limits. It is NOT the local model (LocalModel is the on-device
 * GGUF list) and it is NOT hardcoded anywhere.
 *
 * WHY it answered Arabic while Gemini did not: the generic fix (c87e2a53) pins the
 * rule only in the main agent's system prompt, and ALLaM is optimised to work
 * without a predefined system prompt, so one generic line buried in a long prompt
 * does not hold its Arabic prior on a 2-word greeting — while Gemini follows it.
 * Worse, Agent.askOnce sends a BARE UserItem with NO system prompt at all, so
 * every internal one-shot call (swarm workers, research planning) that lands on
 * allam carries no language rule whatsoever unless the prompt text embeds it —
 * and the Swarm coder/tester briefs and the Subagents plan prompt do not.
 *
 * THE FIX, tested here against the REAL request builder (OpenAiClient.buildBody —
 * the one place where the model id is known and the wire request is built, shared
 * by Groq AND the user's own server path): for an Arabic-first model id, the
 * self-contained pin (shared rule + per-message anchor + short-greeting repeat)
 * is appended to the system message, injected as a system message when there is
 * none, and repeated as an anchor on the last user message. Every one of these
 * tests FAILS on the old code (no pin anywhere in the allam-bound request) and
 * PASSES on the new code. No live model is called — only prompt construction.
 */
class AllamLanguagePinTest {

    private fun groq() = OpenAiClient(Providers.byId("groq")!!, "k")

    /** The "messages" array of a built request, as (role, content) pairs. */
    private fun messages(body: kotlinx.serialization.json.JsonObject): List<Pair<String, String>> =
        body["messages"]!!.jsonArray.map { m ->
            val o = m.jsonObject
            val role = o["role"]!!.jsonPrimitive.content
            val content = when (val c = o["content"]!!) {
                is kotlinx.serialization.json.JsonPrimitive -> c.content
                is JsonArray -> c.jsonArray.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content.orEmpty()
                else -> ""
            }
            role to content
        }

    private fun systemText(msgs: List<Pair<String, String>>) =
        msgs.firstOrNull { it.first == "system" }?.second.orEmpty()

    private fun userText(msgs: List<Pair<String, String>>) =
        msgs.lastOrNull { it.first == "user" }?.second.orEmpty()

    // ── The askOnce hole: a bare user message must still get a system pin ───────

    @Test fun allamAskOnceRequestCarriesLanguagePin() {
        // Exactly what Agent.askOnce sends: no SystemItem, just the user message.
        val msgs = messages(groq().buildBody(LlmRequest("allam-2-7b", listOf(UserItem("hi")))))
        val sys = systemText(msgs)
        assertTrue("allam-bound askOnce request must gain a system message, got roles: ${msgs.map { it.first }}",
            msgs.any { it.first == "system" })
        assertTrue("injected system pin must ban Arabic, got: '$sys'", sys.contains("NEVER reply in Arabic"))
        assertTrue("pin for 'hi' must anchor English letters / Roman Urdu, got: '$sys'",
            sys.contains("English letters") && sys.contains("Roman Urdu"))
        assertTrue("pin must carry the short-greeting repeat, got: '$sys'", sys.contains("one-word greeting"))
        assertTrue("pin must stay Latin-only (no Arabic-script primer chars)",
            sys.none { it.code in 0x0600..0x06FF || it.code in 0xFB50..0xFDFF || it.code in 0xFE70..0xFEFF })
    }

    @Test fun allamRomanUrduHiAnchoredInUserMessageToo() {
        val msgs = messages(groq().buildBody(LlmRequest("allam-2-7b", listOf(UserItem("Kya Hal he")))))
        val user = userText(msgs)
        assertTrue("last user message to allam must carry the anchor, got: '$user'",
            user.contains("NEVER reply in Arabic") && user.contains("English letters"))
        assertTrue("anchor for 'Kya Hal he' must name Roman Urdu, got: '$user'", user.contains("Roman Urdu"))
    }

    // ── The main chat path: the system prompt must gain the strengthening ───────

    @Test fun allamMainChatRequestRepeatsPin() {
        // A realistic main-agent system prompt WITHOUT the new pin (what Agent.run
        // builds today: prose rule + shared rule, no short-greeting repeat).
        val mainPrompt = "You are X, a friendly personal AI assistant on the user's Android phone.\n" +
            "LANGUAGE + SCRIPT: always reply in the user's language AND the same script they typed. " +
            "Roman Urdu / Hinglish → reply in natural Roman Urdu in English letters. " +
            ScriptHint.LANGUAGE_RULE
        val msgs = messages(groq().buildBody(LlmRequest("allam-2-7b", listOf(SystemItem(mainPrompt), UserItem("Kya Hal he")))))
        val sys = systemText(msgs)
        assertTrue("allam system prompt must keep the shared rule", sys.contains(ScriptHint.LANGUAGE_RULE))
        assertTrue("allam system prompt must add the short-greeting repeat, got tail: '${sys.takeLast(400)}'",
            sys.contains("Even for a one-word greeting"))
        assertTrue("allam system prompt must repeat the Roman Urdu anchor, got tail: '${sys.takeLast(400)}'",
            sys.contains("Roman Urdu"))
    }

    @Test fun allamDevanagariHiAnchored() {
        val msgs = messages(groq().buildBody(LlmRequest("allam-2-7b", listOf(UserItem("नमस्ते")))))
        val sys = systemText(msgs)
        assertTrue("pin for Devanagari 'नमस्ते' must demand Devanagari Hindi, got: '$sys'", sys.contains("Devanagari Hindi"))
        assertTrue(sys.contains("NEVER reply in Arabic"))
        assertTrue("user anchor for Devanagari must name Devanagari Hindi, got: '${userText(msgs)}'",
            userText(msgs).contains("Devanagari Hindi"))
    }

    @Test fun allamEnglishHelloAnchored() {
        val msgs = messages(groq().buildBody(LlmRequest("allam-2-7b", listOf(UserItem("hello")))))
        val sys = systemText(msgs)
        assertTrue("pin for 'hello' must anchor English letters, got: '$sys'", sys.contains("English letters"))
        assertTrue(sys.contains("NEVER reply in Arabic"))
    }

    // ── Scope guard: nothing changes for any other model ────────────────────────

    @Test fun nonAllamGroqModelUnchanged() {
        val msgs = messages(groq().buildBody(LlmRequest("openai/gpt-oss-20b", listOf(UserItem("hi")))))
        assertTrue("gpt-oss request must gain NO system message, got roles: ${msgs.map { it.first }}",
            msgs.none { it.first == "system" })
        assertEquals("gpt-oss user text must be byte-identical", "hi", userText(msgs))
        val msgs2 = messages(groq().buildBody(LlmRequest("llama-3.1-8b-instant", listOf(SystemItem("You are X."), UserItem("hi")))))
        assertEquals("llama system text must be byte-identical", "You are X.", systemText(msgs2))
        assertEquals("llama user text must be byte-identical", "hi", userText(msgs2))
    }

    @Test fun arabicFirstDetectionIsNarrow() {
        assertTrue(ScriptHint.isArabicFirstModel("allam-2-7b"))
        assertTrue(ScriptHint.isArabicFirstModel("sdaia/allam-2-7b"))
        assertTrue(ScriptHint.isArabicFirstModel("ALLAM-2-7B"))
        assertFalse(ScriptHint.isArabicFirstModel("openai/gpt-oss-20b"))
        assertFalse(ScriptHint.isArabicFirstModel("llama-3.1-8b-instant"))
        assertFalse(ScriptHint.isArabicFirstModel("gemini-3.1-flash-lite"))
        assertFalse(ScriptHint.isArabicFirstModel("qwen/qwen3-32b"))
    }

    @Test fun arabicFirstPinIsSelfContainedPerScript() {
        for ((text, anchor) in listOf("hi" to "English letters", "नमस्ते" to "Devanagari Hindi", "hello" to "English letters", "Kya Hal he" to "Roman Urdu")) {
            val pin = ScriptHint.arabicFirstPin(text)
            assertTrue("pin('$text') must contain the shared rule", pin.contains(ScriptHint.LANGUAGE_RULE))
            assertTrue("pin('$text') must contain the $anchor anchor, got: '$pin'", pin.contains(anchor))
            assertTrue("pin('$text') must ban Arabic", pin.contains("NEVER reply in Arabic"))
            assertTrue("pin('$text') must stay Latin-only",
                pin.none { it.code in 0x0600..0x06FF || it.code in 0xFB50..0xFDFF || it.code in 0xFE70..0xFEFF })
        }
    }

    // ── Reachability: allam really is in the Groq rotation the app stores ───────

    @Test fun groqFastRankingReachesAllam() {
        // Groq's live chat list as served in Nov 2026 (console.groq.com/docs/models
        // + /v1/models): gpt-oss pair, qwen3 pair, compound pair, allam-2-7b, the
        // llama pair, mistral-saba, deepseek-r1-distill-llama-70b, qwq-32b; whisper
        // and guard models are filtered by ModelRanker.exclude. tools=null matches
        // Groq's /models payload (no supported_parameters field) — and the video
        // proves allam is actually answered, so it passes the tools filter.
        val groqLive = listOf("openai/gpt-oss-120b", "openai/gpt-oss-20b", "openai/gpt-oss-safeguard-20b",
            "qwen/qwen3.6-27b", "qwen/qwen3.8-27b", "groq/compound", "groq/compound-mini", "allam-2-7b",
            "llama-3.1-8b-instant", "llama-3.3-70b-versatile", "mistral-saba-24b",
            "deepseek-r1-distill-llama-70b", "qwen/qwq-32b", "whisper-large-v3", "meta-llama/llama-guard-4-12b")
            .map { ModelRanker.ModelInfo(it) }
        val ranked = ModelRanker.rank(Providers.byId("groq")!!, groqLive, Mode.FAST)
        println("Groq FAST: $ranked")
        assertTrue("allam-2-7b must be inside the stored top-12 FAST list, got: $ranked", "allam-2-7b" in ranked.take(12))
        assertTrue("whisper/guard models must stay excluded", ranked.none { "whisper" in it || "guard" in it })
    }

    @Test fun allamIsNonReasoningSoQuickOrderPromotesIt() {
        // For small talk ("hi") ChatViewModel.quickOrder sorts non-reasoning models
        // ahead of reasoning ones (stable sort). allam is non-reasoning while the
        // models ranked above it in FAST are reasoning — that is how it ends up
        // answering a greeting first once the models ahead cool down.
        assertFalse("allam-2-7b must be non-reasoning", IntentGate.isReasoningModel("allam-2-7b"))
        assertTrue(IntentGate.isReasoningModel("openai/gpt-oss-20b"))
        assertTrue(IntentGate.isReasoningModel("qwen/qwen3-32b"))
        assertTrue(IntentGate.isReasoningModel("deepseek-r1-distill-llama-70b"))
        assertFalse(IntentGate.isReasoningModel("llama-3.1-8b-instant"))
    }

    // ── The pin must not disturb multi-turn requests ────────────────────────────

    @Test fun allamPinAppliesOnceInMultiTurnRequest() {
        val msgs = messages(groq().buildBody(LlmRequest("allam-2-7b", listOf(
            SystemItem("You are X."),
            UserItem("hi"),
            AssistantItem("Hello! Kaise ho?"),
            UserItem("Kya Hal he"),
        ))))
        assertEquals("exactly one system message", 1, msgs.count { it.first == "system" })
        assertTrue("system pin must anchor the LATEST user message (Roman Urdu)",
            systemText(msgs).contains("Roman Urdu"))
        val firstUser = msgs.filter { it.first == "user" }.first().second
        assertEquals("earlier user turn must be untouched", "hi", firstUser)
        assertTrue("last user turn must carry the anchor", userText(msgs).contains("NEVER reply in Arabic"))
    }
}
