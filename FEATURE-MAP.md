# FEATURE-MAP — 19 Grok screens → donor Android capability

Design reference (DO NOT MODIFY): `~/workspace/grok-workspace`
- `src/components/axion-studio.tsx` (1,140 lines, 19 `ScreenId`s + splash, dock, composer, menu drawer)
- `src/components/settings-screen.tsx` (Settings + 35 sub-pages)
- `src/styles.css` (Sand tokens), `public/mascot.glb` (fluffy 3D mascot)

Donor brain: this tree, copied from `~/workspace/axion-roadmap/work/axion-app`
branch `axion-ui-phase2`, commit `57003ac`, v0.9.7 / code 40,
package `com.mazharansari.x`, native Kotlin/Compose, 193 main Kotlin files.

**Important:** the Grok reference is a screen-review MOCK, not a working app.
Most screens are static/hardcoded (canned keyword answers in chat, hardcoded
lists, demo data). "Exists" below means the donor has the REAL capability
behind the screen — the mock's data is not the feature.

Status key: ✅ Exists (real, working) · 🟡 Partial (capability exists, screen
not unified / needs port) · ❌ Missing (no donor equivalent — new build).

## Summary

| Status | Count | Screens |
|---|---|---|
| ✅ Exists | 10 | thinking, chat, chats, approval, menu, memory, skills, connections, mcp, settings |
| 🟡 Partial | 6 | mark, code, computer, library, activity, cli |
| ❌ Missing | 3 | feed, ideas, goals |
| **Total** | **19** | |

## The 19 screens

### 1. mark — 🟡 Partial
Grok: full-screen `AgentMark` (240px, drag-to-turn, eyes follow), Idle/Thinking
toggle, "Axion" wordmark. Mascot is the 3D `mascot.glb`.
Donor: `ui/settings/ThinkingOrb.kt` (2D thinking orb), `ui/bots/BotLogoView.kt`
(bot logos). No 3D mascot, no drag-to-turn.
Gap: GLB mascot renderer decision (Filament / SceneView / static fallback);
port `agent-mark.tsx` behaviour (idle vs thinking states) onto ThinkingOrb.

### 2. thinking — ✅ Exists
Grok: chat frame, thinking dots + "Reading checkout.ts" status line.
Donor: thinking state in `ui/ChatViewModel.kt`, rendered by
`ui/chat/MessageViews.kt`; `ui/settings/ThinkingOrb.kt`.
Gap: only polish — live step text ("Reading …") already flows from the agent.

### 3. chat — ✅ Exists
Grok: `ChatFrame` header (menu/back, AgentMark 64, Axion + status, Invite),
user bubble (sky) / agent bubble, `Composer` (+ attach sheet: Photo/Video/File,
mic via Web Speech API, send/stop), keyword canned answers.
Donor: `ui/chat/ChatList.kt`, `Composer.kt`, `MessageViews.kt`, `MessageText.kt`,
`ui/ChatViewModel.kt`, `voice/VoiceRecognizer.kt` (dictation),
`ui/chat/ReadAloudBar.kt` (read-aloud), `ui/chat/Sheets.kt`.
Gap: attach sheet exists in donor composer — verify parity; Invite is
mock-only in Grok ("Share this chat when the account is connected").

### 4. chats — ✅ Exists
Grok: thread list (Main / Launch copy / Supplier list), tap to open.
Donor: `ui/chat/ChatList.kt` (Phase 2 WhatsApp-style chat list),
`data/ChatDrafts.kt`, conversation store in `data/`.
Gap: none structural — re-skin to Sand + pin/dot affordances from the mock.

### 5. approval — ✅ Exists
Grok: "Sentinel" card, command `npm test -- checkout`, Allow once / This chat /
Decline, "Shell has no network. You can revoke this later."
Donor: `agent/ApprovalGate.kt`, `data/Approvals.kt`,
`ui/settings/ApprovalsScreen.kt` (Ask / This chat / Always).
Gap: none structural — the Grok three-button card maps 1:1 onto the donor gate.

### 6. code — 🟡 Partial
Grok: diff view (`src/checkout.ts`, -/+ lines), "12 passed · 0 failed" pill,
"Tests pass. Nothing is committed."
Donor: `build/BuildHub.kt`, `build/ProjectFiles.kt`, `ui/home/BuildPane.kt`,
`ui/chat/HtmlPreview.kt`, `ui/chat/PlanCard.kt`, `ui/chat/StepDetail.kt`;
agent file tools + test running exist, but no dedicated diff-review screen.
Gap: build a diff view from the agent's file-edit events; wire test-result pill
to real test runs.

### 7. computer — 🟡 Partial
Grok: one screen unifying Browser card (Take control) + Files list
(`src/checkout.ts`, `notes/bug.md`) + shell terminal card (`npm test`).
Donor (all three exist, but separate): `browser/ObscuraEngine.kt`,
`browser/BrowserController.kt`, `ui/browser/` (real headless browser);
`linux/LinuxRuntime.kt`, `linux/ShellSession.kt`, `ui/settings/TerminalScreen.kt`
(PRoot terminal); `ui/home/FileActions.kt`, `build/ProjectFiles.kt`.
Gap: unify into one "Computer" screen; Grok's "Workspace on this phone, not
your laptop" matches the donor's on-phone PRoot workspace.

### 8. feed — ❌ Missing
Grok: "While you were out · 14:02" card (tests green, diff left in chat) +
"Needs you" card (one command waiting → Review approval). Dock tab.
Donor: NO feed screen. Closest is `work/Digest.kt` (`DigestComposer` — a
scheduled-run digest, not a UI feed) and `data/TaskRuns.kt` / `data/JobRecords.kt`.
Gap: NEW — Feed tab (dock: Chat / Feed / Ideas / Goals / Library) fed by task
runs + pending approvals; needs a store + screen from scratch.

### 9. ideas — ❌ Missing
Grok: grouped idea rows (Store: "Rewrite the coupon path…"; You: "Turn the
Kyoto goal into a 30-minute daily plan"). Dock tab.
Donor: NO ideas store or screen (only incidental mentions in agent prompts).
Gap: NEW — Ideas tab + store (capture from chat, group by area, tap to start).

### 10. goals — ❌ Missing
Grok: progress rings (conic-gradient %): "Mobile checkout — 2 of 4 steps, 50%",
"Daily Japanese — Check-in · evenings, 30%". Dock tab.
Donor: NO goals store or screen.
Gap: NEW — Goals tab + store (steps, progress ring UI, check-ins). Note the
user already tracks goals in Muse; keep scope to in-app goals first.

### 11. library — 🟡 Partial
Grok: file list (`checkout.ts` → opens code screen; "Restock note" page).
Dock tab.
Donor: `build/ProjectFiles.kt`, `ui/home/FileActions.kt`, `data/Projects.kt`,
`ui/chat/PdfViewer.kt` — file access exists, but no Library tab.
Gap: NEW tab screen over the existing file/project stores.

### 12. menu — ✅ Exists
Grok: `MenuSheet` drawer (84% width): 17-entry chat history with pins + unread
dots, search field, Settings button, New chat. (`MenuScreen` itself is null —
the drawer IS the menu.)
Donor: drawer + chat list in `ui/AppRoot.kt`, `ui/home/HomeScreen.kt`
(Phase 2: WhatsApp-style chat list, Bots in hamburger menu).
Gap: re-skin to Sand; add pin/dot affordances and in-drawer search if missing.

### 13. activity — 🟡 Partial
Grok: timestamped lines (14:02 "Tests passed. Did not commit."; 13:58 "Read
src/checkout.ts"; 13:57 "Opened the computer").
Donor: `data/TaskRuns.kt`, `data/JobRecords.kt`, `ui/settings/ToolLog.kt`
(tool log). Activity data exists; no dedicated Activity screen.
Gap: NEW screen over TaskRuns + ToolLog.

### 14. memory — ✅ Exists
Grok: editable notes (Store: "Sells Korean skincare…"; Tone: "Short. No
filler. Ask before sending anything."). Hint: "Axion writes these. You can
edit them."
Donor: `data/MemoryStore.kt`, `ui/settings/MemoryScreen.kt`.
Gap: none structural.

### 15. skills — ✅ Exists
Grok: skill cards ("Checkout review", "Restock note" with one-line contracts).
Settings claims "56 enabled" — that number is MOCK data, not real.
Donor: `skills/SkillHub.kt`, `skills/SkillRepo.kt`, `agent/SkillTools.kt`,
**18 skill packs** in `app/src/main/assets/skills/` (incl. 5 Mika + Ponytail +
local-* packs); `ui/settings/ToolsLibraryScreen.kt`.
Gap: none structural — but the mock's "56" must NOT be copied; show the real
count. Skills screen should list the real 18 packs.

### 16. connections — ✅ Exists
Grok: toggles — GitHub Connected, Calendar Off, Mail Off.
Donor: `data/Connectors.kt`, `ui/settings/ConnectorsScreen.kt`,
`llm/Providers.kt` (15 providers, paste-a-key + auto-detect + failover),
`data/KeyVault.kt`.
Gap: none structural.

### 17. mcp — ✅ Exists
Grok: remote server list (GitHub on, Calendar off) + Add server form
(name + `https://` URL). Hint: "Remote servers only. A local process can't
start inside the phone."
Donor: `mcp/Mcp.kt` (remote MCP client, Streamable HTTP),
`agent/ToolCatalog.kt` (MCP tools).
Gap: needs the Add-server UI screen; the remote-only rule already matches the
donor (no local MCP process on phone).

### 18. cli — 🟡 Partial
Grok: Antigravity (`agy`) pair screen — "Not paired", pair-code input, "The
phone does not run the CLI."
Donor: `antigravity/AgyAccounts.kt`, `antigravity/AgyEngine.kt`,
`antigravity/AgyLogin.kt`, `antigravity/AgyFailover.kt` (2 accounts paired in
the mock's settings; real pairing lives here). No pair-code screen as such.
Gap: build the pair-code UI on top of the existing Antigravity pairing flow.

### 19. settings — ✅ Exists
Grok (`settings-screen.tsx`): token-usage bar (10%) + Upgrade; groups —
Connectors / Wallet / Secure credentials store / Permissions / Messaging
channels / Devices; Notifications / Appearance (System/Light/Dark) / App lock /
Default assistant / Add to home screen; Redeem referral code; Data controls /
Report an issue / Help & support / App updates / Legal info; Axion group —
Customize Axion / Skills / GitHub / Memory / Approvals / Schedules (1 active) /
Tools library (13 of 13) / Browser / Terminal / Developer tools / Turbo
(server) / Antigravity (2 accounts) / Quota / Status page; Account (name
"mazhar"); Log out; footer "Axion 0.9.7 / by Mazhar Ansari". 35 sub-pages.
Donor: `ui/settings/` — `SettingsScreens.kt`, `ApprovalsScreen.kt`,
`ConnectorsScreen.kt`, `GitHubScreen.kt`, `MemoryScreen.kt`,
`SchedulesScreen.kt`, `TelegramScreen.kt`, `TerminalScreen.kt`,
`ToolsLibraryScreen.kt`, `StorageScreen.kt`, `OwnModelScreens.kt`,
`V04Screens.kt`; plus `quota/QuotaRepo.kt` + `QuotaMath.kt`,
`update/AppUpdater.kt`, `status/StatusServer.kt`, `telegram/TelegramBot.kt`,
`security/SecureUi.kt` (app lock), `data/KeyVault.kt` (credentials store).
Gap: mostly re-skin + fill the few mock-only rows (Wallet, Referral, Upgrade
checkout — Grok itself says "This phone build has no checkout").

## Shared chrome (from axion-studio.tsx, applies to all screens)

| Element | Grok | Donor |
|---|---|---|
| Splash | AgentMark 196 + "Axion", 700 ms auto-advance, tap to skip | No splash screen — new |
| Dock (pill, 5 tabs) | Chat / Feed / Ideas / Goals / Library | No bottom dock — new nav (Feed/Ideas/Goals/Library are the 3 missing tabs) |
| Composer | + attach sheet, mic, send/stop | `ui/chat/Composer.kt` — exists |
| ChatFrame header | Menu/back, AgentMark 64, status line, Invite | `ui/AppRoot.kt` + `ui/home/HomeScreen.kt` — exists, re-skin |
| Subpage / TabPage shells | Back + Fraunces title, paper cards | `ui/Section.kt`, `ui/components/` — exists, re-skin |

## Build order suggestion (not a commitment)

1. Re-skin existing screens to Sand (chat, chats, settings, approval, memory,
   skills, connections, mcp, menu) — theme switch-on.
2. Unify partials (computer, code diff, library tab, activity screen, cli pair).
3. Build the three missing tabs (feed, ideas, goals) + dock nav.
4. Mascot + splash last (needs the GLB renderer decision).
