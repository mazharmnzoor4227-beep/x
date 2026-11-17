# X — NEW SERIES (temporary name, fresh tree, not a release yet)

Personal AI agent for Android by Mazhar Ansari. Grok-style Sand UI, a real
tool-calling agent, paste-a-key connectors with automatic failover — rebuilt as
a new series from the v0.9.7 donor brain + the Grok Sand design reference.

**Name: "X" is TEMPORARY** (set 2026-11-17 on the user's word — he will
change it later). Package `com.mazharansari.x`, version **1.0 / versionCode 1**.
Donor was Axion v0.9.7 (see Provenance below).

**Status: FIRST BUILD (2026-11-17).** Sand theme wired (live in
`AppRoot.kt`), 24-agent roster (23 seeds in `BotCatalog.kt`), 30 skill packs,
19/19 Grok screens, CI fixes (setup-gradle, google-services removed). NOT
compiled yet — first verification is GitHub Actions CI after push (Gradle
hangs in this sandbox — do not run `./gradlew` here).

## Provenance (donor — DO NOT MODIFY the donor)

- Donor: `~/workspace/axion-roadmap/work/axion-app`, branch `axion-ui-phase2`
- Donor commit: `57003ac52d39b7290e9004c511b1495e1db58b3a`
  ("Phase 2: fix V052SpeedBugTest — default Paste as File to Always…")
- Donor version: v0.9.7 / versionCode 40, package `com.mazharansari.axion`
- This tree: plain `cp -r` copy with `.git` removed (fresh tree, no history).
  The donor's own README is preserved at `docs/DONOR-README.md`.
- Donor `NOTICE.md` + `third_party/` license notes carried over unchanged.

## What was added in this foundation step

- `app/src/main/java/com/mazharansari/x/ui/theme/SandTheme.kt` — Grok Sand
  tokens as `SandLightPalette` / `SandDarkPalette` + `SandTheme()` composable.
  SandTheme is now the LIVE theme (called from `AppRoot.kt`); `Theme.kt` is
  kept as fallback.
- `THEME-MAPPING.md` — token-by-token Grok → Compose mapping + font gap
  (Fraunces/Outfit not bundled yet) + mascot/splash notes.
- `FEATURE-MAP.md` — all 19 Grok screens mapped to donor capability:
  10 exist, 6 partial, 3 missing (Feed, Ideas, Goals).
- `BRAIN-SOURCES.md` — license boundaries (OpenDots MIT, Hermes MIT, Mika
  Apache-2.0, Locus AGPL idea-only, Andee BANNED).
- `app/google-services.json` REMOVED from this copy (donor Firebase project
  `axion-d5461` config). The new series needs its OWN Firebase project/config
  before any build that uses the google-services plugin — do not copy the
  donor's file back in.

## Pending user decisions (do NOT decide alone)

1. **Package + repo rename:** DONE 2026-11-17 on the user's word —
   package is now `com.mazharansari.x`, `versionName` **1.0** / `versionCode` 1,
   app display name **"X"** (temporary — he will change it later). Renaming
   the package breaks install-over updates from the donor Axion, which is
   intended for a new series. Repo: https://github.com/mazharmnzoor4227-beep/x
   (public, created 2026-11-17).
2. **GitHub repo creation:** DONE 2026-11-17 — repo exists; this tree is
   pushed as the first content (README landed first via the Contents API,
   full tree follows via the PAT-backed bulk push).
3. **Signing:** no keystore in this tree (donor signing reads
   `../keys/keystore.properties`, outside the source). Release signing must be
   set up for the new series; never reuse the donor keystore without his word.
4. **Mascot:** `mascot.glb` (Grok reference) needs a renderer decision
   (Filament / SceneView / static fallback) — see THEME-MAPPING.md.
5. **Firebase:** new Firebase project + `google-services.json` for this series.

## Build (after repo + CI exist)

- `./gradlew :app:assembleRelease` (JDK 17, Android SDK 35).
- Tests: `./gradlew :app:testDebugUnitTest` (Robolectric).
- DO NOT run Gradle in the sandbox (it hangs); use GitHub Actions CI.

## Layout (donor modules, unchanged)

`agent/` agent loop + tools · `antigravity/` Agy accounts/engine ·
`browser/` Obscura headless browser · `build/` project builds · `data/`
connectors/keys/memory · `github/` · `linux/` PRoot terminal · `livecall/` ·
`llm/` providers (Gemini/Grok/OpenRouter/Antigravity…) · `local/` LFM3 ·
`mcp/` remote MCP client · `quota/` · `security/` · `skills/` (18 packs in
`app/src/main/assets/skills/`) · `status/` · `telegram/` · `ui/` Compose
screens · `update/` · `voice/` · `work/` swarm bots + Bot Store + schedules.
