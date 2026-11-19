package com.mazharansari.x.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.mazharansari.x.ui.components.LocalHaptics
import com.mazharansari.x.ui.components.LocalTouchSound
import com.mazharansari.x.ui.AnimationGate
import com.mazharansari.x.ui.chat.ChatScreen
import com.mazharansari.x.ui.chat.SplashScreen
import com.mazharansari.x.ui.settings.*
import com.mazharansari.x.ui.theme.XLook
import com.mazharansari.x.ui.theme.XTheme
import com.mazharansari.x.ui.theme.P

@Composable
fun AppRoot(vm: ChatViewModel) {
    val settings by vm.g.settings.state.collectAsState()
    val stack = remember { mutableStateListOf<Route>(Route.Home) }
    // Splash shows once per app start, after onboarding; skipped in tests.
    var splashDone by remember { mutableStateOf(AnimationGate.inTestRuntime) }
    val ctx = LocalContext.current
    LaunchedEffect(vm.toast) { vm.toast?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); vm.toast = null } }
    XTheme(when (settings.theme) { "light" -> XLook.Light; "system" -> XLook.System; else -> XLook.Dark }, settings.textScale) {
        val onboarding = com.mazharansari.x.ui.auth.Onboarding.enabled
        // First run: intro → permissions → the app, cross-faded. No sign-in: the app opens straight in.
        val stage = when {
            onboarding && !settings.introShown -> 0
            onboarding && !settings.permsShown -> 1
            else -> 3
        }
        Crossfade(targetState = stage, animationSpec = tween(320), label = "firstRun") { st -> when (st) {
        0 -> com.mazharansari.x.ui.auth.IntroScreen { vm.g.settings.update { it.copy(introShown = true) } }
        1 -> com.mazharansari.x.ui.auth.PermissionsOnboarding({ vm.g.settings.update { it.copy(permsShown = true) } })
        else ->
        // Home list: the splash (wordmark, ~700 ms, tap to skip) shows
        // once per app start, after the onboarding stages. Skipped in tests.
        if (!splashDone) SplashScreen { splashDone = true } else
        CompositionLocalProvider(LocalHaptics provides settings.haptics, LocalTouchSound provides settings.touchSound) {
            var goingBack by remember { mutableStateOf(false) }
            val nav: (Route) -> Unit = { if (stack.last() != it) { goingBack = false; stack.add(it) } }
            // v0.5.2 (mazhar's test): each screen keeps its saved state (scroll position) while it is in the stack, so
            // coming back from a sub-screen lands where you were; a screen that is closed starts fresh next time.
            val saved = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
            val back: () -> Unit = { if (stack.size > 1) { goingBack = true; saved.removeState(stack.last().toString()); stack.removeAt(stack.lastIndex) } }
            BackHandler(stack.size > 1) { back() }
            LaunchedEffect(vm.debugStart) { vm.debugStart?.let { nav(it) } }
            Box(Modifier.fillMaxSize().background(P.bg)) {
                AnimatedContent(
                    targetState = stack.last(), label = "nav",
                    transitionSpec = {
                        // v0.4 tap-latency pass: screen changes in 150 ms (were 220–240 ms)
                        if (goingBack) (slideInHorizontally(tween(150)) { -it / 4 } + fadeIn(tween(150))) togetherWith (slideOutHorizontally(tween(150)) { it / 3 } + fadeOut(tween(110)))
                        else (slideInHorizontally(tween(150)) { it / 3 } + fadeIn(tween(140))) togetherWith (slideOutHorizontally(tween(150)) { -it / 4 } + fadeOut(tween(110)))
                    },
                ) { r ->
                    if (r != Route.Chat) com.mazharansari.x.ui.components.DismissKeyboard()
                    // v0.5.2: screens where keys, tokens or passwords are typed: no screenshots, screen recording or Recents preview.
                    if (r in com.mazharansari.x.ui.SecureRoutes.all) com.mazharansari.x.security.SecureScreen()
                    saved.SaveableStateProvider(r.toString()) { when (r) {
                        Route.Home -> com.mazharansari.x.ui.home.HomeScreen(vm, nav)
                        Route.Chat -> ChatScreen(vm, nav, back)
                        is Route.BotChat -> com.mazharansari.x.ui.home.BotChatScreen(vm, r.botId, back, nav)
                        Route.Settings -> SettingsScreen(vm, nav, back)
                        Route.Appearance -> AppearanceScreen(vm, back)
                        Route.Language -> LanguageScreen(vm, back)
                        Route.Advanced -> AdvancedScreen(vm, back)
                        Route.Customize -> CustomizeScreen(vm, back)
                        Route.Connectors -> ConnectorsScreen(vm, nav, back)
                        Route.CustomConnector -> CustomConnectorScreen(vm, back)
                        Route.Skills -> SkillsScreen(vm, back)
                        Route.VoiceCall -> com.mazharansari.x.ui.call.VoiceCallScreen(vm, back) { nav(Route.Connectors) }
                        Route.Data -> DataScreen(vm, back)
                        Route.About -> AboutScreen(vm, back, nav)
                        Route.Files -> FilesScreen(vm, back)
                        Route.DevTools -> DevToolsScreen(vm, back, vm.debugToolSheet)
                        Route.Antigravity -> AntigravityScreen(vm, back, nav, vm.debugAgyLogin)
                        Route.Quota -> QuotaScreen(vm, back, nav)
                        Route.KeyBackup -> KeyBackupScreen(vm, back)
                        Route.LocalModels -> LocalModelsScreen(vm, back)
                        Route.MyServer -> MyServerScreen(vm, back, nav)
                        Route.ServerGuide -> ServerGuideScreen(back)
                        Route.ToolsLibrary -> ToolsLibraryScreen(vm, nav, back)
                        Route.Storage -> StorageScreen(vm, back, vm.debugStorage?.first, vm.debugStorage?.second)
                        Route.Browser -> com.mazharansari.x.ui.browser.BrowserScreen(vm, back)
                        Route.Terminal -> TerminalScreen(vm, nav, back)
                        Route.Memory -> MemoryScreen(vm, back)
                        Route.Approvals -> com.mazharansari.x.ui.settings.ApprovalsScreen(vm, back)
                        Route.Schedules -> com.mazharansari.x.ui.settings.SchedulesScreen(vm, back)
                        Route.Turbo -> com.mazharansari.x.ui.settings.TurboScreen(vm, back)
                        Route.GitHub -> com.mazharansari.x.ui.settings.GitHubScreen(vm, back)
                        Route.Telegram -> com.mazharansari.x.ui.settings.TelegramScreen(vm, back)
                        Route.BotStore -> com.mazharansari.x.ui.bots.BotStoreScreen(vm, back)
                        Route.CliPair -> com.mazharansari.x.ui.home.CliPairScreen(vm, back, nav)
                        Route.Feed -> com.mazharansari.x.ui.workspace.FeedScreen(vm, back, nav)
                        Route.Ideas -> com.mazharansari.x.ui.workspace.IdeasScreen(vm, back, nav)
                        Route.Goals -> com.mazharansari.x.ui.workspace.GoalsScreen(vm, back, nav)
                        Route.Library -> com.mazharansari.x.ui.workspace.LibraryScreen(vm, back, nav)
                        Route.Activity -> com.mazharansari.x.ui.workspace.ActivityScreen(vm, back)
                        Route.Computer -> com.mazharansari.x.ui.workspace.ComputerScreen(vm, back, nav)
                        is Route.CodeDiff -> com.mazharansari.x.ui.workspace.CodeDiffScreen(vm, r.projectId, back)
                    } }
                }
                // v0.8.3: while a Live call is held by the view model and the call screen is not
                // open, its orb floats over whatever screen he is working on (floating call).
                val callActive by vm.liveCallActive.collectAsState()
                if (callActive && stack.last() != Route.VoiceCall) {
                    com.mazharansari.x.ui.call.FloatingCallOrb(vm, onOpen = { nav(Route.VoiceCall) })
                }
            }
        }
        } }
    }
}
