package com.mazharansari.x.ui

/**
 * Navigation restructure (2026-11-20, user order — supersedes the 2026-11-19
 * "small settings at top" instruction):
 * the stack starts at [Route.Chat] — ONE ChatGPT-like chat screen, ready to
 * type. [Route.Home] is the MENU PANEL, opened from the chat screen's top
 * button: top row Bots / Library / Connectors / Skills, then the full
 * chat-history list, then Settings / New chat / Search (in that order).
 * [Route.Bots] is the WhatsApp-style Bots section: it shows exactly ONE bot
 * — X Bot — with the sub-bots X Bot spawned listed under it. Tapping X Bot
 * opens THAT bot's own chat ([Route.BotChat]); Back returns to the bot list,
 * never to home or a new chat. [Route.BotStore] is the old name for the same
 * destination, kept so existing callers still resolve.
 * Feed, Ideas, Goals, Library, Activity, Computer and CodeDiff are stack
 * destinations reached only from the menu panel or from chat links.
 * Everything else stays as a stack destination, reached from the menu /
 * Settings / chat actions, never from a dock.
 */
sealed interface Route {
    /** Menu panel: 4 top options + chat history + Settings / New chat / Search. Opened from the chat screen. */
    data object Home : Route
    /** The single chat screen, ready to type. The app opens here. */
    data object Chat : Route
    /** One bot's own chat, inside the Bots section. Back returns to the Bots section. */
    data class BotChat(val botId: String) : Route
    data object Settings : Route
    data object Appearance : Route
    data object Language : Route
    data object Advanced : Route
    data object Customize : Route
    data object Connectors : Route
    data object CustomConnector : Route
    data object Skills : Route
    data object VoiceCall : Route
    data object Data : Route
    data object About : Route
    data object Files : Route
    data object DevTools : Route
    data object Antigravity : Route
    data object Quota : Route
    data object KeyBackup : Route
    // v0.5
    data object LocalModels : Route
    data object MyServer : Route
    data object ServerGuide : Route
    // v0.5.3
    data object Storage : Route
    // v0.6
    data object ToolsLibrary : Route
    // v0.9
    data object Browser : Route
    data object Terminal : Route
    // v0.10 — the learning loop
    data object Memory : Route
    // v0.12 — remembered approvals + scheduled tasks
    data object Approvals : Route
    data object Schedules : Route
    // P1-3 — the user's own server for Turbo tasks (opt-in cloud sandbox)
    data object Turbo : Route
    // P1-1 — in-app GitHub coding agent
    data object GitHub : Route
    // P2-1 — Telegram bot control (a bot token is typed here)
    data object Telegram : Route
    // Bots section (2026-11-20): exactly one bot, X Bot, plus its sub-bots.
    data object Bots : Route
    /** Old name for [Bots] — kept so existing callers still resolve. */
    data object BotStore : Route
    // Config, reachable from Antigravity/menu.
    data object CliPair : Route
    // 2026-11-19 (X-NEW-UI-SPEC §6): Workspace stack destinations — reached
    // from the menu panel or from chat, never from a dock or home tab.
    data object Feed : Route
    data object Ideas : Route
    data object Goals : Route
    data object Library : Route
    data object Activity : Route
    data object Computer : Route
    /** Code-diff review for one project (real ChangeGate ChangeSet). */
    data class CodeDiff(val projectId: String) : Route
}

/** v0.5.2: routes shown with FLAG_SECURE (a key, token or password can be typed or shown on them). */
object SecureRoutes {
    val all: Set<Route> = setOf(Route.Connectors, Route.CustomConnector, Route.Skills, Route.KeyBackup, Route.MyServer, Route.Antigravity, Route.Turbo, Route.GitHub, Route.Telegram)
}
