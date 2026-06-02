package com.github.aashishvibhu.credentialmanagement.security

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
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
    }

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
