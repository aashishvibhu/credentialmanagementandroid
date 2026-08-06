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
    private val entity = CredentialEntity("id-1", "encrypted_blob", 0L)

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
        val bad = CredentialEntity("bad", "corrupted", 0L)
        every { dao.getAll() } returns flowOf(listOf(entity, bad))
        every { vaultCrypto.decrypt("encrypted_blob") } returns """{}"""
        every { vaultSerializer.deserializeOne("""{}""") } returns credential
        every { vaultCrypto.decrypt("corrupted") } throws Exception("bad cipher")

        val result = repository.getAll().first()

        assertEquals(1, result.size)
    }

    @Test
    fun `save encrypts credential and upserts entity`() = runTest {
        every { vaultSerializer.serializeOne(credential) } returns """{"id":"id-1"}"""
        every { vaultCrypto.encrypt("""{"id":"id-1"}""") } returns "encrypted_blob"
        coJustRun { dao.upsert(any()) }

        repository.save(credential)

        coVerify {
            dao.upsert(match {
                it.id == "id-1" && it.encryptedBlob == "encrypted_blob"
            })
        }
    }

    @Test
    fun `delete delegates to dao`() = runTest {
        coJustRun { dao.delete("id-1") }
        repository.delete("id-1")
        coVerify { dao.delete("id-1") }
    }

    @Test
    fun `refreshCache clears table then inserts all`() = runTest {
        coJustRun { dao.deleteAll() }
        coJustRun { dao.upsert(any()) }
        every { vaultSerializer.serializeOne(any()) } returns """{}"""
        every { vaultCrypto.encrypt(any()) } returns "blob"

        repository.refreshCache(listOf(credential))

        coVerify { dao.deleteAll() }
        coVerify(exactly = 1) { dao.upsert(any()) }
    }

    @Test
    fun `clearCache deletes all from dao`() = runTest {
        coJustRun { dao.deleteAll() }
        repository.clearCache()
        coVerify { dao.deleteAll() }
    }
}
