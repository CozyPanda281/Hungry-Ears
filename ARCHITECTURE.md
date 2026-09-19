# ARCHITECTURE — Hungry Ears

> Target architecture. **Status: PHASE 4 (foundation, package layout, the `player/` engine, and the `PlaybackService` media session in place). Later phases planned, not implemented — see ROADMAP.md.**
> Decisions D-001..D-019 in PROJECT_BIBLE.md are binding.

---

## 1. Shape

Single Gradle module `:app` (Kotlin + Compose), package-layered. Layer isolation is enforced by package visibility; internal modules are added only if build time or compile isolation genuinely demands it (they don't at this scale).

```
ui (Compose screens, ViewModels/state holders)
   │
   ▼
player (Media3 controller, MediaSessionService, queue, autoplay hooks)
   │
   ▼
domain (pure Kotlin: models, search, recommendations, autoplay, queue math)  ← no Android imports
   │
   ▼
data (repositories: library, history, favourites, playlists, settings, search)
   │
   ▼
datasource (MediaStore scanner, Room, DataStore, artwork, online providers)
```

Rules:
- `domain` never touches Android SDK, Media3, Room, or network. It is unit-testable on the JVM.
- `ui` never talks to Room/MediaStore/network directly — only through state holders and repositories.
- `data` is the only layer that owns persistence.
- All layer crossings are interfaces where substitutability matters (notably `MusicSource` and playback `Track`).

---

## 2. Package layout

```
com.hungryears.music/
├─ HungryEarsApplication.kt        # AppContainer construction
├─ MainActivity.kt                 # Single-activity Compose host
├─ di/AppContainer.kt              # Manual DI graph
├─ ui/
│  ├─ navigation/                  # Navigation Compose graph + destinations
│  ├─ theme/                       # Design system (see UI_DESIGN_SYSTEM.md)
│  ├─ home/        HomeScreen + HomeViewModel
│  ├─ library/     LibraryScreen + LibraryViewModel   (tracks/albums/artists/genres/folders/playlists)
│  ├─ album/       AlbumDetailScreen
│  ├─ artist/      ArtistDetailScreen
│  ├─ playlist/    PlaylistDetailScreen + edit UI
│  ├─ search/      SearchScreen + SearchViewModel
│  ├─ nowplaying/  NowPlayingScreen + NowPlayingViewModel
│  ├─ queue/       QueueScreen
│  ├─ playerbar/   MiniPlayer (persistent bottom bar)
│  └─ components/  shared Compose building blocks
├─ player/
│  ├─ PlaybackService.kt           # MediaSessionService: owns ExoPlayer + MediaSession
│  ├─ PlayerController.kt          # MediaController wrapper → StateFlow<PlayerUiState>, source-agnostic
│  ├─ PlayerUiState.kt             # PlayerUiState + typed PlaybackError mapping
│  ├─ QueueManager.kt              # queue/shuffle/repeat/history semantics
│  ├─ AutoplayBridge.kt            # asks domain AutoplayEngine to extend queue
│  └─ PlayerUiStateMapper.kt       # → Now Playing state
├─ domain/
│  ├─ model/        Track, Album, Artist, Genre, Folder, Playlist, PlaybackHistory
│  ├─ queue/        QueueLogic (pure)
│  ├─ search/       SearchEngine, Tokenizer, Ranking, SearchResult
│  ├─ recommend/    Signals, AffinityModel, RecommendationEngine, Explaination
│  └─ autoplay/     AutoplayEngine (rule-based), seed decision
├─ data/
│  ├─ repository/   LibraryRepository, HistoryRepository, FavouritesRepository,
│  │                PlaylistRepository, SettingsRepository, SearchRepository,
│  │                RecommendationRepository
│  ├─ db/           Room: entities, DAOs, HungryEarsDatabase, Converters
│  ├─ datastore/    SettingsDataStore (DataStore preferences)
│  ├─ scanner/      MediaStoreScanner → normalized Track records
│  ├─ artwork/      ArtworkLoader (MediaStore thumbs; online-aware later)
│  └─ online/       OnlineMusicSource + providers (Phase 9+)
└─ util/            IDs, time formatting, misc — Android-free where possible
```

---

## 3. MusicSource abstraction

All catalogs expose the same read interface; the player never cares where a track lives:

```kotlin
interface MusicSource {
    val id: SourceId                      // "local", "internet-archive", "jamendo", ...
    suspend fun search(query: SearchQuery): List<OnlineTrack>
    suspend fun resolve(uri: String): PlayableTrack  // file/http/custom
    suspend fun browse(root: BrowseNode?): List<BrowseNode>   // albums, collections, folders
}

sealed class Track {                      // display-level model, source-tagged
    data class Local(...)   : Track()

    data class Remote(      // from a legal, authorized provider
        val remoteId: String,
        val streamUri: String,
        val artworkUri: String?,
        val license: LicenseInfo?,        // per-source data for transparency
        ...
    ) : Track()
}
```

Local tracks flow into the same `QueueManager` as remote tracks. A `SourceRegistry` guards source enable/disable at runtime (privacy: online sources are opt-in).

---

## 4. Playback abstraction

```
MediaSessionService
   └─ ExoPlayer (Media3)
        ├─ MediaItem ← Track (via DefaultMediaSourceFactory + custom DataSource)
        ├─ MediaSession ← media buttons, notification, lock screen, Bluetooth, Wear
        ├─ gapless/repeat/shuffle via sequence manager
        └─ AudioFocus via Media3 audio focus APIs (delegating to platform)
```

`PlayerController` exposes a single `StateFlow<PlayerUiState>` that UI observes; one Now Playing model for online and local tracks.

---

## 5. Data flow

- **Library:** `MediaStoreScanner` (re)scans → Room `tracks` cache → repositories → ViewModels → UI. Search reads the Room cache + FTS5 index, never MediaStore directly.
- **Playback:** UI/queue events → `QueueManager` → `PlayerController` → ExoPlayer. `PlayerController` emits state → `PlayerUiStateMapper` → all screens.
- **History/signals:** `PlayerController` events (PLAYED/SKIPPED/COMPLETED/REPLAYED/FAVED) → `HistoryRepository`/`RecommendationRepository` (append-only, capped).
- **Online (Phase 9+):** UI query → `SearchRepository` → fuse local + enabled online sources → results ranked by `domain/search`. Playback resolves a `PlayableTrack` from the selected source.

---

## 6. Threading & async

- Coroutines everywhere; Room/suspend via `Dispatchers.IO` inside repos.
- `domain` engines are pure and run on any dispatcher (unit tests use `UnconfinedTestDispatcher`).
- ExoPlayer callbacks are marshalled to a single app-level scope; UI never blocks.

---

## 7. Errors

Errors never silently fail. Every layer emits typed errors surfaced as user states:

`Loading | Empty(Library) | NoSearchResults | Offline | SourceUnavailable | PlaybackFailed | UnsupportedFile | MissingArtwork | MissingMetadata`

- Media errors → `PlaybackFailed`, retry-able per track; media buttons do not die.
- Online failure never breaks local playback; online results are best-effort with explicit "source unavailable" state.
- Deleted/corrupt local file → user-visible "track unavailable, will be skipped" and offered removal from queue.
- All unexpected exceptions are caught at layer boundaries and mapped to a generic recoverable error plus a log line (no crash spam, no silent swallow).

---

## 8. Online policy (phase 9+)

- Online playback allowed **only** where technically and legally permissible and where the app does not violate the source's terms, auth, DRM, or paywalls.
- Required consent points, decided **before** any provider is added (D-007 candidates):
  1. **Internet Archive** — fully open public API, no credentials; most audio openly licensed or public domain. Legal for personal playback via streaming.
  2. **Jamendo** — official API, Creative-Commons catalog; free personal client_id; ToS-permitted streaming for private use.
- Both are additive, opt-in, and removable. No provider is bound into the architecture (D-006).
- **Explicitly excluded:** extracting streams from commercial services (NewPipe-style), private API keys, credential reuse, DRM circumvention, bulk downloading, anything violating a source's terms.
- If at some future point no legal ₹0 source is acceptable, the documented limitation stands (per PROJECT_BIBLE §3) and online stays turned off.

---

## 9. Testing strategy

- JVM unit tests: `domain` (search ranking, recommendations, autoplay, queue math, history semantics) — the majority of logic.
- Instrumented tests: scanner against a seeded MediaStore, Room DAOs, player/queue integration, Compose UI smoke tests.
- `:app:lintDebug` runs each phase; `:app:assembleRelease` is the gate for "Release-ready".