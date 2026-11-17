# X Chat Rebuild — Shared Contract (2026-11-17, user correction 21:28)

One chat screen. Mascot = the app logo, at the top of the chat screen. NO Build
mode. NO dock tabs. NO separate Feed/Ideas/Goals/Library/Activity/Computer/Mark/
CodeDiff screens. Config (providers, settings) lives behind a minimal menu off
the chat screen, never as a separate mode. All brains underneath keep working
through the chat.

## Mock spec (source of truth)
- `~/workspace/grok-workspace/src/components/axion-studio.tsx` — ChatFrame,
  Composer, Dock (DO NOT rebuild the dock), ChatScreen, ThinkingScreen,
  MenuSheet, Splash, MarkScreen (mascot behaviour only, not a screen).
- `~/workspace/grok-workspace/src/components/settings-screen.tsx` — settings
  list shape (groups, rows, subpages) for the settings restyle stretch goal.
- `~/workspace/grok-workspace/src/components/FluffyMascot.jsx` — mascot
  behaviour: drag rotates, eyes follow pointer, blinks every ~3-5s, slow
  auto-rotate when thinking, gentle idle bob.
- `~/workspace/grok-workspace/src/styles.css` — tokens + animations.

## Tokens (exact, from styles.css @theme)
Light: sand #F3EEE6, sand-deep #E7E0D4, paper #FFFCF8, ink #1C1916,
muted #746C63, line #E4DDD2, sky #3C8FD4, on-sky #F5F9FD, sky-soft #D7EBFA,
bubble #E8E3DA, stop #9C4038, stop-soft #F3E4E1.
Dark (.theme-dark): sand #1C1C1E, sand-deep #111113, paper #2A2A2C,
ink #F4F1EC, muted #A8A29B, line #3A3A3C, bubble #323234, sky-soft #1D3C52,
stop-soft #3D2624. Sky #3C8FD4, on-sky #F5F9FD and stop #9C4038 are the SAME
in dark mode. (Already in SandTheme.kt as SandLightPalette/SandDarkPalette.)

## Typography
- Display: Fraunces (serif) — "X" wordmark, screen titles, Splash name.
- Sans: Outfit — everything else.
- Mono: keep Google Sans Code (code/diff text only). Remove Google Sans Flex
  (the UI sans) and all GoogleSans references once Outfit is wired.
- Mock sizes: header wordmark 16sp Fraunces; TabPage title 24sp Fraunces;
  Splash name 48sp Fraunces letterSpacing -0.03em; body 16sp; muted 12-14sp.

## The ONE chat screen (mock ChatFrame + ChatScreen composition)
- Entry: Splash (mascot 196dp + "X" Fraunces 48sp + radial glow, ~700ms,
  tap to skip) once per app start, after the existing onboarding stages.
- Header (grid: menu | centre | Invite): left = 44dp paper circle, Menu icon;
  centre = MascotLogo 64dp + "X" Fraunces 16sp + status 12sp muted
  (thinking ? "Thinking" : current chat title); right = "Invite" pill
  (paper bg, 36dp, 14sp medium). Top padding = statusBarsPadding + 12dp:
  header sits below the status bar with a clear gap, every screen.
- Message list: LazyColumn, horizontal padding 16dp, 12dp gaps.
  UserBubble: sky bg, on-sky text, 24dp corners with 4dp bottom-right,
  start margin 40dp. AgentBubble: bubble bg, 24dp corners with 4dp
  bottom-left, end margin 32dp. Thinking row: bubble bg + 3 animated dots
  (axion-dot keyframes: 1s, staggered 0/0.15/0.3s) + muted "Reading …" text.
- MUST use the real ChatViewModel: vm.messages, vm.send(), vm.running,
  vm.draft. Reuse the existing AssistantMessage/UserBubble rendering from
  ui/chat (MessageViews.kt) restyled to the mock bubble shapes/colours —
  do NOT rewrite message rendering, and do NOT ship the mock's fake seed
  lines or fake answer(): empty chat starts empty.
- Composer (mock pill, paper bg, rounded-full): Plus 44dp (opens attach
  sheet: Photo / Video / File rows, bottom sheet, drag handle, paper bg,
  24dp top corners), text field "Message", mic 44dp (sky-soft bg while
  listening), send 44dp ink circle with arrow-up (square icon while
  thinking = stop). Restyle the existing ui/chat Composer.kt wiring — keep
  attachments, mic, model popover, voice call. Keep the compact ToolsRow
  (Deep research / Long task / Turbo) above the composer: function the user
  relies on, deliberate deviation from the mock.
- Menu drawer (mock MenuSheet): left drawer 84% width, sand bg, "X"
  Fraunces 36sp title, chat history list (search field + rows with pin/dot
  markers, active chat paper bg), bottom row: Settings gear (paper circle),
  Search pill, New chat pencil (paper circle). Reuse/restyle the existing
  DrawerContent wiring (real conversations). Config entries at the bottom
  of the drawer, below the chat list: Settings, Bots, Bot Store.
- Approval gate, paste dialog, read-aloud pill, interrupted-task banner,
  context-usage line: keep all, restyled to sand/paper.

## Navigation (AppRoot / Route.kt — owned ONLY by the chat-shell agent)
- Stack starts at Route.Chat (new). Delete Route.Home, Route.Feed,
  Route.Ideas, Route.Goals, Route.Library, Route.Activity, Route.Computer,
  Route.Mark, Route.CodeDiff.
- Keep as stack destinations (reachable from menu/settings/chat actions,
  NEVER from a dock or home tab): Settings (+ all existing settings
  sub-routes), Bots, BotStore, Browser, Terminal, VoiceCall, CliPair,
  Connectors, Skills, Memory, Approvals, Schedules, GitHub, Telegram,
  Antigravity, Quota, Turbo, ToolsLibrary, Storage, Data, DevTools,
  KeyBackup, LocalModels, MyServer, ServerGuide, Appearance, Language,
  Advanced, Customize, CustomConnector, About, Files.
- Onboarding intro/permissions stages stay. FloatingCallOrb stays.
- No other agent edits AppRoot.kt or Route.kt.

## DELETE (Build mode + dock screens)
- Files: ui/build/* (BuildSheets.kt, ProjectWorkspace.kt, ReviewChanges.kt);
  ui/home/HomeScreen.kt, HomeTabCommon.kt, BuildPane.kt, ImaginePane.kt,
  FeedScreen.kt, IdeasScreen.kt, GoalsScreen.kt, LibraryScreen.kt,
  ActivityScreen.kt, ComputerScreen.kt, MarkScreen.kt, FileActions.kt
  (only if unused elsewhere — check first), CodeDiffScreen.kt, CliPairScreen.kt
  stays (config, reachable from Antigravity/menu).
- UI references: Ask | Build top tabs, BuildPopover, BuildChips,
  SuggestionChips "Build an app" chip, DockBar, Section.BUILD switching
  in UI + ChatViewModel entry points (newChat(BUILD), buildReady(),
  build flags on ChatPane). Do NOT delete engine code under agent/,
  build/, data/ — the agent still does build tasks through chat.
- Do NOT touch test files (a sibling agent owns test fixes; tests will be
  fixed after this rebuild).

## Mascot (owned ONLY by the mascot agent)
- Render still frames from the REAL ~/workspace/grok-workspace/public/mascot.glb
  (1.1 MB, Draco-compressed; decoder in public/draco/). Headless Chromium
  is available (~/.cache/ms-playwright: chromium-1243). Node v24 available.
- Composable contract (other agents depend on this exact API):
  `ui/mascot/MascotView.kt`, `@Composable fun MascotLogo(size: Dp,
  thinking: Boolean, modifier: Modifier = Modifier, onTap: (() -> Unit)? = null)`
  Renders the mascot: idle bob, blink every ~3-5s, drag rotates through yaw
  frames, eyes follow pointer (gaze frames), slow auto-rotate when thinking.
  Frames live under app/src/main/res/drawable-nodpi/mascot/ (or assets/).
- Do NOT add Filament/SceneView or any new native dependency.

## Fonts (owned ONLY by the fonts agent)
- Download OFL Fraunces + Outfit from Google Fonts into
  app/src/main/res/font/ (static weights: Regular/Medium/SemiBold/Bold for
  Outfit; Regular/SemiBold for Fraunces — variable files are fine if the
  build accepts them, otherwise static instances).
- Wire into Theme.kt: Outfit as the UI sans (Type.* + Material Typography),
  Fraunces as the display family (new Type.display). Keep Google Sans Code
  as mono. Delete google_sans_flex.ttf + GoogleSans once nothing references it.
- Keep SandTheme colours untouched.

## Hard constraints
- Package com.mazharansari.x, versionCode 1, versionName 1.0 (untouched).
- All UI text English. Lists start empty (no demo content).
- Do NOT touch .github/workflows, CI config, or the package name.
- Do NOT push anything. Edit only under ~/workspace/axion-new-series.
- Gradle does NOT run in this sandbox. Verify by careful code reading only.
