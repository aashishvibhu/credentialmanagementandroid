package com.github.aashishvibhu.credentialmanagement.data.vault

import com.github.aashishvibhu.credentialmanagement.data.local.LocalCredentialRepository
import com.github.aashishvibhu.credentialmanagement.domain.model.Credential
import com.github.aashishvibhu.credentialmanagement.domain.repository.DriveRepository
import com.github.aashishvibhu.credentialmanagement.security.NoVaultException
import com.github.aashishvibhu.credentialmanagement.security.TooManyAttemptsException
import com.github.aashishvibhu.credentialmanagement.security.VaultCodec
import com.github.aashishvibhu.credentialmanagement.security.VaultCrypto
import com.github.aashishvibhu.credentialmanagement.security.VaultLockedException
import com.github.aashishvibhu.credentialmanagement.security.VaultUnreadableException
import com.github.aashishvibhu.credentialmanagement.security.WrongPasswordException
import com.github.aashishvibhu.credentialmanagement.security.WrongRecoveryKeyException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Where the vault in Drive stands relative to this phone. */
sealed interface VaultStatus {
    /** This phone holds the key for the vault in Drive. */
    data object Ready : VaultStatus
    /** Drive has no vault: create one with a master password. */
    data object NoVault : VaultStatus
    /** Drive has a master-password vault this phone hasn't unlocked yet. */
    data object NeedsPassword : VaultStatus
    /** Old-format vault this install can still read: move it to a master password. */
    data class LegacyReadable(val credentials: List<Credential>) : VaultStatus
    /** Old-format vault locked by a key from an earlier install: it can't be opened. */
    data object LegacyUnreadable : VaultStatus
}

/**
 * Creates, unlocks and re-keys the master-password vault. Argon2 work runs on [Dispatchers.Default].
 * Wrong passwords count towards a growing wait (see [registerFailure]).
 */
@Singleton
class VaultAccess @Inject constructor(
    private val driveRepo: DriveRepository,
    private val codec: VaultCodec,
    private val keyStore: VaultKeyStore,
    private val legacyCrypto: VaultCrypto,
    private val serializer: VaultSerializer,
    private val localRepo: LocalCredentialRepository
) {
    companion object {
        const val FREE_ATTEMPTS = 5
        private const val BASE_WAIT_MS = 30_000L
        private const val MAX_DOUBLINGS = 5   // caps the wait at 16 minutes
    }

    internal var now: () -> Long = System::currentTimeMillis

    /** A recovery key waiting to be shown once on the recovery key screen. Never persisted. */
    private var pendingRecoveryKey: String? = null

    fun takeRecoveryKey(): String? = pendingRecoveryKey

    fun clearRecoveryKey() { pendingRecoveryKey = null }

    /** True when this phone can open the vault without the master password (no network needed). */
    fun isUnlockedOnThisPhone(): Boolean = keyStore.hasVaultKey()

    suspend fun checkRemote(): VaultStatus {
        val text = driveRepo.downloadVault() ?: return VaultStatus.NoVault
        val envelope = codec.decode(text) ?: return legacyStatus(text)
        if (keyStore.hasVaultKey() && keyStore.keyId() == envelope.keyId) {
            keyStore.envelope = text
            return VaultStatus.Ready
        }
        // Key missing, or a different vault was created on another phone since
        keyStore.clearVaultKey()
        return VaultStatus.NeedsPassword
    }

    /**
     * Creates a vault holding [credentials] and stores its key on this phone. Refuses to replace an
     * existing master-password vault unless [replaceExisting] (the "Start a new vault" flow).
     * The recovery key is kept for [takeRecoveryKey].
     */
    suspend fun create(password: String, credentials: List<Credential>, replaceExisting: Boolean = false) {
        val existing = driveRepo.downloadVault()
        if (!replaceExisting && existing != null && codec.decode(existing) != null) {
            throw IllegalStateException("There's already a vault in Google Drive. Unlock it instead.")
        }
        val created = withContext(Dispatchers.Default) {
            codec.newVault(serializer.serialize(credentials), password)
        }
        val text = codec.encode(created.envelope)
        driveRepo.uploadVault(text)
        keyStore.saveVaultKey(created.envelope.keyId, created.vaultKey)
        keyStore.envelope = text
        resetAttempts()
        localRepo.refreshCache(credentials)
        pendingRecoveryKey = created.recoveryKey
    }

    suspend fun unlockWithPassword(password: String) =
        unlockFromDrive { codec.unlockWithPassword(it, password) }

    suspend fun unlockWithRecoveryKey(recoveryKey: String) =
        unlockFromDrive { codec.unlockWithRecoveryKey(it, recoveryKey) }

    /** Lock-screen fallback: checks the password against the last-seen vault, so it works offline. */
    suspend fun verifyPassword(password: String) {
        checkLockout()
        val envelope = keyStore.envelope?.let(codec::decode) ?: throw VaultLockedException()
        attempt { withContext(Dispatchers.Default) { codec.unlockWithPassword(envelope, password) } }
    }

    suspend fun changePassword(currentPassword: String, newPassword: String) {
        val (envelope, vaultKey) = latestWithPassword(currentPassword)
        val updated = withContext(Dispatchers.Default) { codec.changePassword(envelope, vaultKey, newPassword) }
        upload(updated)
    }

    /**
     * Sets a new master password without the old one, using the vault key already on this phone.
     * Used right after unlocking with the recovery key.
     */
    suspend fun resetPassword(newPassword: String) {
        val vaultKey = keyStore.vaultKey() ?: throw VaultLockedException()
        val text = driveRepo.downloadVault() ?: throw NoVaultException()
        val envelope = codec.decode(text) ?: throw VaultUnreadableException()
        if (envelope.keyId != keyStore.keyId()) throw VaultLockedException()
        val updated = withContext(Dispatchers.Default) { codec.changePassword(envelope, vaultKey, newPassword) }
        upload(updated)
    }

    /** Replaces the recovery key; the new one is kept for [takeRecoveryKey]. */
    suspend fun newRecoveryKey(currentPassword: String) {
        val (envelope, vaultKey) = latestWithPassword(currentPassword)
        val (updated, recoveryKey) = withContext(Dispatchers.Default) { codec.newRecoveryKey(envelope, vaultKey) }
        upload(updated)
        pendingRecoveryKey = recoveryKey
    }

    /** Wrong attempts left before unlocking pauses. */
    fun attemptsLeft(): Int = (FREE_ATTEMPTS - keyStore.failedAttempts).coerceAtLeast(0)

    fun lockoutRemainingMs(): Long = (keyStore.lockoutUntil - now()).coerceAtLeast(0)

    /** Sign-out: forget the key on this phone. The vault in Drive is untouched. */
    fun forget() {
        keyStore.clear()
        pendingRecoveryKey = null
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun legacyStatus(text: String): VaultStatus =
        runCatching { serializer.deserialize(legacyCrypto.decrypt(text)) }
            .fold(
                onSuccess = { VaultStatus.LegacyReadable(it) },
                onFailure = { VaultStatus.LegacyUnreadable }
            )

    private suspend fun unlockFromDrive(unwrap: (VaultEnvelope) -> ByteArray) {
        checkLockout()
        val text = driveRepo.downloadVault() ?: throw NoVaultException()
        val envelope = codec.decode(text) ?: throw VaultUnreadableException()
        val vaultKey = attempt { withContext(Dispatchers.Default) { unwrap(envelope) } }
        val credentials = serializer.deserialize(codec.decryptPayload(envelope, vaultKey))
        keyStore.saveVaultKey(envelope.keyId, vaultKey)
        keyStore.envelope = text
        localRepo.refreshCache(credentials)
    }

    private suspend fun latestWithPassword(password: String): Pair<VaultEnvelope, ByteArray> {
        checkLockout()
        val text = driveRepo.downloadVault() ?: throw NoVaultException()
        val envelope = codec.decode(text) ?: throw VaultUnreadableException()
        val vaultKey = attempt { withContext(Dispatchers.Default) { codec.unlockWithPassword(envelope, password) } }
        return envelope to vaultKey
    }

    private suspend fun upload(envelope: VaultEnvelope) {
        val text = codec.encode(envelope)
        driveRepo.uploadVault(text)
        keyStore.envelope = text
    }

    private fun checkLockout() {
        val remaining = lockoutRemainingMs()
        if (remaining > 0) throw TooManyAttemptsException(remaining)
    }

    private inline fun <T> attempt(block: () -> T): T =
        try {
            block().also { resetAttempts() }
        } catch (e: WrongPasswordException) {
            registerFailure(); throw e
        } catch (e: WrongRecoveryKeyException) {
            registerFailure(); throw e
        }

    /** After [FREE_ATTEMPTS] wrong tries, wait 30 s, then double the wait each further miss. */
    private fun registerFailure() {
        val failures = keyStore.failedAttempts + 1
        keyStore.failedAttempts = failures
        if (failures >= FREE_ATTEMPTS) {
            val doublings = (failures - FREE_ATTEMPTS).coerceAtMost(MAX_DOUBLINGS)
            keyStore.lockoutUntil = now() + (BASE_WAIT_MS shl doublings)
        }
    }

    private fun resetAttempts() {
        keyStore.failedAttempts = 0
        keyStore.lockoutUntil = 0L
    }
}
