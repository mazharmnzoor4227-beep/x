# X Android App — Redesign Round 2: Build Spec (2026-11-19)

**Status:** Build spec only. No code was edited to produce it. A builder implements from this file + `docs/X-REDESIGN2-AUDIT-2026-11-19.md` alone.
**Replaces:** (1) the Axion one-chat-screen + hamburger-drawer product (mascot logo, "Axion" copy, indigo ChatGPT-like round-1 skin) and (2) round 1's `docs/X-NEW-UI-SPEC.md` in full. Round 1's spec is **superseded, not amended** — do not mix its tokens, radii, or drawer into this build.
**Governing user demands (all six must be verifiably met):** completely different UI; simplified menu (compact settings block + full chat history, workspace screens out of the menu); WhatsApp-style Bots section at the top with per-bot chats inside it; token display removed from chat; cartoon logo removed (professional monogram avatars); one-chat-screen rule replaced by home-list + per-bot chats.
**Standing rules (unchanged):** NO green anywhere, in any role. English UI only. No demo/sample content — every list starts empty, every function does real work. Header sits below the status bar with a clear gap (`statusBarsPadding() + 12dp`) on every screen. No new native dependencies; no webfont downloads.

## 0.0 Differentiation from every previous design (Addendum 3, 2026-11-19)

**Normative (user order): the new UI must not match Axion, and must not match ANY theme or UI design given before — not the Grok mock, not the OpenDots/OpenFuse port, not the round-1 big-brand redesign, not any earlier theme in the codebase.** The table below is the check the verifier runs per design, on layout, colour, type, shapes, and navigation together. No single screen may share its layout + colour + type + shape combination with any row.

| Previous design | What it was (layout / colour / type / shapes / nav) | How Ink Ledger differs on EVERY axis |
|---|---|---|
| **Axion donor UI** (Axion v0.9.6, the code this app was built from) | Light `#F8F7F4`, periwinkle `#8292D6` + teal `#35766F` accents; chat-first single screen; hamburger drawer as the only navigation; fluffy 3D mascot as the logo at the top of the chat; centred brand header; rounded cards/pills | Dark-default ink `#14161A`, single bronze `#E0A63F`; **no periwinkle, no teal, no light canvas**; list-first Home (settings block + Bots + chats); **no drawer at all**; wordmark "X▪" instead of the mascot; left-aligned headers; rectilinear 4–12dp shapes |
| **Grok mock** (19-screen web prototype, `docs/CHAT-REBUILD-CONTRACT.md` tokens) | Warm sand `#F3EEE6`, sand-deep `#E7E0D4`, paper `#FFFCF8`, ink `#1C1916` (dark: sand `#1C1C1E`, paper `#2A2A2C`); Fraunces serif display + Outfit sans; fluffy 3D mascot; rounded pills; 84%-wide sand drawer; chat-first | **No warm sand/paper tones** — cool ink `#14161A` / `#1D2026`; **no serif, no Outfit** — system `FontFamily.Default` + bundled mono only; **no mascot**; no pills (max 12dp radii, 6dp chips); **no drawer**; list-first |
| **OpenDots / OpenFuse port** | Light `#F8F7F4`, periwinkle `#8292D6` + teal `#35766F`; drawer + bottom sheets as primary navigation; chat-first | Dark ink default, bronze only; **no periwinkle/teal, no light canvas**; **no drawer**; bottom sheets exist only for the single Workspace sheet and pickers (not primary nav); list-first Home |
| **Round-1 big-brand redesign** (`docs/X-NEW-UI-SPEC.md`: ChatGPT/Claude/Gemini/Grok-like) | Light default `#FFFFFF`, indigo `#4F46E5` / `#818CF8`; 20–28dp pill composer, full-round (20dp) chips, 20dp user bubbles; chat-first one-chat-screen home; drawer nav; centred mascot header (36dp logo + "X" + status) | **Dark default** `#14161A`; **bronze `#E0A63F` / `#92400E`, no indigo**; composer 12dp, chips 6dp, bubbles 10dp; **list-first Home, no composer on Home**; **no drawer**; back-chevron header, wordmark, monogram tiles |
| **Earlier themes in the codebase** | SandTheme (sand light `#F3EEE6` / `#FFFCF8`, sand dark `#1C1C1E` / `#2A2A2C`, Fraunces + Outfit — documented in `docs/CHAT-REBUILD-CONTRACT.md`; `SandTheme.kt` is no longer in the tree) and DotsTheme (light periwinkle/teal, deleted in round 1) | Ink Ledger reuses **none** of their tokens: no sand `#F3EEE6` / `#E7E0D4`, no paper `#FFFCF8`, no periwinkle, no teal, no Fraunces/Outfit, no mascot, no drawer |

**Collision check (done 2026-11-19, recorded here so the verifier does not have to re-derive it):** the only traits Ink Ledger shares with any prior design are generic ones — a dark neutral background also appears in the Grok-mock dark theme (`#1C1C1E`) and the round-1 dark theme (`#171717`), and bottom sheets also appear in the Dots port. Neither is a differentiator on its own: Ink Ledger's background `#14161A` is a cooler ink than either, and the design is identified by the *combination* (bronze-only accent + hairline-ruled ledger rows + 11sp uppercase eyebrows + 12sp mono metadata + list-first Home + no drawer + wordmark + monogram tiles). If any built screen still reads as one of the rows above, the build has failed this section even if every other checklist item passes.

## 0. The new paradigm in one paragraph

**List-first ledger, not chat-first clone.** The app opens on a **home list** (like a messenger: settings block, Bots, then every chat), never on an empty chat. The visual language is a **rectilinear ledger**: square-ish corners, hairline rules, monospace metadata, one bronze accent on an ink-dark default theme. Navigation is **stack + list**, not a drawer: there is no drawer at all. A bot is a **contact with its own thread**: tapping it opens that bot's chat inside the Bots section, and back returns to the bot list. This differs from Axion (chat-first + drawer + mascot) and from round 1 (ChatGPT-like light/indigo/pill chat-first) on every axis at once — structure, theme, shape, and type — so no single screen can be mistaken for either.

## 1. Layout paradigm, screen map, navigation graph

### 1.1 Screen map (all screens; `*` = new, `~` = heavily reworked, `=` = kept as-is)

| Screen | Route | What it is |
|---|---|---|
| **Home (list)** `*` | `Route.Home` (new start) | Settings block + Bots section + Chats section. The app opens here. No composer, no transcript. |
| **Chat** `~` | `Route.Chat` (existing) | The existing transcript + composer, restyled (§2) and re-headed: back chevron → Home, no hamburger, no drawer. Opens a plain conversation. |
| **Bot chat** `*` | `Route.BotChat(botId: String)` (new) | Dedicated chat with one bot, rendered by the same transcript/composer as Chat but with a bot header. Lives **inside the Bots section**: back → Home (Bots section). Never opens a home/new chat. |
| **Bot Store** `=` | `Route.BotStore` | Unchanged, except bot rows use monogram tiles (§3.5) and the "find it in Bots" toast still points at the Home Bots section. |
| **Workspace sheet** `*` | (bottom sheet, not a route) | The single discreet home for the 7 workspace screens. Opened only from the Home top bar (§1.3). |
| Feed / Ideas / Goals / Library / Activity / Computer / CodeDiff `~` | existing routes | Unchanged content/stores; restyled to §2 tokens/shapes; reached only via the Workspace sheet or in-chat deep links (§1.3). |
| **Settings** `~` | `Route.Settings` + sub-routes | Same screens/functions, reworded (§3.6) and restyled (§2). Reached from the Home settings block only. All sub-routes unchanged. |
| Splash / Intro `~` | (existing flow) | Wordmark only, no mascot (§3.5). |

### 1.2 Navigation graph

```
AppRoot stack starts at Route.Home
  Home ──tap chat row──▶ Chat (plain conversation) ──back──▶ Home
  Home ──tap bot row───▶ BotChat(botId) ──back──▶ Home (Bots section visible)
  Home ──gear──────────▶ Settings ──back──▶ Home
  Home ──grid button───▶ Workspace sheet ──tap──▶ Feed/Ideas/Goals/Library/Activity/Computer
                                                              │ (CodeDiff via chat card / Library only)
                                                              └─back──▶ Home
  BotChat ──overflow──▶ (Rename thread / Delete thread / Bot details → Bot Store row)
  Chat ──in-chat card─▶ Feed/Ideas/Goals/Library/CodeDiff (existing deep links, kept)
  Bot Store ──Install──▶ stays on Bot Store (toast: "Installed — find it in Bots on Home")
```

**Back-stack rules (normative — the verifier tests each):**
1. Back from **Chat** → Home. The open conversation is saved; nothing is discarded.
2. Back from **BotChat** → Home with the Bots section in view. Back never opens a new chat, never lands on `Route.Chat`, never opens the plain chat screen.
3. Back from **Settings / workspace screens** → Home.
4. Back from the **Workspace sheet** → dismisses the sheet, stays on Home.
5. System back and the in-screen back chevron behave identically on every screen.
6. The **drawer is deleted** — no hamburger, no slide-in panel, no scrim, no `MenuDrawer`. Any code path that opens it is removed, not hidden.
7. `vm.debugStart` / deep-link nav in `AppRoot` must be updated to the new start route (Home), or the app will open on a chat after a debug trigger.

### 1.3 Where the 7 workspace screens live now (exact answer to demand 2)

- **One place:** a **Workspace button** in the Home top bar — a 40dp grid icon (`Gk.grid`), right-aligned in the settings-block row (§1.4). Tapping it opens a **bottom sheet** ("Workspace", §2 sheet pattern) listing exactly 6 rows: Feed, Ideas, Goals, Library, Activity, Computer. CodeDiff is **not** listed (it is per-project; reached only from a chat review card or from Library, as today). Each row: 20dp line icon, 15sp name, 13sp one-line real subtitle (e.g. Feed: "2 need you"; Ideas: "4 saved"; empty states show no count, never "0"). Tapping a row navigates to that screen; back returns to Home.
- **Second path (kept, not a menu):** in-chat deep links as today (agent cards, "show my goals", review card → CodeDiff). These are not navigation chrome.
- **Not in:** the Home list, the settings block, the chat header, the composer, or any drawer (there is none). If the builder cannot find a destination from Home in two taps, the spec is not met.

### 1.4 Home (list) layout — top to bottom, exact

```
┌─────────────────────────────────────────────┐
│ status bar gap (12dp)                       │
│ [monogram 40] You / name          [grid][gear] │  ← settings block, 64dp row
│                 Ready · Gemini connected      │
│ [ Search chats .................... ]       │  ← 48dp, 8dp corners (filters both lists)
│ BOTS                              [+ New bot]│  ← 11sp eyebrow + text button
│ [M] Coder        Last message…      2:45 PM  │
│ [M] Researcher   …                          │
│ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ │
│ CHATS                             [New chat] │
│ [M] Mirchi rates…  …               Yesterday │
│ [ ] Weekend plan…  …                12 Nov  │
│ … (full history, pinned first)              │
│ (empty: "No chats yet — start one…")        │
└─────────────────────────────────────────────┘
```

- **Settings block (64dp row, the "small compact settings block"):** left — user monogram tile 40dp (initial of `settings.userName`, fallback "Y" for "You", §2.5 tile rules); centre — two lines: name 15sp SemiBold (`userName` or "You"), status 12sp mono `muted` ("Ready · <active provider or 'No key yet'>", real state from the connectors list — never a fake "online"); right — Workspace grid button 40dp, then Settings gear 40dp. Tapping the name/monogram opens Settings → Customize (profile), exactly as the old profile card did. No avatar photo, no big profile card, no providers banner card.
- **Search:** one 48dp field, filters Bots + Chats inline by name/preview. No separate search screen.
- **BOTS section:** eyebrow "BOTS" + right-aligned "New bot" text button (opens the existing `CreateBotCard` as a bottom sheet, §3.3). Rows are WhatsApp-style: monogram tile 44dp (§2.5), name 15sp SemiBold, preview 13sp `textSecondary` (last message of that bot's thread, or the bot description when no thread exists yet), time 12sp mono right-aligned (`chatTimeLabel`, existing `ChatList.kt`), unread/working dot 8dp accent when its run is live. **Every installed AND every created bot is listed** (source: `botStore.definitions` — the same flow as today). A bot with no thread yet shows its description as preview and "New" in mono muted; tapping it starts its first thread (§3.3). **Running swarm** (live parents/workers) appears as a "RUNNING NOW" group at the top of the Bots section *only while runs are live* (existing `runs.active` + `ParentCard`/`WorkerCard` content, restyled as rows); tapping a worker opens its transcript exactly as today (`openWorkerChat`, back = "Back to parent chat"). Finished workers stay reachable from Activity, as today.
- **CHATS section:** eyebrow "CHATS" + right-aligned "New chat" text button (→ blank Chat, `vm.newChat()`). Rows from `ConversationStore.index` via existing `buildChatRows` (pinned first, newest first, worker transcripts excluded, bot chats show the bot's name + monogram). Long-press a row → Rename / Pin / Delete / Share sheet (same actions as the old drawer rows). Empty state: centred 15sp text "No chats yet — tap New chat, or open a bot above." + a real "New chat" button. **No seeded rows ever.**
- **No composer, no ToolsRow, no suggestion chips on Home.** Home is a list.

### 1.5 Chat screen changes (existing `ChatScreen`, restyle + re-head)

- Header 56dp: **back chevron** (→ Home) left; centre-left (not centred totem): bot-or-chat title — for a plain chat, the chat title 16sp SemiBold + status 12sp mono ("Thinking…" / provider-model / "New chat"); for BotChat, the bot monogram 36dp + bot name + "Bot · <provider or Auto>" (§3.3). Right: New-chat icon + overflow (Rename / Share / Delete — as today). **Delete:** hamburger, centred logo stack, `LogoMark`, status-centre tap → model picker stays (tap the title/status area, as today).
- Transcript: assistant plain text (unchanged structure); user bubble restyled (§2.4); step timeline, approval dialog, composer keep their behaviour; **token lines deleted** (§3.4); "Chatting as <bot>" strip deleted (the BotChat header now carries it); "Exit bot" deleted (leaving a bot chat = back, §3.3).
- **Delete:** `MenuDrawer`, the drawer scrim/slide code, the bottom search pill + footer gear/new-chat row, the pull-to-new-chat gesture (`pullConnection` — it silently discards the open chat on a fling; a list-first home makes it redundant and dangerous), the "Connect a free AI key" drawer banner (the Home settings block already shows key state; the empty-chat "Connect a free AI key" chip stays).

### 1.6 Bot-chat screen (`Route.BotChat`)

- New route: `data class BotChat(val botId: String) : Route` in `ui/Route.kt`; branch in `AppRoot.kt` → `BotChatScreen(vm, botId, back)`.
- Implementation: **reuse, don't duplicate.** `BotChatScreen` calls the existing transcript + composer with a bot header. Concretely: refactor `ChatScreen(vm, navigate)` to accept a header mode (`ChatHeaderMode.Default` | `ChatHeaderMode.Bot(botDef)`); `BotChatScreen` is a thin wrapper that resolves the definition and renders `ChatScreen` in Bot mode with `back` popping to Home. Do **not** fork a second copy of the message list/composer — one transcript implementation, two headers.
- Header (Bot mode): back chevron → Home; monogram tile 36dp; bot name 16sp SemiBold; subtitle 12sp mono "Bot · <suggestedProvider or Auto>" + (thread age or "New thread" when no messages yet). Overflow: Rename thread / Delete thread / View in Bot Store.
- The screen shows **that bot's thread only**. Switching bots = back to Home → tap another bot. There is no in-screen bot switcher.

## 2. Colour / type / shape system ("Ink Ledger")

Concrete tokens. This system must differ from Axion **and** from round 1 on inspection: round 1 was light-default, indigo `#4F46E5`, 20–28dp pills, full-round chips, centred brand header. This is dark-default, bronze, 4–12dp rectilinear, left-aligned headers, mono metadata.

### 2.1 Palette — default DARK ("Ink"), light ("Day") secondary

| Token | Dark (default) | Light ("Day") | Use |
|---|---|---|---|
| `bg` | `#14161A` | `#F7F8FA` | App canvas, message list |
| `surface` | `#1D2026` | `#FFFFFF` | Cards, composer, sheets, rows |
| `surfaceRaised` | `#262A31` | `#FFFFFF` | Cards on `surface` (1dp border, no shadow in dark; 2dp shadow in light only) |
| `surfaceSunken` | `#0F1114` | `#EDEFF2` | Pressed/selected row, code header strip |
| `text` | `#F2F3F5` | `#16181D` | Primary text |
| `textSecondary` | `#A6ADB8` | `#565D68` | Secondary text, previews |
| `muted` | `#6E7683` | `#8A919C` | Placeholders, captions, timestamps |
| `border` | `#2E333D` | `#E2E5EA` | Hairlines, card outlines (1dp, never heavier) |
| `accent` (bronze) | `#E0A63F` | `#92400E` | Primary actions, send, active states, links, selection |
| `onAccent` | `#14161A` | `#FFFFFF` | Text/icons on accent |
| `accentSoft` | `#3A2E18` | `#FDF3E3` | Accent-tinted bg (selected chip, added-diff lines, listening state) |
| `danger` | `#F87171` | `#DC2626` | Destructive, errors, removed-diff lines |
| `dangerSoft` | `#3D2624` | `#FEF2F2` | Removed-diff / error bg |
| `userBubble` | `#2A2F38` | `#EDEFF2` | User message bubble |
| `codeBg` / `codeText` | `#1E1E1E` / `#F5F5F5` | same | Code blocks (both themes) |
| `scrim` | `rgba(0,0,0,0.55)` | `rgba(0,0,0,0.32)` | Sheet dim |

**Rules:** exactly one saturated hue (bronze). No indigo/periwinkle/teal anywhere (grep must find no `#4F46E5`, `#818CF8`, `#8292D6`, `#35766F`). No green/lime anywhere, in any role — success = neutral check icon + plain text ("12 passed · 0 failed"), never a green dot/pill. Diff-added = `accentSoft` bg + `accent` `+` gutter (bronze, not green). Theme switch System/Light/Dark in Settings → Appearance; **default is Dark** (the theme setting's default value changes from system/light to dark — record this as a deliberate behaviour change; if the user hates it on the phone, it is one setting flip, not a rebuild). Contrast: body ≥ 4.5:1 (`#E0A63F` on `#14161A` ≈ 7:1; `#92400E` on `#FFFFFF` ≈ 6.6:1; `#A6ADB8` on `#14161A` ≈ 7:1).
**How this differs, in one line each:** vs Axion Dots — no periwinkle/teal, no light `#F8F7F4`, no mascot, no drawer; vs round 1 — no white canvas, no indigo, no pill shapes, no centred brand header, no chat-first home.

### 2.2 Typography (system fonts only; no downloads)

- UI/body: `FontFamily.Default`. Code/diff/terminal/metadata: bundled mono (`GoogleSansCode`, already in `res/font/` — the only font file; do not add fonts).
- New scale (differs from round 1's 16/24 body, 28 display):

| Style | Size | Weight | Line height | Use |
|---|---|---|---|---|
| Title (screen) | 22sp | SemiBold (600) | 28sp, tracking −0.5sp | Screen titles (Home has no big title; subpage titles) |
| Row name | 15sp | SemiBold (600) | 20sp | Bot/chat names, card titles |
| Body | 15sp | Regular | 22sp | Assistant messages, body copy (was 16/24) |
| Row preview | 13sp | Regular | 18sp | Chat/bot previews |
| Meta (mono) | 12sp | Regular (mono) | 16sp | Times, counts, status lines, provider labels |
| Eyebrow | 11sp | SemiBold | 16sp, tracking 1.2sp, ALL CAPS | Section labels: BOTS / CHATS / RUNNING NOW |
| Label | 12sp | Medium (500) | 16sp | Buttons in rows, chips |
| Code | 13sp | Regular (mono) | 20sp | Code/diff/terminal |

- Never below 11sp; eyebrows are the only all-caps. Respect system font scaling (sp throughout; layout must survive 1.3× scale).

### 2.3 Spacing, corners, elevation, motion

- Spacing scale (dp): 4, 8, 12, 16, 20, 24. Page horizontal padding 16dp; list row gap 2dp (rows are separated by hairlines, not gaps).
- **Corner radii (the visible break from round 1):** cards 10dp; composer 12dp; list rows 8dp; monogram tiles 8dp (rounded square, **not** a circle); buttons 8dp; chips 6dp; sheets top corners 16dp. **No full-round (999dp) shapes anywhere** except the 8dp unread dot and the send button (36dp circle — the one circle in the app, kept because it is a thumb target, not decoration).
- **Rows, not cards:** Home list rows have **no card background** — 64dp rows separated by 1dp `border` hairlines, full-bleed. Cards (10dp, 1dp border) are used only for settings groups, workspace sheet rows, and in-stream cards. This "ruled ledger" home is the single most visible difference from round 1's card-and-pill home.
- Elevation: none in dark (tonal separation only). Light: 2dp shadow on sheets only. No shadows on cards/composer.
- Motion: 120–200ms `FastOutSlowIn`, sheet/slide only. No bounce/spring. Thinking dots pulse 1s staggered (as today). Respect reduce-motion.
- Icons: 20dp line icons, 1.75dp stroke, `textSecondary` default, `text` when active. **Same `Gk` glyph set** (do not redraw icons — the set is fine; the layout around them is what changes). No emoji as UI icons.

### 2.4 Message/composer restyle (same components, new shape)

- User bubble: right-aligned, `userBubble` bg, **10dp corners with 4dp bottom-right**, max 80% width, 15sp/22 text. (Was 20dp.)
- Assistant: plain text, full width, 15sp/22, no avatar. (Unchanged structure.)
- Composer: docked bottom, `surface` bg, 1dp `border`, **12dp corners** (was 28dp pill), min 56dp, 16dp side margins. Inside: text field top, bottom row `+` / mic / send (36dp accent circle; stop square while streaming). ToolsRow chips become **6dp-corner** chips (was full-round), same toggles (Deep research / Long task / Turbo / model chip).
- Approval dialog: keep the `AlertDialog` + Allow once / This chat / Always allow / Decline (round-1 scope work is correct). Restyle to §2 tokens/radii only. The inline-card upgrade (round-1 spec §9.1) stays out of scope.
- Code-diff card: added lines `accentSoft`/accent (bronze), removed `dangerSoft`/danger, test pill neutral dark (as round 1, recoloured).

### 2.5 Monogram tiles (replace every avatar/logo tile)

- **Bot tile:** rounded square 8dp, size 44dp in lists / 36dp in headers / 28dp in the "chatting as" position (deleted with that strip — no 28dp use). Background: deterministic flat fill from the bot's id hash, chosen from **5 muted fills** (slate `#3A4150`, bronze-deep `#6B4A1F`, plum `#4A3542`, teal-free navy `#24344D`, stone `#4A4438` — dark-theme values; light-theme equivalents are the same hues at 12% tint on white with dark text). Foreground: bot's first **two** initials, uppercase, 16sp SemiBold, white (dark) / dark text (light). Examples: "Coder" → "CO", "Researcher" → "RE". **Never** a cartoon face, glyph, or photo by default.
- **User tile:** same tile, initials from `userName` (or "Y"), bronze-deep fill.
- **Bot Store rows** use the same monogram tile (replacing `BotLogoView` there too).
- **Gallery photos as bot logos are removed as a feature** (see §3.5): a user-picked photo is not a monogram and reintroduces arbitrary imagery. Existing `file:` logos already stored are rendered as their bot's monogram (the file is ignored, not deleted, so nothing is lost if the decision is revisited).

### 2.6 Sheets (one pattern)

Shared shell: `surface` bg, **16dp top corners** (was 24dp), drag handle 32×4dp `border`, scrim, swipe-down/scrim-tap dismiss, 200ms slide-up, max 85% height. Uses: Workspace sheet (§1.3), attach sheet, model/provider picker, create-bot sheet (§3.3), confirmation sheets. Titles 17sp SemiBold left-aligned, close × 44dp right.

## 3. Per-demand implementation notes

### 3.1 Demand 1 — Completely different UI
- **Files to rewrite:** `ui/chat/ChatScreen.kt` (delete `MenuDrawer`, drawer state, pull gesture, centred header; add back-chevron header; restyle bubbles/composer per §2.4), `ui/theme/Theme.kt` (new `Palette` values per §2.1 — keep the `Palette` struct + legacy aliases so untouched screens compile; re-point every alias at the new tokens), **new** `ui/home/HomeScreen.kt` (the Home list, §1.4), **new** `ui/home/BotChatScreen.kt` (thin wrapper, §1.6), `ui/Route.kt` (add `Home`, `BotChat(botId)`; keep the rest), `ui/AppRoot.kt` (start at `Route.Home`; branch `BotChat`; delete nothing else).
- **Files to restyle only (tokens/radii/type, no behaviour):** `ui/workspace/WorkspaceScreens.kt`, `ui/settings/SettingsScreens.kt` + all `ui/settings/*Screen` files, `ui/bots/BotStoreScreen.kt`, `ui/chat/Composer.kt`, `ui/chat/MessageViews.kt`, `ui/chat/Sheets.kt`, `ui/components/Common.kt` (`ScreenHeader` title 22sp per §2.2, `DrawerTile` deleted with the drawer).
- **Verification hook:** after the build, a screenshot of Home next to a round-1 screenshot must share **no** layout element: no centred logo header, no hamburger, no pill composer on the home screen, no indigo, no white canvas, no full-round chips.

### 3.2 Demand 2 — Simplified menu
- **Delete:** `MenuDrawer` + drawer state/scrim/slide in `ChatScreen.kt`; hamburger button; drawer bottom bar (search pill, gear, new-chat pencil); the drawer "Connect a free AI key" banner.
- **Add:** Home settings block (§1.4) + Workspace sheet (§1.3). Settings is reached **only** from the gear in the settings block (and from in-app links that already exist, e.g. provider picker → Manage providers).
- **Keep working, new home:** the 7 workspace screens — same composables, same stores, same routes; only their *entry point* changes (Workspace sheet instead of drawer). `Route.Feed/Ideas/Goals/Library/Activity/Computer/CodeDiff` unchanged.
- **Tests:** drawer tests (`V03ScreensTest` "Menu" node, `V04ScreensTest` drawerClosesKeyboard — the two tests fixed in commit 66d2a41) will fail by design: the drawer no longer exists. The test owner updates them to the Home list (see §5).

### 3.3 Demand 3 — Bots section, WhatsApp-style, per-bot chats
- **Data model (reuse, do not invent a new store):** `Conversation.botId/botName` (`data/Models.kt`) + `ConversationStore` (`data/Stores.kt`, one JSON file per conversation under `filesDir/chats/`) already persist per-bot threads. **Add one query** to `ConversationStore`: `fun latestForBot(botId: String): ConversationMeta?` — newest non-worker index entry with that `botId`, or null. No schema change, no migration, no new files on disk.
- **ViewModel (`ui/ChatViewModel.kt`):**
  - **Add** `fun openBotChat(bot: BotDefinition)` — resolve `latestForBot(bot.id)`; if found, `open(id)`; if null, `startBotChat(bot)` (blank thread bound to the bot; persists with `botId` on first send via the existing `persist()` at `:630`). This is the **only** entry point for opening a bot chat. `startBotChat` stays as the create-blank primitive and must never be called directly from UI again.
  - **Add** `fun botThreadMeta(botId: String): ConversationMeta?` (or expose the index filtered by bot) so Home rows can show each bot's last message + time.
  - **Keep:** `activeBot`, `botFor`, `open(id)`, `persist()` botId stamping, `Agent` bot wiring (`promptBlock`, `switchesForBot`, `preferProvider` — the brain side is untouched).
  - **Delete:** `exitBot()` + the "Chatting as / Exit bot" strip (leaving a bot chat is now just Back). Any other caller of `exitBot` must be removed.
- **UI (`ui/bots/BotsScreen.kt` → delete as a screen; its content moves):**
  - Delete `BotsScreen` (the route `Route.Bots` and its `AppRoot` branch go with it). Its three groups split: "Your bots" → the Home BOTS section (§1.4); "Live" parents/workers → the "RUNNING NOW" group on Home (§1.4); "Finished workers" → stays reachable from Activity (already listed there via `TaskRuns`/`JobRecords` — verify, don't rebuild).
  - **Keep and reuse:** `BotDefCard`'s create flow → `CreateBotCard` becomes a bottom sheet launched from Home's "New bot" button. **Change its ending:** after `botStore.create(...)`, call `openBotChat(created)` + `nav(Route.BotChat(created.id))` — creating a bot opens **its** chat; it never lands on a blank home chat.
  - **Tap rule (normative):** tapping a bot row calls `vm.openBotChat(bot)` then `nav(Route.BotChat(bot.id))`. Tapping a bot must never call `newChat()`, never call `back()` to leave the Bots surface, never navigate to `Route.Chat`. The old line `BotsScreen.kt:72` (`onStartBot = { bot -> vm.startBotChat(bot); back() }`) is the bug — it is deleted with the file, and the verifier greps for its pattern (`startBotChat` called from any UI file) to prove it is gone.
  - **Bot-chat back:** `BotChatScreen`'s back (chevron + system back) pops the `AppRoot` stack to `Route.Home`. No ViewModel back handling — the stack does it. `closeWorkerChat` machinery is untouched (worker transcripts keep their parent-return behaviour).
- **Bot Store:** install flow unchanged (install → toast → find it in Bots on Home). Its rows use monogram tiles (§2.5).

### 3.4 Demand 4 — Remove the token display from chat
- **Delete these render blocks (only these):** `ui/chat/ChatScreen.kt:310-315` (context-usage line + `LinearProgressIndicator`) and `:316-319` (local-speed line).
- **Delete the ViewModel state that fed them:** `ChatViewModel.kt:95-96` (`contextUsage`) and `:98` (`localSpeed`), the reset at `:194`, and the feed-in at `:766` (`onContextUsage = …`) and `:831` (`localSpeed = result.speed`). **Do not delete** `agent/ContextUsage.kt` (measurement + `shouldCompact` auto-compact trigger — the agent still acts on it, the user just never sees the number), `local/LocalToolUse.kt` (`LocalSpeed` data class — may be referenced by `P2LocalToolUseTest`; leave the class, delete only its UI use), or `Agent.kt`'s usage measurement.
- **Keep (explicitly out of scope):** Quota screen (`V04Screens.kt:530`) and Antigravity token rows — Settings accounting, not chat. If the verifier finds the word "tokens" anywhere in the chat transcript/composer UI after the build, demand 4 fails. (The word may still appear in Settings → Quota and in code comments.)

### 3.5 Demand 5 — Remove the cartoon logo (reverses the mascot-as-logo decision)
- **This reverses the standing decision** "mascot is the app logo at the top of the chat screen" (round-1 spec §0.2 / §5.1). The reversal is the user's explicit order (2026-11-19, quoted in the audit). The new brand mark is a **wordmark**: the letter "X" in 22sp SemiBold with −0.5sp tracking, in `text`, with a 3dp bronze square (8×8dp, 2dp corners) as its full stop — "X▪" in bronze. No image asset, no character, no face.
- **Delete:** `ui/mascot/MascotView.kt` (whole file — `MascotLogo` + `LogoMark`), all 10 `mascot_*.png` in `app/src/main/res/drawable-nodpi/`, `app/src/main/assets/mascot.glb`, `axion_splash.png` (replace the system splash icon in `res/values/themes.xml:11` with the wordmark tile — a plain bronze-on-ink drawable, or remove the animated-icon item so the system shows the launcher icon).
- **Replace at each render site:**
  | Site | Now | After |
  |---|---|---|
  | Chat header (`ChatScreen.kt:228`) | `LogoMark` 36dp | Wordmark "X▪" + title/status (§1.5) |
  | Empty chat (`:528`) | `LogoMark` 48dp + "X" 28sp | Deleted with the empty chat — Home is a list now; the Chat empty state becomes: wordmark 22sp + "Start typing below." 15sp, no image |
  | Drawer header (`:601`) | `LogoMark` 32dp | Deleted with the drawer |
  | Splash (`:758`) | `LogoMark` 72dp | Wordmark centred, 28sp, ~700ms, tap to skip |
  | About (`SettingsScreens.kt:466`) | `LogoMark` 72dp | Wordmark 28sp + "X" version lines (unchanged) |
  | Intro (`Onboarding.kt:84`) | Animated `MascotLogo` 96dp + indigo glow | Wordmark 40sp rising in (same timing), glow removed, black background kept |
  | Launcher icon | `ic_launcher_fg/mono` (UNVERIFIED — audit §3.10) | Builder must inspect: if it depicts the character, replace with the bronze-square wordmark tile (rounded-square, bronze square + white "X"). If it is already geometric, leave it and record that. |
- **Bot avatars → monogram tiles** (§2.5). Concretely: rewrite `ui/bots/BotLogoView.kt` — `BotLogoView` renders the monogram tile from the bot name only; delete `builtinIcon()` mapping and the `BotLogo` glyph/photo paths from the UI. `data/BotLogo.kt` + `BotDefinition.logo` field may stay as stored data (harmless, and `setLogo` callsites in `BotsScreen` die with that file), but **no UI reads them**. Delete the "Change logo" button, the `LogoPickerSheet`, and the logo picker in `CreateBotCard` (creation keeps name/description/persona/provider only). `saveBotLogo` (`BotLogoView.kt:76`) is deleted with the photo feature.
- **Tests:** no test references `MascotLogo`/`LogoMark` (audit §3) — deletion should be test-clean; the builder still runs the suite.

### 3.6 Demand 6 + the "Axion" rename (part of "completely different")
- **Navigation/data-model rewire (demand 6):** §1.2 graph + §3.3 `latestForBot`/`openBotChat`/`Route.BotChat`. The old rule "one chat screen does all the work" is replaced by: **Home is the list; each bot owns its thread; plain chats are threads too.** The `ChatViewModel` single-active-conversation machinery is kept (it already supports per-conversation sessions, `sessions` map) — only the *entry points* change.
- **Rename every user-visible "Axion" → "X"** (audit §2.2 is the checklist — ~40 strings). Normative list, file by file:
  - `data/Stores.kt:37` — `assistantName` default `"Axion"` → `"X"`.
  - `ui/settings/SettingsScreens.kt` — section "Axion" → "Assistant"; "Customize Axion" → "Customize X"; "About Axion" → "About X"; "What should Axion call itself?" → "What should X call itself?"; email subject `"Axion … problem"` → `"X … problem"`; footer "X ${VERSION_NAME}" stays.
  - `ui/chat/ChatScreen.kt` — clipboard label `"Axion"` → `"X"`; approval text "Axion will ask every time" → "X will ask every time".
  - `ui/bots/BotsScreen.kt` (deleted — its strings die with it; the Home equivalents say "X" or the bot's name).
  - All settings sub-screens (`ConnectorsScreen`, `MemoryScreen`, `ApprovalsScreen`, `GitHubScreen`, `TelegramScreen`, `ToolsLibraryScreen`, `SchedulesScreen`, `DeleteAllDialog`, `StorageScreen`, `BotStoreScreen`, `V04Screens`) — every user-visible "Axion" → "X". The Roman-Urdu example in `SchedulesScreen.kt:110` is rewritten in English ("Ask X in chat — 'tell me the weather tomorrow at 9 AM'…").
  - **Keep (code-only, out of scope):** package name, `AxionTheme`/`AxionApp`/`AxionSheet` identifiers, `NOTICE.md`/`BRAIN-SOURCES.md` attribution. Renaming the package would break the Play listing identity and is not asked for.
- **Settings structure:** the round-1 spec §10 reorder is still not required as a separate job, but the rename above removes the "Axion" section header, which was the most visible part of "Settings UI is the same". The builder must not reorder groups beyond the rename (scope discipline — a reorder is a third redesign, not this one).

## 4. What must NOT change

- **Brain, stores, engines, providers, voice:** `agent/` (Agent, Swarm, ApprovalGate incl. `ALLOW_CHAT`, ToolCatalog, skills wiring), `llm/` (providers, ModelRanker, FreeFallback), `mcp/`, `browser/` (Obscura), `linux/` (PRoot/terminal), `work/` (schedules, RunRegistry), `quota/`, `data/` stores (`ConversationStore` file format, `BotDefinitionStore`, `IdeasStore`, `GoalsStore`, `MemoryStore`, `KeyVault`, `Approvals`), `voice/` (Piper preference `piper-aegis`, `ReadAloudPlayer` chain, `speakPhoneLocale` fallback), `livecall/`, `antigravity/`, `telegram/`, `update/`. The only engine-adjacent edits allowed are: deleting the `onContextUsage` *reporting hook* (§3.4) and adding `ConversationStore.latestForBot` (§3.3).
- **Behaviour:** approval enforcement, swarm caps (max 4 parallel, 8 steps/worker, depth 2), provider failover, key scrubbing, offline behaviour (honest error, no fake reply), empty-list honesty, no demo content. **Test updates only — never change app behaviour to satisfy a test** (standing rule).
- **Bot seeds:** `data/BotCatalog.kt` 23 seeds unchanged (Doc Maker / Shopkeeper / Idea Catcher stay STUB-MOCK seeds, as recorded in the round-1 audit — out of scope for a UI redesign).
- **Dependencies:** no new Gradle dependencies, no new font files, no new image assets except the wordmark tile drawables (vector XML, not PNG).

## 5. Acceptance checklist (for the verifier)

Build passes only if **every** box checks. Quote the evidence (file:line, grep output, or screenshot) per item.

**Structure**
- [ ] App opens on `Route.Home` (Home list), not on an open chat. Fresh install shows the Home list with empty Bots/Chats states, no seeded rows.
- [ ] No drawer exists: grep `MenuDrawer|drawerOpen|DrawerTile` in main Kotlin returns 0 hits; no hamburger icon on any screen.
- [ ] Workspace sheet is the only home for Feed/Ideas/Goals/Library/Activity/Computer: all 7 screens open from it and from in-chat links; none appears in any menu/list on Home.
- [ ] Back from Chat → Home; back from BotChat → Home (Bots visible); back from Settings/workspace → Home; system back == chevron back on every screen.

**Bots**
- [ ] Home BOTS section lists every installed + every created bot (install one from Bot Store, create one from "New bot" — both appear).
- [ ] Tapping a bot opens `Route.BotChat(botId)` showing that bot's thread (previous messages present if it has a thread; blank composer with bot header if new). It never opens a blank home chat.
- [ ] Tapping bot A, back, tapping bot B shows B's thread (not A's, not a blank chat).
- [ ] Creating a bot opens its chat directly; installing from the Store does not open any chat.
- [ ] Grep: `startBotChat` is called from no UI file (only `openBotChat` calls it); the pattern `startBotChat(bot); back()` is gone.
- [ ] Bot rows show last-message preview + time for bots with threads; "New" for bots without.

**Tokens / mascot / name**
- [ ] Grep `contextUsage|localSpeed` in `ui/chat/` returns 0 hits; no "tokens"/"tok/s"/"Context %" text renders anywhere in a chat (plain or bot). Quota screen still shows usage (Settings only).
- [ ] Grep `MascotLogo|LogoMark|mascot` (case-insensitive) in main Kotlin returns 0 hits; `drawable-nodpi/mascot_*.png` and `assets/mascot.glb` deleted; `ui/mascot/` directory gone.
- [ ] No cartoon face anywhere in-app (screenshots: Home, Chat, BotChat, Bot Store, Settings, About, Intro, Splash).
- [ ] Bot avatars are monogram tiles (initials, 8dp rounded square) on Home, in BotChat header, and in Bot Store. No glyph icons or photos as bot avatars.
- [ ] Grep for user-visible "Axion" in `ui/` strings returns 0 (code identifiers `AxionTheme`/`AxionApp` excepted and listed by the builder).

**Design system**
- [ ] No indigo/periwinkle/teal: grep `#4F46E5|#818CF8|#8292D6|#35766F` in main Kotlin returns 0 hits. No green: grep `#10A37F|#76B900|green|lime` returns only the rule comment.
- [ ] Default theme is Dark (Ink); bronze accent `#E0A63F` (dark) / `#92400E` (light); radii ≤ 12dp everywhere except the 36dp send circle; no full-round chips; Home rows are hairline-separated, not cards.
- [ ] Header gap below the status bar on every screen (screenshots: Home, Chat, BotChat, Settings, each workspace screen).
- [ ] 48dp touch targets on list rows/buttons; layout survives 1.3× font scale without clipping.

**Non-regression**
- [ ] All 7 workspace screens still open with real data/empty states; approval flow (Allow once / This chat / Always allow / Decline) works from chat; Piper voice preference still in Settings → Voice & Language; providers/MCP/GitHub/Telegram screens untouched in function.
- [ ] CI compiles; full test suite run. Tests that asserted the drawer / `startBotChat`-to-home behaviour are updated by the test owner to the new navigation (Home list, `openBotChat`, `Route.BotChat`) — app behaviour is never changed to satisfy a test.
- [ ] Phone test by the user: Home list, bot tap → bot chat → back → different bot, Settings gear, Workspace sheet — the exact flows he rejected before.

## 6. Risks / open questions for the builder

1. **ChatScreen refactor size:** `ChatScreen.kt` is ~800 lines with the drawer, pull gesture, popovers, and dialogs inline. The header-mode refactor (§1.6) touches its top half; the builder should extract the transcript+composer into a shared `ChatBody` only if it can do so without changing behaviour — otherwise parameterise `ChatScreen` and keep one file.
2. **`latestForBot` vs duplicate threads:** old data may hold several threads per bot (every old `startBotChat` made a fresh blank one). `latestForBot` picks the newest; older ones remain visible in CHATS (correct — they are real history, not to be hidden or merged).
3. **Default-dark risk:** the user has never asked for dark-default; it is chosen here because light-default + white canvas is precisely the round-1 look he rejected. If his phone test hates dark, the fix is flipping the default in one line (`Theme.kt`/`AppSettings.theme`), not a redesign — say so in the build report rather than silently keeping light.
4. **Launcher icon:** audit could not verify whether it depicts the mascot (§3.5 table). The builder must look and report.
5. **"Axion" in `BuildConfig`/update channel:** the updater verifies against "Axion's signing key" (comment in `SettingsScreens.kt:491`) — code comment only; do not touch the updater or signing.

## 7. Per-screen design spec (Addendum H, 2026-11-19)

**Normative.** Every screen below is built to these numbers exactly — no screen is left to default styling, no message type is left unstyled, no install/progress state is left invisible. Where a screen's content is unchanged from round 1, only its tokens/radii/type change to this section; its behaviour is identical. The rebuild doc (§H in `docs/X-REDESIGN2-REBUILD-2026-11-19.md`) records, per screen, that it was built to this spec.

### 7.0 Shared rules (every screen)

- **Status-bar gap:** `statusBarsPadding() + 12dp` at the top of every screen, before any header. No header, title, or button may touch the status bar.
- **Header:** 56dp tall, left-aligned. Back chevron 40dp tap target on the left; title 22sp SemiBold (`Type.title`); optional trailing action 40dp. `ScreenHeader` is the shared implementation; `SettingsPage` uses the same header (left-aligned 22sp, never centred).
- **Page padding:** 16dp horizontal. List-row gap 2dp; rows are separated by 1dp `border` hairlines, not by gaps or card backgrounds.
- **Groups/cards:** 10dp corners, 1dp `border`, `surface` bg. Settings groups use the same 10dp shell.
- **Rows:** 56dp (settings/workspace rows) to 64dp (Home bot/chat rows). Icon 20dp line glyph, 1.75dp stroke, `textSecondary` (active: `text`). Row title 15sp SemiBold; row subtitle/preview 13sp `textSecondary`; row meta (times, counts, versions) 12sp mono `muted`.
- **Eyebrows:** 11sp SemiBold, tracking 1.2sp, ALL CAPS, `textSecondary` — section labels only (BOTS / CHATS / RUNNING NOW / TOOLCHAINS / UPDATES / MODELS).
- **Buttons:** 8dp corners, 15sp Medium text; primary = accent fill + `onAccent` text; secondary = `surfaceRaised` fill + `text`; destructive = danger text on `surface`. Minimum height 40dp (48dp for primary actions in sheets/dialogs). **Chips:** 6dp corners, 12sp Medium.
- **Empty states:** centred 15sp `textSecondary` text + one real action button (8dp). Never a seeded row, never a fake count.
- **Touch targets:** ≥ 40dp (list rows 56–64dp; icon buttons 40dp). Layout must survive 1.3× font scale without clipping.
- **Animation (global):** 120–200ms `FastOutSlowIn` for slides/fades; sheets slide up 200ms; dialogs fade + scale 140ms; no bounce/spring. Thinking dots pulse 1s staggered. Respect reduce-motion (no entrance motion when reduced).
- **No green, no indigo/periwinkle/teal, no full-round shapes** except the 8dp unread/working dot, the 36dp send circle, the voice Orb, and progress-bar capsule tracks (see §7.9).

### 7.1 Home list (`Route.Home`, `ui/home/HomeScreen.kt`)

Top to bottom, exact (§1.4 layout):
1. **Settings block — 64dp row.** Left: user monogram tile 40dp (8dp rounded square, bronze-deep fill, 1–2 initials 16sp SemiBold). Centre, two lines: name 15sp SemiBold (`settings.userName` or "You"); status 12sp mono `muted` ("Ready · <active provider name or 'No key yet'>", real state from the connectors list). Right: Workspace grid button 40dp (`Gk.grid`), then Settings gear 40dp. Tapping the monogram/name opens Settings → Customize (profile). No avatar photo, no big profile card, no providers banner.
2. **Search — 48dp field, 8dp corners, `surface` bg, 1dp `border`.** Placeholder 15sp `muted` ("Search chats and bots"). Filters Bots + Chats inline by name/preview as typed (no separate search screen, no debounce animation). Clear × appears when text is present.
3. **RUNNING NOW group (only while runs are live).** Eyebrow "RUNNING NOW" + rows: parent/worker monogram 44dp, name 15sp SemiBold, status 13sp `textSecondary` + steps count 12sp mono, "RUNNING" 11sp mono accent label, 8dp accent working dot. Parent tap → its chat; worker tap → worker transcript (`openWorkerChat`, back = "Back to parent chat").
4. **BOTS section.** Eyebrow "BOTS" + right-aligned "New bot" text button (12sp Medium, accent). Rows 64dp, hairline-separated (1dp `border`, inset 72dp): monogram tile 44dp (8dp rounded square, 2 initials, deterministic muted fill per §2.5); name 15sp SemiBold; preview 13sp `textSecondary` (last message of that bot's thread, or the bot description when no thread exists); time 12sp mono right-aligned (`chatTimeLabel`); 8dp accent working dot while its run is live; "New" 12sp mono `muted` when no thread exists. Tap → `Route.BotChat(botId)` with that bot's own thread. Long-press → no action (bot management lives in Bot Store). Empty state: "No bots yet — install one from the Bot Store, or create one." + "Open Bot Store" button.
5. **CHATS section.** Eyebrow "CHATS" + right-aligned "New chat" text button (→ blank Chat, `vm.newChat()`). Rows 64dp, same shape as bot rows: monogram tile 44dp for bot chats (bot's initials), plain-chat rows use the same monogram tile with the chat title's initials (no photo, no glyph); name 15sp SemiBold; preview 13sp; time 12sp mono; pin icon 16dp when pinned; 8dp accent dot while its run is live. Order: pinned first, newest first (existing `buildChatRows`). Long-press → Rename / Pin / Delete / Share sheet (10dp sheet, rows 56dp). Empty state: "No chats yet — tap New chat, or open a bot above." + "New chat" button.
6. **No composer, no ToolsRow, no suggestion chips on Home.** Home is a list.
7. **Animation:** rows appear with no entrance animation on open (the list is the home — it must be instant). Search filtering is instant. The Workspace sheet slides up 200ms. Tapping a row shows the pressed state (`surfaceSunken`) for 120ms, then navigates.

### 7.2 Bots section + bot list (inside Home, §7.1) and Bot Store (`Route.BotStore`)

- **Bot list (Home BOTS):** as §7.1 item 4. Every installed AND every created bot is listed (source: `botStore.definitions`).
- **"New bot" sheet (`ui/bots/CreateBotSheet.kt`):** bottom sheet, 16dp top corners, title "New bot" 17sp SemiBold left-aligned, close × 40dp right. Fields: name (15sp, single line), description (15sp, 3 lines), persona (15sp, 4 lines), preferred provider chips (6dp, Auto / Gemini / Grok / OpenRouter / Groq — single-select, selected = accent fill). No logo picker, no photo, no glyph choice. Primary button "Create bot" 48dp, 8dp corners, accent fill. On create: sheet closes, the bot's own chat opens (`Route.BotChat`). Animation: sheet slides up 200ms; fields focus with the keyboard rising (no layout jump — the sheet is max 85% height).
- **Bot Store screen:** header 56dp ("Bot Store", back chevron). Intro note 13sp `textSecondary` ("Install a bot to give it work. A bot is a definition — persona, skills and a tool allow-list — run on X's own agent loop. Install never starts a bot; you start it from the Bots section."). Rows 64dp: monogram tile 44dp, name 15sp SemiBold, description 13sp `textSecondary` (2 lines max), "Install" 8dp button (or "Installed" 12sp mono `muted` when already installed). Tap a row → its detail (same monogram, full description, skills list 13sp, "Install" button). After install: toast "Installed '<name>' — find it in the Bots section on Home" (no navigation, no chat opened).

### 7.3 Bot chat (`Route.BotChat`, `ui/home/BotChatScreen.kt` + `ChatScreen` in Bot mode)

- **Header — 56dp:** back chevron 40dp (→ Home, Bots section in view); bot monogram tile 36dp (8dp rounded square, 2 initials); bot name 16sp SemiBold; subtitle 12sp mono `muted` ("Bot · <suggestedProvider or Auto>" + " · New thread" when no messages yet, else thread age via `chatTimeLabel`). Right: overflow 40dp (Rename thread / Delete thread / View in Bot Store — 10dp sheet, rows 56dp). No new-chat button in bot mode (a bot has one thread; a second blank thread is never created from the header).
- **Transcript + composer:** identical to main chat (§7.4) — one implementation, two headers. The bot's messages are assistant messages; the user side is the same user bubble. No "Chatting as <bot>" strip, no "Exit bot" — leaving is Back.
- **Empty bot thread:** wordmark 22sp + "Start typing below." 15sp `textSecondary`, centred in the transcript area. No image, no mascot, no suggestion chips that run fake actions (the "Connect a free AI key" chip appears only when no provider key is connected, and it opens Settings → Providers).

### 7.4 Main chat (`Route.Chat`, `ui/chat/ChatScreen.kt`) — every message type, exact

- **Header — 56dp:** back chevron 40dp (→ Home); centre-left title block: chat title 16sp SemiBold (tappable → model picker, as today) + status 12sp mono `muted` ("Thinking…" while streaming / provider-model / "New chat"). Right: new-chat icon 40dp + overflow 40dp (Rename / Share / Delete — 10dp sheet, rows 56dp). No hamburger, no centred logo, no drawer.
- **Transcript:** 16dp horizontal padding, 8dp vertical gap between messages, scrolls to the newest message on send/stream (120ms smooth scroll). Assistant plain text: 15sp/22, full width, `text`. No avatar on assistant messages.
- **Normal chat message (assistant):** plain 15sp/22 text, full width. Markdown headings 17sp SemiBold (H1) / 15sp SemiBold (H2) with a 32×3dp accent rule under H1 (2dp radius). Links accent, underlined on tap. Lists 15sp/22 with 8dp indent. No bubble, no background.
- **User message:** right-aligned bubble, `userBubble` bg, **10dp corners with 4dp bottom-right**, max 80% width, 15sp/22 `text`, 12dp horizontal / 8dp vertical padding. Attachments inside the bubble: thumbnail 10dp corners; remove-× 4dp rounded square. Long-press → Copy / Delete (10dp sheet).
- **Code message (`BashCard` / `CodeBox`):** dark card (`codeBg` `#1E1E1E`), 10dp corners, 1dp `border`. Header strip 36dp: language/tool label 12sp mono `muted` ("Bash" / "Kotlin" / "Diff") left, Copy button 12sp Medium right. Code body 13sp mono/20 `codeText`, 12dp padding, horizontal scroll when wider than the card. **Command-running variant:** status pill 6dp corners (Running: `accentSoft` bg + accent text + pulsing `DotGrid` 8dp dots; Done: `surfaceRaised` bg + `textSecondary`; Failed: `dangerSoft` bg + danger text), title 13sp mono ("$ <command>" truncated to one line), live output tail: last 3 lines, 12sp mono `muted`, updating as the command streams (45ms flush ticker — the output visibly moves). On finish the pill flips to Done/Failed and the full output stays scrollable in the card.
- **APK shared message (`ApkBuildCard`):** 10dp card, `surface` bg, 1dp `border`, 12dp padding. **Running:** title 15sp SemiBold ("Building <project>") + pulsing `DotGrid` + "Building" 12sp mono accent + last 3 build-log lines 12sp mono `muted`. **Done:** title 15sp SemiBold + result line 13sp `textSecondary` ("APK ready: <name>.apk · <size>") + `ArtifactCard` (10dp file card: name 15sp, size 12sp mono, Download + Open icons 40dp) + "Share APK" button 8dp, accent fill, 40dp. **Failed:** error 13sp danger + last lines 12sp mono + "Retry" button 8dp. The card renders only while that project is the open chat's project; it never appears in another chat.
- **Approval card (`AlertDialog`, 10dp corners):** title 22sp SemiBold ("Allow this command?"); body 14sp/20 (the exact command/tool summary, max 600 chars) + a 13sp scope line when the action is memorable ("'Always allow' remembers this kind of command (…); 'This chat' allows it only until you leave this chat, and is never stored.") or a danger 13sp line when it is not ("<reason>. This kind can never be remembered — X will ask every time."); buttons are two rows of TextButtons, 15sp Medium: "Allow once" (accent), "This chat" (`text`), "Always allow" (`text`), "Decline" (danger). "This chat" / "Always allow" are hidden for hard (non-memorable) actions. The dialog appears over the transcript; the transcript behind it is dimmed by the scrim. No approval is ever auto-granted; no approval card is ever shown as a silent inline chip.
- **Install-progress message:** when the agent installs a tool from chat, it appears as a **command-running message** (§7.4 code message): the install script's live output streams into the `BashCard` tail with the Running pill; on completion the card flips to Done with the installed version line ("Installed: <tool> <version>"). The Developer tools screen (§7.6) is the other surface where install progress is visible — the two never contradict (same `ToolStatus` flow).
- **Thinking state:** `DotGrid` — three 8dp dots, 1s staggered pulse, `text` colour, left-aligned in the transcript. No spinner, no "X is thinking" text.
- **Composer:** docked bottom, `surface` bg, 1dp `border`, **12dp corners**, min 56dp, 16dp side margins, 8dp bottom margin above the nav bar. Inside: text field 17sp/24 top; bottom row: `+` attach 36dp, mic 36dp, send 36dp accent circle (`onAccent` arrow icon); while streaming, send becomes a 12dp stop square on accent. ToolsRow chips above the composer: 6dp corners, 12sp Medium (Deep research / Long task / Turbo / model chip). No token line above the composer, no "Context %" anywhere.
- **Animation:** new messages fade in 140ms; the send button scales 0.94→1.0 in 120ms on tap; the composer grows with the keyboard (no jump — `imePadding()`); streaming text appears line by line (no re-layout flicker — the bubble height grows downward).

### 7.5 Settings + each settings screen (shared template + per-screen content)

**Template (all settings screens):** `SettingsPage` — header 56dp, title 22sp SemiBold left-aligned, back chevron 40dp. Content: 16dp horizontal padding; groups 10dp corners, 1dp `border`, `surface` bg, 13dp vertical padding between groups; group label eyebrow 11sp above each group. Rows 56dp: icon 20dp left, title 15sp SemiBold, subtitle 13sp `textSecondary` (one line, ellipsis), trailing control (chevron 20dp / switch / 8dp button / 12sp mono value). Switches: 8dp track, 6dp thumb. Dialogs 10dp corners. No centred titles, no 24sp headers, no circles except the 8dp dots.

- **Settings (main):** profile card 10dp (monogram 40dp + name 15sp SemiBold + email/status 13sp `textSecondary`, tap → Customize). Providers card 10dp ("Connect a free AI key" 15sp + "Connect" 8dp button when no key; otherwise "Ready · <provider>" 13sp). Sections: **Assistant** — Customize X, Memory, Approvals, Schedules, Browser control, Telegram; **Providers** — Providers, Connectors; **Tools** — Developer tools, Models, Voice & Language; **Data** — Files, Storage, Delete all; **About** — About X, Update card. Update card (interim state, Addendum C): honest text 13sp — "X does not check for updates yet — its own update feed is not set up." + disabled "Check for updates" button that explains the same when tapped. No "Update available" banner anywhere while the feed is disabled.
- **Customize X:** name field (15sp, "Your name"), assistant-name field (label "What should X call itself?", placeholder "X", 15sp), avatar = monogram preview 40dp (initials from the name, live). Save is instant on change (no Save button).
- **Providers:** rows per provider (logo tile 32dp rounded square 8dp + name 15sp + status 12sp mono: "Connected" / "Not connected"); tap → provider sheet (key field 15sp mono, "Connect" 8dp button, "How to get a key" link 13sp accent). Universal key box: paste a key → auto-detect provider, one field, no per-provider forms.
- **Memory:** rows of memory entries (15sp title + 13sp preview + 12sp mono date); tap → detail; delete via long-press sheet. Empty: "Nothing saved yet."
- **Approvals:** list of allow-list entries (tool/command 15sp mono + scope 13sp `textSecondary` + "Revoke" 8dp button); "Allow once / This chat / Always allow" scope labels in plain text, never green.
- **Schedules:** rows (task title 15sp + schedule 12sp mono + next-run 12sp mono + toggle 8dp track); tap → detail with "Delete" danger text button. Empty: "No scheduled tasks — ask X in chat to schedule one."
- **Browser control / Telegram / GitHub / Connectors:** rows as per the template; each screen keeps its existing function (pairing codes, QR, repo list, connector toggles). Pairing code: 20sp mono, letter-spaced, centred in a 10dp card. QR: 200dp, centred, 10dp card, no rounded frame.
- **Voice & Language (`VoiceCatalog` + Piper):** voice rows 56dp (name 15sp + language 12sp mono + pack state 12sp mono: "Downloaded · N MB" / "Not downloaded — import the .onnx + .json files"); selected row = accent left border 2dp + accent check 20dp. Provisional default: Piper Urdu Aegis female. The engine (sherpa-onnx) is bundled; the pack is IMPORTED (Files picker, .onnx + .json — the converted copies, not the raw ones); the shared pronunciation data (espeak-ng-data) downloads ONCE from its row ("Downloading · N%" → "Ready · N MB"). Kokoro Hindi reads in the phone voice for now (different model family, not VITS). Language rows: English / Hindi / Urdu 15sp + 12sp mono note ("Hindi — full", "Urdu — limited, Hindi model" — honest, per the model research). No phone-built-in voice presented as the only option.
- **Developer tools:** header 56dp. Intro note 13sp `textSecondary` ("Real Linux on your phone — Ubuntu in a private sandbox (PRoot). The agent runs its commands here. Install only what you need: nothing is downloaded until you tap Install, and installs keep running in the background."). **Linux card 10dp:** terminal icon 26dp + "Linux · Ubuntu" 15sp SemiBold + status 13sp ("Ready in PRoot · uses <size>" / installing message / "Not set up yet") + "Ready" badge 6dp when ready; `QuotaBar` 6dp-high (3dp radius) under the row while installing; free-space note 13sp below. **Toolchains group 10dp:** rows 64dp per toolchain — badge 32dp (8dp rounded square, tool initials), title 15sp SemiBold, tech line 13sp `textSecondary` (one line), status line 12.5sp: Working → "<action> · <percent>% · <step>"; Queued → "Waiting…"; Failed → error message in danger (13sp, full text in the tool sheet); Installed → "Ready in PRoot · <version> · <size>" in accent; Not installed → "Not installed · ~<size> MB download". Trailing: Working/Queued → 22dp spinner (2dp stroke, `text`); Installed → chevron 20dp; Not installed → "Install" 8dp button. Working rows show a 4dp-high `QuotaBar` (3dp radius) under the row. Tap a row → **Tool sheet** (16dp top corners): title 17sp SemiBold, description 13sp, version/size 12sp mono, buttons Install / Update / Remove (8dp, 48dp), "View log" 13sp accent → **Tool log dialog** (10dp, mono 12sp log lines, scrollable, "Close" 8dp button). **Updates group:** "Update all tools now" row + subtitle ("Nothing installed yet" / "<n> updates available" / "Checks Node, Python, PHP, Kotlin… and installs the newest"). **Models group:** "Free Mode" toggle row. **Remove Linux** (only when installed): danger-text row 56dp ("Remove Linux and all tools", subtitle "Frees <size> · Antigravity sign-ins are kept") → confirmation sheet (10dp, "Remove Linux?" 17sp, "Cancel" / "Remove" 8dp buttons).
- **Models (local models screen, `OwnModelScreens`):** header 56dp ("Models"). Intro note 13sp ("Downloaded models run on your phone, offline. ~1–2 GB each — pick by what you need."). Rows 64dp per model: name 15sp SemiBold + purpose label 12sp Medium in a 6dp chip (CHAT + AGENT + HINDI / CODING / ALL-ROUNDER) + size 12sp mono + RAM note 12sp mono ("~1.4 GB RAM — fits 4 GB phones"); trailing: "Download" 8dp button / progress bar 4dp + percent 12sp mono while downloading / "Downloaded" 12sp mono accent + "Use" 8dp button when ready. Honest caveat line 13sp `textSecondary` under the list: "Hindi: yes. Urdu: limited — no small model covers Urdu properly yet." The list is the curated 3 (LFM3-1.7B default, Qwen2.5-Coder-1.5B-Instruct, Qwen3-1.7B-Instruct-2507); nothing else is offered.
- **Quota:** header 56dp. `QuotaBar` 6dp-high capsule per provider (fill: `text` at 90% alpha, amber under 20%, danger at ~0) + usage lines 13sp ("Used <n> / <limit>") + 12sp mono reset date. No token counts in chat — this screen is the only place usage numbers appear.
- **Storage / Files:** rows 56dp (file/folder name 15sp + size 12sp mono + date 12sp mono); storage bar 10dp-high (5dp radius) with used/free 13sp; "Clear cache" 8dp button with confirmation sheet.
- **About X:** wordmark 28sp centred + "X" version lines 13sp mono ("Version <name> (<code>)", "Package com.mazharansari.x") + "Report a problem" 15sp row (email subject "X <version> problem"). No mascot, no logo image.
- **Call screen (Live call):** header 56dp ("Live call", back chevron ends the call only via the End button — back is disabled during a call). Centre: Orb 220dp (the circular voice visualisation — the deliberate circle exception, §7.9) + status 15sp ("Listening…" / "Speaking…" / "Paused"). Bottom: mute 64dp + end 64dp buttons (end = danger fill, 8dp rounded square — not a circle). In-call chips (Speaker / Earpiece, Boost): 6dp corners, 14sp Medium. Floating orb (over other screens): 68dp square tile (8dp corners) with the Orb inside + 24dp hang-up badge (danger fill, 6dp corners) at its top-right corner.
- **Terminal screen:** header 56dp; terminal area mono 13sp/20, `surfaceSunken` bg, 10dp corners; input row 48dp, 8dp corners, mono 13sp.

### 7.6 Workspace bottom sheet + all 7 workspace screens

- **Workspace sheet:** bottom sheet, 16dp top corners, drag handle 32×4dp (2dp radius), scrim, 200ms slide-up, max 85% height. Title "Workspace" 17sp SemiBold left-aligned, close × 40dp right. Rows 56dp, hairline-separated: icon 20dp + name 15sp SemiBold + subtitle 13sp `textSecondary` with **real** counts (Feed: "<n> need you" / "Nothing needs you"; Ideas: "<n> saved" / "No ideas yet"; Goals: "<n> active" / "No goals yet"; Library: "Files, notes and downloads"; Activity: "Runs, tasks and history"; Computer: "Projects, terminal and files"). CodeDiff is **not** listed. Tap a row → that screen; back → Home. Dismiss: swipe down or scrim tap.
- **Feed:** header 56dp ("Feed", back chevron). Items: cards 10dp (title 15sp SemiBold + body 13sp + time 12sp mono + action button 8dp when an item needs a decision). Empty: "Nothing needs you." + no fake items.
- **Ideas:** header 56dp. Rows 64dp (title 15sp SemiBold + preview 13sp + date 12sp mono + 6dp status chip). Tap → idea detail (same type scale, full text 15sp/22). Empty: "No ideas yet — ideas you save appear here."
- **Goals:** header 56dp. Rows 64dp (title 15sp SemiBold + progress bar 4dp + "<n>% · <done>/<total>" 12sp mono). Tap → goal detail (checklist rows 56dp, check = 6dp square with accent check, never a green circle). Empty: "No goals yet."
- **Library:** header 56dp. Rows 56dp (file name 15sp + type/size 12sp mono + date 12sp mono); search field 48dp 8dp corners at top. Empty: "No files yet."
- **Activity:** header 56dp. Rows 56dp (run/task title 15sp + status 12sp mono: "Running" accent / "Done" `textSecondary` / "Failed" danger + time 12sp mono). Tap → run detail (log lines 12sp mono, scrollable). Empty: "No activity yet."
- **Computer:** header 56dp. Projects list (rows 64dp: name 15sp SemiBold + kind 12sp mono + updated 12sp mono); terminal entry row 56dp ("Open terminal" → Terminal screen §7.5); files section rows 56dp. Empty: "No projects yet — ask X to build one in chat."
- **CodeDiff (per-project only, via chat card / Library):** header 56dp ("Code review", back chevron). Diff lines 13sp mono/20: added lines `accentSoft` bg + accent `+` gutter (bronze, never green); removed lines `dangerSoft` bg + danger `−` gutter; context lines plain. File header 36dp strip (`surfaceSunken`, filename 13sp mono + "Apply" 8dp button). Test result pill 6dp (neutral dark, "12 passed · 0 failed" — text only, no green dot).

### 7.7 Approval flow (exact look + animation)

- **Trigger:** the agent asks to run a command/tool that needs approval → the transcript pauses at that step and the approval dialog (§7.4) appears over the transcript (scrim 200ms fade-in, dialog fades + scales 140ms `FastOutSlowIn`).
- **Dialog:** 10dp corners, `card` bg, 16dp padding. Title 22sp SemiBold ("Allow this command?"). Body: the action summary in plain 14sp/20 (max 600 chars) + a 13sp scope line when the action is memorable ("'Always allow' remembers this kind of command (…); 'This chat' allows it only until you leave this chat, and is never stored.") or a danger 13sp line when it is not ("<reason>. This kind can never be remembered — X will ask every time."). Buttons are two rows of TextButtons, 15sp Medium: "Allow once" (accent), "This chat" (`text`), "Always allow" (`text`), "Decline" (danger). "This chat" / "Always allow" are hidden for hard (non-memorable) actions. Tapping one dismisses the dialog (140ms fade-out) and the transcript resumes; dismissing without tapping (scrim/back) counts as Decline. The decision is not written into the transcript as a line.
- **No silent approvals:** if the user never answers, the agent waits — nothing runs, nothing is auto-allowed, and no second dialog stacks on top.

### 7.8 Onboarding / splash (what remains)

- **Splash:** full-screen `bg`, wordmark "X▪" 28sp centred, ~700ms, tap anywhere to skip. No image, no mascot, no progress bar.
- **Intro (first run):** black background, wordmark 40sp rising in (scaleY + translationY, same `markIn` timing as before — 280ms `FastOutSlowIn`), bronze glow behind it (accent, alpha 0.28, no indigo). Tagline 15sp `textSecondary` below. "Continue" button 48dp, 8dp corners, accent fill.
- **Permissions onboarding:** icon tiles 12dp rounded squares (not circles), permission rows 56dp (icon 20dp + title 15sp + description 13sp), buttons 8dp (Allow 48dp accent fill; "Not now" / "Skip all" 48dp `surfaceRaised`), progress dots 3dp. No blue glow, no 50dp pills.

### 7.9 Documented exceptions (the only circles / capsules in the app)

| Shape | Where | Why it stays |
|---|---|---|
| 36dp send circle | Composer send button | Thumb target, not decoration (§2.3) |
| 8dp dots | Unread/working dot (Home rows), `DotGrid` thinking dots, PlanCard 9dp status ring, V04 9dp account dot | Dots, not pills — 8dp is the dot size |
| Voice Orb (220dp + 68dp floating tile) | Call screen + floating call orb | A circular voice visualisation by design; its container tile is an 8dp rounded square |
| Progress-bar capsule tracks | `QuotaBar` (3dp radius), Storage bar (5dp radius), DevTools install bars (3dp radius) | A bar's radius is half its height by geometry; the track is 4–10dp tall, never a pill button |
| 32×3dp heading rule | Markdown H1 rule (2dp radius) | A rule line, not a shape |
| 24dp hang-up badge on the floating orb | Floating call orb top-right | Part of the orb; 6dp corners in the rebuild (was 50dp) |

Everything else is 4–12dp rectilinear (cards 10dp, composer 12dp, rows 8dp, buttons 8dp, chips 6dp, sheets 16dp top corners). If a future screen introduces a new circle or pill, this table must be amended first — a circle without a row here is a spec violation.
