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

    /** One-shot snapshot of all credentials — used to build full vault for upload. */
    suspend fun getAllSnapshot(): List<Credential> =
        dao.getAllSuspend().mapNotNull { it.toCredential() }

    suspend fun save(credential: Credential) {
        dao.upsert(credential.toEntity())
    }

    /** Bulk-replace all local cache entries (called after pulling vault from Drive). */
    suspend fun refreshCache(credentials: List<Credential>) {
        dao.deleteAll()
        credentials.forEach { dao.upsert(it.toEntity()) }
    }

    /** Clear the entire cache (called on sign-out). */
    suspend fun clearCache() {
        dao.deleteAll()
    }

    suspend fun getById(id: String): Credential? = dao.getById(id)?.toCredential()

    suspend fun delete(id: String) = dao.delete(id)

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun CredentialEntity.toCredential(): Credential? = runCatching {
        vaultSerializer.deserializeOne(vaultCrypto.decrypt(encryptedBlob))
    }.getOrNull()

    private fun Credential.toEntity() = CredentialEntity(
        id = id,
        encryptedBlob = vaultCrypto.encrypt(vaultSerializer.serializeOne(this)),
        updatedAt = updatedAt
    )
}
