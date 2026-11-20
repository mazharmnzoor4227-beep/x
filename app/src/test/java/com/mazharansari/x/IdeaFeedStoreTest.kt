package com.mazharansari.x

import androidx.test.core.app.ApplicationProvider
import com.mazharansari.x.data.Idea
import com.mazharansari.x.data.IdeaFeed
import com.mazharansari.x.data.IdeasStore
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Idea store feed — store-level rules (IdeasStore, Robolectric: the store
 * needs a Context). The worker's failure behaviour is covered here at the
 * level the screen reads: a recorded failure leaves every idea untouched
 * and stores the reason, and the feed starts empty with no fake ideas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = AxionApp::class)
class IdeaFeedStoreTest {
    private val app get() = ApplicationProvider.getApplicationContext<AxionApp>()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val now = System.currentTimeMillis()
    private val day = 24L * 3_600_000L

    @Before fun cleanStores() {
        File(app.filesDir, "ideas.json").delete()
        File(app.filesDir, "idea-feed-state.json").delete()
    }

    @Test fun feedStartsEmptyWithNoFakeIdeas() {
        val ideas = IdeasStore(app)
        assertTrue(ideas.ideas.value.isEmpty())
        assertFalse("no refresh has ever succeeded", ideas.refreshState.value.everSucceeded)
        assertEquals(0, ideas.refreshState.value.lastSuccessAtMs)
    }

    @Test fun userAndStoreIdeasStaySeparateBySource() {
        val ideas = IdeasStore(app)
        ideas.add("Solar charger for the shop", "You")
        val added = ideas.addStoreIdeas(
            listOf(IdeaFeed.ParsedIdea("App", "Build a masala price tracker app for the shop in Karachi")), now)
        assertEquals(1, added)
        assertEquals(2, ideas.ideas.value.size)
        assertEquals(1, ideas.ideas.value.count { it.source == IdeaFeed.SOURCE_USER })
        assertEquals(1, ideas.ideas.value.count { it.source == IdeaFeed.SOURCE_STORE })
        assertEquals("App", ideas.ideas.value.first { it.source == IdeaFeed.SOURCE_STORE }.area)
    }

    @Test fun agentSavedUserIdeasNeverBecomeStoreIdeas() {
        // save_idea writes through add(), which always sources "user" —
        // the store section can only ever fill from addStoreIdeas().
        val ideas = IdeasStore(app)
        val saved = ideas.add("Build a masala price tracker app for the shop in Karachi", "Store")
        assertEquals(IdeaFeed.SOURCE_USER, saved!!.source)
        assertEquals("Store", saved.area)
        assertTrue(ideas.ideas.value.none { it.source == IdeaFeed.SOURCE_STORE })
    }

    @Test fun addStoreIdeasDedupesAgainstUserIdeas() {
        val ideas = IdeasStore(app)
        ideas.add("Build a masala price tracker app for the shop in Karachi", "You")
        val added = ideas.addStoreIdeas(
            listOf(IdeaFeed.ParsedIdea("App", "build a masala price tracker app for the shop in karachi.")),
            now)
        assertEquals("a generated repeat of a user idea must not be added", 0, added)
        assertEquals(1, ideas.ideas.value.size)
    }

    @Test fun addStoreIdeasRejectsVagueCandidates() {
        val ideas = IdeasStore(app)
        val added = ideas.addStoreIdeas(
            listOf(IdeaFeed.ParsedIdea("App", "do stuff"), IdeaFeed.ParsedIdea("Shop", "Sell chai")), now)
        assertEquals(0, added)
        assertTrue(ideas.ideas.value.isEmpty())
    }

    @Test fun refreshFailureKeepsOldIdeasAndRecordsTheReason() {
        val ideas = IdeasStore(app)
        ideas.addStoreIdeas(
            listOf(IdeaFeed.ParsedIdea("App", "Build a masala price tracker app for the shop in Karachi")), now)
        ideas.add("Solar charger for the shop", "You")
        val before = ideas.ideas.value.map { it.text }
        ideas.recordRefreshFailure("No AI model is available on this phone right now", now)
        assertEquals("a failed refresh must not touch any idea", before, ideas.ideas.value.map { it.text })
        assertFalse(ideas.refreshState.value.everSucceeded)
        assertTrue(ideas.refreshState.value.lastError.contains("No AI model"))
        assertEquals(now, ideas.refreshState.value.lastFailureAtMs)
    }

    @Test fun refreshSuccessMarksTheStoreUpdated() {
        val ideas = IdeasStore(app)
        ideas.recordRefreshSuccess(5, now)
        assertTrue(ideas.refreshState.value.everSucceeded)
        assertEquals(5, ideas.refreshState.value.lastAddedCount)
        assertEquals(now, ideas.refreshState.value.lastSuccessAtMs)
        assertTrue(ideas.refreshState.value.lastError.isEmpty())
    }

    @Test fun staleStoreIdeasArePrunedOnLoadButUserIdeasSurvive() {
        val seeded = listOf(
            Idea(id = "u-old", text = "Old user idea that must survive pruning", area = "You",
                createdAt = now - 60 * day, source = IdeaFeed.SOURCE_USER),
            Idea(id = "s-old", text = "Old store idea past thirty days", area = "App",
                createdAt = now - 31 * day, source = IdeaFeed.SOURCE_STORE),
            Idea(id = "s-new", text = "Recent store idea inside thirty days", area = "App",
                createdAt = now - day, source = IdeaFeed.SOURCE_STORE),
        )
        File(app.filesDir, "ideas.json").writeText(json.encodeToString(ListSerializer(Idea.serializer()), seeded))
        val ideas = IdeasStore(app)
        assertEquals(2, ideas.ideas.value.size)
        assertTrue(ideas.ideas.value.any { it.id == "u-old" })
        assertTrue(ideas.ideas.value.any { it.id == "s-new" })
        assertTrue(ideas.ideas.value.none { it.id == "s-old" })
    }

    @Test fun oldIdeasWithoutASourceDecodeAsTheUsersOwn() {
        // ideas.json from before the feed existed has no "source" field.
        val legacy = """[{"id":"legacy-1","text":"Solar charger for the shop","area":"You","createdAt":$now,"done":false}]"""
        File(app.filesDir, "ideas.json").writeText(legacy)
        val ideas = IdeasStore(app)
        assertEquals(1, ideas.ideas.value.size)
        assertEquals(IdeaFeed.SOURCE_USER, ideas.ideas.value.single().source)
    }

    @Test fun refreshStateSurvivesAReload() {
        val ideas = IdeasStore(app)
        ideas.recordRefreshSuccess(3, now)
        // The async write lands quickly; a fresh store reads the file back.
        Thread.sleep(500)
        val reloaded = IdeasStore(app)
        assertTrue(reloaded.refreshState.value.everSucceeded)
        assertEquals(3, reloaded.refreshState.value.lastAddedCount)
    }
}
