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

## Detailed guide (inlined — the full procedure)

<!-- 2026-11-20 (blanket audit): the text below is the cited reference file, inlined verbatim — on this phone only this SKILL.md is loaded, so a pointer to references/ would be a dead end. The same file also ships in this skill folder. -->

# GAME DEV — MASTER FOR USER
Mastered 2026-10-12 on user's order ("GitHub se seekh ke aao — pura game UI/UX, buttons, home screen, ek-ek cheez").
Standing rule: user sirf KAAM bataye (kaunsi game / kya feature); Muse isi master ke rules se banaye.
Refs cloned locally: `~/workspace/game-dev/refs/` (slether, slither-clone, slither-vs-bots, Slither.io).

## 1. LICENSE TRUTH (khud verify kiya, 2026-10-12)
- `adylevy/slither-vs-bots` — **proper LICENSE file = MIT** ✅ code reuse OK.
- `sonpython/slether`, `agreedturtle/slither-clone`, `Prathamesh-chougale-17/Slither.io` — README mein MIT declared hai lekin **LICENSE file nahi, GitHub API license=None dikhata hai**. Rule: in se mechanics/design SEEKHO, apna code khud likho; verbatim copy commercial build mein nahi.
- `ilikeeu/snake.io` / `gravityst/snake.io`, `catancats/serpent.io`, `singh-gobind/slither.io-clone` — **no license / educational-only → principles only, code copy NAHI.**

## 2. REFERENCE MAP — kis se kya seekhna hai
| Repo | Kya best hai |
|---|---|
| slether (Go + Canvas) | Circular arena, 50-bot priority AI (flee > chase > food > wander), boost = length kharch + food trail, magnetic food, spatial hash collision, 20Hz tick → 60fps interpolation, camera lerp, minimap, top-10 leaderboard |
| slither-clone (Node + Canvas) | Sab se saaf UI flow: start menu + skin picker + nickname + death/respawn screen; layered bot brain; single thick path-stroke per snake (perf trick) |
| Slither.io by Prathamesh (Next/TS, offline) | Offline-only model: 8 bots with personality (aggressiveness 0.3–1.0), 4000×4000 world, 300 orbs, death orbs 2× points |
| slither-vs-bots (React/TS, MIT file) | Saaf separation: `GameEngine / Renderer / SpatialHash`, `Menu / HUD / GameCanvas` components, difficulty levels, bot count control |
| snake.io by ilikeeu (principles) | Product scope: 43 skins + unlocks, 9 food tiers, mega-orbs, 3-tier bots, camera zoom-out, events/modes |
| snake-arena (PixiJS) | Sinusoidal wobble animation, minimap 15fps throttle, boundary warning particles |
| serpent.io (principles) | Ring-buffer trail (O(1) body), 5 bot tiers Rookie→Legend, quality tiers (Sharp/Balanced/Fast) |

## 3. CORE GAMEPLAY LOOP (.io snake — final)
1. Snake hamesha chalta hai; direction touch/joystick se; turn rate capped — bada snake = wider turn.
2. Glowing orbs khao → length + thickness + score badhta hai; head ke paas food magnetically attract hota hai.
3. **Boost** = speed burst, lekin **length/score kharch hota hai aur peeche chhote orbs ki trail girti hai**; chhota snake boost nahi kar sakta.
4. **Kill direct nahi hota** — dusre ka head tumhare body (ya border) se takraye to woh marta hai. Kill ke tareeqe: boost karke raasta kaato (cut-off), ya chhote ko gher kar circle tight karo (encircle).
5. Mara hua snake **bare high-value orbs (lagbhag 2×) mein phat'ta hai** — fresh kill lootna sab se tez growth hai; ache bots isi par swoop karte hain.
6. Arena border = death (border death par orbs nahi milte).
7. Loop: survive → grow → top-10 leaderboard → skins unlock → death screen (score/best/rank) → ek tap retry.

## 4. TECH STACK — FINAL DECISION (is workflow ke liye)
**V1 = HTML5 Canvas single-file game → chhota Java WebView wrapper APK.**
- Kyun: game ka 80% kaam UI + tuning hai; HTML mein har change browser preview mein foran dikhta hai (user ka pasandeeda preview-link tareeka); APK build proven javac+d8+aapt2 pipeline se (Gradle daemon yahan hang hota hai); sim ko rendering se alag pure-JS module mein rakho to baad mein wahi logic Node WebSocket server ban kar **online multiplayer** ban sakta hai.
- Fallback: agar stress-gate fail ho (neeche dekho) → Native Java + SurfaceView. libGDX (Gradle/Maven bojh) aur Godot (desktop editor + 25MB+ APK) is phone-first workflow mein NAHI.
- APK size estimate: 2–4 MB. Koi engine license/royalty nahi.

### Performance discipline (60fps ke ghair-mufaraq rules)
- Fixed-timestep simulation + render interpolation (sim 20–30Hz, render 60fps).
- Har snake = **ek thick polyline path** (round cap/join), per-segment circles NAHI.
- Glow = **pre-rendered offscreen sprite**; per-frame `shadowBlur` BANNED (fps killer). DPR cap 2.
- Sine wiggle: perpendicular `sin(time − distance)` offset — zinda, leharta hua snake.
- Spatial hash grid collision ke liye; viewport culling (screen se bahar wala draw nahi).
- Object pooling food/particles ke liye; minimap throttle 15fps.
- **STRESS GATE (build commitment se pehle lazmi):** ~25–30 bots, lambe snakes, full glow skins — user ke apne phone par fps check. Fail = native fallback ya quality tiers (Sharp/Balanced/Fast).

## 5. GAME UI/UX — HAR SCREEN KA FINAL SPEC

### Global rules (premium vs sasta)
- Touch: minimum hit-area **48dp**; PLAY button **72–88dp height**, center, screen width ka 65–75%, pill shape; BOOST **88–96dp circle**; joystick base 120–140dp, knob 56–64dp; buttons ke beech ≥8–12dp gap; safe-area (notch) padding lazmi.
- Thumb zone: neeche-left = movement (floating joystick — jahan thumb lage wahi base), neeche-right = boost. Upar sirf passive readout (score/leaderboard), koi action nahi.
- Colors: dark arena (`#0E0F1A` type) + **ek** neon accent + gold sirf coins/reward ke liye + white text. Har button alag rainbow = sasta.
- Button style: 2–3dp darker outline, upar halka/neeche gehra gradient, neeche 4–6dp hard shadow jo press par gayab ho.
- Font: 1 display font (rounded extra-bold — logo/PLAY/score) + 1 saaf UI font. Bas.
- Juice (har jagah): press = 95% squash, release = overshoot spring + click sound + halki haptic; popup = 0.8 scale se overshoot pop-in + peeche dim; numbers hamesha **count-up (~250ms)** + halka pop; screen shake sirf bare event par 0.1–0.3s.

### Splash/Loading
Center animated logo (snake uske gird loop), neeche patli pill loading bar + %, bilkul neeche version + rotating tip ("Boost se length kam hoti hai…"). Background = blurred arena preview. Koi button nahi; bar kabhi ruka hua nahi.

### Home Screen (sab se important)
```
Top bar:  ⚙ Settings (48dp, left)        BEST pill (center)        🪙 Coins pill + [+] (right)
Center:   LOGO (sab se bada)
          Animated snake preview — selected skin mein idle gol ghumta hua
          [ Name input pill — max 12–14 chars ]
          ◀ SKIN ▶ arrows / SKINS button (preview slide animation ke saath badle)
          [ ▶ PLAY ] — sab se bada, accent color, halka breathing glow
Bottom:   [🎯 Missions] [🏆 Leaderboard] [🎁 Daily Reward — claim ready ho to pulsing badge]
          Mode segmented: Offline / AI Bots (V1) — Online baad mein
```
Rule: PLAY hi hero hai; baaki sab ghost/outline secondary. Coins badhne par pill bounce + count-up.

### In-Game HUD (landscape)
- Top-left: minimap (90–110dp, semi-transparent) — apna dot sab se bright.
- Top-center: khaali (view block nahi).
- Top-right: leaderboard Top-10 (rank + name + length), apni row hamesha highlight, chahe rank neeche ho; saath pause button 48dp.
- Bottom-left: floating joystick. Bottom-right: BOOST circle (dabane par ring glow + particles).
- Joystick ke upar: Length pill + Kills (☠ n).
- Saare panels semi-transparent black 40–60% + rounded 12–16dp.
- Kill feedback: red edge flash + "You killed X!" floating text. Bada snake = camera aahista zoom-out.

### Death / Game-Over
Peeche game dim+blur freeze. Center card pop-in: "Killed by <name>", bare numbers **SCORE | BEST** (NEW BEST ho to gold + confetti + badge), chhoti row: Kills • Time • Rank, coins mile hon to "+45 🪙" count-up. **[PLAY AGAIN] sab se bada — ek tap seedha game mein**; [HOME] secondary; [SHARE] chhota.

### Skins Gallery
Upar badi live animated preview + naam + rarity tag (Common grey / Rare blue / Epic purple / Legendary gold). Neeche 3–4 column grid; har cell: Owned normal, Equipped = accent border + tick, Locked = halka dim (bilkul kaala NAHI — dikhna chahiye kaisa lagega) + price 🪙 ya shart ("Reach 5,000 length"). Tabs: All/Solid/Pattern/Special. Neeche sticky [EQUIP] / [BUY 🪙]. Buy par coin count-down + sparkle burst.

### Missions
Header + "Resets in 07:23:14" pill. Upar 7-day Daily Reward strip (Day 7 = bada gold glowing box; aaj wala pulsing border; claimed par tick). Quest cards: icon + text + progress bar + "3/5" + reward pill "+50 🪙"; pura hone par wahi jagah pulsing [CLAIM] — claim par coins coin-pill tak **fly-to-wallet** animation. Tabs: Daily / Weekly / Achievements.
Mission types: Play N games, Kill N, Reach length N, Survive N min, Eat N orbs, Top-10 finish, Boost kill.

### Settings (home + pause, EK hi panel)
Music slider+toggle (alag), SFX slider+toggle (alag), Vibration toggle; Control type segmented (Joystick/Floating/Follow-Finger), Left-Handed toggle (joystick/boost mirror), Joystick size slider, Boost (Hold/Double-Tap); Quality segmented (Low/Med/High — Low mein glow/particles asal mein band), Particles toggle, Snake Names toggle. Har row ≥56dp; change foran save, koi Apply nahi; pause se resume par 3-2-1 countdown.

## 6. SNAKE ANIMATION SPEC (user ki khaas farmaish — "bhar ke acha animation wala snake")
- Body: smooth spline path, sine wiggle se leharta hua; head par do aankhen jo direction dekhti hain, boost par aankhen narrow + speed lines/particles.
- Skin rendering: base color + pattern overlay (stripes/dots/galaxy) path ke saath flow karta hua; kinare par halka glow (pre-rendered sprite se).
- Growth: orb khane par head se ek halki "pulse" body mein travel kare + scale pop.
- Death: body orbs mein phat'ta hai, chhota burst + shake.
- Idle (home preview): snake gol circle mein aaram se ghumta hai — pehli nazar mein hi zinda lage.

## 7. BUILD KE RULES (user ke standing usool)
- Concept/blueprint pehle, user ki haan ke baad build. Pehle stress-gate prototype, phir full game.
- Delivery = public preview link pehle; APK usi ke baad (WebView wrapper, javac pipeline, apksigner verify).
- App UI text = FULL ENGLISH. Koi fake cheez nahi. Har claim device/preview evidence ke baad hi.


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
