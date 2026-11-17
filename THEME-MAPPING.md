# THEME-MAPPING — Grok "Sand" tokens → X Compose theme

Source of truth: `~/workspace/grok-workspace/src/styles.css` (`@theme` + `.theme-dark`).
Android side: `app/src/main/java/com/mazharansari/axion/ui/theme/SandTheme.kt`
(new file, additive). The live theme is still `Theme.kt` (`AxionTheme`,
`DarkPalette` / `LightPalette`) — nothing is re-skinned yet.

## Token table

| Grok token | Light | Dark | Palette field(s) |
|---|---|---|---|
| sand | #F3EEE6 | #1C1C1E | `bg` |
| sand-deep | #E7E0D4 | #111113 | `page2`, `code` |
| paper | #FFFCF8 | #2A2A2C | `surface`, `card`, `sheet`, `popover` |
| ink | #1C1916 | #F4F1EC | `text` |
| muted | #746C63 | #A8A29B | `text2`, `text3`, `dim` |
| line | #E4DDD2 | #3A3A3C | `border`, `cardBorder` |
| sky | #3C8FD4 | #3C8FD4 (inherited — dark block does not override) | `accent`, `blue` |
| on-sky | #F5F9FD | #F5F9FD (inherited) | `onAccent` |
| sky-soft | #D7EBFA | #1D3C52 | `chip` |
| bubble | #E8E3DA | #323234 | `surface2` |
| stop | #9C4038 | #9C4038 (inherited) | `danger` |
| stop-soft | #F3E4E1 | #3D2624 | unused (danger tint if needed) |

## Fonts

| Grok | Android status |
|---|---|
| Fraunces (display serif) | NOT bundled — needs OFL files in `res/font/` + a `FontFamily` in theme |
| Outfit (sans) | NOT bundled — same as above |
| Current: Google Sans Flex / Google Sans Code | Stays until Fraunces + Outfit land; `SandTheme` only changes colours |

## What changes when the re-skin switches on

1. `AppRoot` (or wherever `AxionTheme` is applied) calls `SandTheme(look)` instead.
   `SandLook` mirrors the Grok `Look` pref: System / Light / Dark.
2. Screens are re-skinned one at a time against FEATURE-MAP.md — the old
   dark ink + indigo-violet accent (#6C63FF) is replaced by sand + sky
   (#3C8FD4) per screen, not in one blind pass.
3. Shape language from the reference: fully-rounded pills (`rounded-full`
   dock/composer/buttons), 24px cards (`rounded-3xl`), 16px rows
   (`rounded-2xl`). Compose equivalents: `RoundedCornerShape(24.dp)` cards,
   `CircleShape` dock/composer.

## Not in the tokens (needs design decisions, not code yet)

- **Mascot:** the reference renders a fluffy 3D mascot from
  `grok-workspace/public/mascot.glb` (1.1 MB, `FluffyMascot.jsx` + `agent-mark.tsx`).
  Android has no GLB renderer in the donor — port needs a decision (Filament /
  SceneView / static fallback). Donor equivalent is the 2D `ThinkingOrb`.
- **Splash:** 700 ms auto-advance, tap-to-skip, big AgentMark + "Axion" wordmark.
- **Status-bar / header gap:** the standing header-gap rule still applies —
  every screen's header sits below the status bar (see AGENTS.md).
