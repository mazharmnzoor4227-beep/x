package com.mazharansari.x

import androidx.test.core.app.ApplicationProvider
import com.mazharansari.x.data.AppSettings
import com.mazharansari.x.data.Connector
import com.mazharansari.x.data.ConnectorRepo
import com.mazharansari.x.llm.Mode
import com.mazharansari.x.llm.ModelChoice
import com.mazharansari.x.llm.ModelRanker
import com.mazharansari.x.llm.Providers
import com.mazharansari.x.local.LocalModel
import com.mazharansari.x.ui.ChatViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * LFM3-1.7B as the default local model (2026-11-20, user decision: "LFM3-1.7B
 * default kar do", replacing allam-2-7b).
 *
 * These tests FAIL on the old code: there the default attempt list was the
 * cloud ranking (Groq first, allam-2-7b #3 in the Groq key for small talk and
 * first whenever the models ahead of it cooled down — AllamLanguagePinTest),
 * the local model was only the backup after the keys, and nothing anywhere
 * named LFM3 as the app-wide default. They PASS on the new code: no pick +
 * stored AUTO + the LFM3 file on the phone → local LFM3 answers first;
 * allam is demoted to last in every default ranking; an explicit pick or an
 * explicit mode is never overridden; without the download the fallback is
 * the cloud ranking minus allam-first, honestly labelled.
 *
 * The LFM3 "download" here is just the file's presence (the tests never
 * stream from it, so no native model is loaded).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = AxionApp::class)
class Lfm3DefaultTest {
    private val app get() = ApplicationProvider.getApplicationContext<AxionApp>()

    @Suppress("UNCHECKED_CAST")
    private fun <T> flow(o: Any, name: String) = o.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(o) as MutableStateFlow<T>

    private fun seed(vararg cs: Connector) { flow<List<Connector>>(app.graph.connectors, "_list").value = cs.toList() }
    private fun setMode(m: String) { app.graph.settings.update { it.copy(mode = m) } }
    private fun setPick(id: String) { app.graph.settings.update { it.copy(pickedModel = id) } }
    private fun blob(s: String) = "plain:" + android.util.Base64.encodeToString(s.toByteArray(), 2)

    /** A Groq key whose STORED fast list leads with allam — the old default. */
    private fun groqAllamFirst() = Connector("c-groq", "groq", blob("k"), "gsk_…1",
        models = listOf("openai/gpt-oss-120b", "allam-2-7b", "llama-3.3-70b-versatile"),
        fastModels = listOf("allam-2-7b", "openai/gpt-oss-20b", "llama-3.1-8b-instant"), addedAt = 1)
    private fun gemini() = Connector("c-gemini", "gemini", blob("k"), "AIza…1",
        models = listOf("gemini-2.5-flash", "gemini-2.5-pro"),
        fastModels = listOf("gemini-2.5-flash-lite"), pinned = "gemini-3-flash", addedAt = 1)
    private fun local(modelId: String) = Connector("c-local", "local", blob("local"), modelId,
        listOf(modelId), listOf(modelId), addedAt = 1, localModel = modelId)
    private fun server() = Connector("c-server", "myserver", blob(""), "example.invalid",
        listOf("qwen2.5-coder:7b"), listOf("qwen2.5-coder:7b"), addedAt = 1, baseUrl = "https://example.invalid/v1")

    private fun lfm3File(): File {
        val f = File(app.filesDir, "models/${LocalModel.LFM3_1_7B.url.substringAfterLast('/')}")
        f.parentFile?.mkdirs()
        f.writeText("fake-gguf")
        return f
    }

    @After fun tearDown() {
        runCatching { File(app.filesDir, "models/${LocalModel.LFM3_1_7B.url.substringAfterLast('/')}").delete() }
        app.graph.settings.update { it.copy(mode = "AUTO", pickedModel = "") }
    }

    // (a) LFM3 downloaded + no pick → local LFM3 first, exactly once.

    @Test fun `lfm3 downloaded with no pick answers first`() {
        seed(groqAllamFirst(), local("lfm3-1.7b")); setMode("AUTO"); setPick(""); lfm3File()
        val list = ChatViewModel(app).attempts(Mode.FAST)
        assertEquals("Local model", list.first().provider)
        assertEquals("lfm3-1.7b", list.first().model)
        assertEquals(1, list.count { it.model == "lfm3-1.7b" })   // no duplicate tail copy
        assertTrue(list.any { it.model == "allam-2-7b" })          // allam still exists as failover…
        assertEquals("allam-2-7b", list.map { it.model }.filter { it in setOf("allam-2-7b", "openai/gpt-oss-20b", "llama-3.1-8b-instant") }.last())
    }

    @Test fun `lfm3 downloaded but never activated still answers first and is activated`() {
        seed(groqAllamFirst()); setMode("AUTO"); setPick(""); lfm3File()
        val list = ChatViewModel(app).attempts(Mode.FAST)
        assertEquals("lfm3-1.7b", list.first().model)
        // One-time migration: the phone now has LFM3 as its local model, so the
        // chooser row and the chip exist for it from here on.
        val localNow = app.graph.connectors.list.value.firstOrNull { it.providerId == "local" }
        assertNotNull(localNow)
        assertEquals("lfm3-1.7b", localNow!!.localModel)
    }

    @Test fun `lfm3 downloaded while another local model is active still answers first`() {
        seed(groqAllamFirst(), local("qwen2.5-coder-1.5b")); setMode("AUTO"); setPick(""); lfm3File()
        val models = ChatViewModel(app).attempts(Mode.FAST).map { it.model }
        assertEquals("lfm3-1.7b", models.first())
        assertTrue("qwen2.5-coder-1.5b" in models)   // the active local model stays as backup, not replaced
        val localNow = app.graph.connectors.list.value.firstOrNull { it.providerId == "local" }
        assertEquals("qwen2.5-coder-1.5b", localNow!!.localModel)   // activation never replaces an explicit local model
    }

    // (b) allam is never first in the default ranking, with or without LFM3.

    @Test fun `allam never first without lfm3, quick or not`() {
        seed(groqAllamFirst()); setMode("AUTO"); setPick("")
        val vm = ChatViewModel(app)
        for (quick in listOf(false, true)) {
            val models = vm.attempts(Mode.FAST, quick).map { it.model }
            assertNotEquals("allam-2-7b", models.first())
            assertEquals("allam-2-7b", models.filter { it in setOf("allam-2-7b", "openai/gpt-oss-20b", "llama-3.1-8b-instant") }.last())
        }
        // Directly at the ranking layer (covers Telegram / scheduled runs too).
        val ordered = ConnectorRepo.order(listOf(groqAllamFirst()), Mode.FAST, System.currentTimeMillis(), emptyMap(), null)
        assertEquals("allam-2-7b", ordered.map { it.second }.filter { it in setOf("allam-2-7b", "openai/gpt-oss-20b", "llama-3.1-8b-instant") }.last())
        assertNotEquals("allam-2-7b", ordered.first().second)
    }

    @Test fun `allam never first with lfm3 downloaded`() {
        seed(groqAllamFirst(), local("lfm3-1.7b")); setMode("AUTO"); setPick(""); lfm3File()
        val models = ChatViewModel(app).attempts(Mode.FAST, quick = true).map { it.model }
        assertEquals("lfm3-1.7b", models.first())
        assertEquals("allam-2-7b", models.filter { it in setOf("allam-2-7b", "openai/gpt-oss-20b", "llama-3.1-8b-instant") }.last())
    }

    @Test fun `ranker puts allam last on a newly added groq key`() {
        val groq = Providers.byId("groq")!!
        val live = listOf("allam-2-7b", "openai/gpt-oss-20b", "llama-3.1-8b-instant", "openai/gpt-oss-120b", "qwen/qwen3-32b")
            .map { ModelRanker.ModelInfo(it) }
        for (mode in listOf(Mode.FAST, Mode.EXPERT)) {
            val ranked = ModelRanker.rank(groq, live, mode)
            assertEquals("allam-2-7b", ranked.last())
            assertNotEquals("allam-2-7b", ranked.first())
        }
    }

    // (c) an explicit chooser pick always wins over the default — including allam.

    @Test fun `explicit allam pick still wins and routes to allam`() {
        seed(groqAllamFirst(), local("lfm3-1.7b")); setMode("AUTO"); setPick("api:c-groq:allam-2-7b"); lfm3File()
        val list = ChatViewModel(app).attempts(Mode.FAST)
        assertEquals("allam-2-7b", list.first().model)
        // The Arabic-first pin for that explicit use is unchanged (AllamLanguagePinTest).
        assertTrue(com.mazharansari.x.agent.ScriptHint.isArabicFirstModel(list.first().model))
    }

    @Test fun `explicit gemini pick beats the lfm3 default`() {
        seed(groqAllamFirst(), gemini(), local("lfm3-1.7b")); setMode("AUTO"); setPick("api:c-gemini:gemini-2.5-flash"); lfm3File()
        assertEquals("gemini-2.5-flash", ChatViewModel(app).attempts(Mode.FAST).first().model)
    }

    @Test fun `explicit local pick beats the lfm3 default`() {
        seed(groqAllamFirst(), local("qwen2.5-coder-1.5b")); setMode("AUTO"); setPick("local:qwen2.5-coder-1.5b"); lfm3File()
        val list = ChatViewModel(app).attempts(Mode.FAST)
        assertEquals(1, list.size)
        assertEquals("qwen2.5-coder-1.5b", list.first().model)
    }

    // (d) LFM3 NOT downloaded → honest cloud fallback, never allam-first, never labelled LFM3.

    @Test fun `lfm3 not downloaded falls back to cloud without allam first`() {
        seed(groqAllamFirst()); setMode("AUTO"); setPick("")
        val models = ChatViewModel(app).attempts(Mode.FAST).map { it.model }
        assertFalse("lfm3-1.7b" in models)
        assertNotEquals("allam-2-7b", models.first())
        assertEquals("openai/gpt-oss-20b", models.first())
    }

    @Test fun `chip names lfm3 only when it is set up, else stays Auto`() {
        val withLfm3 = ModelChoice.buildChooserEntries(listOf(groqAllamFirst(), local("lfm3-1.7b")), emptyList())
        assertEquals("LFM3-1.7B", ModelChoice.displayLabelFromEntries("", "AUTO", withLfm3))
        val withoutLfm3 = ModelChoice.buildChooserEntries(listOf(groqAllamFirst()), emptyList())
        assertEquals("Auto", ModelChoice.displayLabelFromEntries("", "AUTO", withoutLfm3))
    }

    // (e) migration: stored AUTO becomes the LFM3-first default; explicit modes/picks are preserved.

    @Test fun `stored AUTO migrates, explicit modes and picks are preserved`() {
        assertTrue(AppSettings(mode = "AUTO", pickedModel = "").prefersLfm3Default(true))
        assertFalse(AppSettings(mode = "AUTO", pickedModel = "").prefersLfm3Default(false))
        assertFalse(AppSettings(mode = "LOCAL", pickedModel = "").prefersLfm3Default(true))
        assertFalse(AppSettings(mode = "SERVER", pickedModel = "").prefersLfm3Default(true))
        assertFalse(AppSettings(mode = "FREE", pickedModel = "").prefersLfm3Default(true))
        assertFalse(AppSettings(mode = "FAST", pickedModel = "").prefersLfm3Default(true))
        assertFalse(AppSettings(mode = "EXPERT", pickedModel = "").prefersLfm3Default(true))
        assertFalse(AppSettings(mode = "AUTO", pickedModel = "api:c-groq:allam-2-7b").prefersLfm3Default(true))
    }

    @Test fun `explicit LOCAL mode still uses only the local model even with lfm3 downloaded`() {
        seed(groqAllamFirst(), local("lfm3-1.7b")); setMode("LOCAL"); setPick(""); lfm3File()
        val list = ChatViewModel(app).attempts(Mode.AUTO)
        assertEquals(listOf("Local model"), list.map { it.provider })
        assertEquals(listOf("lfm3-1.7b"), list.map { it.model })
    }

    @Test fun `explicit SERVER mode still uses only the server even with lfm3 downloaded`() {
        seed(groqAllamFirst(), local("lfm3-1.7b"), server()); setMode("SERVER"); setPick(""); lfm3File()
        val list = ChatViewModel(app).attempts(Mode.AUTO)
        assertEquals(listOf("My server"), list.map { it.provider })
    }

    // ACTION turns stay cloud-first: the small model cannot drive tools.

    @Test fun `action turns stay cloud first even with lfm3 downloaded`() {
        seed(groqAllamFirst(), local("lfm3-1.7b")); setMode("AUTO"); setPick(""); lfm3File()
        val models = ChatViewModel(app).attempts(Mode.FAST, forAction = true).map { it.model }
        assertNotEquals("lfm3-1.7b", models.first())
        assertNotEquals("allam-2-7b", models.first())
        assertTrue("lfm3-1.7b" in models)   // still the backup after the keys
        val autoModels = ChatViewModel(app).attempts(Mode.AUTO, forAction = true).map { it.model }
        assertNotEquals("lfm3-1.7b", autoModels.first())
    }
}
