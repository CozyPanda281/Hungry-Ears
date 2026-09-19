# ROADMAP — Hungry Ears

> Source of truth for progress. A phase is **Done** only when its acceptance criteria pass and the build is verified.
> **Current status: PHASE 5 COMPLETE — local search (FTS5 index over a normalized `tracks` FTS table via Room's bundled SQLite driver, recent-query capture + clear, debounced search UI with ranked local results verified to play on device) and playlists (create/rename/delete/add/remove/reorder + detail screen) are all verified on `emulator-5554`. PHASE 6 (Now Playing UI + queue) is next.**

Milestone bar legend: `[ ]` not started — `[~]` in progress — `[x]` done — `[!]` blocked (reason recorded).

---

### PHASE 0 — Repository inspection + architecture

- [x] Inspect repository → empty directory, greenfield project
- [x] Propose architecture (ARCHITECTURE.md), decisions (PROJECT_BIBLE.md)
- [x] Dependencies, cost analysis, legal analysis (this doc §Deps/Cost/Legal)
- [x] Documentation set created (see index)
- [x] Toolchain audit → **Java 24 present; Android SDK, Gradle, Kotlin absent** — SDK must be installed to build (free, ~1–2 GB)
- [x] User approval of plan before PHASE 1

**Acceptance:** architecture reviewed and approved; docs consistent.

---

### PHASE 1 — Android project foundation + design system

- [x] Gradle wrapper (9.6.0), `libs.versions.toml`, application scaffolding, package `com.hungryears.music`
- [x] Compose + Material 3 theme (design tokens from UI_DESIGN_SYSTEM.md), typography, dark/light
- [x] Navigation skeleton: Home / Library / Search / Playlists
- [x] Empty states, error components, mini player placeholder slot
- [x] CI-friendly build entry (`assembleDebug`) + first unit test
- [x] Theme/visual render verification on an emulator — **AVD `he_test` created (Pixel 7, `system-images;android-36.1;google_apis;arm64-v8a`), APK installed, all four destinations verified via UI dump (Home/Library/Search/Playlists render their expected content, no crashes)**

**Build gate:** `./gradlew :app:assembleDebug` succeeds ✓ — theme renders on device ✓ (verified on `emulator-5554`).

---

### PHASE 2 — Local music discovery / library

- [x] MediaStore scanner (permissions SAF/READ_MEDIA_AUDIO), rescan strategy, notify-triggered refresh — `MediaStoreScanner` queries `Audio.Media`, derives folders from DATA/RELATIVE_PATH, best-effort genre mapping, permission-aware; `LibraryRepository` holds a `ContentObserver` with 1.5s debounce for notify-triggered refresh
- [x] Room schema v1: tracks, artists, albums, genres, folders (derived via GROUP BY), playlists, favourites, history/signals — entities + `LibraryDao`; schema exported to `app/schemas/…/1.json`
- [x] Library repository + browse (tracks / albums / artists / genres / folders) — domain models (pure Kotlin) + `LibraryRepository` flows + `LibraryViewModel`
- [x] Format coverage check: MP3/WAV/FLAC/AAC/M4A/OGG/OPUS (+ MP4/AIFF/AMR) whitelist; unsupported → `isSupported=false` (`SupportedFormats` + unit tests)
- [x] Artwork loading (MediaStore album-art `content://` URIs via Coil, with placeholder for missing art)
- [x] Deleted/corrupt file handling — stale-entry deletion via id diff on each scan; corrupt-file `PlaybackFailed` pathways land with the player in PHASE 3

**Build gate:** `assembleDebug` ✓, lint clean (0 errors) ✓, unit tests 6/6 ✓, instrumented scanner tests 2/2 on `emulator-5554` ✓ — **Library listing shows real device audio: 3 seeded WAVs appeared under Tracks/Albums/Artists/Genres/Folders.**
**Permissions:** `READ_MEDIA_AUDIO` (API 33+) / `READ_EXTERNAL_STORAGE` (≤32), requested at runtime with a permission gate + Settings fallback.

---

### PHASE 3 — Native audio playback engine

- [x] Media3 ExoPlayer wired through `PlayerController` (audio-only, low latency drain) — app-scoped `PlayerController` owns an audio-only ExoPlayer (`USAGE_MEDIA`/`CONTENT_TYPE_MUSIC`, audio focus, pause-on-becoming-noisy) and exposes one `StateFlow<PlayerUiState>`
- [x] Track queue: play from library, album, artist, genre, folder, playlist; next/previous — rows build real queues (album/artist/genre/folder via new DAO `getTracksFor*` queries); playlist playback lands with the playlist UI in PHASE 5
- [x] Shuffle + repeat modes (off/all/one) via QueueManager — `QueueManager` owns a fixed shuffled permutation (current track kept first, stable next/previous); repeat maps to ExoPlayer's native `REPEAT_MODE_ALL/ONE`
- [x] Gapless best-effort (see AUDIO_ENGINE.md — honest about limits) — the whole queue is handed to ExoPlayer as a playlist, so MP3/FLAC/OGG gapless stays native (no extra claims)
- [x] Seek/progress state, buffering state, completion detection — `PlayerUiState` exposes position/duration/buffering/playing/ended + up-next and button enablement; 500ms position ticker while playing
- [x] Unit tests: queue math, shuffle correctness, repeat semantics — `QueueLogicTest`, `QueueManagerTest`, `PlaybackErrorMapperTest`

**Build gate:** `assembleDebug` ✓, lint clean (0 errors) ✓, unit tests 27/27 ✓ — **on `emulator-5554`: tapping a track starts playback (audio focus acquired, files opened), the queue auto-advances `first_light → midnight_drive → paper_boats`, Previous/Next/seek work, repeat cycles off→all→one, shuffle toggles without interruption, and album/folder group playback works; no crashes or playback errors.**
**Note:** the engine was process-scoped in PHASE 3; PHASE 4 has since lifted it under a `MediaSessionService` without changing the `PlayerController` surface (it now wraps a `MediaController`). Playback `isSupported=false`/corrupt files map to `PlaybackError.UNSUPPORTED_FORMAT`/`PLAYBACK_FAILED` and auto-skip to the next track.

---

### PHASE 4 — Background playback + notification + media controls

- [x] `MediaSessionService` + `MediaSession`; foreground service with `mediaPlayback` type — `player/PlaybackService.kt` owns the ExoPlayer + `MediaSession`; `PlayerController` now wraps a `MediaController` (same `StateFlow<PlayerUiState>` surface), declared in the manifest with `FOREGROUND_SERVICE`/`FOREGROUND_SERVICE_MEDIA_PLAYBACK` and `foregroundServiceType="mediaPlayback"`
- [x] Media notification: artwork, title, artist, transport controls, seek bar, queue entry — Media3 builds the `default_channel_id` notification (category `transport`, `NO_CLEAR`, title/artist, seek bar, prev/pause/next)
- [x] Lock-screen / media resumption, headset & Bluetooth media buttons, AVRCP — served by `MediaSession`; media-button play/pause verified
- [x] Audio focus handling (duck/pause), pause on output route loss (e.g., Bluetooth disconnect) — `AudioAttributes(USAGE_MEDIA/CONTENT_TYPE_MUSIC)` with `handleAudioFocus=true` and `setHandleAudioBecomingNoisy(true)` on the service player
- [ ] Shortcuts/quick settings tile (optional, low risk)
- [ ] Android 15+ media hardening review (running in debug ok, release validation on supported devices)

**Build gate:** `assembleDebug` ✓, lint clean (0 errors) ✓, unit tests 27/27 ✓ — **on `emulator-5554`: playback continues with the screen off (foreground service `types=0x2`, notification posted), notification/media-button pause & play work, and audio focus is held (`USAGE_MEDIA`/`CONTENT_TYPE_MUSIC`, gain). Returning to the app reconnects the mini player to the running session (`morning_window`, "Up next: paper_boats").**
- **Scope note:** custom notification styling/queue-entry UI and the optional quick-settings tile remain; core background playback + system media controls are done.


---

### PHASE 5 — Search / library / playlists / history / favourites

- [x] SQLite FTS5 index over normalized titles/artists/albums/genres (tracks_fts, external-content over `tracks`, `unicode61 remove_diacritics 2`/`remove_diacritics 2` + ai/ad/au triggers) — **delivered via `BundledSQLiteDriver` (`androidx.sqlite:sqlite-bundled`): the platform's bundled SQLite has no FTS5 module (`no such module: fts5` on the emulator), so the DB now runs on androidx's bundled SQLite where FTS5 is compiled in; Room stays as the ORM and FTS is still created/maintained outside Room in `FtsIndex`**
- [x] Unified `SearchRepository` (local now; the online fuse socket is populated in PHASE 9) — FTS5 candidate retrieval (MATCH + bm25, LIMIT 300) fused with `LibraryRepository`/`PlaylistRepository` snapshots, ranked in `domain/search/` (`TextNormalizer` + `SearchRanking`, 300 candidate cap, history-per-play + favourite bonuses, article handling); debounced (250ms) ranked search in `SearchViewModel`
- [x] Recent queries (persisted `recent_queries` table, recorded ≥2 chars, recent-query chips + clear, re-usable store; FTS index built/rebuild-once with schema-gen flag)
- [x] Playlists: create/rename/delete, add/remove tracks, reorder (move up/down + remove from a per-row menu), play-all / per-track play, add-from-library "Add to playlist" dialog (incl. create-and-add)
- [x] Ranking tuned per SEARCH_SYSTEM.md (exact title/artist/album/genre → combined → prefix → contains → partial → metadata → history/favourites) with unit-test coverage
- [x] Empty/no-results states; async debounced search; favourites persisted (`favourites` table) with toggles in library + search rows; search results render grouped (Songs / Artists / Albums / Genres / Playlists) with artwork + favourite toggles + tapping plays the relevant queue

**Build gate:** assembleDebug ✓, lint clean (0 errors) ✓, unit tests pass ✓ — **verified on `emulator-5554`: FTS5 search returns expected rows (tapping a result plays the right track), favourites persist across relaunch, recents show after submitting for "mor", DB `user_version=2`, `tracks_fts` populated via the bundled driver.**

**Build gate:** assembleDebug + unit tests for ranking pass; search on a large seeded library returns expected order.

---

### PHASE 6 — Now Playing UI + queue

- [ ] Now Playing: artwork, title, artist, progress + precise seek, prev/next, play/pause, shuffle, repeat, favourite, queue drawer, add-to-playlist
- [ ] Queue screen (view/reorder/remove/clear), queue persistence
- [ ] Mini player bar wired; album/artist/folder detail screens polished
- [ ] Landscape + small-screen layout pass; talkback labels

**Build gate:** assembleDebug; full play-path exercised from every entry point.

---

### PHASE 7 — Local recommendation engine

- [ ] Signals model + capture (plays, completion %, skips, replays, favs, recency, time-of-day, artists/albums/genres)
- [ ] Affinity scoring (frequency × recency decay × completion × favourites), artist/album/genre co-occurrence
- [ ] `RecommendationEngine` with explainable reason strings; Home "Made For You" section
- [ ] Home personalization: Continue Listening, Recently Played, Favourite Artists, Recently Added, Smart Mixes — surfaced conditionally, prioritized

**Build gate:** assembleDebug + JVM unit tests for scoring and explanations; Home shows personalized rows after seeded history.

---

### PHASE 8 — Smart autoplay

- [ ] `AutoplayEngine`: deterministic queue extension from current track (seed → affinity → co-occurrence → diversity constraint), anti-repetition, skip-behaviour weighting
- [ ] Autoplay toggle (default off until user enables), edge rules when nothing to play
- [ ] Gap-fill when queue ends mid-session; "play similar" entry points

**Build gate:** assembleDebug + unit tests (diversity, no immediate repeat, respects disable).

---

### PHASE 9 — Online source architecture

- [ ] `OnlineMusicSource` interface finalization, `SourceRegistry`, opt-in enablement + per-source toggles
- [ ] HTTP datasource integration into ExoPlayer; offline/fallback semantics (net on/off, slow, source down)
- [ ] Streaming states (Loading/SourceUnavailable/Offline) end-to-end in UI
- [ ] Fuse socket in `SearchRepository` (local + online merge, ranked)
- [ ] **Decision checkpoint, no provider code yet:** confirm provider(s) with user (D-007 candidates, §Online policy in ARCHITECTURE.md)

**Build gate:** assembleDebug; all existing local features pass with network disabled.

---

### PHASE 10 — Implement only legitimate/authorized online integrations

- [ ] Implement approved provider(s) against official, free, legal APIs (candidates: Internet Archive, Jamendo). Nothing else without explicit discussion
- [ ] Respect licensing/attribution metadata where the source requires it; transparent "source/licence" info in UI
- [ ] Network/timeout/retry policy; caching of metadata (not content unless source permits)

**Build gate:** assembleDebug + instrumentation for provider parse/error paths; manual validation against live APIs.

---

### PHASE 11 — Security audit

- [ ] Audit per SECURITY.md checklist: exported components, cleartext, backups, permissions, dependency advisories, secure storage (when credentials ever exist), no secrets in repo, network policy
- [ ] Fix findings; produce the signed-off audit summary

**Build gate:** assembleRelease passes; audit log written into SECURITY.md.

---

### PHASE 12 — Performance optimization

- [ ] Profiling: cold start, library scroll, search latency, artwork cache, notify-thrash on rescan
- [ ] Memory: co-routine leaks, bitmap reuse, Room query plans (indexes)
- [ ] Battery: no polling, no unnecessary wake-ups, media service only during playback

**Build gate:** performance checklist passed; no regressions in lint.

---

### PHASE 13 — UI/UX polish

- [ ] Micro-interactions (constrained), consistent typography/spacing pass, dark-first polish, accessibility (contrast, touch targets, TalkBack wording)
- [ ] Release the full UI_DESIGN_SYSTEM audit

**Build gate:** assembleDebug; design-system checklist complete.

---

### PHASE 14 — Release APK build + testing

- [ ] Release signing setup (personal keystore, stored securely, never in repo)
- [ ] `assembleRelease`, strict lint, R8/minification config, crash-free manual soak on device (2+ days of personal use)
- [ ] Version/tag; APK delivered for personal install

**Build gate:** release APK installs and passes the soak checklist.

---

## Dependencies plan (target, audited in each phase)

Core (all free/open-source):
- Kotlin 2.3.21 + Compose compiler plugin (ships with Kotlin)
- AGP 9.4.0, Gradle wrapper, compile SDK 37, min SDK 26 (D-011)
- Compose BOM `2026.08.00` (ui, foundation, material3, ui-tooling, material-icons-core)
- Activity-compose 1.13.0, Lifecycle (viewmodel-compose, runtime-ktx)
- Navigation Compose 2.10.1
- Media3 1.11.0: `-exoplayer`, `-session`, `-common-ktx`, `-exoplayer-hls` (only if a provider needs HLS), `-extractor`
- Room 2.8.5 (+ ksp compiler 2.3.11)
- DataStore Preferences 1.2.1 (PHASE 4+ settings/state)
- kotlinx-coroutines (transitive via Lifecycle; explicit if needed), kotlinx-serialization-json (Phase 9+, JSON caching)
- OkHttp (Phase 9+ only, for online HTTP)
- Coil 3.6.3 (`coil-compose`) — album art now, remote artwork later
- Test: JUnit4, AndroidX Test (runner 1.7.0, rules, ext-junit 1.3.0), Room testing, Compose UI-test, Truth (as phases need)

Deliberately avoided: Hilt/Koin (manual DI), WorkManager (not needed yet), analytics, ads, crash-reporting, any cloud SDK.

## Cost analysis

- All above are ₹0 (FLOSS / free tier).
- Android SDK: free download; no licenses to buy; personal sideload APK — no Play fees.
- No paid APIs, databases, hosting, AI, analytics, or storage are introduced anywhere in this plan.
- If a future feature cannot be done at ₹0, it is flagged in PROJECT_BIBLE §6 before any work.

## Legal/technical analysis (online music, ₹0)

| Approach | ₹0? | Legal for personal use? | Risk | Decision |
|---|---|---|---|---|
| Internet Archive public API | Yes | Yes (open/public-domain catalog) | Catalog is niche/archival | Candidate D-007 |
| Jamendo official API (free CC client_id) | Yes | Yes; ToS-permitted private streaming | Non-mainstream catalog | Candidate D-007 |
| Free Music Archive API | Yes | Yes (CC) | Service availability is unreliable | Recheck at PHASE 10 |
| MusicBrainz / ListenBrainz (metadata only) | Yes | Yes | No audio | Auxiliary (metadata/enrichment) |
| NewPipe-style extraction (YouTube etc.) | Yes | **No** — violates service ToS/robots policy | High legal + account-ban risk | Excluded permanently |
| Commercial catalogs / Spotify / Apple | — | Requires paid subscription or licensed API | Paywall bypass is illegal | Out of scope |

**Bottom line:** legal ₹0 online audio is limited to open/CC/public-domain catalogs via authorized APIs. Hungry Ears gets open, honest, license-respecting online discovery — never a sketchy backdoor to commercial music. If the user later wants commercial streaming, that requires their own subscription or an official paid/partner API (needs explicit approval).