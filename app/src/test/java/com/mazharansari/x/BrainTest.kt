package com.mazharansari.x.agent

import com.mazharansari.x.data.StepKind
import com.mazharansari.x.llm.ToolSpec
import com.mazharansari.x.llm.UserItem
import com.mazharansari.x.local.LocalAgentRunner
import com.mazharansari.x.local.LocalGeneration
import com.mazharansari.x.local.LocalToolUse
import com.mazharansari.x.skills.SkillRepo
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

// BRAIN (2026-11-20) — the Hermes-level brain layer, model-independent.
// Pure-logic tests for Brain (claims, correction tracker, step check, verify
// gate) plus LocalAgentRunner integration tests with a fake generator and
// fake tools: no model, no network, no Android framework.
class BrainTest {

    private fun ctx(dir: File): ToolContext =
        ToolContext(Workspace(File(dir, "ws")), SkillRepo(null, File(dir, "skills")))

    private fun tmpDir(): File = Files.createTempDirectory("axion-brain-test").toFile()

    private class FakeFailReadTool(var calls: Int = 0) : Tool {
        override val spec = ToolSpec("read_file", "Read a file in the workspace.", schema { str("path", "Path to read") })
        override fun label(args: JsonObject) = StepLabel(StepKind.READ, "Read", args.s("path").orEmpty())
        override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
            calls++
            return ToolOutput("not found: ${args.s("path")}", isError = true)
        }
    }

    private class FakeWriteTool(var calls: Int = 0) : Tool {
        override val spec = ToolSpec("write_file", "Write a file in the workspace.", schema { str("path", "Path to write"); str("content", "Content") })
        override fun label(args: JsonObject) = StepLabel(StepKind.WRITE, "Write", args.s("path").orEmpty())
        override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
            calls++
            val f = ctx.workspace.resolve(args.s("path").orEmpty())
            f.parentFile?.mkdirs()
            f.writeText(args.s("content").orEmpty())
            return ToolOutput("Wrote ${args.s("path")}")
        }
    }

    // -- Guidance ------------------------------------------------------------

    @Test fun brainGuidanceCoversPlanCheckCorrectVerify() {
        val g = Brain.BRAIN_GUIDANCE
        assertTrue(g.contains("PLAN FIRST"))
        assertTrue(g.contains("what success looks like"))
        assertTrue(g.contains("CHECK EACH STEP"))
        assertTrue(g.contains("Never repeat the exact input"))
        assertTrue(g.contains("2 changed approaches"))
        assertTrue(g.contains("VERIFY BEFORE YOU SAY DONE"))
        assertTrue(g.contains("could not verify"))
        assertTrue(g.contains("ONE short question"))
    }

    @Test fun brainGuidanceFitsAntigravitySystemPromptCap() {
        // AgyEngine.promptOf carries sys.take(3000) and ~1650 chars precede
        // this block in the Agent system prompt — over ~1300 chars the tail
        // (the verify rule) would be cut off for every Antigravity run.
        assertTrue("BRAIN_GUIDANCE is ${Brain.BRAIN_GUIDANCE.length} chars, must stay <= 1300",
            Brain.BRAIN_GUIDANCE.length <= 1300)
    }

    @Test fun smallModelGuidanceIsShorterAndKeepsTheSameRules() {
        val small = Brain.SMALL_MODEL_GUIDANCE
        assertTrue(small.length < Brain.BRAIN_GUIDANCE.length)
        assertTrue(small.contains("PLAN:"))
        assertTrue(small.contains("ONE TOOL PER TURN"))
        assertTrue(small.contains("never the same call twice", ignoreCase = true))
        assertTrue(small.contains("could not verify", ignoreCase = true))
    }

    // -- Completion claims -----------------------------------------------------

    @Test fun detectClaimsFindsFileBuildDownloadAndRomanUrdu() {
        val file = Brain.detectClaims("PDF ban gaya, outputs/report.pdf ready hai")
        assertTrue(file.any { it.kind == Brain.ClaimKind.FILE && it.path == "outputs/report.pdf" })
        val dl = Brain.detectClaims("download ho gaya, file mil gayi")
        assertTrue(dl.any { it.kind == Brain.ClaimKind.DOWNLOAD })
        val build = Brain.detectClaims("The app is built and ready")
        assertTrue(build.any { it.kind == Brain.ClaimKind.BUILD })
        val fix = Brain.detectClaims("file fix ho gaya, ab theek hai")
        assertTrue(fix.any { it.kind == Brain.ClaimKind.FIX })
    }

    @Test fun detectClaimsIgnoresPlainChat() {
        assertTrue(Brain.detectClaims("done, thanks!").isEmpty())
        assertTrue(Brain.detectClaims("I am done thinking about it").isEmpty())
        assertTrue(Brain.detectClaims("sounds good, let's do it tomorrow").isEmpty())
    }

    // -- Self-correction tracker ----------------------------------------------

    @Test fun trackerRefusesIdenticalRepeatButAllowsChangedArgs() {
        val t = Brain.CorrectionTracker()
        t.record("read_file", """{"path": "a.txt"}""", ok = false, error = "not found")
        assertTrue(t.isIdenticalRepeat("read_file", """{"path": "a.txt"}"""))
        assertFalse(t.isIdenticalRepeat("read_file", """{"path": "b.txt"}"""))
        assertEquals(1, t.correctionsUsed("read_file"))
        assertFalse(t.shouldGiveUp("read_file"))
        t.record("read_file", """{"path": "b.txt"}""", ok = false, error = "not found")
        assertEquals(2, t.correctionsUsed("read_file"))
        assertTrue(t.shouldGiveUp("read_file"))
        assertTrue(t.triedSummary().contains("read_file"))
    }

    @Test fun trackerSuccessDoesNotCountAsACorrection() {
        val t = Brain.CorrectionTracker()
        t.record("read_file", """{"path": "a.txt"}""", ok = true)
        assertFalse(t.isIdenticalRepeat("read_file", """{"path": "a.txt"}"""))
        assertEquals(0, t.correctionsUsed("read_file"))
        assertFalse(t.shouldGiveUp("read_file"))
    }

    @Test fun trackerGivesUpOnRepeatedIdenticalReissues() {
        val t = Brain.CorrectionTracker()
        t.record("run_command", """{"command": "x"}""", ok = false, error = "boom")
        assertFalse(t.shouldGiveUp("run_command"))
        t.noteIdenticalRepeat("run_command", """{"command": "x"}""")
        assertFalse(t.shouldGiveUp("run_command"))
        t.noteIdenticalRepeat("run_command", """{"command": "x"}""")
        assertTrue(t.shouldGiveUp("run_command"))
    }

    // -- Cheap per-step check ---------------------------------------------------

    @Test fun stepCheckFlagsErrorsEmptySuccessAndNonZeroExit() {
        assertFalse(Brain.stepProducedWhatWasNeeded(ToolOutput("boom", isError = true)))
        assertFalse(Brain.stepProducedWhatWasNeeded(ToolOutput("", isError = false)))
        assertTrue(Brain.isEmptySuccess(ToolOutput("")))
        assertFalse(Brain.isEmptySuccess(ToolOutput("some output")))
        assertFalse(Brain.stepProducedWhatWasNeeded(ToolOutput("[exit code 2]\nno such file", exitCode = 2)))
        assertTrue(Brain.stepProducedWhatWasNeeded(ToolOutput("wrote 42 lines", exitCode = 0)))
    }

    // -- Verify gate --------------------------------------------------------------

    @Test fun verifyGateBlocksFalseDoneAndPassesWithToolEvidence() {
        val gate = Brain.VerifyGate()
        val claim = "report.pdf ban gaya, file ready hai"
        val blocked = gate.check(claim)
        assertTrue(blocked is Brain.Decision.NeedsVerification)
        gate.noteToolCall("write_file", buildJsonObject { put("path", "outputs/report.pdf") }, ToolOutput("Wrote outputs/report.pdf"))
        assertTrue(gate.check(claim) is Brain.Decision.Allowed)
    }

    @Test fun verifyGateLiveFileCheckPassesWithoutAToolCall() {
        val dir = tmpDir()
        val wsRoot = File(dir, "ws").also { File(it, "outputs").mkdirs() }
        File(wsRoot, "outputs/report.pdf").writeText("fake pdf bytes")
        val gate = Brain.VerifyGate { p -> File(wsRoot, p).let { it.isFile && it.length() > 0 } }
        assertTrue(gate.check("outputs/report.pdf ban gaya") is Brain.Decision.Allowed)
        assertTrue(gate.check("outputs/other.pdf ban gaya") is Brain.Decision.NeedsVerification)
    }

    @Test fun verifyGateBuildClaimNeedsBuildEvidence() {
        val gate = Brain.VerifyGate()
        assertTrue(gate.check("app ban gaya") is Brain.Decision.NeedsVerification)
        gate.noteToolCall("run_command", buildJsonObject { put("command", "ls") }, ToolOutput("ok", exitCode = 0))
        assertTrue(gate.check("app ban gaya") is Brain.Decision.NeedsVerification) // plain ls is not a build
        gate.noteToolCall("run_command", buildJsonObject { put("command", "./gradlew assembleRelease") }, ToolOutput("BUILD SUCCESSFUL", exitCode = 0))
        assertTrue(gate.check("app ban gaya") is Brain.Decision.Allowed)
    }

    @Test fun verifyGateFailedToolCallIsNotEvidence() {
        val gate = Brain.VerifyGate()
        gate.noteToolCall("write_file", buildJsonObject { put("path", "outputs/report.pdf") }, ToolOutput("disk full", isError = true))
        assertTrue(gate.check("report.pdf ban gaya") is Brain.Decision.NeedsVerification)
    }

    @Test fun verificationNoteAndDisclaimerNameTheClaim() {
        val claims = Brain.detectClaims("PDF ban gaya, outputs/report.pdf ready hai")
        assertTrue(Brain.verificationNote(claims).contains("VERIFY"))
        val d = Brain.unverifiedDisclaimer(claims)
        assertTrue(d.contains("could not verify"))
        assertTrue(d.contains("ban gaya"))
    }

    // -- Plan shape -----------------------------------------------------------------

    @Test fun planShapeNeedsStepsAndASuccessLine() {
        assertTrue(Brain.isWellFormedPlan(listOf("Read file", "Write page"), "done means the file exists"))
        assertFalse(Brain.isWellFormedPlan(listOf("only one step"), "done means x"))
        assertFalse(Brain.isWellFormedPlan(listOf("a", "b"), ""))
        assertFalse(Brain.isWellFormedPlan((1..8).map { "step $it" }, "done means x"))
    }

    // -- Local prompt carries the brain -----------------------------------------------

    @Test fun localPromptCarriesBrainDisciplineAndStaysShort() {
        val specs = listOf(
            ToolSpec("read_file", "Read a file.", buildJsonObject { put("type", "object") }),
            ToolSpec("run_command", "Run a command.", buildJsonObject { put("type", "object") }),
        )
        val prompt = LocalToolUse.buildToolPrompt(specs)
        assertTrue(prompt.contains("PLAN:"))
        assertTrue(prompt.contains("ONE TOOL PER TURN"))
        assertTrue(prompt.contains("could not verify", ignoreCase = true))
        assertTrue("prompt must stay short for a 4k model: ${prompt.length}", prompt.length < 4000)
    }

    // -- Local runner: identical repeat, verify gate, termination ----------------------

    @Test fun localRunnerDoesNotRunIdenticalFailingCallTwice() = runBlocking {
        val read = FakeFailReadTool()
        val script = listOf(
            """{"tool": "read_file", "arguments": {"path": "missing.txt"}}""",
            """{"tool": "read_file", "arguments": {"path": "missing.txt"}}""",
            """{"answer": "I could not read the file."}""",
        )
        var n = 0
        val runner = LocalAgentRunner(
            generate = { LocalGeneration(script[n++], 100, 10) },
            tools = listOf(read), ctx = ctx(tmpDir()),
        )
        val r = runner.run(emptyList(), UserItem("What does missing.txt say?"))
        assertEquals(1, read.calls) // the identical re-issue was refused, never executed
        assertEquals(3, r.generations.size)
        assertEquals("I could not read the file.", r.answer)
    }

    @Test fun localRunnerVerifyGateBlocksFalseDone() = runBlocking {
        val script = listOf(
            """{"answer": "PDF ban gaya, outputs/report.pdf ready hai"}""",
            """{"answer": "PDF ban gaya, outputs/report.pdf ready hai"}""",
        )
        var n = 0
        val runner = LocalAgentRunner(
            generate = { LocalGeneration(script[n++], 100, 10) },
            tools = emptyList(), ctx = ctx(tmpDir()),
        )
        val r = runner.run(emptyList(), UserItem("Make me a PDF"))
        assertEquals(2, r.generations.size) // one verification round, then the honest note
        assertTrue(r.answer.contains("could not verify"))
    }

    @Test fun localRunnerVerifiedClaimPassesThrough() = runBlocking {
        val write = FakeWriteTool()
        val script = listOf(
            """{"tool": "write_file", "arguments": {"path": "outputs/notes.txt", "content": "hi"}}""",
            """{"answer": "The notes file ban gaya"}""",
        )
        var n = 0
        val runner = LocalAgentRunner(
            generate = { LocalGeneration(script[n++], 100, 10) },
            tools = listOf(write), ctx = ctx(tmpDir()),
        )
        val r = runner.run(emptyList(), UserItem("Write notes.txt for me"))
        assertEquals(1, write.calls)
        assertEquals("The notes file ban gaya", r.answer) // verified by the write — no disclaimer
    }

    @Test fun localRunnerTerminatesOnEndlessFailingCalls() = runBlocking {
        val read = FakeFailReadTool()
        val runner = LocalAgentRunner(
            generate = { LocalGeneration("""{"tool": "read_file", "arguments": {"path": "missing.txt"}}""", 100, 10) },
            tools = listOf(read), ctx = ctx(tmpDir()), maxSteps = 6,
        )
        val r = runner.run(emptyList(), UserItem("What does missing.txt say?"))
        assertEquals(1, read.calls)
        assertTrue("run must end within the round bound: ${r.generations.size}", r.generations.size <= 7)
        assertTrue(r.answer.contains("could not finish"))
        assertTrue(r.answer.contains("read_file"))
    }
}
