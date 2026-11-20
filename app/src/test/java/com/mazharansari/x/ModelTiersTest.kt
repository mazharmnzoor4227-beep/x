package com.mazharansari.x

import com.mazharansari.x.local.LocalModel
import com.mazharansari.x.local.ModelTier
import com.mazharansari.x.local.blockReason
import com.mazharansari.x.local.fitsTier
import com.mazharansari.x.local.isHardRamBlock
import org.junit.Assert.*
import org.junit.Test

/**
 * Tiered local-model catalog (2026-11-20). Every wired model must carry a
 * real, verified URL + size + SHA-256; no model may sit on a tier it cannot
 * run on; phone-tier detection must map the named RAM/storage boundaries to
 * the right tier; and a model that needs more RAM than the phone has must
 * be blocked with the honest reason. These tests fail on a blank URL/size/
 * SHA or a model placed on the wrong tier — they do not hit the network;
 * the values themselves were verified against live Hugging Face (see
 * LocalModels.kt KDoc) before being wired.
 */
class ModelTiersTest {
    private val sha256 = Regex("^[0-9a-f]{64}$")
    private val gb = 1073741824.0

    @Test fun everyModelHasVerifiedIdentity() {
        for (m in LocalModel.entries) {
            assertTrue("${m.id}: URL blank", m.url.isNotBlank())
            assertTrue("${m.id}: URL must be a Hugging Face resolve link", m.url.startsWith("https://huggingface.co/") && m.url.contains("/resolve/main/"))
            assertTrue("${m.id}: URL must be a single GGUF file", m.url.endsWith(".gguf"))
            assertTrue("${m.id}: byte size must be positive", m.bytes > 0)
            assertTrue("${m.id}: SHA-256 must be 64 lowercase hex chars, was '${m.sha256}'", sha256.matches(m.sha256))
            assertTrue("${m.id}: RAM need must be a real tier size", m.minRamGb in setOf(2, 4, 6, 8))
            assertTrue("${m.id}: purpose must say what it is for", m.purpose.isNotBlank())
        }
        assertEquals("model ids must be unique", LocalModel.entries.size, LocalModel.entries.map { it.id }.toSet().size)
        assertEquals("download URLs must be unique", LocalModel.entries.size, LocalModel.entries.map { it.url }.toSet().size)
    }

    @Test fun everyModelBelongsToAtLeastOneTier() {
        val tiered = ModelTier.ALL.flatMap { it.models }.toSet()
        for (m in LocalModel.entries) assertTrue("${m.id} is in no tier", m in tiered)
    }

    @Test fun tierMembershipIsCorrect() {
        for (tier in ModelTier.ALL) {
            assertTrue("${tier.id}: tier must list at least one model", tier.models.isNotEmpty())
            for (m in tier.models) {
                assertTrue("${m.id} needs ${m.minRamGb} GB RAM but sits on ${tier.id} (${tier.minRamGb} GB)", fitsTier(m, tier))
                assertTrue("${m.id} on ${tier.id}: RAM need must fit the tier", m.minRamGb <= tier.minRamGb)
            }
        }
        // Storage split is honest: a 64 GB phone is offered one model, a 128 GB phone all four 4 GB models.
        assertEquals("4/64 offers exactly the default", listOf(LocalModel.LFM3_1_7B), ModelTier.TIER_4GB_64.models)
        assertEquals("4/128 offers all four 4 GB models", 4, ModelTier.TIER_4GB_128.models.size)
        assertTrue("LFM3 stays the 4 GB default", ModelTier.TIER_4GB_128.models.first() == LocalModel.LFM3_1_7B)
        assertTrue("2 GB tier offers only the smallest model", ModelTier.TIER_2GB.models == listOf(LocalModel.QWEN_0_5B))
        assertTrue("8 GB tier offers only 7-8B models", ModelTier.TIER_8GB.models.all { it.minRamGb == 8 })
        assertTrue("6 GB tier offers only 3B models", ModelTier.TIER_6GB.models.all { it.minRamGb == 6 })
    }

    @Test fun phoneTierDetectionBoundaries() {
        assertEquals("exactly 2 GB RAM", ModelTier.TIER_2GB, ModelTier.tierFor(2.0, 59.0))
        assertEquals("3 GB RAM falls back to the 2 GB tier", ModelTier.TIER_2GB, ModelTier.tierFor(3.0, 59.0))
        assertEquals("exactly 4 GB RAM, 64 GB storage", ModelTier.TIER_4GB_64, ModelTier.tierFor(4.0, 59.0))
        assertEquals("exactly 4 GB RAM, 64 GB storage (boundary value)", ModelTier.TIER_4GB_64, ModelTier.tierFor(4.0, 64.0))
        assertEquals("exactly 4 GB RAM, 128 GB storage", ModelTier.TIER_4GB_128, ModelTier.tierFor(4.0, 119.0))
        assertEquals("exactly 4 GB RAM, 128 GB storage (boundary value)", ModelTier.TIER_4GB_128, ModelTier.tierFor(4.0, 128.0))
        assertEquals("storage split is at 96 GB", ModelTier.TIER_4GB_128, ModelTier.tierFor(4.0, 96.0))
        assertEquals("exactly 6 GB RAM", ModelTier.TIER_6GB, ModelTier.tierFor(6.0, 119.0))
        assertEquals("exactly 8 GB RAM", ModelTier.TIER_8GB, ModelTier.tierFor(8.0, 119.0))
        assertEquals("more than 8 GB RAM still gets the 8 GB tier", ModelTier.TIER_8GB, ModelTier.tierFor(12.0, 256.0))
        // Phone tier first, then the rest in size order.
        val ordered = ModelTier.orderedForPhone(ModelTier.TIER_6GB)
        assertEquals("phone tier first", ModelTier.TIER_6GB, ordered.first())
        assertEquals("all tiers shown exactly once", ModelTier.ALL.size, ordered.toSet().size)
    }

    @Test fun modelNeedingMoreRamThanPhoneIsBlockedWithHonestReason() {
        val reason = blockReason(LocalModel.QWEN_7B, totalRamGb = 4.0, availRamGb = 2.0, freeBytes = 20L * gb.toLong())
        assertNotNull("7B on a 4 GB phone must be blocked", reason)
        assertTrue("reason must name the RAM the model needs: $reason", reason!!.contains("8 GB RAM"))
        assertTrue("reason must name the RAM the phone has: $reason", reason.contains("4 GB"))
        assertTrue("hard block", isHardRamBlock(LocalModel.QWEN_7B, 4.0))
        val llamaReason = blockReason(LocalModel.LLAMA_8B, totalRamGb = 6.0, availRamGb = 3.0, freeBytes = 20L * gb.toLong())
        assertNotNull("8B on a 6 GB phone must be blocked", llamaReason)
        assertTrue("reason must say download is blocked: $llamaReason", llamaReason!!.contains("blocked"))
        val smallOnBig = blockReason(LocalModel.QWEN_3B, totalRamGb = 6.0, availRamGb = 4.0, freeBytes = 20L * gb.toLong())
        assertNull("3B on a 6 GB phone with RAM and storage free must not be blocked", smallOnBig)
    }

    @Test fun smallModelFitsSmallPhoneButBigModelDoesNot() {
        assertNull("0.5B fits a 2 GB phone with room to spare",
            blockReason(LocalModel.QWEN_0_5B, totalRamGb = 2.0, availRamGb = 1.5, freeBytes = 10L * gb.toLong()))
        assertFalse("0.5B is not hard-blocked on 2 GB", isHardRamBlock(LocalModel.QWEN_0_5B, 2.0))
        assertNotNull("LFM3 must be blocked on a 2 GB phone",
            blockReason(LocalModel.LFM3_1_7B, totalRamGb = 2.0, availRamGb = 1.0, freeBytes = 10L * gb.toLong()))
        assertNull("LFM3 fits a 4 GB phone",
            blockReason(LocalModel.LFM3_1_7B, totalRamGb = 4.0, availRamGb = 2.5, freeBytes = 10L * gb.toLong()))
    }

    @Test fun lowStorageBlocksWithHonestReason() {
        val reason = blockReason(LocalModel.LFM3_1_7B, totalRamGb = 4.0, availRamGb = 2.5, freeBytes = 100L * 1024 * 1024)
        assertNotNull("LFM3 with only 100 MB free must be blocked", reason)
        assertTrue("reason must name storage: $reason", reason!!.contains("storage", ignoreCase = true))
        assertFalse("low storage is a soft block, not a hard RAM block", isHardRamBlock(LocalModel.LFM3_1_7B, 4.0))
    }

    @Test fun baseModelIsNotPresentedAsInstruct() {
        val base = LocalModel.QWEN3_1_7B
        assertTrue("Qwen3-1.7B blurb must say it is a base model", base.blurb.contains("Base model"))
        assertTrue("Qwen3-1.7B blurb must say it is NOT instruction-tuned", base.blurb.contains("NOT instruction-tuned"))
        assertFalse("Qwen3-1.7B purpose must not claim instruct", base.purpose.contains("nstruct", ignoreCase = true))
    }
}
