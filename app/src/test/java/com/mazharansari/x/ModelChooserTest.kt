package com.mazharansari.x

import com.mazharansari.x.antigravity.AgyAccount
import com.mazharansari.x.data.Connector
import com.mazharansari.x.llm.FailKind
import com.mazharansari.x.llm.LlmException
import com.mazharansari.x.llm.ModelChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Model chooser + auto-shift (2026-11-20, user order). These tests fail on
 * the old code: the old popover listed only Fast/Auto/Expert (+ Local /
 * Server / Free) with no Antigravity accounts and no per-model rows, the
 * shift was a transient "Switching to…" line cleared by the first answer
 * text, and nothing persisted the shift — the next message re-tried the
 * spent model. They pass on the new [ModelChoice] seam that the popover,
 * ChatViewModel and Agent all read.
 */
class ModelChooserTest {

    private fun connector(
        id: String, providerId: String, models: List<String>, fastModels: List<String> = emptyList(),
        pinned: String? = null, enabled: Boolean = true, keyHint: String = "••••abcd",
        localModel: String? = null, baseUrl: String? = null,
    ) = Connector(id, providerId, "", keyHint, models, fastModels, pinned, enabled, localModel = localModel, baseUrl = baseUrl)

    private val gemini = connector("c-gemini", "gemini",
        models = listOf("gemini-2.5-flash", "gemini-2.5-pro"),
        fastModels = listOf("gemini-2.5-flash-lite"), pinned = "gemini-3-flash")
    private val openRouter = connector("c-or", "openrouter",
        models = listOf("google/gemma-4-31b-it:free", "openai/gpt-oss-120b:free"))
    private val groqDisabled = connector("c-groq", "groq", models = listOf("openai/gpt-oss-120b"), enabled = false)
    private val local = connector("c-local", "local", models = listOf("LFM3-1.7B"), localModel = "LFM3-1.7B", keyHint = "LFM3-1.7B")
    private val serverDisabled = connector("c-server", "myserver", models = listOf("qwen2.5-coder:7b"), enabled = false, baseUrl = "https://1-2-3-4.sslip.io/v1")

    private val agyA = AgyAccount("mazhar@gmail.com", "mazhar@gmail.com_aaa", label = "Mazhar")
    private val agyB = AgyAccount("second@gmail.com", "second@gmail.com_bbb", label = "")
    private val agyNoToken = AgyAccount("ghost@gmail.com", "ghost@gmail.com_ccc", label = "Ghost")

    private fun entries(hasToken: (AgyAccount) -> Boolean = { it != agyNoToken }) =
        ModelChoice.buildChooserEntries(
            listOf(gemini, openRouter, groqDisabled, local, serverDisabled),
            listOf(agyA, agyB, agyNoToken), hasToken,
        )

    @Test
    fun `chooser lists every connected providers models and every Antigravity account`() {
        val e = entries()
        val titles = e.map { it.title }
        // Every model on every connected key (pinned first, then ranked lists).
        assertTrue("gemini-3-flash" in titles)
        assertTrue("gemini-2.5-flash" in titles)
        assertTrue("gemini-2.5-pro" in titles)
        assertTrue("gemini-2.5-flash-lite" in titles)
        assertTrue("google/gemma-4-31b-it:free" in titles)
        assertTrue("openai/gpt-oss-120b:free" in titles)
        // Every Antigravity account with a sign-in, as its own row, by account.
        val agy = e.filter { it.kind == ModelChoice.Kind.AGY }
        assertEquals(listOf("agy:mazhar@gmail.com", "agy:second@gmail.com"), agy.map { it.id })
        assertEquals(listOf("Antigravity", "Antigravity"), agy.map { it.group })
        assertEquals("Mazhar", agy[0].title)
        assertEquals("second@gmail.com", agy[1].title)   // no label → the email is the title
        assertEquals("Antigravity · Mazhar", agy[0].attemptLabel)
        // The on-phone model and Free are there too.
        assertTrue(e.any { it.kind == ModelChoice.Kind.LOCAL && it.id == "local:LFM3-1.7B" })
        assertTrue(e.any { it.kind == ModelChoice.Kind.FREE && it.id == "free" })
    }

    @Test
    fun `chooser shows only models the user can actually use`() {
        val e = entries()
        val titles = e.map { it.title }
        assertFalse("openai/gpt-oss-120b" in titles)   // the disabled Groq key contributes nothing
        assertFalse("qwen2.5-coder:7b" in titles)      // the disabled server contributes nothing
        assertFalse(e.any { it.id == "agy:ghost@gmail.com" })   // account without a sign-in is not routable
        // Nothing is invented: every API row comes from a connector's own stored lists.
        val known = listOf(gemini, openRouter).flatMap { it.models + it.fastModels + listOfNotNull(it.pinnedModel) }.toSet()
        e.filter { it.kind == ModelChoice.Kind.API }.forEach { assertTrue(it.title in known) }
    }

    @Test
    fun `picking a model persists and routes the next attempt through it`() {
        var saved = ""
        val router = ModelChoice.ModelRouter("") { saved = it }
        val e = entries()
        val geminiFlash = e.first { it.title == "gemini-2.5-flash" }
        router.pick(geminiFlash.id)
        assertEquals(geminiFlash.id, saved)   // persisted (ChatViewModel wires this to settings.pickedModel)
        assertEquals(geminiFlash.id, router.pickedId)
        assertEquals(geminiFlash.id, router.ordered(e).first().id)   // the next attempt starts here
        // Mid-conversation switch (plan with one model, build with another): just as quick, and it sticks.
        val agyId = "agy:second@gmail.com"
        router.pick(agyId)
        assertEquals(agyId, saved)
        assertEquals(agyId, router.ordered(e).first().id)
        // Back to Auto clears the pick and restores the chooser order.
        router.clear()
        assertEquals("", saved)
        assertEquals(e.first().id, router.ordered(e).first().id)
    }

    @Test
    fun `quota failure on the picked model shifts to the next available model and the shift persists`() {
        // Detection reuses the providers' own classification — no new error strings.
        assertTrue(ModelChoice.isExhaustion(LlmException("429: exceeded your current quota", FailKind.QUOTA, 429)))
        assertTrue(ModelChoice.isExhaustion(LlmException("Rate limit exceeded: try again in 28s", FailKind.RATE, 429)))
        assertTrue(ModelChoice.isExhaustion(LlmException("resource_exhausted: quota", FailKind.QUOTA, 429)))
        assertFalse(ModelChoice.isExhaustion(LlmException("invalid api key", FailKind.AUTH, 401)))
        assertFalse(ModelChoice.isExhaustion(LlmException("model exploded", FailKind.SERVER, 500)))
        assertFalse(ModelChoice.isExhaustion(LlmException("no space left on device", FailKind.SERVER, 0)))

        val e = entries()
        val ordered = e.map { it.id }
        var saved = ""
        val router = ModelChoice.ModelRouter(ordered[0]) { saved = it }
        // The picked model is spent: the next untried model/account answers the SAME work.
        val tried = setOf(ordered[0])
        val next = ModelChoice.nextUntriedId(ordered, tried)
        assertEquals(ordered[1], next)
        val from = e[0].attemptLabel
        val to = e[1].attemptLabel
        val notice = router.shiftTo(next!!, from, to)
        assertEquals("Shifted to $to — $from hit its limit.", notice)
        assertEquals(next, saved)                       // the shift is persisted at once
        assertEquals(next, router.pickedId)
        assertEquals(next, router.ordered(e).first().id)   // the NEXT message keeps using the new model
    }

    @Test
    fun `account level shift goes to the next Antigravity account before the next provider`() {
        val e = entries()
        val ordered = e.map { it.id }
        // Both Antigravity accounts head the order; spending the first lands on the second, not on Gemini.
        assertEquals(listOf("agy:mazhar@gmail.com", "agy:second@gmail.com"), ordered.take(2))
        val next = ModelChoice.nextUntriedId(ordered, setOf("agy:mazhar@gmail.com"))
        assertEquals("agy:second@gmail.com", next)
        val afterBoth = ModelChoice.nextUntriedId(ordered, setOf("agy:mazhar@gmail.com", "agy:second@gmail.com"))
        assertTrue(afterBoth!!.startsWith("api:"))   // both accounts spent → the next provider model
    }

    @Test
    fun `exhausting all models and accounts surfaces an honest state instead of looping`() {
        val e = entries()
        val ordered = e.map { it.id }
        val tried = ordered.toSet()
        assertNull(ModelChoice.nextUntried(e, tried))
        assertNull(ModelChoice.nextUntriedId(ordered, tried))
        // Calling again still returns null — the chain ends, it never wraps around to the first model.
        assertNull(ModelChoice.nextUntriedId(ordered, tried))
        assertTrue(ModelChoice.ALL_EXHAUSTED_MESSAGE.contains("All models hit their limit"))
    }

    @Test
    fun `stale pick falls back to Auto and unknown ids never parse`() {
        val e = entries()
        assertEquals(e, ModelChoice.attemptOrder(e, "api:gone:gemini-2.5-flash"))
        assertEquals(e, ModelChoice.attemptOrder(e, ""))
        assertNull(ModelChoice.parsePick("garbage"))
        assertNull(ModelChoice.parsePick("api:only-one-part"))
        assertEquals("Auto", ModelChoice.displayLabelFromEntries("api:gone:gemini-2.5-flash", "AUTO", e))
        assertEquals("Fast", ModelChoice.displayLabelFromEntries("", "FAST", e))
        assertEquals("Auto", ModelChoice.displayLabelFromEntries("", "AUTO", e))
    }

    @Test
    fun `pick ids round-trip every kind including models with a colon`() {
        val api = ModelChoice.parsePick("api:c-or:google/gemma-4-31b-it:free")
        assertEquals(ModelChoice.ModelPick.Api("c-or", "google/gemma-4-31b-it:free"), api)
        assertEquals(ModelChoice.ModelPick.Agy("mazhar@gmail.com"), ModelChoice.parsePick("agy:Mazhar@Gmail.com"))
        assertEquals(ModelChoice.ModelPick.Local("LFM3-1.7B"), ModelChoice.parsePick("local:LFM3-1.7B"))
        assertEquals(ModelChoice.ModelPick.Server("qwen2.5-coder:7b"), ModelChoice.parsePick("server:qwen2.5-coder:7b"))
        assertEquals(ModelChoice.ModelPick.Free, ModelChoice.parsePick("free"))
        assertNull(ModelChoice.parsePick(""))
    }

    @Test
    fun `shift labels map back to the pick that was landed on`() {
        val e = entries()
        val flash = e.first { it.title == "gemini-2.5-flash" }
        assertEquals(flash.id, ModelChoice.findChoiceId(e, "Google Gemini · gemini-2.5-flash"))
        assertEquals("agy:mazhar@gmail.com", ModelChoice.findChoiceId(e, "Antigravity · Mazhar"))
        assertEquals("agy:second@gmail.com", ModelChoice.findChoiceId(e, "Antigravity · second@gmail.com"))
        assertEquals("free", ModelChoice.findChoiceId(e, "Free (slow)"))
        // "Antigravity · auto" names no account — the account-level shift already happened inside
        // AgyClient, so the pick is left alone (null) instead of being guessed.
        assertNull(ModelChoice.findChoiceId(e, "Antigravity · auto"))
        assertNull(ModelChoice.findChoiceId(e, "No such provider · no-such-model"))
    }

    @Test
    fun `header and chip show the picked model, or the tier when nothing is picked`() {
        val e = entries()
        val flash = e.first { it.title == "gemini-2.5-flash" }
        assertEquals("gemini-2.5-flash", ModelChoice.displayLabelFromEntries(flash.id, "AUTO", e))
        assertEquals("Antigravity · Mazhar", ModelChoice.displayLabelFromEntries("agy:mazhar@gmail.com", "AUTO", e))
        assertEquals("Free (slow)", ModelChoice.displayLabelFromEntries("free", "AUTO", e))
        assertEquals("Auto", ModelChoice.displayLabelFromEntries("", "AUTO", e))
    }
}
