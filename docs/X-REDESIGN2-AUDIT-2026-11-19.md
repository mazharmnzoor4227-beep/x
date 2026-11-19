# X Android App — Redesign Round 2: Audit (2026-11-19)

**Auditor:** stage-1 subagent (read-only; no app code changed, nothing pushed, no Gradle run — Gradle does not work in this sandbox).
**Tree:** `~/workspace/axion-new-series`, package `com.mazharansari.x`, v1.0 / code 1, 194 main Kotlin files.
**Prior docs read first:** `docs/X-AUDIT-2026-11-19.md`, `docs/X-NEW-UI-SPEC.md`, `docs/X-REBUILD-2026-11-19.md`, `docs/X-VERIFY-2026-11-19.md`.
**User verdict being answered:** installed the signed 66d2a41 APK and rejected the UI again — "You just lifted the whole Axion code and put it in. The UI is the same, Settings UI is the same, everything is the same. What did you change? The Home screen looks cheap. I want a completely different UI, unlike anything."

**Method:** grep + read of the actual code. Every claim below names a file; line numbers are given only where a grep/read returned them. Anything not verified from code is marked UNVERIFIED.

## 0. Bottom line

Round 1 changed the **paint** (palette, corner radii, bubble style, a few screens) and left the **product** untouched. The user is right on the facts, even if "everything is the same" overstates it:

1. **The app still opens on Axion's one-chat-screen**, with Axion's hamburger drawer as the only navigation, Axion's header (mascot logo + "X" + status), Axion's composer + ToolsRow chips, and Axion's settings tree.
2. **The mascot is still the logo everywhere that matters** — header, empty chat, drawer, splash, About, onboarding intro. Round 1 only froze it (static front frame) instead of removing it.
3. **"Axion" is still the product's name in the copy** — Settings section header, "Customize Axion", "About Axion", assistant-name default, approval dialog, toasts, clipboard label, email subject, and ~40 user-visible strings. The app is called "X" on the launcher (`res/values/strings.xml:4` `app_name` = "X") but calls itself Axion once inside.
4. **The bot-tap bug is real and exactly as the user describes it.** Tapping a bot in Bots → "Your bots" calls `vm.startBotChat(bot)` and then `back()`, which pops the Bots route and lands on the home chat as a blank new chat. There is no bot-chat screen and no per-bot thread lookup. (`ui/bots/BotsScreen.kt:72`, `ui/ChatViewModel.kt:205-207`.)
5. **The token display is real and in the chat**, above the composer: a "Context 62% · 12,400 / 20,000 tokens" line plus a progress bar, and a "Local model · 12.3 tok/s · 4,096 tokens" line. (`ui/chat/ChatScreen.kt:310-319`.)
6. **Settings is structurally identical to Axion's.** Round 1's spec §10 (14-group reorder) was explicitly NOT done (rebuild doc §7.5). Same sections, same rows, same order, same "Axion" grouping, same profile card and providers card at the top.

Round 1's genuine changes are real but all **surface-level**: indigo palette, system fonts, right-aligned user bubbles, 7 workspace screens restored, "This chat" approval scope, Invite pill deleted, Build mode unreachable, Piper voice preference. Nothing about how the user *navigates* the app changed. That is why it still reads as Axion on the phone.

## 1. What round 1 actually changed (verified in code)

| Change | Evidence |
|---|---|
| Indigo palette, light default | `ui/theme/Theme.kt`: `LightPalette` bg `#FFFFFF`, accent `#4F46E5`; `DarkPalette` bg `#171717`, accent `#818CF8`. `XTheme`/`XLook` wrap the old `AxionTheme()` entry point (kept because tests wrap screens in it). |
| No green; diff-added = indigo | Rebuild doc §1; `Theme.kt` header comment; `statusDone` = neutral `textSecondary`. No green hex found in main Kotlin (verify doc item 1e). |
| User bubble right-aligned, 20dp corners, 4dp bottom-right | `ui/chat/MessageViews.kt` (per rebuild doc §2; `SelectionContainer(Modifier.fillMaxWidth(0.8f))`). |
| Composer 28dp pill, ToolsRow chips, model chip | `ui/chat/Composer.kt`; `ChatScreen.kt` ToolsRow wiring. |
| 7 workspace screens restored | `ui/workspace/WorkspaceScreens.kt` (463 lines): `FeedScreen:95`, `IdeasScreen:149`, `GoalsScreen:188`, `LibraryScreen:254`, `ActivityScreen:305`, `ComputerScreen:338`, `CodeDiffScreen:392`. Branched in `ui/AppRoot.kt:101-107`. Routes added in `ui/Route.kt` (`Feed`, `Ideas`, `Goals`, `Library`, `Activity`, `Computer`, `CodeDiff(projectId)`). |
| Approval "This chat" scope | `agent/ApprovalGate.kt:13` `ALLOW_CHAT`; `ChatViewModel.kt:81,86-87` in-memory `chatApprovedPatterns`; dialog button `ChatScreen.kt:419`. Surface is still an `AlertDialog`, not the spec §9.1 inline card. |
| Mascot frozen to a static front frame | `ui/mascot/MascotView.kt:273` `LogoMark` uses only `R.drawable.mascot_yaw_0`. Animated `MascotLogo` kept for the ThinkingOrb behaviour. |
| Invite pill deleted; Build mode unreachable at runtime | `ChatScreen.kt:73,219` comments only; `ChatViewModel.kt` has 0 `Section.BUILD` references; `openProject` sets `Section.ASK`. |
| Piper voice preference | `data/Stores.kt:32` `voicePref = "piper-aegis"`; `voice/PiperVoice.kt`; Settings "Voice & Language" (`ui/settings/SettingsScreens.kt:213`). Engine still not bundled (honest note in UI). |
| Drawer relabelled/restructured | `ChatScreen.kt` `MenuDrawer`: "Conversations" → "Chats", new "Workspace" section (6 tiles + badges), "Configure" section (13 tiles). See §6 for why this is still Axion's drawer. |

## 2. What round 1 left untouched — the Axion leftovers (ranked)

These are the things the user's eye lands on. Ranked by how much they make the app read as "the same Axion".

### 2.1 The navigation model is Axion's, unchanged since v0.x
- **One chat screen is the home.** `ui/AppRoot.kt` builds its stack as `mutableStateListOf<Route>(Route.Chat)`; the app opens straight into an open (usually empty) chat. `Route.kt` header still states the 2026-11-17 rebuild contract ("the stack starts at Route.Chat … no dock").
- **The hamburger drawer is the only navigation.** `ChatScreen.kt` `MenuDrawer` (~84% width, max 320dp, scrim, slide 280ms) holds the chat list, Workspace, and Configure. The header's left button is the hamburger (`Gk.menu`); there is no other way to reach anything except in-chat links.
- **The header is a logo totem, not a tool header.** `ChatScreen.kt:225-245`: centred vertical stack — `LogoMark` 36dp, "X" 17sp SemiBold, status line ("Thinking…" / chat title / "New chat") — flanked by two `Spacer(weight(1f))` and 44dp icon buttons (menu left, new-chat + overflow right). On a phone this reads as a brand splash at the top of every screen, which is a large part of "the Home screen looks cheap".
- **Empty chat is a logo + tagline.** `ChatScreen.kt:522-528`: `LogoMark` 48dp at 40% opacity, "X" 28sp, "Ask anything, or tell X to do something.", then a horizontally scrolling chip strip (Connect a free AI key / Deep research / Long task / Turbo / Connectors / Skills / Voice). There is no content, no list, no work visible — the home screen is empty by construction.
- **Pull-to-new-chat gesture, Haze blur, UpdateBanner, ThinkingRow, ToolsRow chips, ReadAloudPill, floating call orb** — all Axion-era chat-screen furniture, untouched. `ChatScreen.kt` still imports `dev.chrisbanes.haze` and wires `HazeState`.

### 2.2 The name "Axion" is still all over the user-visible copy
The launcher says "X" (`res/values/strings.xml:4`), the in-app copy says "Axion". Grep for `"Axion` / `Axion ` in `ui/` + `AxionApp.kt` returns ~60 hits; the user-visible ones include:

| Location | Text the user sees |
|---|---|
| `ui/settings/SettingsScreens.kt:118` | Settings section header **"Axion"** (groups 15 rows under it) |
| `ui/settings/SettingsScreens.kt:120` | "Customize Axion" |
| `ui/settings/SettingsScreens.kt:146` | "About Axion" |
| `ui/settings/SettingsScreens.kt:391` | "What should Axion call itself?" (Customize screen field) |
| `ui/settings/SettingsScreens.kt:483` | Report-a-problem email subject `"Axion ${BuildConfig.VERSION_NAME} problem"` |
| `data/Stores.kt:37` | `assistantName` default = `"Axion"` (used as the bot/assistant name fallback, `ChatViewModel.kt:694`) |
| `ui/chat/ChatScreen.kt:260` | Clipboard label `"Axion"` |
| `ui/chat/ChatScreen.kt:407` | Approval dialog: "…Axion will ask every time." |
| `ui/bots/BotsScreen.kt:60,99,106,194` | Parent-bot fallback name `"Axion"`; empty text "Ask Axion something…" |
| `ui/settings/ConnectorsScreen.kt:75,462` | "Connectors let Axion use AI models…", "Skills teach Axion…" |
| `ui/settings/MemoryScreen.kt:43,52` | "Axion saves short notes about you…", "Axion remembers only what is on this screen." |
| `ui/settings/ApprovalsScreen.kt:27,44,88` | "the command kinds Axion may run…", "Axion will ask before every command again…" |
| `ui/settings/GitHubScreen.kt:105,159,180,198` | "Connect GitHub so Axion can clone…", "Axion shows you the diff…" |
| `ui/settings/TelegramScreen.kt:62,121` | "Text Axion from anywhere…", "only this chat can talk to Axion now." |
| `ui/settings/ToolsLibraryScreen.kt:27,33` | "every tool Axion's agent can use…", "Axion picks the right tool…" |
| `ui/settings/SchedulesScreen.kt:110` | "Ask Axion in chat — …" (includes a Roman-Urdu example sentence) |
| `ui/settings/DeleteAllDialog.kt:38,44` | "This erases everything Axion keeps on this phone…" |
| `ui/settings/StorageScreen.kt:68` | "Axion uses…" |
| `ui/bots/BotStoreScreen.kt:48,50` | "run on Axion's own agent loop", "through Axion's provider system" |
| `ui/settings/V04Screens.kt:372,482` | "Axion switches to the next…", "Axion never sees your password or token" |

Code-only identifiers (`AxionTheme`, `AxionApp`, the `Axion` package segment, `AxionSheet`) are **not** user-visible and are not part of this complaint — but every string above is. Fixing the name is the cheapest, highest-impact part of round 2.

### 2.3 Settings is Axion's Settings, unreordered
`ui/settings/SettingsScreens.kt:80-150` (`SettingsScreen`): profile card (56dp avatar circle + name + "Add your name"/"Edit profile", taps → Customize), providers card ("Connect a free AI key" + blue pill Connect/Manage), then sections in the **old Axion order** — "App" (Appearance, Voice & Language, Advanced), **"Axion"** (Customize Axion, Connectors, Skills, GitHub, Memory, Approvals, Schedules, Tools library, Browser, Terminal, Developer tools, Turbo, Antigravity, Quota, Telegram control, Status page toggle), "Data & Information" (Library, Data Controls, Storage), then About / Report a problem, footer "X ${VERSION_NAME}" / "by Mazhar Ansari". Round 1's spec §10 14-group reorder was explicitly not done (rebuild doc §7.5). All ~15 settings sub-screens (`V04Screens.kt`, `ConnectorsScreen.kt`, `ToolsLibraryScreen.kt`, `TerminalScreen.kt`, etc.) are untouched Axion screens; the only round-1 touch was adding Voice & Language rows.

### 2.4 The mascot is still the logo (full inventory — §3)
Round 1 froze the animation; it did not remove the character. The same fluffy 3D-character face renders in the header, empty chat, drawer, splash, About, and the first-run intro.

### 2.5 The bot flow is Axion's Phase-2 flow, with the reported bug (§5)
`BotsScreen` is a swarm-monitor page (Live parents/workers + Your bots + Finished workers), not a WhatsApp-style bot list. Bot identity is a glyph/photo logo (`BotLogoView`), not a monogram. There is no bot-chat screen.

### 2.6 The icon set, theme plumbing, and visual effects are Axion's
- `ui/components/Common.kt:39-70` `object Gk` — Axion's own `gk_*` drawable glyph set (90+ icons), unchanged. `GkIcon` is used by every screen.
- `ui/theme/Theme.kt` — `AxionTheme()` is still the real theme entry point; `XTheme` is a thin wrapper over it. Legacy alias fields (`chip`, `card`, `page2`, `blue`, `sidebar`, `status*`) still exist so old screens compile unchanged.
- `ui/settings/ThinkingOrb.kt` + `SettingsScreens.kt:406-420` `Orb()` — the ported ThinkingOrb dot-wave/ribbon math, still drawn in Customize.
- `ui/chat/MessageViews.kt:226` still calls `ThinkingOrbEngine.ribbonSmall(...)` for the thinking ribbon.
- `ui/chat/Composer.kt` + `Sheets.kt` (`AxionSheet`, `PlusPopover`, `ModelPopover`) — same sheets/popovers as Axion.

### 2.7 The 7 workspace screens are Axion-style subpages
`ui/workspace/WorkspaceScreens.kt` — each is a `ScreenHeader` (back chevron + 20sp title) + card list, same `SettingsPage`/`ScreenHeader` shell as every Axion settings screen (`ui/components/Common.kt:123`). Their content is real (wired to real stores), but visually they are indistinguishable from Settings pages. They are also only reachable from the drawer (or chat links) — there is no home surface for them.

### 2.8 The chat transcript itself is Axion's
`ui/chat/MessageViews.kt` (assistant plain text, user bubble, step timeline, message action row Copy/Share/👍/👎/Read-aloud/Regenerate), `ui/chat/ChatList.kt` (WhatsApp-style row builder — the row *logic* is WhatsApp-style, but it is only ever rendered inside the drawer), `ui/ChatViewModel.kt` (single active conversation + `activeBot` + `sessions` map). The brain is fine and must not change; the transcript presentation is Axion's and round 1 only restyled its colours/corners.

## 3. Mascot inventory — every reference (demand 5)

**The character:** 10 PNG frames in `app/src/main/res/drawable-nodpi/` (`mascot_yaw_m60/m30/0/p30/p60`, `mascot_blink`, `mascot_gaze_left/right/up/down`) + `axion_splash.png`, rendered from `app/src/main/assets/mascot.glb`. The animated composable is `MascotLogo` (`ui/mascot/MascotView.kt:59-258`): idle bob (4.33s period), blink every 3–5s for 170ms, drag-to-rotate through 5 yaw frames, gaze frames that follow the pointer, auto-rotate while thinking. The static composable is `LogoMark` (`MascotView.kt:273-288`): front frame `mascot_yaw_0` only.

| # | Where it renders | Code |
|---|---|---|
| 1 | Chat header, 36dp — first thing on the home screen | `ui/chat/ChatScreen.kt:228` `LogoMark(size = 36.dp)` (import `:61`) |
| 2 | Empty chat, 48dp at 40% opacity | `ChatScreen.kt:528` (comment `:522` still calls it "the mascot") |
| 3 | Drawer header, 32dp + "X" | `ChatScreen.kt:601` |
| 4 | Splash, 72dp + "X" 28sp, ~700ms on every app start | `ChatScreen.kt:758` (`SplashScreen` `:749-763`; called from `ui/AppRoot.kt:43`, comment still says "splash (mascot + X…)") |
| 5 | About screen, 72dp + "X" 24sp | `ui/settings/SettingsScreens.kt:466` (import `:53`) |
| 6 | First-run intro, **animated** 96dp growing out of an indigo glow, rotation −18°→0 | `ui/auth/Onboarding.kt:84` `MascotLogo(96.dp, thinking = false, …)` (import `:41`; doc comment `:58` "the mascot logo grows in out of a soft glow") |
| 7 | System splash icon | `app/src/main/res/values/themes.xml:11` `windowSplashScreenAnimatedIcon` = `@drawable/axion_splash` |
| 8 | The animated behaviour itself (kept for "the ThinkingOrb screen") | `MascotView.kt:59-258` whole `MascotLogo`; header comment `:43-47` documents the GLB source and mock lighting |
| 9 | Content descriptions / semantics | `MascotView.kt:153,161` `"X mascot"`; `LogoMark` uses `"X logo"` (`:275`-ish) |
| 10 | Launcher icon — **UNVERIFIED whether it is the mascot.** `AndroidManifest.xml:36-37` points at `@mipmap/ic_launcher` (`mipmap-anydpi-v26/ic_launcher.xml` → `ic_launcher_fg`/`ic_launcher_mono`). The PNGs could not be visually inspected in this sandbox. The rebuild agent must look at the launcher icon and replace it if it shows the character. |

**What is NOT mascot (do not delete):** `ui/settings/ThinkingOrb.kt` / `ThinkingOrbEngine` — abstract dot wave/ribbon math, no character. `ui/bots/BotLogoView.kt` — bot logos (glyph/photo/initial), separate system, see §5. `Orb()` in `SettingsScreens.kt:406` — the same abstract dot orb, no character.

**Test impact of deleting the mascot:** grep over `app/src/test/java` for `MascotLogo|LogoMark` returns **no hits** (checked; `Phase2UiTest.kt` contains neither). `ThinkingOrbEngineTest.kt` tests only the orb math. Deleting `MascotView.kt` + PNGs + GLB should not break tests — the rebuild agent must still run the suite to confirm.

## 4. Token-display inventory — every user-facing token count in chat (demand 4)

There is **no per-message token count** under messages (grep for `token` in `ui/chat/MessageViews.kt`, `Composer.kt`, `Sheets.kt` returns nothing). The token UI in chat is two lines **above the composer**, rendered on every chat that has run:

| # | UI element | Code |
|---|---|---|
| 1 | Context-usage line + progress bar: `"Context 62% · 12,400 / 20,000 tokens"` (+ `" · compacted N turns"`) with a 3dp accent progress bar | `ui/chat/ChatScreen.kt:310-315`: `vm.contextUsage?.let { u -> Text(u.label(), …); LinearProgressIndicator(progress = { u.percent / 100f }, …) }`. Label built by `agent/ContextUsage.kt:22-23` (`label()`); data by `ContextUsage.kt:15` (`usedTokens`, `budgetTokens`, `compactedTurns`); percent `:20`. State: `ui/ChatViewModel.kt:95-96` (`var contextUsage …`); reset on new chat `:194`; fed from the agent run `:766` (`onContextUsage = { u -> … contextUsage = u }`). **Engine note:** `ContextUsage.shouldCompact` (`ContextUsage.kt:48`, compact at 75%) is the auto-compact trigger the agent acts on — that logic must stay; only the *display* goes. |
| 2 | Local-model speed line: `"Local model · 12.3 tok/s · 4,096 tokens"` | `ui/chat/ChatScreen.kt:316-319`: `vm.localSpeed?.let { sp -> Text(sp.label(), …) }`. Label built by `local/LocalToolUse.kt:32-35` (`LocalSpeed.label()` — includes `"$tokens tokens"`); state `ChatViewModel.kt:98`; set after a local run `:831` (`localSpeed = result.speed`). |

**Out of scope for demand 4 (NOT chat — keep):** Quota screen token usage (`ui/settings/V04Screens.kt:530` `QuotaScreen`; token rows `:571,600,635,709,714-717,771-774` — provider/token accounting inside Settings, not chat). Antigravity run token counts in `V04Screens.kt` are also Settings-only. The `ContextUsage`/`LocalSpeed` *measurement* code stays for compaction/diagnostics; only the two chat render blocks and their ViewModel state go.

## 5. Bot-tap navigation — the reported bug, traced exactly (demand 3)

**Current behaviour (the bug):** tapping a bot in Bots → "Your bots" → "Start chat" opens a **blank new chat on the home screen** and pops the Bots screen. The bot's own thread is never looked up, and there is no bot-chat screen to land on.

**Exact chain:**
1. `ui/bots/BotsScreen.kt:72` — `BotsScreen` wires `onStartBot = { bot -> vm.startBotChat(bot); back() }`. `back()` pops `Route.Bots` off the `AppRoot` stack, so the user lands on whatever is underneath: the home chat (`Route.Chat`).
2. `ui/ChatViewModel.kt:205-207` — `startBotChat(bot)` = `newChat()` + `activeBot = bot`. `newChat()` (`:188-198`) wipes the open conversation (`conversationId = null`, `messages = emptyList()`, `activeBot = null` then re-set, `contextUsage = null`, `chatApprovedPatterns.clear()`). So the tap **destroys the open chat's in-memory state** and shows an empty composer with a small "Chatting as <bot>" strip (`ChatScreen.kt:~340`, `BotLogoView` 28dp + "Exit bot" button).
3. No lookup happens: nothing queries the conversation store for an existing thread with that `botId`. A bot the user chatted with yesterday opens as a **fresh blank chat**, and the old thread is only reachable by finding it in the drawer chat list.
4. The same bug fires from bot creation: `BotsScreen.kt` `CreateBotCard` calls `onStartBot(created…)` after `botStore.create(...)` — creating a bot also dumps the user onto the blank home chat.
5. `BotStoreScreen.kt:73-78` (install) is *not* buggy in this way: install only stores the definition and toasts "Installed '…' — find it in Bots → Your bots". It correctly does not open a chat.

**Why there is no per-bot thread to open:** the data model already *supports* it but nothing *uses* it that way.
- `data/Models.kt:103,116` — `Conversation.botId: String = ""`, `botName: String = ""` (and the same on `ConversationMeta`). `data/Stores.kt:110` copies them into the index meta; `ChatViewModel.kt:630,636` persists them on every save; `:402` restores `activeBot = botFor(c.botId)` when a saved bot chat is opened from the drawer. So a bot chat, once it has had a first message, is saved with its `botId` and reopens as that bot.
- What is missing: (a) a query "newest conversation for botId X" (no such function exists in `ConversationStore` — grep for `latestForBot|forBot|byBot` returns nothing); (b) a route/screen to open it in (no `Route.BotChat`; `Route.Bots` is the only bot route, and it is the swarm-monitor page); (c) a back rule (today `back()` = pop to home chat).
- Related existing machinery that the fix reuses, not replaces: `ChatViewModel.open(id)` (`:380-410`, loads a saved conversation incl. bot restore), `openWorkerChat` / `closeWorkerChat` (`:416-428`, worker transcripts with a remembered parent — the *only* precedent for "back returns to where you came from"), `BotDefinitionStore` (`data/BotStore.kt:64-112`: `definitions` StateFlow, `install`, `create`, `remove`, `setLogo`), `BotDefinition.promptBlock()` (`BotStore.kt:54`, persona/system prompt fed to the agent at `ChatViewModel.kt:771`), `switchesForBot` / `preferProvider` (`BotStore.kt:122,131`, tool allow-list + provider preference applied at run time `:764-767`).

**What `BotsScreen` actually is today (not a bot list):** `BotsScreen.kt:72-150` renders three groups — "Live" (swarm parent runs + their worker runs, with status/step counts), "Your bots" (installed/created definitions), "Finished workers" (old worker transcripts). Tapping a *parent/worker* opens its transcript via `vm.open` / `vm.openWorkerChat` (correct — those open a real saved conversation). Only the "Your bots" tap is broken. Bot avatars today are `BotLogoView` (`ui/bots/BotLogoView.kt`): gallery photo, or a built-in glyph (`BotLogo.BUILTINS`, 12 ids: brain/agent/rocket/sparkles/bolt/hammer/globe/person/gem/shield/wand/cube — `data/BotLogo.kt`), or the bot's initial when no logo. The initial fallback already exists and is the seed of the monogram system.

## 6. Drawer inventory (demand 2)

`MenuDrawer` in `ui/chat/ChatScreen.kt` (drawer section, after the renaming dialog). Contents, top → bottom:

1. Header: `LogoMark` 32dp + "X" 20sp + close button (`:601`).
2. "Connect a free AI key" banner (only when no keys) — indigo (`p.blue`) rounded card with Connect pill.
3. "PINNED" chats, then "CHATS" (collapsible) — full conversation list from `vm.g.chats.index`, filtered to exclude `section == "worker"`; rows via `ChatList.kt` `buildChatRows` (pinned first, newest first; bot chats show bot logo + bot name as `displayName`). Long-press → Rename / Pin / Delete / Share sheet. Search lives in the **bottom bar**, not the top (spec §7's top search field was never built — the drawer has a bottom search pill instead).
4. "WORKSPACE" — 6 tiles: Feed (badge = pending approval + running runs), Ideas (badge = count), Goals (badge = count), Library, Activity, Computer. (`DrawerTile`, 48dp rows, 6dp corners, `accentSoft` count badges.)
5. "CONFIGURE" — 13 tiles: Settings, Bots, Bot Store, Skills, Memory, Connections, MCP Servers (→ Connectors), Antigravity / CLI Pair, Approvals, Schedules, GitHub, Terminal, Browser.
6. Bottom bar (fixed, gradient fade above it): search pill + Settings gear + New-chat pencil (48dp circles, `p.card`).

Note the drawer is **not** the spec §7 drawer: search is at the bottom, there is no "Conversations" label (it is "Chats"), and the Workspace/Configure grouping is round 1's addition. The drawer is also the *only* place the chat list exists — deleting it (as the round-2 spec does) requires the home list to replace it, not just the tiles.

## 7. Home-screen diagnosis — why it "looks cheap"

Concrete, checkable observations (not taste claims):

1. **The home screen is an empty chat.** The app opens on `Route.Chat` with zero messages, so the first paint is: logo totem header + empty middle + chip strip + composer. There is no list, no content, no evidence of work. Every other serious app the user named opens on a list; X opens on a blank page.
2. **The header is a centred brand totem.** Logo + "X" + status stacked in the centre with empty space on both sides is a splash-screen layout, not a tool header. It is also the *only* header in the app that centres its title — every subpage uses `ScreenHeader` (back chevron + left-aligned 20sp title, `ui/components/Common.kt:123`).
3. **The empty state sells nothing real.** The chip strip mixes setup ("Connect a free AI key") with mode toggles (Deep research / Long task / Turbo) that are also in the ToolsRow — the same controls twice, once as empty-state decoration.
4. **The drawer hides the product.** Chat history — the thing the user actually returns to — is behind a hamburger and a 280ms slide-in. Bots, the thing he asked for, is one tile among 13 in "Configure".
5. **Round 1's look is ChatGPT-with-indigo.** White `#FFFFFF` canvas, `#F7F7F8` surfaces, indigo `#4F46E5` accent, 20dp bubbles, 28dp pill composer, full-round chips, system sans 16/24. It is competently executed and instantly recognisable as "generic AI-chat clone #N" — which is exactly what the user means by "unlike anything" not being met. The indigo is also one hue-step from the rejected Dots periwinkle (`#8292D6`), so on a phone the family resemblance survives the recolour.

## 8. What the rebuild agent must NOT assume

- **Do not assume a per-message token UI exists** — it does not (§4). The removal is two blocks in `ChatScreen.kt` + ViewModel state.
- **Do not assume bot threads need a new database.** `Conversation.botId/botName` already persist; the fix is a lookup + a route + a back rule (§5).
- **Do not assume the mascot is only in `MascotView.kt`.** It is in 6 render sites + the system splash icon + the intro + 10 PNGs + the GLB (§3).
- **Do not assume "Axion" is only in comments.** It is in ~40 user-visible strings (§2.2). Code identifiers (`AxionTheme`, `AxionApp`) are a separate, lower-priority rename; the strings are the user complaint.
- **Do not assume the drawer search is at the top** (spec §7 said so; the code puts it at the bottom, §6).
- **Do not assume Settings was reordered** (spec §10; it was not, §2.3). The round-2 spec's "compact settings block" is a new surface, not a restyle of `SettingsScreen`.
- **UNVERIFIED (needs a phone/eyes, could not be checked here):** whether the launcher icon (`ic_launcher_fg`/`ic_launcher_mono`) depicts the mascot; the exact visual weight of the header/empty state on a real phone (no screenshots were taken in this audit); compile status of the tree (no Gradle in this sandbox; CI green on commit 66d2a41 is the last known state per memory).
