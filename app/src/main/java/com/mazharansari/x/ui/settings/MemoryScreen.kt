package com.mazharansari.x.ui.settings

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazharansari.x.data.MemoryFileImport
import com.mazharansari.x.data.MemoryStore
import com.mazharansari.x.data.MemoryTarget
import com.mazharansari.x.ui.ChatViewModel
import com.mazharansari.x.ui.components.*
import com.mazharansari.x.ui.theme.P
import com.mazharansari.x.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * v0.10 (Hermes learning loop): everything X has saved about the user, in the open.
 * The agent writes these entries with its memory tools and reads them at the start of every
 * chat; here the user can add, edit and delete them — nothing is remembered that is not
 * on this screen.
 */
@Composable
fun MemoryScreen(vm: ChatViewModel, back: () -> Unit) {
    val p = P
    val store = vm.g.memory
    val data by store.data.collectAsState()
    var editing by remember { mutableStateOf<Triple<MemoryTarget, Int, String>?>(null) }   // target, index (-1 = new), text
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var fileTarget by remember { mutableStateOf<MemoryTarget?>(null) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // Memory fix (2026-11-20): file input. The section whose "Add from file" was tapped is
    // where the entries go — the file never picks the section. Any type is accepted ("*/*"):
    // ZIP / PDF / Word / Excel / text are read by MemoryFileImport; anything unreadable is
    // reported honestly instead of pretending it was saved. Files are read only to save as
    // memories in this app, never sent anywhere. Over 10 MB is refused with a clear error.
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = fileTarget ?: return@rememberLauncherForActivityResult
        fileTarget = null
        if (uri == null || busy) return@rememberLauncherForActivityResult
        busy = true; notice = "Reading file…"; error = null
        scope.launch {
            var fileName = "file"
            val report = withContext(Dispatchers.IO) {
                runCatching {
                    fileName = ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
                    } ?: "file"
                    val bytes = ctx.contentResolver.openInputStream(uri)?.use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val buf = ByteArray(8192)
                        var n: Int
                        var total = 0L
                        while (input.read(buf).also { n = it } >= 0) {
                            total += n
                            if (total > MemoryFileImport.MAX_FILE_BYTES) break
                            out.write(buf, 0, n)
                        }
                        out.toByteArray()
                    } ?: throw IllegalStateException("Could not open that file.")
                    if (bytes.size > MemoryFileImport.MAX_FILE_BYTES)
                        throw IllegalStateException("That file is over 10 MB — too large to import as memories.")
                    MemoryFileImport.importInto(store, target, fileName, bytes)
                }
            }
            busy = false
            report.onSuccess { notice = it.describe(fileName); error = null }
            report.onFailure { error = it.message ?: "Could not read that file."; notice = null }
        }
    }

    Column(Modifier.fillMaxSize().background(p.bg)) {
        ScreenHeader("Memory", back)
        LazyColumn(Modifier.weight(1f).navigationBarsPadding(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Text("X saves short notes about you and what it learns while working, and reads them at the start of every chat — that is how it gets better at helping you. You can edit or delete anything here; X remembers only what is on this screen.",
                    style = Type.small.copy(fontSize = 14.sp, lineHeight = 20.sp), color = p.text2,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                error?.let { Text(it, style = Type.small, color = p.danger, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) }
                notice?.let { Text(it, style = Type.small, color = p.text2, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) }
            }
            memorySection(store, MemoryTarget.USER, "About you", data.user, onEdit = { editing = it },
                onAddFile = { fileTarget = it; pickFile.launch(arrayOf("*/*")) })
            memorySection(store, MemoryTarget.MEMORY, "Facts, lessons & projects", data.notes, onEdit = { editing = it },
                onAddFile = { fileTarget = it; pickFile.launch(arrayOf("*/*")) })
            item {
                Spacer(Modifier.height(10.dp))
                Text("Memory is kept small on purpose (About you: ${MemoryStore.USER_CHAR_BUDGET} characters, Facts & lessons: ${MemoryStore.MEMORY_CHAR_BUDGET}). When a section is full, X replaces or removes an old entry before adding a new one.",
                    style = Type.small.copy(fontSize = 13.sp, lineHeight = 18.sp), color = p.text3,
                    modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
    }

    editing?.let { (target, index, initial) ->
        var text by remember(target, index) { mutableStateOf(initial) }
        var dlgError by remember { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { editing = null }, containerColor = p.card, shape = RoundedCornerShape(10.dp),
            title = { Text(if (index < 0) "Add memory" else "Edit memory", style = Type.title, color = p.text) },
            text = {
                Column {
                    Field(text, { text = it; dlgError = null }, if (target == MemoryTarget.USER) "e.g. Prefers short answers in Roman Urdu" else "e.g. Store prices include delivery")
                    dlgError?.let { Spacer(Modifier.height(8.dp)); Text(it, style = Type.small, color = p.danger) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val err = if (index < 0) store.add(target, text) else store.replaceAt(target, index, text)
                    if (err == null) editing = null else dlgError = err
                }) { Text("Save", color = p.accent) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel", color = p.text2) } })
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.memorySection(
    store: MemoryStore,
    target: MemoryTarget,
    title: String,
    entries: List<String>,
    onEdit: (Triple<MemoryTarget, Int, String>) -> Unit,
    onAddFile: (MemoryTarget) -> Unit,
) {
    item { SectionLabel(title) }
    if (entries.isEmpty()) {
        item {
            Text("Nothing saved yet — X adds entries here as it learns, or add one yourself.",
                style = Type.small, color = P.text3, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
        }
    }
    itemsIndexed(entries, key = { i, e -> "${target.name}_$i" }) { i, e ->
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(P.surface).padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(e, style = Type.body.copy(fontSize = 15.sp, lineHeight = 20.sp), color = P.text, modifier = Modifier.weight(1f))
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).clickable { onEdit(Triple(target, i, e)) }, contentAlignment = Alignment.Center) {
                GkIcon(Gk.editFile, "Edit", 19.dp, P.text2)
            }
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).clickable { store.removeAt(target, i) }, contentAlignment = Alignment.Center) {
                GkIcon(Gk.trash, "Delete", 19.dp, P.danger)
            }
        }
    }
    item {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).clickable { onEdit(Triple(target, -1, "")) }, verticalAlignment = Alignment.CenterVertically) {
            GkIcon(Gk.plus, null, 18.dp, P.accent)
            Spacer(Modifier.width(8.dp))
            Text("Add", style = Type.label, color = P.accent)
        }
    }
    item {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).clickable { onAddFile(target) }, verticalAlignment = Alignment.CenterVertically) {
            GkIcon(Gk.file, null, 18.dp, P.accent)
            Spacer(Modifier.width(8.dp))
            Text("Add from file", style = Type.label, color = P.accent)
        }
    }
}
