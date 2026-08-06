package com.github.aashishvibhu.credentialmanagement.sync

import android.util.Log
import com.github.aashishvibhu.credentialmanagement.data.local.LocalCredentialRepository
import com.github.aashishvibhu.credentialmanagement.data.vault.VaultSerializer
import com.github.aashishvibhu.credentialmanagement.domain.repository.DriveRepository
import com.github.aashishvibhu.credentialmanagement.security.VaultCrypto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultSyncManager @Inject constructor(
    private val localRepo: LocalCredentialRepository,
    private val driveRepo: DriveRepository,
    private val vaultCrypto: VaultCrypto,
    private val vaultSerializer: VaultSerializer
) {
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val isSyncing = AtomicBoolean(false)

    companion object {
        private const val TAG = "VaultSyncManager"
    }

    /** Download the vault from Drive, decrypt it, and refresh the local cache. */
    suspend fun pullFromDrive() {
        if (!isSyncing.compareAndSet(false, true)) {
            _syncState.value = SyncState.Skipped
            return
        }
        _syncState.value = SyncState.Syncing
        try {
            val encryptedVault = driveRepo.downloadVault()
            if (encryptedVault != null) {
                val credentials = vaultSerializer.deserialize(
                    vaultCrypto.decrypt(encryptedVault)
                )
                localRepo.refreshCache(credentials)
            }
            _syncState.value = SyncState.Success
        } catch (e: Exception) {
            Log.e(TAG, "pullFromDrive failed: ${e.javaClass.simpleName} — ${e.message}", e)
            _syncState.value = SyncState.Error(e.message ?: "Sync failed")
            throw e
        } finally {
            isSyncing.set(false)
        }
    }

    /** Build the full vault from local cache, encrypt, and upload to Drive. */
    suspend fun pushFullVault(credentials: List<com.github.aashishvibhu.credentialmanagement.domain.model.Credential>) {
        val serialized = vaultSerializer.serialize(credentials)
        val encrypted = vaultCrypto.encrypt(serialized)
        driveRepo.uploadVault(encrypted)
    }

    /** Pull latest vault, return its decrypted credentials list. Returns empty list if no vault exists. */
    suspend fun pullLatestCredentials(): List<com.github.aashishvibhu.credentialmanagement.domain.model.Credential> {
        val encryptedVault = driveRepo.downloadVault() ?: return emptyList()
        return vaultSerializer.deserialize(vaultCrypto.decrypt(encryptedVault))
    }
}
