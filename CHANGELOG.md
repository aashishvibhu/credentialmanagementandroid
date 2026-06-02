# Changelog

All notable changes to Credential Manager are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [1.0.0] — 2026-06-02

### Initial Release

First complete implementation of the credential management app with
end-to-end Google Drive sync and AES-256-GCM encryption.

### Added

**Authentication**
- Google Sign-In with `DRIVE_APPDATA` scope (no Firebase required)
- Silent sign-in on relaunch — skips sign-in screen if session is still valid
- Sign-out clears local database before revoking the Google session

**Encryption**
- AES-256-GCM encryption via Android Keystore (hardware-backed key)
- Random 12-byte IV per encryption — identical plaintext never produces identical ciphertext
- GCM authentication tag verifies integrity on decrypt; tampered data throws immediately
- `kotlin.io.encoding.Base64` used throughout — works in both Android and JVM unit tests

**Local Storage**
- Room database with per-row encrypted blobs (one `Credential` JSON → one encrypted entity)
- `isDirty` flag marks records that have not yet been uploaded to Drive
- Offline-first: app is fully functional without network; syncs when connectivity returns

**Google Drive Sync**
- Single encrypted vault file (`vault.enc`) stored in `appDataFolder` — invisible to users
- Last-write-wins merge by `updatedAt` timestamp when remote vault is newer than local
- Pull → merge → re-upload on conflict; push-only when local has dirty entries
- WorkManager periodic sync every 15 minutes (network-required constraint)
- Immediate sync triggered on every credential save, delete, and app resume
- `@HiltWorker` + `HiltWorkerFactory` for dependency injection inside workers
- `IllegalStateException` (not signed in) is non-retryable; network errors retry up to 3×

**Credential Management UI (Jetpack Compose + Material3)**
- Credential List: searchable, swipe-to-delete with Snackbar undo, long-press copy (clipboard auto-clears after 30 s)
- Credential Detail: title, username, password (show/hide toggle), URL, notes; password strength indicator; 16-char random password generator
- Biometric Lock: shown on every cold start; skips gracefully on devices without biometric
- Settings: signed-in account, sync status, last sync time, Sync Now, Sign Out with confirmation

**Security**
- `FLAG_SECURE` on the activity window — blocks screenshots and app-switcher thumbnails
- Network security config blocks all cleartext HTTP traffic

### Technical Stack

| Layer | Library |
|---|---|
| UI | Jetpack Compose + Material3 |
| DI | Hilt 2.56 |
| Local DB | Room 2.7.0 |
| Background sync | WorkManager 2.10.1 |
| Drive API | google-api-services-drive v3-rev20240521-2.0.0 |
| Encryption | Android Keystore + JCE AES/GCM/NoPadding |
| Serialization | kotlinx.serialization 1.7.3 |
| Auth | play-services-auth 21.3.0 |
| Biometric | androidx.biometric 1.2.0-alpha05 |
| Navigation | Navigation Compose 2.8.5 |

### Known Limitations

- Biometric lock does not yet enforce a timeout — it shows on every cold start regardless of how long the app was backgrounded (Phase 8 will add 60-second timeout)
- No password import/export
- Single Google account per device installation

---

*For planned improvements see [PLAN.md](PLAN.md) — Phases 8–10 cover security hardening, full test suite, and release prep.*
