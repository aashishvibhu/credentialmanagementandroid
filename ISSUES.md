# Repository Issues Audit

Generated: 2026-08-06 | Version: v1.1.0

---

## 🔴 Critical

### 1. Deletion Resurrection Bug — `SyncManager.merge()` doesn't track deletions

**File:** `app/src/main/java/.../sync/SyncManager.kt:109-112`

The last-write-wins merge algorithm treats `(local + remote)` as an additive union grouped by ID. Deleted credentials have no entry in either list, so there's no tombstone. If device A deletes credential X and pushes, then device B syncs (without having deleted X locally), X gets **resurrected** because it still exists in device B's local DB and the merge keeps it.

**Scenario to reproduce:**
1. Device A deletes credential X → pushes vault (X removed from remote)
2. Device B has X locally, edits credential Y (so `localMaxUpdatedAt > remoteModifiedMs`) → syncs
3. Device B uploads its vault (still contains X) → X is back on remote
4. Device A syncs → X reappears on device A

**Fix:** Introduce a per-record `isDeleted` tombstone flag in the entity and vault, or switch to a CRDT-based approach.

---

### 2. `CredentialDetailViewModel.load()` infinite spinner if credential not found

**File:** `app/src/main/java/.../ui/credentialdetail/CredentialDetailViewModel.kt:69-77`

```kotlin
private fun load() = viewModelScope.launch {
    localRepo.getById(credentialId)?.let { c ->  // ← null when credential deleted externally
        _title.value = c.title
        // ...
    }
    _isLoading.value = false  // ← only reached when getById is non-null
}
```

If a credential is deleted by a background sync while the detail screen is open, `getById` returns `null`, the `let` block is skipped, and `_isLoading` is never set to `false`. The user sees an infinite spinner.

**Fix:** Move `_isLoading.value = false` outside the `let` block (always execute it).

---

## 🟠 High

### 3. Sign-out does not delete remote vault

**Files:** `SettingsViewModel.kt:39-44`, `DriveRepository.kt`

`signOut()` clears the local DB but never calls `driveRepo.deleteVault()`. The encrypted vault file remains orphaned in Google Drive `appDataFolder`. If the user signs in again, a stale vault could be downloaded and merged.

**Fix:** Call `driveRepo.deleteVault()` before or after `localRepo.replaceAll(emptyList())` during sign-out.

---

### 4. Copy-to-clipboard race condition

**File:** `CredentialListViewModel.kt:79-89`

```kotlin
fun copyToClipboard(context: Context, text: String, label: String) {
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    viewModelScope.launch {
        delay(30_000)
        clipboard.clearPrimaryClip()  // ← clears whatever is on clipboard now
    }
}
```

If the user copies password A, then copies password B 10 seconds later, the timer for A will fire at 30 seconds and clear **whatever is currently on the clipboard** (which is now B). B gets cleared prematurely.

**Fix:** Store the copied text and only clear if `clipboard.primaryClip?.getItemAt(0)?.text == storedText`.

---

### 5. `AuthRepositoryImpl.handleSignInResult()` misses non-`ApiException` errors

**File:** `AuthRepositoryImpl.kt:26-33`

```kotlin
override suspend fun handleSignInResult(data: Intent?) {
    try {
        val account = GoogleSignIn.getSignedInAccountFromIntent(data)
            .getResult(ApiException::class.java)
        // ...
    } catch (e: ApiException) { ... }
    // No catch for RuntimeException, NullPointerException, etc.
}
```

If `data` is `null` (user canceled) or any non-`ApiException` occurs, the app crashes with an unhandled exception.

**Fix:** Add a general `catch (e: Exception)` block or null-check `data` first.

---

### 6. VaultCrypto uses experimental `kotlin.io.encoding.Base64`

**File:** `VaultCrypto.kt:30,38`

Uses `kotlin.io.encoding.Base64` with `@OptIn(ExperimentalEncodingApi::class)`. This API may change or be removed in future Kotlin versions.

**Fix:** Switch to `java.util.Base64.getDecoder()/getEncoder()` which is stable and already used in the test file (`VaultCryptoTest.kt:56-58`).

---

### 7. Backup rules include encrypted database

**Files:** `AndroidManifest.xml`, `backup_rules.xml`, `data_extraction_rules.xml`

`android:allowBackup="true"` with default (empty) backup rules means the Room database is included in Android backups. While records are individually AES-GCM encrypted, this exposes the vault to cloud backup providers and device transfers.

**Fix:** Add explicit `<exclude>` rules for the Room database files, or disable backup entirely for this security-sensitive app.

---

## 🟡 Medium

### 8. `KeystoreManager` loads KeyStore on every crypto operation

**File:** `KeystoreManager.kt:20-22`

```kotlin
fun getOrCreateKey(): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    // Called on every encrypt/decrypt — unnecessary overhead
}
```

**Fix:** Cache the key after first retrieval using `lazy` or a private field.

---

### 9. `SyncManager.sync()` silently ignores concurrent calls

**File:** `SyncManager.kt:40`

```kotlin
suspend fun sync() {
    if (!isSyncing.compareAndSet(false, true)) return  // ← no feedback to caller
```

If sync is triggered while another sync is in progress, the caller gets no indication their request was skipped. The `syncState` stays at whatever the first sync produces.

**Fix:** Either return a `Boolean` or emit a distinct state like `SyncState.Skipped`.

---

### 10. `SettingsViewModel.signedInEmail` is not observable

**File:** `SettingsViewModel.kt:32`

```kotlin
val signedInEmail: String get() = authRepository.getSignedInAccount()?.email ?: ""
```

This is a plain computed property, not a `StateFlow`. The UI won't recompose when the user signs out or the account changes. After sign-out via the settings screen itself this isn't an issue (navigation replaces the screen), but if sign-out is triggered from elsewhere, the settings screen would show stale data.

**Fix:** Expose as `StateFlow<String>` by collecting from the auth state.

---

### 11. No Keystore error handling

**Files:** `KeystoreManager.kt`, `VaultCrypto.kt`

If the Android Keystore is temporarily unavailable or the key is permanently invalidated (e.g., after device lock screen change on some devices), the app will throw `KeyStoreException` or `InvalidKeyException` with no user-friendly recovery path.

**Fix:** Catch `KeyStoreException`/`KeyPermanentlyInvalidatedException` and guide the user to re-authenticate or reset.

---

### 12. `SyncManager.getAllSnapshot()` called twice during sync

**File:** `SyncManager.kt:58,77`

`localRepo.getAllSnapshot()` is called once to compute `localMaxUpdatedAt` and again to build the upload payload after merge. This is two full DB reads.

**Fix:** Store the result of the first call and reuse it.

---

### 13. No connectivity check before `pushNow()`

**Files:** `CredentialDetailViewModel.kt:99,112`, `CredentialListViewModel.kt`

`pushNow()` is called from ViewModels without checking network connectivity. It will fail with a network error that gets silently swallowed. While the dirty flag ensures periodic sync will retry, the immediate feedback is lost.

**Fix:** Add a `ConnectivityManager` check before calling `pushNow()`, or surface the error to the user.

---

## 🟢 Low

### 14. All UI strings are hardcoded

**Files:** All `*Screen.kt` and `*ViewModel.kt` files

Every string (labels, placeholders, error messages, dialog text) is hardcoded in Kotlin. This makes localization impossible.

**Fix:** Extract strings to `res/values/strings.xml` and reference via `stringResource()`.

---

### 15. No Room migration strategy

**File:** `CredentialDatabase.kt:7-9`

```kotlin
@Database(entities = [CredentialEntity::class], version = 1, exportSchema = false)
```

`exportSchema = false` means no schema history is tracked. When the schema changes in future versions, Room will crash with `IllegalStateException` unless destructive migration or manual migrations are added.

**Fix:** Set `exportSchema = true`, export schemas to a version-controlled directory, and add `Migration` objects.

---

### 16. `BiometricLockScreen` silent failure if context is not `FragmentActivity`

**File:** `BiometricLockScreen.kt:63`

```kotlin
val activity = context as? FragmentActivity ?: return  // ← silent no-op
```

If the `LocalContext` is somehow not a `FragmentActivity`, the biometric prompt silently fails to launch and the user is stuck on the lock screen with no error message. The retry button would also call `launchPrompt()` which would fail again.

**Fix:** Show an error message and provide a fallback authentication method.

---

### 17. Search has no debounce

**File:** `CredentialListViewModel.kt:46-54`

The `combine` flow re-evaluates the filter on every keystroke, triggering recomposition and decryption for all entities. For large vaults, this could cause jank.

**Fix:** Add `debounce(300)` to the search query flow.

---

### 18. No `BiometricPrompt.cancelAuthentication()` on lifecycle stop

**File:** `BiometricLockScreen.kt`

If the user backgrounds the app while the biometric prompt is showing, the prompt remains active. On some devices this can cause issues when returning to the app.

**Fix:** Use `DisposableEffect` to call `biometricPrompt.cancelAuthentication()` on dispose.

---

### 19. Save button progress indicator sizing

**File:** `CredentialDetailScreen.kt:111`

```kotlin
CircularProgressIndicator(
    modifier = Modifier.size(20.dp).padding(2.dp),
    strokeWidth = 2.dp
)
```

The `.size(20.dp).padding(2.dp)` creates a 16×16dp indicator with 2dp stroke, which may appear very small on some devices.

**Fix:** Use a larger base size or the default IconButton content sizing.

---

### 20. `VaultSerializer` encodes defaults

**File:** `VaultSerializer.kt:11-14`

```kotlin
private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true  // ← bloats JSON with empty strings for url, notes
}
```

Every credential serializes `url = ""` and `notes = ""` even when empty, increasing vault file size.

**Fix:** Set `encodeDefaults = false` unless defaults are needed for merge correctness.

---

## Summary

| Severity | Count |
|----------|-------|
| 🔴 Critical | 2 |
| 🟠 High     | 5 |
| 🟡 Medium   | 6 |
| 🟢 Low      | 7 |
| **Total**   | **20** |
