# BRAIN-SOURCES — license boundaries for the new series

Standing user rules (from memory): Axion is the master project. Other
open-source apps' CODE may be lifted only where their license allows it;
behaviour/ideas are fine to copy, code is not, unless the license says so.
Andee code is BANNED outright. Locus is AGPL — idea/behavior only.

This file is the license gate for every future port into this tree. When in
doubt, KEEP the boundary: use the idea, write original Axion code, and record
the source in `NOTICE.md` (donor `NOTICE.md` already follows this pattern).

## Allowed — with conditions

### OpenDots — MIT — attribution OK, code may be reused with attribution
- Source: `~/workspace/opendots/app` (CopilotKit OpenDots — check `LICENSE` there).
- **It is a WEB app, not Android.** There is no Android code to copy — its
  agent-computer ideas (computer-use, Coworker bot) need a PORT to Kotlin,
  not a copy. The donor already used it this way: Bot Store seeds are
  "definitions only, no code copied" (`NOTICE.md`).
- Rule: reuse ideas freely; any code adapted from it keeps the MIT attribution
  in `NOTICE.md` + `third_party/`.

### Hermes Agent — MIT — skills/patterns port OK
- Donor `NOTICE.md`: "Hermes Agent (MIT), patterns only: learning loop,
  subagent delegation, cron scheduler, memory search (v0.9.7). Only the public
  README was read and no source code was copied."
- Rule: port patterns and SKILL.md skills into Axion Kotlin; keep the Hermes
  credit in `NOTICE.md`. Do not paste Hermes source files in.

### Mika — Apache-2.0 — NOTICE kept
- Donor ships 5 Mika skill packs in `app/src/main/assets/skills/`
  (`mika-content-transcribe`, `mika-documents`, `mika-info-research`,
  `mika-messaging`, `mika-skill-authoring`) + `MIKA-LICENSE-Apache-2.0.txt` +
  `MIKA-NOTICE.txt` at tree root. Donor `NOTICE.md`: "Mika (Apache-2.0), idea
  only: step-by-step phone control with live steps and a stop button."
- Rule: keep the Apache-2.0 license + NOTICE files with any Mika-derived work;
  keep attribution in `NOTICE.md`.

### Koog — Apache-2.0 — ideas only (donor precedent)
- Donor `NOTICE.md`: "Koog (Apache-2.0), ideas only: planning strategy,
  history compression, fault tolerance." Same NOTICE-keeping rule as Mika.

### Ponytail — MIT — ideas/definitions only (donor precedent)
- Donor `NOTICE.md` (Bot Store seeds): Ponytail (MIT) — definitions only, no
  code copied. `app/src/main/assets/skills/ponytail-minimal-code/` is a skill
  pack, not Ponytail source.

### Mobile-Harness, ffmpeg-skill — MIT — ideas only (donor precedent)
- Donor `third_party/NOTICE-mobile-harness.md`, `NOTICE-ffmpeg-skill.md`:
  ideas / tool contract only, no source copied.

## Idea/behavior ONLY — NO code copy

### Locus — AGPL-3.0 — NEVER copy code
- Locus sets the benchmark for Axion (10X plan), but AGPL code copied into
  Axion would force Axion's source open under AGPL. Only behaviour/ideas
  (on-device agent loop, tool design, UX patterns) may be reused — all Axion
  code stays original. This is the user's explicit standing rule.

### Operit — LGPL-3.0 — not copied, ported or translated in any way
- Donor `NOTICE.md`: only the general concept "a library of ready tools the
  user can switch on and off" was used; `agent/ToolCatalog.kt` and
  `ui/settings/ToolsLibraryScreen.kt` are original Axion code.

### Eta — no licence — source never opened or used
- Donor `NOTICE.md`: its source was never opened or used. Keep it that way.

## BANNED — do not touch

### Andee — BANNED — no code, no white-label
- License verified 2026-11-08: Apache-2.0 + additional conditions; Part A.1(d)
  bars distributing it as another product's component without KUAFUAI written
  authorisation, and A.1(a) bars white-labelling. Andee-JAISA behaviour may be
  built from Axion's OWN code; Andee code, assets or branding must never enter
  this tree. This is a hard user rule.

## Also relevant

- **Grok design reference** (`~/workspace/grok-workspace`): a UI mock built for
  this user. Its tokens/layout are the new-series design — reusing the design
  is the assignment. Its 3D mascot (`public/mascot.glb`) is a project asset;
  check its provenance before shipping it in a release build.
- **Astryx** (Meta, MIT) is the user's standing primary design system for new
  app/website builds — but this project keeps its locked Grok/Sand design; do
  not re-skin to Astryx without the user's word.
- **LFM3-1.7B** (Liquid AI): offline on-device model for chat only, already
  integrated in the donor (`local/`). Keep it chat-only; no agentic coding on it.
