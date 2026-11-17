---
name: banner-design
description: How to compose professional e-commerce hero/campaign banners (Clawora + client stores). Product arrangement, hierarchy, spacing rules learned from research + big K-beauty stores (Olive Young, StyleKorean). Read BEFORE building any banner.
version: 1.0.0
author: User custom skill (ported from Muse)
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [design, banner, ecommerce, marketing, hero, product]
    related_skills: [local-frontend-design, meta-astryx-design]
---

# Banner Design — Mastered Rules

## The one law
A banner sells ONE message. Order of the eye: **offer/message → product → support line → CTA**. If two things shout, nothing is heard.

## Product arrangement (the part that keeps failing — follow exactly)
1. **Never stack cutouts on top of each other.** Overlapping separate product cutouts (big one in front, small ones peeking behind) reads as a cheap collage. Big stores avoid it because their group shots are ONE studio photograph, shot physically in one frame.
2. When compositing separate product photos, use a **clean ROW**: products stand side by side on ONE shared floor line, same baseline, clear gaps between them (gap at least ~12% of a product's width), nothing hidden behind anything else. Every product 100% visible, never cropped.
3. **Similar visual weight**: normalize heights so no product dwarfs another (tallest : shortest within ~1.25×). Tallest in the middle or at the end of the row, like a lineup.
4. **One-product banners are the safest professional format**: single hero product, large, right-of-centre (rule of thirds), with room to breathe. Use floating info chips (SPF, benefit) to fill space instead of extra products.
5. Max 3 products per banner. More than 3 = catalogue grid, not a banner.
6. Every product sits on a **ground shadow** (soft ellipse under the baseline) — floating products look pasted. A soft disc/arch behind the group is decoration, not a container: it must never clip a product.

## Layout system (mobile-first, Clawora)
- Mobile: headline block on top, product row along the bottom spanning the full width. Desktop: text left (~45%), products right.
- Generous empty space around products — whitespace is what makes it look premium; crowding is what makes it look cheap.
- Background/graphic elements must be quieter than the product (low contrast disc, soft gradient). If the eye goes to the decoration first, the decoration is wrong.

## Copy rules (user-approved style)
- Kicker pill (short caps) → headline → ONE big number/price if it's a sale banner → one support line (real facts only) → one CTA pill.
- Numbers must be real and verified from live store data (prices, % off). Never invent a discount.
- Text stays in English on the store (UI language rule).

## Offer-banner anatomy (researched + user-approved direction, 2026-10-15)
- **Centered layout wins for offer banners**: kicker pill (top, centered) → headline → **PRODUCT IN THE CENTER, big** → price/% row → CTA. Text never covers or crowds the product; the product is the hero in the middle. (Confirmed by pro banner/email designers on YouTube + GitHub hero components: auto-rotate ~5s, pause control, promo badge ON the visual.)
- An offer banner carries: LIMITED TIME pill, the product's real % OFF as a badge, price with strikethrough compare price, one CTA. Nothing else.
- White background, clean, no decorative discs/blobs behind products (user rejected them twice). Products straight, equal heights in a row, generous whitespace.

## Bundle offers (margin-safe — Clawora formula)
- Store pricing formula: selling price = cost + Rs 1,500 on EVERY product → each product carries a Rs 1,500 profit cushion.
- Therefore tiered mix-and-match offers can never lose money at these levels: **any 3 items → extra 10% OFF** (worst case 3 cheapest ≈ Rs 5,700 → Rs 570 off vs Rs 4,500 cushion); **any 5 items → extra 15% OFF** (worst case ≈ Rs 9,500 → Rs 1,425 off vs Rs 7,500 cushion). Industry sweet spot for bundle discounts is 10–20% — consistent.
- Always frame with anchoring: show the strikethrough total + "You save Rs X". Name bundles by routine/scenario ("The Glow Routine"), never "A+B Set".
- Bundle offers run as Panel discount CODES (e.g. GLOW3 / GLOW5) — creating codes is a money decision: user approves the offer math first.
- NEVER invent a discount % for a product; solo-banner % = the product's real live sale %.

## Clawora specifics
- Brand = black & white; banner colour comes from soft tints + the products themselves. No AI-generated product imagery — real product photos / rembg cutouts of real photos only.
- Reference grammar (user's chosen sites): Olive Young / StyleKorean hero = rotating designed banners, counter pill (1/3) + pause button bottom-right, thin progress line, swipe + autoplay ~4.5s.

## Update 2026-10-15 (evening)
- NO sparkle/star decorations in banners — user rejected them ("star acha nahi lag raha, hatao"). Clean background only; soft ground shadow under products is fine. Live banner set = bn2-img*.jpg (star-free), slider links = product-<handle>.html (verified pattern).
