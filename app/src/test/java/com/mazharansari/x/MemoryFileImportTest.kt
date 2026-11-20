package com.mazharansari.x

import com.mazharansari.x.data.MemoryFileImport
import com.mazharansari.x.data.MemoryStore
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Memory file import, pure-JVM tests (no Android needed): PDF text extraction, .docx text,
 * entry splitting, and the honest "could not read" paths. These FAIL on the old code — the
 * helper did not exist and the Memory screen accepted text only.
 */
class MemoryFileImportTest {

    private fun zipBytes(files: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, data) in files) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** A minimal but real PDF: catalog/pages/font objects and one content stream. */
    private fun pdfBytes(content: ByteArray, flate: Boolean): ByteArray {
        val stream: ByteArray
        val filter: String
        if (flate) {
            val def = Deflater()
            def.setInput(content)
            def.finish()
            val buf = ByteArray(4096)
            val n = def.deflate(buf)
            def.end()
            stream = buf.copyOf(n)
            filter = " /Filter /FlateDecode"
        } else {
            stream = content
            filter = ""
        }
        val head = ("%PDF-1.4\n" +
            "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n" +
            "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n" +
            "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj\n" +
            "4 0 obj << /Length ${stream.size}$filter >> stream\n")
            .toByteArray(Charsets.ISO_8859_1)
        val tail = "\nendstream\nendobj\n5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n"
            .toByteArray(Charsets.ISO_8859_1)
        return head + stream + tail
    }

    @Test fun pdfExtractsTextUncompressed() {
        val pdf = pdfBytes("BT /F1 12 Tf 72 720 Td (Hello memory world) Tj ET".toByteArray(Charsets.ISO_8859_1), flate = false)
        val ex = MemoryFileImport.extract("notes.pdf", pdf)
        assertTrue(ex is MemoryFileImport.Extracted.Text)
        assertTrue((ex as MemoryFileImport.Extracted.Text).text.contains("Hello memory world"))
    }

    @Test fun pdfExtractsTextFlateCompressed() {
        val content = "BT /F1 12 Tf 72 720 Td (Flate memory line one) Tj 0 -14 Td (Flate memory line two) Tj ET"
            .toByteArray(Charsets.ISO_8859_1)
        val ex = MemoryFileImport.extract("notes.pdf", pdfBytes(content, flate = true))
        assertTrue(ex is MemoryFileImport.Extracted.Text)
        val text = (ex as MemoryFileImport.Extracted.Text).text
        assertTrue(text.contains("Flate memory line one"))
        assertTrue(text.contains("Flate memory line two"))
    }

    @Test fun pdfWithNoTextIsHonestlyUnreadable() {
        val pdf = pdfBytes("BT /F1 12 Tf 72 720 Td ET".toByteArray(Charsets.ISO_8859_1), flate = false)
        val ex = MemoryFileImport.extract("blank.pdf", pdf)
        assertTrue(ex is MemoryFileImport.Extracted.Unreadable)
    }

    @Test fun docxExtractsText() {
        val xml = ("<?xml version=\"1.0\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
            "<w:body><w:p><w:r><w:t>Hello docx memory</w:t></w:r></w:p>" +
            "<w:p><w:r><w:t>Second paragraph here</w:t></w:r></w:p></w:body></w:document>")
            .toByteArray(Charsets.UTF_8)
        val ex = MemoryFileImport.extract("notes.docx", zipBytes(mapOf("word/document.xml" to xml)))
        assertTrue(ex is MemoryFileImport.Extracted.Text)
        val text = (ex as MemoryFileImport.Extracted.Text).text
        assertTrue(text.contains("Hello docx memory"))
        assertTrue(text.contains("Second paragraph here"))
    }

    @Test fun textFileExtractsVerbatim() {
        val ex = MemoryFileImport.extract("prefs.txt", "Prefers short answers.\nShop opens at 9am.".toByteArray(Charsets.UTF_8))
        assertTrue(ex is MemoryFileImport.Extracted.Text)
        assertTrue((ex as MemoryFileImport.Extracted.Text).text.contains("Prefers short answers."))
    }

    @Test fun unknownBinaryIsHonestlyUnreadable() {
        val garbage = ByteArray(512) { (it * 37 % 256).toByte() }
        val ex = MemoryFileImport.extract("photo.bin", garbage)
        assertTrue("binary junk must not pretend to be text", ex is MemoryFileImport.Extracted.Unreadable)
    }

    @Test fun emptyFileIsUnreadable() {
        assertTrue(MemoryFileImport.extract("empty.txt", ByteArray(0)) is MemoryFileImport.Extracted.Unreadable)
    }

    @Test fun splitEntriesRespectsLimitAndKeepsShortParagraphsWhole() {
        val short = "Prefers short answers in Roman Urdu."
        assertEquals(listOf(short), MemoryFileImport.splitEntries(short))
        val long = "Word ".repeat(500)
        val entries = MemoryFileImport.splitEntries(long)
        assertTrue(entries.size > 1)
        assertTrue(entries.all { it.length <= MemoryStore.ENTRY_MAX_CHARS })
        assertEquals(long.trim().replace(Regex("\\s+"), " "), entries.joinToString("").replace("…", ""))
    }

    @Test fun reportDescribesWhatHappened() {
        val rep = MemoryFileImport.Report(saved = 2, alreadySaved = 1, notSavedFull = 0,
            unreadable = listOf("Skipped “photo.jpg” (not readable as text)."), truncated = false, charsRead = 100)
        val msg = rep.describe("notes.zip")
        assertTrue(msg.contains("Saved 2 memories from notes.zip"))
        assertTrue(msg.contains("1 entry was already saved"))
        assertTrue(msg.contains("Skipped"))
        val empty = MemoryFileImport.Report(0, 0, 0, listOf("No readable text was found."), truncated = false, charsRead = 0)
        assertTrue(empty.describe("blank.pdf").contains("Nothing could be saved"))
    }
}
