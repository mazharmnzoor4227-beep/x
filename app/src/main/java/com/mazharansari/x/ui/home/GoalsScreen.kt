package com.mazharansari.x.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazharansari.x.data.Goal
import com.mazharansari.x.ui.ChatViewModel
import com.mazharansari.x.ui.components.Gk
import com.mazharansari.x.ui.components.IconTap
import com.mazharansari.x.ui.components.ScreenHeader
import com.mazharansari.x.ui.components.rememberTap
import com.mazharansari.x.ui.theme.P
import com.mazharansari.x.ui.theme.Type

/**
 * Goals (new series, dock tab): goals with checkable steps. Progress is a real
 * ring from the step counts — never a stored or guessed number. Starts empty.
 */
@Composable
fun GoalsScreen(vm: ChatViewModel, back: () -> Unit) {
    val p = P
    val store = vm.g.goals
    val goals by store.goals.collectAsState()
    var title by remember { mutableStateOf("") }
    var stepsText by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }

    Column(Modifier.fillMaxSize().background(p.bg)) {
        ScreenHeader("Goals", back)
        // Add row: title + one step per line.
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(20.dp)).background(p.card).padding(14.dp)) {
            BasicTextField(title, { title = it }, singleLine = true, textStyle = Type.body.copy(color = p.text), cursorBrush = SolidColor(p.text),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner -> if (title.isEmpty()) Text("New goal…", style = Type.body, color = p.text3); inner() })
            Spacer(Modifier.height(8.dp))
            BasicTextField(stepsText, { stepsText = it }, textStyle = Type.small.copy(color = p.text), cursorBrush = SolidColor(p.text),
                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                decorationBox = { inner -> if (stepsText.isEmpty()) Text("Steps, one per line (optional)", style = Type.small, color = p.text3); inner() })
            Spacer(Modifier.height(10.dp))
            val tap = rememberTap { if (store.add(title, stepsText.lines()) != null) { title = ""; stepsText = "" } }
            Box(Modifier.align(Alignment.End).clip(RoundedCornerShape(50)).background(if (title.isBlank()) p.surface2 else p.text).clickable(enabled = title.isNotBlank(), onClick = tap).padding(horizontal = 18.dp, vertical = 9.dp)) {
                Text("Add goal", style = Type.small.copy(fontWeight = FontWeight.SemiBold), color = if (title.isBlank()) p.text3 else p.onAccent)
            }
        }
        if (goals.isEmpty()) {
            DockEmpty("No goals yet", "Add the first one above — a title and its steps. Checking a step off moves the ring.")
            return@Column
        }
        LazyColumn(Modifier.weight(1f).navigationBarsPadding(), contentPadding = PaddingValues(top = 8.dp, bottom = DOCK_CLEARANCE), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(goals, key = { it.id }) { goal ->
                GoalCard(goal, expanded = goal.id in expanded,
                    onToggleExpand = { expanded = if (goal.id in expanded) expanded - goal.id else expanded + goal.id },
                    onToggleStep = { i -> store.toggleStep(goal.id, i) },
                    onDelete = { store.remove(goal.id) })
            }
        }
    }
}

@Composable
private fun GoalCard(goal: Goal, expanded: Boolean, onToggleExpand: () -> Unit, onToggleStep: (Int) -> Unit, onDelete: () -> Unit) {
    val p = P
    val tap = rememberTap(onToggleExpand)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp)).background(p.card).clickable(onClick = tap).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { goal.progress }, modifier = Modifier.size(52.dp), strokeWidth = 4.dp, color = p.accent, trackColor = p.border)
                Text("${(goal.progress * 100).toInt()}%", style = Type.tiny.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = p.text)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(goal.title, style = Type.row.copy(fontSize = 16.sp), color = p.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (goal.steps.isEmpty()) "No steps yet" else "${goal.doneCount} of ${goal.steps.size} steps", style = Type.small, color = p.text2, modifier = Modifier.padding(top = 2.dp))
            }
            IconTap(Gk.trash, "Delete goal", 20.dp, p.text2, 40.dp, onDelete)
        }
        AnimatedVisibility(expanded && goal.steps.isNotEmpty()) {
            Column(Modifier.padding(top = 12.dp)) {
                goal.steps.forEachIndexed { i, s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        CheckBox(s.done) { onToggleStep(i) }
                        Spacer(Modifier.width(12.dp))
                        Text(s.text, style = Type.body.copy(fontSize = 15.sp), color = if (s.done) p.text3 else p.text, modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (goal.steps.isNotEmpty()) Text(if (expanded) "Hide steps" else "Show steps", style = Type.tiny, color = p.text3, modifier = Modifier.padding(top = 8.dp, start = 64.dp))
    }
}
