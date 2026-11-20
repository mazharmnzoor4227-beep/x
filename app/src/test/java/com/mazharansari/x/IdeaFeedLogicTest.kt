package com.mazharansari.x

import com.mazharansari.x.data.Idea
import com.mazharansari.x.data.IdeaFeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Idea store feed — pure rules (IdeaFeed), tested on the JVM without
 * Robolectric. Every test fails on the old code (no feed rules at all)
 * and passes on the new.
 */
class IdeaFeedLogicTest {
    private val now = 1_794_000_000_000L
    private val day = 24L * 3_600_000L

    private fun idea(text: String, source: String, at: Long = now, area: String = "App") =
        Idea(id = "$source-$at-${text.hashCode()}", text = text, area = area, createdAt = at, source = source)

    private fun goodLine(i: Int) =
        "App: Build a small tool number $i for the masala shop in Karachi"

    // ── Normalisation / dedup ──────────────────────────────────────────────

    @Test fun normalizeIgnoresCaseWhitespaceAndTrailingPunctuation() {
        assertEquals(
            IdeaFeed.normalize("Build a masala price tracker app."),
            IdeaFeed.normalize("  build   a MASALA price tracker app  "),
        )
    }

    @Test fun qualityRejectsVagueOneWordAndTooShort() {
        assertFalse(IdeaFeed.isQualityIdea(""))
        assertFalse(IdeaFeed.isQualityIdea("App"))
        assertFalse(IdeaFeed.isQualityIdea("Start a business"))
        assertFalse(IdeaFeed.isQualityIdea("Sell chai"))
        assertFalse(IdeaFeed.isQualityIdea("12345678901234567890"))
    }

    @Test fun qualityAcceptsAConcreteActionableIdea() {
        assertTrue(IdeaFeed.isQualityIdea("Open a small masala stall near Al-Khalil Market selling 100g packs"))
        assertTrue(IdeaFeed.isQualityIdea("Build a masala price tracker app for the shop in Karachi"))
    }

    // ── Parsing ────────────────────────────────────────────────────────────

    @Test fun parseBatchReadsAreaLinesAndStripsBulletsAndNumbers() {
        val raw = "- App: Build a masala price tracker app for the shop in Karachi\n" +
            "2. Shop: Open a small tasting counter for new masala blends near Al-Khalil Market\n" +
            "Video: Film a 30 second reel showing how biryani masala is stone ground"
        val parsed = IdeaFeed.parseBatch(raw)
        assertEquals(3, parsed.size)
        assertEquals(listOf("App", "Shop", "Video"), parsed.map { it.area })
        assertTrue(parsed[0].text.startsWith("Build a masala price tracker"))
    }

    @Test fun parseBatchKeepsUnknownAreaPrefixAsPlainText() {
        val parsed = IdeaFeed.parseBatch("Something else: Build a small masala stall near Al-Khalil Market")
        assertEquals(1, parsed.size)
        assertEquals("Ideas", parsed[0].area)
        assertTrue(parsed[0].text.startsWith("Something else:"))
    }

    @Test fun parseBatchDropsVagueLinesInsteadOfStoringThem() {
        val parsed = IdeaFeed.parseBatch("App: Build a masala price tracker app for the shop\nApp: do stuff\nShop: Sell chai")
        assertEquals(1, parsed.size)
    }

    // ── Selection ──────────────────────────────────────────────────────────

    @Test fun selectNewIdeasDropsRepeatsAndCapsAtFive() {
        val raw = (1..7).joinToString("\n") { goodLine(it) }
        val existing = listOf("build a small tool number 1 for the masala shop in karachi.")
        val picked = IdeaFeed.selectNewIdeas(raw, existing)
        assertEquals(IdeaFeed.BATCH_SIZE, picked.size)
        assertTrue(picked.none { IdeaFeed.normalize(it.text) in existing.map { e -> IdeaFeed.normalize(e) } })
        assertEquals(5, picked.map { IdeaFeed.normalize(it.text) }.toSet().size)
    }

    @Test fun selectNewIdeasIsEmptyWhenEverythingIsARepeat() {
        val raw = "App: Build a masala price tracker app for the shop in Karachi"
        val picked = IdeaFeed.selectNewIdeas(raw, listOf("Build a masala price tracker app for the shop in Karachi"))
        assertTrue(picked.isEmpty())
    }

    @Test fun selectNewIdeasIsEmptyOnBlankAnswer() {
        assertTrue(IdeaFeed.selectNewIdeas("", emptyList()).isEmpty())
        assertTrue(IdeaFeed.selectNewIdeas("   \n\n", emptyList()).isEmpty())
    }

    // ── Pruning ────────────────────────────────────────────────────────────

    @Test fun pruneKeepsUserIdeasForeverAndDropsOldStoreIdeas() {
        val list = listOf(
            idea("Old user idea that must survive pruning", IdeaFeed.SOURCE_USER, at = now - 60 * day),
            idea("Old store idea past thirty days", IdeaFeed.SOURCE_STORE, at = now - 31 * day),
            idea("Recent store idea inside thirty days", IdeaFeed.SOURCE_STORE, at = now - 29 * day),
        )
        val kept = IdeaFeed.prune(list, now)
        assertEquals(2, kept.size)
        assertTrue(kept.any { it.source == IdeaFeed.SOURCE_USER })
        assertTrue(kept.any { it.text.startsWith("Recent store idea") })
        assertTrue(kept.none { it.text.startsWith("Old store idea") })
    }

    @Test fun pruneCapsStoreIdeasAtOneHundredButNeverUserIdeas() {
        val list = (1..105).map { idea("Fresh store idea number $it for the shop", IdeaFeed.SOURCE_STORE, at = now) } +
            (1..3).map { idea("User idea number $it that must survive", IdeaFeed.SOURCE_USER, at = now) }
        val kept = IdeaFeed.prune(list, now)
        assertEquals(IdeaFeed.MAX_STORE_IDEAS, kept.count { it.source == IdeaFeed.SOURCE_STORE })
        assertEquals(3, kept.count { it.source == IdeaFeed.SOURCE_USER })
    }

    // ── Timing / handoff ───────────────────────────────────────────────────

    @Test fun millisUntilNextMorningIsWithinADayAndLandsAtSeven() {
        val delay = IdeaFeed.millisUntilNextMorning(now)
        assertTrue(delay > 0)
        assertTrue(delay <= 24L * 3_600_000L)
        val target = java.time.LocalDateTime.ofInstant(Instant.ofEpochMilli(now + delay), ZoneId.systemDefault())
        assertEquals(7, target.hour)
        assertEquals(0, target.minute)
    }

    @Test fun isNewTodayMatchesTheLocalCalendarDay() {
        assertTrue(IdeaFeed.isNewToday(now, now))
        assertTrue(IdeaFeed.isNewToday(now - 60_000L, now))
        assertFalse(IdeaFeed.isNewToday(now - 2 * day, now))
    }

    @Test fun startWorkMessageCarriesTheIdeaVerbatim() {
        val text = "Build a masala price tracker app for the shop in Karachi"
        val msg = IdeaFeed.startWorkMessage(text)
        assertTrue(msg.startsWith("Let's build this idea: "))
        assertTrue(msg.contains(text))
        assertEquals(text, msg.removePrefix("Let's build this idea: "))
    }

    @Test fun buildPromptNamesTheUserAsksForFiveAndListsWhatHeAlreadyHas() {
        val prompt = IdeaFeed.buildPrompt(listOf("Build a masala price tracker app for the shop in Karachi"))
        assertTrue(prompt.contains("Mazhar"))
        assertTrue(prompt.contains("exactly ${IdeaFeed.BATCH_SIZE}"))
        assertTrue(prompt.contains("App, Shop, Product, Video, Business"))
        assertTrue("the model must see existing ideas so it does not repeat them",
            prompt.contains("Build a masala price tracker app"))
    }
}
