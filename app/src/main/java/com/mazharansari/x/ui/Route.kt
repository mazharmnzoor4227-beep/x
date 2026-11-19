package com.mazharansari.x.ui

/**
 * One chat screen (2026-11-17 rebuild; 2026-11-19 new UI): the stack starts
 * at [Route.Chat]. There is no Build mode and no dock. Feed, Ideas, Goals,
 * Library, Activity, Computer and CodeDiff are stack destinations again
 * (X-NEW-UI-SPEC §6), reached only from the drawer or from chat. Everything
 * else stays as a stack destination, reached only from the chat menu /
 * settings / chat actions, never from a dock.
 */
sealed interface Route {
    data object Chat : Route
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
    // P1 — Bot Store (installable bot definitions)
    data object BotStore : Route
    // Phase 2 — Bots section (menu only).
    data object Bots : Route
    // Config, reachable from Antigravity/menu.
    data object CliPair : Route
    // 2026-11-19 (X-NEW-UI-SPEC §6): Workspace stack destinations — reached
    // from the drawer or from chat, never from a dock or home tab.
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
