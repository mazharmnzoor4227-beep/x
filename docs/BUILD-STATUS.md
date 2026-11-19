# BUILD-STATUS — X v1.0 (first build, 2026-11-17)

**App:** "X" (TEMPORARY name — user will change it later). Package
`com.mazharansari.x`, versionName **1.0**, versionCode **1**.
**Donor:** Axion v0.9.7 / code 40 (`axion-ui-phase2`, commit `57003ac`).
**Status: READY FOR PUSH + CI. NOT compiled locally** — Gradle hangs in this
sandbox, so no compile or test run was possible here. First verification is
GitHub Actions CI after the push (see "First CI build" below).

**Counts (re-verified 2026-11-19, after the UI rebuild):** 194 main Kotlin
files (`find app/src/main/java -name '*.kt'`) · **23 bot seeds**
(`BotCatalog.kt`: 23 `BotDefinition(`) · **30 skill packs** (audit count —
the old "56" was wrong) · 40 routes (`Route.kt`: 39 data objects +
`CodeDiff`). The 2026-11-17 counts above (206 files / 41 routes) were stale;
see `docs/X-REBUILD-2026-11-19.md` for the rebuild. The rest of this file
describes the 2026-11-17 first build unless marked otherwise.

## 1. What was built in this session

### Theme — Sand is DEAD (superseded 2026-11-19)
- **2026-11-19 rebuild:** the Sand theme and the DotsTheme are deleted.
  `ui/theme/DotsTheme.kt` removed; `ui/theme/Theme.kt` rewritten as the X
  theme (light `#FFFFFF`/`#F7F7F8`/`#171717`, dark `#171717`/`#212121`/`#ECECEC`,
  accent indigo `#4F46E5` light / `#818CF8` dark; no green anywhere — verified
  by grep). `ui/AppRoot.kt` now uses `XTheme` / `XLook`. `AxionTheme()` kept
  intact in `Theme.kt` as fallback (the test suite wraps screens in it).
  The Sand paragraph below is kept as history only — do not re-apply it.
- (2026-11-17, history) `ui/AppRoot.kt`: `AxionTheme(...)` → `SandTheme(look, textScale)` (string
  setting "system"|"dark"|"light" mapped to `SandLook`).
- Appearance screen: System/Dark/Light tiles already write the same string
  setting and use palette colours — no change needed.
- Hardcoded-accent sweep: **0 replacements needed** — no hardcoded brand
  accent (`#6C63FF` / `#5A50E6` / `#3B82F6`) exists in `ui/` outside the
  palette definitions in `Theme.kt`.
- Header-gap audit: **no gaps found** — shared `ScreenHeader` and all custom
  headers use `statusBarsPadding()`.

### Agents — 24-agent roster, 23 seeds in the store
`data/BotCatalog.kt` now holds 23 `BotDefinition` seeds (6 donor + 17 new).
All personas are Axion's own words (behavior patterns only), every persona
carries the verify-before-deliver line, `suggestedProvider` is only Gemini /
Groq / OpenRouter / xAI Grok / null (never OpenAI, never Antigravity).

| # | Agent | Status | Note |
|---|-------|--------|------|
| 1 | Axion (main loop) | ✅ Exists | The orchestrator itself |
| 2 | Coworker | ✅ Exists | store-coworker seed (OpenDots pattern) |
| 3 | Researcher | ✅ Exists | store-researcher seed (Hermes pattern) |
| 4 | Coder | ✅ Exists | store-coder seed (Ponytail + Mika) |
| 5 | Reviewer | ✅ Exists | store-reviewer seed (Koog pattern) |
| 6 | Learner | ✅ Exists | store-learner seed (Hermes pattern) |
| 7 | Scheduler | ✅ Exists | store-scheduler seed (Hermes pattern) |
| 8 | Tester | 🟡 Seed written | Swarm TESTER role already existed |
| 9 | Planner | 🟡 Seed written | Planning code already existed |
| 10 | Explorer | 🟡 Seed written | File tools already existed |
| 11 | Computer | 🟡 Seed written | Obscura works; no image/computer skill pack exists, so skill list is empty (nothing invented) |
| 12 | Builder | 🟡 Seed written | Antigravity CLI only; provider null by rule |
| 13 | Doc Maker | 🆕 Seed written | PDF tool exists; **no docs workspace / Pages store yet** |
| 14 | File Clerk | 🟡 Seed written | File/PDF/ZIP tools already existed |
| 15 | Image Maker | 🟡 Seed written | `generate_image` tool already existed; skill list empty (no image skill pack) |
| 16 | Video Editor | 🟡 Seed written | ffmpeg tools exist; **needs Linux Developer tools installed** |
| 17 | Voice | 🟡 Seed written | Voice + live calls exist; toolGroups empty (no voice tool group in ToolCatalog) |
| 18 | Messenger | 🟡 Seed written | **Honest limit in persona:** drafts only; sends email via connected Gmail only — **cannot send WhatsApp/WeChat** |
| 19 | Shopkeeper | 🆕 Seed written | **No shop API wired yet** — persona says so; works from web research until then |
| 20 | Money | 🆕 Seed written | Uses existing verify-before-recommending skill |
| 21 | Teacher | 🟡 Seed written | Guided-setup persona (definition only) |
| 22 | Memory Keeper | 🟡 Seed written | Memory store already existed |
| 23 | Guard (Sentinel) | 🟡 Seed written | Approval gate + quota already existed; toolGroups empty (internal machinery) |
| 24 | Idea Catcher | 🆕 Seed written | **2026-11-19:** Ideas + Goals are now real drawer destinations wired to `data/IdeasStore.kt` / `data/GoalsStore.kt` (see `docs/X-REBUILD-2026-11-19.md`). |

Swarm caps (unchanged, honest limits): max 4 parallel workers, 8 steps each,
depth 2, 6 workers per run. Workers never do GitHub / posting / installing /
payments. BYOK: one universal key box, auto-detect, encrypted storage; 15
providers + on-device LFM3 (chat-only); **OpenAI never**.

### Skills — 18 → 30 packs
`app/src/main/assets/skills/` — 30 dirs with `SKILL.md`, zero duplicate
normalized names, all valid UTF-8. 12 added verbatim from
`~/workspace/hermes-skills-port/` (MIT Hermes; attribution already in root
`NOTICE.md` + `HERMES-NOTICE.txt`, bundled list updated):

agent-building · banner-design · content-transcribe · deep-research ·
delivery · documents · info-research · local-android-dev ·
local-app-store-design · local-frontend-design · local-frontend-seo ·
local-game-dev · local-header-footer-design · local-playwright-skill ·
local-pre-delivery-verification-gate · local-shopify-development ·
local-verified-status-claims · local_external_builder_brief ·
local_reference_pattern_research_gate · local_verify_before_recommending ·
meta-astryx-design · mika-content-transcribe (front-matter name
`content-transcribe`) · mika-documents (`documents`) · mika-info-research
(`info-research`) · mika-messaging (`messaging`) · mika-skill-authoring
(`skill-authoring`) · pdf · ponytail-minimal-code ·
reverse-engineering-android · roman-urdu · token-efficiency · video ·
website-audit.

Notes: `SkillRepo.kt` `BUILT_IN` fallback list extended to all 30 dir names
(runtime discovery uses `assets.list()` first, so this is belt-and-braces).
Skipped (retired stubs, superseded): `local-ui-ux-pro-max`,
`local-tailwind-design-system`, `local-android-ui-journey-testing`,
`local-design-taste-frontend`, `local-web-design-guidelines`.
**Open item:** 4 packs reference sibling support files that were NOT copied
(`local-frontend-seo`, `local-shopify-development`, `local-game-dev`,
`local-playwright-skill` reference `references/` / `scripts/` / `lib/`) —
bundle those support files in a later pass if the skills need them.

### Screens — 19/19 Grok screens now have a real screen
New stores: `data/IdeasStore.kt`, `data/GoalsStore.kt` (JSON-file pattern from
`MemoryStore.kt`, `Graph` lazy stores in `AxionApp.kt`). New screens in
`ui/home/`: `FeedScreen`, `IdeasScreen`, `GoalsScreen`, `LibraryScreen`,
`ActivityScreen`, `ComputerScreen`, `MarkScreen`, `CodeDiffScreen`,
`CliPairScreen`, shared `HomeTabCommon.kt`. New routes in `ui/Route.kt` +
branches in `ui/AppRoot.kt`: Feed, Ideas, Goals, Library, Activity, Computer,
Mark, CodeDiff, CliPair (9). Bottom dock pill added to `HomeScreen.kt`
(Chat / Feed / Ideas / Goals / Library; Chat keeps Ask|Build; other tabs
full-bleed with back-to-Chat; content ends 84dp above the dock; dock has
`imePadding`).

| Screen | Status | How it works |
|---|---|---|
| mark | 🟡 Built (2D) | Large 240dp ThinkingOrb, Idle/Thinking toggle. No 3D/GLB renderer — that decision is still open |
| thinking | ✅ Exists | Live step text already flowed from the agent |
| chat | ✅ Exists | ChatPane + Composer + attach sheet (existing) |
| chats | ✅ Exists | WhatsApp-style list (existing) |
| approval | ✅ Exists | ApprovalGate + ApprovalsScreen (existing) |
| code | 🟡 Built | **CodeDiffScreen**: real `ChangeGate` before/after + `PatchEngine.diff`, real −/+ lines and counts, Approve/Reject wired. **Gap: no test-count pill** — no persisted per-test pass/fail source exists (`ProjectVerifier` returns only a transient problems list). Pill shows real diff counts; no test numbers invented |
| computer | 🟡 Built | Three real cards: Browser → Route.Browser, Terminal → Route.Terminal (real Linux-installed subtitle), Files → Route.Files (real file count) |
| feed | 🆕 Built | Derived only, no new store: live runs (`RunRegistry`), pending approval (`vm.pendingApproval`), recent TaskRuns + JobRecords. Real empty state |
| ideas | 🆕 Built | IdeasStore, add + area chips (Store/You), toggle done, delete, grouped. Starts empty |
| goals | 🆕 Built | GoalsStore, add goal with steps (one per line), check off steps, progress ring from real step counts, delete. Starts empty |
| library | 🟡 Built | Build projects (tap → Build workspace) + workspace files via existing viewers/download |
| menu | ✅ Exists | Drawer + chat list (existing) |
| activity | 🟡 Built | Timestamped rows from live runs + TaskRuns + JobRecords, tappable. Note: `ToolLog.kt` is only the Developer-tools *install* log, not a general event feed — no tool-call stream was invented |
| memory | ✅ Exists | MemoryStore + MemoryScreen (existing) |
| skills | ✅ Exists | SkillHub + ToolsLibraryScreen (existing); now lists 30 real packs |
| connections | ✅ Exists | ConnectorsScreen + BYOK key box (existing) |
| mcp | ✅ Exists | Remote MCP client (existing) |
| cli | 🟡 Built | **CliPairScreen**: real `AgyLogin` state machine — paired accounts, Start sign-in → Google URL (open/copy) → code field → `submitCode` → SignedIn/Error, 15-min timeout note, Linux+agy prerequisite routes to Developer tools |
| settings | ✅ Exists | SettingsScreens (existing); Wallet/Referral/Upgrade rows are mock-only in the Grok reference and stay unimplemented (no checkout in this build) |

All new screens: English text only, header below the status bar with a clear
gap, aligned boxes, no cartoon badges, real empty states, no mock/demo data.

### CI fixes made in this session (so the first build can run)
Two certain first-build failures were found and fixed (both documented,
revertible):
1. **google-services plugin removed** (root `build.gradle.kts` +
   `app/build.gradle.kts`): the plugin was applied but `app/google-services.json`
   was deliberately removed, and **zero Kotlin files use Firebase/GMS** — the
   plugin would only fail the build. Comment left in both files: re-add with
   the X project's `google-services.json` when the user creates an X Firebase
   project. Do NOT reuse donor `axion-d5461` config.
2. **Workflows use `setup-gradle` instead of `./gradlew`** (`build.yml`,
   `verify.yml`, `verify-targeted.yml`): `gradle-wrapper.jar` is binary and
   cannot travel via snapshot push, so `./gradlew` would fail immediately.
   Now `gradle/actions/setup-gradle@v3` (Gradle 8.11.1, same as
   `gradle-wrapper.properties`) + plain `gradle` commands. Also: workflow
   names rebranded to X v1.0, push branches set to `[main]` (donor branch
   names would never trigger), artifact renamed to `x-release-unsigned`.
   Revert to `./gradlew` once the wrapper jar is in the repo, if wanted.

## 2. Static verification done (no Gradle in sandbox)
- `com.mazharansari.axion`: **0 occurrences** in any `*.kt` / `*.kts` / `*.xml` /
  `*.pro` file (only intentional history notes in `.md` docs).
- Brace/paren/bracket balance = 0 in all 17 touched Kotlin files + SkillRepo
  after the BUILT_IN edit.
- 9 new routes exist in `Route.kt` AND have `AppRoot` branches (1:1).
- Dock wires all 5 tabs; `Route` imported in `HomeScreen.kt`.
- All new screens reference only symbols that exist (spot-checked:
  `vm.pendingApproval`, `vm.openProject`, `vm.approveReview`,
  `PatchEngine.diff`, `AgyLogin.submitCode`, `Orb`); no `R.*` references in
  new screens (palette-driven, no missing resources).
- `SandTheme` called in `AppRoot.kt` (line 27) with the `SandLook` import;
  zero `AxionTheme(` calls remain outside `Theme.kt`'s own definition.
- 23 bot seeds: ids unique, all `toolGroups` are valid `ToolCatalog` ids, all
  logos are valid builtins, names ≤ 40 chars, English only.
- **Could NOT verify:** compilation, unit tests, runtime behaviour, layout on
  a real phone, the first CI build. That is all downstream of the push.

## 3. Still missing / honest gaps
- **Compile + tests + phone run** — first real check is CI (below).
- **15 binary files** (same list as before): 2 TTF fonts, `axion_mark.png`,
  `axion_splash.png`, 10 mipmap launcher PNGs, `gradle-wrapper.jar`. They
  cannot go via snapshot push (non-UTF-8). The build no longer *needs* the
  jar (setup-gradle), but the fonts + icons ARE needed: without them the
  build fails on missing `res/font` / `res/mipmap` resources. They must reach
  the repo before the first green build — separate route (browser web upload
  to github.com, or a CI step that fetches the OFL fonts + generates icons;
  see push steps).
- **Firebase project for X** — new project + `google-services.json` needed
  only if/when Firebase features are wanted; the app builds without it now.
- **Release signing** — no keystore in this tree (donor reads
  `../keys/keystore.properties`). First CI artifact is **unsigned**; release
  signing must be set up for X before any signed build.
- **Fraunces + Outfit fonts** — not bundled (Google Sans Flex/Code stay).
- **Mascot GLB renderer** — undecided (Filament / SceneView / static
  fallback); Mark screen uses the 2D ThinkingOrb.
- **CodeDiff test pill** — needs `ProjectVerifier`/`BuildLoop` to persist
  verify outcomes first (new store, out of scope).
- **Doc Maker workspace (Spaces/Pages)** — biggest missing Dots piece; seed
  exists, capability does not.
- **Shopkeeper APIs** — no shop API wired; bot works from web research only.
- **Upload gaps** (unchanged from donor): Gallery is image-only (video only
  via Files picker), no audio attach in composer, no batch pick / progress
  bar, no cloud-drive pickers, no per-bot isolated workspaces.
- **OpenFuse identity unconfirmed** by the user (research says OpenMuse →
  OpenFuse is almost certainly the "Open Muse" he meant). Merge behaviour was
  used, not its code; no Intelligence-key dependency was built in.
- **Name "X" is temporary** (user will change it later; X is Twitter/X Corp's
  trademark — change before any public launch).

## 4. Push steps for the parent
Repo already exists, verified empty: **https://github.com/mazharmnzoor4227-beep/x**
(public, no README/gitignore/license; `list_commits` → "409 Git Repository is
empty"). Manifests were rebuilt fresh after all code changes (they list paths
only; content is read at push time):
- Snapshot A: `/tmp/x-manifest-A.paths` — 507 files, 2.97 MiB (everything
  except `third_party/`).
- Snapshot B: `/tmp/x-manifest-B.paths` — 813 files, 12.68 MiB (`third_party/`).
- Both within limits (≤ 2,000 files, ≤ 16 MiB UTF-8 each); all files valid UTF-8.

Per the snapshot-push guide, immediately before the first upload recheck the
repo is still empty, then:
```text
github push-files --owner mazharmnzoor4227-beep --repo x --branch main \
  --message "X v1.0 — first build: Sand theme, 24-agent roster, 30 skills, 19 screens" \
  --source-dir ~/workspace/axion-new-series --paths-file /tmp/x-manifest-A.paths
github push-files --owner mazharmnzoor4227-beep --repo x --branch main \
  --message "X v1.0 — third_party (llama.cpp)" \
  --source-dir ~/workspace/axion-new-series --paths-file /tmp/x-manifest-B.paths
```
Then record the confirmed commit SHAs in the adjacent sync record
(`~/workspace/axion-new-series.muse-github-snapshot-sync.json`) per the guide.
Then get the 15 binaries in (browser web upload to the same paths is the
straightforward route; the APK will not build green until the fonts + icons
land). Then watch CI.

## 5. First CI build — what to expect
- `build.yml` (Release Build X v1.0) runs on push to `main`: checkout →
  Temurin JDK 17 → setup-gradle 8.11.1 → SDK packages (android-35,
  build-tools 35.0.0, cmake 3.22.1, ndk 26.3.11579264) → `gradle
  assembleRelease` → artifact **`x-release-unsigned`** (unsigned APK).
- `verify.yml` + `verify-targeted.yml` run the unit tests on the same push.
- Known pre-existing failures (donor): EnglishUiTest, V03ScreensTest,
  V053AuthStorageTest (7 failed of 706 in the donor's last check-4); the full
  suite hangs pre-existing (V053), so it cannot gate a release build. Judge a
  red run by the job summary, not the count alone.
- First build is slow (llama.cpp native CMake build; 120-minute timeout).
  If it goes red, read the failure from the log — do not guess at the cause.

## 6. License notes (unchanged boundaries, all honoured)
Andee: zero code. Locus AGPL / nanoMuse GPL: behaviour only, no code.
diggerhq/openmuse: nothing (no licence, dead). Jarvis: architecture reference
only (licence unverified). MIT (OpenDots, OpenFuse, OpenCode, OpenClaw,
Hermes) + Apache-2.0 (Mika, Koog): OK with attribution — root `NOTICE.md` +
`HERMES-NOTICE.txt` / `MIKA-NOTICE.txt` kept, Hermes bundled list updated.
No OpenAI anywhere. No demo/sample content anywhere (NO-DEMO RULE).
