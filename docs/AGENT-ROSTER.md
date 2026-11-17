# AGENT ROSTER — 24 specialist agents for X (temporary name)

Docs only, 2026-11-17. No app code changed, nothing pushed.
Donor base: Axion Android v0.9.7 / code 40 (`axion-ui-phase2`, Kotlin/Compose).
Design: Grok workspace UI mock (19 screens) — reference only.
Merge map: `docs/MERGE-SOURCES.md`. License gate: `BRAIN-SOURCES.md`.

User order (2026-11-17): **at least 20 specialist agents, more if possible** —
each an expert in its own work. The user just plugs in their own provider
(BYOK) and the app does every task properly. Antigravity attaches via CLI.
All skills + file upload + everything inside.

## How a "bot" works here (read this before the table)

A bot is a **definition bundle, not code** (`data/BotStore.kt`): name +
description + persona + skills + tool allow-list + suggested provider,
stored in `bots.json` and run through **Axion's own agent loop**. Installing a
bot never executes foreign code. Any bot can create another bot; there is no
hardcoded CEO class — the name + parent/child relationship is what makes a
bot the parent for a run. Every bot must verify before it delivers: never
present a result as done until it is checked.

Swarm caps (honest limits, `agent/Subagents.kt` + `agent/Swarm.kt`): max **4
parallel workers** per fan-out, **8 steps per worker**, recursion depth **2**,
**6 workers per parent run**, **2 spawns per worker**. Workers only touch
their role's allow-list — **no GitHub tools, no posting, no installing, no
payments, ever** — and never ask for approvals; the parent approves the whole
swarm once (`agent/ApprovalGate.kt`). A worker failure never kills the run.

Status key: ✅ Exists (seeded in the donor Bot Store) · 🟡 Partial (runs on
the existing loop + tools, but no store seed yet — definition to write) ·
🆕 New (definition + a missing capability to build first — the gap is named).

## 1. The roster — 24 agents

| # | Agent | Expert domain | Provider | Tools / skills it uses | Status |
|---|-------|---------------|----------|------------------------|--------|
| 1 | **Axion** | Main agent + orchestrator. General chat, routes work, spawns specialist workers, merges results, talks to the user | Auto failover (Gemini → Grok → OpenRouter…) | All tool groups; `subagent_swarm`; `agent/Agent.kt`, `agent/Swarm.kt` | ✅ Exists — the main loop itself |
| 2 | **Coworker** | Persistent teammate. Keeps context across chats, remembers decisions, picks work back up | Auto (suggested: OpenRouter) | web, research, files, documents, learning, schedule, skills | ✅ Exists — `store-coworker` seed (OpenDots pattern, MIT) |
| 3 | **Researcher** | Deep research. Plans sub-questions, reads sources in full, cross-checks, cited report | Auto (suggested: Gemini) | deep-research, local_verify_before_recommending; web, research, files, documents | ✅ Exists — `store-researcher` seed (Hermes pattern, MIT) |
| 4 | **Coder** | Writes the minimum code that fully works. Reads first, reuses, verifies by reading back | Auto (suggested: Grok) | local-android-dev, ponytail-minimal-code; files, commands, skills | ✅ Exists — `store-coder` seed (Ponytail MIT + Mika Apache-2.0) |
| 5 | **Reviewer** | Runs the tests, reviews code, reports honestly what passed / failed / is unverified | Auto (suggested: Gemini) | local-pre-delivery-verification-gate, local-verified-status-claims; files, commands | ✅ Exists — `store-reviewer` seed (Koog pattern, Apache-2.0) |
| 6 | **Learner** | Learns from every task — saves what worked as a reusable skill or memory note | Auto | agent-building; skills, learning, files, web | ✅ Exists — `store-learner` seed (Hermes pattern, MIT) |
| 7 | **Scheduler** | Runs tasks on a schedule (once / daily / weekly) and reports back as a new chat | Auto | schedule, learning, web, research; `work/ScheduleEngine.kt` | ✅ Exists — `store-scheduler` seed (Hermes pattern, MIT) |
| 8 | **Tester** | Test runner. Executes the test suite, reports exit code + failures verbatim | Auto (fast model) | files, commands; swarm TESTER role (`agent/Subagents.kt`) | 🟡 Partial — swarm role exists, no store seed yet |
| 9 | **Planner** | Plan mode. Breaks a build into steps + file list before any code is written (OpenCode Plan) | Auto (strong model) | `agent/Planning.kt`, `build/BuildLoop.kt`; files | 🟡 Partial — planning code exists, no seed, no Plan UI |
| 10 | **Explorer** | Codebase exploration. Maps a repo / finds where a feature lives before Coder touches it (OpenCode Explore) | Auto (fast model) | files, web; `build/ProjectFiles.kt` | 🟡 Partial — file tools exist, no seed |
| 11 | **Computer** | Computer-use. Drives the real browser: click, type, scroll, screenshot, take over from the user (OpenDots Dot-computer shape) | Auto | browser group; `browser/ObscuraEngine.kt`, `browser/BrowserController.kt` | 🟡 Partial — Obscura works, no Dot bot, no unified Computer screen (FEATURE-MAP §7) |
| 12 | **Builder** | Builds apps with Antigravity. The Build engine — full app builds, not chat answers | **Antigravity only** (CLI, never an Ask provider) | `build/AgyBrain.kt`, `build/BuildLoop.kt`, `antigravity/AgyEngine.kt` | 🟡 Partial — engine exists, no store seed; needs agy + signed-in account (§2) |
| 13 | **Doc Maker** | Documents workspace. Writes + edits pages/docs, review-before-save, exports PDF (OpenDots Spaces/Pages shape) | Auto | documents group (`create_pdf`), files; `agent/PdfTool.kt` | 🆕 New — PDF tool exists, but **no docs workspace / Pages store**; build from scratch |
| 14 | **File Clerk** | Files. Reads uploaded files (PDF, documents, ZIP), lists workspace, organises outputs | Auto | files group; `agent/FileTools.kt`, `agent/PdfTool.kt` | 🟡 Partial — tools exist, no seed |
| 15 | **Image Maker** | Image generation. Posters, logos, product shots from a description | Auto (connected image model) | images group (`generate_image`); `agent/ImageGen.kt` | 🟡 Partial — tool exists, no seed |
| 16 | **Video Editor** | Video on the phone. Trim, captions, reel size (9:16), loudness, compress, extract audio | Auto | video group; `agent/VideoTools.kt`, `agent/FfmpegCommands.kt` | 🟡 Partial — tools exist, **needs Developer tools (Linux)** installed |
| 17 | **Voice** | Voice. Dictation, read-aloud, live voice calls | Auto | `voice/VoiceRecognizer.kt`, `voice/ReadAloudPlayer.kt`, `livecall/` | 🟡 Partial — voice + calls exist, no seed |
| 18 | **Messenger** | Messages + email. Drafts replies for any chat app; sends **email only** via a connected Gmail (MCP connector); Telegram bot control | Auto | mika-messaging skill; `telegram/TelegramBot.kt`; connectors group | 🟡 Partial — Telegram bot + drafting exist. **Honest limit:** Axion **cannot open or send WhatsApp / WeChat messages** — no tool sends them and no tool opens another app's chats. It drafts; the user sends. (Per `mika-messaging` SKILL.md.) |
| 19 | **Shopkeeper** | The user's shops. Clawora + Whitening Plus + TMF: listings, prices, orders, customer questions | Auto | web, research, files; local_verify_before_recommending | 🆕 New — definition only; no shop API is wired yet (needs each store's API/connector) |
| 20 | **Money** | Prices + availability, Pakistan-checked. Verifies a service/model/product is really available in Pakistan (signup, payout, price) before recommending it | Auto | web, research; local_verify_before_recommending | 🆕 New — definition only; uses the existing verify skill |
| 21 | **Teacher** | Guided setup. One plain step at a time, in the user's language, with marked screenshots when a button is hard to find | Auto (cheap/fast model) | learning, files; memory | 🆕 New — definition only; matches the user's guided-setup rule |
| 22 | **Memory Keeper** | Memory curation. Keeps memory notes clean, editable, and searchable; prunes stale facts | Auto | learning group; `data/MemoryStore.kt`, `agent/LearningTools.kt` | 🟡 Partial — memory store exists, no seed |
| 23 | **Guard (Sentinel)** | Approvals + safety + quota watch. The approval card (Allow once / This chat / Decline), quota + limit warnings | Auto | `agent/ApprovalGate.kt`, `data/Approvals.kt`, `quota/QuotaRepo.kt` | 🟡 Partial — gate + quota exist; the Grok "Sentinel" card maps 1:1, needs the seed + UI wiring |
| 24 | **Idea Catcher** | Ideas + goals. Captures ideas from chat, groups them, turns a goal into a daily plan with progress rings | Auto | learning, schedule, files | 🆕 New — **blocked until the Feed / Ideas / Goals tabs exist** (FEATURE-MAP §8–10: no stores, no screens yet) |

**Count: 24 agents — ✅ 7 exist · 🟡 12 partial · 🆕 5 new.**

What the numbers honestly mean: the **agent loop, swarm, approvals, tools and
18 skill packs already exist** — so a "partial" agent is mostly a new
definition bundle (persona + tool allow-list) on top of working machinery, not
a new engine. The 5 "new" ones need real new capability first (docs workspace,
shop APIs, Ideas/Goals stores). The Grok mock's "56 skills enabled" is **fake
mock data — do not copy it**; the real count is 18 packs (§3).

The user can also create **their own bot** from the Bots section
(`BotDefinitionStore.create` — any name, description, persona, suggested
provider) and pick any of the **12 built-in logos** (brain, agent, rocket,
sparkles, bolt, hammer, globe, person, gem, shield, wand, cube) or their own
image (`data/BotLogo.kt`).

## 2. Antigravity attach — the CLI route (exact, as built in the donor)

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
5. **Use it** — Antigravity is the **Build engine only** (Builder agent, §1
   #12). It never answers chat; chat runs on Gemini / Grok / OpenRouter. If
   every account hits its limit, the app says which account is back at what
   time instead of failing silently.

Caveats: sign-in times out after 15 minutes (start again); a failed code
exchange means "paste a fresh code". The Grok mock's CLI screen ("Not paired",
pair-code input) is the UI to build on top of this flow — the pairing itself
already works.

## 3. Skills — all 18 packs, and which agents use each

Bundled in the donor at `app/src/main/assets/skills/` (18 packs + 3
notice/license files). `skills/SkillHub.kt` + `skills/SkillRepo.kt` load them;
`agent/SkillTools.kt` exposes `load_skill` / `skill_create` / `skill_update`
to the agent loop.

| # | Skill pack | What it teaches | Used by agents |
|---|------------|-----------------|----------------|
| 1 | agent-building | How to build/define agents + bots | Learner, Axion |
| 2 | deep-research | Multi-angle research, citations, two-source rule | Researcher, Money |
| 3 | local-android-dev | Production Android dev (native + cross-platform) | Coder, Planner, Explorer |
| 4 | local-pre-delivery-verification-gate | Pre-delivery checks (geometry, header gap, wiring) before claiming done | Reviewer, Tester |
| 5 | local-verified-status-claims | Report status only from a verifiable handle | Reviewer, Guard |
| 6 | local_reference_pattern_research_gate | Research a pattern from its real source before building | Researcher, Planner |
| 7 | local_verify_before_recommending | Verify the deciding fact (country, price, payout) before recommending | Researcher, Money, Shopkeeper |
| 8 | mika-content-transcribe | Transcribe audio/video content | Voice, File Clerk |
| 9 | mika-documents | Work with documents | Doc Maker, File Clerk |
| 10 | mika-info-research | Info research (Mika) | Researcher |
| 11 | mika-messaging | Read chats/email, draft replies (draft-only for chat apps) | Messenger |
| 12 | mika-skill-authoring | Write + improve skills | Learner |
| 13 | pdf | PDF reading / reports | File Clerk, Doc Maker |
| 14 | ponytail-minimal-code | Minimum code that works | Coder |
| 15 | roman-urdu | Roman Urdu handling | Messenger, Teacher |
| 16 | token-efficiency | Do more with fewer tokens | All (loop-level) |
| 17 | video | Video editing with ffmpeg | Video Editor |
| 18 | website-audit | Audit a website properly | Researcher, Reviewer |

**Pipeline, not yet in the app:** `~/workspace/hermes-skills-port/` holds **29
more SKILL.md packs** (creative, research, software-development, web —
incl. Astryx design, frontend, game-dev, Shopify, Playwright) ready to bundle
into `assets/skills/` (MERGE-SOURCES §4). Skills install from GitHub too
(`install_skills_from_github` tool) and the agent can write its own learned
skills (`skill_create`) — reviewed before they are published (OpenDots
learning-container pattern).

## 4. File upload — what exists today, and the gaps

What exists in the donor:

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

## 5. BYOK — one universal key box

Standing rule: **the user plugs in their own provider; the app does the work.**
No OpenAI — ever (user rule; bots never suggest it, even though an `openai`
entry exists in the provider table — it is excluded from bot suggestions).

- **One key box, auto-detect:** paste a key in Settings → Connectors and the
  app detects which provider it belongs to (`llm/Providers.kt` — Gemini keys,
  then format heuristics). Keys are stored encrypted (`data/KeyVault.kt`).
- **Providers in the table (15):** Google Gemini, Groq, Cerebras, Mistral,
  OpenRouter, Hugging Face, NVIDIA NIM, SambaNova, Cohere, Together AI,
  DeepSeek, xAI Grok, **Local model (LFM3-1.7B, on-device, no key, chat-only)**,
  My server — plus Antigravity, which is **not a key at all** (CLI login, §2).
- **Failover is automatic:** if the first provider fails, the next in the
  ranked chain takes over; a bot's suggested provider only **reorders** that
  chain, never pins it (`data/BotStore.kt: preferProvider`).
- **Per-bot provider:** each bot definition carries one suggested provider
  (e.g. Coder → Grok, Researcher → Gemini); null = Auto.

## Build order (suggestion, not a commitment)

1. Write the 12 "partial" definitions (one file each in the Bot Store seeds) —
   no new engine needed.
2. Antigravity pair-code UI (Grok CLI screen) on the working login flow.
3. Doc Maker workspace (Spaces/Pages) — the biggest missing Dots piece.
4. Ideas + Goals stores/screens (unblocks agent #24 and the dock tabs).
5. Upload gaps: video in Gallery row, audio attach, batch pick + progress.
6. Bundle the 29 Hermes skill packs.
