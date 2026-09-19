# PROJECT BIBLE — Hungry Ears

> Master specification for the Hungry Ears personal Android music player.
> **Status: PHASE 5 (local search — FTS5 index, normalization, ranking, debounced search UI with recent queries; playlists create/rename/delete/add/remove/reorder + detail screen; persisted favourites everywhere; playback history captured behind the engine). Verified on `emulator-5554`. Phases ≥6 remain only until marked complete in ROADMAP.md.**

---

## 1. Purpose

Hungry Ears is a **personal, native Android music application** that combines:

1. A high-quality local music player for the user's own on-device audio files.
2. Online music discovery/playback through technically and legally permissible sources.
3. Fast, relevant unified search.
4. Deterministic, local, explainable recommendations.
5. Intelligent autoplay and queue generation.
6. Playlists, favourites, and listening history.
7. A minimal, premium, fast, native UI.
8. Strong privacy and security.
9. Offline-first behaviour.

This is **not** a demo, prototype, toy, or generic generated dashboard. It is a serious personal production-quality application.

---

## 2. Non-negotiable principles

| # | Principle | Enforcement |
|---|-----------|-------------|
| 1 | Native Android first. Kotlin + Jetpack Compose + Android SDK + Media3. | Architecture locked in ARCHITECTURE.md |
| 2 | ₹0 development cost. | Never introduce paid services without explicit approval (see §6) |
| 3 | No fake/mock functionality in the final implementation. | Every claimed feature must exist and be tested |
| 4 | Local-first. The player must work fully offline. | Online layers are optional and additive |
| 5 | No illegal or ToS-violating online access. | Online source rules in ARCHITECTURE.md §Online |
| 6 | Privacy by default. | SECURITY.md |
| 7 | No unnecessary dependencies, permissions, background services, or analytics. | Reviewed in every phase |
| 8 | Errors never silently fail. | Error-handling contract in ARCHITECTURE.md §Errors |
| 9 | Documentation reflects reality. | Docs updated as features land; never ahead of implementation |
| 10 | Buildable after every major phase. | Verified per phase in ROADMAP.md |

---

## 3. Hard scope

**In scope (v1 core):** local playback (MP3/WAV/FLAC/AAC/M4A/OGG/OPUS + others Android supports), MediaStore scanning, folders, metadata + artwork, favourites, playlists, history, queue, shuffle/repeat, background playback + notification + lock-screen + headset/Bluetooth controls, unified search, local recommendations, autoplay, Now Playing UI, Home/Library/Search/Playlists screens.

**In scope (v1 optional, provider-gated):** legal online discovery/playback via free public APIs (see §Online in ARCHITECTURE.md). No provider is locked in; each is discussed before implementation.

**Explicitly out of scope:**
- DRM content and any DRM bypass.
- Bypassing authentication or paid subscriptions.
- Credential theft or use of non-public credentials.
- Scraping/harvesting content in violation of a source's terms.
- NewPipe-style extraction of commercial services (YouTube, Spotify, etc.).
- Claiming commercial music is "legally available" just because it is technically reachable.
- Lossy transcoding of local files, bit-perfect claims unsupported by the device.
- Cloud accounts, syncing, or cloud storage.
- Analytics, advertising, telemetry.

---

## 4. Glossary

| Term | Meaning |
|------|---------|
| `Track` | An audio item with a playback URI, regardless of source. |
| `MusicSource` | Abstraction over a catalog of tracks (local, online, future). |
| `PlaybackSource` | A physical provider of a playable URI (file path, http, etc.). |
| `Queue` | An explicit user-editable playback list. |
| `Autoplay` | Automatic queue extension after the current queue ends (or near its end), based on what is playing. Disabled by default setting is user-controllable. |
| `Now Playing` | The full-screen playback UI. |
| `Mini Player` | Compact persistent playback bar. |
| `Signal` | A recorded playback behaviour fact (played, skipped, completed, faved). |
| `FTS` | Full-text search index. |

---

## 5. Decision record

Every locked architectural decision is recorded here (reverse-chronological). "Proposed" items need approval before implementation; "Accepted" items are binding.

| ID | Status | Decision |
|----|--------|----------|
| D-001 | Accepted | Single `:app` module, package-layered (no premature multi-module). Rationale: solo device app, keeps Gradle build minimal; layers are enforced by package boundaries. |
| D-002 | Accepted | Manual DI through a lightweight `AppContainer` (no Hilt/Koin). Rationale: zero extra dependency, explicit, sufficient. |
| D-003 | Accepted | Media3/ExoPlayer via `MediaSessionService` as the single playback engine. Handles background, notification, lock-screen, media buttons, audio focus, gapless. |
| D-004 | Accepted | Room (SQLite) for all persistent structured state: library cache, playlists, favourites, history, signals, queue restore. DataStore for settings. |
| D-005 | Accepted | Compose Material 3, dynamic dark-first theme, no third-party UI kit. |
| D-006 | Accepted | Online music via pluggable `OnlineMusicSource` interface; no provider decided yet (see D-007/D-008). |
| D-007 | Accepted | First online integration candidates (Phase 10, subject to re-discussion): Internet Archive public API (no credentials) and Jamendo official API (free client_id, CC licensed). NewPipe-style extraction is excluded (legal). |
| D-008 | Accepted | Search: SQLite FTS5 index over normalized local library + fused online results. Ranking tuned in SEARCH_SYSTEM.md. |
| D-009 | Accepted | Recommendations: local deterministic engine over raw signals + affinity scoring; rules-based and explainable; no ML service. |
| D-010 | Accepted | Autoplay: deterministic queue generator honoring diversity/anti-repetition, user-readable rules, disable toggle. |
| D-011 | Accepted | minSdk 26 (Android 8.0). |
| D-012 | Accepted | Package `com.hungryears.music`. |
| D-013 | Accepted | MediaStore is the source of truth for local audio; the Room `tracks` table is a derived cache rebuilt/updated on each scan (id-diff for stale entries). User-owned data (favourites, playlists, history/signals) is never wiped by scans and cascades only when its track is genuinely gone. |
| D-014 | Accepted | One domain model (`domain/model`) is the shared model for data, domain, and UI — no parallel `*Ui` copies for library entities. UI-only formatting stays in `ui/util`. |
| D-015 | Accepted | Coil 3 (`coil-compose`) is the image pipeline for local album art now and remote artwork later. |
| D-016 | Accepted | Queue order is owned by `QueueManager`/`QueueLogic`, not ExoPlayer's shuffle. Shuffle is a fixed permutation that keeps the current track first; the resulting order is baked into ExoPlayer's playlist (native shuffle off) so gapless playback is preserved, and repeat `off/all/one` maps to ExoPlayer's native `repeatMode`. |
| D-017 | Accepted | The playback engine is app-scoped (`AppContainer.playerController`). PHASE 4 moved the ExoPlayer into `player/PlaybackService` (`MediaSessionService`); `PlayerController` now connects with a `MediaController` and keeps the same `StateFlow<PlayerUiState>` surface, so UI code was unchanged. |
| D-018 | Accepted | Playback failures are typed (`PlaybackError.UNSUPPORTED_FORMAT` / `PLAYBACK_FAILED`) and auto-skip forward one track; the queue stops cleanly at the end rather than crashing on a bad file. |
| D-019 | Accepted | The media session is the single control surface: the notification, lock screen, media buttons and (later) Wear/Auto/AVRCP all act on the same `MediaSession`. The app's own UI is just another `MediaController`, so there is no second playback path to keep in sync. |
| D-020 | Accepted | FTS5 runs on the **bundled SQLite driver** (`androidx.sqlite:sqlite-bundled` via Room's `BundledSQLiteDriver`), not the platform driver: the platform emulator/device SQLite is compiled without the FTS5 module (`no such module: fts5`), which broke `CREATE VIRTUAL TABLE ... USING fts5`. The bundled SQLite (`sqlite3` compiled with `-DSQLITE_ENABLE_FTS5`) guarantees FTS5 everywhere, at the cost of ~2MB of APK. The FTS5 virtual table + triggers are still created outside Room's schema (`FtsIndex`), and Room identity/`user_version` now points at the bundled DB file. |

---

## 6. Cost policy

- All dependencies, APIs, and services are FLOSS or free tier unless explicitly approved.
- **Nothing in this plan requires payment.** Any future paid feature (packages, services, keys) must be flagged and approved here before implementation.
- Do not add paid libraries, hosted databases, AI APIs, analytics, or cloud storage.
- Build/sign a release APK for personal sideloading; no Play billing needed.

---

## 7. Documentation index

| Document | Purpose |
|----------|---------|
| PROJECT_BIBLE.md (this) | Master spec, principles, scope, decisions, cost policy |
| ARCHITECTURE.md | Layers, modules, data flow, MusicSource/player abstractions, online policy |
| ROADMAP.md | Phased plan with acceptance criteria; the source of truth for progress |
| SECURITY.md | Threat model, permissions, data handling, network policy |
| AUDIO_ENGINE.md | Playback engine, formats, gapless, background/media-session behaviour, audio focus, error mapping |
| SEARCH_SYSTEM.md | Unified search: index, normalization, ranking, merging, states |
| RECOMMENDATION_ENGINE.md | Signals, affinity scoring, explainability, autoplay generation |
| UI_DESIGN_SYSTEM.md | Design tokens, components, screens, anti-patterns, accessibility |

Docs are kept synchronized with ROADMAP.md status.

---

## 8. Quality bar

- Builds pass after every major phase (`./gradlew :app:assembleDebug` plus unit tests).
- Lint clean on non-trivial rules (`./gradlew :app:lintDebug` — informational issues triaged).
- Release build `:app:assembleRelease` must succeed before a phase is considered "Release-ready".
- No feature is marked done until verified manually on device/emulator for the primary paths and covered by unit tests where the logic is non-trivial (search ranking, recommendations, autoplay, queue math, history semantics).