# SECURITY — Hungry Ears

> **Status: PHASE 4. Decisions recorded; hardening applied so far: no telemetry deps, backups disabled (`allowBackup=false`, full-backup + data-extraction rules), min-SDK-aware resource splitting, storage permissions limited to audio read (`READ_MEDIA_AUDIO` on API 33+, `READ_EXTERNAL_STORAGE` capped at `maxSdkVersion=32`), no network permission requested, all scanning and playback done on-device. PHASE 4 added only what background playback needs: `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` and runtime-requested `POST_NOTIFICATIONS` (API 33+); the `PlaybackService` is unexported to third parties by policy — it is exported only so the system/session can bind, and only same-package controllers get full commands. Full audit at PHASE 11.**
> Local-first by default. This is a personal single-user music player, not a multi-tenant service.

---

## 1. Threat model

| Threat | Exposure | Mitigation |
|---|---|---|
| Malicious/accidental network exfiltration of personal library | Low–Med | No upload; network only for opt-in online sources; no telemetry |
| Online source compromise feeding malicious content | Low | Only strict typed parsers; no remote code exec; no privilege; content only reachable via player |
| On-device data loss (app uninstall / device theft) | Med | Store nothing critical unencrypted where avoidable; no credentials in v1 |
| Compromised dependencies | Low–Med | Minimal dependency set; versions pinned; review upstream advisories each phase |
| Media file path/URI injection | Low | Paths come from MediaStore/SAF; treat URIs as opaque, never execute |
| OS-level permission creep | Low–Med | Least privilege (§3) |

---

## 2. Principles

1. **Local-first.** Full functionality offline.
2. **No telemetry, analytics, ads, or crash reporters.** By design, not just by toggle.
3. **No uploading local music** — ever.
4. **No unnecessary permissions** — see matrix below.
5. **No secrets in the repository.** Zero API keys in v1. Any future credential is stored in Android Keystore (EncryptedSharedPreferences / Keystore), never strings.xml, never BuildConfig, never VCS.
6. **No arbitrary remote code execution.** Online content parsed by strict codecs/parsers only; no JavaScript, no webviews for playback.
7. **No unnecessary background services.** Only the media session service during active/expected playback.
8. **No unnecessary network access.** Internet permission exists **only** because online sources opt-in; if the user never enables an online source, the app makes no network calls.
9. **Honest error handling** — no silent failures that could hide compromise.

---

## 3. Android permission matrix (target)

| Permission | Needed for | When |
|---|---|---|
| `READ_MEDIA_AUDIO` (API 33+) / `READ_EXTERNAL_STORAGE` (≤32) | On-device music discovery via MediaStore | Runtime request, only once, with rationale |
| `POST_NOTIFICATIONS` (API 33+) | Media notification | Request when first needed |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Background playback | Media3 `MediaSessionService` |
| `FOREGROUND_SERVICE` media playback minor type (API 34+) | Media3 requirement | Manifest only |
| Internet (`android.permission.INTERNET`) | Online sources | Manifest, guarded by user opt-in (no network without enable) |
| **NOT used:** `ACCESS_FINE/COARSE_LOCATION`, `READ_CONTACTS`, microphone, camera, SMS, account, call logs, all background location | — | Never |

Storage: **no `MANAGE_EXTERNAL_STORAGE`** — MediaStore/SAF only. No global file access.

---

## 4. Data classification + handling

| Data | Where | Security |
|---|---|---|
| Play history, favourites, playlists, queue | Room (app-private) | On-device; backed by platform file encryption |
| Scan cache (titles, artists, artworks) | Room + MediaStore thumbs | app-private |
| Preferences | DataStore (app-private file) | On-device |
| Signs/affinity model | Room | On-device, deletable via "clear history" |
| Online: stream/metadata cache (if a provider allows) | app-private cache dir | Content-Disposition honored; strings never logged |

No data ever leaves the device except explicit online streaming requests made by the user to an opt-in source.

---

## 5. Network policy

- **Default: offline.** Until the user explicitly enables an online source, no HTTP calls are made and `INTERNET` is effectively dormant.
- Network Security Config: forbid cleartext by default; per-provider rules only where a provider legally requires it (secondary-only, documented).
- TLS ≥1.2 enforced; certificate pinning is **not** used for personal-use flexible providers (adds breakage risk), but strict hostname verification stays.
- All provider traffic uses the official, documented, free endpoints — never private API keys.

---

## 6. Build & release security

- Release keystore: generated locally, password-protected, **excluded from the repository** (`.gitignore`), backed up out-of-band. Never checked in.
- `buildConfigFields`/`resValues`: no secrets.
- Dependency versions pinned in `gradle/libs.versions.toml`; updates reviewed (CHANGELOG + advisory scan) in each phase.
- Verify supply chain: Gradle wrapper SHA-256 pinned; wrapper jar from gradle.org; plugins from Google/Maven Central only; avoid snapshot/alphabet sources.

---

## 7. Audit checklist (run at PHASE 11 and keep updated)

- [ ] No secrets in repo (grep: `api[_-]?key`, tokens, passwords, keystores)
- [ ] Manifest: exported components audit (`android:exported` correct; no exported receivers/services from our code)
- [ ] Cleartext audit (NetworkSecurityConfig)
- [ ] Backup: decide `allowBackup` semantics (prefer `android:allowBackup="false"` or backup rules excluding tokens/DB until clearly understood and stated here)
- [ ] Permission requests verified to match actual usage (aapt dump permissions on release APK)
- [ ] Dependency advisory scan (e.g., OSV/GitHub advisories) for the pinned set
- [ ] No WebView used for anything media-related
- [ ] Log hygiene: no PII/titles/paths in logs; strip in release (R8 rules)
- [ ] R8/minify enabled for release; keep rules minimal and owned
- [ ] Crash pages don't expose stack traces to UI (logged only)

## 8. Future accounts note

If accounts/tokens are ever added (e.g., a user's own provider account), they are stored via Android Keystore-backed encryption, never WebView-bridged, and the threat model + this doc are updated before any code ships.