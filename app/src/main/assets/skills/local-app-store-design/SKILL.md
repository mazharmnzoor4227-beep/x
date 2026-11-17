---
name: local-app-store-design
description: "Design spec for app-store / catalog apps (Android, programmatic or Compose UI): professional header (search-as-header), Material 3 bottom navigation, type scale, icon sizes, spacing, and category sections."
version: 1.0.0
author: User custom skill (ported from Muse)
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [design, android, app-store, material-3, ui, navigation]
    related_skills: [local-frontend-design, local-android-dev, design-mobile-apps]
---

# App Store Design

Full research with sources: [SPEC.md](SPEC.md) in this folder. This file is the
working distillation — apply it directly; open SPEC.md when a number needs its
source or a trade-off needs its reasoning.

## The header rule (no brand strip)

Never ship a header that is a logo tile + store name + a row of action icons.
That strip is the #1 "AI-generated template" tell. Instead:

- **Search-as-header (Play pattern):** one row, 16dp side padding — a
  full-width **search pill** (52dp high, full radius, tonal fill, search glyph
  20–24dp, hint at 14–15sp muted) that opens the Search screen, plus **one**
  trailing element: the **profile avatar (36–40dp circle)**.
- The screen's large title lives in the content below (Apple pattern), not in
  the header bar.
- Any action evicted from the header must be **relocated, never deleted**:
  duplicates of nav destinations are dropped; refresh goes to the Updates
  screen; downloads/manager goes to Settings or the account sheet. Write the
  relocation map into your delivery report.

## Bottom navigation (M3 Navigation Bar, exact)

- Container **80dp** (+ safe-area inset), color = tonal surface
  (`surfaceContainer`), hairline on top is fine.
- Active indicator: **pill 64×32dp**, fully rounded, `secondaryContainer`
  color, centered behind the icon.
- Icon **24dp** (filled when active, outlined when not). Label **12sp
  Medium** (semibold when active), **4dp** below the pill.
- Inactive icon/label = `onSurfaceVariant` (muted). Active = accent family.
- Badge on an icon: dot 6dp, or number badge min-height **16dp**, 11sp text,
  **error color** (#BA1A1A light / #FFB4AB dark), at the pill's top-end.
- 3–5 destinations, equal width. No top-line indicators, no floating glass
  pills, no 11sp labels.

## Typography (Inter, M3 scale)

| Role | Size / weight |
|---|---|
| Screen large title | 28sp SemiBold |
| Screen subtitle | 14sp Regular, muted |
| Section header | 17sp SemiBold |
| Row title | 15sp Medium–SemiBold |
| Row meta | 12–13sp Regular, muted |
| Nav label | 12sp Medium |
| Chip label | 14sp Medium |
| Button | 14sp SemiBold |
| Search text/hint | 15–16sp Regular |
| Badge | 11sp SemiBold |

Only these steps. If you reach for 21sp titles or 11sp nav labels, stop.

## Icon sizes

- Nav 24dp · header actions 24dp in 48dp targets · avatar 36–40dp ·
  chip glyph 18dp · list-row app artwork **56–64dp** · shelf tiles **88dp+** ·
  detail header artwork **96–120dp**.
- Artwork corner radius = **22% of size** (Apple's ≈22.37% squircle).
- **The tile IS the artwork:** full-bleed, no small icon floating in a big
  empty tile; letter fallbacks fill the whole tile at tile scale.

## Spacing

4dp grid · screen padding **16dp** · title→subtitle 4dp · subtitle→content
12–16dp · section gap 22–24dp · section header→content 10–12dp · list rows
12dp vertical padding · chips separated by 8dp.

## Categories

Horizontally scrollable **chip row** (Play pattern): visual height **36dp**,
full-pill radius, label 14sp Medium, glyph 18dp, 8dp gaps. Selected = solid
active fill + on-color text; unselected = transparent + 1dp outline.
(M3 chip tokens are 32dp/8dp-corner — the pill shape is a documented Play-store
deviation; touch row stays ≥48dp.) If categories outgrow chips (>~12 and a
dedicated screen exists), F-Droid-style icon-tile list rows are the fallback.

## Colors (role mapping, any palette)

Map your palette onto M3 roles before styling: nav container =
`surfaceContainer` (one step off the page bg), nav pill =
`secondaryContainer` tonal version of the accent, active nav content =
accent, inactive = muted, search pill = `surfaceContainerHigh`, badge =
error. Keep light/dark pairs for every role.

## Motion

Nav pill/label cross-fade 150–200ms, standard easing (0.4, 0, 0.2, 1).
Screen content fade + ≤12dp rise, 220–280ms. No bouncing indicators, no
glass/blur theatrics, respect system animator-off settings.

## Do / Don't

**Do:** search-as-header · avatar as the only header action · M3 nav pill ·
28sp screen titles · icons that fill their tiles · relocate evicted actions ·
verify every number against SPEC.md.
**Don't:** brand strips with icon clusters · top-line nav indicators ·
floating glass nav · 21sp titles · small icons in big empty tiles · deleting
a function because its header icon was removed · inventing sizes not in the
table.

## APK STORE application (this user's app)

APK STORE (Android, programmatic Views, Inter bundled) applies this spec as:
search pill + avatar header; bell dropped (Updates is a nav tab), refresh
moved to the Updates screen header, Downloads via Settings → Storage →
Downloaded APKs + profile sheet. Nav = 5 tabs (Apps, Search, Updates,
Favorites, Settings) on the M3 spec above with the app's panel tokens
(light #FFFFFF/#F5F5F5, dark #000000/#141414, accent blue #2563EB/#60A5FA,
pill #DBEAFE/#17293F, badge = M3 error). Applied first in v1.1.36 — see
SPEC.md §7–§8 for the relocation map and color table.
