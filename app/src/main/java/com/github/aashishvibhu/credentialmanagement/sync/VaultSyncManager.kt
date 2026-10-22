package com.github.aashishvibhu.credentialmanagement.sync

import android.util.Log
import com.github.aashishvibhu.credentialmanagement.data.local.LocalCredentialRepository
import com.github.aashishvibhu.credentialmanagement.data.vault.VaultKeyStore
import com.github.aashishvibhu.credentialmanagement.data.vault.VaultSerializer
import com.github.aashishvibhu.credentialmanagement.domain.model.Credential
import com.github.aashishvibhu.credentialmanagement.domain.repository.DriveRepository
import com.github.aashishvibhu.credentialmanagement.security.VaultCodec
import com.github.aashishvibhu.credentialmanagement.security.VaultLockedException
import com.github.aashishvibhu.credentialmanagement.security.VaultUnreadableException
import com.github.aashishvibhu.credentialmanagement.util.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Moves the vault between Drive and the local cache using the vault key stored on this phone.
 * Creating and unlocking the vault (master password) lives in VaultAccess.
 */
@Singleton
class VaultSyncManager @Inject constructor(
    private val localRepo: LocalCredentialRepository,
    private val driveRepo: DriveRepository,
    private val codec: VaultCodec,
    private val keyStore: VaultKeyStore,
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
            val text = driveRepo.downloadVault()
            if (text != null) {
                localRepo.refreshCache(open(text))
            }
            _syncState.value = SyncState.Success
        } catch (e: Exception) {
            Log.e(TAG, "pullFromDrive failed: ${e.javaClass.simpleName} — ${e.message}", e)
            _syncState.value = SyncState.Error(e.userMessage("Couldn't sync"))
            throw e
        } finally {
            isSyncing.set(false)
        }
    }

    /** Encrypt [credentials] with the vault key and upload them as the whole vault. */
    suspend fun pushFullVault(credentials: List<Credential>) {
        val envelope = keyStore.envelope?.let(codec::decode) ?: throw VaultLockedException()
        val vaultKey = keyStore.vaultKey() ?: throw VaultLockedException()
        val updated = codec.withPayload(envelope, vaultKey, vaultSerializer.serialize(credentials))
        val text = codec.encode(updated)
        driveRepo.uploadVault(text)
        keyStore.envelope = text
    }

    /** Pull latest vault, return its decrypted credentials list. Returns empty list if no vault exists. */
    suspend fun pullLatestCredentials(): List<Credential> {
        val text = driveRepo.downloadVault() ?: return emptyList()
        return open(text)
    }

    /**
     * Decrypts a downloaded vault and remembers it as the latest version. Throws
     * [VaultLockedException] when this phone needs the master password first.
     */
    private fun open(text: String): List<Credential> {
        val envelope = codec.decode(text) ?: throw VaultUnreadableException()
        val vaultKey = keyStore.vaultKey() ?: throw VaultLockedException()
        if (envelope.keyId != keyStore.keyId()) {
            // A new vault was created on another phone; this key no longer fits
            keyStore.clearVaultKey()
            throw VaultLockedException()
        }
        val credentials = vaultSerializer.deserialize(codec.decryptPayload(envelope, vaultKey))
        keyStore.envelope = text
        return credentials
    }
}
