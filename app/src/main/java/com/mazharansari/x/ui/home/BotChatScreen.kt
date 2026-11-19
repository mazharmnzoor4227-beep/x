package com.mazharansari.x.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.mazharansari.x.ui.ChatViewModel
import com.mazharansari.x.ui.Route
import com.mazharansari.x.ui.chat.ChatScreen
import com.mazharansari.x.ui.theme.P

/**
 * One bot's own chat, inside the Bots section (redesign round 2, spec §1.6).
 * Thin wrapper: it resolves the [BotDefinition] by id, makes sure that bot's
 * thread is open ([ChatViewModel.openBotChat] — reopens its newest thread, or
 * starts its first blank one), and renders the shared transcript/composer
 * ([ChatScreen]) in bot mode. There is NO second copy of the message
 * list/composer — one transcript implementation, two headers. Back pops to
 * Home (the Bots section); it never opens a new chat.
 */
@Composable
fun BotChatScreen(vm: ChatViewModel, botId: String, back: () -> Unit, navigate: (Route) -> Unit) {
    val bots by vm.g.botStore.definitions.collectAsState()
    val bot = bots.firstOrNull { it.id == botId }
    if (bot == null) {
        // The bot was removed while this screen was open: go back to Home.
        LaunchedEffect(Unit) { back() }
        Box(Modifier.fillMaxSize().background(P.bg))
    } else {
        LaunchedEffect(botId) { vm.openBotChat(bot) }
        ChatScreen(vm, navigate, onBack = back, bot = bot)
    }
}
