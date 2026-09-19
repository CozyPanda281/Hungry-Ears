# RECOMMENDATION ENGINE — Hungry Ears

> Deterministic, local, explainable. **Status: PHASE 1 – design agreed; engine not built.** PHASE 7 (recommendations) + PHASE 8 (autoplay).

No cloud AI. No ML service. A rules-based affinity model over raw behavioural signals is deliberately chosen: transparent, fast, unit-testable, and honest about why a recommendation is shown.

---

## 1. Signals (captured in PHASE 7)

| Signal | Source | Storage |
|---|---|---|
| plays (count, ongoing) | PlayerController.PLAYED | Room `events` |
| completion % (0–1) | position/duration at end | same |
| skips | next pressed <30% into track | same |
| replays | replayed start → 20%+ again | same |
| likes/favourites | toggle event | same |
| recently played | ordering events | same |
| frequently played | count | derived |
| artist/album/genre co-occurrence | both events + tags | derived table |
| recency | timestamp | same |

Cap: keep a bounded rolling window (e.g., last N=5000 events) — cheap, private, sufficient.

## 2. Affinity model

Per track & per artist/album/genre `affinity = Σ (weight_signal × occurrence) × decay(age)`:

- frequency: additive per count; completion ≥0.8 counts full, <0.3 counts 0.25
- skip subtracts; replay adds; favourite multiplies
- recency decay: half-life ≈ 30 days, re-normalized on read

Result: `TrackScore` + `ArtistScore` + `GenreScore` + co-occurrence edges (`A ⇒ B`: P(B played soon after A) from joint history).

## 3. Explainability

Every recommendation exposes `Explanation` strings the engine can prove:

- "Because you listened to *Album X* often"
- "Same artist as *Song Y* you liked"
- "Similar genre to *Z* you finished"
- "Because you play *Artist A* on weekends" (time-of-day/weekday features)
- "New release from an artist you follow" (only when such metadata exists)

UI may hide it, but the data is always present and asserted in tests.

## 4. Recommendation surfaces

`MadeForYou`: top tracks/albums by affinity, diversified (≤2 same artist/album in top section). `ContinueListening`: last mid-completed items surfaced. `FavouriteArtists`: top artists. `SmartMixes`: deterministic rule-mixes, e.g. "Calm Sundays" (soft genre flags where metadata gives tone), "Deep Cuts" (recent-but-rare). Sections render conditionally, never all at once (UI_DESIGN_SYSTEM §order).

## 5. Autoplay (PHASE 8)

`AutoplayEngine.generate(seed, signals) → QueueItem[]`:

1. Seed = current track (or last completed context: album if finishing an album).
2. Candidates = co-occurring artists (weighted), same-genre, same-artist non-recent, same-album up-next only when album context.
3. Constraints: diversity (≤3 same artist in 20), anti-repetition (no track already in queue; artist cooldown 5), min distance from recently played, weed out skips-heavy tracks.
4. Deterministic given (seed, signals, RNG seed) → testable.
5. Autoplay is **opt-in** (default off) and togglable; when off, queue ends naturally. Gap-fill only runs if the user enabled autoplay.

## 6. Privacy

All signals stay on-device (Room). "Clear history" wipes signals + derived tables and resets affinities. Nothing aggregated or exported.

## 7. Test plan

- Affinity ordering; decay math; skip penalties; diversification; autoplay diversity/no-immediate-repeat/disable; explanation-consistency ("reason" always matches the top contributing signal).