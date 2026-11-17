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
 * Activity (new series): one timestamped list of everything Axion did — live
 * runs, persisted task runs and background jobs, newest first.
 *
 * Note on ToolLog: ui/settings/ToolLog.kt is the Developer-tools INSTALL log
 * (agy / Linux setup output + retry), not a general event stream, so there is
 * no tool-call event feed to list here. Task runs + job records are the real
 * activity history the app keeps.
 */
@Composable
fun ActivityScreen(vm: ChatViewModel, back: () -> Unit, navigate: (Route) -> Unit = {}) {
    val p = P
    val runs by vm.g.taskRuns.records.collectAsState()
    val jobs by vm.g.jobRecords.records.collectAsState()
    val live by vm.g.runs.active.collectAsState()
    val rows = rememberRows(runs, jobs, live, onOpenChat = { id -> vm.open(id); back() }, onOpenTerminal = { navigate(Route.Terminal) })

    Column(Modifier.fillMaxSize().background(p.bg)) {
        ScreenHeader("Activity", back)
        if (rows.isEmpty()) {
            DockEmpty("No activity yet", "Runs and background jobs show up here with their time, newest first.")
            return@Column
        }
        LazyColumn(Modifier.weight(1f).navigationBarsPadding(), contentPadding = PaddingValues(bottom = DOCK_CLEARANCE)) {
            items(rows, key = { it.key }) { r ->
                val tap = r.onClick?.let { rememberTap(it) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).clip(RoundedCornerShape(16.dp)).background(p.card)
                    .then(if (tap != null) Modifier.clickable(onClick = tap) else Modifier)
                    .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(p.surface), contentAlignment = Alignment.Center) {
                        GkIcon(r.icon, null, 20.dp, p.text)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.title, style = Type.row.copy(fontSize = 16.sp), color = p.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(r.detail, style = Type.small, color = p.text2, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(r.time, style = Type.tiny, color = p.text3, maxLines = 1)
                }
            }
        }
    }
}

private data class ActivityRow(val key: String, val at: Long, val icon: Int, val title: String, val detail: String, val time: String, val onClick: (() -> Unit)?)

@Composable
private fun rememberRows(
    runs: List<com.mazharansari.x.data.TaskRunRecord>,
    jobs: List<com.mazharansari.x.data.JobRecord>,
    live: List<com.mazharansari.x.work.RunRegistry.Handle>,
    onOpenChat: (String) -> Unit,
    onOpenTerminal: () -> Unit,
): List<ActivityRow> {
    // Rows are built from the three live lists; recomposition re-sorts them.
    val out = ArrayList<ActivityRow>()
    live.forEach { h ->
        out += ActivityRow("live-${h.id}", h.startedAt, Gk.bolt, h.title.ifBlank { "Working" }, "Running now",
            timeLabel(h.startedAt), h.ownerId?.let { id -> { onOpenChat(id) } })
    }
    runs.forEach { r ->
        out += ActivityRow("run-${r.kind}-${r.conversationId}-${r.startedAt}", r.updatedAt,
            if (r.kind == "build") Gk.hammer else Gk.agent, r.goal.ifBlank { "A task" },
            when (r.status) { "done" -> "Finished · ${r.stepsDone} steps"; "running" -> "Running · ${r.stepsDone} steps done"; "interrupted" -> "Interrupted"; else -> r.status },
            dateTimeLabel(r.updatedAt), { onOpenChat(r.conversationId) })
    }
    jobs.forEach { j ->
        out += ActivityRow("job-${j.id}", if (j.finishedAt > 0) j.finishedAt else j.startedAt, Gk.terminal, j.command.take(120),
            when (j.state) {
                JobState.DONE -> "Finished" + (j.exitCode?.let { " · exit $it" } ?: "")
                JobState.FAILED -> "Failed" + (j.exitCode?.let { " · exit $it" } ?: "")
                JobState.INTERRUPTED -> "Interrupted"
                else -> "Running"
            },
            dateTimeLabel(if (j.finishedAt > 0) j.finishedAt else j.startedAt), onOpenTerminal)
    }
    return out.sortedByDescending { it.at }
}
