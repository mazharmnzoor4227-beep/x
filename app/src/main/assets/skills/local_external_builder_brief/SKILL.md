---
name: local_external_builder_brief
description: Brief another agent or session to build a website or app so standing rules, separate pages and an explain-then-preview gate travel with the brief.
version: 1.0.0
author: User custom skill (ported from Muse)
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [brief, delegation, agent, build, spec, preview]
    related_skills: [local-frontend-design, local-pre-delivery-verification-gate, local_confirm_surface_before_building]
---

# External builder brief

## Copy the standing rules into the brief

- Open memory and standing notes for the build before writing any brief and list every rule that constrains this build, including visual style, product imagery, page structure, cart behaviour and delivery terms.

- Write each rule into the brief in plain explicit words, because another agent or session does not inherit this session memory and will fill a silent gap with its own default.

- Mark every undecided item as to decide in the brief and forbid the builder from inventing a brand name, a price, a delivery fee, a free delivery limit or a story for it.

- Flag any conflict between sources in the brief before building, such as two different colour palettes or two different animation systems, and ask the user to lock one choice before the builder starts.

- State that patterns may be borrowed from reference work but code, copy, logos and branding from a reference must not be copied.

## Require an explanation and a small preview first

- Require the builder to explain the full design section by section in simple language before building anything, covering homepage, header, categories, product pages, cart, checkout and footer in that order.

- Require only a small homepage preview after the explanation, with the hero, category tiles and two or three cards in the approved colours and fonts, and stop the builder from building the full website at that stage.

- Send the explanation and preview to the user for approval and do not authorise a full build until the user approves them or corrects them.

## Check the returned build against the brief

- Check the returned build item by item against the brief before presenting it as a website, including one separate page or route for each category and each product page the brief promised.

- Inspect every icon in the build and reject any drawn, coloured or cartoon style icon where the brief required real photographs and thin professional icons only.

- Test the cart flow from the brief, including variant validation, the fly to cart animation and the rule that the drawer opens only on a cart icon tap.

- Name anything that could not be checked as unverified instead of folding it into a pass, and fix only the failed items without widening the build scope.

## Alternatives for better results

- Ask the user to approve the brief itself before it leaves this session when the brief carries a new visual direction or a new store structure.

- Run the pre delivery verification gate over a returned build before claiming it is done, and use the local frontend design skill for the visual direction inside the build.
