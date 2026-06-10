package com.github.aashishvibhu.credentialmanagement.sync

import com.github.aashishvibhu.credentialmanagement.data.local.LocalCredentialRepository
import com.github.aashishvibhu.credentialmanagement.data.vault.VaultSerializer
import com.github.aashishvibhu.credentialmanagement.domain.model.Credential
import com.github.aashishvibhu.credentialmanagement.domain.repository.DriveRepository
import com.github.aashishvibhu.credentialmanagement.security.VaultCrypto
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    private val localRepo: LocalCredentialRepository,
    private val driveRepo: DriveRepository,
    private val vaultCrypto: VaultCrypto,
    private val vaultSerializer: VaultSerializer
) {
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val isSyncing = AtomicBoolean(false)

    companion object {
        private const val TAG = "SyncManager"
    }

    suspend fun sync() {
        if (!isSyncing.compareAndSet(false, true)) return
        _syncState.value = SyncState.Syncing
        try {
            performSync()
            _syncState.value = SyncState.Success
        } catch (e: Exception) {
            Log.e(TAG, "sync failed: ${e.javaClass.simpleName} — ${e.message}", e)
            _syncState.value = SyncState.Error(e.message ?: "Sync failed")
            throw e
        } finally {
            isSyncing.set(false)
        }
    }

    private suspend fun performSync() {
        val remoteModifiedMs = driveRepo.getVaultModifiedTime()
        val localCredentials = localRepo.getAllSnapshot()
        val localMaxUpdatedAt = localCredentials.maxOfOrNull { it.updatedAt } ?: 0L

        var shouldUpload = localRepo.hasDirtyEntries()

        // Step 1: Pull remote vault if it contains changes newer than any local record
        if (remoteModifiedMs != null && remoteModifiedMs > localMaxUpdatedAt) {
            val encryptedRemote = driveRepo.downloadVault()
            if (encryptedRemote != null) {
                val remoteCredentials = vaultSerializer.deserialize(
                    vaultCrypto.decrypt(encryptedRemote)
                )
                val merged = merge(localCredentials, remoteCredentials)
                localRepo.replaceAll(merged, isDirty = false)
                shouldUpload = true  // always re-upload after a merge so Drive reflects merged state
            }
        }

        // Step 2: Push to Drive when local has changes or no remote vault exists yet
        if (shouldUpload || remoteModifiedMs == null) {
            val snapshot = localRepo.getAllSnapshot()
            driveRepo.uploadVault(
                vaultCrypto.encrypt(vaultSerializer.serialize(snapshot))
            )
            localRepo.markAllClean()
        }
    }

    /**
     * Upload the current local snapshot to Drive immediately, skipping the remote-newer check.
     * Use after a local write when we know the local state is authoritative.
     */
    suspend fun pushNow() {
        if (!isSyncing.compareAndSet(false, true)) return
        _syncState.value = SyncState.Syncing
        try {
            val snapshot = localRepo.getAllSnapshot()
            driveRepo.uploadVault(vaultCrypto.encrypt(vaultSerializer.serialize(snapshot)))
            localRepo.markAllClean()
            _syncState.value = SyncState.Success
        } catch (e: Exception) {
            Log.e(TAG, "pushNow failed: ${e.javaClass.simpleName} — ${e.message}", e)
            _syncState.value = SyncState.Error(e.message ?: "Upload failed")
            throw e
        } finally {
            isSyncing.set(false)
        }
    }

    /** Last-write-wins merge by id: keeps the credential with the newest updatedAt. */
    private fun merge(local: List<Credential>, remote: List<Credential>): List<Credential> =
        (local + remote)
            .groupBy { it.id }
            .values
            .map { group -> group.maxBy { it.updatedAt } }
}
