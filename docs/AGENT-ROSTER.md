# AGENT ROSTER — X Bot + 50 specialist agents

Navigation restructure, 2026-11-20. This doc replaces the 2026-11-17
24-agent roster (7 existed, 12 partial, 5 new). The model changed:

- The user talks to **exactly ONE bot: X Bot** (`data/BotStore.kt:
  xBotDefinition`, id `x-bot`, seeded first by `ensureXBot()`). The Bots
  section shows X Bot only.
- The 50 agents below are **specialists X Bot spawns**, not bots the user
  chats with. A big job fans out through `subagent_swarm`; each part is
  tagged with the specialist whose work it is (`agent/XBotSpawn`), the
  workers run under the swarm safety caps, X Bot verifies and aggregates
  every result, and answers the user FROM ITSELF.

User order (2026-11-17, kept): **at least 20 specialist agents, more if
possible** — each an expert in its own work. The user plugs in their own
provider (BYOK) and the app does every task properly. Antigravity attaches
via CLI.

## How a "bot" works here

A bot is a **definition bundle, not code** (`data/BotStore.kt`): name +
description + persona + skills + tool allow-list + suggested provider,
stored in `bots.json` and run through **X's own agent loop**. Installing a
bot never executes foreign code. X Bot is the parent for every run; there
is no hardcoded CEO class — the name + parent/child relationship is what
makes a bot the parent for its run. Every bot must verify before it
delivers: never present a result as done until it is checked.

Swarm caps (honest limits, `agent/Subagents.kt` + `agent/Swarm.kt` —
UNCHANGED by the restructure): max **4 parallel workers** per fan-out,
**8 steps per worker**, recursion depth **2**, **6 workers per parent run**,
**2 spawns per worker**. Workers only touch their role's allow-list — **no
GitHub tools, no posting, no installing, no payments, ever** — and never
ask for approvals; the parent approves the whole swarm once
(`agent/ApprovalGate.kt`). A worker failure never kills the run.

Status key: ✅ Wired (seeded in `data/BotCatalog.kt`, backed by a real
tool group / bundled skill / named engine — see the Backing column).

## 1. X Bot — the orchestrator

| Agent | Expert domain | Provider | Tools / skills it uses | Status |
|-------|---------------|----------|------------------------|--------|
| **X Bot** | Main bot + orchestrator. General chat, routes work, spawns specialist sub-bots, merges + verifies all results, talks to the user — the ONLY bot the user ever talks to | Auto failover (Gemini → Grok → OpenRouter…) | All tool groups; `subagent_swarm`; `agent/Agent.kt`, `agent/Swarm.kt`, `agent/XBotSpawn` | ✅ Wired — `xBotDefinition()` in `data/BotStore.kt` |

## 2. The roster — 50 specialists

All 50 live in `data/BotCatalog.kt` (`seeds`). 23 are donor-pattern seeds
(verified MIT/Apache sources, see `third_party/NOTICE-bot-store.md`); 27
are X built-in (`sourceName = "X built-in"`, no URL — never a fabricated
link). Every `skills` entry is a directory in
`app/src/main/assets/skills/` (30 packs bundled); every `toolGroups`
entry is a real `ToolCatalog` group id.

| # | Agent | Expert domain | Backing (real, in-tree) |
|---|-------|---------------|--------------------------|
| 1 | **Coworker** | Persistent teammate, context across chats | web, research, files, documents, learning, schedule, skills |
| 2 | **Learner** | Learns from tasks, saves skills + memory notes | `agent-building` skill; skills, learning, files, web |
| 3 | **Researcher** | Deep research, cited reports | `deep-research` skill; web, research, files, documents |
| 4 | **Coder** | Minimum code that works, verified by reading back | `local-android-dev`, `ponytail-minimal-code`; files, commands, skills |
| 5 | **Reviewer** | Runs tests, reviews code, honest pass/fail | `local-pre-delivery-verification-gate`, `local-verified-status-claims`; files, commands |
| 6 | **Scheduler** | Scheduled tasks (once / daily / weekly) | schedule, learning, web, research; `work/ScheduleEngine.kt` |
| 7 | **Tester** | Test runner, exit codes verbatim | `local-pre-delivery-verification-gate`, `local-verified-status-claims`; files, commands; swarm TESTER role |
| 8 | **Planner** | Plan mode: steps + file list before code | `agent/Planning.kt`, `build/BuildLoop.kt`; files |
| 9 | **Explorer** | Maps a codebase before anyone changes it | `build/ProjectFiles.kt`; files, web |
| 10 | **Computer** | Drives the real browser (Obscura) | browser group; `browser/ObscuraEngine.kt`, `browser/BrowserController.kt` |
| 11 | **Builder** | Builds apps with Antigravity (Build engine) | `build/AgyBrain.kt`, `build/BuildLoop.kt`, `antigravity/AgyEngine.kt` |
| 12 | **Doc Writer** | Writes docs as workspace files, exports real PDFs | `mika-documents`, `pdf`; documents (`create_pdf`, `agent/PdfTool.kt`), files |
| 13 | **File Clerk** | Reads uploaded PDF / docs / ZIP, organises outputs | `mika-documents`, `pdf`, `mika-content-transcribe`; files |
| 14 | **Image Maker** | Image generation (posters, logos, product shots) | images group (`generate_image`); `agent/ImageGen.kt` |
| 15 | **Video Editor** | Video on the phone (trim, captions, 9:16, loudness) | `video` skill; video, files, commands; `agent/VideoTools.kt`, `agent/FfmpegCommands.kt` |
| 16 | **Voice** | Dictation, read-aloud, live voice calls | `mika-content-transcribe`; `voice/VoiceRecognizer.kt`, `voice/ReadAloudPlayer.kt`, `livecall/` |
| 17 | **Messenger** | Drafts replies; sends **email only** via connected Gmail | `mika-messaging`, `roman-urdu`; connectors group; `telegram/TelegramBot.kt` |
| 18 | **Shop Watcher** | Shops from their live public pages (no shop API wired — says so) | `local_verify_before_recommending`; web, research, files |
| 19 | **Money** | Prices + availability, Pakistan-checked | `deep-research`, `local_verify_before_recommending`; web, research |
| 20 | **Teacher** | Guided setup, one plain step at a time | `roman-urdu`; learning, files |
| 21 | **Memory Keeper** | Memory curation (clean, editable, searchable) | learning group; `data/MemoryStore.kt`, `agent/LearningTools.kt` |
| 22 | **Guard (Sentinel)** | Approvals + safety + quota watch | `local-verified-status-claims`; `agent/ApprovalGate.kt`, `data/Approvals.kt`, `quota/QuotaRepo.kt` |
| 23 | **Idea Catcher** | Ideas + goals (real Ideas/Goals tabs) | learning, schedule, files; `ui/workspace/WorkspaceScreens.kt` |
| 24 | **GitHub Coder** | Clone, edit, commit, push, PRs, CI status | `local-android-dev`; github, files, commands; `agent/GitHubTools.kt` |
| 25 | **PR Reviewer** | Reviews pull-request diffs (file, line, problem, fix) | github, files |
| 26 | **Terminal Runner** | Shell commands, persistent sessions, background jobs | commands group; `agent/ShellTools.kt` |
| 27 | **Script Writer** | Small Python/Node scripts, runs them, reports output | files, commands |
| 28 | **Data Analyst** | CSV/table analysis with shell Python | files, commands |
| 29 | **PDF Reader** | Reads long PDFs, summary + key points + quotes | `pdf`, `mika-documents`; files; `agent/PdfTool.kt` |
| 30 | **Note Taker** | Clean notes as workspace files, filed by topic | files, learning |
| 31 | **Summarizer** | Summarises chats, docs, long threads | learning (`session_search`), files; `agent/HistoryCompressor.kt` |
| 32 | **News Reader** | Latest news with sources, newest first | web group |
| 33 | **Price Checker** | Live prices + stock, Pakistan first | `local_verify_before_recommending`, `deep-research`; web, research |
| 34 | **Fact Checker** | Citation checks: cited, cross-checked, or unverified | `deep-research`, `local-verified-status-claims`; research, web; `agent/CitationCheck.kt` |
| 35 | **Email Drafter** | Drafts + sends email via connected Gmail only | `mika-messaging`; connectors group |
| 36 | **Digest Writer** | Daily digest: what ran, finished, still needs the user | schedule, learning; `work/Digest.kt` |
| 37 | **Transcriber** | Transcribes audio/video files to text | `mika-content-transcribe`; files |
| 38 | **Designer** | Astryx-first design (components, tokens, spacing) | `meta-astryx-design`, `local-frontend-design`; files |
| 39 | **Frontend Builder** | Web frontends (layout, type, colour, a11y) | `local-frontend-design`, `local-frontend-seo`; files, commands |
| 40 | **SEO Auditor** | SEO audits against the real pages | `local-frontend-seo`, `website-audit`; web, files |
| 41 | **Game Maker** | Casual arcade games → WebView APK | `local-game-dev`; files, commands |
| 42 | **Shopify Helper** | Shopify apps, extensions, themes | `local-shopify-development`; files, commands |
| 43 | **Playwright Tester** | Real-browser checks (clicks, geometry, rendering) | `local-playwright-skill`; files, commands |
| 44 | **Banner Designer** | Banners (mastered rules, real product shots) | `banner-design`; images, files |
| 45 | **APK Builder** | Builds + signs Android APKs in chat | `local-android-dev`; files, commands; `build/BuildLoop.kt`, `build/ProjectVerifier.kt`, `build/ReleaseSigning.kt` |
| 46 | **Website Auditor** | Website audits (loads, breaks, slow, fix-first) | `website-audit`; web, research |
| 47 | **Reverse Engineer** | Inspects APKs (assets, libs, manifest) — behaviour study only | `reverse-engineering-android`; files, commands |
| 48 | **Skill Smith** | Writes + improves skills (small, reviewed) | `mika-skill-authoring`, `agent-building`; skills group; `agent/SkillTools.kt` |
| 49 | **Token Saver** | More with fewer tokens (short prompts, tight plans) | `token-efficiency`; skills group |
| 50 | **Delivery Checker** | Checks work is delivered as the thing itself (link / APK) | `delivery`; files, web |

**Count: 50 specialists + X Bot = 51 agents — all ✅ wired.**

What changed vs the old roster: the 3 stub seeds are gone — "Doc
Maker" (no docs workspace) is rewritten as **Doc Writer** (writes files +
real `create_pdf`, no workspace claimed); "Shopkeeper" (no shop API) is
rewritten as **Shop Watcher** (live public pages, says so in its persona);
"Idea Catcher" is kept — its Ideas/Goals screens exist
(`ui/workspace/WorkspaceScreens.kt`), so it was never actually blocked.
Stale skill ids in older seeds were fixed (`documents` →
`mika-documents`, `content-transcribe` → `mika-content-transcribe`,
`messaging` → `mika-messaging`). The Grok mock's "56 skills enabled" is
**fake mock data — do not copy it**; the real count is 30 packs (§3).

The user can still create **their own bot definition** from code
(`BotDefinitionStore.create` — any name, description, persona, suggested
provider), but the UI no longer offers "New bot": the Bots section shows
X Bot only, and sub-bots are created by X Bot itself.

## 3. Skills — all 30 packs, and which agents use each

Bundled in the app at `app/src/main/assets/skills/` (30 packs + 3
notice/license files). `skills/SkillHub.kt` + `skills/SkillRepo.kt` load
them; `agent/SkillTools.kt` exposes `load_skill` / `skill_create` /
`skill_update` to the agent loop.

| # | Skill pack | What it teaches | Used by agents |
|---|------------|-----------------|----------------|
| 1 | agent-building | How to build/define agents + bots | Learner, X Bot, Skill Smith |
| 2 | banner-design | Banner rules | Banner Designer |
| 3 | deep-research | Multi-angle research, citations, two-source rule | Researcher, Money, Price Checker, Fact Checker |
| 4 | delivery | Deliver the thing itself (link / APK), never only code | X Bot, Delivery Checker |
| 5 | local-android-dev | Production Android dev (native + cross-platform) | Coder, Planner, Explorer, GitHub Coder, APK Builder |
| 6 | local-app-store-design | App-store / catalog design spec | (bundled; no roster agent claims it yet) |
| 7 | local-frontend-design | Web frontend design (layout, type, colour, a11y) | Designer, Frontend Builder |
| 8 | local-frontend-seo | Frontend SEO system | Frontend Builder, SEO Auditor |
| 9 | local-game-dev | Casual arcade games (Canvas → WebView APK) | Game Maker |
| 10 | local-header-footer-design | Header/footer design | (bundled; no roster agent claims it yet) |
| 11 | local-playwright-skill | Real-browser checks with Playwright | Playwright Tester |
| 12 | local-shopify-development | Shopify apps, extensions, themes | Shopify Helper |
| 13 | local-pre-delivery-verification-gate | Pre-delivery checks before claiming done | Reviewer, Tester |
| 14 | local-verified-status-claims | Report status only from a verifiable handle | Reviewer, Guard, Fact Checker |
| 15 | local_external_builder_brief | Brief another agent/session to build | (bundled; no roster agent claims it yet) |
| 16 | local_reference_pattern_research_gate | Research a pattern from its real source before building | Planner |
| 17 | local_verify_before_recommending | Verify the deciding fact before recommending | Researcher, Money, Shop Watcher, Price Checker |
| 18 | meta-astryx-design | Meta Astryx — primary design system | Designer |
| 19 | mika-content-transcribe | Transcribe audio/video content | Voice, File Clerk, Transcriber |
| 20 | mika-documents | Work with documents | Doc Writer, File Clerk, PDF Reader |
| 21 | mika-info-research | Info research (Mika) | Researcher |
| 22 | mika-messaging | Read chats/email, draft replies (draft-only for chat apps) | Messenger, Email Drafter |
| 23 | mika-skill-authoring | Write + improve skills | Skill Smith |
| 24 | pdf | PDF reading / reports | File Clerk, Doc Writer, PDF Reader |
| 25 | ponytail-minimal-code | Minimum code that works | Coder |
| 26 | reverse-engineering-android | Inspect Android APKs | Reverse Engineer |
| 27 | roman-urdu | Roman Urdu handling | Messenger, Teacher |
| 28 | token-efficiency | Do more with fewer tokens | X Bot, Token Saver |
| 29 | video | Video editing with ffmpeg | Video Editor |
| 30 | website-audit | Audit a website properly | SEO Auditor, Website Auditor |

**Pipeline, not yet in the app:** `~/workspace/hermes-skills-port/` holds
**29 more SKILL.md packs** (creative, research, software-development,
web — incl. Astryx design, frontend, game-dev, Shopify, Playwright)
ready to bundle into `assets/skills/` (MERGE-SOURCES §4). Skills install
from GitHub too (`install_skills_from_github` tool) and the agent can
write its own learned skills (`skill_create`) — reviewed before they are
published (OpenDots learning-container pattern).

## 4. How X Bot spawns + aggregates (the contract)

1. **Pick:** `agent/XBotSpawn.pickSubBot(brief)` — keyword rules over the
   brief (most specific first), falling back to the worker's engine role
   (CODER → Coder, TESTER → Tester, REVIEWER → Reviewer, RESEARCHER →
   Researcher). The tag rides on `WorkerTask.subBotId` / `subBotName`.
2. **Fan out:** `agent/SubagentOrchestrator.fanOut` (caps §0) + the swarm
   tool (`agent/Swarm.kt: SubagentSwarmTool`), whose plan prompt lists the
   full 50-name roster so the model names a specialist per part.
3. **Verify:** `agent/SwarmVerify.verify` checks every worker result
   (research facts need source URLs + pass `CitationCheck`; "Wrote
   `<path>`" facts are read back from the workspace; test facts need an
   exit code; reviews need the reviewed file to exist). ONE bounded fix
   attempt (at most 2 fix workers), then re-verify.
4. **Aggregate + answer from itself:** `agent/XBotSpawn.aggregate`
   groups every result by sub-bot (roster name + role), lists the parts no
   worker covered, and puts the verification mark ("Verified" /
   "Needs check") up front. The user only ever talks to X Bot.

## 5. Profile editing (name + icon, per bot)

Tapping any bot's profile opens profile editing (`ui/bots/
CreateBotSheet.kt: BotProfileSheet`): name + icon kind. Icon options:
**Monogram** (default, two initials on a muted tile), **Static image**
(user-picked photo, copied into `bot-logos/` in app storage),
**3D** (five drawn 3D-style tiles — Bronze, Slate, Plum, Navy, Stone;
no .glb renderer is wired in-tree, so the drawn tile is the 3D look the
app can render), **Video** (user-picked clip, looping, muted, capped at
8 MB — video avatars never play sound). X Bot's choice persists in
`bots.json` (`BotDefinitionStore.rename` / `setLogo`); a sub-bot's choice
is an in-memory override in `agent/BotRegistry` (workers are ephemeral
runs — the override lasts for the process; the roster seed is the
fallback). Video playback uses the framework `VideoView` (no new
dependency); static images load with Coil (`coil-compose`, already a
dependency).

## 6. File upload — what exists today, and the gaps

What exists in the app:

- **Composer + menu** (`ui/chat/Composer.kt`, `ui/chat/Sheets.kt`): Camera ·
  Gallery · Files · Skills · Connectors. Camera = take a photo
  (`TakePicture`); Gallery = images only (`PickVisualMedia.ImageOnly`);
  Files = any file type via the system picker (`OpenDocument`).
- **Attachment model** (`data/Models.kt`): name + mime + size + path inside
  the private workspace — the agent sees the same path, so it can read what
  the user attached (`agent/FileTools.kt`: PDF, documents, ZIP).
- **In-chat viewing:** `ui/chat/PdfViewer.kt` (PDFs), `ui/chat/HtmlPreview.kt`
  (built HTML). Agent outputs are cards with Download / Open (`Artifact`).
- **Other pickers:** bot logo image (`GetContent`), voice file
  (`OpenDocument`), keystore file (`OpenDocument`).

Gaps for "upload anything" — stated honestly:

1. **No video upload button** — Gallery is image-only; a video file can only
   arrive via Files → system picker, not from the gallery row.
2. **No audio file upload in the composer** — mic is dictation / live call,
   not "attach an audio file". Audio only enters via Files picker or the
   transcribe skill.
3. **No batch/multi-file pick, no progress bar, no size cap shown** — one file
   at a time through the pickers; limits are not surfaced in the UI.
4. **No cloud drive pickers** — no Google Drive / Dropbox row; only
   on-device Camera / Gallery / Files.
5. **No "upload to a bot" scoping yet** — attachments land in the shared
   workspace; per-Dot isolated workspaces (OpenDots) are not implemented.

## 7. BYOK — one universal key box

Standing rule: **the user plugs in their own provider; the app does the work.**
No OpenAI — ever (user rule; bots never suggest it, even though an `openai`
entry exists in the provider table — it is excluded from bot suggestions).

- **One key box, auto-detect:** paste a key in Settings → Connectors and the
  app detects which provider it belongs to (`llm/Providers.kt` — Gemini keys,
  then format heuristics). Keys are stored encrypted (`data/KeyVault.kt`).
- **Providers in the table (15):** Google Gemini, Groq, Cerebras, Mistral,
  OpenRouter, Hugging Face, NVIDIA NIM, SambaNova, Cohere, Together AI,
  DeepSeek, xAI Grok, **Local model (LFM3-1.7B, on-device, no key, chat-only)**,
  My server — plus Antigravity, which is **not a key at all** (CLI login, §8).
- **Failover is automatic:** if the first provider fails, the next in the
  ranked chain takes over; a bot's suggested provider only **reorders** that
  chain, never pins it (`data/BotStore.kt: preferProvider`).
- **Per-bot provider:** each bot definition carries one suggested provider
  (e.g. Coder → Grok, Researcher → Gemini); null = Auto.

## 8. Antigravity attach — the CLI route (exact, as built in the donor)

Antigravity is **not a pasted API key**. It is Google's official `agy` CLI
(v1.1.27) running **inside the phone's own Linux (PRoot Ubuntu)**. The app
downloads the official binary itself — resumable, SHA-512 checked — and
installs it at `/root/.local/bin/agy` (+ `/usr/local/bin/agy` link)
(`linux/DevTools.kt`, `AgyCli`). It is installed automatically when the user
adds an Antigravity account; the phone never runs the CLI natively on Android.

Plain steps for the user:

1. **Linux download first** — Settings → Developer tools → download Linux
   (PRoot Ubuntu). Without it, Antigravity cannot start.
2. **Add account** — Settings → Antigravity → "Start sign-in". The app runs
   `agy` in a real terminal inside Linux and shows a Google sign-in link.
3. **Sign in in the browser** — open the link, sign in with Google, copy the
   one-time code it shows, paste it back in the app. The app answers the
   colour / rendering / privacy (opts OUT of data collection) / trust screens
   by itself.
4. **Done** — the token is saved into that account's own folder on the phone.
   **Axion never reads the token.** Add more accounts the same way; the app
   picks the best one per run and fails over to the next on a limit/sign-in
   failure (`antigravity/AgyAccounts.kt`, `AgyFailover.kt`).
5. **Use it** — Antigravity is the **Build engine only** (Builder agent, §2
   #11). It never answers chat; chat runs on Gemini / Grok / OpenRouter. If
   every account hits its limit, the app says which account is back at what
   time instead of failing silently.

Caveats: sign-in times out after 15 minutes (start again); a failed code
exchange means "paste a fresh code". The Grok mock's CLI screen ("Not paired",
pair-code input) is the UI to build on top of this flow — the pairing itself
already works.

## Build order (suggestion, not a commitment)

1. Bundle the remaining Hermes skill packs (fills the 3 "no roster agent
   claims it yet" rows: app-store-design, header-footer-design,
   local_external_builder_brief).
2. Antigravity pair-code UI (Grok CLI screen) on the working login flow.
3. Upload gaps: video in Gallery row, audio attach, batch pick + progress.
4. Real 3D avatar engine for the "3D" profile icon (replaces the drawn
   tile) — only if the user asks for it.
