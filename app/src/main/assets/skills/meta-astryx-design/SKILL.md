---
name: meta-astryx-design
description: "Meta Astryx — the user's PRIMARY design system for building apps and websites (standing order 2026-11-09). Open-source (MIT) React 19 + StyleX design system from Meta: 150+ components, 10 themes, templates, agent-ready CLI + docs. Read BEFORE building any new app UI or website; other design skills support, this one leads."
version: 1.0.0
author: User custom skill (ported from Muse)
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [design, design-system, astryx, react, ui, ux, components, themes]
    related_skills: [local-frontend-design, claude-design, popular-web-designs, frontend-design, web-design]
---

# Meta Astryx — Primary Design System

User's standing order (2026-11-09): jab bhi koi naya app ya website bane, **Astryx sab se pehle** use ho. Doosri design skills/resources (local-frontend-design, design-resources MASTER.md) supporting role mein rahen — lead Astryx kare.

## Facts (verified from the local repo, 2026-11-09)

- Meta ka internal design system, 8 saal Meta ke andar chala, 13,000+ apps; ab open source, **MIT license**.
- Repo: GitHub meta/astryx — docs site: astryx.atmeta.com. (The original skill referenced a local repo copy at `~/workspace/design-resources/astryx` on the user's dev machine; on this server use the published npm packages and the online docs, or clone meta/astryx if a local copy is needed.)
- Stack: **React 19+** + StyleX (Meta ka compile-time CSS). Pre-built CSS ship hoti hai, build plugin lazmi nahi.
- Packages (install ke waqt ki versions): `@astryxdesign/core` 0.7.12, `@astryxdesign/cli` 0.7.12, `@astryxdesign/charts`, `@astryxdesign/native` 0.1.0 (early — bharosa karne se pehle check karo).
- Core mein ~147 component folders (Button, Card, AppShell, Chat, BottomSheet, CommandPalette, DateInput, Table patterns, form wizard patterns waghera); docs site 150+ ginta hai.
- Themes (10): default, neutral, daily, butter, chocolate, matcha, stone, gothic, brutalist, y2k. Theming = CSS-variable cascade — variables badlo, saare components restyle; component code nahi badalta. Dark mode built-in.
- Status: **Beta** — versions pin karo; core bump par `astryx upgrade --from <old> --apply` codemods chalao.

## Project mein lagana (React/Next/Vite)

```bash
npm install @astryxdesign/core @astryxdesign/theme-neutral
npm install -D @astryxdesign/cli
```

App ko ek dafa Theme provider mein wrap karo:

```tsx
import {Theme} from '@astryxdesign/core/theme';
import {neutralTheme} from '@astryxdesign/theme-neutral/built';
export function Providers({children}) {
  return <Theme theme={neutralTheme}>{children}</Theme>;
}
```

Components seedha import: `import {Button} from '@astryxdesign/core/Button'` — props guess NAHI karne; pehle component doc parho (neeche).

## Kaam karne ka tareeka (agent-ready surface — yehi is ki taqat hai)

Kisi bhi component ko use/modify karne se PEHLE us ki official doc parho — hallucinated props is system mein sab se bari ghalti hai:

- CLI: project ke andar `@astryxdesign/cli` install ho to `npx astryx ...`. Agar repo clone kiya ho to repo root se `node packages/cli/clients/cli/bin/astryx.mjs` ko `astryx` samjho.
- Bootstrap (har naye project/branch par): `astryx help`, `astryx docs`, `astryx docs principles --dense` (design rules + anti-patterns + tokens), `astryx docs tokens --dense` (spacing/color/radius/typography/shadow), `astryx docs theme --dense`, `astryx component --list`, `astryx template --list`.
- Ek component ki poori detail: `astryx component <Name> --dense` (props, variants, usage, anatomy). Yehi detail har component ke `{Name}.doc.mjs` file mein bhi hai (repo clone mein `packages/core/src/<Name>/<Name>.doc.mjs`).
- Poora page chahiye to template se shuru karo: `astryx template <name>` (dashboard, settings, forms waghera) — khali screen se design invent mat karo.
- Component qareeb hai magar exact nahi: `astryx swizzle <Name>` se source eject karke project ke andar apna bana lo (code ownership allowed hai, MIT).
- Builder docs ka khazana: `packages/cli/assets/docs/` (repo clone ke andar) — offline parh sakte ho.

## Scope — kahan full, kahan sirf guidance

- **React web apps / websites (Next.js, Vite, React Native Web):** FULL use — components, themes, templates, CLI docs.
- **Plain HTML/static sites:** components nahi chalte — Astryx ke tokens/principles (spacing scale, type, color cascade, anti-patterns `docs principles` se) design guidance ke tor par use karo.
- **Native Android (Kotlin/Compose) apps:** Astryx components NAHI lagte. Wahan Astryx sirf design soch hai — tokens, spacing, component patterns ko Compose mein translate karo; asal build wahan ki apni design skills (local-app-store-design waghera) se hoga. `@astryxdesign/native` abhi 0.1.0 hai — React Native kaam ho to pehle verify karo.
- **Mau jooda projects jin ka design lock hai** (Clawora = black & white + real photos; Axion/Kodex ki apni UI zabaan): is rule se un ka design NAHI badlega — Astryx sirf naye builds ke liye hai, jab tak user khud kisi purane project ke liye order na de.

## Usool

- Pehle `astryx docs principles --dense` aur mutaliqa component docs — phir code. Props/variants kabhi andaze se nahi.
- Ek theme poori surface par; brand change chahiye to theme variables badlo, components nahi.
- Spacing Astryx ka context-aware system khud sambhalta hai — manual double-padding fixes mat ghusao.
- Beta hai: jo version test kiya, wahi pin; screenshot/geometry verification (pre-delivery gate) har build par waise hi lazmi hai jaise pehle thi.
