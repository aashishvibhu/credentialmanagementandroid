package com.github.aashishvibhu.credentialmanagement.data.local

import com.github.aashishvibhu.credentialmanagement.data.vault.VaultSerializer
import com.github.aashishvibhu.credentialmanagement.domain.model.Credential
import com.github.aashishvibhu.credentialmanagement.security.VaultCrypto
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalCredentialRepositoryTest {

    private val dao = mockk<CredentialDao>()
    private val vaultCrypto = mockk<VaultCrypto>()
    private val vaultSerializer = mockk<VaultSerializer>()
    private lateinit var repository: LocalCredentialRepository

    private val credential = Credential(
        id = "id-1", title = "GitHub", username = "user", password = "pass"
    )
    private val entity = CredentialEntity("id-1", "encrypted_blob", 0L, true)

    @Before
    fun setUp() {
        repository = LocalCredentialRepository(dao, vaultCrypto, vaultSerializer)
    }

    @Test
    fun `getAll decrypts entities and emits credentials`() = runTest {
        every { dao.getAll() } returns flowOf(listOf(entity))
        every { vaultCrypto.decrypt("encrypted_blob") } returns """{"raw":"json"}"""
        every { vaultSerializer.deserializeOne("""{"raw":"json"}""") } returns credential

        val result = repository.getAll().first()

        assertEquals(1, result.size)
        assertEquals(credential, result[0])
    }

    @Test
    fun `getAll silently drops corrupted entities`() = runTest {
        val bad = CredentialEntity("bad", "corrupted", 0L, false)
        every { dao.getAll() } returns flowOf(listOf(entity, bad))
        every { vaultCrypto.decrypt("encrypted_blob") } returns """{}"""
        every { vaultSerializer.deserializeOne("""{}""") } returns credential
        every { vaultCrypto.decrypt("corrupted") } throws Exception("bad cipher")

        val result = repository.getAll().first()

        assertEquals(1, result.size)
    }

    @Test
    fun `save encrypts credential and upserts with dirty flag`() = runTest {
        every { vaultSerializer.serializeOne(credential) } returns """{"id":"id-1"}"""
        every { vaultCrypto.encrypt("""{"id":"id-1"}""") } returns "encrypted_blob"
        coJustRun { dao.upsert(any()) }

        repository.save(credential)

        coVerify {
            dao.upsert(match {
                it.id == "id-1" && it.encryptedBlob == "encrypted_blob" && it.isDirty
            })
        }
    }

    @Test
    fun `save with isDirty false stores clean entity`() = runTest {
        every { vaultSerializer.serializeOne(credential) } returns """{}"""
        every { vaultCrypto.encrypt(any()) } returns "blob"
        coJustRun { dao.upsert(any()) }

        repository.save(credential, isDirty = false)

        coVerify { dao.upsert(match { !it.isDirty }) }
    }

    @Test
    fun `delete delegates to dao`() = runTest {
        coJustRun { dao.delete("id-1") }
        repository.delete("id-1")
        coVerify { dao.delete("id-1") }
    }

    @Test
    fun `hasDirtyEntries returns true when dirty entries exist`() = runTest {
        coEvery { dao.getDirty() } returns listOf(entity)
        assertTrue(repository.hasDirtyEntries())
    }

    @Test
    fun `markAllClean delegates to dao`() = runTest {
        coJustRun { dao.markAllClean() }
        repository.markAllClean()
        coVerify { dao.markAllClean() }
    }

    @Test
    fun `replaceAll clears table then inserts all as clean`() = runTest {
        coJustRun { dao.deleteAll() }
        coJustRun { dao.upsertAll(any()) }
        every { vaultSerializer.serializeOne(any()) } returns """{}"""
        every { vaultCrypto.encrypt(any()) } returns "blob"

        repository.replaceAll(listOf(credential))

        coVerify { dao.deleteAll() }
        coVerify { dao.upsertAll(match { it.size == 1 && !it[0].isDirty }) }
    }
}
