package com.mazharansari.x

import androidx.test.core.app.ApplicationProvider
import com.mazharansari.x.agent.*
import com.mazharansari.x.data.*
import com.mazharansari.x.llm.*
import com.mazharansari.x.local.LocalAgentRunner
import com.mazharansari.x.local.LocalGeneration
import com.mazharansari.x.skills.SkillRepo
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Memory fix (2026-11-20): the wiring tests. The store itself always worked — these prove the
 * path around it: the agent is offered the memory tools for the short utterances that teach it
 * things, the saved snapshot rides into every chat start, the local-model loop sees memory too,
 * a tool save survives a store reload (restart), an explicit "remember this" is saved even when
 * the model does not call the tool, and file imports land as memories without blowing budgets.
 * The small-talk, local-loop and reload tests FAIL on the old code (empty tool list / no memory
 * in the local prompt / file write still in flight) and pass on the new code.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = AxionApp::class)
class MemoryWiringTest {
    private val app get() = ApplicationProvider.getApplicationContext<AxionApp>()

    private fun freshStore(): MemoryStore {
        File(app.filesDir, "memory.json").delete()
        File(app.filesDir, "memory.json.tmp").delete()
        return MemoryStore(app)
    }

    private fun workspace() = Workspace(File(app.filesDir, "workspace"))
    private fun skills() = SkillRepo(app, File(app.filesDir, "skills"))
    private fun toolCtx(memory: MemoryStore?) = ToolContext(workspace(), skills(), memory = memory)

    private class Rec : AgentSink {
        val text = StringBuilder()
        override fun text(delta: String) { text.append(delta) }
        override fun reasoning(delta: String) {}
        override fun textToThoughts() {}
        override fun step(step: Step) {}
        override fun artifact(a: Artifact) {}
        override fun model(label: String) {}
    }

    private fun fakeClient(seen: MutableList<LlmRequest>, reply: String = "Noted."): LlmClient =
        object : LlmClient {
            override fun stream(req: LlmRequest) = flow {
                seen += req
                emit(LlmEvent.Text(reply))
                emit(LlmEvent.Finished("stop"))
            }
        }

    private fun agent(memory: MemoryStore?) = Agent(
        workspace(), skills(), baseTools = Agent.defaultTools(), extraTools = { emptyList() },
        toolSwitches = { emptyMap() }, memory = memory)

    private fun attempt(seen: MutableList<LlmRequest>) = Attempt("Fake", "m", fakeClient(seen))

    @Test fun shortMemoryStatementKeepsMemoryTools() = runBlocking {
        // OLD CODE: "mera naam … hai" is 25 chars / 4 words, so isSmallTalk() was true and the
        // CHAT branch offered NO tools — memory_add never existed for the canonical learning
        // utterance. NEW CODE: the memory-statement carve-out keeps the chat tools.
        val seen = mutableListOf<LlmRequest>()
        agent(freshStore()).run(emptyList(), UserItem("mera naam Mazhar Ansari hai"),
            { listOf(attempt(seen)) }, Rec(), intent = Intent.CHAT)
        val names = seen.single().tools.map { it.name }
        assertTrue("memory_add must be offered for a short memory statement, got $names", "memory_add" in names)
        assertTrue("memory_read must be offered, got $names", "memory_read" in names)
    }

    @Test fun plainGreetingStillGetsNoTools() = runBlocking {
        // The carve-out is narrow: a plain greeting stays tool-free (fast + cheap, as before).
        val seen = mutableListOf<LlmRequest>()
        agent(freshStore()).run(emptyList(), UserItem("hi bhai"),
            { listOf(attempt(seen)) }, Rec(), intent = Intent.CHAT)
        assertTrue("a greeting must still get no tools", seen.single().tools.isEmpty())
    }

    @Test fun memoryQuestionIsNotTreatedAsSaveInstruction() {
        assertFalse(isMemoryStatement("do you remember what we decided?"))
        assertFalse(isMemoryStatement("hi bhai"))
        assertTrue(isMemoryStatement("mera naam Mazhar Ansari hai"))
        assertTrue(isMemoryStatement("yaad rakhna, I prefer short answers"))
        assertTrue(isMemoryStatement("remember this: the shop opens at 9am"))
    }

    @Test fun chatStartPromptContainsSavedMemory() = runBlocking {
        // The READ side: whatever is saved rides in the system prompt of every chat, even a
        // small-talk one (the snapshot was already injected for small talk on the old code —
        // this guards the read path against regressions).
        val store = freshStore()
        assertNull(store.add(MemoryTarget.USER, "Prefers short answers in Roman Urdu"))
        val seen = mutableListOf<LlmRequest>()
        agent(store).run(emptyList(), UserItem("what do you remember about me"),
            { listOf(attempt(seen)) }, Rec(), intent = Intent.CHAT)
        val sys = (seen.single().items[0] as SystemItem).text
        assertTrue("system prompt must contain the saved memory", sys.contains("Prefers short answers in Roman Urdu"))
    }

    @Test fun memoryAddToolSavesAndSurvivesReload() = runBlocking {
        // OLD CODE: the tool returned success while the file write was still queued on an IO
        // dispatcher — a reload (restart) immediately afterwards read the stale file and the
        // entry was gone. NEW CODE: the write is synchronous, so it is on disk when add() returns.
        val store = freshStore()
        val args = buildJsonObject { put("target", "memory"); put("content", "Clawora prices include delivery") }
        val out = MemoryAddTool().run(args, toolCtx(store))
        assertFalse("tool must succeed: ${out.forModel}", out.isError)
        assertTrue(store.entries(MemoryTarget.MEMORY).contains("Clawora prices include delivery"))
        assertTrue("memory.json must contain the entry right after the tool returns",
            File(app.filesDir, "memory.json").readText().contains("Clawora prices include delivery"))
        val reloaded = MemoryStore(app)
        assertTrue("a fresh store (restart) must see the entry",
            reloaded.entries(MemoryTarget.MEMORY).contains("Clawora prices include delivery"))
    }

    @Test fun memoryToolsRefuseWithoutStore() = runBlocking {
        // Documents the null-store failure mode: every memory tool errors when the store was
        // never passed in, so a surface built without it can never save or read.
        val args = buildJsonObject { put("target", "user"); put("content", "Prefers short answers") }
        val out = MemoryAddTool().run(args, toolCtx(null))
        assertTrue(out.isError)
        assertTrue(out.forModel.contains("not available"))
        val read = MemoryReadTool().run(buildJsonObject { }, toolCtx(null))
        assertTrue(read.isError)
    }

    @Test fun memoryToolsRegistered() {
        val names = Agent.defaultTools().map { it.spec.name }
        listOf("memory_add", "memory_replace", "memory_remove", "memory_read").forEach {
            assertTrue("$it must be a default tool", it in names)
            assertTrue("$it must be on by default", ToolCatalog.isEnabled(it, emptyMap()))
            assertEquals("$it must belong to the learning group", "learning", ToolCatalog.groupOf(it)?.id)
        }
    }

    @Test fun localRunnerPromptContainsMemoryAndTools() = runBlocking {
        // OLD CODE: LOCAL-mode action turns ran LocalAgentRunner, whose ALLOWED_TOOLS had no
        // memory tools and whose prompt had no snapshot — local chats could neither save nor
        // read memory. NEW CODE: both ride in the local loop.
        val store = freshStore()
        store.add(MemoryTarget.USER, "Prefers short answers in Roman Urdu")
        val seen = mutableListOf<List<ChatItem>>()
        val runner = LocalAgentRunner(
            generate = { items -> seen += items; LocalGeneration("{\"answer\": \"ok\"}", 10, 10) },
            tools = Agent.defaultTools(), ctx = toolCtx(store))
        val result = runner.run(emptyList(), UserItem("do the thing"))
        assertEquals("ok", result.answer)
        val sys = (seen.single()[0] as SystemItem).text
        assertTrue("local prompt must contain the saved memory", sys.contains("Prefers short answers in Roman Urdu"))
        assertTrue("local prompt must offer memory_add", sys.contains("memory_add"))
    }

    @Test fun savePassExtractsExplicitRemember() {
        val saves = MemorySavePass.extract("yaad rakhna: my shop opens at 9am")
        assertEquals(1, saves.size)
        assertEquals(MemoryTarget.MEMORY, saves[0].target)
        assertTrue(saves[0].content.contains("shop opens at 9am"))
        assertEquals(
            listOf(MemorySavePass.Save(MemoryTarget.USER, "Name: Mazhar Ansari")),
            MemorySavePass.extract("mera naam Mazhar Ansari hai"))
        assertEquals(
            MemoryTarget.USER,
            MemorySavePass.extract("I prefer short answers").single().target)
        assertTrue("small talk saves nothing", MemorySavePass.extract("hello, kya hal hai").isEmpty())
        assertTrue("a question saves nothing", MemorySavePass.extract("do you remember what we decided?").isEmpty())
        assertTrue("secrets are never saved", MemorySavePass.extract("remember my password is hunter2").isEmpty())
        assertTrue("reminiscing saves nothing", MemorySavePass.extract("remember when we launched the shop").isEmpty())
    }

    @Test fun savePassSavesThroughStore() {
        val store = freshStore()
        assertEquals(1, MemorySavePass.saveAll(store, "yaad rakhna: delivery is Rs 250"))
        assertTrue(store.entries(MemoryTarget.MEMORY).contains("delivery is Rs 250"))
        assertEquals("a duplicate re-save is a no-op", 0, MemorySavePass.saveAll(store, "yaad rakhna: delivery is Rs 250"))
    }

    private fun zipBytes(files: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, text) in files) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test fun zipAndTextFileImportSavesMemories() {
        val store = freshStore()
        val zip = zipBytes(mapOf(
            "notes.txt" to "Shop opens at 9am.\nDelivery is Rs 250.\n",
            "doc.md" to "# Prices\nSingle Rs 1599.\n"))
        val rep = MemoryFileImport.importInto(store, MemoryTarget.MEMORY, "notes.zip", zip)
        assertTrue("zip import must save entries, got $rep", rep.saved >= 2)
        assertTrue(store.entries(MemoryTarget.MEMORY).any { it.contains("Shop opens at 9am") })
        assertTrue(store.entries(MemoryTarget.MEMORY).any { it.contains("Single Rs 1599") })
        val txt = MemoryFileImport.importInto(store, MemoryTarget.USER, "prefs.txt",
            "Prefers short answers.".toByteArray(Charsets.UTF_8))
        assertEquals(1, txt.saved)
        assertTrue(store.entries(MemoryTarget.USER).contains("Prefers short answers."))
    }

    @Test fun oversizedFileDoesNotBlowBudget() {
        val store = freshStore()
        val big = "This is a very long fact about the shop and its products. ".repeat(400)
            .toByteArray(Charsets.UTF_8)
        val rep = MemoryFileImport.importInto(store, MemoryTarget.MEMORY, "big.txt", big)
        assertTrue("some entries must save, got $rep", rep.saved > 0)
        assertTrue("the rest must be reported as not fitting, got $rep", rep.notSavedFull > 0)
        assertTrue(store.usedChars(MemoryTarget.MEMORY) <= MemoryStore.MEMORY_CHAR_BUDGET)
        assertTrue(store.entries(MemoryTarget.MEMORY).all { it.length <= MemoryStore.ENTRY_MAX_CHARS })
    }

    @Test fun corruptFileLeavesMemoriesIntact() {
        val store = freshStore()
        store.add(MemoryTarget.MEMORY, "Keep this fact.")
        val garbage = ByteArray(512) { (it * 37 % 256).toByte() }
        val rep1 = MemoryFileImport.importInto(store, MemoryTarget.MEMORY, "broken.bin", garbage)
        assertEquals(0, rep1.saved)
        assertTrue("binary junk must be reported unreadable, got $rep1", rep1.unreadable.isNotEmpty())
        val rep2 = MemoryFileImport.importInto(store, MemoryTarget.MEMORY, "bad.zip",
            "PK this is not a zip at all".toByteArray(Charsets.UTF_8))
        assertEquals(0, rep2.saved)
        assertTrue(rep2.unreadable.isNotEmpty())
        assertEquals(listOf("Keep this fact."), store.entries(MemoryTarget.MEMORY))
    }
}
