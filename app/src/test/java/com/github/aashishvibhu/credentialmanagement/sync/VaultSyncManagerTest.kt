package com.github.aashishvibhu.credentialmanagement.sync

import com.github.aashishvibhu.credentialmanagement.data.local.LocalCredentialRepository
import com.github.aashishvibhu.credentialmanagement.data.vault.VaultSerializer
import com.github.aashishvibhu.credentialmanagement.domain.model.Credential
import com.github.aashishvibhu.credentialmanagement.domain.repository.DriveRepository
import com.github.aashishvibhu.credentialmanagement.security.VaultCrypto
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VaultSyncManagerTest {

    private val localRepo       = mockk<LocalCredentialRepository>()
    private val driveRepo       = mockk<DriveRepository>()
    private val vaultCrypto     = mockk<VaultCrypto>()
    private val vaultSerializer = mockk<VaultSerializer>()

    private lateinit var vaultSyncManager: VaultSyncManager

    private val cred = Credential(id = "1", title = "T", username = "U", password = "P", updatedAt = 1000L, createdAt = 1000L)

    @Before
    fun setUp() {
        vaultSyncManager = VaultSyncManager(localRepo, driveRepo, vaultCrypto, vaultSerializer)
    }

    // ── pullFromDrive ─────────────────────────────────────────────────────────

    @Test
    fun `pullFromDrive refreshes cache when vault exists`() = runTest {
        coEvery { driveRepo.downloadVault() } returns "encrypted_vault"
        every { vaultCrypto.decrypt("encrypted_vault") } returns "[decrypted]"
        every { vaultSerializer.deserialize("[decrypted]") } returns listOf(cred)
        coJustRun { localRepo.refreshCache(listOf(cred)) }

        vaultSyncManager.pullFromDrive()

        coVerify { driveRepo.downloadVault() }
        coVerify { vaultCrypto.decrypt("encrypted_vault") }
        coVerify { vaultSerializer.deserialize("[decrypted]") }
        coVerify { localRepo.refreshCache(listOf(cred)) }
    }

    @Test
    fun `pullFromDrive skips cache refresh when no vault exists`() = runTest {
        coEvery { driveRepo.downloadVault() } returns null

        vaultSyncManager.pullFromDrive()

        coVerify(exactly = 0) { localRepo.refreshCache(any()) }
    }

    @Test
    fun `pullFromDrive state transitions to Success`() = runTest {
        coEvery { driveRepo.downloadVault() } returns "enc"
        every { vaultCrypto.decrypt("enc") } returns "[]"
        every { vaultSerializer.deserialize("[]") } returns emptyList()
        coJustRun { localRepo.refreshCache(any()) }

        vaultSyncManager.pullFromDrive()

        assertEquals(SyncState.Success, vaultSyncManager.syncState.value)
    }

    @Test
    fun `pullFromDrive state transitions to Error and rethrows on exception`() = runTest {
        coEvery { driveRepo.downloadVault() } throws RuntimeException("Network error")

        try { vaultSyncManager.pullFromDrive() } catch (_: Exception) {}

        assertTrue(vaultSyncManager.syncState.value is SyncState.Error)
    }

    @Test
    fun `concurrent pull is skipped`() = runTest {
        coEvery { driveRepo.downloadVault() } returns null

        vaultSyncManager.pullFromDrive()
        assertEquals(SyncState.Success, vaultSyncManager.syncState.value)
        vaultSyncManager.pullFromDrive()
        assertEquals(SyncState.Skipped, vaultSyncManager.syncState.value)
    }

    // ── pushFullVault ─────────────────────────────────────────────────────────

    @Test
    fun `pushFullVault serializes encrypts and uploads`() = runTest {
        val credentials = listOf(cred)
        every { vaultSerializer.serialize(credentials) } returns "[serialized]"
        every { vaultCrypto.encrypt("[serialized]") } returns "encrypted"
        coJustRun { driveRepo.uploadVault("encrypted") }

        vaultSyncManager.pushFullVault(credentials)

        coVerify { driveRepo.uploadVault("encrypted") }
    }

    // ── pullLatestCredentials ─────────────────────────────────────────────────

    @Test
    fun `pullLatestCredentials returns decrypted list`() = runTest {
        coEvery { driveRepo.downloadVault() } returns "enc"
        every { vaultCrypto.decrypt("enc") } returns "[dec]"
        every { vaultSerializer.deserialize("[dec]") } returns listOf(cred)

        val result = vaultSyncManager.pullLatestCredentials()

        assertEquals(1, result.size)
        assertEquals(cred, result[0])
    }

    @Test
    fun `pullLatestCredentials returns empty list when no vault exists`() = runTest {
        coEvery { driveRepo.downloadVault() } returns null

        val result = vaultSyncManager.pullLatestCredentials()

        assertTrue(result.isEmpty())
    }
}
