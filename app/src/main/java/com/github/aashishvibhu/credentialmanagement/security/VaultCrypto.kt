package com.github.aashishvibhu.credentialmanagement.security

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Singleton
class VaultCrypto @Inject constructor(
    private val keystoreManager: KeystoreManager
) {
    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        // Fixed salt — security comes from the account ID's entropy, not the salt's secrecy
        private val DRIVE_VAULT_SALT = "credential.manager.drive.vault.v1".toByteArray(Charsets.UTF_8)
        private const val PBKDF2_ITERATIONS = 100_000
    }

    // Cached so PBKDF2 only runs once per account per session
    @Volatile private var cachedAccountId: String? = null
    @Volatile private var cachedDriveKey: SecretKey? = null

    @OptIn(ExperimentalEncodingApi::class)
    fun encrypt(plaintext: String): String {
        val key = keystoreManager.getOrCreateKey()
        val cipher = buildCipher(Cipher.ENCRYPT_MODE, key, iv = null)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encode(iv + ciphertext)
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun decrypt(encoded: String): String {
        val combined = Base64.decode(encoded)
        val iv = combined.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = combined.copyOfRange(IV_LENGTH_BYTES, combined.size)
        val key = keystoreManager.getOrCreateKey()
        val cipher = buildCipher(Cipher.DECRYPT_MODE, key, iv)
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    /** Encrypt a Drive vault payload using a key derived from the Google account ID. */
    @OptIn(ExperimentalEncodingApi::class)
    fun encryptForDrive(plaintext: String, accountId: String): String {
        val cipher = buildCipher(Cipher.ENCRYPT_MODE, getDriveKey(accountId), iv = null)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encode(iv + ciphertext)
    }

    /** Decrypt a Drive vault payload using a key derived from the Google account ID. */
    @OptIn(ExperimentalEncodingApi::class)
    fun decryptFromDrive(encoded: String, accountId: String): String {
        val combined = Base64.decode(encoded)
        val iv = combined.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = combined.copyOfRange(IV_LENGTH_BYTES, combined.size)
        val cipher = buildCipher(Cipher.DECRYPT_MODE, getDriveKey(accountId), iv)
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    @Synchronized
    private fun getDriveKey(accountId: String): SecretKey {
        if (accountId == cachedAccountId) cachedDriveKey?.let { return it }
        val spec = PBEKeySpec(accountId.toCharArray(), DRIVE_VAULT_SALT, PBKDF2_ITERATIONS, 256)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(raw, "AES").also {
            cachedAccountId = accountId
            cachedDriveKey = it
        }
    }

    private fun buildCipher(mode: Int, key: SecretKey, iv: ByteArray?): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        if (iv == null) {
            cipher.init(mode, key)
        } else {
            cipher.init(mode, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        }
        return cipher
    }
}
