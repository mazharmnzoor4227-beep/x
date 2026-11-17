package com.mazharansari.x.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazharansari.x.ui.ChatViewModel
import com.mazharansari.x.ui.components.Gk
import com.mazharansari.x.ui.components.GkIcon
import com.mazharansari.x.ui.components.IconTap
import com.mazharansari.x.ui.components.ScreenHeader
import com.mazharansari.x.ui.components.rememberTap
import com.mazharansari.x.ui.theme.P
import com.mazharansari.x.ui.theme.Type

/**
 * Ideas (new series, dock tab): ideas captured by the user, grouped by area
 * (Store first, then You, then anything else). Starts empty — no sample ideas.
 */
@Composable
fun IdeasScreen(vm: ChatViewModel, back: () -> Unit) {
    val p = P
    val store = vm.g.ideas
    val ideas by store.ideas.collectAsState()
    var text by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("You") }
    val groups = remember(ideas) {
        val order = listOf("Store", "You")
        ideas.groupBy { it.area }.entries.sortedWith(compareBy({ order.indexOf(it.key).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }, { it.key }))
    }

    Column(Modifier.fillMaxSize().background(p.bg)) {
        ScreenHeader("Ideas", back)
        // Add row: text + area picker.
        AddTextRow(text, { text = it }, "Capture an idea…", { store.add(text, area); text = "" })
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Store", "You").forEach { a ->
                val sel = area == a
                val tap = rememberTap { area = a }
                Box(Modifier.clip(RoundedCornerShape(50)).background(if (sel) p.text else p.card).border(1.dp, p.border, RoundedCornerShape(50)).clickable(onClick = tap).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(a, style = Type.small.copy(fontSize = 14.sp), color = if (sel) p.onAccent else p.text)
                }
            }
            Text("Ideas are grouped by area.", style = Type.tiny, color = p.text3, modifier = Modifier.align(Alignment.CenterVertically).padding(start = 4.dp))
        }
        if (ideas.isEmpty()) {
            DockEmpty("No ideas yet", "Capture the first one above. Ideas you save here stay on this phone until you act on them or delete them.")
            return@Column
        }
        LazyColumn(Modifier.weight(1f).navigationBarsPadding(), contentPadding = PaddingValues(bottom = DOCK_CLEARANCE)) {
            groups.forEach { (groupArea, list) ->
                item(key = "head-$groupArea") { SectionHead(groupArea) }
                items(list, key = { it.id }) { idea ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).clip(RoundedCornerShape(16.dp)).background(p.card).padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        CheckBox(idea.done) { store.toggleDone(idea.id) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(idea.text, style = Type.body.copy(fontSize = 16.sp, textDecoration = if (idea.done) TextDecoration.LineThrough else null),
                                color = if (idea.done) p.text3 else p.text, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            Text(timeLabel(idea.createdAt), style = Type.tiny, color = p.text3, modifier = Modifier.padding(top = 2.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        IconTap(Gk.trash, "Delete idea", 20.dp, p.text2, 40.dp) { store.remove(idea.id) }
                    }
                }
            }
        }
    }
}
