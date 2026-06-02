package com.github.aashishvibhu.credentialmanagement.security

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import javax.crypto.KeyGenerator

class VaultCryptoTest {

    private val keystoreManager = mockk<KeystoreManager>()
    private lateinit var vaultCrypto: VaultCrypto

    @Before
    fun setUp() {
        // Use a plain JVM AES key — avoids Android Keystore requirement in unit tests
        val testKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        every { keystoreManager.getOrCreateKey() } returns testKey
        vaultCrypto = VaultCrypto(keystoreManager)
    }

    @Test
    fun `round trip encrypt then decrypt returns original plaintext`() {
        val original = "super-secret-password-123!"
        val decrypted = vaultCrypto.decrypt(vaultCrypto.encrypt(original))
        assertEquals(original, decrypted)
    }

    @Test
    fun `encrypting the same plaintext twice produces different ciphertexts`() {
        val plaintext = "same-value"
        val first = vaultCrypto.encrypt(plaintext)
        val second = vaultCrypto.encrypt(plaintext)
        // Random IV guarantees distinct outputs
        assertNotEquals(first, second)
    }

    @Test
    fun `empty string round trips correctly`() {
        val result = vaultCrypto.decrypt(vaultCrypto.encrypt(""))
        assertEquals("", result)
    }

    @Test
    fun `unicode content round trips correctly`() {
        val unicode = "パスワード 🔑 pässwørd"
        assertEquals(unicode, vaultCrypto.decrypt(vaultCrypto.encrypt(unicode)))
    }

    @Test(expected = Exception::class)
    fun `tampered ciphertext throws on decrypt`() {
        val encrypted = vaultCrypto.encrypt("original-value")
        // Decode, flip a byte in the ciphertext region (past the 12-byte IV), re-encode
        val bytes = java.util.Base64.getDecoder().decode(encrypted)
        bytes[20] = (bytes[20].toInt() xor 0xFF).toByte()
        val tampered = java.util.Base64.getEncoder().encodeToString(bytes)
        vaultCrypto.decrypt(tampered)
    }
}
