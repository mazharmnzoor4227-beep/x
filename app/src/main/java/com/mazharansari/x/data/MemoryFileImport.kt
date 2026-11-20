package com.mazharansari.x.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater
import java.util.zip.ZipInputStream

/**
 * Memory fix (2026-11-20): memories from FILES, not just typed text.
 *
 * The Memory screen used to accept text only. This helper reads a user-picked file and turns
 * its contents into short memory entries: ZIP (text files inside), PDF (extracted text),
 * Word/Excel (.docx/.xlsx), plain text, and — for anything else — a best-effort read as text
 * when the bytes really are text. It never sends the file anywhere and never invents content:
 * a file (or a part of one) that cannot be read is reported honestly in the [Report] instead
 * of being silently dropped or faked. Pure Kotlin, no Android classes, so it is unit-testable
 * on the JVM. Budgets are enforced by the store itself: entries are capped at
 * [MemoryStore.ENTRY_MAX_CHARS] and a full section stops saving with an honest note.
 */
object MemoryFileImport {
    const val MAX_FILE_BYTES = 10 * 1024 * 1024
    const val MAX_TOTAL_CHARS = 200_000
    private const val MAX_ZIP_ENTRIES = 200
    private const val MAX_ENTRY_BYTES = 512 * 1024
    private const val MAX_DECOMPRESSED_BYTES = 20 * 1024 * 1024

    private val TEXT_EXTS = setOf(
        "txt", "md", "markdown", "csv", "json", "log", "xml", "html", "htm", "yaml", "yml",
        "properties", "srt", "tsv", "text", "rst", "adoc", "kt", "java", "py", "js", "ts",
        "sh", "sql", "ini", "cfg", "conf",
    )

    sealed interface Extracted {
        data class Text(val text: String, val notes: List<String> = emptyList(), val truncated: Boolean = false) : Extracted
        data class Unreadable(val reason: String) : Extracted
    }

    data class Report(
        val saved: Int,
        val alreadySaved: Int,
        val notSavedFull: Int,
        val unreadable: List<String>,
        val truncated: Boolean,
        val charsRead: Int,
    ) {
        /** One plain sentence the Memory screen can show after an import. */
        fun describe(fileName: String): String {
            val parts = ArrayList<String>()
            if (saved > 0) parts += "Saved $saved ${if (saved == 1) "memory" else "memories"} from $fileName"
            if (alreadySaved > 0) parts += "$alreadySaved ${if (alreadySaved == 1) "entry was" else "entries were"} already saved"
            if (notSavedFull > 0) parts += "$notSavedFull ${if (notSavedFull == 1) "entry did" else "entries did"} not fit — the section is full"
            if (unreadable.isNotEmpty()) parts += unreadable.joinToString(" ")
            if (truncated) parts += "Only the first part of the file was read."
            // Nothing saved at all must say so first — the old code only said
            // it when parts was empty, so a report that only carried an
            // unreadable reason ("No readable text was found.") never said
            // "Nothing could be saved" and the reason read as the whole story.
            if (saved == 0 && alreadySaved == 0 && notSavedFull == 0) {
                val lead = "Nothing could be saved from $fileName."
                val rest = parts.joinToString(" ")
                return if (rest.isEmpty()) lead else "$lead $rest"
            }
            if (parts.isEmpty()) return "Nothing could be saved from $fileName."
            return parts.joinToString(". ") + "."
        }
    }

    fun extract(fileName: String, bytes: ByteArray): Extracted {
        if (bytes.isEmpty()) return Extracted.Unreadable("The file is empty.")
        if (bytes.size > MAX_FILE_BYTES) return Extracted.Unreadable("The file is over 10 MB — too large to import as memories.")
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "zip", "apk", "jar" -> extractZip(bytes, fileName)
            "docx" -> extractDocx(bytes)
            "xlsx" -> extractXlsx(bytes)
            "pdf" -> extractPdf(bytes)
            "doc", "xls", "ppt" -> Extracted.Unreadable(
                "Old Microsoft Office files (.doc, .xls, .ppt) cannot be read on this phone. Save it as .docx, .xlsx or .txt and try again.")
            in TEXT_EXTS -> Extracted.Text(decodeText(bytes))
            else -> sniffText(fileName, bytes)
        }
    }

    /** Best effort for an unknown type: read it as text only when the bytes really are text. */
    private fun sniffText(fileName: String, bytes: ByteArray): Extracted {
        val raw = String(bytes, Charsets.UTF_8)
        val bad = raw.count { it == '\uFFFD' }
        val printable = raw.count { it == '\n' || it == '\r' || it == '\t' || it.code in 32..0x2FFF }
        if (raw.isNotBlank() && raw.length > 10 && bad * 20 <= raw.length && printable * 100 / raw.length >= 95)
            return Extracted.Text(decodeText(bytes), notes = listOf("Read as plain text — the file type was not recognised."))
        return Extracted.Unreadable(
            "“$fileName” could not be read as text (it looks like binary data). Text, PDF, ZIP and Word/Excel files can be imported.")
    }

    private fun decodeText(bytes: ByteArray): String =
        String(bytes, Charsets.UTF_8).replace('\uFFFD', ' ')

    private fun extractZip(bytes: ByteArray, fileName: String): Extracted {
        val out = StringBuilder()
        val notes = ArrayList<String>()
        var entries = 0
        var textEntries = 0
        var total = 0L
        var truncated = false
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    entries++
                    if (entries > MAX_ZIP_ENTRIES) { truncated = true; break }
                    if (!e.isDirectory) {
                        val name = e.name
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (ext in TEXT_EXTS) {
                            val data = readCapped(zip, MAX_ENTRY_BYTES)
                            if (data == null) { notes += "Skipped “$name” (over 512 KB)."; }
                            else {
                                total += data.size
                                if (total > MAX_DECOMPRESSED_BYTES) { truncated = true; break }
                                out.append("--- ").append(name).append(" ---\n")
                                out.append(decodeText(data)).append("\n\n")
                                textEntries++
                            }
                        } else if (name != "word/document.xml" && name != "xl/sharedStrings.xml") {
                            notes += "Skipped “$name” (not readable as text)."
                        }
                    }
                    e = zip.nextEntry
                }
            }
        } catch (ex: Exception) {
            return Extracted.Unreadable("Could not open “$fileName” as a ZIP file — it may be corrupt or password-protected (${ex.message}).")
        }
        if (textEntries == 0) return Extracted.Unreadable(
            "No readable text files were found inside “$fileName” ($entries ${if (entries == 1) "file" else "files"} checked).")
        if (truncated) notes += "Only the first $MAX_ZIP_ENTRIES files were read."
        return cap(out.toString(), notes, truncated)
    }

    private fun extractDocx(bytes: ByteArray): Extracted {
        val xml = zipEntryText(bytes, "word/document.xml")
            ?: return Extracted.Unreadable("This .docx file has no readable document text.")
        val text = xml
            .replace(Regex("</w:p>|<w:tab[^>]*/>|<w:br[^>]*/>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
        return if (text.isBlank()) Extracted.Unreadable("This .docx file has no readable text.")
        else cap(text, emptyList(), false)
    }

    private fun extractXlsx(bytes: ByteArray): Extracted {
        val xml = zipEntryText(bytes, "xl/sharedStrings.xml")
            ?: return Extracted.Unreadable("This .xlsx file has no readable text (only numbers, or an unusual layout).")
        val cells = Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
            .map { it.groupValues[1].replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&") }
            .filter { it.isNotBlank() }.toList()
        if (cells.isEmpty()) return Extracted.Unreadable("This .xlsx file has no readable text.")
        return cap(cells.joinToString("\n"), emptyList(), false)
    }

    /** Reads one named entry out of a ZIP container (.docx/.xlsx), or null when it is absent. */
    private fun zipEntryText(bytes: ByteArray, wanted: String): String? {
        return try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    if (!e.isDirectory && e.name == wanted) {
                        val data = readCapped(zip, MAX_ENTRY_BYTES) ?: return null
                        return decodeText(data)
                    }
                    e = zip.nextEntry
                }
                null
            }
        } catch (ex: Exception) { null }
    }

    /** Reads at most [cap] bytes; null when the stream is longer (the entry is skipped). */
    private fun readCapped(input: java.io.InputStream, cap: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var n: Int
        while (input.read(buf).also { n = it } >= 0) {
            if (out.size() + n > cap) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun cap(text: String, notes: List<String>, truncated: Boolean): Extracted.Text {
        return if (text.length > MAX_TOTAL_CHARS)
            Extracted.Text(text.take(MAX_TOTAL_CHARS), notes, truncated = true)
        else Extracted.Text(text, notes, truncated)
    }

    // ---- PDF: a small dependency-free text extractor (the app had only a PDF *writer*). ----
    //
    // Real PDFs put their text in content streams as literal "(…)" or hex "<…>" strings after
    // Tj/TJ operators, usually Flate-compressed. This pulls those strings out of BT…ET blocks.
    // It honestly cannot read scanned-image PDFs, encrypted PDFs, or text drawn with embedded
    // fonts that remap character codes — those come back Unreadable with the reason, never as
    // invented text.

    fun extractPdf(bytes: ByteArray): Extracted {
        val raw = String(bytes, Charsets.ISO_8859_1)
        val streamRe = Regex("stream\r?\n")
        val found = StringBuilder()
        var streams = 0
        var pos = 0
        while (streams < 100) {
            val m = streamRe.find(raw, pos) ?: break
            val start = m.range.last + 1
            val end = raw.indexOf("endstream", start)
            if (end < 0) break
            streams++
            val chunk = raw.substring(start, end).trimEnd('\r', '\n', ' ').toByteArray(Charsets.ISO_8859_1)
            val decoded = inflate(chunk) ?: chunk
            if (decoded.size > 2 * 1024 * 1024) { pos = end + 9; continue }
            found.append(String(decoded, Charsets.ISO_8859_1)).append('\n')
            pos = end + 9
        }
        val text = pdfStrings(found.toString())
        if (text.isBlank()) return Extracted.Unreadable(
            "No readable text was found in this PDF — it may be scanned images, or encrypted.")
        return cap(text, emptyList(), false)
    }

    private fun inflate(bytes: ByteArray): ByteArray? {
        for (nowrap in listOf(false, true)) {
            try {
                val inf = Inflater(nowrap)
                inf.setInput(bytes)
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (!inf.finished()) {
                    val n = inf.inflate(buf)
                    if (n == 0) break
                    out.write(buf, 0, n)
                    if (out.size() > 2 * 1024 * 1024) break
                }
                inf.end()
                val res = out.toByteArray()
                if (res.isNotEmpty()) return res
            } catch (e: Exception) {
                // Not zlib/deflate data — the caller falls back to the raw bytes.
            }
        }
        return null
    }

    /** Literal and hex strings inside BT…ET blocks, joined into readable lines. */
    private fun pdfStrings(decoded: String): String {
        val litRe = Regex("""\((?:\\.|[^\\()])*\)""")
        val hexRe = Regex("<([0-9A-Fa-f\\s]+)>")
        val blocks = Regex("BT(.*?)ET", RegexOption.DOT_MATCHES_ALL).findAll(decoded)
        val lines = ArrayList<String>()
        for (b in blocks) {
            val body = b.groupValues[1]
            val parts = ArrayList<String>()
            for (m in litRe.findAll(body)) parts += unescapePdfLiteral(m.value.substring(1, m.value.length - 1))
            for (m in hexRe.findAll(body)) {
                val hex = m.groupValues[1].filter { !it.isWhitespace() }
                if (hex.length >= 2 && hex.length % 2 == 0) {
                    val bytes = ByteArray(hex.length / 2) { i ->
                        hex.substring(i * 2, i * 2 + 2).toIntOrNull(16)?.toByte() ?: 0
                    }
                    parts += if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
                        String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
                    else String(bytes, Charsets.ISO_8859_1)
                }
            }
            val line = parts.joinToString(" ").replace(Regex("\\s+"), " ").trim()
            if (line.isNotEmpty()) lines += line
        }
        return lines.joinToString("\n")
    }

    private fun unescapePdfLiteral(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                val n = s[i + 1]
                when {
                    n == 'n' -> { out.append('\n'); i += 2 }
                    n == 'r' -> { out.append('\r'); i += 2 }
                    n == 't' -> { out.append('\t'); i += 2 }
                    n == 'b' -> { out.append('\b'); i += 2 }
                    n == 'f' -> { out.append('\u000C'); i += 2 }
                    n == '(' || n == ')' || n == '\\' -> { out.append(n); i += 2 }
                    n in '0'..'7' -> {
                        var j = i + 1
                        var v = 0
                        while (j < s.length && j < i + 4 && s[j] in '0'..'7') { v = v * 8 + (s[j] - '0'); j++ }
                        out.append(v.toChar()); i = j
                    }
                    n == '\r' || n == '\n' -> { i += 2; if (n == '\r' && i < s.length && s[i] == '\n') i++ }
                    else -> { out.append(n); i += 2 }
                }
            } else { out.append(c); i++ }
        }
        return out.toString()
    }

    /**
     * Splits extracted text into memory-sized entries (each at most ENTRY_MAX_CHARS).
     * Paragraphs first, then sentences, then a hard split — so an entry is a whole thought
     * whenever it fits, never a mid-word cut.
     */
    fun splitEntries(text: String): List<String> {
        val max = MemoryStore.ENTRY_MAX_CHARS
        val out = ArrayList<String>()
        val paras = text.split(Regex("\\n\\s*\\n|\\r\\n\\s*\\r\\n"))
        for (para in paras) {
            val p = para.replace(Regex("\\s+"), " ").trim()
            if (p.isEmpty()) continue
            if (p.length <= max) { out += p; continue }
            val sentences = p.split(Regex("(?<=[.!?؟۔])\\s+"))
            val cur = StringBuilder()
            fun flush() { if (cur.isNotBlank()) { out += cur.toString().trim(); cur.setLength(0) } }
            for (s in sentences) {
                val sen = s.trim()
                if (sen.isEmpty()) continue
                if (sen.length > max) {
                    flush()
                    var rest = sen
                    while (rest.length > max) { out += rest.take(max - 1) + "…"; rest = rest.drop(max - 1) }
                    if (rest.isNotBlank()) out += rest
                    continue
                }
                if (cur.isNotEmpty() && cur.length + 1 + sen.length > max) flush()
                if (cur.isNotEmpty()) cur.append(' ')
                cur.append(sen)
            }
            flush()
        }
        return out
    }

    /**
     * The whole import: extract, split, and save into [store] under [target]. The store's own
     * duplicate check and section budget decide what actually lands — this only counts it.
     * The section is the one the user tapped "Add from file" under; the file never chooses it.
     */
    fun importInto(store: MemoryStore, target: MemoryTarget, fileName: String, bytes: ByteArray): Report {
        return when (val ex = extract(fileName, bytes)) {
            is Extracted.Unreadable -> Report(0, 0, 0, listOf(ex.reason), truncated = false, charsRead = 0)
            is Extracted.Text -> {
                val text = ex.text.trim()
                if (text.isEmpty()) return Report(0, 0, 0, ex.notes + "No text was found in this file.", ex.truncated, 0)
                var saved = 0
                var dup = 0
                var full = 0
                for (entry in splitEntries(text)) {
                    when (val err = store.add(target, entry)) {
                        null -> saved++
                        else -> if (err.startsWith("Already saved")) dup++ else full++
                    }
                }
                Report(saved, dup, full, ex.notes, ex.truncated, text.length)
            }
        }
    }
}
