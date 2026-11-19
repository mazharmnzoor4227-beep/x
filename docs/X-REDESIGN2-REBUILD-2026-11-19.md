# X — Redesign Round 2: Rebuild Notes (2026-11-19)

Rebuild of the X Android app (`com.mazharansari.x`, repo `mazharmnzoor4227-beep/x`)
to the Ink Ledger design spec (`docs/X-REDESIGN2-SPEC-2026-11-19.md`, incl. §0.0
differentiation table and §7 per-screen spec). Round 1's indigo/light
ChatGPT-like UI is gone; the donor Axion UI is gone. What shipped before this
rebuild: CI green on commit `66d2a41`, signed APK delivered 2026-11-19 ~22:38,
rejected by the user on his phone ~22:47 ("still Axion's code/design").

**How this doc is organised:** §1–§6 = the six redesign demands. §A–§H = the
addenda, folded into the same rebuild. §T = test updates. §V = how it was
verified (and what was NOT verifiable — read this before calling anything
done). Per-screen spec compliance is noted in §H.

**Standing rules kept throughout:** no green anywhere; English UI only; no
demo/sample content (lists start empty, all functions do real work); brain /
stores / engines untouched except where a demand required it; no secrets in
the tree; no `ci-artifacts/` in the tree; Gradle does not work in this
sandbox, so there is no local compile — **CI is the compile proof** and the
user's phone test is the design proof.

---

## 1. Completely different UI — Ink Ledger

**Was:** round-1 big-brand redesign (light theme, indigo `#4F46E5`, 20–28dp
pills, full-round chips, one chat screen, drawer nav) — itself a re-skin of
Axion's donor UI.

**Now:** "Ink Ledger" — list-first (the app opens on a Home list, NOT a chat
screen), dark-default ink theme, a single bronze accent, rectilinear ledger
shapes (4–12dp radii), hairline-separated rows, 11sp uppercase eyebrows,
12sp mono metadata. No drawer anywhere.

- `ui/theme/Theme.kt` — fully rewritten. Dark (default): bg `#14161A`,
  surface `#1D2026`, surfaceRaised `#262A31`, surfaceSunken `#0F1114`, text
  `#F2F3F5`, textSecondary `#A6ADB8`, muted `#6E7683`, border `#2E333D`,
  accent `#E0A63F`, onAccent `#14161A`, accentSoft `#3A2E18`, danger
  `#F87171`, dangerSoft `#3D2624`, userBubble `#2A2F38`. Light ("Day"): bg
  `#F7F8FA`, surface `#FFFFFF`, text `#16181D`, accent `#92400E`, accentSoft
  `#FDF3E3`, danger `#DC2626`, userBubble `#EDEFF2`. Legacy alias fields kept
  (same struct) so untouched screens compile; the `blue` alias now points at
  bronze. Type scale: title 22/28 SemiBold, body 15/22, small 13/18, tiny
  12/16, label 12/16 Medium, row 15/20 SemiBold, header 17/22 Medium, mono
  12/16 (GoogleSansCode), new `eyebrow` 11/16 SemiBold letterSpacing 1.2sp.
  `XTheme` default changed to `XLook.Dark` (deliberate behaviour change).
- Restyle sweep across `ui/`: 13/14/15/16/17/18/19/20/24dp radii → 10dp;
  pills/chips 20–50dp → 6–8dp; circles removed except the documented
  exceptions in spec §7.9 (the 36dp accent send circle, 8dp status dots,
  `DotGrid` dots). Files touched: `ui/components/Common.kt` (IconTap,
  CircleButton, Pill, SectionLabel, SettingsGroup, AxionSwitch, Avatar,
  DotsButton), `ui/chat/Composer.kt` (field 12dp, chips 6dp, send stays the
  one allowed circle), `ui/chat/MessageViews.kt` (user bubble 10dp corners
  with 4dp bottom-right, StatusPill 6dp), `ui/chat/Sheets.kt` (sheet top
  corners 16dp, drag handle 32×4dp), `ui/chat/StepDetail.kt` (diff added
  lines indigo → bronze), `ui/workspace/WorkspaceScreens.kt`,
  `ui/settings/SettingsScreens.kt`, `ui/settings/V04Screens.kt`,
  `ui/settings/OwnModelScreens.kt`, `ui/call/CallScreen.kt`,
  `ui/settings/StorageScreen.kt`, `ui/settings/ConnectorsScreen.kt`,
  `ui/bots/BotStoreScreen.kt`, `ui/chat/ReadAloudBar.kt` (dead `NewChatPull`
  deleted — no callers since the pull gesture was deleted),
  `ui/chat/PdfViewer.kt`, `ui/chat/Markdown.kt`, `ui/chat/HtmlPreview.kt`,
  `ui/chat/CliPairScreen.kt`, `ui/chat/ApprovalsScreen.kt`,
  `ui/chat/DeleteAllDialog.kt`, `ui/chat/GitHubScreen.kt`,
  `ui/chat/MemoryScreen.kt`, `ui/chat/SchedulesScreen.kt`,
  `ui/chat/TerminalScreen.kt`, `ui/chat/ToolLog.kt`, `ui/chat/PlanCard.kt`.
- `ui/auth/Onboarding.kt`: indigo glow `#4F46E5` → bronze `#E0A63F`; blue
  glow `#3B6BFF` → bronze; 50dp pills → 8dp; progress dots 50 → 3dp;
  permission icon circle → 12dp rounded square.

**Verified:** grep for the old palette (`#4F46E5|#818CF8|#8292D6|#35766F`)
returns 0 in `app/src/main/`; no green hex anywhere by the same sweep.

## 2. Menu simplified — no drawer, Home list + Workspace sheet

**Was:** a drawer (~84% width, max 320dp) holding Pinned/Chats, Workspace
(6 tiles), Configure (13 tiles), a "Connect a free AI key" banner and a
search pill; the app opened straight into a chat screen.

**Now:**
- `ui/Route.kt`: added `Route.Home` (new start) and
  `data class BotChat(val botId: String)`; deleted `Route.Bots`.
- `ui/AppRoot.kt`: stack starts at `Route.Home`; `Route.Chat` renders the
  chat; `is Route.BotChat` renders `BotChatScreen`; the `Route.Bots` branch
  is deleted.
- `ui/home/HomeScreen.kt` (NEW): 64dp settings block at the top (user
  monogram 40dp + name 15sp SemiBold + status 12sp mono
  `"Ready · <provider name or 'No key yet'>"` + Workspace grid button 40dp
  + Settings gear 40dp; monogram/name tap → `Route.Customize`); 48dp search
  field (8dp corners, filters Bots + Chats inline, clear button); then a
  `LazyColumn` with RUNNING NOW (only while live bots exist), BOTS, CHATS.
  Chat rows come from `buildChatRows` (worker transcripts excluded, pinned
  first, newest first) via `ChatListMeta`; time labels "2:45 PM" / "Yesterday"
  / "12 Nov". Hairlines: 1dp `P.border`, inset 72dp after each row.
- Workspace lives behind ONE discreet entry point: the grid button opens an
  `AxionSheet` bottom sheet listing exactly **6** rows (Feed, Ideas, Goals,
  Library, Activity, Computer) with real subtitles (Feed: `"$feedCount need
  you"` / "Nothing needs you"; Ideas: `"$n saved"` / "No ideas yet"; Goals:
  `"$n active"` / "No goals yet"; Library: "Files, notes and downloads";
  Activity: "Runs, tasks and history"; Computer: "Projects, terminal and
  files"). **CodeDiff is NOT listed** (per-project only, via chat card /
  Library) — spec §1.3 was corrected from "7 rows" to "6 rows".
- The 7 workspace screens themselves are untouched in content (real stores,
  real engines); only tokens/radii were restyled (`ui/workspace/
  WorkspaceScreens.kt`).
- **Deliberate decision, function preservation:** a "Bot Store" text button
  was added to the BOTS header. Without it the Bot Store becomes unreachable
  once the drawer is deleted (it was a drawer entry). Documented here so the
  verifier knows it is intentional, not spec drift.

## 3. Bots section — WhatsApp-style, per-bot chats

**Was:** `BotsScreen.kt:72` — `onStartBot = { bot -> vm.startBotChat(bot);
back() }` popped onto a blank new home chat (via `ChatViewModel.kt:205-207`
`startBotChat` → `newChat()`). Every bot tap destroyed the context and
opened the same blank chat.

**Now:**
- `ui/home/HomeScreen.kt` BOTS section sits at the TOP of Home, above chat
  history: eyebrow + "Bot Store" + "New bot" text buttons; 64dp rows, 44dp
  monogram, name + `chatTimeLabel` + last-message preview or bot
  description, "New" state when no thread, 8dp working dot. Tap →
  `vm.openBotChat(bot)` + `Route.BotChat(bot.id)`. Empty state has an "Open
  Bot Store" button. Lists installed AND created bots (`BotDefinitionStore`
  definitions + live parents/workers).
- `ui/home/BotChatScreen.kt` (NEW): thin wrapper — resolves the
  `BotDefinition` by id, `LaunchedEffect(botId) { vm.openBotChat(bot) }`,
  renders `ChatScreen(vm, navigate, onBack = back, bot = bot)`. If the bot
  was removed, calls `back()` once and shows a blank bg box. No second copy
  of the transcript/composer.
- `data/Stores.kt`: added `ConversationStore.latestForBot(botId)` — newest
  non-worker index entry with that botId, or null (old data may hold several
  threads per bot; newest wins, older ones stay visible in CHATS).
- `ui/ChatViewModel.kt`: deleted `exitBot()`; added `openBotChat(bot)` (the
  only UI entry point: `latestForBot` → `open(id)`, else `startBotChat(bot)`)
  and `botThreadMeta(botId)`. `startBotChat` is kept as the create-blank
  primitive only — no UI file calls it (verified by grep; only
  `openBotChat` calls it, `ChatViewModel.kt:218`). `open(id)` restores
  `activeBot` from the saved conversation's `botId` (`:491`), so reopening
  a bot thread keeps working as that bot. **Note:** a blank bot thread is
  NOT persisted until the first send (`conversationId = null`), so a double
  `openBotChat` call is harmless (the second call finds `latestForBot` null
  again and re-creates the same blank state).
- `ui/bots/CreateBotSheet.kt` (NEW): the old `CreateBotCard` extracted into
  an `AxionSheet` — name / description / persona / preferred provider
  (Auto/Gemini/Groq/OpenRouter/Grok) only, NO logo picker. On create:
  `botStore.create(...)` then dismiss then `onCreated(created)` (caller
  opens its chat).
- `ui/chat/ChatScreen.kt`: header shows the bot monogram + bot name when
  `bot != null`; new-chat button hidden in bot mode; overflow sheet Delete
  is bot-aware (bot mode → `onBack()`, else `vm.newChat()`). Back from a bot
  chat returns to the bot list — never to home/new chat.
- **Deleted:** `ui/bots/BotsScreen.kt` (whole file).

## 4. Token display removed from chat

**Was:** `ChatScreen.kt:310-319` — "Context 62% · 12,400 / 20,000 tokens" +
progress bar, and "Local model · 12.3 tok/s · 4,096 tokens" above the
composer.

**Now:** both blocks deleted from `ChatScreen.kt`. In `ChatViewModel.kt` the
`contextUsage` and `localSpeed` state fields are deleted, along with the
reset in `newChat()`, the `onContextUsage` feed-in at the Agent call (the
Agent's `onContextUsage` param has a default, so the call still compiles),
and the `localSpeed = result.speed` feed-in in `runLocalToolTurn`. The
`ContextUsage` / `LocalSpeed` measurement classes are untouched, and the
Quota screen is kept, and the compaction engine is untouched — the removal
is display-only. Verified: grep for `contextUsage|localSpeed` in `ui/chat/`
returns 0; `P2SessionTest.kt:34` and `P2LocalToolUseTest.kt:160` still assert
the engine classes' `label()` methods, which still exist.

## 5. Cartoon logo / mascot removed; "Axion" → "X"

**Was:** a white fluffy mascot character with big eyes and pom-pom ears
(verified visually in `drawable-nodpi/mascot_yaw_0.png`) in the header
(36dp), empty chat (48dp), drawer (32dp), splash (72dp), About (72dp) and
an animated 96dp intro; plus 10 PNGs and `assets/mascot.glb`; plus ~40
user-visible "Axion" strings.

**Now:**
- **Deleted files:** all 10 `drawable-nodpi/mascot_*.png`,
  `drawable-nodpi/axion_splash.png`, `assets/mascot.glb`,
  `ui/mascot/MascotView.kt` (whole directory removed, now empty),
  `ui/bots/BotsScreen.kt` (see §3).
- `ui/components/Wordmark.kt` (NEW): the only brand mark in the app —
  text "X" SemiBold, −0.5sp tracking, + 8×8dp bronze square with 2dp
  corners as the full stop (size scales as `fontSize * 0.36`, min 6dp).
  Used in the chat header (22sp), empty chat (22sp), splash (28sp), About
  (28sp) and the onboarding intro (40sp, rising in with the same timing
  the mascot had).
- `res/values/themes.xml`: the `windowSplashScreenAnimatedIcon` item for
  the deleted `axion_splash.png` is removed; without it the system shows
  the launcher icon on the splash. **Launcher icon checked and kept:** it
  is NOT the mascot — `mipmap-xxxhdpi/ic_launcher_fg.png` is an abstract
  silver ribbon/loop (verified visually).
- `ui/bots/BotLogoView.kt` rewritten monogram-only: same signature, but the
  stored `logo` param is ignored. Deterministic flat fill from the name
  hash (5 muted fills per theme), first TWO initials uppercase, 16sp
  SemiBold at 44dp (`size * 0.36`), 8dp rounded square. Deleted:
  `builtinIcon()`, `saveBotLogo()`, `builtinLabel()`, Coil photo/glyph
  paths (no callers remained). `Avatar` in `Common.kt` now delegates to
  `BotLogoView("", name, size)`.
- Whole-app "Axion" sweep (`/tmp/axion_sweep.py`, word-boundary regex
  `\bAxion's\b` → `X's`, then `\bAxion\b` → `X`, over all main Kotlin):
  257 replacements across 108 files; post-check: no file still contains
  `\bAxion\b`. Spot-checked risky hits before writing — all safe (comments
  or user-visible strings), e.g. `Agent.kt:518` default assistantName,
  `AgyBrain.kt:106` contains-check `"<axion_build>\nYou are X"` (consistent
  with `BuildPrompts.kt:14`), `ScheduledRunWorker.kt:175` fallback,
  `StatusServer.kt:97-98` title, `SkillHub.kt:84` licence, `SecretScanner.kt:
  96`, `Mcp.kt:134,277` clientInfo, `VoiceCatalog.kt:48,50` preview texts,
  `TelegramRunner.kt` replies, `BrowserDownloads.kt:31` `DIR = "X"`,
  `GallerySaver.kt` folders (`Movies/X`, `Music/X`, `Pictures/X`),
  `KeyBackup.kt:44-46`, `DevTools.kt:218`, `LocalModels.kt:177` and
  `LocalToolUse.kt:75` system prompts, `LiveCallProtocol.kt:89`,
  `CallContext.kt:29` role label `"X: "`. `Stores.kt:37` assistant-name
  default `"Axion"` → `"X"`.
- **Kept deliberately (documented):** internal identifiers `AxionApp`,
  `AxionTheme`, `AxionSheet`, `AxionSwitch`, `AxionPlugin`, `linkAxionTools`
  (renaming them would churn 100+ files for no user-visible gain);
  internal paths `.axion*` / `/opt/axion` (see §C); `NOTICE.md` /
  `BRAIN-SOURCES.md` attribution; the `axion_bg` resource name in
  `res/values/colors.xml`; the `TelegramApi.kt:25` KDoc example was changed
  to `"XBot"`.
- `ui/settings/SettingsScreens.kt`: section "Axion" → "Assistant";
  "Customize Axion" → "Customize X" (×2); "What X remembers about you";
  "Commands X may run without asking"; "Tasks X runs on a schedule"; "X's
  own — real pages, forms, screenshots"; "Text X from anywhere — off by
  default"; "About X"; email subject `"X ${BuildConfig.VERSION_NAME}
  problem"`; `SettingsPage` title centred 24sp → left-aligned `Type.title`
  22sp. `SchedulesScreen.kt:110` Roman-Urdu example rewritten in English.
- `ui/chat/ChatScreen.kt`: approval text → "X will ask every time.";
  clipboard label `"Axion"` → `"X"`; `SuggestionChip`/`UpdateBanner` radii
  20→6dp; dialogs 17→10dp; in-stream cards 13/16→10dp.
- **Verified:** grep for `MascotLogo|LogoMark|mascot` (case-insensitive)
  returns 0 in `app/src/main/`; grep for user-visible `\bAxion\b` in `ui/`
  (identifiers excepted) returns 0.

## 6. Navigation / data model

Covered by §2 and §3 (Home = chat history list; each bot = its own chat
inside the Bots section; `Route.Home` start; `Route.BotChat(botId)`;
`ConversationStore.latestForBot`). Brain/stores/engines were not touched
except for the `latestForBot` addition and the `ChatViewModel` changes
listed in §3–§4.

---

## A. Toolchain installs — the symlink bug (root cause + fix)

**Was:** Web (JS), Python and Android toolchains showed a red error
"Unsafe symlink target in archive: ..." on Install; the rest showed "Not
installed". The user saw a toolchain problem; the real bug was one shared
failure underneath all of them.

**Root cause (diagnosed, `linux/LinuxRuntime.kt:445`):** `extractTarGz`
resolved symlink targets against the HOST filesystem (`File(linkName)` for
absolute targets; `File(link.parentFile, linkName)` for relative). Ubuntu
base tarballs legitimately contain absolute symlinks meant to be
interpreted INSIDE the rootfs (e.g. `/usr/bin/*` → `/etc/alternatives/*`),
so resolved against the host they "escape" `dest` and the whole extraction
aborted — the install failed. Worse, a file entry written through an
earlier absolute symlink would also fail the entry-path check.
`extractTarGz` is used by `ensureBase()` (Ubuntu base download, SHA-256
checked, `LinuxRuntime.kt:99-140`) and by the Antigravity direct install
(`DevTools.installAgyDirect`) and `ObscuraInstaller.kt:88`. Every toolchain
install runs `ensureBase()` first on a fresh Linux, so the symlink bug was
the single shared failure for ALL toolchains — Web/Python/Android were
simply the ones he tapped; the other six were never attempted.

**Fix (`linux/LinuxRuntime.kt`, `extractTarGz`):**
- Signature now `(archive, dest, onCount = {}, onSkipped: (String) -> Unit
  = {})`; KDoc rewritten with the new policy.
- Absolute symlink targets are REWRITTEN to dest-relative targets at
  creation time (`Paths.get(canonical).resolve(raw.trimStart('/')).normalize()`
  + `linkParent.relativize(abs)`), so the stored link points inside `dest`
  both on the host and under PRoot. A symlink that is load-bearing inside
  the rootfs keeps working.
- Relative targets that genuinely escape `dest` are SKIPPED and reported via
  `onSkipped(...)` (they cannot be load-bearing inside the rootfs) — never
  planted, never silent.
- Entry-path (`../evil`) refusal and hard-link refusal are unchanged (a hard
  link cannot be rewritten; copying a host file in would leak host data).
- `ensureBase` passes named args `onCount = {...}, onSkipped = { skippedLinks
  += it }` (trailing-lambda trap fixed — with two function params the
  trailing lambda would bind to `onSkipped`, so both are named) and logs
  skips with `Log.w("LinuxRuntime", ...)` (added `import android.util.Log`).
- `DevTools.kt:273` (installAgyDirect) passes
  `onSkipped = { rawLine(uiId, "[agy] $it") }`;
  `ObscuraInstaller.kt:88` passes `onSkipped = { onProgress(it) }`.

**Tests (`app/src/test/java/com/mazharansari/x/TarExtractSecurityTest.kt`,
rewritten, KDoc rewritten with the new policy):**
- `absoluteSymlinkEscapeRefused` →
  `absoluteSymlinkRewrittenInsideDest` (link to a host-absolute path + file
  through it: extraction succeeds, nothing outside, link is a symlink with
  a relative in-dest target, file lands inside dest via the link).
- `relativeSymlinkEscapeRefused` → `relativeSymlinkEscapeSkipped`
  (escaping relative link + file through it + good file: extraction
  succeeds, nothing outside, link NOT planted, `onSkipped` called once
  naming the link, file lands inside dest as a plain file, good file
  extracts).
- New test `absoluteSymlinkToLaterFileResolves` (Ubuntu-style:
  `usr/bin/foo` → `/etc/alternatives/foo`, target file LATER in the
  archive: link resolves to `../../etc/alternatives/foo` and reads the
  file).
- `dotdotSymlinkEntryRefused`, `absoluteHardLinkEscapeRefused`,
  `legitSymlinkInsideDestStillWorks` unchanged. `assertRefused` helper kept.

## B. Builds in chat, inline

**Was:** `runBuildTurn` (`ChatViewModel.kt:840+`) ran ONLY when `projectId !=
null` (project chats opened via `openProject` from the Computer screen).
Plain chats had no project and no path to one; the Agent had no project /
build tool in `ToolCatalog`. `BuildHub.buildApk(p)` (`BuildHub.kt:152-197`)
was never invoked from any UI/VM path — it builds via Gradle in Linux (or
Turbo server), secret-scans the project first, writes the APK to
`<project>/.axion/out/<slug>.apk`, sets `JobState.Done("APK ready", …,
file)` on the `jobs` StateFlow. Commands ALREADY rendered inline live
(agent COMMAND steps stream through `sink.step` → 45ms flush ticker →
`BashCard` with live output tail + Running/Done/Failed pill) — that surface
was reused, not reimplemented.

**Now (`ui/ChatViewModel.kt`):**
- `var apkBuild: JobState?` + `var apkBuildPid: String?` (the chat renders
  the card only while that project is open).
- `attachProjectForBuild(name)` — creates an Android project via
  `g.build.create(name, "android")` and links it to the current conversation
  via `g.build.store.setConversation(pid, conversationId!!)` WITHOUT wiping
  messages (unlike `openProject`, which clears the conversation).
- `buildApkInChat()` — attaches a project if none, pre-checks
  `g.build.apkReady()` with a toast pointing at Developer tools, starts
  `g.build.buildApk(proj)`, collects `g.build.jobs` for key `"$pid:apk"`
  into `apkBuild`, calls `attachApkArtifact` on Done.
- `attachApkArtifact(apk, pid)` — guards `projectId == pid` so it never
  writes into a different chat; dedupes by path; appends an `Artifact` with
  mime `application/vnd.android.package-archive` to the last assistant
  message, or creates a new assistant message "APK ready: …".
- `ui/chat/ChatScreen.kt`: new `ApkBuildCard` private composable, inserted
  above `ToolsRow`, rendered only while `vm.apkBuildPid == vm.projectId`.
  Running: title + pulsing `DotGrid` + "Building" + last 3 log lines mono.
  Done: title/result + `ArtifactCard` for the APK + "Share APK" button
  (`shareFile` / `openFile` / `download` via `ui/home/FileActions.kt` —
  for an APK, Open launches the installer). Failed: error + lines + Retry.
  Overflow sheet gains `SheetRow(Gk.rocket, "Build APK") {
  vm.buildApkInChat() }`. No separate Build mode screen was brought back —
  the chat is the surface, for normal chatting AND building.
- `build/BuildPrompts.kt:14` "You are Axion's Build agent…" → "You are X's
  Build agent…"; `build/AndroidTemplate.kt:12` `pkgOf` prefix `"com.axion."`
  → `"com.x."`, Gradle properties `axion.compileSdk`/`axion.buildTools` →
  `x.*` (see §C).

## C. Axion separation — X's own update (or none)

**Was:** opening X showed a banner "Update available" — and the update
offered was Axion's. `update/AppUpdater.kt` — `UpdateRules.FEED =
"https://axion.130-210-62-94.sslip.io/dl/latest.json"`, `REPO_FEED =
"https://claworabeauty.store/uploads/dl/axion/latest.json"`, `FEEDS =
listOf(FEED, REPO_FEED)` (highest versionCode wins); `CERT_SHA256 =
"d064d58577350df28a532b83062e55a1ddc573799db702634af17fdc70c2e8c4"` —
Axion's signing cert. `check()` fetched both feeds; `quietCheck()` runs
once a day from `ChatViewModel` init and notifies on `Available` — that is
exactly the banner the user saw (`UpdateBanner` in `ChatScreen.kt`).

**Fix — deliberate interim state:** wiring X's own GitHub releases feed is
NOT straightforward/safe (GitHub API `releases/latest` has no versionCode
and no SHA-256 in its schema, and no X feed infrastructure exists yet), so
per the addendum's fallback the update check is **disabled entirely**:
- `FEEDS = emptyList()` with a KDoc explaining the interim state (X's own
  feed comes later from repo `mazharmnzoor4227-beep/x`; do NOT point at any
  Axion URL); `CERT_SHA256` → X's cert
  `7f458cdb4e0b7e8f11a3a6be4d893f8f8c78becfeb56949a0f549bc862328b0f`;
  `check()` early-returns a clear "not set up yet" Failed state when feeds
  are empty (never touches the network); `quietCheck()` returns immediately
  when feeds are empty (no banner, no notification).
- `EXTRA_OPEN_ABOUT` `"axion.open_about"` → `"x.open_about"` (consumer
  `MainActivity.kt:38` references the constant, so it stays consistent).
  APK file name `"Axion-${info.versionName}.apk"` → `"X-…"`;
  notification title `"Axion ${info.versionName} is available"` →
  `"X …"`; error strings "signed with Axion's key" → "X's key".
- The multi-feed machinery (check/download/verify/cert check) is kept
  intact for a future X feed.
- `ui/settings/SettingsScreens.kt` `UpdateCard`: Idle text rewritten
  honestly — "X does not check for updates yet — its own update feed is not
  set up. The button below will say so too."; "Allow Axion to install
  updates" → "Allow X…"; "for Axion" → "for X".
- **Tests:** `AppUpdateTest.kt` — `foreignSignatureRefused` assertion →
  `"isn't signed with X's key"`; `defaultFeedsCoverBothHosts` replaced by
  `defaultFeedsEmptyUntilXHasItsOwn` (FEEDS empty) +
  `defaultUpdaterSaysNotSetUp` (default updater returns Failed containing
  "not set up" without touching the network); `rules()` parse-fixture URL →
  `https://example.invalid/x-0.5.0.apk`; section comment reworded.
  `V05ScreensTest.kt:283` fixture `UpdateInfo` URL →
  `https://example.invalid/x-0.5.1.apk`.
- **Whole-app Axion cross-link audit (everything found and removed):**
  `llm/Providers.kt:73` OpenRouter header `X-Title: "Axion"` → `"X"`;
  `build/AndroidTemplate.kt:12` (see §B); `agent/GallerySaver.kt` folders
  `Movies/Axion`, `Music/Axion`, `Pictures/Axion` → `X`;
  `ui/home/FileActions.kt` `Downloads/Axion` → `Downloads/X`;
  `data/KeyBackup.kt:46` "unknown version of Axion" → "X";
  `linux/NativeSpawn.kt:76` "Reinstall Axion." → "Reinstall X.";
  `agent/PdfTool.kt` "Prepared by Axion" / drawText "Axion" → "X";
  `linux/DevTools.kt` rawLine `"Axion ${VERSION}"` → `"X …"`;
  `antigravity/AgyEngine.kt:150` "running inside Axion…" → "inside X…";
  plus the §5 sweep (257 replacements).
- **Kept deliberately (documented):** internal paths `.axion*` /
  `/opt/axion` (e.g. `/opt/axion/devtools/run.sh`,
  `/opt/axion/cacert.pem`, `.axion/out`, `.axion-signing`,
  `.axion-rootfs`, `.axion-tmp`, workspace `.axion/git-credentials`) —
  renaming them would break the Linux scripts and existing installs; they
  are sandbox-internal, not Axion-app infrastructure. Also kept: the
  intentional documentation KDoc in `AppUpdater.kt:52-53` naming the two
  deleted URLs (it says they are DELETED — documentation, not a live
  reference; the verifier's grep for the feed URLs will find only this
  KDoc).
- **Verified:** grep for `axion.130-210-62-94.sslip.io`,
  `claworabeauty.store/uploads/dl/axion` and the Axion cert `d064d5857…`
  returns only that KDoc.

## D. No resemblance to any previous design

Spec `docs/X-REDESIGN2-SPEC-2026-11-19.md` gained section
`## 0.0 Differentiation from every previous design` with a per-design table
(layout, colour, type, shapes, navigation) for each of: Axion's own UI
(periwinkle/teal, donor design); the Grok-mock UI (19-screen web prototype);
the OpenDots/OpenFuse port (light `#F8F7F4`, periwinkle/teal, drawer +
sheets); the round-1 big-brand redesign (light theme, indigo `#4F46E5`,
ChatGPT/Claude/Gemini/Grok-like, 20–28dp pills, full-round chips, one chat
screen, drawer nav); earlier themes in the codebase (SandTheme/DotsTheme).
Collision check recorded there: the only shared traits are generic (a dark
neutral background also appears in the Grok-mock dark `#1C1C1E` and round-1
dark `#171717`; bottom sheets also appear in the Dots port) — Ink Ledger's
bg `#14161A` is a cooler ink, and the differentiator is the combination
(bronze-only accent + hairline-ruled ledger rows + 11sp uppercase eyebrows
+ 12sp mono metadata + list-first Home + no drawer + wordmark + monogram
tiles). If any choice had resembled an earlier design it was changed; the
table is the proof. Two stale spec lines were also fixed in the same pass:
§1.3 "listing exactly 7 rows" → "6 rows" (CodeDiff not listed).

## E. ALL toolchain installs (not just the three with the error)

**Diagnosis:** all scripts in `app/src/main/assets/devtools/` were read
(`run.sh`, `common.sh`, `core.sh`, `web.sh`, `python.sh`, `android.sh`,
`cpp.sh`, `php.sh`, `browser.sh`, `androidsdk.sh`, `go.sh`, `rust.sh`,
`agy.sh`) and the `DevTools.kt` install flow (`Toolchain` enum:20-60,
`enqueue`/`runJob` ~195-250, `runScript` ~291-345). Installs run through
`DevTools.runJob` → `linux.ensureBase()` (first use only) → `runScript(tool,
action, …)` → `linux.run("/bin/bash /opt/axion/devtools/run.sh $tool
$action")`. `run.sh` sources `common.sh`, runs `<tool>.do_install`, then
requires `do_version` non-empty or dies `"<tool> did not verify after
install"`. **Finding: the symlink bug in `ensureBase` (§A) is the single
shared failure mode for ALL nine toolchains on a fresh Linux** — the ones he
tapped failed there; the other six show "Not installed" because they were
never attempted. No second per-tool failure mode hides behind them.
- **Verified live 2026-11-19 (curl):** SDK URL
  `https://appdevforall.org/dev-assets/debug/android-sdk-arm64-v8a.zip` →
  200 OK (application/zip); Gradle
  `https://services.gradle.org/distributions/gradle-8.11.1-bin.zip` → 307
  (CDN redirect, normal); Node index.json is COMPACT (so `node_latest`'s
  `grep '"lts":"'` works — an earlier space concern was wrong); Go latest is
  `go1.27.4`. `kotlin_latest` pattern works (grep on key name only, sed
  tolerant of spaces). Toolchain `needs` are wired (`BROWSER` needs `web`,
  `ANDROIDSDK` needs `android`; `enqueue` auto-queues deps). `runScript`
  has a 45-minute timeout per script and surfaces errors via `ERROR:` lines
  / last non-blank tail line. `common.sh` `apt_ready` patient pass tries
  only the first usable mirror by design (bounded time on mobile data) —
  left alone, documented.
- **Two genuine per-script fixes applied:**
  - `php.sh` — Composer fetch failure was silently tolerated
    (`fetch... && chmod` — `do_install` returned success and `do_version`
    still reported the PHP version, marking a partial install as success);
    now `fetch... || die "Composer download failed"` then `chmod 755`.
  - `rust.sh` — rustup output was suppressed (`>/dev/null 2>&1`), leaving
    a failed rustup with no diagnostics in the install log; redirect
    dropped so failures are diagnosable.

## F. Voice — Piper engine bundled (sherpa-onnx) + Aegis pack

**Was:** no on-device Piper engine; a Piper pick read in the phone's
built-in Urdu/Hindi voice with an honest notice. The user says the voice
still sounds bad on full listening.

**What was verified first (2026-11-19, sherpa-onnx official docs + example
code + DeepWiki, all fetched):** JitPack coordinate
`com.github.k2-fsa:sherpa-onnx:v1.10.42` (quoted from the shipping SherpaTTS
app); Java API `OfflineTts`, 7-arg `getOfflineTtsConfig(modelDir, modelName,
lexicon, dataDir, dictDir, ruleFsts, ruleFars)`, `OfflineTts(assetManager,
config)`, `tts.sampleRate()`, `generateWithCallback(text, sid, speed,
callback)` returning `GeneratedAudio` (`.samples`, `.save()`), callback
`(FloatArray) -> Int` (1 = continue, 0 = stop); espeak-ng-data download URL
`https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/espeak-ng-data.tar.bz2`
("shared by all models from piper, no matter which language"); tokens.txt
format `{phoneme} {id[0]}` per line, generated from the `.onnx.json`
`phoneme_id_map`; ONNX metadata fields (`model_type=vits`,
`comment=piper`, `language`, `voice`, `has_espeak=1`, `n_speakers`,
`sample_rate`); sherpa-onnx 2.0 dropped espeak-ng — so the verified 1.x
path (v1.10.42 + espeak-ng-data) is pinned, not the unverified 2.x lexicon
path. repo1.maven.org fetch failed 403 (flaky/blocked here; not retried).

**Critical finding about the local pack files:** `strings` grep on
`~/workspace/piper-voices/aegis.onnx` and `fasih.onnx` found NO
`model_type` / `has_espeak` / `comment` strings — the raw files carry no
Piper metadata, so sherpa-onnx 1.x will not recognise them as Piper VITS
models until the conversion step is applied. **Fixed on the VM:**
`~/onnx-venv` (onnx 1.17.0) + `/tmp/convert_piper.py` (the official
conversion script) produced converted copies at
`~/workspace/piper-voices/converted/piper-aegis/` and
`.../piper-fasih/` with the filenames the app expects
(`ur_PK-aegis-medium.onnx` / `.onnx.json`, `ur_PK-fasih-medium.onnx` /
`.onnx.json`) plus `tokens.txt` (166 lines Aegis, 161 Fasih — the cross-check
that the app's runtime generation uses the same algorithm). Metadata
verified present by `strings` (`model_type`, `comment=piper`,
`has_espeak`); originals untouched (md5 differs, as expected). The user
copies the CONVERTED pack dirs to the phone and imports them; the raw
files will be refused by the engine. Aegis `.json` (verified by reading):
`espeak: {voice: 'ur'}`, `phoneme_type: 'espeak'`, sample_rate 22050,
quality medium, 1 speaker, `piper_version 1.5.0`, `language.code 'ur_PK'`,
`dataset 'aegis_female'`, `speaker_id_map: {}` (single speaker, sid 0).

**Implementation:**
- `settings.gradle.kts`: JitPack repo added (required — the file uses
  `RepositoriesMode.FAIL_ON_PROJECT_REPOS` with only `google()` +
  `mavenCentral()`; JitPack must be added in settings, not the app module).
- `app/build.gradle.kts`: `implementation("com.github.k2-fsa:sherpa-onnx:
  v1.10.42")` with a comment (engine Apache-2.0; AAR carries arm64-v8a
  native libs; the app's abiFilters already keep arm64-v8a only).
- `voice/SherpaPiperEngine.kt` (NEW): implements `PiperVoiceEngine`.
  Lazy per-pack `OfflineTts`, cached; config via the 7-arg
  `getOfflineTtsConfig` with modelDir = the pack dir, modelName = the pack
  onnx name, dataDir = the espeak dir; `speak()` on `Dispatchers.IO` with an
  `AudioTrack` at `tts.sampleRate()` (`ENCODING_PCM_FLOAT`,
  `CHANNEL_OUT_MONO`, `MODE_STREAM`) + `generateWithCallback` + stop flag;
  returns `audio.samples.isNotEmpty()`. A `Mutex` serialises generations
  (OfflineTts is not thread-safe); `stop()` sets the flag the callback
  checks. Only VITS Piper packs run here — Kokoro is refused (different
  model family, needs its own sherpa-onnx config). Pace
  (`VoicePrefs.speed`) does not apply to Piper (natural rate, speed 1.0).
- `voice/PiperVoice.kt` (rewritten): header KDoc rewritten to the new
  honest state. **Per-pack dirs** — `<filesDir>/piper-voices/<pack.id>/
  <onnx + json + tokens.txt>` (the engine needs `tokens.txt` next to each
  `.onnx` as its modelDir); flat files from the pre-engine import UI are
  moved into per-pack dirs on first use. `ensureTokens(pack)` generates
  `tokens.txt` from the `.json` in the official format (no-op when it
  exists). `espeakReady()` (dir exists + `phontab` marker) and
  `downloadEspeakData(client, onProgress)` — downloads the verified
  tarball once (OkHttp streaming, 0–100 progress) and extracts the
  `.tar.bz2` with the same safety policy as `LinuxRuntime.extractTarGz`
  (no entry escapes the destination; escaping symlinks skipped, never
  planted). `importFile` now also accepts the short names the raw files
  carry (`aegis.onnx`, `fasih.onnx`) and always stores under the canonical
  name. `PiperVoiceEngine` gained a `stop()` method (default no-op).
- `AxionApp.kt`: `PiperVoiceEngines.engine = SherpaPiperEngine`
  registered next to the other ReadAloudPlayer hooks.
- `voice/ReadAloudPlayer.kt`: `stop()` now calls
  `PiperVoiceEngines.engine?.stop()`; the Piper fallback comment + missing-
  reason logic updated (engine is bundled now — the missing cases are the
  pack or the espeak data, and the notice names exactly which).
- `ui/settings/SettingsScreens.kt` Language screen: KDoc updated; new
  espeak-ng-data row (state: "Not downloaded — one download, shared by all
  Piper packs" / "Downloading · N%" / "Ready · N MB — shared by all Piper
  packs", tap downloads via `Http.download`); explainer text rewritten to
  the new honest state (engine built in; import the converted .onnx +
  .json; download the pronunciation data once; Kokoro reads in the phone
  voice for now). Pack rows keep their real download state.
- Spec §7.5 Voice & Language corrected to match (pack = import, espeak =
  download-once, engine = bundled).

**Decision (documented):** the engine is bundled in the APK; the 61 MB
packs are NOT bundled (import + espeak data download-on-first-use).
**Provisional, pending the voice research (still running, NOT yet
received):** Piper Urdu Aegis female stays the provisional default
(`AppSettings.voicePref` default `"piper-aegis"`); the final pack choice
may swap when the research lands — the swap point is `PiperPack` + the
default in `AppSettings`. Kokoro-82M Hindi remains the alternative in
principle, but it is NOT a Piper VITS model and the VITS engine does not
speak it; do not present Kokoro audio as Piper audio.

## G. Local models — curated list of 3, 4 GB phones

**Research findings used (received 2026-11-19 23:08, applied verbatim —
no invented quality claims):** LFM3-1.7B default (chat + agent + Hindi,
1.10 GB GGUF, ~1.4 GB RAM at runtime, needs llama.cpp v0.8.0+);
Qwen2.5-Coder-1.5B-Instruct coding only (HumanEval 70.7%);
Qwen3-1.7B-Instruct-2507 all-rounder (~1.28 GB). Rejected: Qwen3-4B and
Gemma 3 4B (don't fit 4 GB RAM); Qwen3-0.6B and Gemma 3 1B (too weak);
SmolLM2-1.7B (superseded); DeepSeek-R1-Distill-1.5B (bad chat, burns
context on thinking traces). Caveat: no candidate covers Urdu properly.

**llama.cpp version check — PASSED, no upgrade needed:** the vendored copy
at `third_party/llama.cpp/CMakeLists.txt` is `LLAMA_VERSION_MAJOR 0`,
`MINOR 8`, `PATCH 0` → **v0.8.0 exactly** (commit `dde879bc…`, 2026-11-01,
per `AXION_VERSION.txt`), which meets the requirement (≥ v0.8.0).
`llama_version()` exists in `include/llama.h:479`.

**Qwen3-1.7B GGUF filename — NOT confirmed, handled honestly:** the
Hugging Face API fetch for `Qwen/Qwen3-1.7B-Instruct-2507-GGUF` was
rejected (401) and was not retried; two web searches confirmed only the
naming convention (`…-Instruct-2507-Q4_K_M.gguf` pattern), not the file,
its byte size, or its SHA-256. So the Qwen3 entry ships with its download
URL **blank on purpose** — the store refuses to download it
(`DlState.Failed("Download link not confirmed yet — the exact file is
being checked…")`) and the Models screen shows "Download link not
confirmed yet — the exact file is being checked." instead of a Download
button. The URL/size/SHA go in `LocalModel.QWEN3_1_7B` the day the file is
verified. Never a guessed URL — a wrong SHA would make every download
fail verification forever.

**Implementation (`local/LocalModels.kt`, `ui/settings/OwnModelScreens.kt`):**
- Enum gained a `purpose` field (the label the screen shows). Final list:
  `LFM3_1_7B` ("Chat · Agent · Hindi — default", URL/size/SHA unchanged),
  `QWEN_CODER_1_5B` ("Coding only", URL/size/SHA unchanged, blurb now
  carries "HumanEval 70.7%"), `QWEN3_1_7B` ("All-rounder", blank URL, 0
  bytes, blank SHA, minRam 4, ctx 4096).
- Removed: `QWEN_CODER_3B`, `GEMMA3_1B`, `GEMMA3_4B` (rejected per the
  research). The only other reference was the `V05ScreensTest.kt:257`
  fixture — changed to `LFM3_1_7B` (see §T).
- `LocalModelStore.download(m)` refuses a blank URL; `file(m)` no longer
  resolves to the models dir itself for a blank URL (sentinel
  `".${m.id}.missing"`).
- Models screen: purpose shown as an accent eyebrow above each blurb;
  family tile fixed (Liquid AI gets "Lf" + bronze-deep tile; Gemma is
  gone); size line shows "~1.28 GB (to confirm)" for the unconfirmed
  model; Urdu caveat added as a Note right after the honest banner
  ("Languages: Hindi works well on LFM3; Urdu is limited on all three —
  none of these models covers Urdu properly yet."); bottom note updated
  ("Liquid AI and Qwen" instead of "Qwen and Google's Gemma").
- Everything stays download-on-first-use; NOTHING is bundled in the APK.
  `LocalModelStore` downloads on tap only (resumable, SHA-256 verified),
  `ramVerdict()` gives honest RAM warnings, `LocalEngine` loads one model
  at a time via JNI llama.cpp — that mechanism was already there and is
  unchanged.

## H. Design quality — every screen, once, right

Spec `docs/X-REDESIGN2-SPEC-2026-11-19.md` §7 (~26 KB, added this rebuild)
is now a complete per-screen design spec: §7.0 shared rules, §7.1 Home
list, §7.2 Bots section + Bot Store, §7.3 Bot chat, §7.4 Main chat (exact
style per message type: normal assistant 15sp/22 plain; user bubble 10dp
corners 4dp bottom-right, max 80% width; code message `BashCard`/`CodeBox`
dark 10dp card, 13sp mono/20, live output tail last 3 lines 12sp mono;
command-running pill 6dp with pulsing `DotGrid`; APK shared message
`ApkBuildCard` Running/Done/Failed states; approval card; install-progress
message; thinking `DotGrid`; composer 12dp min 56dp 16dp margins, 17sp/24
field, 6dp chips, 36dp accent send circle), §7.5 Settings + each settings
screen (incl. Voice & Language, Developer tools, Models, Quota, About X,
Call screen, Terminal), §7.6 Workspace sheet + all 7 workspace screens,
§7.7 Approval flow, §7.8 Onboarding/splash, §7.9 Documented exceptions
table (the only circles/capsules left in the app).

**Per-screen build-to-spec notes:**
- Home list, Bots section + bot list, bot chat, main chat: built to §7.1–
  §7.4 (`HomeScreen.kt`, `BotChatScreen.kt`, `ChatScreen.kt` + `ApkBuildCard`
  + `BashCard` live tail). Composer field text kept at 17sp/24 (deliberate
  readable input size, recorded in §7.4).
- Settings + each settings screen: built to §7.5 (`SettingsScreens.kt`
  restyle + `UpdateCard` honest state; `V04Screens.kt` DevTools screen
  verified by reading `DevToolsScreen` (:167-280) — Linux card, Toolchains
  group rows 64dp with status lines ("Installing · 47% · <step>" 12.5sp,
  `QuotaBar` 4dp-high, 22dp spinner), ToolSheet per toolchain, Tool log
  dialog, Updates/Models groups, Remove Linux flow; install progress is
  visible in the row itself and in the Tool log).
- Voice & Language, Developer tools, Models: built to §7.5 (see §F, §E, §G
  — espeak row, toolchain rows, purpose labels).
- Workspace sheet + all 7 workspace screens: built to §7.6
  (`WorkspaceScreens.kt` restyle; content untouched).
- Approval flow: built to §7.7 — verified against the built dialog
  (`ChatScreen.kt:325-360`): title 22sp "Allow this command?", body 14sp/20
  summary max 600 chars, 13sp scope/danger line, two rows of TextButtons
  15sp Medium ("Allow once" accent, "This chat"/"Always allow" text,
  "Decline" danger); dismiss without tapping counts as Decline; no
  transcript line records the decision. The spec's §7.4/§7.7 description
  was corrected to match this built dialog during the write.
- Onboarding/splash: built to §7.8 (`Onboarding.kt` bronze glow, Wordmark
  40sp intro; `themes.xml` splash now shows the launcher icon).
- Two spec-vs-code corrections made during the write: the floating-orb
  hang-up badge in `CallScreen.kt:362` changed 50 → 6dp in code to match
  §7.5/§7.9; the approval dialog description corrected as above.
- Animations: drawer slide (280ms) deleted with the drawer; sheet open
  200ms, wordmark intro same timing as the old mascot intro; `DotGrid`
  pulsing for running states; no other animation was left at a default.

---

## T. Test updates (UI that was deliberately redesigned)

- `V03ScreensTest` — `callScreen`, `connectorsLogos`, `popoverWithKeyboard`
  (+ `expandableSteps`, same root cause): the app now opens on the Home
  list, so each test navigates to a chat first via the `debugStart =
  Route.Chat` hook (no `newChat` — it would clear the messages
  `expandableSteps` sets directly). `voiceLibrary` left alone (already
  `@Ignore`).
- `V04ScreensTest.drawerClosesKeyboard` → rewritten as
  `backFromChatClosesKeyboard`: `route(Route.Chat)`, focus the composer,
  tap the chat header's Back chevron, assert the Home search field is not
  focused (the drawer is gone; Back is now the way out of chat).
- `V031ScreensTest.open()` — sets `vm.debugStart = Route.Chat` before
  `vm.open(id)`; stale "pull-up-for-new-chat gesture" comment removed
  from `toEnd()`.
- `CallContextTest:33` → `"X: Clawora Beauty."` (role label is X now).
- `BrowserDownloadsTest` — `downloadFolderIsAxion` → `downloadFolderIsX`,
  asserts `DIR == "X"`; KDoc "Downloads/Axion" → "Downloads/X".
- `V052ScreensTest:176` fixture → `"X 0.5.2 · Installing Antigravity CLI …"`.
- `V05ScreensTest.kt:257` fixture — `GEMMA3_1B` (removed model) →
  `LFM3_1_7B`.
- `TarExtractSecurityTest.kt` — rewritten for the new extractor policy
  (see §A). `AppUpdateTest.kt` — updated for the disabled update check
  (see §C).
- `V04Screens.kt:808` needed no change — the sweep had already made it
  `"This isn't an X keys file"`.
- Left as-is (fixtures/data, not assertions on renamed app output):
  `V05ScreensTest.kt:89`, `V052ScreensTest.kt:160`, `V03Test.kt:45,47`,
  `VoiceLogicTest.kt:49`, `LiveAgentTest.kt:130`, `SwarmPhase1Test.kt:67`,
  `ObscuraRealBinaryTest.kt:66`, `V052SecurityTest.kt:280`.
  `ScreensTest.screens()` left alone (already `@Ignore`).

## V. Verification — what was checked, and what was NOT

**Static checks run (all pass):**
- Brace/paren/bracket balance (string-aware checker — naive counting
  miscounts parens inside strings) on every edited file, main and test.
- Grep `MenuDrawer|drawerOpen|DrawerTile` → 0 in main Kotlin.
- Grep `MascotLogo|LogoMark|mascot` (case-insensitive) → 0 in
  `app/src/main/`.
- Grep `contextUsage|localSpeed` in `ui/chat/` → 0.
- Grep `startBotChat` → only its definition (`ChatViewModel.kt:205`) and
  the `openBotChat` call (`:218`); no UI file calls it.
- Grep old palette `#4F46E5|#818CF8|#8292D6|#35766F` → 0.
- Grep user-visible `\bAxion\b` in `ui/` (identifiers excepted) → 0.
- Grep Axion feed URLs / Axion cert → only the intentional documentation
  KDoc in `AppUpdater.kt:52-53`.
- Grep `axion_splash|Route.Bots|BotsScreen|builtinIcon|saveBotLogo|
  builtinLabel` → only KDoc/comments explaining where things came from.
- Deleted files confirmed gone: 10 mascot PNGs, `axion_splash.png`,
  `assets/mascot.glb`, `BotsScreen.kt`, `MascotView.kt` (+ `ui/mascot/`
  dir); `drawable-nodpi/` is empty.
- No `ci-artifacts/`, no `.jks`/`.pem`/`.env` in the tree.
- Live URL checks 2026-11-19: Android SDK zip 200 OK; Gradle zip 307
  (CDN redirect, normal); Node index.json compact; Go latest 1.27.4.
- Voice pack conversion verified on the VM (see §F): metadata present,
  tokens 166/161 lines, originals untouched.
- llama.cpp version verified from the vendored tree (see §G): v0.8.0.

**NOT verifiable here — the verifier / CI / phone must prove these:**
- **No local compile.** Gradle does not work in this sandbox (see
  `~/AGENTS.md`). Every Kotlin change above is statically checked only;
  **CI is the compile proof.** In particular the sherpa-onnx call shapes
  in `SherpaPiperEngine.kt` (7-arg `getOfflineTtsConfig` for v1.10.42,
  `OfflineTts(assetManager = null, config = …)`,
  `generateWithCallback(text, sid, speed, callback)`) are taken from the
  verified sherpa-onnx example code of the same era, but only CI can
  prove they compile against the v1.10.42 AAR — if the 7-arg form has
  changed, the fix is the 10-arg form (with acousticModelName/vocoder/
  voices) documented in the engine's KDoc, not a redesign.
- **No phone test.** The mascot/logo removal, the Home list, the Bots
  section, the ink theme, the Piper voice, the toolchain installs and the
  in-chat APK build all need the user's phone verdict — the same bar as
  every previous round. The exact phone speech throw that killed Settings
  in the masala app could not be reproduced in a test browser either;
  nothing here claims otherwise.
- **Qwen3-1.7B download URL/SHA** — not confirmed (see §G). The entry is
  present but not downloadable until the file is verified.
- **Voice research** — still running; the final pack choice (Aegis vs
  whatever it finds) is pending it. The mechanism is built so the pack
  swaps at `PiperPack` + the `voicePref` default.
- **Toolchain installs end-to-end** — verified by static trace + live URL
  checks + the extractor tests, not by a real install run on a phone.
  The first real install after this build is the proof.

## Files changed (summary)

New: `ui/home/HomeScreen.kt`, `ui/home/BotChatScreen.kt`,
`ui/bots/CreateBotSheet.kt`, `ui/components/Wordmark.kt`,
`voice/SherpaPiperEngine.kt`, this doc.
Rewritten: `ui/theme/Theme.kt`, `ui/bots/BotLogoView.kt`,
`voice/PiperVoice.kt`, `local/LocalModels.kt` (enum + store guards),
`app/src/test/java/com/mazharansari/x/TarExtractSecurityTest.kt`.
Edited (main): `ui/Route.kt`, `ui/AppRoot.kt`, `ui/ChatViewModel.kt`,
`ui/chat/ChatScreen.kt`, `ui/auth/Onboarding.kt`,
`ui/settings/SettingsScreens.kt`, `ui/settings/OwnModelScreens.kt`,
`ui/settings/V04Screens.kt`, `ui/components/Common.kt`,
`ui/chat/Composer.kt`, `ui/chat/MessageViews.kt`, `ui/chat/Sheets.kt`,
`ui/chat/StepDetail.kt`, `ui/chat/ReadAloudBar.kt`,
`ui/workspace/WorkspaceScreens.kt`, `data/Stores.kt`,
`linux/LinuxRuntime.kt`, `linux/DevTools.kt`,
`update/AppUpdater.kt`, `voice/ReadAloudPlayer.kt`, `AxionApp.kt`,
`app/build.gradle.kts`, `settings.gradle.kts`,
`res/values/themes.xml`, `app/src/main/assets/devtools/php.sh`,
`app/src/main/assets/devtools/rust.sh`, plus the §5/§C "Axion" sweep
(257 replacements, 108 files) and the radii/circles restyle batch
(18 files).
Edited (tests): `V03ScreensTest.kt`, `V04ScreensTest.kt`,
`V031ScreensTest.kt`, `CallContextTest.kt`, `BrowserDownloadsTest.kt`,
`V052ScreensTest.kt`, `V05ScreensTest.kt`, `AppUpdateTest.kt`.
Deleted: `ui/bots/BotsScreen.kt`, `ui/mascot/MascotView.kt`, 10
`drawable-nodpi/mascot_*.png`, `drawable-nodpi/axion_splash.png`,
`assets/mascot.glb`.
Outside the repo (do NOT ship): `~/workspace/piper-voices/converted/
{piper-aegis,piper-fasih}/` (converted voice packs for the user to copy
to the phone), `~/onnx-venv` (onnx 1.17.0, conversion tooling),
`/tmp/{chat,settings,common,restyle_batch,radii_sweep,circles_cleanup,
axion_sweep,lang,models,tests}_surgery.py`, `/tmp/convert_piper.py`.
