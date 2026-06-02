# Credential Manager

An Android app for securely storing and managing credentials, with end-to-end AES-256-GCM encryption and automatic Google Drive backup.

---

## Features

- **Encrypted vault** — Every credential is encrypted with AES-256-GCM via the Android Keystore before being written to disk or Drive
- **Google Drive sync** — Credentials are stored as a single encrypted vault file in your Drive `appDataFolder` (private to the app, invisible in your Drive UI)
- **Offline-first** — All reads and writes go to the local Room database first; Drive sync happens in the background
- **Biometric lock** — Biometric or device-credential authentication required on every launch
- **Auto-sync** — WorkManager syncs every 15 minutes and on every credential change
- **Screenshot protection** — `FLAG_SECURE` prevents screenshots and app-switcher previews
- **Clipboard auto-clear** — Copied passwords are cleared from the clipboard after 30 seconds

---


## Architecture

```
app/
├── data/
│   ├── auth/          # Google Sign-In, AuthRepository
│   ├── drive/         # DriveServiceFactory, DriveRepositoryImpl
│   ├── local/         # Room DB, CredentialDao, LocalCredentialRepository
│   └── vault/         # VaultSerializer (kotlinx.serialization)
├── di/                # Hilt modules (Auth, Database, Drive)
├── domain/
│   ├── model/         # Credential data class (@Serializable)
│   └── repository/    # DriveRepository interface
├── security/
│   ├── KeystoreManager.kt   # Android Keystore AES-256 key
│   └── VaultCrypto.kt       # AES/GCM/NoPadding encrypt/decrypt
├── sync/
│   ├── SyncManager.kt       # Pull-merge-push algorithm
│   ├── SyncWorker.kt        # @HiltWorker CoroutineWorker
│   └── SyncScheduler.kt     # WorkManager scheduling
└── ui/
    ├── auth/           # SignInScreen, AuthViewModel
    ├── biometric/      # BiometricLockScreen
    ├── credentiallist/ # CredentialListScreen, CredentialListViewModel
    ├── credentialdetail/ # CredentialDetailScreen, CredentialDetailViewModel
    ├── navigation/     # AppNavGraph, Routes
    ├── settings/       # SettingsScreen, SettingsViewModel
    └── theme/          # Material3 dynamic color theme
```

**Pattern:** MVVM + Repository + Hilt DI + offline-first Room

---

## Tech Stack

| Category | Library | Version |
|---|---|---|
| UI | Jetpack Compose + Material3 | BOM 2024.12.01 |
| DI | Hilt | 2.56 |
| Local DB | Room | 2.7.0 |
| Background sync | WorkManager | 2.10.1 |
| Drive API | google-api-services-drive | v3-rev20240521-2.0.0 |
| Auth | play-services-auth | 21.3.0 |
| Encryption | Android Keystore + JCE | — |
| Serialization | kotlinx.serialization | 1.7.3 |
| Navigation | Navigation Compose | 2.8.5 |
| Biometric | androidx.biometric | 1.2.0-alpha05 |
| Language | Kotlin | 2.1.0 |
| Min SDK | 24 (Android 7.0) | — |
| Target SDK | 35 | — |

---

## Getting Started

### Prerequisites

- Android Studio Meerkat or newer
- A Google account
- Google Cloud project with Drive API enabled

### 1. Clone the repository

```bash
git clone https://github.com/aashishvibhu/credentialmanagementandroid.git
cd credentialmanagementandroid
```

### 2. Set up Google Cloud Console

1. Go to [console.cloud.google.com](https://console.cloud.google.com) and create a project
2. Enable **Google Drive API** under *APIs & Services → Library*
3. Configure the **OAuth consent screen** (External, add your email as a test user)
4. Create an **OAuth 2.0 Client ID** → type: **Android**
   - Package name: `com.github.aashishvibhu.credentialmanagement`
   - SHA-1: run `./gradlew signingReport` and copy the debug fingerprint

### 3. Build and run

```bash
./gradlew assembleDebug
```

Install on a device or emulator that has Google Play Services.

> **Note:** The emulator must be a standard image (with Play Services), not an AOSP image.

---

## Sync Algorithm

```
1. Fetch remote vault modified time  (T_remote)
2. Compute local max updatedAt       (T_local)
3. If T_remote > T_local:
       download vault → decrypt → deserialize
       merge by id, keep credential with newer updatedAt
       replaceAll in local DB
4. If local has dirty entries OR no remote vault exists:
       serialize all credentials → encrypt → upload to Drive
       markAllClean in local DB
```

Conflict resolution is **last-write-wins** by `updatedAt` timestamp.

---

## Security Model

| Threat | Mitigation |
|---|---|
| Device lost / stolen | AES-256-GCM key in Android Keystore (hardware-backed on supported devices) |
| Drive storage access | `appDataFolder` scope — files are invisible to other apps and the Drive UI |
| Screenshot / recents leak | `FLAG_SECURE` on the activity window |
| Clipboard sniffing | Auto-clear after 30 seconds |
| Tampered vault file | GCM authentication tag — decryption throws on any modification |
| Unauthorized app launch | Biometric / device-credential prompt on every cold start |

---

## Running Tests

```bash
# Unit tests (JVM)
./gradlew test

# Instrumented tests (requires connected device or emulator)
./gradlew connectedAndroidTest
```

Key test classes:

| Class | Type | What it covers |
|---|---|---|
| `VaultCryptoTest` | Unit | Round-trip, random IV, unicode, tamper detection |
| `VaultSerializerTest` | Unit | Serialize/deserialize, unknown keys ignored |
| `LocalCredentialRepositoryTest` | Unit | Encrypt-on-save, decrypt-on-read, corrupted entry drop |
| `SyncManagerTest` | Unit | All 4 sync branches + merge logic + state transitions |
| `DriveRepositoryImplTest` | Unit | Create vs update, download, modifiedTime, auth guard |
| `CredentialDaoTest` | Instrumented | In-memory Room CRUD, ordering, dirty flag |

---

## Roadmap

See [PLAN.md](PLAN.md) for the full implementation roadmap.

- [x] Phase 1 — Project setup (Compose, Hilt, Room, Drive SDK)
- [x] Phase 2 — Google Sign-In & Drive authentication
- [x] Phase 3 — AES-256-GCM encryption layer
- [x] Phase 4 — Local Room database
- [x] Phase 5 — Google Drive repository
- [x] Phase 6 — Sync engine (WorkManager)
- [x] Phase 7 — Compose UI (all screens)
- [x] Phase 8 — Security hardening (biometric timeout, in-memory wipe)
- [x] Phase 9 — Full test suite
- [x] Phase 10 — Release prep (ProGuard, signed AAB)

---

## License

This project is for personal and educational use.
