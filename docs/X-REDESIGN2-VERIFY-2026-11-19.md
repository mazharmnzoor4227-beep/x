# X Android App — Redesign Round 2: Verification (2026-11-19)

**Verifier:** stage-3 subagent (verification only — no app code changed).
**Tree:** `~/workspace/axion-new-series`, package `com.mazharansari.x` (not a git repo on disk; repo `mazharmnzoor4227-beep/x`).
**Docs read first, in order:** `docs/X-REDESIGN2-AUDIT-2026-11-19.md`, `docs/X-REDESIGN2-SPEC-2026-11-19.md` (incl. §0.0 differentiation table and §7 per-screen spec), `docs/X-REDESIGN2-REBUILD-2026-11-19.md`, plus round-1 contrast docs `docs/X-NEW-UI-SPEC.md`, `docs/X-REBUILD-2026-11-19.md`.
**Method:** grep + file reads against the actual code. Every verdict below names a file and line. The rebuild doc's word was not trusted anywhere — each claim was re-checked.

**Hard limits on this verification (say plainly):** Gradle does NOT work in this sandbox, so there is **no compile proof here — CI is the compile proof**. No phone test was possible: Home/Bots/theme on a real phone, Piper voice quality, toolchain installs actually succeeding on a device, and in-chat APK builds end-to-end are all **unverified by this stage** and must be proven by CI + the user's phone test. The sherpa-onnx API call shapes in `SherpaPiperEngine.kt` are statically plausible but only CI can prove they compile against the v1.10.42 AAR. Live URL checks claimed in the rebuild doc (§E) were **not re-run** here.

## 0. Overall verdict: GO for push (to CI)

| Scope | Result |
|---|---|
| Six redesign demands (1–6) | **6/6 PASS**, 0 FAIL |
| Addenda A–G | **6 PASS, 1 PROVISIONAL (F — voice pack choice pending research)**, 0 FAIL |
| Addendum H (per-screen spec) | **14 PASS, 8 PARTIAL, 0 FAIL**, 1 screen not verified in detail (Storage/Files) |
| Static hygiene | Brace/paren balance 29/29 edited files; no secrets in tree; no `ci-artifacts/`; tests updated only where the UI was deliberately redesigned |

No FAIL anywhere. The 8 PARTIALs in §H are all small, concrete deviations (a missing "New" label, 1sp type sizes, a missing projects list on Computer, inline-vs-detail Bot Store, BashCard hard-coded colours, Settings grouping). None of them blocks the push — they are fixable in a follow-up commit and none changes the user's verdict on the phone. The push goes to CI first; **CI green + the user's phone test remain the real gates**, exactly as in round 1.

## 1. Per-demand verdict table

### Demand 1 — Completely different UI ("Ink Ledger") — PASS

| Check | Evidence |
|---|---|
| Home is a list, app opens on it | `ui/AppRoot.kt:27` stack starts `Route.Home`; `Route.Home -> HomeScreen` (`:83`) |
| Dark-default ink + bronze | `ui/theme/Theme.kt:64-68` DarkPalette bg `#14161A`, surface `#1D2026`, accent `#E0A63F`; `:79` Light accent `#92400E`; `Stores.kt:15` theme default `"dark"`; `XTheme` default `XLook.Dark` (`Theme.kt:134`) |
| No old palette | grep `#4F46E5\|#818CF8\|#8292D6\|#35766F` in `app/src/main/` → **0 hits**; grep `#3B6BFF` → 0 |
| No green | grep green/lime hex/names in main Kotlin + res + devtools → only 4 hits, all comments saying "never green" (`Theme.kt:28-29`, `MessageViews.kt:247`, `StepDetail.kt:125`) |
| Rectilinear 4–12dp radii, hairlines, eyebrows, mono meta | `Type.eyebrow` 11sp/1.2sp (`Theme.kt:113`); `Type.mono` 12sp (`:112`); Home rows hairline-separated `HomeScreen.kt` `Hairline()` 1dp `P.border`, inset 72dp |
| No full-round shapes except documented §7.9 exceptions | grep `RoundedCornerShape(50\|999` / `CircleShape` in `ui/` → only Orb 220dp (`CallScreen.kt:294,348`), 36dp send circle (`Composer.kt:159`), 8/9dp dots (`MessageViews.kt:263,267`, `PlanCard.kt:44`, `V04Screens.kt:646`), 10dp Storage legend dot (`StorageScreen.kt:111`), 8dp ToolLog dot (`ToolLog.kt:105`) — all §7.9 exceptions or dots |
| English UI | No Urdu-script characters in `ui/` (Python scan of all `ui/**/*.kt` → 0 hits); `SchedulesScreen.kt:110` example rewritten in English |
| No demo/sample content | Home empty states are real ("No chats yet — …", "No bots yet — …", `HomeScreen.kt`); no seeded chat rows; chat starts empty (`ChatScreen.kt` EmptyChat) |
| Differs from Axion / Grok mock / OpenDots port / round-1 indigo per §0.0 table | Dark `#14161A` (not Axion `#F8F7F4`/periwinkle/teal, not Grok sand `#F3EEE6`/serif, not round-1 white `#FFFFFF`/indigo `#4F46E5`); list-first Home; no drawer; wordmark not mascot; system `FontFamily.Default` + bundled mono only (`Theme.kt:95-113`); spec §0.0 table itself present in the spec doc |

Minor nits (not verdict-changing): chat/bot titles use `Type.row` 15sp (`ChatScreen.kt:176,182`) where spec §7.3/§7.4 says 16sp; header status shows `settings.mode` (`"AUTO"` default, `Stores.kt:40`) rather than a provider-model string.

### Demand 2 — Menu simplified, no drawer — PASS

| Check | Evidence |
|---|---|
| No drawer | grep `MenuDrawer\|drawerOpen\|DrawerTile` in main Kotlin → **0 hits**; no hamburger on any screen (only a comment noting its absence, `ChatScreen.kt:164`) |
| Pull-to-new-chat gesture gone | grep `pullConnection\|NewChatPull` in `ChatScreen.kt` → 0 (only a comment line `:131` describing the deleted gesture) |
| 7 workspace screens reachable from one discreet place | Home top-bar grid button `Gk.grid` (`HomeScreen.kt:114`) opens the Workspace sheet with exactly 6 rows — Feed, Ideas, Goals, Library, Activity, Computer (`:255-272`); CodeDiff deliberately not listed (per-project only, via chat card / Library) |
| Rows carry real subtitles | Feed `"$feedCount need you"` / "Nothing needs you"; Ideas `"$n saved"` / "No ideas yet"; Goals `"$n active"` / "No goals yet"; Library / Activity / Computer fixed real subtitles (`HomeScreen.kt:257-271`) |
| Workspace screens themselves work | All 7 exist as routes + `AppRoot` branches (`AppRoot.kt:106-112`); content untouched, restyled to §2 tokens |

Deliberate deviation, documented in the rebuild doc §2 and accepted here: a **"Bot Store" text button** was added to the BOTS header (`HomeScreen.kt:169-170`). Without it the Bot Store would be unreachable once the drawer died. It is intentional, not drift.

### Demand 3 — Bots section, WhatsApp-style, per-bot chats — PASS

| Check | Evidence |
|---|---|
| Bots section at top of Home | BOTS section above CHATS (`HomeScreen.kt:155-204`); rows from `botStore.definitions` (installed + created) |
| Tap opens THAT bot's own chat | `HomeScreen.kt:178` `clickable { vm.openBotChat(bot); navigate(Route.BotChat(bot.id)) }` |
| `Route.BotChat(botId)` exists, branched | `ui/Route.kt` `data class BotChat(val botId: String)`; `AppRoot.kt:85` → `BotChatScreen` |
| Per-bot persisted threads | `data/Stores.kt:171` `ConversationStore.latestForBot(botId)` — newest non-worker entry with that `botId`, or null; `ChatViewModel.kt:216-218` `openBotChat` = `latestForBot` → `open(id)`, else `startBotChat(bot)` |
| Back returns to bot list | `BotChatScreen.kt:34` renders `ChatScreen(..., onBack = back)`; AppRoot `back()` pops the stack to `Route.Home` (`AppRoot.kt:60-62`); no ViewModel back handling, no path to `Route.Chat` |
| Old bug gone | `ui/bots/BotsScreen.kt` deleted (dir now holds only `BotLogoView.kt`, `BotStoreScreen.kt`, `CreateBotSheet.kt`); grep `startBotChat` → definition (`ChatViewModel.kt:205`) + the single call inside `openBotChat` (`:218`) only; **no UI file calls it**; pattern `startBotChat(bot); back()` gone |
| `exitBot` / "Chatting as" strip gone | grep `exitBot` → only a KDoc comment (`ChatViewModel.kt:214`); grep `Chatting as|Exit bot` → 0 |
| `open(id)` restores the bot | `ChatViewModel.kt:490-491` `activeBot = botFor(c.botId)` on saved-chat open; `:473` same for live sessions |
| Create-a-bot opens its chat | `CreateBotSheet.kt:87-94` create → `onCreated`; `HomeScreen.kt:270` `onCreated = { vm.openBotChat(created); navigate(Route.BotChat(created.id)) }` |
| Install does not open a chat | `BotStoreScreen.kt:75` install → toast only: "Installed '…' — find it in the Bots section on Home" |
| Bot rows show preview + time | `HomeScreen.kt:175-196` `botThreadMeta(bot.id)` → last-message preview + `chatTimeLabel`; working 8dp accent dot while its run is live |
| RUNNING NOW group | `HomeScreen.kt:143-153` live parents/workers only while runs are live; worker tap → `openWorkerChat` + `Route.Chat` (correct — worker transcripts keep parent-return behaviour) |

Minor deviation: spec §7.1 item 4 wants a **"New" 12sp mono `muted` label** on bot rows with no thread. The code shows the bot description as preview instead (`HomeScreen.kt:190`) and no "New" text. Cosmetic; the threadless state is still distinguishable (no time label shown).

### Demand 4 — Token display removed from chat — PASS

| Check | Evidence |
|---|---|
| Context/token lines deleted | grep `contextUsage\|localSpeed` in `ui/chat/` → **0 hits**; grep `token` in `ui/chat/` + `ui/home/` → only a KDoc comment (`ChatScreen.kt:61`) and auth-token uses in `CliPairScreen.kt:45,88` ("Has token" = Antigravity pairing token, not a token count) |
| Measurement code kept (display-only removal) | `agent/ContextUsage.kt` and `local/LocalToolUse.kt` untouched (compaction trigger `shouldCompact` intact); Quota screen kept (`V04Screens.kt:530`) — Settings-only usage, explicitly out of scope |
| No "Context % / tok/s" text anywhere in chat | Verified by the greps above; `ChatScreen.kt` renders no token line above the composer |

### Demand 5 — Cartoon logo removed; "Axion" → "X" — PASS

| Check | Evidence |
|---|---|
| Mascot code gone | grep `MascotLogo|LogoMark|mascot` case-insensitive in `app/src/main/` → **0 hits**; `ui/mascot/` directory does not exist; `res/drawable-nodpi/` is **empty**; no `mascot.glb`, no `axion_splash.png` anywhere under `app/src/main` |
| Wordmark "X▪" present | `ui/components/Wordmark.kt` — "X" SemiBold, −0.5sp tracking, 8×8dp bronze square, 2dp corners, scales `fontSize * 0.36`; used in chat header 22sp (`ChatScreen.kt:173`), empty chat 22sp (`:510`), splash 28sp (`:571`), About 28sp (`SettingsScreens.kt:489`), intro 40sp (`Onboarding.kt:83`) |
| Launcher icon is NOT the mascot | `mipmap-xxxhdpi/ic_launcher_fg.png` **visually inspected** in this verification: an abstract silver ribbon/loop on black, no character, no face. Keeping it is per spec §3.5 (replace only if it depicts the character). `themes.xml` splash icon item removed, so the system shows the launcher icon |
| Bot avatars are monogram tiles | `ui/bots/BotLogoView.kt` rewritten: 8dp rounded square, deterministic muted fill from name hash (5 fills, dark + light sets), first **two** initials uppercase, 16sp SemiBold at 44dp; stored `logo` param deliberately ignored; `builtinIcon()` / `saveBotLogo()` / `builtinLabel()` / `LogoPickerSheet` gone (grep → 0); `Avatar` in `Common.kt:199-201` delegates to `BotLogoView` |
| No user-visible "Axion" strings | grep word-boundary `\bAxion\b` in main Kotlin, excluding code identifiers → **only** the `AppUpdater.kt:51-57` KDoc (documented, see §C); grep `Axion` in `ui/` → **0 hits** outside identifiers; `res/values/strings.xml` `app_name` = `"X"`; `Stores.kt:37` assistant-name default `"Axion"` → `"X"`; Settings section "Axion" → "Assistant", "Customize Axion" → "Customize X", "About Axion" → "About X", email subject → `"X … problem"` (`SettingsScreens.kt:116,118,144,411`); clipboard label → `"X"` (`ChatScreen.kt:210`); approval text → "X will ask every time." (`:346`) |
| Onboarding glow | `Onboarding.kt:79,136` bronze `#E0A63F` glow, alpha 0.28/0.30; 50dp pills → 8dp; permission icons 12dp rounded squares (`:137`); progress dots 3dp (`:126`) |

Remaining "Axion" references (complete list — all deliberate, none user-visible; see §3): code identifiers `AxionApp` / `AxionTheme` / `AxionSheet` / `AxionSwitch` / `AxionPlugin` / `linkAxionTools`; internal paths `.axion*` / `/opt/axion` (rootfs, devtools, out dirs); resource name `axion_bg` (`res/values/colors.xml:3-4`, `#FF1B1B1B`); the `AppUpdater.kt:51-57` KDoc naming the deleted feeds.

### Demand 6 — Navigation / data model — PASS

Home = chat history list (`Route.Home`, `buildChatRows`, pinned first, newest first, worker transcripts excluded); each bot owns its thread (`latestForBot` + `Route.BotChat`); plain chats are threads too; brain/stores/engines untouched except the `latestForBot` addition and the `ChatViewModel` changes listed in §3–§4 of the rebuild doc. `ChatViewModel` single-active-conversation machinery kept (`sessions` map, `open(id)`).

## 2. Addenda verdicts

### A. Toolchain extractor ("Unsafe symlink target in archive") — PASS (static)

- Error site found: `linux/LinuxRuntime.kt:433` `extractTarGz` (used by `ensureBase()` `:135`, `DevTools.installAgyDirect` `:273`, `ObscuraInstaller.kt:88`).
- Fix verified in code: absolute symlink targets are **rewritten** to dest-relative (`:459-463` — `canonDest.resolve(raw.trimStart('/'))` + `linkParent.relativize(abs)`), so Ubuntu-style `/usr/bin/* → /etc/alternatives/*` links keep working inside the rootfs; relative targets that genuinely escape `dest` are **skipped and reported** via `onSkipped` (`:466-467`), never planted, never silent; entry-path (`../evil`) refusal via canonical-path check (`:449`) and hard-link refusal (`:486`) unchanged.
- Callers wire `onSkipped`: `ensureBase` collects into `skippedLinks` + logs (`:135-136`), `DevTools.kt:273` reports to the install log, `ObscuraInstaller.kt:88` reports to progress.
- Tests rewritten to the new policy (`TarExtractSecurityTest.kt`): `absoluteSymlinkRewrittenInsideDest`, `relativeSymlinkEscapeSkipped`, `absoluteSymlinkToLaterFileResolves` (Ubuntu-style later-file target), plus unchanged `dotdotSymlinkEntryRefused`, `absoluteHardLinkEscapeRefused`, `legitSymlinkInsideDestStillWorks`.
- **Not verifiable here:** a real install on a phone. The first real install after this build is the proof.

### B. Builds in chat, inline — PASS (static)

- `ChatViewModel.kt:231-233` `apkBuild` / `apkBuildPid` state; `:243` `attachProjectForBuild(name)` (links project without wiping messages); `:264` `buildApkInChat()` (attaches a project if none, pre-checks `apkReady()`, collects `g.build.jobs` for `"$pid:apk"`); `:286` `attachApkArtifact` (guards `projectId == pid`, dedupes by path, appends APK artifact to the last assistant message or creates "APK ready: …").
- `ChatScreen.kt:282-283` renders `ApkBuildCard` above ToolsRow only while `vm.apkBuildPid == vm.projectId`; `:435-495` `ApkBuildCard` — Running (title + pulsing `DotGrid` + "Building" + last 3 log lines mono), Done (title/result + `ArtifactCard` + "Share APK" button), Failed (error + lines + Retry).
- Overflow sheet gains "Build APK" (`ChatScreen.kt:406`); no `Section.BUILD` / Build mode screen anywhere (grep → 0).
- Command output already streamed inline via `BashCard` (`MessageViews.kt:162,286`) — reused, not reimplemented.
- **Not verifiable here:** a real Gradle build on a phone (5–15 min first build, ~1 GB of libraries) and the installer launch from the APK card.

### C. Axion separation — PASS

- `update/AppUpdater.kt:60` `FEEDS = emptyList()` with KDoc explaining the interim state; `:61` `CERT_SHA256` = X's cert `7f458cdb4e0b7e8f11a3a6be4d893f8f8c78becfeb56949a0f549bc862328b0f`; `check()` early-returns Failed "X does not check for updates yet — its own update feed is not set up." without touching the network; `quietCheck()` returns immediately when feeds are empty (`:144-148`).
- Grep for the Axion feed URLs / Axion cert (`axion.130-210-62-94.sslip.io`, `claworabeauty.store/uploads/dl/axion`, `d064d5857…`) → **only** the intentional KDoc in `AppUpdater.kt:52-53` saying they are DELETED.
- `UpdateBanner` (`ChatScreen.kt:538-551`) only renders on `UpdateState.Available`, which is unreachable with empty feeds — no "Update available" banner can appear.
- Settings `UpdateCard` honest state (`SettingsScreens.kt:527,560`): "X does not check for updates yet — its own update feed is not set up. The button below will say so too."
- Cross-link sweep verified in the places checked: OpenRouter header `"X"` (`llm/Providers.kt:73` per rebuild doc), GallerySaver folders `X`, `Downloads/X` (`FileActions.kt`), Android template prefix `com.x.` / `x.*` properties, APK file name `"X-…"`, notification/error strings "X's key".
- Remaining Axion references: see the complete list at the end of §1 demand 5 — code identifiers, internal `.axion*` / `/opt/axion` paths, `axion_bg` resource name, one KDoc. All documented as deliberate; none is a live Axion URL, feed, or cross-link.

### D. No resemblance to any previous design — PASS

- Spec `docs/X-REDESIGN2-SPEC-2026-11-19.md` §0.0 differentiation table exists (Axion donor, Grok mock, OpenDots/OpenFuse port, round-1 big-brand, earlier Sand/Dots themes — layout/colour/type/shapes/nav per row, with the collision check recorded).
- Built theme matches the table's "Ink Ledger" column on every axis: dark `#14161A` default, single bronze `#E0A63F`/`#92400E`, list-first Home, no drawer, wordmark, monogram tiles, 11sp eyebrows, 12sp mono metadata, hairline ledger rows, 4–12dp radii. The only shared traits with prior designs are the generic ones the table already names (a dark neutral background; bottom sheets as a secondary surface).

### E. All toolchain installs — PASS (static trace only)

- 9 toolchains in `linux/DevTools.kt:23-32` (Web, Python, Android, C/C++, PHP, Browser, Android SDK, Go, Rust) + hidden `AGY` (`:33`); `needs` wired (BROWSER needs web, ANDROIDSDK needs android).
- All install through one path: `DevTools.runJob` → `linux.ensureBase()` (first use) → `runScript` → `/bin/bash /opt/axion/devtools/run.sh $tool $action`; `run.sh:8` requires `do_version` non-empty after `do_install` or dies `"<tool> did not verify after install"`. Every script in `assets/devtools/` (`web, python, android, cpp, php, browser, androidsdk, go, rust, agy.sh`) defines both `do_install` and `do_version` (verified per file).
- The symlink bug (§A) is the single shared failure for all nine on a fresh Linux — consistent with the trace: every install runs `ensureBase()` first.
- Two per-script fixes verified present: `php.sh:20` Composer fetch now `|| die "Composer download failed"`; `rust.sh:8` rustup output no longer suppressed (`>/dev/null 2>&1` removed).
- **Not re-verified here:** the rebuild doc's live URL checks (SDK zip 200, Gradle 307, Node index compact, Go 1.27.4). Static trace + extractor tests are the evidence at this stage; a real install on the phone is the proof.

### F. Voice — Piper engine bundled (sherpa-onnx) + Aegis pack — PROVISIONAL PASS

Mechanism built; final pack choice pending the still-running voice research — flagged PROVISIONAL per the task, not failed.

- `settings.gradle.kts:8` JitPack repo; `app/build.gradle.kts:151` `implementation("com.github.k2-fsa:sherpa-onnx:v1.10.42")`.
- `voice/SherpaPiperEngine.kt` (127 lines): lazy per-pack `OfflineTts` via 7-arg `getOfflineTtsConfig` (`:68`), `AudioTrack` at `tts.sampleRate()` (`:92-94`), `generateWithCallback(text, sid=0, speed=1.0f)` (`:111`), `Mutex`-serialised, `stop()` flag; Kokoro refused (different family).
- `AxionApp.kt:38` registers `SherpaPiperEngine`; `ReadAloudPlayer.kt:140,537` uses/stops it; missing-reason logic names exactly which piece is missing (pack vs espeak data).
- `voice/PiperVoice.kt`: per-pack dirs, `ensureTokens` (generates `tokens.txt` from `.json`), `espeakReady()` / `downloadEspeakData` with the same extractor safety policy; `importFile` accepts short raw names (`aegis.onnx`, `fasih.onnx`) and stores under canonical names.
- Settings → Voice & Language: espeak-ng-data row with real states (`SettingsScreens.kt:238-247` — "Not downloaded… / Downloading · N% / Ready · N MB — shared by all Piper packs"); explainer text states the honest state, incl. "Kokoro Hindi … reads in the phone voice for now" (`:260`); default `voicePref = "piper-aegis"` (`Stores.kt:32`).
- Converted packs exist on the VM at `~/workspace/piper-voices/converted/{piper-aegis,piper-fasih}/` (outside the repo, for the user to copy to the phone) — noted, not re-verified byte-by-byte here.
- **Unverified / provisional:** sherpa-onnx call shapes compile only via CI; Piper voice quality on the phone; the final pack choice may swap at `PiperPack` + the `voicePref` default when the voice research lands.

### G. Local models — curated list of 3 — PASS

- `local/LocalModels.kt:62-75`: exactly 3 entries — `LFM3_1_7B` ("Chat · Agent · Hindi — default"), `QWEN_CODER_1_5B` ("Coding only", blurb carries "HumanEval 70.7%"), `QWEN3_1_7B` ("All-rounder"). Removed models (`QWEN_CODER_3B`, `GEMMA3_1B`, `GEMMA3_4B`) gone from the enum (grep → 0 in the file).
- llama.cpp version verified from the vendored tree: `third_party/llama.cpp/CMakeLists.txt:6-8` `0.8.0` — meets ≥ v0.8.0; `llama_version()` exists (`include/llama.h:479`).
- Qwen3 GGUF URL **blank on purpose** (`LocalModels.kt:73` `"", 0, ""`); `LocalModelStore.download` refuses blank URLs with "Download link not confirmed yet — the exact file is being checked." (`:109`); `file(m)` sentinel `".${m.id}.missing"` (`:95`); Models screen shows the same honest text instead of a Download button (`OwnModelScreens.kt:115`) and "~1.28 GB (to confirm)" (`:84`). No guessed URL anywhere.
- Purpose labels shown as accent eyebrow above each blurb (`OwnModelScreens.kt:91`); Urdu caveat present (`:62` — "Hindi works well on LFM3; Urdu is limited on all three — none of these models covers Urdu properly yet."); nothing bundled, download-on-first-use with SHA-256 verification.

## 3. Per-screen checklist (§H of the spec)

| Screen | Verdict | Evidence / deviation |
|---|---|---|
| Home list (§7.1) | **PARTIAL** | Layout, 64dp settings block, 48dp search, RUNNING NOW, BOTS, CHATS, hairlines, empty states all match `HomeScreen.kt`. Deviations: no "New" label on threadless bot rows (description shown instead); Workspace sheet title uses `Type.title` 22sp vs spec 17sp; pin icon 14dp vs spec 16dp. |
| Bots section + Bot Store (§7.2) | **PARTIAL** | Home BOTS list, "New bot" sheet (`CreateBotSheet.kt`: name/description/persona/provider chips Auto/Gemini/Groq/OpenRouter/Grok, no logo picker), install toast, monogram rows — all present. Deviations: **no tap-to-detail screen** — detail (name/description/source/skills) is shown inline in the row card itself (`BotStoreScreen.kt:44-100`); rows are 10dp cards, not 64dp hairline rows; name 16sp Bold vs spec 15sp SemiBold. |
| Bot chat (§7.3) | **PARTIAL** | `BotChatScreen.kt` thin wrapper, shared transcript/composer, 56dp header with back chevron + 36dp monogram + name, no new-chat button in bot mode, no "Chatting as" strip. Deviations: bot name 15sp (`Type.row`) vs spec 16sp; subtitle is "Bot · <provider>" only — no "· New thread" / thread-age suffix the spec wants. |
| Main chat (§7.4) | **PARTIAL** | Back-chevron header, wordmark, 56dp, no hamburger; user bubble 10dp/4dp bottom-right, max 80% (`MessageViews.kt:69`); composer 12dp, 17sp/24 field, 6dp chips, 36dp accent send circle (`Composer.kt:63,110,136,159`); ApkBuildCard Running/Done/Failed (§B); approval dialog 22sp title, 14sp/20 body, 4 buttons, dismiss = Decline (`ChatScreen.kt:335-360`); empty chat wordmark + "Start typing below." Deviations: **BashCard uses hard-coded colours** (`#17181C` bg, `#26262B` border, khaki `#CACBB2`/salmon `#B38F82` code — `MessageViews.kt:286-330`) instead of spec `codeBg #1E1E1E` / `codeText #F5F5F5`, and shows output clipped to ~6 lines (max 132dp + fade) rather than a live last-3-lines tail; **ThinkingRow adds "Reading …" 16sp text** (`ChatScreen.kt:498-505`) where spec §7.4 says DotGrid with no text; header status shows mode `"AUTO"` rather than provider-model. (BashCard KDoc also still says "13dp corners" while the code is 10dp — stale comment only.) |
| Settings main (§7.5) | **PARTIAL** | Rename done ("Assistant", "Customize X", "About X"); `SettingsPage` left-aligned 22sp title, 56dp header, status-bar gap (`SettingsScreens.kt:62-70`); UpdateCard honest state; profile + providers cards present. Deviations: **section order is still the old Axion order** (App / Assistant / Data & Information) — §7.5's regrouping (Assistant / Providers / Tools / Data / About) was NOT done. Note this is a **spec-internal conflict**: §3.6 explicitly forbids reordering beyond the rename ("a reorder is a third redesign"), and the build followed §3.6 — recorded here, not counted as a build failure. Also: profile card uses a 56dp avatar box + 18sp name vs spec 40dp monogram + 15sp; providers "Connect" button uses **white text on bronze** (`SettingsScreens.kt:110-112`, `p.blue` = `#E0A63F` in dark theme — poor contrast; spec wants `onAccent` text); no "Models" row in Settings main (Local Models is reachable via Connectors → "Local model", `ConnectorsScreen.kt:116`). |
| Voice & Language (§7.5) | **PASS** | Espeak row, pack states, honest explainer, Kokoro note — see §F. |
| Developer tools (§7.5) | **PASS** | Intro note, Linux card with `QuotaBar`, Toolchains group rows 64dp with status lines ("Ready in PRoot · <version> · <size>" / "Not installed · ~<size> MB download", `V04Screens.kt:231-232,240`), 4dp install bars (`:243`), ToolSheet/log, Updates/Models groups, Remove Linux flow — verified by reading `DevToolsScreen` (`V04Screens.kt:167-390`); amber `Amber #F5A524` used for Installing, never green. |
| Models (§7.5) | **PASS** | See §G. |
| Quota (§7.5) | **PASS** | `QuotaScreen` (`V04Screens.kt:530`) with `QuotaBar` capsules + usage lines; the only place usage numbers appear. |
| Storage / Files (§7.5) | **NOT VERIFIED IN DETAIL** | Not line-checked in this pass beyond the StorageScreen 10dp legend dot (`StorageScreen.kt:111`). Existence confirmed; exact §7.5 conformance (storage bar, "Clear cache" sheet) was not verified — CI/phone or a follow-up check should cover it. |
| About X (§7.5) | **PASS** | Wordmark 28sp + version lines, "Report a problem" with subject "X <version> problem" (`SettingsScreens.kt:489`). |
| Call screen (§7.5) | **PASS** | Orb 220dp, floating orb 68dp square tile, hang-up badge 24dp with **6dp corners** (`CallScreen.kt:362`, fixed from 50dp per rebuild doc). |
| Terminal (§7.5) | **PASS** (minor) | Mono transcript, 10dp card, mono input (`TerminalScreen.kt:78,84,88,124`). Minor: terminal bg is `P.surface`, spec says `surfaceSunken`. |
| Workspace sheet (§7.6) | **PARTIAL** | 6 rows, real subtitles, 16dp top corners via `AxionSheet`, 200ms slide-up, max 85% height. Deviation: sheet title "Workspace" uses `Type.title` 22sp (`HomeScreen.kt:257`) vs spec 17sp SemiBold. |
| Feed (§7.6) | **PARTIAL** | Header, cards, "NEEDS YOU" / "WHILE YOU WERE OUT" groups, real data. Deviation: empty state reads **"Nothing yet"** (`WorkspaceScreens.kt:103`) vs spec "Nothing needs you." |
| Ideas (§7.6) | **PASS** | 64dp rows, 6dp status chip, detail, empty "No ideas yet" (`:164`). |
| Goals (§7.6) | **PASS** | 64dp rows, 4dp progress bar, "<n>% · <done>/<total>" mono, empty "No goals yet" (`:225`). |
| Library (§7.6) | **PASS** | 56dp rows, search field, empty "No files yet" (`:263`). |
| Activity (§7.6) | **PASS** | 56dp rows, Running/Done/Failed mono status, run detail with mono log, empty "No activity yet" (`:318`). |
| Computer (§7.6) | **PARTIAL** | Browser / Files / Terminal cards present with real state. Deviation: **no projects list and no "No projects yet — ask X to build one in chat" empty state** — the spec §7.6 projects list does not exist in the build (round 1 didn't have it either; the spec describes content the code never had). |
| CodeDiff (§7.6) | **PASS** (minor) | Added `accentSoft`/bronze `+`, removed `dangerSoft`/danger `−` (`WorkspaceScreens.kt:439`); test pill neutral text. Minor: header reads "Code changes" (`:400`) vs spec "Code review". |
| Approval flow (§7.7) | **PASS** | Dialog, scrim, 140ms fade/scale, dismiss = Decline, no transcript line records the decision (`ChatScreen.kt:334-363`). |
| Onboarding / splash (§7.8) | **PASS** | Splash wordmark 28sp, ~700ms, tap to skip (`ChatScreen.kt:566-577`); intro wordmark 40sp rising in, bronze glow, no indigo (`Onboarding.kt:79-83`); permission rows 56dp, 8dp buttons, 3dp progress dots. |

## 4. Static hygiene

- **Brace/paren/bracket balance:** string-aware checker over all 29 edited main files (Theme, Route, AppRoot, ChatViewModel, ChatScreen, HomeScreen, BotChatScreen, BotLogoView, CreateBotSheet, Wordmark, Common, Composer, MessageViews, Sheets, StepDetail, WorkspaceScreens, SettingsScreens, V04Screens, OwnModelScreens, Stores, LinuxRuntime, DevTools, AppUpdater, SherpaPiperEngine, PiperVoice, ReadAloudPlayer, LocalModels, Onboarding, CallScreen) → **29/29 balanced**.
- **Secrets:** no `.jks` / `.pem` / `.env` / `.keystore` anywhere in the tree; no `ci-artifacts/` directory.
- **Tests updated only where the UI was deliberately redesigned:** `V03ScreensTest` navigates via `debugStart = Route.Chat` (app now opens on Home); `V04ScreensTest.drawerClosesKeyboard` → `backFromChatClosesKeyboard` (drawer gone; Back is the way out); `V031ScreensTest.open()` sets `debugStart`; `AppUpdateTest` — `defaultFeedsEmptyUntilXHasItsOwn` + `defaultUpdaterSaysNotSetUp` + "isn't signed with X's key"; `TarExtractSecurityTest` — 6 tests for the new extractor policy; `CallContextTest`, `BrowserDownloadsTest`, `V052ScreensTest`, `V05ScreensTest` fixtures renamed X / model fixture swapped to `LFM3_1_7B`. No app behaviour was changed to satisfy a test — every test change follows a deliberate redesign change. **Test results themselves are unverified here (no Gradle); CI runs them.**
- **Round-1 contrast (sanity):** round 1 was light/indigo/pill/chat-first with a drawer; this build is dark/bronze/rectilinear/list-first with no drawer, a wordmark instead of the mascot, and per-bot chats. The differentiation is structural, not a recolour.

## 5. Open items (for the parent / CI / phone test)

1. **CI must go green** — compile (esp. sherpa-onnx v1.10.42 API shapes in `SherpaPiperEngine.kt` and the JitPack dependency) + full test suite. If the 7-arg `getOfflineTtsConfig` form has changed, the engine's KDoc already documents the 10-arg fallback — a fix, not a redesign.
2. **Phone test by the user** — Home list, bot tap → bot chat → back → different bot, Settings gear, Workspace sheet, Piper voice quality on full listening, first real toolchain install, first in-chat APK build. Same bar as every previous round; nothing here claims the phone verdict.
3. **Voice research still pending** — final pack choice (Aegis vs whatever it finds) may swap at `PiperPack` + the `voicePref` default. Mechanism is built; do not treat Aegis as final.
4. **Qwen3-1.7B download URL/SHA still unconfirmed** — entry is present but not downloadable by design; fill in `LocalModel.QWEN3_1_7B` the day the file is verified. Never guess the URL.
5. **X's own update feed does not exist yet** — update check is disabled by design; wiring GitHub releases (`mazharmnzoor4227-beep/x`) is a later job.
6. **§H partials worth a follow-up commit (all small):** "New" label on threadless bot rows; bot-chat subtitle thread age / "New thread"; BashCard colours to spec tokens + live last-3-lines tail; ThinkingRow "Reading …" text; Settings providers Connect button text colour (`onAccent`, not white); Bot Store tap-to-detail (or amend §7.2 to the inline-card design that was actually built); Computer projects list (or amend §7.6); sheet titles 17sp; Feed empty-state wording; chat/bot title 16sp; Storage/Files §7.5 line-check.
7. **Spec-internal conflict to record:** §7.5's Settings regrouping vs §3.6's no-reorder rule — the build followed §3.6. If the regrouping is ever wanted, it is a separate job, not a defect in this build.

## 6. Go / no-go

**GO for push to CI.** No FAILs; all six demands and addenda A–E, G verify against the code; F is provisionally built pending voice research; H is 14 PASS / 8 minor PARTIALs / 0 FAILs. The push's purpose is to get the compile proof (CI) that this sandbox cannot provide — then the user's phone test decides the design, as always.
