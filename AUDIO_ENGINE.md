# AUDIO ENGINE — Hungry Ears

> Playback engine design. **Status: PHASE 4 — background playback live: the ExoPlayer + `MediaSession` run in `PlaybackService` (`MediaSessionService`), and `PlayerController` drives them through a `MediaController` behind the same `StateFlow<PlayerUiState>` surface.** Decided: Media3/ExoPlayer 1.11.0 (media3-exoplayer + media3-session).

---

## 1. Stack

- **Player:** Media3 `ExoPlayer` (audio-only), hosted by `PlaybackService` (a Media3 `MediaSessionService`).
- **Session/controls:** `MediaSession` → lock-screen, notification (custom via `PlayerNotificationManager` or Media3 Compose), headset/Bluetooth (AVRCP), Wear/auto-ready later.
- **Audio focus:** media3 `AudioFocusManager` (platform AudioFocus), with `DEFAULT_COMMAND` ducking preference and pause-on-loss semantics. Sonification is handled (media notifications respect ringer/silent modes via Media3 conventions).
- **Routing:** ExoPlayer uses the default platform audio route; route changes (Bluetooth connect/disconnect, wired headset) pause/resume according to OS broadcaster.

## 2. Formats (v1 target matrix)

| Format | Decoder path | Notes |
|---|---|---|
| MP3 | Media3 `Mp3Extractor` + platform/MediaCodec or built-in | Gapless via Xing/LAME header best-effort |
| WAV | Media3 extractor | PCM; trivial |
| FLAC | Media3 FLAC extractor + platform/MediaCodec | Native FLAC supported; no transcode |
| AAC / M4A (MP4/ADTS) | Media3 extractor + MediaCodec | Hua/LATM as supported by platform |
| OGG / OPUS | Media3 OggExtractor; Opus via platform/MediaCodec (API 21+) | Supported on modern devices |
| Other Android-supported (`mp2`, `amr`, `midi`, etc.) | Media3 generic + platform | Best-effort; fail → `UnsupportedFile` |

No resampling, transcoding, or volume-gain applied to source data unless the platform pipeline does it as part of normal playback. **Bit-perfect playback is not claimed** unless the device (output path, mixer, volume steps, audio HAL) can demonstrably achieve it; the honest behavior is "high-quality lossless playback through the platform mixer."

## 3. Gapless playback

- Implemented as best-effort: Media3 handles MP3 gapless (LAME header) and FLAC/OGG intrinsic properties; set `CompositingDecoderFactory`-equivalent gapless enablement if needed.
- Where the format/device can't do it (e.g., AAC streams with padding, Bluetooth A2DP buffering), we do not claim gapless.
- Consecutive `QueueItem`s from the same album are the primary gapless surface (no silence when the encoder has metadata for it).

## 4. Queue semantics

- `QueueManager` maintains: current list, cursor, repeat modes (`off|all|one`), shuffle mode on/off.
- Shuffle is a **fixed permutation** (built once per toggle with a seeded generator; persistence across launches is a later phase) so next/previous are stable and honest; skipping in shuffle follows the current shuffled order (no true-random re-roll -> avoids "same track" surprises). The current track is always kept at the front when shuffling.
- The shuffled/sequential order is baked into ExoPlayer's playlist (ExoPlayer's own shuffle stays off), which keeps gapless playback and repeat semantics native; repeat `all|one|off` maps directly to `ExoPlayer.repeatMode`.
- Queue supports: append, insert-after, remove, reorder, move-to-next, clear, restore-on-launch (edit/restore operations land with the queue screen in PHASE 6).
- **Known limitation:** the queue order lives UI-side in `QueueManager`, so if the OS kills the app process while `PlaybackService` keeps playing, the session audio continues but the UI cannot rebuild the queue until restore-on-launch lands in PHASE 6 (the session playlist itself is still intact in the service).
- Play-history recording is separate from queue; completion events and skip events are distinct signals.

## 5. Background playback

- `PlaybackService` (a `MediaSessionService`) owns the ExoPlayer and `MediaSession`; declared with `android:foregroundServiceType="mediaPlayback"` and started/bound by `PlayerController` via a `MediaController` + `SessionToken`. Same-package controllers are granted full player commands through `MediaSession.Callback.onConnect`.
- Permissions: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`; `POST_NOTIFICATIONS` requested at runtime on API 33+ (`MainActivity`).
- Notification: Media3's default `default_channel_id` notification — category `transport`, `NO_CLEAR`, artwork + title/artist, prev/play-pause/next, seek bar, and a queue entry through the session. Custom notification styling/queue UI is a later polish item.
- Lock-screen/status controls operate through MediaSession (no custom overlays).
- Media button routing: single tap, double/triple tap, long-press handled by MediaSession default (play/pause verified on device).
- Bluetooth: AVRCP metadata + transports via MediaSession; route-loss pauses playback (`setHandleAudioBecomingNoisy(true)`).
- `onTaskRemoved` stops the service when not playing, so it doesn't linger after the app is swiped away mid-pause.
- Android "background media hardening" (Android 14/15/16+ + Android 17 background audio changes, 2026): the service runs as a foreground `mediaPlayback` service while a session is playing (verified `isForeground=true types=0x2`); release-build validation across supported devices remains.

## 6. Offline / resilience

- Missing file: event → `PlaybackFailed` → auto-skip to next (user-visible state) → repair (dedupe in queue, mark stale in library).
- Corrupt/partial file: same path; nothing crashes.
- While online sources are absent (pre-PHASE 9) everything is offline by definition; after PHASE 9, any network hiccup only affects remote tracks, never local playback.

## 7. Error mapping (media)

```
MEDIA_ERROR → PlaybackFailed(user: "Can't play this track")
       │    short-circuit → track.error = true, skip
UNSUPPORTED format → UnsupportedFile(user: "Format not supported")
Authentication/DRM-like failures (none expected) → SourceUnavailable
IO on stream → SourceUnavailable / Offline  (only for remote tracks)
```
Player state machine in `PlayerUiState`: preparing → ready → playing → paused → ended → error(typed). Buffer/progress always exposed.

## 8. Output & volume honesty

- Volume handled by system/recommendation audio focus (no artificial loudness doubling).
- Ducts respect user "reduce" preferences only via platform ducking settings.
- Sleep timer + "pause when headphones removed" flags preserved as preferences (platform-backed `AudioManager` intents only where appropriate).