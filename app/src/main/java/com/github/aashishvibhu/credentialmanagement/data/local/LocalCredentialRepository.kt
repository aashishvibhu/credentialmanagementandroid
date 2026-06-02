package com.github.aashishvibhu.credentialmanagement.data.local

import com.github.aashishvibhu.credentialmanagement.data.vault.VaultSerializer
import com.github.aashishvibhu.credentialmanagement.domain.model.Credential
import com.github.aashishvibhu.credentialmanagement.security.VaultCrypto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalCredentialRepository @Inject constructor(
    private val dao: CredentialDao,
    private val vaultCrypto: VaultCrypto,
    private val vaultSerializer: VaultSerializer
) {
    /** Live stream of all credentials for UI — decrypts each entity on emission. */
    fun getAll(): Flow<List<Credential>> = dao.getAll().map { entities ->
        entities.mapNotNull { it.toCredential() }
    }

    /** One-shot snapshot of all credentials — used by SyncManager to build vault. */
    suspend fun getAllSnapshot(): List<Credential> =
        dao.getAllSuspend().mapNotNull { it.toCredential() }

    suspend fun save(credential: Credential, isDirty: Boolean = true) {
        dao.upsert(credential.toEntity(isDirty))
    }

    /** Bulk-replace all local credentials (called after downloading vault from Drive). */
    suspend fun replaceAll(credentials: List<Credential>, isDirty: Boolean = false) {
        dao.deleteAll()
        dao.upsertAll(credentials.map { it.toEntity(isDirty) })
    }

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun getDirty(): List<CredentialEntity> = dao.getDirty()

    suspend fun hasDirtyEntries(): Boolean = dao.getDirty().isNotEmpty()

    suspend fun markAllClean() = dao.markAllClean()

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun CredentialEntity.toCredential(): Credential? = runCatching {
        vaultSerializer.deserializeOne(vaultCrypto.decrypt(encryptedBlob))
    }.getOrNull()

    private fun Credential.toEntity(isDirty: Boolean) = CredentialEntity(
        id = id,
        encryptedBlob = vaultCrypto.encrypt(vaultSerializer.serializeOne(this)),
        updatedAt = updatedAt,
        isDirty = isDirty
    )
}
