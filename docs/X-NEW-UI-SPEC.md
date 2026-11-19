# X — New UI Spec (v1.0, 2026-11-19)

**Status:** Design spec only. No code was edited. A builder implements from this file alone.
**Replaces:** (1) the Grok-mock "sand" design (warm sand `#F3EEE6`, Fraunces serif + Outfit, fluffy 3D mascot, rounded pills) and (2) the OpenDots/OpenFuse-ported UI (light `#F8F7F4`, periwinkle `#8292D6` + teal `#35766F`, drawer + bottom sheets as primary nav). **Neither may reappear** — no warm sand/paper background, no serif display type, no periwinkle, no teal, no big mascot scene, no bottom-tab dock.
**Style reference only:** ChatGPT, Claude, Gemini, Grok, Copilot, Perplexity mobile apps — layout, composer, message and navigation *patterns*. No proprietary code, assets, logos, or fonts are copied. All tokens, values and copy below are original.

## 0. Governing decisions (non-negotiable)

1. **One chat screen does all the work.** Every agent and every task runs through chat. There is **no Build mode** and **no bottom-tab dock** (this supersedes the dock in `FEATURE-MAP.md`; the dock was deleted by the user correction in `docs/CHAT-REBUILD-CONTRACT.md`, 2026-11-17 21:28, and the Copilot app itself removed bottom tabs in Aug 2025, landing on Chat by default — same conclusion).
2. **Mascot = app logo only.** A small logo mark at the top of the chat screen (36dp, see §5). No big 3D character, no mascot screen, no mascot scene anywhere.
3. **Settings / providers live behind a minimal menu** (drawer + top-bar menu), never on the main screen.
4. **English UI only. No demo/sample content anywhere.** Every list starts empty; every function does real work.
5. **Header sits below the status bar with a clear gap** on every screen (`statusBarsPadding() + 12dp`).
6. **No green or lime anywhere** — not as accent, success colour, badge, or illustration. (This rules out ChatGPT's teal-green `#10A37F` and Perplexity's teal as accents.)
7. **Professional, clean, minimal, chat-first, clear hierarchy.** No cartoon icons, mascots-as-characters, badges, or playful illustration.

## 1. Design principles

1. **The transcript is the product.** Chrome occupies ≤15% of the viewport; hierarchy comes from type scale and tonal shifts, not boxes and borders.
2. **Subtract, don't decorate.** One accent, one border weight, one corner system. If an element doesn't help the user read, send, or approve, delete it.
3. **Assistant text is plain; user text is a bubble.** Long structured answers (code, lists, tables) need full width — never trap them in a rounded container.
4. **The composer is the real interface.** It is always visible; everything secondary collapses behind `+` or into the header/drawer.
5. **Every state is designed, not just the happy path.** Empty, queued, streaming, stopped, failed, awaiting-approval, and offline each have a specified treatment (§9).

## 2. Colour tokens

Single accent, used sparingly: **indigo**. Neutral black/white/ grey base (ChatGPT-like tonal separation, Claude-like restraint), chosen to look like neither rejected design — no warm sand, no periwinkle/teal.

### Light theme (default)

| Token | Hex | Use |
|---|---|---|
| `bg` | `#FFFFFF` | App canvas, message list |
| `surface` | `#F7F7F8` | Composer, cards, drawer, sheets |
| `surfaceRaised` | `#FFFFFF` | Cards on `surface` screens (settings rows, sheets) — 1dp border, no shadow |
| `surfaceSunken` | `#EFEFF1` | Pressed / selected row, code-block header strip |
| `text` | `#171717` | Primary text |
| `textSecondary` | `#5F5F5F` | Secondary text, timestamps |
| `muted` | `#8A8A8E` | Placeholders, captions, disabled icons |
| `border` | `#E5E5E5` | Hairlines, card outlines (1dp, never heavier) |
| `accent` | `#4F46E5` | Primary actions, send button, active states, links, selection |
| `onAccent` | `#FFFFFF` | Text/icons on accent |
| `accentSoft` | `#EDEBFE` | Accent-tinted background (selected chip, added-diff lines, listening state) |
| `danger` | `#DC2626` | Destructive actions, errors, removed-diff lines |
| `dangerSoft` | `#FEF2F2` | Removed-diff line background, error banner background |
| `userBubble` | `#F0F0F0` | User message bubble |
| `codeBg` | `#1E1E1E` | Code block background (both themes) |
| `codeText` | `#F5F5F5` | Code text (both themes) |
| `scrim` | `rgba(0,0,0,0.32)` | Drawer/sheet dim |

### Dark theme (first-class, not derived)

| Token | Hex | Use |
|---|---|---|
| `bg` | `#171717` | App canvas (never pure `#000000` — avoids halation) |
| `surface` | `#212121` | Composer, cards, drawer, sheets |
| `surfaceRaised` | `#2A2A2A` | Cards on `surface` |
| `surfaceSunken` | `#101010` | Drawer background / sunken panels |
| `text` | `#ECECEC` | Primary text |
| `textSecondary` | `#A3A3A3` | Secondary text, timestamps |
| `muted` | `#737373` | Placeholders, captions |
| `border` | `#2E2E2E` | Hairlines (`rgba(255,255,255,0.10)` equivalent) |
| `accent` | `#818CF8` | Primary actions, send, links (indigo-400 — holds contrast on dark) |
| `onAccent` | `#171717` | Text/icons on accent in dark |
| `accentSoft` | `#26264A` | Accent-tinted background in dark |
| `danger` | `#F87171` | Destructive / errors in dark |
| `dangerSoft` | `#3D2624` | Removed-diff / error background in dark |
| `userBubble` | `#2F2F2F` | User message bubble |
| `codeBg` / `codeText` | `#1E1E1E` / `#F5F5F5` | Same as light |
| `scrim` | `rgba(0,0,0,0.55)` | Drawer/sheet dim |

### Rules

- **Accent discipline:** accent is for the send button, primary CTA, active toggle, link text, and selection — never as a large background fill, never as a decorative wash. Exactly one saturated hue in the whole app.
- **Success is never green.** A passing test run, a completed step, a connected account: show a neutral check icon in `textSecondary` (or accent for the primary success) plus plain text ("12 passed · 0 failed"). No green dot, check, or pill anywhere.
- **Tonal separation over hard lines:** surfaces separate by value shift (`bg` → `surface` → `surfaceRaised`), not by heavy borders. Borders are 1dp `border` only.
- **Theme switch:** System / Light / Dark in Settings → Appearance. Follows system by default. Theme change applies instantly, no restart.
- **Contrast minimums:** body text ≥ 4.5:1, large text / icons ≥ 3:1. `#4F46E5` on `#FFFFFF` ≈ 6.0:1; `#818CF8` on `#171717` ≈ 5.5:1; `#5F5F5F` on `#FFFFFF` ≈ 7.0:1; `#A3A3A3` on `#171717` ≈ 7.0:1.

## 3. Typography (system fonts only)

No webfont downloads, no bundled display font. Android system families only:

- **UI / body:** `FontFamily.Default` (Roboto on nearly all devices; falls back to the device system sans). Never ship Fraunces/Outfit/Google Sans.
- **Code / diff / terminal / commands:** `FontFamily.Monospace` (system mono).

| Style | Size (sp) | Weight | Line height (sp) | Use |
|---|---|---|---|---|
| Display | 28 | SemiBold (600) | 34 | Empty-state title, splash wordmark |
| Title | 20 | SemiBold (600) | 26 | Subpage titles, sheet titles |
| Headline | 17 | Medium (500) | 22 | Card titles, drawer section labels, settings group titles |
| Body | 16 | Regular (400) | 24 | Assistant messages, body copy |
| BodySmall | 15 | Regular (400) | 22 | User bubble text, list rows |
| Caption | 13 | Regular (400) | 18 | Timestamps, status lines, step text |
| Label | 12 | Medium (500) | 16 | Chips, pills, section eyebrows, button labels in rows |
| Code | 13 | Regular (400) | 20 | Code blocks, diff lines, terminal, commands |

- Message text: assistant 16sp/24, user bubble 15sp/22.
- Never below 12sp for any readable text. Never all-caps except 12sp Medium eyebrows with 0.08em letter spacing (e.g. "WHILE YOU WERE OUT").
- Respect system font scaling (sp units throughout; layout must not clip at 1.3× scale).

## 4. Spacing, corners, motion

- **Spacing scale (dp):** 4, 8, 12, 16, 20, 24, 32. Page horizontal padding 16dp; message-list gap 12dp between turns, 24dp between turns from different roles after a card.
- **Corner radii:** message bubbles 20dp (with one 4dp corner — see §6); composer 28dp; cards 16dp; sheets top corners 24dp; chips/pills full-round (999dp); small buttons 12dp; icon buttons circular.
- **Touch targets:** minimum 48dp × 48dp for all tappable rows and buttons; composer icon buttons 44dp minimum (inside a 48dp hit area). Nothing tappable below 44dp.
- **Elevation:** none. No shadows on cards/composer — separation is tonal + 1dp border. Sheets/drawer use `scrim` only.
- **Motion:** 120–200ms, `FastOutSlowIn` easing, for sheet/drawer/expansion only. No bounce, spring, or playful animation. Streaming text appears token-by-token with a blinking caret; thinking dots pulse 1s staggered (0 / 0.15 / 0.3s). Respect "reduce motion" — dots become a static "Thinking…" label.
- **Icons:** 20dp line icons, 1.75dp stroke, `textSecondary` by default, `text` when active. No filled cartoon icons, no emoji as UI icons. One icon set throughout (Material Symbols Rounded — outline style — or equivalent single-family line set; do not mix families).

## 5. The single chat screen

The only home screen. Layout top → bottom:

### 5.1 Header — 56dp, below the status bar

- Top padding = `statusBarsPadding() + 12dp` on **every** screen (standing rule). Header content height 56dp.
- Left (44dp hit area): menu icon (hamburger) — opens the drawer (§7). On a subpage, a back chevron instead.
- Centre: **logo mark 36dp** (the mascot as a static logo mark — small, flat, single-colour mark in `text`/`accent`; no 3D, no animation, no eyes) + wordmark **"X"** 17sp SemiBold + status line 12sp `muted` underneath ("Ready" / "Thinking…" / current chat title, truncated to one line). Tapping the centre opens the model/provider picker sheet (§8).
- Right (44dp hit area each): **New chat** icon (square-pen) and **overflow** icon (three dots) → menu: Search in chat, Rename, Share (only when connected), Delete. No "Invite" pill (mock-only, deleted).
- Header has no divider line and no background of its own — it sits on `bg`. When the list scrolls, the header stays fixed; the composer stays fixed at the bottom.

### 5.2 Message list

- `LazyColumn`, horizontal padding 16dp, top padding 8dp, bottom padding 12dp above the composer.
- **User message:** right-aligned bubble, `userBubble` bg, `text` text, 20dp corners with **4dp bottom-right** corner, max width 80% of screen, start margin ≥ 48dp. Attachments (images/files) sit above the text in the same right-aligned column, 16dp corners.
- **Assistant message:** plain text, **no bubble**, full content width, `text` 16sp/24. No avatar. Markdown: headings 17sp SemiBold, lists with 8dp indent, links in `accent` with underline on press, inline code in `codeBg` chip (13sp mono, 6dp corners, 4dp horizontal padding).
- **Code block:** `codeBg` card, 12dp corners, header strip (filename, 12sp mono `muted`, copy button right), body 13sp mono, horizontal scroll if needed — never wrap code.
- **Run-steps / timeline (thinking + agent work):** a single collapsible block above the assistant's reply, not a separate screen:
  - Collapsed: one row — animated 3-dot indicator + status text 13sp `textSecondary` ("Reading checkout.ts…", "Running tests…"), elapsed time right-aligned, chevron to expand.
  - Expanded: step list, each row 40dp — status icon (20dp: spinner while running, check when done, danger icon on failure), step text 13sp, duration right-aligned in `muted`. Tap a step → step detail (file/line/command) opens inline below the row, never navigates away.
  - On completion the block collapses to a one-line summary ("Done in 42s · 3 steps") in `muted`.
- **Message actions (assistant only):** a 32dp-high row under each assistant message — Copy, Read aloud, Regenerate (icon + 12sp label, `muted`, 48dp hit areas). Appears on tap/long-press on mobile; always visible on the latest message.
- **Streaming:** first token within ~1s or show the queued placeholder (single shimmering line + pulsing dot — never a fake percentage bar). A **Stop** button replaces Send in the composer while streaming (§5.3); there is no separate floating stop button.
- **Error state:** inline error card under the failed turn — 16dp card, `dangerSoft` bg, `danger` icon + 13sp message stating what happened and the fix ("Connection lost. Check your provider key in Settings."), with a Retry button. Never a bare toast.
- **Interrupted-task banner:** if a previous run was interrupted, a slim banner above the composer (surface, 1dp border, 12sp): "Interrupted task: <name> — Resume / Dismiss".

### 5.3 Composer — the real interface

- Docked at the bottom, `navigationBarsPadding() + 8dp` below, 16dp side margins. One card: `surface` bg, 1dp `border`, 28dp corners, min height 56dp, grows with text to max 160dp then scrolls internally.
- Inside, top row: text field ("Message X…", 16sp, `muted` placeholder, no label). Bottom row (44dp icons): **`+`** (left) → attach sheet (§8); **mic** (dictation; `accentSoft` bg + `accent` icon while listening, with live transcription shown in the field and Confirm/Cancel — never auto-send on silence); **send** (right, 36dp circle, `accent` bg, white arrow-up icon) — disabled (40% opacity) until there is text or an attachment; while streaming it becomes a **square stop icon** on the same circle.
- **Tools row (chips) above the composer**, horizontally scrollable, 32dp high, 8dp gap: Deep research · Long task · Turbo · (provider/model chip showing the active model, e.g. "Gemini 2.5 Flash"). Chips: `surface` bg, 1dp border, 12sp Medium, full-round. Tapping a chip toggles it (active = `accentSoft` bg + `accent` text). This row is the one deliberate carry-over from the old UI — the user relies on it.
- Attachments appear as thumbnails **inside** the composer above the text field (48dp thumbs, 12dp corners, × to remove) — never as filename chips alone.
- Keyboard: composer stays above the keyboard (`imePadding()`); send on Enter; Shift+Enter newline is not applicable on mobile — the Enter key sends when the field is single-line-submitted via the IME action.
- Voice call (full voice mode) is entered from the mic long-press → VoiceCall screen; the FloatingCallOrb stays as-is.

### 5.4 Empty chat (new conversation)

- Vertically centred, not top-aligned: logo mark 48dp (`muted`, 40% opacity), "X" 28sp SemiBold, one line 15sp `textSecondary`: "Ask anything, or tell X to do something."
- Up to 4 capability chips (only capabilities that are **really installed** — derived from the live skill/tool list, e.g. "Review my code", "Plan my day"): tapping one inserts that exact text into the composer as a real user message. **No fake conversations, no fake results, no canned answers.**
- If no skills/tools are configured, show only the greeting line and a "Connect a provider" button (→ Settings → Providers).

### 5.5 Splash

- Once per app start, after onboarding: centred logo mark 72dp + "X" 28sp SemiBold on `bg`, ~700ms, tap to skip. No glow, no mascot scene, no tagline.

## 6. Secondary surfaces — how Feed / Ideas / Goals / Library / Activity / Computer / Code / Mark are reached

**No dock, no bottom tabs, no home-screen tiles.** These are stack destinations (per the Chat Rebuild Contract navigation section), reached in exactly two ways:

1. **Drawer → "Workspace" section** (§7): Feed, Ideas, Goals, Library, Activity, Computer.
2. **From chat:** the agent offers them as inline cards ("I saved 2 ideas — View" → opens Ideas), or the user asks ("show my goals").

Each opens as a **standard subpage**: back chevron + 20sp SemiBold title + `bg` canvas, same 16dp padding, same header-gap rule. Each subpage's content is a list of cards (16dp radius, `surface` bg, 1dp border) — never a separate visual language.

| Destination | Content (real data only) | Empty state |
|---|---|---|
| **Feed** | "While you were out" cards from task runs + "Needs you" cards for pending approvals, grouped by day, newest first. Each card: 12sp eyebrow (date/time), 15sp title, 14sp `textSecondary` summary, tap → opens the chat turn / approval card it refers to. | "Nothing yet — completed runs and items needing you will appear here." |
| **Ideas** | Grouped rows by area (Store / You / …), 15sp title + 13sp `textSecondary` note, tap → starts a chat with that idea. Captured from chat ("save this as an idea"). | "No ideas yet — ask X to save an idea from any chat." |
| **Goals** | Progress rows: 15sp title, thin 4dp progress track (`surfaceSunken` track, `accent` fill — never green), "2 of 4 steps · 50%" 13sp `muted`, check-in button. | "No goals yet — tell X a goal and it will track it here." |
| **Library** | File/project list: 20dp file icon, 15sp name (mono for paths), 13sp `muted` meta; tap a code file → code-diff view (§10), tap a page → viewer. Search field at top. | "No files yet — files X creates or opens will appear here." |
| **Activity** | Timestamped lines from TaskRuns + ToolLog: time 12sp mono `muted`, 14sp action text. Newest first. | "No activity yet." |
| **Computer** | One screen unifying three cards: Browser (Take control → Browser screen), Files (project file list), Terminal (PRoot shell card, mono 13sp). Label: "Workspace on this phone". | Cards show their own empty states; never hide a card for being empty. |
| **Code (diff)** | §10. Reached from Library, Feed, or a chat card — never a tab. | — |
| **Mark / mascot** | Not a screen. The logo mark lives only in the header (§5.1) and splash (§5.5). | — |

## 7. Drawer (menu) — the only navigation chrome

- Left drawer, **84% width, max 320dp**, `surface` bg, `scrim` behind. Slides 200ms. Swipe-from-left-edge opens it; scrim tap / back closes it.
- Top: logo mark 32dp + "X" 20sp SemiBold, 24dp padding. No user avatar block (no account header — the app is key-based, not account-based).
- **Search field** (48dp, `surfaceRaised`, 12dp corners, magnifier icon, "Search chats"): filters the chat list inline.
- **Chats section:** label 12sp Medium `muted` ("CHATS"), rows 56dp: 15sp title (one line, ellipsis), 13sp `muted` preview + time right-aligned; pinned chats first with a 16dp pin icon; unread dot = 8dp `accent` circle. Active chat row: `surfaceRaised` bg + 12dp corners. Long-press a row → Rename / Pin / Delete sheet.
- **Workspace section:** label "WORKSPACE", rows 48dp with 20dp icons: Feed, Ideas, Goals, Library, Activity, Computer. Each row shows a count badge only when the count is real and > 0 (badge: `accentSoft` bg, `accent` 11sp Medium text — never a red/green dot count).
- **Config section (bottom, above the footer):** label "CONFIGURE", rows 48dp: Settings, Bots, Bot Store, Skills, Memory, Connections, MCP Servers, Antigravity / CLI Pair, Approvals, Schedules, GitHub, Terminal, Browser. (Each opens its existing settings sub-route.)
- **Footer row (fixed at drawer bottom):** Settings gear (48dp circle, `surfaceRaised`), Search pill (flex, `surfaceRaised`, 12dp corners), New chat pencil (48dp circle, `surfaceRaised`).
- The drawer is the **only** place navigation lives. No bottom nav, no home tabs, no floating nav buttons.

## 8. Bottom sheets (one pattern, three uses)

- Shared shell: `surface` bg, **24dp top corners**, drag handle (32dp × 4dp, `border`, centred, 8dp below top), `scrim` behind, swipe-down or scrim-tap to dismiss, 200ms slide-up. Content max height 85% of screen; internal scroll. Title 17sp SemiBold at top-left, close (×) 44dp top-right.
- **Attach sheet** (`+`): rows 56dp with 20dp icon + 15sp label + 13sp `muted` hint: Photo, Video, File, Camera. (Voice is the mic button, not a sheet row.)
- **Model / provider picker** (tap header centre): list of configured providers and their models, each row 56dp — 15sp model name, 13sp `muted` provider + status ("Connected" in `textSecondary`, never green; "Needs key" in `danger`); active row has an `accent` check. Footer link: "Manage providers →" (Settings → Providers).
- **Tools / `+` overflow menu** (Gemini-style unified tools menu, if needed beyond the ToolsRow chips): rows 56dp, icon + 15sp name + 13sp `muted` one-line description. Everything in it must be a real, working tool.
- **Confirmation / destructive sheet** (delete chat, revoke key): title 17sp SemiBold, 14sp body, two buttons — Cancel (text button) and Delete/Revoke (`danger` text button, never a filled red block).

## 9. Cards in the message stream

All inline cards share: `surface` bg, 1dp `border`, 16dp corners, 16dp padding, full message width.

### 9.1 Approval card

- Header row: 20dp shield icon (`accent`), "Approval needed" 15sp SemiBold, timestamp 12sp `muted` right.
- Command/file shown in a mono chip: `codeBg` bg, `codeText` 13sp mono, 8dp corners, full-width, e.g. `npm test -- checkout`.
- Context line 13sp `textSecondary`: "Shell has no network. You can revoke this later." (real capability text from the gate, not invented.)
- Buttons (48dp high, full-width stacked or 3-across if ≥ 360dp wide, 8dp gap):
  - **Allow once** — filled `accent`, `onAccent` text, 15sp Medium.
  - **This chat** — outlined (1dp `border`, `text` text).
  - **Decline** — text button, `danger` text.
- After a decision: card collapses to one line — "Allowed once · 14:02" 13sp `muted` with an Undo action (5s window). Pending approvals also surface as a "Needs you" card in Feed (§6).

### 9.2 Code-diff card

- Header: mono 13sp filename (`src/checkout.ts`), right: test pill — neutral dark pill (`text` bg, `bg` text, 12sp Medium, full-round): "12 passed · 0 failed". **Never green for pass, never a green pill.**
- Diff body: 13sp mono, line numbers 12sp `muted`, 4dp row padding.
  - Removed lines: `dangerSoft` bg, `danger` `−` gutter.
  - Added lines: `accentSoft` bg, `accent` `+` gutter. (Indigo, not green — the user's no-green rule overrides the diff convention.)
  - Context lines: transparent bg, `textSecondary`.
- Footer: "Tests pass. Nothing is committed." 13sp `muted` + two buttons: **Apply** (filled `accent`) and **Discard** (text, `danger`). Applied diffs collapse to "Applied · <time>".

### 9.3 Other inline cards

- **Plan / step card:** numbered steps, 14sp, check icons in `textSecondary` when done.
- **File card:** icon + name + size, tap → Library/code view.
- **Progress / long-task card:** 4dp track (`surfaceSunken`), `accent` fill, 13sp status line, Cancel text-button.
- All cards: no coloured left border, no icon badges, no decorative header art.

## 10. Settings menu structure

Reached from drawer footer gear or drawer → Configure → Settings. Standard subpage shell (back + 20sp title). Groups in this order; every row is a real, working setting (mock-only rows — Wallet, Referral, Upgrade checkout — are **omitted**: the build has no checkout):

1. **Usage** — Token-usage bar (4dp track, `accent` fill, "10% used" 13sp `muted`); Quota.
2. **Providers** — Gemini / Grok / OpenRouter / Antigravity + local model status. Universal key box: paste key → auto-detect provider → "Active". Keys masked, stored in the secure vault, never shown in full, never logged.
3. **Connections** — GitHub / Calendar / Mail toggles (real connection state; "Off" is a legitimate state, not an error).
4. **MCP Servers** — remote server list + Add server (name + `https://` URL). Note under the list, 13sp `muted`: "Remote servers only. A local process can't start inside the phone."
5. **Skills** — real skill-pack list with the **real count** (never the mock's "56"). Each row: 15sp name + 13sp one-line contract. Toggle per pack.
6. **Memory** — editable notes (Store / Tone / …). Hint 13sp `muted`: "X writes these. You can edit them."
7. **Approvals** — Ask / This chat / Always (default: Ask).
8. **Schedules** — active schedules with real counts ("1 active").
9. **Appearance** — System / Light / Dark; (no accent-colour picker — the accent is fixed indigo by this spec).
10. **Voice & Language** — voice model, read-aloud toggle, language (English).
11. **Privacy & Security** — App lock, Secure credentials store, Data controls, Key backup.
12. **Devices & Access** — Devices, Telegram, My Server / Server guide, Antigravity (accounts, pair-code entry → CliPair screen).
13. **Tools** — Tools library (real count, e.g. "13 of 13"), Browser, Terminal, Developer tools, Turbo (server).
14. **About** — "X 1.0", "by Mazhar Ansari", Status page, Help & support, Report an issue, Legal info, App updates, Log out.

Rows: 56dp, 15sp title + 13sp `muted` subtitle, chevron or toggle right. Toggles: 52dp × 32dp switch, `accent` when on, `surfaceSunken` when off. No row navigates to a dead page — if a destination doesn't exist yet, the row doesn't exist yet.

## 11. Empty states (global rule)

- Every list (chats, Feed, Ideas, Goals, Library, Activity, skills, connections) starts **empty** — no seeded rows, no sample chats, no example results.
- Empty-state pattern (same everywhere): centred 48dp line icon in `muted`, 17sp Medium title, one 14sp `textSecondary` line saying what will appear here and how to create the first real item, one primary action button (filled `accent`, 48dp) that does the real thing ("New chat", "Connect a provider", "Save an idea"). No illustration, no mascot, no lorem text.
- Search with no results: "No results for '<query>'." — never "Try these popular searches".

## 12. Accessibility & contrast

- Minimum touch target 48dp (44dp absolute floor inside the composer); rows 56dp.
- Contrast: body ≥ 4.5:1, captions/labels ≥ 4.5:1 where they carry meaning (`muted` `#8A8A8E` on white is 3.9:1 — use it only for decorative placeholders and timestamps, never for action labels or status the user must read; readable secondary text uses `textSecondary`).
- TalkBack: every icon button has a content description ("Open menu", "New chat", "Send message", "Stop generating"); message list announces role + text; approval card announces as an alert requiring action; progress uses `stateDescription` ("50 percent").
- Focus order: header → message list → tools row → composer → drawer (when open). No focus traps in sheets; Esc/back dismisses topmost layer first.
- Dynamic type: layout survives 1.3× font scale without clipping (composer grows, rows wrap to two lines, drawer list scrolls).
- Reduced motion: disable dot pulse, bob, and slide animations; state changes are instant.
- Colour is never the only signal: approval uses icon + text, errors use icon + text, selected states use icon + text + tonal change.

## 13. What this spec deliberately does NOT include

- No dock / bottom tabs / home screen (deleted per contract).
- No Build mode, no Build chips, no "Build an app" suggestion.
- No mascot screen, no 3D mascot, no mascot animation beyond the static logo mark (idle bob / blink / drag-rotate behaviours from the old mock are deleted with it).
- No serif display font, no warm sand/paper palette, no periwinkle/teal.
- No green/lime in any role, including success, "connected", "online", diff-added, and progress.
- No demo data, no canned answers, no fake counts (skills count, token usage, schedule counts are all read from the real stores).
- No webfont downloads (system fonts only), no new native dependencies.

## 14. Implementation notes for the builder

- Theme: single `XTheme` (light/dark token tables in §2) in Compose `MaterialTheme` — colour, typography (§3), shapes (§4). Delete the Sand palette and the Fraunces/Outfit font wiring; do not keep them as an unused theme.
- Navigation: `NavHost` starting at `Route.Chat`; stack destinations per the Chat Rebuild Contract (Settings + sub-routes, Bots, BotStore, Browser, Terminal, VoiceCall, CliPair, Connectors, Skills, Memory, Approvals, Schedules, GitHub, Telegram, Antigravity, Quota, Turbo, ToolsLibrary, Storage, Data, DevTools, KeyBackup, LocalModels, MyServer, ServerGuide, Appearance, Language, Advanced, Customize, CustomConnector, About, Files). Feed/Ideas/Goals/Library/Activity/Computer are stack destinations too — reached from the drawer or chat, never from a dock.
- Reuse the real `ChatViewModel` (`messages`, `send()`, `running`, `draft`) and existing `MessageViews`/`Composer` wiring, restyled to §§5–6. Empty chat starts empty.
- Verify with the pre-delivery gate: header gap below the status bar (screenshot), 48dp targets, contrast spot-checks, empty-state honesty (fresh install shows zero seeded rows), no green pixel in the palette (grep the theme for `0xFF` greens / `#10A37F` / lime values).
- Sources consulted (style reference only): ChatGPT/Claude/Gemini/Grok/Perplexity/Copilot mobile UI patterns — single-column transcript, right-aligned tinted user bubble, plain-text assistant, pill composer with `+` / mic / send-stop, header model picker, slide-out drawer for history, bottom sheets for tools/attachments, inline approval and diff cards. Exact proprietary hex values and assets were not copied; tokens in §2 are original choices.
