package com.mazharansari.x

import com.mazharansari.x.linux.LinuxRuntime
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.GZIPOutputStream

/**
 * P0-8 (HI-6) history: extractTarGz used to exempt symlinks from its path-escape check
 * and never validated the symlink TARGET, so a malicious tar could plant a link pointing
 * outside the destination and a later entry written through it would escape the sandbox.
 *
 * Policy since redesign round 2 (Addendum A): an absolute symlink target is REWRITTEN to a
 * path relative to the link's parent inside the destination — rootfs tarballs legitimately
 * contain rootfs-absolute links (e.g. /usr/bin/foo -> /etc/alternatives/foo), and resolving
 * them against the host used to abort the whole install with "Unsafe symlink target in
 * archive". A target that still escapes the destination after normalisation (absolute or
 * relative) cannot be load-bearing inside the rootfs, so the link is SKIPPED and reported
 * via onSkipped instead of aborting — nothing is ever written outside the destination.
 * Entry paths that escape and hard-link targets that escape are still refused outright.
 */
class TarExtractSecurityTest {

    private fun buildTar(tgz: File, add: TarArchiveOutputStream.() -> Unit) {
        TarArchiveOutputStream(GZIPOutputStream(tgz.outputStream())).use { it.add() }
    }

    private fun TarArchiveOutputStream.file(name: String, data: ByteArray) {
        val e = TarArchiveEntry(name).apply { size = data.size.toLong(); mode = "100644".toInt(8) }
        putArchiveEntry(e); write(data); closeArchiveEntry()
    }

    private fun TarArchiveOutputStream.symlink(name: String, target: String) {
        val e = TarArchiveEntry(name, TarConstants.LF_SYMLINK).apply { setLinkName(target) }
        putArchiveEntry(e); closeArchiveEntry()
    }

    private fun TarArchiveOutputStream.hardlink(name: String, target: String) {
        val e = TarArchiveEntry(name, TarConstants.LF_LINK).apply { setLinkName(target) }
        putArchiveEntry(e); closeArchiveEntry()
    }

    private fun assertRefused(tgz: File, dest: File, what: String) {
        try {
            LinuxRuntime.extractTarGz(tgz, dest)
            fail("extraction must refuse: $what")
        } catch (_: IllegalArgumentException) {
            // expected: the require() in extractTarGz
        }
    }

    @Test fun absoluteSymlinkRewrittenInsideDest() {
        val work = Files.createTempDirectory("p0-8-").toFile()
        try {
            val dest = File(work, "dest").apply { mkdirs() }
            val outside = File(work, "outside").apply { mkdirs() }
            val tgz = File(work, "evil.tgz")
            // The old attack: a symlink to an absolute path outside the destination plus a
            // file written through it. New policy: the link is rewritten to a relative target
            // inside dest, so the file lands inside dest and nothing escapes.
            buildTar(tgz) { symlink("evil", outside.absolutePath); file("evil/pwned.txt", "pwned".toByteArray()) }
            LinuxRuntime.extractTarGz(tgz, dest)
            assertFalse("nothing may be written outside the destination", File(outside, "pwned.txt").exists())
            assertTrue("the link is planted, rewritten inside dest", Files.isSymbolicLink(File(dest, "evil").toPath()))
            val target = Files.readSymbolicLink(File(dest, "evil").toPath()).toString()
            assertFalse("rewritten target must be relative, never host-absolute", target.startsWith("/"))
            assertFalse("rewritten target must not climb out of dest", target.startsWith(".."))
            assertEquals("the file written through the link lands inside dest", "pwned", File(dest, "evil/pwned.txt").readText())
        } finally { work.deleteRecursively() }
    }

    @Test fun relativeSymlinkEscapeSkipped() {
        val work = Files.createTempDirectory("p0-8-").toFile()
        try {
            val dest = File(work, "dest").apply { mkdirs() }
            val outside = File(work, "outside").apply { mkdirs() }
            val tgz = File(work, "evil.tgz")
            val skipped = mutableListOf<String>()
            buildTar(tgz) { symlink("sub/link", "../../outside"); file("sub/link/pwned.txt", "pwned".toByteArray()); file("good.txt", "good".toByteArray()) }
            // A relative target that escapes dest cannot be load-bearing inside the rootfs:
            // the link is skipped and reported, the rest of the archive still extracts.
            LinuxRuntime.extractTarGz(tgz, dest, onSkipped = { skipped += it })
            assertFalse(File(outside, "pwned.txt").exists())
            assertFalse("the escaping link must not be planted", Files.isSymbolicLink(File(dest, "sub/link").toPath()))
            assertEquals("the skipped link is reported", 1, skipped.size)
            assertTrue("the report names the link", skipped[0].contains("sub/link"))
            assertEquals("the file written through the skipped link lands inside dest as a plain file", "pwned", File(dest, "sub/link/pwned.txt").readText())
            assertEquals("the rest of the archive still extracts", "good", File(dest, "good.txt").readText())
        } finally { work.deleteRecursively() }
    }

    @Test fun absoluteSymlinkToLaterFileResolves() {
        val work = Files.createTempDirectory("p0-8-").toFile()
        try {
            val dest = File(work, "dest").apply { mkdirs() }
            val tgz = File(work, "ubuntu.tgz")
            // The real Ubuntu-base case: /usr/bin/foo -> /etc/alternatives/foo, where the
            // target file comes LATER in the archive. The rewritten relative link must
            // resolve to it once the file exists.
            buildTar(tgz) { symlink("usr/bin/foo", "/etc/alternatives/foo"); file("etc/alternatives/foo", "alt".toByteArray()) }
            LinuxRuntime.extractTarGz(tgz, dest)
            assertTrue(Files.isSymbolicLink(File(dest, "usr/bin/foo").toPath()))
            assertEquals("../../etc/alternatives/foo", Files.readSymbolicLink(File(dest, "usr/bin/foo").toPath()).toString())
            assertEquals("alt", File(dest, "usr/bin/foo").readText())
        } finally { work.deleteRecursively() }
    }

    @Test fun dotdotSymlinkEntryRefused() {
        val work = Files.createTempDirectory("p0-8-").toFile()
        try {
            val dest = File(work, "dest").apply { mkdirs() }
            val tgz = File(work, "evil.tgz")
            // the entry's own path escapes — the old code waved symlinks through unchecked
            buildTar(tgz) { symlink("../evil", "x") }
            assertRefused(tgz, dest, "symlink entry named ../evil")
            assertFalse(File(work, "evil").exists())
        } finally { work.deleteRecursively() }
    }

    @Test fun absoluteHardLinkEscapeRefused() {
        val work = Files.createTempDirectory("p0-8-").toFile()
        try {
            val dest = File(work, "dest").apply { mkdirs() }
            val tgz = File(work, "evil.tgz")
            buildTar(tgz) { file("real.txt", "real".toByteArray()); hardlink("bad", "/etc/passwd") }
            assertRefused(tgz, dest, "hard link whose target is an absolute path outside the destination")
        } finally { work.deleteRecursively() }
    }

    @Test fun legitSymlinkInsideDestStillWorks() {
        val work = Files.createTempDirectory("p0-8-").toFile()
        try {
            val dest = File(work, "dest").apply { mkdirs() }
            val tgz = File(work, "ok.tgz")
            buildTar(tgz) { file("real.txt", "hello".toByteArray()); symlink("link", "real.txt"); hardlink("hard", "real.txt") }
            LinuxRuntime.extractTarGz(tgz, dest)
            assertTrue(Files.isSymbolicLink(File(dest, "link").toPath()))
            assertEquals("hello", File(dest, "link").readText())
            assertEquals("hello", File(dest, "hard").readText())
            assertEquals("hello", File(dest, "real.txt").readText())
        } finally { work.deleteRecursively() }
    }
}
