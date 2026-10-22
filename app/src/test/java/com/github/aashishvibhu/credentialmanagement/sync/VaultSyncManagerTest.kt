package com.github.aashishvibhu.credentialmanagement.sync

import android.util.Log
import com.github.aashishvibhu.credentialmanagement.data.local.LocalCredentialRepository
import com.github.aashishvibhu.credentialmanagement.data.vault.KdfParams
import com.github.aashishvibhu.credentialmanagement.data.vault.VaultKeyStore
import com.github.aashishvibhu.credentialmanagement.data.vault.VaultSerializer
import com.github.aashishvibhu.credentialmanagement.domain.model.Credential
import com.github.aashishvibhu.credentialmanagement.domain.repository.DriveRepository
import com.github.aashishvibhu.credentialmanagement.security.VaultCodec
import com.github.aashishvibhu.credentialmanagement.security.VaultLockedException
import com.github.aashishvibhu.credentialmanagement.security.VaultUnreadableException
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.SecureRandom

@OptIn(ExperimentalCoroutinesApi::class)
class VaultSyncManagerTest {

    private val localRepo  = mockk<LocalCredentialRepository>()
    private val driveRepo  = mockk<DriveRepository>()
    private val keyStore   = mockk<VaultKeyStore>(relaxed = true)
    private val serializer = VaultSerializer()
    private val codec = VaultCodec(KdfParams("argon2id", 64, 1, 1), SecureRandom())

    private lateinit var vaultSyncManager: VaultSyncManager

    private val cred = Credential(id = "1", title = "T", username = "U", password = "P", updatedAt = 1000L, createdAt = 1000L)

    // A vault in Drive holding [cred], and this phone's copy of its key
    private val vault = codec.newVault(serializer.serialize(listOf(cred)), "master-password")
    private val vaultText = codec.encode(vault.envelope)

    @Before
    fun setUp() {
        // android.util.Log isn't available on the JVM
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        every { keyStore.vaultKey() } returns vault.vaultKey
        every { keyStore.keyId() } returns vault.envelope.keyId
        every { keyStore.envelope } returns vaultText
        coJustRun { localRepo.refreshCache(any()) }
        vaultSyncManager = VaultSyncManager(localRepo, driveRepo, codec, keyStore, serializer)
    }

    // ── pullFromDrive ─────────────────────────────────────────────────────────

    @Test
    fun `pullFromDrive decrypts with the stored vault key and refreshes the cache`() = runTest {
        coEvery { driveRepo.downloadVault() } returns vaultText

        vaultSyncManager.pullFromDrive()

        coVerify { localRepo.refreshCache(listOf(cred)) }
        verify { keyStore.envelope = vaultText }
        assertEquals(SyncState.Success, vaultSyncManager.syncState.value)
    }

    @Test
    fun `pullFromDrive skips cache refresh when no vault exists`() = runTest {
        coEvery { driveRepo.downloadVault() } returns null

        vaultSyncManager.pullFromDrive()

        coVerify(exactly = 0) { localRepo.refreshCache(any()) }
        assertEquals(SyncState.Success, vaultSyncManager.syncState.value)
    }

    @Test
    fun `pullFromDrive needs the master password when this phone has no key`() = runTest {
        coEvery { driveRepo.downloadVault() } returns vaultText
        every { keyStore.vaultKey() } returns null

        val error = runCatching { vaultSyncManager.pullFromDrive() }.exceptionOrNull()

        assertTrue(error is VaultLockedException)
        val state = vaultSyncManager.syncState.value as SyncState.Error
        assertTrue(state.message.contains("master password"))
    }

    @Test
    fun `pullFromDrive forgets the key when another phone created a new vault`() = runTest {
        val other = codec.newVault("[]", "someone-else")
        coEvery { driveRepo.downloadVault() } returns codec.encode(other.envelope)

        val error = runCatching { vaultSyncManager.pullFromDrive() }.exceptionOrNull()

        assertTrue(error is VaultLockedException)
        verify { keyStore.clearVaultKey() }
    }

    @Test
    fun `pullFromDrive reports an old-format vault as unreadable`() = runTest {
        coEvery { driveRepo.downloadVault() } returns "QUJDREVGR0g="

        val error = runCatching { vaultSyncManager.pullFromDrive() }.exceptionOrNull()

        assertTrue(error is VaultUnreadableException)
    }

    @Test
    fun `pullFromDrive state transitions to Error and rethrows on exception`() = runTest {
        coEvery { driveRepo.downloadVault() } throws RuntimeException("Network error")

        try { vaultSyncManager.pullFromDrive() } catch (_: Exception) {}

        assertTrue(vaultSyncManager.syncState.value is SyncState.Error)
    }

    @Test
    fun `concurrent pull is skipped`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { driveRepo.downloadVault() } coAnswers { gate.await(); null }

        // First pull suspends inside downloadVault, so the second one overlaps it
        val first = launch { vaultSyncManager.pullFromDrive() }
        runCurrent()
        vaultSyncManager.pullFromDrive()
        assertEquals(SyncState.Skipped, vaultSyncManager.syncState.value)

        gate.complete(Unit)
        first.join()
        assertEquals(SyncState.Success, vaultSyncManager.syncState.value)
        coVerify(exactly = 1) { driveRepo.downloadVault() }
    }

    // ── pushFullVault ─────────────────────────────────────────────────────────

    @Test
    fun `pushFullVault re-encrypts under the same key and keeps the key slots`() = runTest {
        val uploaded = slot<String>()
        coJustRun { driveRepo.uploadVault(capture(uploaded)) }
        val updated = listOf(cred, cred.copy(id = "2", title = "New"))

        vaultSyncManager.pushFullVault(updated)

        val envelope = codec.decode(uploaded.captured)!!
        assertEquals(vault.envelope.password, envelope.password)
        assertEquals(vault.envelope.keyId, envelope.keyId)
        assertEquals(updated, serializer.deserialize(codec.decryptPayload(envelope, vault.vaultKey)))
        // Still opens with the master password after the push
        codec.unlockWithPassword(envelope, "master-password")
    }

    @Test(expected = VaultLockedException::class)
    fun `pushFullVault refuses without a vault key`() = runTest {
        every { keyStore.vaultKey() } returns null
        vaultSyncManager.pushFullVault(listOf(cred))
    }

    // ── pullLatestCredentials ─────────────────────────────────────────────────

    @Test
    fun `pullLatestCredentials returns decrypted list`() = runTest {
        coEvery { driveRepo.downloadVault() } returns vaultText

        assertEquals(listOf(cred), vaultSyncManager.pullLatestCredentials())
    }

    @Test
    fun `pullLatestCredentials returns empty list when no vault exists`() = runTest {
        coEvery { driveRepo.downloadVault() } returns null

        assertTrue(vaultSyncManager.pullLatestCredentials().isEmpty())
    }
}
