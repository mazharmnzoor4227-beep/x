package com.mazharansari.x

import android.app.Service
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.mazharansari.x.linux.BackgroundJobs
import com.mazharansari.x.ui.chat.BotRunningBanner
import com.mazharansari.x.ui.chat.botRunningSubtitle
import com.mazharansari.x.ui.theme.AxionTheme
import com.mazharansari.x.work.BotRunInfo
import com.mazharansari.x.work.BotRunService
import com.mazharansari.x.work.BotRunState
import com.mazharansari.x.work.RunRegistry
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Background bot runs: the foreground service lifecycle, run-id keying
 * (a run survives leaving its chat), the run-to-Linux-job links, and the
 * running-state banner. Pure-logic tests run under Robolectric like the
 * other screen tests; the service test drives onStartCommand directly.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi", application = AxionApp::class)
class BotBackgroundTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Before fun setUp() {
        BotRunService.clearAllForTest()
    }

    // ── BotRunService: the bot-run map, keyed by run id ─────────────────────

    @Test fun noteStartedAndFinishedIsIdempotent() {
        val info = BotRunInfo("run-1", "bot-1", "Aria", "conv-1", "Draft the menu", 1000L)
        assertFalse(BotRunService.isBotRun("run-1"))
        BotRunService.noteRunStarted(info)
        assertTrue(BotRunService.isBotRun("run-1"))
        assertEquals(info, BotRunService.info("run-1"))
        assertEquals(listOf(info), BotRunService.snapshot())
        BotRunService.noteRunFinished("run-1")
        assertFalse(BotRunService.isBotRun("run-1"))
        BotRunService.noteRunFinished("run-1")
        assertTrue(BotRunService.snapshot().isEmpty())
    }

    @Test fun notificationTitleSingleAndMany() {
        val one = listOf(BotRunInfo("run-1", "b1", "Aria", "c1", "Draft the menu", 1))
        assertEquals("Aria is working", BotRunService.notificationTitle(one))
        val two = one + BotRunInfo("run-2", "b2", "Boris", "c2", "Plan", 2)
        assertEquals("2 bots working", BotRunService.notificationTitle(two))
    }

    @Test fun notificationTextListsRunsWithWorkerCounts() {
        val a = BotRunInfo("run-1", "b1", "Aria", "c1", "Draft the menu", 1)
        val b = BotRunInfo("run-2", "b2", "Boris", "c2", "", 2)
        assertEquals(
            "Aria — Draft the menu · 2 workers\nBoris — Working",
            BotRunService.notificationText(listOf(a, b), mapOf("c1" to 2)),
        )
        assertEquals("Aria — Draft the menu · 1 worker", BotRunService.summaryLine(a, 1))
        assertEquals("Boris — Working", BotRunService.summaryLine(b, 0))
    }

    @Test fun pruneDeadKeepsLiveAndFreshNotes() {
        val live = BotRunInfo("run-1", null, "Aria", "c1", "T", 1000)
        val fresh = BotRunInfo("run-2", null, "Boris", "c2", "T", 9_999)
        val stale = BotRunInfo("run-3", null, "Cleo", "c3", "T", 0)
        val kept = BotRunService.pruneDead(listOf(live, fresh, stale), setOf("run-1"), nowMs = 10_000)
        assertEquals(listOf("run-1", "run-2"), kept.map { it.runId })
    }

    // ── Service lifecycle: a restart with no live runs must not linger ──────

    @Test fun staleStartStopsInsteadOfLingering() {
        val app = ApplicationProvider.getApplicationContext<AxionApp>()
        val svc = Robolectric.buildService(BotRunService::class.java).create().get()
        val intent = Intent(app, BotRunService::class.java).setAction(BotRunService.ACTION_REFRESH)
        val result = svc.onStartCommand(intent, 0, 1)
        assertEquals(Service.START_NOT_STICKY, result)
        svc.onDestroy()
    }

    @Test fun stopAllWithNoRunsIsNotSticky() {
        val app = ApplicationProvider.getApplicationContext<AxionApp>()
        val svc = Robolectric.buildService(BotRunService::class.java).create().get()
        val intent = Intent(app, BotRunService::class.java).setAction(BotRunService.ACTION_STOP_ALL)
        val result = svc.onStartCommand(intent, 0, 1)
        assertEquals(Service.START_NOT_STICKY, result)
        svc.onDestroy()
    }

    // ── RunRegistry: runs are keyed by run id, not by the open screen ───────

    @Test fun runsSurviveLeavingTheirChatAndStopOneAtATime() {
        val r = RunRegistry()
        val botJob = Job()
        val otherJob = Job()
        r.register("run-bot-1", RunRegistry.Kind.ASK, "conv-bot", "Aria", botJob)
        r.register("run-other-1", RunRegistry.Kind.ASK, "conv-other", "Other", otherJob)
        // Leaving the bot chat (its screen goes away) touches nothing here.
        assertTrue(r.isActive("run-bot-1"))
        assertEquals("run-bot-1", r.forOwner("conv-bot")?.id)
        assertEquals("run-other-1", r.forOwner("conv-other")?.id)
        // Stopping the bot run leaves the other run in flight.
        botJob.cancel()
        assertFalse(r.isActive("run-bot-1"))
        assertTrue(r.isActive("run-other-1"))
        assertEquals(listOf("run-other-1"), r.active.value.map { it.id })
        otherJob.cancel()
        assertTrue(r.active.value.isEmpty())
    }

    // ── BackgroundJobs: Linux jobs belong to a run id ───────────────────────

    @Test fun jobRunLinksTrackAndClearByRunId() {
        val links = BackgroundJobs.JobRunLinks()
        links.link(3, "run-a")
        links.link(5, "run-a")
        links.link(7, "run-b")
        assertEquals(listOf(3, 5), links.jobsForRun("run-a"))
        assertEquals("run-b", links.runIdForJob(7))
        assertEquals(null, links.runIdForJob(99))
        links.unlink(3)
        assertEquals(listOf(5), links.jobsForRun("run-a"))
        links.clearRun("run-a")
        assertTrue(links.jobsForRun("run-a").isEmpty())
        assertEquals("run-b", links.runIdForJob(7))
    }

    // ── BotRunningBanner: subtitle + rendering + Stop ───────────────────────

    @Test fun bannerSubtitleShowsTaskWorkersAndElapsed() {
        val withWorkers = BotRunState("run-1", "b1", "Aria", "Draft the menu", "c1", 1000L, workerCount = 2)
        assertEquals("Draft the menu · 2 workers · 01:05", botRunningSubtitle(withWorkers, nowMs = 66_000L))
        val plain = BotRunState("run-2", "b2", "Boris", "", "c2", 1000L)
        assertEquals("Working · 00:05", botRunningSubtitle(plain, nowMs = 6_000L))
        val oneWorker = withWorkers.copy(workerCount = 1)
        assertEquals("Draft the menu · 1 worker · 01:05", botRunningSubtitle(oneWorker, nowMs = 66_000L))
    }

    @Test fun bannerShowsBotNameTaskAndStopCallsBack() {
        var stopped: String? = null
        val state = BotRunState("run-1", "bot-1", "Aria", "Draft the menu", "conv-1", System.currentTimeMillis() - 65_000, workerCount = 2)
        compose.setContent {
            AxionTheme {
                BotRunningBanner(state = state, onStop = { stopped = state.runId })
            }
        }
        compose.onNodeWithText("Aria is working").assertIsDisplayed()
        compose.onNodeWithText("Draft the menu", substring = true).assertIsDisplayed()
        compose.onNodeWithText("2 workers", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Stop bot run").performClick()
        assertEquals("run-1", stopped)
    }
}
