package com.mazharansari.x

import com.mazharansari.x.skills.SkillRepo
import com.mazharansari.x.voice.EdgeTts
import com.mazharansari.x.voice.VoiceCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** v0.3 voice + skills checks. EdgeLive hits Microsoft's real endpoint from the build box (network). */
@RunWith(RobolectricTestRunner::class)
class V03Test {
    private val out = File(System.getProperty("axion.out", "/tmp/axion_v03_test")).apply { mkdirs() }

    /**
     * Live network: the Edge endpoint occasionally resets the connection
     * mid-synthesis on CI (f545e2a: EdgeError "Connection reset", after the
     * same test passed on 66d2a41). One retry, then the failure is real — do
     * not chase it further, and do not @Ignore the live test.
     */
    private suspend fun synth(text: String, voice: String, ratePercent: Int = 0): ByteArray {
        var last: Exception? = null
        repeat(2) {
            try { return EdgeTts.synthesize(text, voice, ratePercent) } catch (e: EdgeTts.EdgeError) { last = e }
        }
        throw last!!
    }

    @Test fun edgeLiveSynthesisFromKotlin() = runBlocking {
        for ((voice, text) in listOf(
            "en-US-AvaMultilingualNeural" to VoiceCatalog.PREVIEW_TEXT,
            "hi-IN-SwaraNeural" to VoiceCatalog.PREVIEW_URDU,
            "ur-PK-AsadNeural" to "السلام علیکم! میں آپ کا ایکسیون ہوں۔",
        )) {
            val mp3 = synth(text, voice)
            assertTrue("$voice returned ${mp3.size} bytes", mp3.size > 8_000)
            File(out, "kotlin_$voice.mp3").writeBytes(mp3)
        }
        // Pace: Slow (-15%) must give a longer file than Fast (+18%)
        val slow = synth(VoiceCatalog.PREVIEW_TEXT, "en-US-AndrewMultilingualNeural", -15)
        val fast = synth(VoiceCatalog.PREVIEW_TEXT, "en-US-AndrewMultilingualNeural", 18)
        assertTrue("slow ${slow.size} vs fast ${fast.size}", slow.size > fast.size)
    }

    @Test fun scriptDetection() {
        assertEquals(VoiceCatalog.Script.URDU, VoiceCatalog.detect("السلام علیکم! آپ کیسے ہیں؟"))
        assertEquals(VoiceCatalog.Script.DEVANAGARI, VoiceCatalog.detect("नमस्ते, आप कैसे हैं?"))
        assertEquals(VoiceCatalog.Script.ROMAN_URDU, VoiceCatalog.detect("Yaar main abhi kaam kar raha hoon, aap batao kya chahiye"))
        assertEquals(VoiceCatalog.Script.LATIN_ENGLISH, VoiceCatalog.detect("Here is the audit of your website with five fixes."))
        val ava = VoiceCatalog.natural.first { it.name == "Ava" }
        assertEquals("hi-IN-SwaraNeural", VoiceCatalog.edgeVoiceFor(ava, "Main aapka Axion hoon, batayein kya madad karoon", true))
        assertEquals("ur-PK-UzmaNeural", VoiceCatalog.edgeVoiceFor(ava, "میں آپ کا ایکسیون ہوں", true))
        assertEquals("en-US-AvaMultilingualNeural", VoiceCatalog.edgeVoiceFor(ava, "Main aapka Axion hoon", false))
        assertEquals("kodex:rounded", VoiceCatalog.DEFAULT_ID)
        assertEquals("en-gb-x-gbb-network", VoiceCatalog.byId(null).engineVoice)
    }

    @Test fun skillUploadZipMdPdf() = runBlocking {
        val dir = File(out, "skills").apply { deleteRecursively(); mkdirs() }
        val repo = SkillRepo(RuntimeEnvironment.getApplication(), dir)
        val zip = ByteArrayOutputStream().also { bo -> ZipOutputStream(bo).use { z ->
            fun put(n: String, s: String) { z.putNextEntry(ZipEntry(n)); z.write(s.toByteArray()); z.closeEntry() }
            put("pack/seo-check/SKILL.md", "---\nname: seo-check\ndescription: Check a page's SEO basics\n---\n\nSteps: fetch, check title, meta, h1.")
            put("pack/seo-check/reference.md", "extra notes")
            put("pack/urdu-writer/SKILL.md", "---\nname: urdu-writer\ndescription: Write in Roman Urdu\n---\n\nAlways reply in Roman Urdu.")
        } }.toByteArray()
        val z = repo.importFile("pack.zip", zip)
        assertEquals(setOf("seo-check", "urdu-writer"), z.map { it.name }.toSet())
        assertTrue(File(dir, "seo-check/reference.md").exists())
        val md = repo.importFile("pricing-rules.md", "# Pricing\nAlways quote prices in PKR and add free delivery note.".toByteArray())
        assertEquals("pricing-rules", md.single().name)
        assertTrue(repo.body("pricing-rules")!!.startsWith("---\nname: pricing-rules"))
        val pdfFile = File(System.getProperty("axion.pdf", "/tmp/axion_skill_test.pdf"))
        if (pdfFile.exists()) {
            val p = repo.importFile("returns-policy.pdf", pdfFile.readBytes())
            assertTrue(repo.body(p.single().name)!!.contains("Cash on Delivery", ignoreCase = true))
        }
        assertTrue(repo.skills.value.map { it.name }.containsAll(listOf("seo-check", "urdu-writer", "pricing-rules")))
    }
}
