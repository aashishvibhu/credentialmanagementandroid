package com.github.aashishvibhu.credentialmanagement.security

import com.github.aashishvibhu.credentialmanagement.data.vault.KdfParams
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class VaultCodecTest {

    // Tiny Argon2 cost so tests run fast; production uses VaultCodec.DEFAULT_KDF
    private val codec = VaultCodec(KdfParams("argon2id", memoryKiB = 64, iterations = 1, parallelism = 1), SecureRandom())
    private val json = """[{"id":"1","title":"Google","username":"a@gmail.com","password":"p"}]"""

    @Test
    fun `new vault opens with the master password`() {
        val created = codec.newVault(json, "correct horse battery")
        val key = codec.unlockWithPassword(created.envelope, "correct horse battery")
        assertArrayEquals(created.vaultKey, key)
        assertEquals(json, codec.decryptPayload(created.envelope, key))
    }

    @Test
    fun `new vault opens with the recovery key in any case and spacing`() {
        val created = codec.newVault(json, "pw-123456789")
        val typed = created.recoveryKey.lowercase().replace("-", " ")
        val key = codec.unlockWithRecoveryKey(created.envelope, typed)
        assertEquals(json, codec.decryptPayload(created.envelope, key))
    }

    @Test(expected = WrongPasswordException::class)
    fun `wrong password is rejected`() {
        val created = codec.newVault(json, "right-password")
        codec.unlockWithPassword(created.envelope, "wrong-password")
    }

    @Test(expected = WrongRecoveryKeyException::class)
    fun `wrong recovery key is rejected`() {
        val created = codec.newVault(json, "pw-123456789")
        codec.unlockWithRecoveryKey(created.envelope, RecoveryKey.generate(SecureRandom()))
    }

    @Test(expected = VaultLockedException::class)
    fun `another vault's key can't decrypt the payload`() {
        val a = codec.newVault(json, "pw-123456789")
        val b = codec.newVault(json, "pw-123456789")
        codec.decryptPayload(a.envelope, b.vaultKey)
    }

    @Test
    fun `envelope survives encode and decode`() {
        val created = codec.newVault(json, "pw-123456789")
        val decoded = codec.decode(codec.encode(created.envelope))
        assertEquals(created.envelope, decoded)
        assertTrue(codec.encode(created.envelope).contains("\"v\":2"))
    }

    @Test
    fun `version 1 vault is detected as legacy`() {
        assertNull(codec.decode("QUJDREVGR0hJSktMTU5PUA=="))
    }

    @Test
    fun `changing the password keeps the payload and recovery key`() {
        val created = codec.newVault(json, "old-password")
        val changed = codec.changePassword(created.envelope, created.vaultKey, "new-password")

        assertArrayEquals(created.vaultKey, codec.unlockWithPassword(changed, "new-password"))
        assertArrayEquals(created.vaultKey, codec.unlockWithRecoveryKey(changed, created.recoveryKey))
        assertEquals(created.envelope.payload, changed.payload)
        assertEquals(created.envelope.keyId, changed.keyId)
        assertThrows<WrongPasswordException> { codec.unlockWithPassword(changed, "old-password") }
    }

    @Test
    fun `new recovery key replaces the old one`() {
        val created = codec.newVault(json, "pw-123456789")
        val (updated, newKey) = codec.newRecoveryKey(created.envelope, created.vaultKey)

        assertNotEquals(created.recoveryKey, newKey)
        assertArrayEquals(created.vaultKey, codec.unlockWithRecoveryKey(updated, newKey))
        assertThrows<WrongRecoveryKeyException> { codec.unlockWithRecoveryKey(updated, created.recoveryKey) }
    }

    @Test
    fun `withPayload re-encrypts new contents under the same key`() {
        val created = codec.newVault("[]", "pw-123456789")
        val updated = codec.withPayload(created.envelope, created.vaultKey, json)
        assertEquals(json, codec.decryptPayload(updated, created.vaultKey))
        assertEquals(created.envelope.password, updated.password)
    }

    @Test
    fun `password text is normalised before deriving the key`() {
        // "é" as one code point vs "e" + combining accent
        val created = codec.newVault(json, "café-password")
        codec.unlockWithPassword(created.envelope, "café-password")
    }

    @Test
    fun `recovery keys are well formed and unambiguous`() {
        val key = RecoveryKey.generate(SecureRandom())
        assertTrue(Regex("[A-Z0-9]{4}(-[A-Z0-9]{4}){5}").matches(key))
        assertTrue(RecoveryKey.isWellFormed(key.lowercase()))
        assertFalse(key.any { it in "01ILOU" })
        assertFalse(RecoveryKey.isWellFormed("ABCD-EFGH"))
    }

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) return
            throw AssertionError("Expected ${T::class.simpleName} but got $e")
        }
        throw AssertionError("Expected ${T::class.simpleName}")
    }
}
