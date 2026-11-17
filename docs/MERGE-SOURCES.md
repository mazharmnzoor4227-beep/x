# Merge Sources — X New Series (temporary name)

Research-only, 2026-11-17. No code was copied, no repo created, nothing pushed.
Donor base: Axion Android v0.9.7 / code 40 (`axion-ui-phase2`, Kotlin/Compose, MIT).
Design: Grok workspace UI mock (19 screens, sand theme) — reference only.

User scope correction (2026-11-17): do NOT just lift Axion code. Merge code/ideas
from GitHub open-source agents into ONE proper agent that does every task.

## 1. "Open Muse" — identification verdict

**Verdict: the user almost certainly means CopilotKit's OpenMuse, now renamed
OpenFuse (`github.com/CopilotKit/openfuse`, formerly `CopilotKit/openmuse`).**

Evidence:
- The user names it in the same breath as OpenDots ("Open dots ho gaya, open muse
  ho gaya"). OpenDots and OpenMuse are siblings from the same team (CopilotKit),
  listed together in CopilotKit's own family (OpenBot, OpenDots, OpenDuet,
  OpenMuse). Same naming pattern, same stack (CopilotKit + AG-UI), same purpose
  (open take on Meta's Muse).
- OpenClaw and OpenCode are real, famous agents — but neither is called
  "Open Muse" anywhere, and the user already treats them as separate names.

### Ranked candidates for "Open Muse"

| # | Repo | Stars (Nov 2026) | License | Language | What it is | Verdict |
|---|------|------------------|---------|----------|------------|---------|
| 1 | `CopilotKit/openmuse` → **renamed `CopilotKit/openfuse` 2026-11-13** | ~6,483 | MIT | TypeScript | Personal agent: persistent Chromium browser, optional Linux workspace, terminal, files, durable background tasks, approvals/receipts; Expo app (iOS/Android/web) + Hono server + Playwright browser worker | **Most likely. Take as behavior + portable patterns; see blocker below** |
| 2 | `nano-muse/nanoMuse` (was `OpenMuseAgent/OpenMuse`, renamed) | ~3,499 | GPL-3.0 | Python | Phone-first personal agent: real Android APK, screen-driven operation of apps with no API, works while app closed, asks before irreversible actions; 26 providers | **Behavior only — GPL-3.0 forbids merging its code into Axion (MIT) without relicensing. Its phone-native behaviors are the most valuable of this family** |
| 3 | `chyroc/open-muse` | ~124 | MIT | TypeScript | iPhone/Mac client for Volcano Engine Ark Managed Agents | Minor; backend-dependent, little to take |
| 4 | `diggerhq/openmuse` | ~54 | **None** | — | Web app on OpenComputer Serverless Agents; no commit since 2026-09-11 | **Do not take — no license, dead** |
| — | `openclaw/openclaw` (OpenClaw) | ~393,000 | MIT | TypeScript | Gateway-based personal assistant, WhatsApp/Telegram/Discord/Slack channels, ClawHub skills registry (3,000+ skills), Markdown memory | **Not "Open Muse", but a top merge source in its own right** |
| — | `anomalyco/opencode` (OpenCode; `opencode-ai/opencode` is archived) | ~216,000 | MIT | TypeScript | Coding agent: terminal/desktop/IDE, 75+ providers, Build/Plan/Explore subagents, permission rules, headless server + OpenAPI | **Not "Open Muse", but the coding-brain merge source** |

### OpenMuse/OpenFuse blocker (important)

- The code is MIT, **but every mode needs a CopilotKit Intelligence project key** —
  a separate hosted service **not covered by the MIT license** (free tier 1,000
  interactions/org/month; paid after that). Axion must NOT depend on it.
- Take: the agent-computer architecture (persistent browser + Linux workspace +
  task worker), the approval/receipt flow, the AG-UI event protocol shape, the
  "visible work" UX. Run it on Axion's own providers (Gemini/Grok/OpenRouter/
  Antigravity) and Axion's own Obscura browser + PRoot terminal instead.
- Status: alpha, no tagged releases; Android bundle-checked but not installed on a
  device by its own verification notes (Oct 2026).

## 2. Per-source merge table

| Source | Repo | License | Language | Take as | Port strategy for Android Kotlin |
|--------|------|---------|----------|---------|----------------------------------|
| Axion (donor base) | `mazharmnzoor4227-beep/axion-app` (local `axion-roadmap/work/axion-app`, branch `axion-ui-phase2`) | MIT (own) | Kotlin | **Code — the base** | Already Kotlin/Compose; re-skin to Grok design, keep Obscura browser, PRoot Linux terminal, LFM3 local model, swarm bots, Bot Store, 16 skills |
| OpenDots | `CopilotKit/OpenDots` (cloned at `~/workspace/opendots/app`) | MIT | TypeScript | Code patterns + behavior | See §3. Server/TS code is NOT portable line-for-line; port the concepts to Kotlin. Attribution required |
| OpenMuse → OpenFuse | `CopilotKit/openfuse` (was `openmuse`) | MIT (+ Intelligence service, not covered) | TypeScript | Behavior + patterns only | Agent-computer architecture, approvals/receipts, AG-UI protocol shape. Do NOT take the Intelligence dependency |
| nanoMuse | `nano-muse/nanoMuse` | **GPL-3.0** | Python | **Behavior only** | Copy the behaviors: phone-GUI operation of apps with no API, background work while closed, ask-before-irreversible. **No code copy** |
| OpenClaw | `openclaw/openclaw` | MIT | TypeScript | Behavior + skill format | Gateway/channel model, Markdown memory, ClawHub-style skill registry (SKILL.md format already adopted 2026-10-07). Node gateway code → Kotlin service, not a copy |
| OpenCode | `anomalyco/opencode` | MIT | TypeScript | Behavior + loop design | Agent loop, Build/Plan/Explore subagents, permission rules, session model. Feeds Axion's Build section (`BuildLoop`, `AgyBrain`) |
| Hermes Agent | `NousResearch/hermes-agent` | MIT | Python | Skills + behavior | Skills already ported as SKILL.md (see §4). Learning loop (curated memory + self-written skills) + GOAP reasoning as Kotlin behavior. Python code not portable directly. Runs on the Oracle server, not on-device |
| Hermes skills port (local) | `~/workspace/hermes-skills-port/` | MIT (Hermes) | SKILL.md | **Code — copy the skill files** | Already in SKILL.md form; bundle into Axion `assets/skills/` like the Mika skills |
| Jarvis (Hermes Android client) | `bwarhness/jarvis-assistant` | **Unverified — check before any code use** | Kotlin/Compose | Architecture reference only (for now) | Voice-assistant client pattern; no releases, APK must be built from source. Do not copy code until license confirmed |
| OpenBot | `CopilotKit/OpenBot` | MIT (verify at clone time) | TypeScript | Behavior | Computer supervisor/container pattern behind OpenDots computers; on Android the equivalent is Axion's Obscura + PRoot, not Docker |
| Mika | `wangzan101` / Mika (already in Axion) | Apache-2.0 | Kotlin skills | **Code — already merged** | 5 skills live since 0.9.3; keep NOTICE + LICENSE files |
| Locus | `springmusk026/locus` | **AGPL-3.0** | Kotlin | **Behavior only** | Benchmark for on-device agent UX; **no code copy** |
| Andee | Andee (AAswordman/Operit lineage) | Apache-2.0 + additional conditions | — | **BANNED entirely** | License A.1(d) bars distributing it as another product's component without written authorisation + no white-labelling. No code, no behavior lift as a component |
| ChatGPT Dots | Closed product (OpenAI) | — | — | **Behavior/UX reference only** | Multi-Dot coworker UX is the experience target; no code exists to take |
| Grok Bot / Grok app | Closed product (xAI) | — | — | **Behavior/UX reference only** | Grok workspace design file IS the UI reference; no code to take |
| Meta Muse | Closed product (Meta) | — | — | **Behavior/UX reference only** | The shape all of the above copy: one proactive agent, isolated computer, works while closed |

## 3. OpenDots capability inventory (read from `~/workspace/opendots/app`)

Source files: `src/server/` (dot-agent, computer-tools/service, page-tools,
research, runner, parallel, learning, slack-channel, voice), `src/client/`
(Chat, ComputerPanel, SpaceLibrary, WorkspaceDialog…), `src/shared/`
(computer-types, learning, page-review). README Features table + `docs/`.

| Capability (OpenDots) | How it works there | Port to native Android Kotlin? |
|-----------------------|--------------------|--------------------------------|
| Specialist Dots (multi-agent, name/role/instructions/per-Dot tools) | `dot-agent.ts` — one agent class per Dot, tools assembled per Dot | **Port — direct.** Axion already has swarm bots + Bot Store; map Dot = Bot, per-Dot tool allowlist = Axion's existing permission model |
| Dot computers: browser | Playwright persistent Chromium worker, snapshot/click/type/key/scroll/screenshot (`computer-navigate/read/snapshot/screenshot/click/type/key/scroll`) | **Behavior port.** Playwright+Chromium can't run on Android; Axion's equivalent is Obscura (already built) — reuse the snapshot/ref action model |
| Dot computers: files | `computer-files_list/read/write` in the Dot's isolated workspace | **Port — direct.** Axion already has file tools; adopt the per-Dot workspace + permission scoping |
| Dot computers: shell | `computer-exec` inside Docker container (OpenBot supervisor) | **Behavior port.** Docker can't run on Android; Axion's PRoot Linux terminal is the equivalent — reuse the exec/timeout/audit model |
| Human takeover | `human_click/type/key/scroll` — user takes over the live browser | **Port — direct.** Axion's Computer screen (Grok design) already has this slot; wire Obscura view to it |
| Spaces / Pages (document workspace) | Searchable library, visual editor, slash commands, autosave, revision checks; page tools `list/create/read/edit_space_page` | **Port — new feature.** Axion has no docs workspace; build with Room + Compose editor. Medium effort, high value (Dots' signature feature) |
| Review before saving | Human review card in chat; save waits for approval, then links the page | **Port — direct.** Axion already has approval gate + verify-before-deliver; extend it to cover page saves |
| Persistent memory | User-managed preferences usable by permitted Dots; `learning.ts` per-Dot Learning containers, evidence routing, published-skill delivery | **Port — direct.** Axion has memory + skills; adopt the learning-container + review-before-publish-skill flow |
| Background work / schedules | Scheduled server-side turns in the original conversation, pause/retry | **Behavior port.** Needs a server for true scheduling; on-device use WorkManager + Axion's scheduler (already exists, timing inexact — noted gap) |
| Browser (read-only public pages) | Separate read-only page service with capture + navigation limits | **Port — direct.** Axion's research/browser path already does this; adopt the read-only + limits discipline |
| Research | `research.ts` — `search_web`, cite source URLs | **Port — direct.** Axion deep-research skill already does this |
| Voice / calls | WebRTC speech, delegated compute, bounded sessions, hangup, timeline receipts (`voice.ts`, `useVoice.ts`) | **Needs server — not portable on-device.** Voice input/output portable (Axion has voice); live calls need a server/telephony side. Defer |
| Slack channel | Managed Channels SDK, workspace/user allowlists (`slack-channel.ts`) | **Needs server.** Axion has Telegram; Slack = future server-side channel, same pattern |
| Conversations / Threads | CopilotKit Threads, page-specific conversations | **Behavior port.** Axion's chat storage is local Room; adopt the per-page/per-Dot thread separation, not the CopilotKit service |
| AG-UI protocol | Event protocol connecting agent ↔ UI | **Behavior — optional.** Useful if a server sync is ever added; not needed for on-device v1 |

## 4. Hermes — what's portable

From `~/workspace/hermes-skills-port/` (SKILL.md ports, already in portable form)
and `~/workspace/hermes-jarvis/SETUP-STATUS.md`:

- **Skill packs ready to bundle** (copy SKILL.md files into Axion `assets/skills/`):
  - `autonomous-ai-agents/agent-building`
  - `creative/`: banner-design, display_font_glyph_check, local-app-store-design,
    local-design-taste-frontend, local-frontend-design, local-header-footer-design,
    local-tailwind-design-system, local-ui-ux-pro-max, local-web-design-guidelines,
    local_confirm_surface_before_building, meta-astryx-design, responsive-layout
  - `research/`: local_reference_pattern_research_gate, local_verify_before_recommending
  - `software-development/`: delivery, local-android-dev,
    local-android-ui-journey-testing, local-game-dev, local-playwright-skill,
    local-pre-delivery-verification-gate, local-shopify-development,
    local-verified-status-claims, local_android_crash_evidence_first,
    local_external_builder_brief, ponytail-minimal-code,
    reverse-engineering-android, token-efficiency
  - `web/`: local-frontend-seo
- **Behavior to port (Kotlin, not Python):** Hermes learning loop — curated memory
  + agent writes its own skills, GOAP-style task planning, gateway session model.
- **Not portable on-device:** Hermes Agent Python runtime (Termux package carried a
  "currently broken" banner, Oct 2026); it lives on the Oracle server instead.
  Jarvis Android client is Kotlin/Compose but has no releases and its license is
  unverified — architecture reference only until checked.

## 5. License blockers — summary

| Blocker | Detail |
|---------|--------|
| nanoMuse GPL-3.0 | Behavior only. Any code copy would force the whole app GPL. |
| Locus AGPL-3.0 | Behavior only. Same copyleft problem. |
| Andee additional conditions | Banned entirely — no code, no component use. |
| OpenMuse/OpenFuse Intelligence key | MIT code is fine, but the hosted Intelligence service is outside the license and every mode needs its key. Do not build a dependency on it. |
| diggerhq/openmuse | No license at all — do not take anything. |
| Jarvis client | License unverified — check before any code use. |
| All MIT/Apache-2.0 sources | OK with attribution (MIT) / NOTICE (Apache-2.0). Keep `NOTICE.md` + per-source LICENSE files, as Axion already does for Mika/Hermes. |

## 6. Recommended merge order (for the build plan)

1. **Axion base first** — re-skin to Grok design, keep everything that already
   works (providers, Obscura, PRoot, LFM3, swarm, approvals).
2. **OpenDots concepts** — per-Dot workspaces + review-before-save + Spaces/pages
   (the biggest missing piece vs Dots).
3. **OpenMuse/OpenFuse architecture** — task worker + receipts + visible-work UX,
   on Axion's own providers (no Intelligence key).
4. **OpenCode loop** — permission rules + Build/Plan/Explore subagents into the
   Build section.
5. **OpenClaw patterns** — Markdown memory + channel model (Telegram exists;
   WhatsApp later) + skill-registry shape.
6. **Hermes skills** — bundle the §4 skill packs; learning-loop behavior after.
7. **nanoMuse behaviors** — phone-GUI operation + ask-before-irreversible, as the
   final polish (behavior only, never code).
