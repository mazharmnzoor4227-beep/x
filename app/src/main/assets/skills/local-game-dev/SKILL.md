---
name: local-game-dev
description: "Mobile game development: .io / casual arcade games — gameplay loop, bot AI, game UI/UX for every screen (home, HUD, skins, missions, settings, game over), snake animation, HTML5 Canvas to WebView APK delivery."
version: 1.0.0
author: User custom skill (ported from Muse)
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [game, mobile-game, canvas, html5, apk, ui]
    related_skills: [local-frontend-design, local-pre-delivery-verification-gate, local-android-dev]
---

# Game Development

## Confirm the concept before building

- Read the detailed guide at `references/detailed-guide.md` before building anything, and follow its gameplay loop, UI spec, animation spec, and performance rules.

- Confirm the game concept and V1 scope with the user first, and record the agreement in a blueprint file under the user's game-dev workspace (ask the user for the project path if it is not already known).

- Run the stress gate before committing to a full build, with about 25 to 30 bots, long snakes, and full glow, and check frames per second on the user phone.

- Keep game UI text in full English, and use no fake data in any delivered surface.

## Build the game the proven way

- Build as a single file HTML5 Canvas game, and keep simulation in a pure JavaScript module separate from rendering so it can later become a server for online play.

- Use fixed timestep simulation with interpolated rendering, one thick polyline path per snake, pre rendered glow sprites, a device pixel ratio cap of 2, a spatial hash for collisions, and pooling for food and particles.

- Never use per frame shadow blur for glow, and never copy verbatim code from a reference repo that has no proper license file.

- Deliver a public preview link first, and wrap the game in the small Java WebView APK only after the user approves the preview, using the javac plus d8 plus aapt2 plus apksigner pipeline.

- Verify the preview and the device check on real evidence before claiming anything is done or premium.

## Render characters to the approved style

- Translate the approved UI direction into character art before drawing any creature, and state the rendering standard in the design before building mockups.

- Render creatures as premium glossy game art with rich shading, jewel gloss, depth, and rim light when the user approved a glossy bubble direction, and never render them as flat cartoon art.

- Apply the approved rendering consistently to every place a creature appears, including the hero, the evolution line, Habitat, and the share card, and redo every instance when the user rejects the style.

- Ask the user for a verdict on each creature separately after a revision, instead of asking for one general approval for the whole set.

- Keep the per screen UI spec from the detailed guide, with 48dp minimum targets, PLAY and BOOST as the largest controls, and count up numbers with squash stretch button juice.

## Review before delivery

- Compare the finished design against the user request item by item, and fix only what the review failed without widening the scope.

- Name anything that could not be verified as unverified, instead of folding it into a pass.

- Use the pre delivery verification gate to measure geometry and trace controls before claiming a build is done.

## Alternatives for better results

- Use the reference pattern research gate before building a pattern based site or hub, so the shortlist is approved before the build.

- Use the external builder brief when another builder will do the work, so standing rules travel with the brief.

- Use the pre delivery verification gate before claiming any build is done, so measured geometry and control wiring are checked on real evidence.
