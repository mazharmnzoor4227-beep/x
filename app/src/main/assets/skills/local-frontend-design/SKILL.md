---
name: local-frontend-design
description: Design, build and review web frontends within the existing brand, covering layout, typography, colour, interaction states, design tokens and accessibility.
version: 1.0.0
author: User custom skill (ported from Muse)
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [design, frontend, web, ui, ux, css, accessibility, tokens]
    related_skills: [meta-astryx-design, claude-design, frontend-design, web-design, local-pre-delivery-verification-gate]
---

# Local Frontend Design

## Start from the existing product

- Read the existing product, its brand, its design system and its framework before proposing any visual direction.

- Treat the existing brand, typography and layout conventions as the default, and change them only where the user asked for a change.

- State the design direction in one short paragraph before building, naming the layout pattern, the type choices and the single accent colour.

- Check `package.json` before importing any library, and check the installed framework and CSS versions before using their syntax.

## Design within the brand

- Keep one palette for the whole surface, with neutral bases and one accent colour, and keep contrast at 4.5 to 1 or better for normal text.

- Use the product font the brand already uses, and build hierarchy with weight, size and spacing rather than swapping in a fashionable font.

- Prefer restrained, static layouts by default, and add motion only where the user asked for it or where it explains a state change.

- Use plain stroke SVG icons from one consistent set, and never use emojis or coloured glyph tiles as UI icons.

- Keep every element complete inside the canvas, with nothing cropped at the edges and no horizontal overflow at mobile widths.

## Build the system

- Define design tokens for colour, spacing, radius and type before building components, and reuse the tokens instead of one off values.

- Build every interactive element through its full cycle, covering default, hover, focus, active, loading, empty, error and disabled states.

- Place a visible label above each form input, helper text under it when needed, and the error message directly under the field it belongs to.

- Use CSS grid for multi column structures instead of flex percentage math, and use `min-h-[100dvh]` instead of `h-screen` for full height sections.

- Animate only `transform` and `opacity`, respect `prefers-reduced-motion`, and reserve space for content that loads later so the layout does not jump.

- When Tailwind CSS is the stack, check whether the project uses version 3 or version 4 before writing config or syntax, express variants through the token system, and cover dark mode through colour roles rather than separate one off styles.

## Review before delivery

- Review the changed files against accessibility, touch and layout rules, and report findings in terse file and line form with the fix for each one.

- Check touch targets of at least 44 by 44 px, visible focus states, alt text on meaningful images, and labels on icon only buttons.

- Verify the result at real mobile widths such as 360, 390 and 412 px, and name anything that could not be verified instead of folding it into a pass.

- Compare the finished surface against the user request item by item, and fix only what the review failed, without widening the scope.

## Alternatives for better results

- Reuse a component or pattern that already exists in the product before designing a new one, so the new work looks like it was always part of the product.

- Ask the user to pick between two small preview variants when a visual direction is genuinely open, instead of committing the whole build to one guess.

- Use the dedicated app store design skill for store and catalog apps, and the pre delivery verification gate before claiming any build is done.
