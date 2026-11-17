package com.mazharansari.x.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazharansari.x.data.JobState
import com.mazharansari.x.ui.ChatViewModel
import com.mazharansari.x.ui.Route
import com.mazharansari.x.ui.components.Gk
import com.mazharansari.x.ui.components.GkIcon
import com.mazharansari.x.ui.components.ScreenHeader
import com.mazharansari.x.ui.components.rememberTap
import com.mazharansari.x.ui.theme.P
import com.mazharansari.x.ui.theme.Type

/**
 * Feed (new series, dock tab): derived — NO new store. "Needs you" is the live
 * approval waiting in ChatViewModel plus task runs that were interrupted; "While you
 * were out" is the recent task runs and background jobs. Tapping a run opens its
 * chat; tapping the approval goes back to Chat, where the approval card is shown.
 */
@Composable
fun FeedScreen(vm: ChatViewModel, back: () -> Unit, navigate: (Route) -> Unit = {}) {
    val p = P
    val runs by vm.g.taskRuns.records.collectAsState()
    val jobs by vm.g.jobRecords.records.collectAsState()
    val live by vm.g.runs.active.collectAsState()
    val pending = vm.pendingApproval
    val interrupted = runs.filter { it.status == "interrupted" }
    val pastRuns = runs.filter { it.status != "interrupted" }.sortedByDescending { it.updatedAt }
    val pastJobs = jobs.sortedByDescending { it.startedAt }
    val hasAnything = pending != null || interrupted.isNotEmpty() || pastRuns.isNotEmpty() || pastJobs.isNotEmpty() || live.isNotEmpty()

    Column(Modifier.fillMaxSize().background(p.bg)) {
        ScreenHeader("Feed", back)
        if (!hasAnything) {
            DockEmpty("Nothing yet", "Runs, background jobs and approvals will show up here when Axion works while you are away.")
            return@Column
        }
        LazyColumn(Modifier.weight(1f).navigationBarsPadding(), contentPadding = PaddingValues(bottom = DOCK_CLEARANCE)) {
            if (live.isNotEmpty()) {
                item { SectionHead("Running now") }
                items(live, key = { it.id }) { h ->
                    FeedRow(icon = Gk.bolt, title = h.title.ifBlank { "Working" }, detail = if (h.kind.name == "BUILD") "Build run" else "Chat run",
                        time = timeLabel(h.startedAt), onClick = if (h.ownerId != null) ({ vm.open(h.ownerId!!); back() }) else null)
                }
            }
            if (pending != null || interrupted.isNotEmpty()) {
                item { SectionHead("Needs you") }
                if (pending != null) {
                    item {
                        FeedRow(icon = Gk.shield, title = "Approval waiting", detail = pending.req.summary.take(140),
                            time = "Now", onClick = { back() })
                    }
                }
                items(interrupted, key = { it.kind + it.conversationId + it.startedAt }) { r ->
                    FeedRow(icon = Gk.history, title = r.goal.ifBlank { "A task was interrupted" }, detail = "Interrupted — open the chat to continue",
                        time = timeLabel(r.updatedAt), onClick = { vm.open(r.conversationId); back() })
                }
            }
            if (pastRuns.isNotEmpty() || pastJobs.isNotEmpty()) {
                item { SectionHead("While you were out") }
                items(pastRuns, key = { it.kind + it.conversationId + it.startedAt }) { r ->
                    FeedRow(icon = if (r.kind == "build") Gk.hammer else Gk.agent, title = r.goal.ifBlank { "A task" },
                        detail = when (r.status) { "done" -> "Finished · ${r.stepsDone} step${if (r.stepsDone == 1) "" else "s"}"; "running" -> "Running · ${r.stepsDone} steps done"; else -> r.status.replaceFirstChar { it.uppercase() } },
                        time = timeLabel(r.updatedAt), onClick = { vm.open(r.conversationId); back() })
                }
                items(pastJobs, key = { it.id }) { j ->
                    FeedRow(icon = Gk.terminal, title = j.command.take(120),
                        detail = when (j.state) {
                            JobState.DONE -> "Finished" + (j.exitCode?.let { " · exit $it" } ?: "")
                            JobState.FAILED -> "Failed" + (j.exitCode?.let { " · exit $it" } ?: "")
                            JobState.INTERRUPTED -> "Interrupted"
                            else -> "Running"
                        },
                        time = timeLabel(if (j.finishedAt > 0) j.finishedAt else j.startedAt),
                        onClick = { navigate(Route.Terminal) })
                }
            }
        }
    }
}

@Composable
private fun FeedRow(icon: Int, title: String, detail: String, time: String, onClick: (() -> Unit)?) {
    val p = P
    val tap = onClick?.let { rememberTap(it) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).clip(RoundedCornerShape(16.dp)).background(p.card)
        .then(if (tap != null) Modifier.clickable(onClick = tap) else Modifier)
        .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(p.surface), contentAlignment = Alignment.Center) {
            GkIcon(icon, null, 20.dp, p.text)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.row.copy(fontSize = 16.sp), color = p.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (detail.isNotBlank()) Text(detail, style = Type.small, color = p.text2, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(time, style = Type.tiny, color = p.text3, maxLines = 1)
    }
}
