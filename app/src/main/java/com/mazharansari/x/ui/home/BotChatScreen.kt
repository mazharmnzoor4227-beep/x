package com.mazharansari.x.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.mazharansari.x.ui.ChatViewModel
import com.mazharansari.x.ui.Route
import com.mazharansari.x.ui.chat.BotRunningBanner
import com.mazharansari.x.ui.chat.ChatScreen
import com.mazharansari.x.ui.theme.P

/**
 * One bot's own chat, inside the Bots section (navigation restructure,
 * 2026-11-20: the Bots section shows exactly ONE bot — X Bot — and tapping
 * it opens THAT bot's own chat here; Back pops to the bot list, never to
 * home or a new chat).
 *
 * Thin wrapper: it resolves the [BotDefinition] by id, makes sure that bot's
 * thread is open ([ChatViewModel.openBotChat] — reopens its newest thread, or
 * starts its first blank one), and renders the shared transcript/composer
 * ([ChatScreen]) in bot mode. There is NO second copy of the message
 * list/composer — one transcript implementation, two headers.
 *
 * Live-run banner (Task 3 API, `ui/chat/BotRunningBanner.kt`): the drop-in
 * [BotRunningBanner] reads live state itself and renders nothing when this
 * conversation has no bot run in flight. It sits above [ChatScreen] in a
 * Column — [ChatScreen] itself is owned by another worker, so the banner
 * cannot be placed directly under its header from here; when idle it is
 * zero-height and the layout is unchanged. (If the header ever gains a
 * banner slot inside ChatScreen, move this call there and drop the wrap.)
 */
@Composable
fun BotChatScreen(vm: ChatViewModel, botId: String, back: () -> Unit, navigate: (Route) -> Unit) {
    val bots by vm.g.botStore.definitions.collectAsState()
    // X Bot is seeded on app start (AppRoot), but guarantee it here too: a
    // deep link straight to this screen must never land on a missing bot.
    LaunchedEffect(botId) { if (botId == com.mazharansari.x.data.X_BOT_ID) vm.g.botStore.ensureXBot() }
    val bot = bots.firstOrNull { it.id == botId }
    if (bot == null) {
        // The bot was removed while this screen was open: go back to the bot list.
        LaunchedEffect(Unit) { back() }
        Box(Modifier.fillMaxSize().background(P.bg))
    } else {
        LaunchedEffect(botId) { vm.openBotChat(bot) }
        Column(Modifier.fillMaxSize().background(P.bg)) {
            BotRunningBanner(vm = vm, conversationId = vm.conversationId)
            Box(Modifier.weight(1f)) {
                ChatScreen(vm, navigate, onBack = back, bot = bot)
            }
        }
    }
}
