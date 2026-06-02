# Credential Manager — Implementation Plan

**App:** `com.github.aashishvibhu.credentialmanagement`  
**Package root:** `app/src/main/java/com/github/aashishvibhu/credentialmanagement/`  
**Min SDK:** 24 | **Target SDK:** 36  
**Stack:** Jetpack Compose · MVVM · Hilt · Room · Android Keystore · Google Drive (`appDataFolder`) · WorkManager

Mark each checkbox `[x]` when the step is complete before moving to the next phase.

---

## Phase 1 — Project Setup & Dependencies

**Goal:** Buildable skeleton with all libraries configured. No feature code yet.

### 1.1 Enable Kotlin & Compose in plugins
File: `build.gradle.kts` (root)

- [x] Add `kotlin-android` plugin alias
- [x] Add `ksp` plugin alias (for Room + Hilt annotation processing)
- [x] Add `hilt-android-gradle-plugin` alias

File: `app/build.gradle.kts`

- [x] Apply plugins: `kotlin-android`, `kotlin-compose`, `ksp`, `hilt`
- [x] Enable `buildFeatures { compose = true }`
- [x] Set `kotlinOptions { jvmTarget = "11" }` (Kotlin 2.x handles compose compiler automatically)
- [x] Add `packaging` block to exclude conflicting META-INF files from Google Drive SDK

### 1.2 Add all dependencies
File: `gradle/libs.versions.toml`

Versions used:
```
kotlin              = "2.1.0"
ksp                 = "2.1.0-1.0.29"
composeBom          = "2024.12.01"
hilt                = "2.53"
room                = "2.7.0"
work                = "2.10.1"
googleAuthLibrary   = "1.28.0"
googleApiServicesDrive = "v3-rev20240521-2.0.0"
googleHttpClientAndroid = "1.44.2"
securityCrypto      = "1.1.0-alpha06"
biometric           = "1.2.0-alpha05"
navigationCompose   = "2.8.5"
```

Dependencies added in `app/build.gradle.kts`:
- [x] Compose BOM + `material3`, `ui`, `ui-tooling`, `activity-compose`, `navigation-compose`, `material-icons-extended`
- [x] `hilt-android` + `hilt-compiler` (ksp) + `hilt-navigation-compose` + `hilt-work`
- [x] `room-runtime` + `room-ktx` + `room-compiler` (ksp)
- [x] `work-runtime-ktx`
- [x] `play-services-auth` (Google Sign-In)
- [x] `google-api-services-drive` + `google-http-client-android` + `google-auth-library-oauth2-http`
- [x] `androidx.security:security-crypto`
- [x] `biometric`
- [x] `kotlinx-coroutines-android`
- [x] `kotlinx-serialization-json`
- [x] `lifecycle-viewmodel-compose`, `lifecycle-runtime-ktx`

### 1.3 Google Cloud Console setup (manual steps — see below)
- [ ] Create a project at console.cloud.google.com
- [ ] Enable **Google Drive API**
- [ ] Create OAuth 2.0 credential → type: **Android**
- [ ] Note the **Web Client ID** for use in Phase 2

### 1.4 Update AndroidManifest.xml
- [x] Add `INTERNET` and `USE_BIOMETRIC` permissions
- [x] Add `android:usesCleartextTraffic="false"`
- [x] Add `android:networkSecurityConfig="@xml/network_security_config"`
- [x] Add `android:name=".CredentialApp"` on `<application>`
- [x] Add `android:windowSoftInputMode="adjustResize"` on `<activity>`

### 1.5 Application class & Entry point
- [x] Created `CredentialApp.kt` — `@HiltAndroidApp class CredentialApp : Application()`
- [x] Created `ui/theme/Theme.kt` — Material3 dynamic color theme
- [x] Updated `MainActivity.kt` — `@AndroidEntryPoint`, `ComponentActivity`, `FLAG_SECURE`, Compose `setContent`
- [x] Created `res/xml/network_security_config.xml` — cleartext disabled

**Phase 1 complete when:** project builds and launches a blank Compose screen.

---

## Phase 2 — Google Sign-In & Drive Authentication

**Goal:** User can sign in with Google; app holds a valid Drive token.

### 2.1 Auth data layer
Directory: `data/auth/`

- [x] `AuthState.kt` — sealed class: Idle | Loading | SignedIn(email) | Error(message)
- [x] `GoogleAuthClient.kt` — wraps `GoogleSignInClient`; scope `DriveScopes.DRIVE_APPDATA`; `silentSignIn()`, `signOut()`, `hasRequiredScopes()`
- [x] `AuthRepository.kt` — interface with `authState: StateFlow<AuthState>`
- [x] `AuthRepositoryImpl.kt` — checks cached account first, falls back to silent refresh; no EncryptedSharedPrefs needed (GoogleSignIn persists itself)

### 2.2 Hilt module
File: `di/AuthModule.kt`
- [x] `@Provides @Singleton GoogleSignInOptions` — requests email + `DRIVE_APPDATA` scope
- [x] `@Provides @Singleton GoogleSignInClient`
- [x] `@Binds @Singleton AuthRepository` → `AuthRepositoryImpl`
- Note: `GoogleAuthClient` uses `@Inject constructor` so no explicit `@Provides` needed

### 2.3 ViewModel
File: `ui/auth/AuthViewModel.kt`
- [x] `authState: StateFlow<AuthState>` from repository
- [x] `silentSignIn()` called in `init` — auto-runs on ViewModel creation
- [x] `getSignInIntent()` — returns sign-in Intent
- [x] `handleSignInResult(data)` — processes activity result
- [x] `signOut()`

### 2.4 Sign-In Screen
File: `ui/auth/SignInScreen.kt`
- [x] Lock icon + app title + subtitle
- [x] `OutlinedButton` launches sign-in via `rememberLauncherForActivityResult`
- [x] `CircularProgressIndicator` shown during `Loading` state
- [x] `LaunchedEffect` navigates to credential list on `SignedIn`
- [x] Error message shown inline below button

### 2.5 Navigation & MainActivity wiring
File: `ui/navigation/AppNavGraph.kt`
- [x] `Routes` object with `SIGN_IN` and `CREDENTIAL_LIST` constants
- [x] `NavHost` starts at `sign_in`; on sign-in navigates to `credential_list` (popping sign_in)
- [x] Placeholder `credential_list` composable for Phase 7

File: `MainActivity.kt`
- [x] Replaced placeholder `Surface` with `AppNavGraph()`
- [x] Silent sign-in triggers automatically via `AuthViewModel.init`

**Phase 2 complete when:** user can sign in with Google and the account is persisted across restarts.

---

## Phase 3 — Data Model & Encryption Layer

**Goal:** Define the `Credential` model and a working AES-256-GCM encrypt/decrypt utility.

### 3.1 Domain model
File: `domain/model/Credential.kt`
- [x] `@Serializable data class Credential` with id (UUID default), title, username, password, url, notes, createdAt, updatedAt

### 3.2 Android Keystore key management
File: `security/KeystoreManager.kt`
- [x] `getOrCreateKey(): SecretKey` — checks Keystore first, generates if absent
- [x] AES-256, GCM, NoPadding, `setUserAuthenticationRequired(false)`
- [x] Key alias: `"credential_vault_key"`

### 3.3 Vault encryption utility
File: `security/VaultCrypto.kt`
- [x] `encrypt(plaintext)` — random 12-byte IV, AES/GCM/NoPadding, returns Base64(IV + ciphertext + GCM tag)
- [x] `decrypt(encoded)` — splits IV at byte 12, verifies GCM tag, returns plaintext
- [x] Uses `kotlin.io.encoding.Base64` (Kotlin stdlib, works in both JVM tests and Android)

### 3.4 Vault serialization
File: `data/vault/VaultSerializer.kt`
- [x] `serialize(List<Credential>): String` via `kotlinx.serialization` JSON
- [x] `deserialize(String): List<Credential>` with `ignoreUnknownKeys = true`

### 3.5 Hilt wiring
- [x] No explicit `SecurityModule` needed — `KeystoreManager`, `VaultCrypto`, and `VaultSerializer` all use `@Singleton @Inject constructor` so Hilt injects them directly

### Unit tests
- [x] `VaultCryptoTest`: round-trip, distinct ciphertexts (random IV), empty string, unicode, tampered ciphertext throws — mocks `KeystoreManager` with a plain JVM AES key
- [x] `VaultSerializerTest`: full list round-trip, empty list, optional field defaults, unknown keys ignored
- [x] `./gradlew test` → BUILD SUCCESSFUL, all tests pass

**Phase 3 complete when:** unit tests pass for encrypt → decrypt round-trip with a known plaintext.

---

## Phase 4 — Local Room Database

**Goal:** Offline-first local cache with per-row encryption and a dirty flag for sync.

### 4.1 Room entity
File: `data/local/CredentialEntity.kt`
- [x] `CredentialEntity` — `@PrimaryKey id`, `encryptedBlob`, `updatedAt`, `isDirty`

### 4.2 DAO
File: `data/local/CredentialDao.kt`
- [x] `getAll(): Flow<List<CredentialEntity>>` — ordered by `updatedAt DESC`
- [x] `getAllSuspend(): List<CredentialEntity>` — one-shot snapshot for SyncManager
- [x] `getById(id)`, `getDirty()`, `upsert()`, `upsertAll()`, `delete()`, `deleteAll()`, `markAllClean()`

### 4.3 Database
File: `data/local/CredentialDatabase.kt`
- [x] `@Database(version = 1, exportSchema = false)`, `CredentialDatabase : RoomDatabase`

### 4.4 Hilt module
File: `di/DatabaseModule.kt`
- [x] `@Provides @Singleton CredentialDatabase` via `Room.databaseBuilder("credential_db")`
- [x] `@Provides CredentialDao` from database

### 4.5 Local repository
File: `data/local/LocalCredentialRepository.kt`
- [x] `getAll(): Flow<List<Credential>>` — decrypts blobs; corrupted entries silently dropped
- [x] `getAllSnapshot(): List<Credential>` — one-shot for sync vault build
- [x] `save(credential, isDirty = true)` — encrypts and upserts
- [x] `replaceAll(credentials, isDirty = false)` — atomic delete+insert for Drive download
- [x] `delete(id)`, `getDirty()`, `hasDirtyEntries()`, `markAllClean()`
- [x] `VaultSerializer` extended with `serializeOne()` / `deserializeOne()` for per-entity encryption

### Tests
- [x] `CredentialDaoTest` (instrumented, in-memory Room) — 9 cases covering upsert, update, ordering, dirty flag, markAllClean, delete, deleteAll
- [x] `LocalCredentialRepositoryTest` (JUnit + MockK) — 8 cases covering decrypt mapping, corrupted entity drop, save/dirty flag, delete, replaceAll
- [x] `./gradlew test` → BUILD SUCCESSFUL

**Phase 4 complete when:** credentials can be saved and retrieved locally without Drive, surviving app restarts.

---

## Phase 5 — Google Drive Repository

**Goal:** Upload and download the single encrypted vault file from `appDataFolder`.

### 5.1 Drive service factory
File: `data/drive/DriveServiceFactory.kt`
- [x] `build(account: GoogleSignInAccount): Drive` — `GoogleAccountCredential.usingOAuth2()` with `DRIVE_APPDATA` scope, `NetHttpTransport`, `GsonFactory`

### 5.2 Drive repository interface
File: `domain/repository/DriveRepository.kt`
- [x] `uploadVault(encryptedContent)`, `downloadVault(): String?`, `getVaultModifiedTime(): Long?`, `deleteVault()`

### 5.3 Drive repository implementation
File: `data/drive/DriveRepositoryImpl.kt`
- [x] Drive service built lazily per-call via `requireDriveService()` — fetches current account from `AuthRepository`, safe across sign-in/sign-out
- [x] `findVaultFile()` — lists `appDataFolder` filtered by `name='vault.enc' and trashed=false`
- [x] `uploadVault` — creates new file if absent, patches existing file by ID
- [x] `downloadVault` — `executeMediaAsInputStream()` decoded as UTF-8
- [x] `getVaultModifiedTime` — `modifiedTime.value` (epoch ms)
- [x] `deleteVault` — no-op if file absent, otherwise deletes by ID
- [x] All operations on `Dispatchers.IO`

### 5.4 Hilt module
File: `di/DriveModule.kt`
- [x] `@Binds @Singleton DriveRepository` → `DriveRepositoryImpl`
- Note: `DriveServiceFactory` uses `@Singleton @Inject constructor`, no explicit `@Provides` needed

### Unit tests
- [x] `DriveRepositoryImplTest` — 9 cases: create vs update branch, download null/content, modifiedTime null/value, delete no-op/deletes, auth guard
- [x] Added `google-api-client-android:2.7.0` — contains `GoogleAccountCredential` (was missing from deps)
- [x] `./gradlew test` → BUILD SUCCESSFUL, all tests pass

**Phase 5 complete when:** a test (or debug button) can write a string to Drive and read it back.

---

## Phase 6 — Sync Engine

**Goal:** Merge local and remote state; run sync automatically in background.

### 6.1 Sync logic
File: `sync/SyncManager.kt`
- [x] `SyncState` sealed class: Idle | Syncing | Success | Error(message)
- [x] `AtomicBoolean` concurrency guard — second call while syncing returns immediately
- [x] Pull step: if `remoteModifiedMs > localMaxUpdatedAt`, download + decrypt + merge + `replaceAll`
- [x] Push step: if dirty entries OR no remote vault, encrypt full snapshot + upload + `markAllClean`
- [x] `merge()` — last-write-wins by `updatedAt` using `groupBy { id }.maxBy { updatedAt }`
- [x] Exceptions propagate after setting Error state (so SyncWorker can decide retry/fail)
- [x] `syncState: StateFlow<SyncState>` for UI consumption in Phase 7

### 6.2 WorkManager worker
File: `sync/SyncWorker.kt`
- [x] `@HiltWorker class SyncWorker : CoroutineWorker` with `@AssistedInject`
- [x] `IllegalStateException` (not signed in) → `Result.failure()`, no retry
- [x] Other exceptions → `Result.retry()` up to 3 attempts, then `Result.failure()`

### 6.3 Sync scheduler
File: `sync/SyncScheduler.kt`
- [x] `schedulePeriodicSync()` — 15-min `PeriodicWorkRequest`, CONNECTED constraint, exponential backoff, `KEEP` policy
- [x] `scheduleImmediateSync()` — `OneTimeWorkRequest`, `REPLACE` policy (cancels pending)
- [x] `cancelAll()` cancels both periodic and immediate work

### 6.4 Wire sync triggers
- [x] `AuthViewModel`: `observeAuthForSync()` — collects authState, schedules periodic + immediate sync on `SignedIn`; `signOut()` calls `cancelAll()` first
- [x] `MainActivity.onResume()` — schedules immediate sync if signed in
- [x] Credential save/delete will trigger immediate sync in Phase 7 ViewModels
- [x] `CredentialApp` now implements `Configuration.Provider` with `HiltWorkerFactory`
- [x] `AndroidManifest.xml` — removes default `WorkManagerInitializer` via `tools:node="remove"`

### Unit tests
- [x] `SyncManagerTest` — 8 cases: no remote→upload, dirty→upload, remote newer→pull+merge+reupload, no-op when in sync, merge keeps newer, state Success, state Error, concurrent guard
- [x] `./gradlew test assembleDebug` → BUILD SUCCESSFUL

**Phase 6 complete when:** editing a credential on the device, killing and relaunching, shows the credential downloaded from Drive.

---

## Phase 7 — Compose UI

**Goal:** Full navigation flow with all screens.

### 7.1 Navigation graph
File: `ui/navigation/AppNavGraph.kt`
- [x] Routes: `sign_in`, `biometric_lock`, `credential_list`, `credential_detail/{credentialId}`, `settings`
- [x] Shared `AuthViewModel` at NavGraph level — `LaunchedEffect` observes `authState` and navigates to `sign_in` on sign-out (Idle after non-Idle)
- [x] `Routes` object with helper `credentialDetail(id)` function

### 7.2 Screen: Credential List
File: `ui/credentiallist/CredentialListScreen.kt`
- [x] `LazyColumn` with `SwipeToDismissBox` (end-to-start swipe deletes)
- [x] Swipe-to-delete triggers Snackbar with UNDO action; undo re-saves to Room
- [x] `FloatingActionButton` → `credential_detail/new`
- [x] `OutlinedTextField` search bar filters by title, username, url
- [x] `TopAppBar` with sync status icon (Idle/Syncing/Success/Error) + Settings button
- [x] Long-press `DropdownMenu` → Copy username / Copy password (auto-clears clipboard after 30 s)

### 7.3 Screen: Credential Detail (Add / Edit)
File: `ui/credentialdetail/CredentialDetailScreen.kt`
- [x] Fields: Title*, Username*, Password* (show/hide toggle + eye icon), URL, Notes
- [x] `LinearProgressIndicator` password strength bar (Weak/Fair/Strong color-coded)
- [x] `Generate Password` button (16-char random from alphanumeric + symbols)
- [x] Save icon in TopAppBar — enabled only when title + username non-blank
- [x] Delete icon (edit mode only) shows confirmation `AlertDialog` before deleting
- [x] `navEvent` SharedFlow triggers `onBack()` after save or delete

### 7.4 Screen: Biometric Lock
File: `ui/biometric/BiometricLockScreen.kt`
- [x] `BiometricManager.canAuthenticate()` check — skips to `onAuthenticated()` on devices without biometric
- [x] `BiometricPrompt` launched via `LaunchedEffect(Unit)` on screen entry
- [x] On success → `onAuthenticated()`; on error/cancel → shows "Try Again" button
- [x] Inline error message for non-user-cancel errors
- [x] `MainActivity` changed to extend `FragmentActivity` (required by biometric library)

### 7.5 Screen: Settings
File: `ui/settings/SettingsScreen.kt`
- [x] Signed-in email display
- [x] Sync status + last sync time (formatted "MMM d, HH:mm")
- [x] `Sync Now` button (disabled while syncing)
- [x] `Sign Out` button with confirmation dialog; clears local DB then signs out
- [x] App version from `PackageManager`

### 7.6 ViewModels
- [x] `CredentialListViewModel`: `credentials` Flow filtered by `searchQuery`; `deleteCredential`/`undoDelete` with `recentlyDeleted` buffer; `copyToClipboard` with 30 s auto-clear
- [x] `CredentialDetailViewModel`: `SavedStateHandle` for nav arg; loads existing by `localRepo.getById()`; `canSave`, `passwordStrength` derived StateFlows; `generatePassword()`; `save()`/`delete()` emit `navEvent`
- [x] `SettingsViewModel`: `lastSyncTime` tracked by observing `SyncState.Success`; `syncNow()`, `signOut()`
- [x] `LocalCredentialRepository` extended with `getById(id): Credential?`

**Phase 7 complete when:** all screens are navigable and credentials can be added, viewed, and deleted through the UI.

---

## Phase 8 — Security Hardening

**Goal:** The app is safe to use on a shared or lost device.

### 8.1 Screenshot prevention
File: `ui/MainActivity.kt`
- [x] Add `window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)` in `onCreate`

### 8.2 Biometric lock state
File: `ui/biometric/LockStateManager.kt`
- [x] `@Singleton LockStateManager` tracks `lastBackgroundedAt`; `MainActivity.onPause/onResume` call `onBackgrounded()`/`onForegrounded()`
- [x] If `now - lastBackgroundedAt > 60_000` ms on foreground → set `isLocked = true`
- [x] `isLocked: StateFlow<Boolean>` (via `LockViewModel`) consumed by `AppNavGraph` — redirects to `biometric_lock` while signed in, clearing the back stack
- [x] `BiometricLockScreen` calls `LockViewModel.unlock()` on successful auth

### 8.3 Clipboard auto-clear
File: `ui/credentiallist/CredentialListViewModel.kt`
- [x] On copy: `ClipboardManager.setPrimaryClip(...)` then `viewModelScope.launch { delay(30_000); clearPrimaryClip() }`

### 8.4 In-memory wipe on lock
- [x] `credentials` flow gated on `LockStateManager.isLocked` — emits `emptyList()` while locked, wiping the decrypted list from memory
- [x] On unlock, upstream Room flow re-emits and refills the list

### 8.5 Network security config
File: `app/src/main/res/xml/network_security_config.xml`
- [x] `cleartextTrafficPermitted="false"`
- [x] Referenced in `AndroidManifest.xml` via `android:networkSecurityConfig`

### 8.6 ProGuard rules
File: `app/proguard-rules.pro`
- [x] Keep rules for: kotlinx.serialization models, Google Drive SDK / google-api-client (Gson + `@Key` fields), Hilt, Room
- [x] `isMinifyEnabled = true` + `isShrinkResources = true` on release; `assembleRelease` (R8) succeeds

**Phase 8 complete when:** screenshots are blocked, app locks after backgrounding, clipboard self-clears.

---

## Phase 9 — Testing

**Goal:** Confidence that core logic is correct.

### 9.1 Unit tests
Directory: `app/src/test/`

- [ ] `VaultCryptoTest` — encrypt/decrypt round-trip, tampered ciphertext throws
- [ ] `VaultSerializerTest` — serialize → deserialize produces equal list
- [ ] `SyncManagerTest` — mock Drive and Room; verify merge logic for all 4 conflict cases
- [ ] `CredentialDetailViewModelTest` — save triggers dirty flag; delete removes from repo

### 9.2 Instrumented tests
Directory: `app/src/androidTest/`

- [ ] `CredentialDaoTest` — upsert, getAll, getDirty, markAllClean using in-memory Room
- [ ] `LocalCredentialRepositoryTest` — save + retrieve decrypts correctly

### 9.3 UI tests (optional)
- [ ] `CredentialListScreenTest` — search filters list, swipe-to-delete shows snackbar
- [ ] `SignInScreenTest` — loading state during sign-in

**Phase 9 complete when:** `./gradlew test` and `./gradlew connectedAndroidTest` pass.

---

## Phase 10 — Polish & Release Prep

- [ ] Add empty-state illustration to Credential List (no credentials yet)
- [ ] Add app icon (replace default launcher icon)
- [ ] Set `isMinifyEnabled = true` and `isShrinkResources = true` in release build type
- [ ] Verify ProGuard doesn't break Drive SDK or Room
- [ ] Test on API 24 emulator (min SDK) and a physical device
- [ ] Create a signed release APK/AAB via `Build > Generate Signed Bundle`
- [ ] Review Drive API quota (default: 1 billion requests/day — sufficient)

---

## File Tree (target end state)

```
app/src/main/java/com/github/aashishvibhu/credentialmanagement/
├── CredentialApp.kt
├── MainActivity.kt
├── data/
│   ├── auth/
│   │   ├── GoogleAuthClient.kt
│   │   ├── AuthRepository.kt
│   │   └── AuthRepositoryImpl.kt
│   ├── drive/
│   │   ├── DriveServiceFactory.kt
│   │   └── DriveRepositoryImpl.kt
│   ├── local/
│   │   ├── CredentialDatabase.kt
│   │   ├── CredentialEntity.kt
│   │   ├── CredentialDao.kt
│   │   └── LocalCredentialRepository.kt
│   └── vault/
│       └── VaultSerializer.kt
├── di/
│   ├── AuthModule.kt
│   ├── DatabaseModule.kt
│   ├── DriveModule.kt
│   └── SecurityModule.kt
├── domain/
│   ├── model/
│   │   └── Credential.kt
│   └── repository/
│       └── DriveRepository.kt
├── security/
│   ├── KeystoreManager.kt
│   └── VaultCrypto.kt
├── sync/
│   ├── SyncManager.kt
│   ├── SyncWorker.kt
│   └── SyncScheduler.kt
└── ui/
    ├── auth/
    │   ├── SignInScreen.kt
    │   └── AuthViewModel.kt
    ├── biometric/
    │   ├── BiometricLockScreen.kt
    │   └── LockStateManager.kt
    ├── credentialdetail/
    │   ├── CredentialDetailScreen.kt
    │   └── CredentialDetailViewModel.kt
    ├── credentiallist/
    │   ├── CredentialListScreen.kt
    │   └── CredentialListViewModel.kt
    ├── navigation/
    │   └── NavGraph.kt
    └── settings/
        ├── SettingsScreen.kt
        └── SettingsViewModel.kt
```

---

## Quick Reference — Key Decisions

| Decision | Choice | Reason |
|---|---|---|
| UI | Jetpack Compose + Material3 | Modern, less boilerplate |
| Drive scope | `appDataFolder` | Files invisible to user; app-private |
| Vault format | Single `vault.enc` file | Fewer API calls; simpler conflict resolution |
| Encryption | AES-256-GCM via Android Keystore | Hardware-backed key; authenticated encryption |
| Local DB | Room + per-row encrypted blob | Offline support; dirty-flag sync |
| DI | Hilt | First-class Android support |
| Background sync | WorkManager | Battery-aware; survives process death |
| Auth | Google Sign-In (legacy SDK) | Stable; works without Firebase |
