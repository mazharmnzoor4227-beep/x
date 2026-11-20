package com.mazharansari.x.ui.chat

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mazharansari.x.ui.ChatViewModel
import com.mazharansari.x.ui.components.Gk
import com.mazharansari.x.ui.components.GkIcon
import com.mazharansari.x.ui.theme.P
import com.mazharansari.x.ui.theme.Type
import com.mazharansari.x.work.BotRunState
import kotlinx.coroutines.delay

/**
 * The running state of one bot run, shown at the top of its bot chat while
 * the bot works — including while the app is backgrounded or the user has
 * left for another chat and come back: the state comes from the run-id
 * ledger ([com.mazharansari.x.work.RunRegistry] via
 * [ChatViewModel.botRuns]), never from the screen that started the run.
 *
 * Ink Ledger: dark ink surface, bronze accent, rectilinear 8dp corners,
 * mono metadata line. No emoji, no green.
 *
 * PUBLIC API (for BotChatScreen — the navigation worker owns that file):
 *
 *   // 1. Drop-in form — reads the live state itself, shows nothing when
 *   //    this conversation has no bot run in flight:
 *   BotRunningBanner(vm = vm, conversationId = vm.conversationId)
 *
 *   // 2. Explicit-state form — for previews / tests, or when the caller
 *   //    already collected vm.botRuns:
 *   val state = vm.botRunState(vm.conversationId)
 *   if (state != null) BotRunningBanner(
 *       state = state,
 *       onStop = { vm.stopBotRun(state.runId) },
 *   )
 *
 * Either form: place it directly under the chat header, above the message
 * list. Tapping Stop cancels that run only (see [ChatViewModel.stopBotRun]);
 * other runs in flight are untouched.
 */

/** "Draft the menu · 2 workers · 01:23" — task, live worker count, elapsed time. */
fun botRunningSubtitle(state: BotRunState, nowMs: Long = System.currentTimeMillis()): String {
    val task = state.title.ifBlank { "Working" }
    val elapsed = clockLabel((nowMs - state.startedAt).coerceAtLeast(0L))
    return if (state.workerCount > 0) "$task · ${state.workerCount} worker${if (state.workerCount == 1) "" else "s"} · $elapsed"
    else "$task · $elapsed"
}

/** Drop-in form: shows the live bot run for [conversationId], or nothing. */
@Composable
fun BotRunningBanner(vm: ChatViewModel, conversationId: String?, modifier: Modifier = Modifier) {
    val runs by vm.botRuns.collectAsState()
    val state = runs.firstOrNull { it.conversationId == conversationId } ?: return
    BotRunningBanner(state = state, onStop = { vm.stopBotRun(state.runId) }, modifier = modifier)
}

/** Explicit-state form: the running banner for one live bot run. */
@Composable
fun BotRunningBanner(state: BotRunState, onStop: () -> Unit, modifier: Modifier = Modifier) {
    var nowMs by remember(state.runId) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.runId) {
        while (true) {
            delay(1000)
            nowMs = System.currentTimeMillis()
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, P.border, RoundedCornerShape(8.dp))
            .padding(12.dp)
            .semantics { contentDescription = "Bot running: ${state.botName}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GkIcon(Gk.bolt, "Working", 20.dp, P.accent)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                text = "${state.botName} is working",
                style = Type.row,
                color = P.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = botRunningSubtitle(state, nowMs),
                style = Type.mono,
                color = P.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "Stop",
            style = Type.label,
            color = P.danger,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, P.danger, RoundedCornerShape(6.dp))
                .clickable { onStop() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .semantics { contentDescription = "Stop bot run"; role = Role.Button },
        )
    }
}
