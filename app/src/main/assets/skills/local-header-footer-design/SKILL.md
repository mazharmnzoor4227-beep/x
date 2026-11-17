---
name: local-header-footer-design
description: Header, footer, text, icons and animation detailing for every app and website build - use on every build.
---

# Local Header Footer Design

Header, footer, text, icons and animation detailing for every app and website build. Use on every build — even when the prompt does not mention the header or footer. A build is not done until every rule below passes on a real mobile viewport (~390px) and a desktop viewport.

## Header — the first rule

- The header NEVER touches the phone status bar (date, time, battery, signal). It always sits BELOW it with a clear gap.
- Apps: use window insets / `fitsSystemWindows` top padding. Web: `env(safe-area-inset-top)` plus top padding, so the header clears the status bar on every phone.
- The header is always fully visible — never clipped at the top, never half-hidden, never sliding under the browser chrome or another bar.
- Title, logo, actions and hamburger must all fit inside the header on mobile. If they do not fit, shorten or move them — never let them overflow or overlap.
- A sticky header keeps its full height while scrolling. Content scrolls UNDER it only with enough top padding that nothing hides behind it.

## Footer — complete, never an afterthought

- Every website and app screen that has a footer gets a COMPLETE footer: all links, text and buttons visible, proper spacing, nothing clipped at the bottom.
- On mobile the footer stacks cleanly in one column. Nothing overflows sideways.
- The footer never overlaps content and content never hides behind it. Bottom padding on the page must clear a fixed footer.

## Text — readable, never layout-breaking

- Long text must NEVER break the header or the layout. Truncate with an ellipsis where space is fixed, or wrap cleanly where space can grow.
- No text is ever clipped mid-word at an edge, and no text pushes cards, pills or the header out of place.
- Headings, labels and button text stay on one or two clean lines on mobile. If a label is long, shorten the label — do not let it stretch the layout.
- Keep one type scale for the whole surface. Contrast 4.5 to 1 or better for normal text.

## Icons — one set, proper size

- Use plain stroke SVG icons from one consistent set. Never use emojis or coloured glyph tiles as UI icons.
- Icons are sized to their context (navigation, buttons, status) and vertically aligned with their label. No oversized, cropped or half-visible icons.
- Status icons (warning, low stock, info) are small, calm and professional — never a big shouting graphic.

## Badges and pills — professional, never cartoon

- No big round cartoon circles with mixed loud colours (for example red text inside a circle plus yellow text next to it). Badges and pills are flat, quiet and consistent: one background, one text colour, readable at a glance.
- Every badge in the app uses the same pill style. Status pills (low stock, paid, pending, udhaar) differ by colour role only, never by shape or size.

## Cards and boxes — aligned

- Cards in a row align evenly: same top edge, consistent heights and gaps. No card sits higher or lower than its neighbours.
- Use CSS grid for multi-column structures. Do not rely on flex percentage math that drifts.
- Keep every element complete inside the canvas, with nothing cropped at the edges.

## Animation — subtle, never the point

- Animate only `transform` and `opacity`. Respect `prefers-reduced-motion`.
- Motion explains a state change (open, close, loading, success). It never distracts from content and never moves layout around.
- Reserve space for content that loads later so the layout does not jump.

## Layouts — mobile and desktop are separate, always

- EVERY website and app ships with a separate mobile layout AND a separate desktop layout. This is mandatory, not optional.
- Mobile: single column, hamburger menu or bottom navigation, touch-friendly targets, ZERO horizontal overflow (`document.documentElement.scrollWidth` must equal `window.innerWidth`).
- Desktop: sidebar or multi-column layout with the same content and the same design language.
- Never ship a desktop layout squeezed onto a phone, and never ship a phone layout stretched across a desktop.

## Verify before delivery

- Open every screen in a real mobile viewport (~390px wide) and a desktop viewport before calling the build done.
- On every screen check: header fully visible below the status bar, footer complete, no horizontal overflow, no clipped text, cards aligned, badges professional.
- If any check fails, fix the layout and re-check. A screenshot that looks right on desktop proves nothing about mobile.
