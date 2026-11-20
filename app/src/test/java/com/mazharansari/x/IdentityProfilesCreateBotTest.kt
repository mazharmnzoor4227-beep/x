package com.mazharansari.x

import com.mazharansari.x.agent.AgentIdentity
import com.mazharansari.x.data.AppSettings
import com.mazharansari.x.data.BotCatalog
import com.mazharansari.x.data.BotDefinition
import com.mazharansari.x.data.BotDefinitionStore
import com.mazharansari.x.data.BotLogo
import com.mazharansari.x.data.X_BOT_ID
import com.mazharansari.x.data.xBotDefinition
import com.mazharansari.x.voice.VoiceCatalog
import com.mazharansari.x.voice.VoicePicker
import com.mazharansari.x.voice.VoicePreview
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Identity + profiles + create-bot (2026-11-20, user order):
 *  1. Creator identity — every bot answers "Mazhar Ansari" to "who made you".
 *  2. Voice greeting — "I am X, ask me anything", in the CURRENT bot name.
 *  3. Profile pictures — the user's own photo and each bot's photo persist
 *     as app-private files and resolve back to the photo.
 *  4. Create bot — a custom bot (name + instructions + photo) appears in
 *     the Bots list and opens its own chat; X Bot stays the orchestrator.
 *
 * Pure JVM tests (no Android framework). The UI half of item 4 lives in
 * [CreateBotUiTest] (Robolectric, V06NavScreensTest pattern): the entry is
 * reachable, the sheet saves, the bot appears in the list, and its row
 * opens its own chat.
 */
class IdentityProfilesCreateBotTest {

    // ── 1. Creator identity ──────────────────────────────────────────────

    @Test fun creatorIdentityIsMazharAnsari() {
        assertEquals("Mazhar Ansari", AgentIdentity.CREATOR_NAME)
        assertTrue(AgentIdentity.creatorAnswer().contains("Mazhar Ansari"))
        assertTrue(AgentIdentity.creatorLine().contains("Mazhar Ansari"))
        // The system-prompt line carries the creator AND the bot's current name.
        val line = AgentIdentity.systemLine("X")
        assertTrue(line.contains("Mazhar Ansari"))
        assertTrue(line.contains("You are X"))
        val renamed = AgentIdentity.systemLine("X Prime")
        assertTrue(renamed.contains("Mazhar Ansari"))
        assertTrue("the identity line uses the live name", renamed.contains("You are X Prime"))
    }

    @Test fun everyBotPromptBlockCarriesTheCreator() {
        val x = xBotDefinition()
        assertTrue(x.promptBlock().contains("Mazhar Ansari"))
        assertTrue(x.promptBlock().contains("X Bot"))
        val custom = BotDefinition(
            id = "user-1", name = "Helper", description = "Helps.",
            persona = "You are Helper, a tidy assistant.")
        assertTrue(custom.promptBlock().contains("Mazhar Ansari"))
        assertTrue(custom.promptBlock().contains("You are Helper"))
        assertTrue(custom.promptBlock().contains("tidy assistant"))
        // The build path has its own system prompt (AgyBrain) — it carries
        // the same creator fact, so a project chat answers identically.
        // (Its <axion_build> tags and axion-* tool names are internal
        // markers for the build sandbox, never an identity string — so no
        // no-Axion assert applies to them here.)
        val build = com.mazharansari.x.build.BuildPrompts.system(
            com.mazharansari.x.data.Project(id = "p1", name = "Shop", slug = "shop", createdAt = 1L), "/workspace/shop")
        assertTrue(build.contains("Mazhar Ansari"))
    }

    // ── 2. Voice greeting ────────────────────────────────────────────────

    @Test fun greetingSaysIAmXAskMeAnything() {
        assertEquals("I am X, ask me anything.", VoiceCatalog.PREVIEW_TEXT)
        assertEquals(VoiceCatalog.PREVIEW_TEXT, VoiceCatalog.greetingFor("X"))
        assertEquals("I am X Prime, ask me anything.", VoiceCatalog.greetingFor("X Prime"))
        assertEquals("blank name falls back to X", VoiceCatalog.PREVIEW_TEXT, VoiceCatalog.greetingFor("   "))
        assertEquals("blank name falls back to X", VoiceCatalog.PREVIEW_TEXT, VoiceCatalog.greetingFor(""))
    }

    @Test fun greetingUsesTheRenamedBotNameInEveryVoice() {
        // English voices speak the greeting with the new name.
        assertEquals("I am X Prime, ask me anything.", VoicePreview.sampleTextFor("kodex:rounded", "X Prime"))
        assertEquals("I am X Prime, ask me anything.", VoicePreview.sampleTextFor("piper-amy", "X Prime"))
        assertEquals("I am X Prime, ask me anything.", VoicePreview.sampleTextFor("edge:ava", "X Prime"))
        // The per-voice introductions keep their own words but swap the name in.
        val aegis = VoicePreview.sampleTextFor("piper-aegis", "X Prime")
        assertTrue(aegis.contains("X Prime"))
        assertFalse("no hardcoded old name survives the rename", aegis.contains("aapki X voice"))
        val uzma = VoicePreview.sampleTextFor("edge:uzma", "X Prime")
        assertTrue(uzma.contains("X Prime"))
        assertFalse(uzma.contains("آپ کی X آواز"))
        val kokoro = VoicePreview.sampleTextFor("kokoro-hindi", "X Prime")
        assertTrue(kokoro.contains("X Prime"))
        assertFalse(kokoro.contains("आपका X हूँ"))
        // Un-renamed: the one-arg form is unchanged (VoicePickerPreviewTest's
        // per-voice sample assertions still hold).
        VoicePicker.voices.forEach { v ->
            assertEquals("sample for ${v.id} unchanged without a rename", v.sampleText, VoicePreview.sampleTextFor(v.id))
        }
    }

    @Test fun noAxionInAnyIdentityOrGreetingPath() {
        val haystack = buildString {
            appendLine(AgentIdentity.systemLine("X"))
            appendLine(AgentIdentity.systemLine("X Prime"))
            appendLine(AgentIdentity.creatorLine())
            appendLine(AgentIdentity.creatorAnswer())
            val x = xBotDefinition()
            appendLine(x.name); appendLine(x.description); appendLine(x.persona); appendLine(x.promptBlock())
            BotCatalog.seeds.forEach { appendLine(it.name); appendLine(it.description); appendLine(it.persona); appendLine(it.promptBlock()) }
            appendLine(VoiceCatalog.PREVIEW_TEXT)
            appendLine(VoiceCatalog.greetingFor("X"))
            appendLine(VoiceCatalog.greetingFor("X Prime"))
            VoicePicker.voices.forEach { appendLine(it.sampleText) }
            VoicePicker.voices.forEach { appendLine(VoicePreview.sampleTextFor(it.id, "X Prime")) }
        }
        assertFalse("no user-visible 'Axion' may survive in identity/greeting paths",
            haystack.contains("axion", ignoreCase = true))
        assertTrue("the creator name IS present", haystack.contains("Mazhar Ansari"))
    }

    // ── 3. Profile pictures ─────────────────────────────────────────────

    @Test fun botPhotoPersistsAndResolvesToTheFile() {
        val dir = File("/tmp/identity-botphoto-test-${System.nanoTime()}").apply { mkdirs() }
        try {
            // A test image: a real file on disk (the app copies a picked
            // photo into its own storage the same way — see copyPickedMedia).
            val photo = File(dir, "photo.jpg").apply { writeBytes(ByteArray(64) { 0xFF.toByte() }) }
            val store = BotDefinitionStore(dir)
            store.ensureXBot()
            store.setLogo(X_BOT_ID, BotLogo.custom(photo.absolutePath))
            val reloaded = BotDefinitionStore(dir)
            val x = reloaded.definitions.value.first { it.id == X_BOT_ID }
            assertEquals("file:${photo.absolutePath}", x.logo)
            val parsed = BotLogo.parse(x.logo)
            assertTrue(parsed is BotLogo.Custom)
            assertEquals(photo.absolutePath, (parsed as BotLogo.Custom).path)
            assertTrue("the stored photo file still exists", File(parsed.path).isFile)
            // A missing file still parses (the UI falls back to the monogram).
            store.setLogo(X_BOT_ID, BotLogo.custom("/tmp/does-not-exist-photo.jpg"))
            assertTrue(BotLogo.parse(BotDefinitionStore(dir).definitions.value.first { it.id == X_BOT_ID }.logo) is BotLogo.Custom)
        } finally { dir.deleteRecursively() }
    }

    @Test fun userPhotoSurvivesSettingsRoundTrip() {
        assertEquals("no photo by default", "", AppSettings().userPhoto)
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val saved = AppSettings(userName = "Mazhar Ansari", userPhoto = "file:/tmp/me.jpg")
        val roundTripped = json.decodeFromString<AppSettings>(json.encodeToString(AppSettings.serializer(), saved))
        assertEquals("Mazhar Ansari", roundTripped.userName)
        assertEquals("file:/tmp/me.jpg", roundTripped.userPhoto)
        // Old settings files (no userPhoto key) still decode.
        val old = json.decodeFromString<AppSettings>("""{"userName":"Mazhar Ansari","theme":"dark","mode":"AUTO"}""")
        assertEquals("", old.userPhoto)
    }

    // ── 4. Create bot (store level) ─────────────────────────────────────

    @Test fun createBotPersistsAppearsAndDeletes() {
        val dir = File("/tmp/identity-createbot-test-${System.nanoTime()}").apply { mkdirs() }
        try {
            val store = BotDefinitionStore(dir)
            store.ensureXBot()
            val def = store.create("Helper Bot", "Helps with chores", "You are Helper Bot. Be tidy.", null)
            assertTrue(def.id.startsWith("user-"))
            assertEquals("Helper Bot", def.name)
            assertEquals("Helps with chores", def.description)
            assertEquals("You are Helper Bot. Be tidy.", def.persona)
            assertTrue("X Bot stays first — it remains the default orchestrator",
                store.definitions.value.first().id == X_BOT_ID)
            assertTrue(store.definitions.value.any { it.id == def.id })
            // Persist round-trip: a new store on the same folder keeps it.
            val reloaded = BotDefinitionStore(dir)
            val kept = reloaded.definitions.value.firstOrNull { it.id == def.id }
            assertNotNull(kept)
            assertEquals("Helper Bot", kept!!.name)
            assertEquals("Helps with chores", kept.description)
            assertEquals("You are Helper Bot. Be tidy.", kept.persona)
            assertTrue("the created bot answers as its creator's bot", kept.promptBlock().contains("Mazhar Ansari"))
            // A photo set after creation persists too.
            val photo = File(dir, "helper.jpg").apply { writeBytes(ByteArray(32) { 1 }) }
            reloaded.setLogo(def.id, BotLogo.custom(photo.absolutePath))
            assertEquals("file:${photo.absolutePath}",
                BotDefinitionStore(dir).definitions.value.first { it.id == def.id }.logo)
            // Rename persists; delete removes it and leaves X Bot alone.
            reloaded.rename(def.id, "Helper Prime")
            assertEquals("Helper Prime", BotDefinitionStore(dir).definitions.value.first { it.id == def.id }.name)
            reloaded.remove(def.id)
            val afterDelete = BotDefinitionStore(dir)
            assertNull(afterDelete.definitions.value.firstOrNull { it.id == def.id })
            assertEquals(X_BOT_ID, afterDelete.definitions.value.single().id)
        } finally { dir.deleteRecursively() }
    }

    @Test fun createBotWithBlankNameFallsBack() {
        val dir = File("/tmp/identity-createbot-blank-${System.nanoTime()}").apply { mkdirs() }
        try {
            val store = BotDefinitionStore(dir)
            assertEquals("My bot", store.create("   ", "d", "p", null).name)
        } finally { dir.deleteRecursively() }
    }

    // ── Key-backup file name (found in the Axion sweep) ─────────────────
    @Test fun keyBackupExportsAsXKeysAndStillRoundTrips() {
        val entries = listOf(
            com.mazharansari.x.data.KeyBackup.Entry("gemini", "AIza-test", listOf("gemini-3-flash"), listOf("gemini-3-flash")),
        )
        val file = com.mazharansari.x.data.KeyBackup.export(entries, "correct horse", iter = 100_000)
        assertTrue("new exports are named x-keys", file.contains("\"format\":\"x-keys\""))
        assertFalse("no Axion string in a new export", file.contains("axion", ignoreCase = true))
        assertEquals(entries, com.mazharansari.x.data.KeyBackup.import(file, "correct horse"))
        // Old "axion-keys" files still import — guarded by
        // V052SecurityTest.oldV1FilesStillImport; the dual accept lives here.
    }
}
