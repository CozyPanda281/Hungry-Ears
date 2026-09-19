# SEARCH SYSTEM — Hungry Ears

> Unified search design. **Status: PHASE 5 — local search LIVE (FTS5 index + normalization + ranking all in the app; grouped results UI + recent queries + playlists/favourite hooks verified on `emulator-5554`). Projection and fuse to online sources are PHASE 9 — see §9.**

---

## 1. Goal

One search box returning the best answer first. Local library is the primary corpus; online results fuse in only when online sources are enabled (PHASE 9+). A simple query like **"History One Direction"** must rank *History — One Direction* at the top.

---

## 2. Local index (PHASE 5)

- **Store:** SQLite FTS5 virtual table over `tracks` columns `(title, artistName, albumTitle)`, with normalized text (see §3).
- **Sync:** rebuilt/incremented from the Room cache after each scan; background, non-blocking.
- **Coverage:** tracks; plus lightweight `albums` / `artists` / `genres` token tables so `search("Coldplay")` surfaces the artist row too.
- **Search doamins:** `tracks`, `albums`, `artists`, `genres`, `playlists`, plus (later) folders.

## 3. Normalization

Every searchable token is normalized: lower-case → strip diacritics (NFD + remove marks) → trim → collapse whitespace → keep digits/letters/hyphen/apostrophe boundaries. Optionally strip leading articles ("the", "an", "a", and Hindi "द"?) only for ranking boosts, not for matching — so *The Beatles* is still findable by "the".

## 4. Ranking (weights, v1)

Score(query, doc) = Σ weighted signals, all deterministic:

| Signal | Weight | Meaning |
|---|---|---|
| Exact title match | 100 | query ≈ normalized title |
| Exact artist match | 90 | query ≈ artist | 
| Title + artist combined | 95 | both hit on one track (the "History → History — One Direction" case) |
| Album exact | 85 | query ≈ album |
| Prefix/partial title | 60→40 | title starts with / contains tokens |
| Partial artist | 50→30 | artist starts with / contains tokens |
| Metadata relevance | 20 | genre/tag hit, album-near-hit |
| User history weight | ≤15 | whether/requency the user actually played the result |
| Favourite bonus | ≤10 | user faved the result |
| Popularity (legit metadata) | ≤10 | only where the provider exposes real play counts (online) |

Term matching uses FTS5 prefix handler (`token*`); multi-term queries AND-joined with per-token hits; missing any token lowers but does not remove a doc. All magnitudes are tuneable in one constants object so ranking is testable.

## 5. Fusion (local + online, PHASE 9)

`SearchRepository` runs local ranking and, if any online source is enabled and reachable, asks each `OnlineMusicSource.search()` concurrently (timeout ~4s), normalizes results into the same `Highlight` shape, then merges:

- Local exact → always before online.
- Online results interleaved after local *partial* tier; equal-tied online items de-duplicated by (title,artist,album) and marked `source:`.
- Online failure: silently degrade to local-only with `SourceUnavailable` flag shown subtly (never a broken screen).
- Debounced input (≈250 ms) keeps keystrokes instant.

## 6. States

`Idle (empty input) | Searching | Results | NoResults | SourceUnavailable (when only source failing) | Error(recoverable)`

- No results shows helpful suggestions (recent queries, spelling hint for near-misses) — never a dead end.
- Per-query "recent queries" list persisted (privacy-friendly, on-device, clearable).

## 7. Test plan (JVM unit tests)

- Exact > combined > partial ordering; multi-token AND/OR; diacritics; article-insensitivity; prefix match; no results; empty query; historical boost clique; favourite boost clamp; determinism on equal scores (stable tiebreak by title asc).