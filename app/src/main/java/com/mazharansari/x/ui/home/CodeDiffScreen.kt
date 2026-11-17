package com.mazharansari.x.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazharansari.x.build.ChangeGate
import com.mazharansari.x.build.PatchEngine
import com.mazharansari.x.ui.ChatViewModel
import com.mazharansari.x.ui.components.Gk
import com.mazharansari.x.ui.components.GkIcon
import com.mazharansari.x.ui.components.ScreenHeader
import com.mazharansari.x.ui.components.rememberTap
import com.mazharansari.x.ui.theme.P
import com.mazharansari.x.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Code diff (new series): the pending Build change, file by file, as real -/+
 * lines from the ChangeGate before/after texts (the same source the review
 * sheet uses). Approve applies the ticked files, Reject throws them all away.
 *
 * The Grok mock shows a "12 passed · 0 failed" test pill. There is NO real
 * source for per-test pass/fail counts: ProjectVerifier returns only a
 * transient problems list (no counts, nothing persisted) and the BuildLoop
 * outcome is not stored. So no test-count pill is rendered here — the pill
 * shows the real +/− line counts instead. Inventing test numbers would be
 * demo data.
 */
@Composable
fun CodeDiffScreen(vm: ChatViewModel, back: () -> Unit) {
    val p = P
    val review = vm.pendingReview
    Column(Modifier.fillMaxSize().background(p.bg)) {
        ScreenHeader("Code", back)
        if (review == null) {
            DockEmpty("No changes waiting", "When Axion edits a project, the diff shows up here for review before anything is applied.")
            return@Column
        }
        val set = review.set
        val selected = remember(review.projectId, set.createdAt) { mutableStateListOf<String>().apply { addAll(set.changes.map { it.path }) } }
        var open by remember(review.projectId, set.createdAt) { mutableStateOf<String?>(set.changes.singleOrNull()?.path) }
        Text("${set.changes.size} file${if (set.changes.size == 1) "" else "s"} · +${set.totalAdded} −${set.totalRemoved}. Nothing is applied until you approve.",
            style = Type.small, color = p.text2, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        LazyColumn(Modifier.weight(1f).navigationBarsPadding(), contentPadding = PaddingValues(bottom = DOCK_CLEARANCE)) {
            items(set.changes, key = { it.path }) { c ->
                val isOpen = open == c.path
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).clip(RoundedCornerShape(14.dp)).background(p.card)) {
                    Row(Modifier.fillMaxWidth().clickable { open = if (isOpen) null else c.path }.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        val on = c.path in selected
                        Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(if (on) P.text else Color.Transparent)
                            .clickable { if (on) selected.remove(c.path) else selected.add(c.path) }, contentAlignment = Alignment.Center) {
                            if (on) GkIcon(Gk.check, null, 15.dp, P.onAccent)
                            else Box(Modifier.matchParentSize().clip(RoundedCornerShape(7.dp)).background(p.surface2))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.path, style = Type.mono.copy(fontSize = 13.sp), color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(when {
                                c.binary -> "${statusWord(c.status)} · binary · ${c.size / 1024} KB"
                                else -> "${statusWord(c.status)} · +${c.added} −${c.removed}"
                            }, style = Type.small, color = statusColor(c.status))
                        }
                    }
                    if (isOpen) DiffBody(vm, c)
                }
            }
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val rej = rememberTap { vm.rejectReview() }
            Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(50)).background(p.surface2).clickable(onClick = rej), contentAlignment = Alignment.Center) {
                Text("Reject", style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = Color(0xFFF04438))
            }
            val appr = rememberTap { vm.approveReview(selected.toSet()) }
            Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(50)).background(if (selected.isEmpty()) p.surface2 else p.text)
                .clickable(enabled = selected.isNotEmpty(), onClick = appr), contentAlignment = Alignment.Center) {
                Text(if (selected.size == set.changes.size) "Approve all" else "Approve ${selected.size}", style = Type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = if (selected.isEmpty()) p.text3 else p.onAccent)
            }
        }
    }
}

private fun statusWord(s: ChangeGate.Status) = when (s) {
    ChangeGate.Status.ADDED -> "New file"
    ChangeGate.Status.MODIFIED -> "Edited"
    ChangeGate.Status.DELETED -> "Deleted"
}

private fun statusColor(s: ChangeGate.Status) = when (s) {
    ChangeGate.Status.ADDED -> Color(0xFF3FB950)
    ChangeGate.Status.MODIFIED -> Color(0xFFE3B341)
    ChangeGate.Status.DELETED -> Color(0xFFF04438)
}

/** Real -/+ lines for one staged file, with 2 lines of context around each change. */
@Composable
private fun DiffBody(vm: ChatViewModel, c: ChangeGate.FileChange) {
    val p = P
    val lines by produceState<List<PatchEngine.Line>?>(null, c.path) {
        value = withContext(Dispatchers.IO) {
            val (old, new) = vm.reviewTexts(c)
            if (old == null && new == null) emptyList() else PatchEngine.diff(old.orEmpty(), new.orEmpty())
        }
    }
    Column(Modifier.fillMaxWidth().background(if (p.isDark) Color(0xFF0E0E0E) else Color(0xFFF1F1F1)).padding(vertical = 6.dp)) {
        val ls = lines
        when {
            c.binary -> Text("Binary file - cannot be shown as text.", style = Type.small, color = p.text2, modifier = Modifier.padding(12.dp))
            ls == null -> Text("Loading…", style = Type.small, color = p.text2, modifier = Modifier.padding(12.dp))
            ls.isEmpty() -> Text("No text to show.", style = Type.small, color = p.text2, modifier = Modifier.padding(12.dp))
            else -> {
                val keep = BooleanArray(ls.size)
                ls.forEachIndexed { i, l -> if (l.op != PatchEngine.Op.SAME) for (j in (i - 2).coerceAtLeast(0)..(i + 2).coerceAtMost(ls.lastIndex)) keep[j] = true }
                val shown = ls.indices.filter { keep[it] }.take(400)
                Column(Modifier.heightIn(max = 360.dp).horizontalScroll(rememberScrollState())) {
                    var prev = -2
                    for (i in shown) {
                        if (prev >= 0 && i != prev + 1) Text("  …", style = Type.mono.copy(fontSize = 11.sp), color = p.text3)
                        prev = i
                        val l = ls[i]
                        val (mark, bg, fg) = when (l.op) {
                            PatchEngine.Op.ADD -> Triple("+", Color(0xFF3FB950).copy(alpha = 0.16f), Color(0xFF3FB950))
                            PatchEngine.Op.DEL -> Triple("−", Color(0xFFF04438).copy(alpha = 0.16f), Color(0xFFF04438))
                            else -> Triple(" ", Color.Transparent, p.text2)
                        }
                        Text("$mark ${l.text}", style = Type.mono.copy(fontSize = 11.5.sp, lineHeight = 16.sp), color = fg, softWrap = false,
                            modifier = Modifier.background(bg).padding(horizontal = 10.dp).widthIn(min = 300.dp))
                    }
                    if (shown.size >= 400) Text("  … diff cut after 400 lines", style = Type.mono.copy(fontSize = 11.sp), color = p.text3)
                }
            }
        }
    }
}
