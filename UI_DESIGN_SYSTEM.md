# UI DESIGN SYSTEM — Hungry Ears

> Visual language. **Status: PHASE 4 – tokens, shell, library list rows and the persistent mini player (artwork, transport, shuffle/repeat) styled with tokens; system media notification uses Media3 defaults for now; polish lands per phase.**

The UI must read as a hand-built, premium native music app — not a generated dashboard. It is dark-first, typography-led, restrained, and fast.

---

## 1. Principles

- **Music-focused:** artwork and typography first; chrome last.
- **Minimal:** one primary action per screen; nothing decorative.
- **Calm:** no random gradients, no glassmorphism, no confetti animations, no dashboard cards.
- **Consistent:** tokens everywhere; components derive from tokens.
- **Fast, native:** standard Material touch targets, gestures, and system nav; Compose but platform-idiomatic.
- **Accessible:** WCAG-AA contrast, 48dp+ targets, full TalkBack labels, no color-only meaning.

## 2. Color system

- **Dark (default, preferred):** neutral near-black surfaces (e.g., `#0F0F12`), one accent (warm "hungry" amber/orange), minimal elevation via tone, not shadow-on-card.
- **Light:** system-friendly, same accent.
- **Dynamic color (Android 12+):** adopted only when it preserves contrast and the accent maps cleanly; otherwise fixed palettes.
- No multi-color random vibrancy. Emphasis is expressed through the accent ONLY.

## 3. Typography

- Primary typeface: system (Roboto / platform default) — native, always renders, no font payload.
- Scale: 5 sizes (display-sm for legacy, title-md for screens/artists, title-sm for items, body-md for text, label-md for meta chips). No mega-headings.
- Weights: regular/medium only for text; semibold only for numbers/timers. Leading tight on titles.

## 4. Size & spacing

- 4-pt grid; default rhythm 16; section gaps 24; list item height 64–72.
- Rounded corners: 12 (cards/tiles) / 8 (buttons) / full (originals on Now Playing). No "squircle everywhere".
- Iconography: Material Symbols (`material-icons-core` baseline + a small curated extended set), stroke-style consistency, 24dp.

## 5. Signature components

| Component | Spec |
|---|---|
| Mini Player | Full-width bottom bar: artwork 48, title+artist single-line ellipsized, play/pause + next, tap→Now Playing. Always above system nav. |
| Now Playing | Full artwork (respecting rounded-square crop fill), title, artist, progress bar (seekable, time both ends), transport row (shuffle, prev, play, next, repeat), actions row (favourite, queue, add-to-playlist, sleep timer later). Not a button wall — groups, minimal labels. |
| Track row | artwork 40, title, artist, overflow menu (play/next/fav/add-to-playlist/remove). |
| Album/Artist tile | one artwork, title one-line, optional subtitle; no useless "follow/composite" chips. |
| Section header | text label + optional "See all" text action — never buttons in a pill. |
| Empty/error states | artwork-glyph + one sentence + one affordance; no debug text. |

## 6. Screens inventory

1. **Home** — conditional, prioritized order (see RECOMMENDATION_ENGINE §4): Continue Listening → Recently Played → Made For You → Favourite Artists → Recently Added → Smart Mixes. Shows 2–3 sections at once on phone; content chosen by signals. No fake stats.
2. **Library** — segmented view: Tracks / Albums / Artists / Genres / Folders / Playlists; Favourites + Recently Played shortcuts; sort menu only where meaningful.
3. **Search** — one field, recent queries, instant results grouped (Tracks/Albums/Artists/Online) with rank ordering; source chips only when online active.
4. **Playlists** — list, thumbnail collage (2×2), editable; drag-reorder suffix.
5. **Now Playing** — the flagship; single gesture (swipe down) back; cover-forward design.
6. **Queue** — simple sheet/list: now playing row, up-next list, reorder/remove/clear.

## 7. Shared interactions

- Swipe-back / bottom-sheet drawer for queue and playlist-add (native feel).
- No parallax/scroll-jacking/auto-play previews.
- All async presents `Loading/Empty/Error` states with the shared component set.
- Haptics limited (button success, long-press start), no gimmicks.

## 8. Explicit anti-patterns (never)

Excessive cards · random gradients · glassmorphism · needless animations · giant headings · dashboard layouts · fake stats · button walls · "AI-powered" badges · emoji icons in UI · skeleton shimmer walls · marquees in lists · infinite scroll of identical rows.

## 9. Accessibility pass (per phase)

Contrast check on accent usage · touch targets ≥48dp/≥24dp spacing · TalkBack content descriptions for artwork/actions · focus order in Now Playing · dynamic type ≤1.3× no clipping test.