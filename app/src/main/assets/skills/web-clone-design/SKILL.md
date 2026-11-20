---
name: web-clone-design
description: Clone any website from its link as the user's own site, then keep changing that same site while keeping its theme — source-first reconnaissance, plan-first verdict, own branding, theme tokens in design-dna.json.
version: 1.0.0
author: X built-in (methodology ported from Jane-xiaoer/claude-skill-web-clone, MIT; English fork monjurkuet/web-clone)
license: MIT (methodology source)
---

# Web Clone Design · Design Agent skill

Build a clone of any website the user links to — as HIS website, under HIS
name and branding — and then keep applying his follow-up changes inside that
same website while KEEPING the same theme.

Source methodology: Jane-xiaoer/claude-skill-web-clone v1.6.0 (MIT, 1,114
stars, 132 forks, fetched 2026-11-20, commit 0269e0e08a3783184ec641d341e7d57065d4a5f8),
ported from the English fork monjurkuet/web-clone (MIT, commit
6bd5fd07f7ec4e81b0a02c30fd396da3c66fd921). The upstream ships Node/Playwright
probe scripts (`recon-site.mjs`, `asset-harvest.mjs`, `route-crawl.mjs`,
`interaction-probe.mjs`, `mirror-site.mjs`, `dna-scaffold.mjs`,
`visual-diff.mjs`, `audit-clone.mjs`); those scripts are NOT bundled in X —
the table in "Tool mapping" maps each probe to the X tools that already
exist. The methodology is ported; the probes are re-expressed, never copied
as code. Second reference only for pipeline shape: JCodesMore /
ai-website-cloner-template (`/clone-website`, five-phase pipeline).

## When to use

User pastes a website link and asks for a clone / a site like it / "make it
my own" / "remix this site's look", or asks for a change to a clone project
that already exists ("make the header blue", "add a pricing section").

## IRON RULE — real source first, never AI-guessed code

Fetch the real HTML / CSS / JS of the reference site first. Treat any
AI-generated "clone analysis" as a hint only: assume every executable code
block in it is hallucinated until verified line-by-line against the real
source. A real WebGL demo once used analytic ray-sphere intersection + SVG
`feDisplacementMap` refracting the live DOM; an AI analysis confidently
described ray-marching + SDF + DOM-as-texture — an entirely different, much
slower implementation. Get the real source first, always.

## BOUNDARY — OWN BRANDING ALWAYS, no wholesale lifting (standing rule)

- NEVER copy the reference site's logo, brand name, copyrighted images, or
  trademarked look. Rebuild structure and behaviour; use the user's own
  name, design tokens and logo.
- NEVER hotlink the original site's assets. Captured assets are studied for
  structure only; the clone ships the user's own assets (generated or his
  own photos). Original brand images and logos are never shipped.
- License check before any build (see "License" below). A site with NO
  license is all rights reserved by default: local learning only, credit the
  author, no public redeploy. A proprietary / forbidding licence means
  read-only study only.

## The chat flow (never skip a step)

1. **URL in.** The user pastes a link. Research the live site with the real
   web tools: `browse_url` the home page, follow its internal links
   (route crawl), read the real HTML/CSS/JS, `web_search` the site + GitHub
   for its real source (see Step 1 below). Record what could NOT be
   verified.
2. **Grade complexity (L1–L6) and pick a mode** — faithful clone / visual
   clone / content remix / technical teardown (table below). State the
   expected fidelity and what will NOT be cloned.
3. **Verdict + plan, SHOWN FIRST.** Report: can it be built, the cheapest
   working route (Cloudflare free tier first; Oracle free-tier server only
   when a real server is needed; a good open-source project — check stars +
   license; AVOID frontend-only projects that secretly use someone else's
   API with quotas), honest time (prototype 1 day $0 → working clone 2–4
   days → extras 1–2 weeks part-time), cost (domain ~$11/year; running
   $0/month on free tiers), and honest limits (blocklists, no privacy where
   the service is public, abuse/spam handling, free-tier scale caps, no
   sending where the original doesn't send).
4. **Ask once: build prototype / not now.** Do NOT build before the user
   says yes. No code, no files, no project folder until his yes.
5. **Build only on yes.** Scaffold the project (see "Project layout"),
   write `design-dna.json` from the recon, build the prototype first, then
   the working clone, then extras.
6. **Iterate in the same project.** Every later change request opens the
   EXISTING project, reads `design-dna.json` FIRST, and edits inside it —
   the theme is preserved, never restyled from scratch (see "Theme
   preservation").

## Decision tree (in order, no skipping)

### Step 0 · Scaffold the clone project

One folder per clone under the workspace clone hub `website-clones/`:
`website-clones/<slug>/` with `NOTES.md`, `RECON/`, `design-dna.json`,
`index.html` (the clone). The project record is created by the
`CloneProjects` helper (`agent/CloneProjects.kt`): name, source URL,
created date, mode, complexity level, and the tokens file path. Never
create a second folder for the same source URL — a follow-up change reuses
the existing project.

### Step 1 · Search GitHub for the real source FIRST

`web_search` the site name + `github_clone` when a repo is found.
`vercel.app` / `netlify.app` / `github.io` URL slugs are often the repo or
deployer name. Single-file site → fetch the raw file with `http_request`.
Found source + license allows → skip to Step 4.

### Step 2 · No source found → browser reconnaissance

Map of the upstream probes to X tools (no new tool was needed):

| Upstream probe | X equivalent (already in the app) |
|---|---|
| `recon-site.mjs` (framework, assets, DOM, console, screenshots 1440/768/390) | `browse_url` + `browser_open` / `browser_screenshot` (Obscura) |
| `asset-harvest.mjs` (download assets + manifest) | `http_request` + `write_file`; manifest = `RECON/asset-manifest.json` |
| `network-capture.mjs` (XHR/fetch fixtures) | `browser_*` tools + `http_request`; fixtures saved as local JSON |
| `route-crawl.mjs` (internal route map, max 25 pages, depth 2) | `browse_url` following internal links; map in `RECON/routes.md` |
| `interaction-probe.mjs` (hover / click / scroll / drag states) | `browser_click`, `browser_scroll`, `browser_screenshot` per state |
| `sourcemap-hunt.mjs` (source maps from JS chunks) | `http_request` on `*.js.map` URLs found in recon |
| `mirror-site.mjs` (full-scroll same-origin mirror, static-built sites) | `http_request` per deployed asset incl. fonts / `.wasm` / runtime data; self-host fonts, strip tracking |
| `dna-scaffold.mjs` (design-dna.json skeleton) | `CloneProjects` helper + `references/design-dna-template.json` in this pack |
| `compare-recon.mjs` + `visual-diff.mjs` (clone vs original) | re-run recon on the local clone + `browser_screenshot` side-by-side compare |
| `audit-clone.mjs` (tracking / brand residue / external URLs) | manual audit pass before delivery; results in `CLONE_AUDIT.md` |

### Step 2.5 · Grade complexity before promising anything

| Level | Type | Typical recoverable fidelity | Default boundary |
|---|---|---|---|
| L1 | Static HTML/CSS | 90–98% | Near pixel-perfect; asset copyright is separate |
| L2 | CMS / corporate content site | 70–90% | Front-end reproducible; CMS admin not cloned |
| L3 | React / Vue / Next content front-end | 65–90% | Data/APIs replaced with local JSON stand-ins |
| L4 | Animation-heavy brand site | 50–80% | Hero reproducible; micro-interactions approximated |
| L5 | WebGL / Canvas / Three.js | 30–95% | High if source available; without source, teardown first |
| L6 | SaaS / e-commerce / logged-in systems | Presentation layer only | Server business logic is never cloned |

Modes: faithful clone (real source, byte-faithful) / visual clone (looks
close, implementation replaced) / content remix (keep rhythm + visual
grammar, swap in the user's content) / technical teardown (confirm the real
implementation first, don't rebuild yet).

### Step 3 · Pick the path by recon result

| Recon result | Path |
|---|---|
| Static HTML/CSS, no framework | Mirror → strip tracking → edit copy |
| React / Vue / Next content site | Rebuild on a template, pour content in |
| SPA / data-driven | Network capture → local JSON / mock fixtures |
| Multi-page site | Route map → template per page type |
| Complex interactive | Interaction probe records states → build per state |
| WebGL / Canvas / Three.js heavy | Deep reverse-engineering of real source, or a similar open-source 3D template. No source → runtime frame-capture + baseline gate |
| Static-built site (Astro / Vite SSG / Hugo), even WebGL-heavy | Full-scroll mirror of every deployed asset → self-host fonts → strip tracking → 1:1 clone |
| Site using an off-the-shelf theme | Find the original theme in the theme market (theme-based sites only; custom sites take the mirror path) |

L4–L6 sites follow the complex playbooks: split the site into technical
pillars (rendering, composition, physics, interaction, audio), locate the
real implementation + line numbers for each, and only then check any
second-hand analysis.

### Step 4 · Set up the project in the clone hub

`website-clones/<slug>/`. Keep a read-only `index-original.html` baseline
(the fetched original, for comparison only — never shipped). Write
`design-dna.json` (visual-clone / content-remix modes only — the
faithful-clone branch never uses DNA; real source is the truth, don't let an
approximate style profile dilute the byte-faithful rule).

### Step 5 · Strip tracking + document + verify

- **Strip tracking**: GA (`gtag` / `googletagmanager`), pixels, heatmaps —
  excise line-by-line.
- **NOTES.md** (required): complexity, mode, original-vs-clone comparison,
  fidelity score, known gaps.
- **TEARDOWN.md** for complex sites — every conclusion cites real source
  line numbers.
- **Score the clone** (0–5 per dimension, only scores backed by source,
  screenshots or browser runs): source evidence, structure fidelity, visual
  fidelity, motion/interaction, responsive, functional completeness,
  content replacement, legal/deploy risk.
- **Verify in a real browser** (hard requirement): local server → load →
  console free of JS/WebGL errors → screenshot vs original. Honestly record
  what can't be verified (e.g. synthetic `isTrusted=false` events can't
  trigger drags — say so, never fake success).
- Re-run recon on the clone and compare (report in `CLONE_REPORT.md`,
  pre-launch audit in `CLONE_AUDIT.md`).

### Step 6 · Replace with the user's own content

Replace the trio: text (`index.html` / `data/*.json` / `content/*.md`),
media (`public/` / `assets`), brand colours (CSS vars / Tailwind theme).
Non-trivial structure → write `REPLACE_GUIDE.md`. With `design-dna.json`:
keep the DNA, swap the content — design_system → CSS custom properties;
`visual_effects.effect_intensity` picks the tier (lightweight CSS /
medium Canvas+GSAP / heavy Three.js); prefer real harvested assets over
AI re-draws — but never the original's brand assets (see Boundary).

## Theme preservation (the rule that makes iteration work)

A clone project is a long-lived thing: the user will keep asking for
changes, and every change must land INSIDE the same website with the SAME
theme. The mechanism is a FILE the agent reads, not a prompt wish:

1. Every clone project has `design-dna.json` at its root (created at
   scaffold time by the `CloneProjects` helper; template:
   `references/design-dna-template.json` in this pack). It records the
   clone's tokens: colours (primary / secondary / accent / background /
   surface / text), fonts (heading / body / mono), spacing base, radii,
   max content width, breakpoints, motion and component styles — plus
   `meta.source_url` (the reference) and `meta.name`.
2. **Before ANY edit to an existing clone project, read
   `design-dna.json` first** (via `read_file`). If the file is missing,
   stop and rebuild it from the live clone before editing — never edit
   blind.
3. Apply the requested change using the recorded tokens. A change that
   names a token ("make the header blue") changes ONLY that token, in
   `design-dna.json` AND in the CSS — every other token stays byte-identical.
   A change that does not name a token (new section, new copy, new page)
   must not change ANY token: the helper's `tokensUnchanged` check
   (before/after comparison) must come back empty, or the edit is wrong and
   must be redone.
4. After the edit, re-verify in the real browser and update `NOTES.md`
   (what changed, tokens touched or "none").

## Evidence grading (WebGL / effect extraction)

Every conclusion about an effect is tagged `SOURCE` (real source /
sourcemap / runtime dump / frame capture), `PARTIAL` (names/slices,
unproven) or `GUESS` (visual fit / magic numbers). Untagged = GUESS, and a
GUESS must be upgraded to SOURCE before it is copied. Never hide a
timing/coordinate/state bug by tuning brightness, speed, position or noise;
fitted values stay GUESS with the upgrade path documented. Baseline-first
gate: build a minimal RAW REPLAY from real draw calls / shaders / uniforms,
pass frame-by-frame comparison, only then refactor.

## License (check before cloning)

| License | Allowed |
|---|---|
| MIT / Apache / BSD / Unlicense | Modify, deploy; keep attribution |
| NONE | All rights reserved by default: local learning only, credit the author, no public redeploy |
| Proprietary / forbids | Read-only study only |

"Public on GitHub" is not MIT — verify the LICENSE file + README.

## Capability boundaries

- High fidelity: static marketing pages, corporate sites, content
  React/Vue/Next, animation sites with findable source.
- Visual-only, simplified: CMS backends, complex scroll narratives,
  multi-breakpoint, WebGL/Canvas effects, third-party embeds.
- Never promised complete: login, payments, ordering, search /
  recommendation, permissions, server business logic, proprietary APIs,
  copyrighted assets — demo-grade front-end stand-ins only.
- Content remix: keep information architecture, rhythm, motion, visual
  grammar; swap copy / media / brand colour / business claims.

## Project layout

```
website-clones/<slug>/
  project.json        # CloneProject record: name, sourceUrl, createdAt, mode, complexity
  design-dna.json     # theme tokens — read before EVERY edit
  index.html          # the clone (user's own branding)
  index-original.html # read-only fetched baseline, never shipped
  NOTES.md            # complexity, mode, comparison, fidelity score, known gaps
  TEARDOWN.md         # complex sites only
  CLONE_REPORT.md     # recon compare, post-build
  CLONE_AUDIT.md      # pre-launch audit
  REPLACE_GUIDE.md    # non-trivial content replacement map
  RECON/              # recon json, screenshots, asset-manifest.json, routes.md
  assets/             # the USER's own assets for the clone
```
