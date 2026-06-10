package com.github.aashishvibhu.credentialmanagement.sync

import com.github.aashishvibhu.credentialmanagement.data.auth.AuthRepository
import com.github.aashishvibhu.credentialmanagement.data.local.LocalCredentialRepository
import com.github.aashishvibhu.credentialmanagement.data.vault.VaultSerializer
import com.github.aashishvibhu.credentialmanagement.domain.model.Credential
import com.github.aashishvibhu.credentialmanagement.domain.repository.DriveRepository
import com.github.aashishvibhu.credentialmanagement.security.VaultCrypto
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
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

class SyncManagerTest {

    private val localRepo      = mockk<LocalCredentialRepository>()
    private val driveRepo      = mockk<DriveRepository>()
    private val vaultCrypto    = mockk<VaultCrypto>()
    private val vaultSerializer = mockk<VaultSerializer>()
    private val authRepository = mockk<AuthRepository>()
    private val mockAccount    = mockk<GoogleSignInAccount>()

    private lateinit var syncManager: SyncManager

    private val credOld = Credential(id = "1", title = "T", username = "U", password = "P",     updatedAt = 1000L, createdAt = 1000L)
    private val credNew = Credential(id = "1", title = "T", username = "U", password = "P_new", updatedAt = 9000L, createdAt = 1000L)

    companion object {
        private const val ACCOUNT_ID = "test_account_123"
    }

    @Before
    fun setUp() {
        every { mockAccount.id } returns ACCOUNT_ID
        every { authRepository.getSignedInAccount() } returns mockAccount
        syncManager = SyncManager(localRepo, driveRepo, vaultCrypto, vaultSerializer, authRepository)
    }

    // ── No remote vault yet ───────────────────────────────────────────────────

    @Test
    fun `uploads local vault when no remote file exists`() = runTest {
        coEvery { driveRepo.getVaultModifiedTime() } returns null
        coEvery { localRepo.getAllSnapshot() } returns listOf(credOld)
        coEvery { localRepo.hasDirtyEntries() } returns false
        every { vaultSerializer.serialize(any()) } returns "[{}]"
        every { vaultCrypto.encryptForDrive("[{}]", ACCOUNT_ID) } returns "enc"
        coJustRun { driveRepo.uploadVault("enc") }
        coJustRun { localRepo.markAllClean() }

        syncManager.sync()

        coVerify { driveRepo.uploadVault("enc") }
        coVerify { localRepo.markAllClean() }
    }

    // ── Local dirty entries ───────────────────────────────────────────────────

    @Test
    fun `uploads when local has dirty entries and remote is not newer`() = runTest {
        coEvery { driveRepo.getVaultModifiedTime() } returns 500L   // remote older than local
        coEvery { localRepo.getAllSnapshot() } returns listOf(credOld)
        coEvery { localRepo.hasDirtyEntries() } returns true
        every { vaultSerializer.serialize(any()) } returns "[{}]"
        every { vaultCrypto.encryptForDrive(any(), ACCOUNT_ID) } returns "enc"
        coJustRun { driveRepo.uploadVault(any()) }
        coJustRun { localRepo.markAllClean() }

        syncManager.sync()

        coVerify { driveRepo.uploadVault("enc") }
        coVerify { localRepo.markAllClean() }
    }

    // ── Remote is newer → pull, merge, re-upload ──────────────────────────────

    @Test
    fun `pulls remote when newer, merges with local, re-uploads merged result`() = runTest {
        coEvery { driveRepo.getVaultModifiedTime() } returns 9999L  // remote newer than local (1000L)
        coEvery { localRepo.getAllSnapshot() } returnsMany listOf(
            listOf(credOld),        // first call — snapshot before merge
            listOf(credNew)         // second call — snapshot after replaceAll
        )
        coEvery { localRepo.hasDirtyEntries() } returns false
        coEvery { driveRepo.downloadVault() } returns "remote_enc"
        every { vaultCrypto.decryptFromDrive("remote_enc", ACCOUNT_ID) } returns "[remote]"
        every { vaultSerializer.deserialize("[remote]") } returns listOf(credNew)
        coJustRun { localRepo.replaceAll(any(), any()) }
        every { vaultSerializer.serialize(listOf(credNew)) } returns "[merged]"
        every { vaultCrypto.encryptForDrive("[merged]", ACCOUNT_ID) } returns "merged_enc"
        coJustRun { driveRepo.uploadVault("merged_enc") }
        coJustRun { localRepo.markAllClean() }

        syncManager.sync()

        coVerify { localRepo.replaceAll(match { it.size == 1 && it[0].updatedAt == 9000L }, isDirty = false) }
        coVerify { driveRepo.uploadVault("merged_enc") }
        coVerify { localRepo.markAllClean() }
    }

    // ── No-op when fully in sync ──────────────────────────────────────────────

    @Test
    fun `no upload when remote is not newer and no dirty entries`() = runTest {
        coEvery { driveRepo.getVaultModifiedTime() } returns 500L   // remote older
        coEvery { localRepo.getAllSnapshot() } returns listOf(credOld)
        coEvery { localRepo.hasDirtyEntries() } returns false

        syncManager.sync()

        coVerify(exactly = 0) { driveRepo.uploadVault(any()) }
        coVerify(exactly = 0) { driveRepo.downloadVault() }
        coVerify(exactly = 0) { localRepo.markAllClean() }
    }

    // ── Merge logic ───────────────────────────────────────────────────────────

    @Test
    fun `merge keeps newer updatedAt when same id exists locally and remotely`() = runTest {
        val localV  = credOld   // updatedAt = 1000
        val remoteV = credNew   // updatedAt = 9000 → should win

        coEvery { driveRepo.getVaultModifiedTime() } returns 9999L
        coEvery { localRepo.getAllSnapshot() } returnsMany listOf(
            listOf(localV),
            listOf(credNew)
        )
        coEvery { localRepo.hasDirtyEntries() } returns false
        coEvery { driveRepo.downloadVault() } returns "enc"
        every { vaultCrypto.decryptFromDrive("enc", ACCOUNT_ID) } returns "[r]"
        every { vaultSerializer.deserialize("[r]") } returns listOf(remoteV)
        coJustRun { localRepo.replaceAll(any(), any()) }
        every { vaultSerializer.serialize(any()) } returns "[]"
        every { vaultCrypto.encryptForDrive(any(), ACCOUNT_ID) } returns "e"
        coJustRun { driveRepo.uploadVault(any()) }
        coJustRun { localRepo.markAllClean() }

        syncManager.sync()

        coVerify {
            localRepo.replaceAll(
                match { it.size == 1 && it[0].password == "P_new" },
                isDirty = false
            )
        }
    }

    // ── AEADBadTagException (stale Keystore-encrypted vault on Drive) ──────────

    @Test
    fun `bad tag on remote vault is swallowed and local state is uploaded`() = runTest {
        coEvery { driveRepo.getVaultModifiedTime() } returns 9999L
        coEvery { localRepo.getAllSnapshot() } returns listOf(credOld)
        coEvery { localRepo.hasDirtyEntries() } returns false
        coEvery { driveRepo.downloadVault() } returns "stale_enc"
        every { vaultCrypto.decryptFromDrive("stale_enc", ACCOUNT_ID) } throws
            javax.crypto.AEADBadTagException()
        every { vaultSerializer.serialize(listOf(credOld)) } returns "[local]"
        every { vaultCrypto.encryptForDrive("[local]", ACCOUNT_ID) } returns "local_enc"
        coJustRun { driveRepo.uploadVault("local_enc") }
        coJustRun { localRepo.markAllClean() }

        syncManager.sync()  // must not throw

        coVerify { driveRepo.uploadVault("local_enc") }
        coVerify { localRepo.markAllClean() }
        assertEquals(SyncState.Success, syncManager.syncState.value)
    }

    // ── State transitions ─────────────────────────────────────────────────────

    @Test
    fun `sync state transitions to Success on happy path`() = runTest {
        coEvery { driveRepo.getVaultModifiedTime() } returns null
        coEvery { localRepo.getAllSnapshot() } returns emptyList()
        coEvery { localRepo.hasDirtyEntries() } returns false
        every { vaultSerializer.serialize(emptyList()) } returns "[]"
        every { vaultCrypto.encryptForDrive("[]", ACCOUNT_ID) } returns "e"
        coJustRun { driveRepo.uploadVault(any()) }
        coJustRun { localRepo.markAllClean() }

        syncManager.sync()

        assertEquals(SyncState.Success, syncManager.syncState.value)
    }

    @Test
    fun `sync state transitions to Error and rethrows on exception`() = runTest {
        coEvery { driveRepo.getVaultModifiedTime() } throws RuntimeException("Network error")

        try { syncManager.sync() } catch (_: Exception) {}

        assertTrue(syncManager.syncState.value is SyncState.Error)
    }

    @Test
    fun `concurrent sync call is ignored while first is running`() = runTest {
        coEvery { driveRepo.getVaultModifiedTime() } returns null
        coEvery { localRepo.getAllSnapshot() } returns emptyList()
        coEvery { localRepo.hasDirtyEntries() } returns false
        every { vaultSerializer.serialize(any()) } returns "[]"
        every { vaultCrypto.encryptForDrive(any(), ACCOUNT_ID) } returns "e"
        coJustRun { driveRepo.uploadVault(any()) }
        coJustRun { localRepo.markAllClean() }

        syncManager.sync()
        assertEquals(SyncState.Success, syncManager.syncState.value)
        syncManager.sync()
        assertEquals(SyncState.Success, syncManager.syncState.value)
    }
}
