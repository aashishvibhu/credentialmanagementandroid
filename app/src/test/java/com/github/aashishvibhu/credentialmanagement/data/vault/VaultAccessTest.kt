package com.github.aashishvibhu.credentialmanagement.data.vault

import com.github.aashishvibhu.credentialmanagement.data.local.LocalCredentialRepository
import com.github.aashishvibhu.credentialmanagement.domain.model.Credential
import com.github.aashishvibhu.credentialmanagement.domain.repository.DriveRepository
import com.github.aashishvibhu.credentialmanagement.security.TooManyAttemptsException
import com.github.aashishvibhu.credentialmanagement.security.VaultCodec
import com.github.aashishvibhu.credentialmanagement.security.VaultCrypto
import com.github.aashishvibhu.credentialmanagement.security.WrongPasswordException
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.SecureRandom

class VaultAccessTest {

    private val serializer = VaultSerializer()
    private val codec = VaultCodec(KdfParams("argon2id", 64, 1, 1), SecureRandom())
    private val legacyCrypto = mockk<VaultCrypto>()
    private val localRepo = mockk<LocalCredentialRepository>()
    private val keyStore = FakeKeyStore()

    /** In-memory Drive holding at most one vault file. */
    private var driveFile: String? = null
    private val driveRepo = mockk<DriveRepository>().also { drive ->
        coEvery { drive.downloadVault() } answers { driveFile }
        coEvery { drive.uploadVault(any()) } answers { driveFile = firstArg() }
    }

    private var clock = 1_000_000L
    private lateinit var access: VaultAccess

    private val cred = Credential(id = "1", title = "Google", username = "a@gmail.com", password = "p", createdAt = 1, updatedAt = 1)

    @Before
    fun setUp() {
        coJustRun { localRepo.refreshCache(any()) }
        access = VaultAccess(driveRepo, codec, keyStore.mock, legacyCrypto, serializer, localRepo)
        access.now = { clock }
    }

    @Test
    fun `empty Drive needs a new vault`() = runTest {
        assertEquals(VaultStatus.NoVault, access.checkRemote())
    }

    @Test
    fun `create uploads a vault, keeps the key on this phone and hands over the recovery key`() = runTest {
        access.create("master-password", listOf(cred))

        val envelope = codec.decode(driveFile!!)!!
        assertArrayEquals(codec.unlockWithPassword(envelope, "master-password"), keyStore.key)
        assertEquals(envelope.keyId, keyStore.keyId)
        assertNotNull(access.takeRecoveryKey())
        coVerify { localRepo.refreshCache(listOf(cred)) }
        assertEquals(VaultStatus.Ready, access.checkRemote())
    }

    @Test(expected = IllegalStateException::class)
    fun `create refuses to overwrite an existing vault`() = runTest {
        access.create("first-password", emptyList())
        access.create("second-password", emptyList())
    }

    @Test
    fun `a new phone needs the password, then opens the vault with it`() = runTest {
        access.create("master-password", listOf(cred))
        keyStore.reset()   // simulate reinstall / new phone

        assertEquals(VaultStatus.NeedsPassword, access.checkRemote())
        access.unlockWithPassword("master-password")

        assertTrue(access.isUnlockedOnThisPhone())
        assertEquals(VaultStatus.Ready, access.checkRemote())
    }

    @Test
    fun `recovery key opens the vault and lets you set a new password`() = runTest {
        access.create("forgotten-password", listOf(cred))
        val recoveryKey = access.takeRecoveryKey()!!
        keyStore.reset()

        access.unlockWithRecoveryKey(recoveryKey)
        access.resetPassword("brand-new-password")

        keyStore.reset()
        access.unlockWithPassword("brand-new-password")
        assertTrue(access.isUnlockedOnThisPhone())
    }

    @Test
    fun `changing the password keeps logins and invalidates the old password`() = runTest {
        access.create("old-password", listOf(cred))
        access.changePassword("old-password", "new-password")
        keyStore.reset()

        assertTrue(runCatching { access.unlockWithPassword("old-password") }.exceptionOrNull() is WrongPasswordException)
        access.unlockWithPassword("new-password")
        coVerify(atLeast = 1) { localRepo.refreshCache(listOf(cred)) }
    }

    @Test
    fun `lock screen check works offline from the last-seen vault`() = runTest {
        access.create("master-password", emptyList())
        driveFile = null   // Drive unreachable / gone

        access.verifyPassword("master-password")
    }

    @Test
    fun `five wrong passwords pause unlocking, and the wait grows`() = runTest {
        access.create("master-password", emptyList())

        repeat(4) { runCatching { access.verifyPassword("nope") } }
        assertEquals(1, access.attemptsLeft())
        assertEquals(0L, access.lockoutRemainingMs())

        runCatching { access.verifyPassword("nope") }
        assertEquals(30_000L, access.lockoutRemainingMs())
        // Even the right password waits
        assertTrue(runCatching { access.verifyPassword("master-password") }.exceptionOrNull() is TooManyAttemptsException)

        clock += 30_000L
        runCatching { access.verifyPassword("nope") }
        assertEquals(60_000L, access.lockoutRemainingMs())

        clock += 60_000L
        access.verifyPassword("master-password")
        assertEquals(VaultAccess.FREE_ATTEMPTS, access.attemptsLeft())
    }

    @Test
    fun `old vault this install can read is offered for upgrade`() = runTest {
        driveFile = "bGVnYWN5"
        every { legacyCrypto.decrypt("bGVnYWN5") } returns serializer.serialize(listOf(cred))

        val status = access.checkRemote()

        assertEquals(VaultStatus.LegacyReadable(listOf(cred)), status)
    }

    @Test
    fun `old vault from a lost install is unreadable and can be replaced`() = runTest {
        driveFile = "bGVnYWN5"
        every { legacyCrypto.decrypt(any()) } throws javax.crypto.AEADBadTagException()

        assertEquals(VaultStatus.LegacyUnreadable, access.checkRemote())
        access.create("master-password", emptyList(), replaceExisting = true)
        assertEquals(VaultStatus.Ready, access.checkRemote())
    }

    @Test
    fun `sign-out forgets the key`() = runTest {
        access.create("master-password", emptyList())
        access.forget()
        assertNull(keyStore.key)
        assertNull(access.takeRecoveryKey())
    }

    /** VaultKeyStore backed by fields instead of SharedPreferences and the Keystore. */
    private class FakeKeyStore {
        var key: ByteArray? = null
        var keyId: String? = null
        var envelope: String? = null
        var failed = 0
        var lockoutUntil = 0L

        fun reset() { key = null; keyId = null; envelope = null }

        val mock: VaultKeyStore = mockk<VaultKeyStore>().also { ks ->
            every { ks.saveVaultKey(any(), any()) } answers { keyId = firstArg(); key = secondArg() }
            every { ks.vaultKey() } answers { key }
            every { ks.keyId() } answers { keyId }
            every { ks.hasVaultKey() } answers { key != null }
            every { ks.clearVaultKey() } answers { key = null; keyId = null }
            every { ks.envelope } answers { envelope }
            every { ks.envelope = any() } answers { envelope = firstArg() }
            every { ks.failedAttempts } answers { failed }
            every { ks.failedAttempts = any() } answers { failed = firstArg() }
            every { ks.lockoutUntil } answers { lockoutUntil }
            every { ks.lockoutUntil = any() } answers { lockoutUntil = firstArg() }
            every { ks.clear() } answers { reset(); failed = 0; lockoutUntil = 0 }
        }
    }
}
