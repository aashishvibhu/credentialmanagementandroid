package com.github.aashishvibhu.credentialmanagement.security

import com.github.aashishvibhu.credentialmanagement.data.vault.KdfParams
import com.github.aashishvibhu.credentialmanagement.data.vault.KeySlot
import com.github.aashishvibhu.credentialmanagement.data.vault.VaultEnvelope
import kotlinx.serialization.json.Json
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom
import java.text.Normalizer
import java.util.Base64
import java.util.UUID
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure-JVM crypto for the version 2 vault (no Android APIs, so it is unit-tested directly).
 *
 * Key hierarchy: master password / recovery key → Argon2id → key-encryption key, which wraps a
 * random 256-bit vault key; the vault key encrypts the credentials with AES-256-GCM.
 */
@Singleton
class VaultCodec(
    private val kdf: KdfParams,
    private val random: SecureRandom
) {
    @Inject constructor() : this(DEFAULT_KDF, SecureRandom())

    companion object {
        const val VERSION = 2

        /** ~1 s on a mid-range phone; stored in each vault so it can be raised later. */
        val DEFAULT_KDF = KdfParams(alg = "argon2id", memoryKiB = 32 * 1024, iterations = 3, parallelism = 1)

        private const val KEY_BYTES = 32
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128

        // Associated data binds each ciphertext to its purpose, so a slot can't be swapped for the payload
        private val AAD_KEY_SLOT = "vault-key-v2".toByteArray()
        private val AAD_PAYLOAD  = "vault-payload-v2".toByteArray()

        private val json = Json { ignoreUnknownKeys = true }
    }

    /** A freshly created vault and the secrets the user must keep. */
    class NewVault(val envelope: VaultEnvelope, val vaultKey: ByteArray, val recoveryKey: String)

    // ── Encoding ──────────────────────────────────────────────────────────────

    fun encode(envelope: VaultEnvelope): String = json.encodeToString(VaultEnvelope.serializer(), envelope)

    /** Parses a version 2 vault; returns null for a version 1 (bare Base64) vault. */
    fun decode(text: String): VaultEnvelope? {
        if (!text.trimStart().startsWith("{")) return null
        val envelope = json.decodeFromString(VaultEnvelope.serializer(), text)
        require(envelope.v == VERSION) { "Unsupported vault version ${envelope.v}" }
        return envelope
    }

    // ── Creating and unlocking ────────────────────────────────────────────────

    fun newVault(credentialsJson: String, masterPassword: String): NewVault {
        val vaultKey = randomBytes(KEY_BYTES)
        val recoveryKey = RecoveryKey.generate(random)
        val envelope = VaultEnvelope(
            v = VERSION,
            keyId = UUID.randomUUID().toString(),
            kdf = kdf,
            password = wrap(vaultKey, passwordBytes(masterPassword), kdf),
            recovery = wrap(vaultKey, RecoveryKey.bytes(recoveryKey), kdf),
            payload = encryptPayload(vaultKey, credentialsJson)
        )
        return NewVault(envelope, vaultKey, recoveryKey)
    }

    fun unlockWithPassword(envelope: VaultEnvelope, masterPassword: String): ByteArray =
        unwrap(envelope.password, passwordBytes(masterPassword), envelope.kdf) ?: throw WrongPasswordException()

    fun unlockWithRecoveryKey(envelope: VaultEnvelope, recoveryKey: String): ByteArray =
        unwrap(envelope.recovery, RecoveryKey.bytes(recoveryKey), envelope.kdf) ?: throw WrongRecoveryKeyException()

    // ── Payload ───────────────────────────────────────────────────────────────

    /** Decrypts the credentials JSON; throws [VaultLockedException] if [vaultKey] isn't this vault's key. */
    fun decryptPayload(envelope: VaultEnvelope, vaultKey: ByteArray): String {
        val plain = gcmDecrypt(vaultKey, Base64.getDecoder().decode(envelope.payload), AAD_PAYLOAD)
            ?: throw VaultLockedException()
        return plain.toString(Charsets.UTF_8)
    }

    fun withPayload(envelope: VaultEnvelope, vaultKey: ByteArray, credentialsJson: String): VaultEnvelope =
        envelope.copy(payload = encryptPayload(vaultKey, credentialsJson))

    // ── Changing secrets ──────────────────────────────────────────────────────

    /**
     * Re-wraps the vault key for a new password. The payload and recovery key are untouched, and
     * the vault keeps its own KDF params because both slots share them.
     */
    fun changePassword(envelope: VaultEnvelope, vaultKey: ByteArray, newPassword: String): VaultEnvelope =
        envelope.copy(password = wrap(vaultKey, passwordBytes(newPassword), envelope.kdf))

    /** Replaces the recovery key; the old one stops working. */
    fun newRecoveryKey(envelope: VaultEnvelope, vaultKey: ByteArray): Pair<VaultEnvelope, String> {
        val recoveryKey = RecoveryKey.generate(random)
        val updated = envelope.copy(recovery = wrap(vaultKey, RecoveryKey.bytes(recoveryKey), envelope.kdf))
        return updated to recoveryKey
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun encryptPayload(vaultKey: ByteArray, credentialsJson: String): String =
        Base64.getEncoder().encodeToString(gcmEncrypt(vaultKey, credentialsJson.toByteArray(Charsets.UTF_8), AAD_PAYLOAD))

    private fun wrap(vaultKey: ByteArray, secret: ByteArray, params: KdfParams): KeySlot {
        val salt = randomBytes(SALT_BYTES)
        val kek = deriveKey(secret, salt, params)
        val wrapped = gcmEncrypt(kek, vaultKey, AAD_KEY_SLOT)
        kek.fill(0)
        return KeySlot(
            salt = Base64.getEncoder().encodeToString(salt),
            wrappedKey = Base64.getEncoder().encodeToString(wrapped)
        )
    }

    /** Returns the vault key, or null when [secret] is wrong (GCM tag mismatch). */
    private fun unwrap(slot: KeySlot, secret: ByteArray, params: KdfParams): ByteArray? {
        val kek = deriveKey(secret, Base64.getDecoder().decode(slot.salt), params)
        return gcmDecrypt(kek, Base64.getDecoder().decode(slot.wrappedKey), AAD_KEY_SLOT).also { kek.fill(0) }
    }

    private fun deriveKey(secret: ByteArray, salt: ByteArray, params: KdfParams): ByteArray {
        require(params.alg == "argon2id") { "Unsupported key derivation ${params.alg}" }
        val generator = Argon2BytesGenerator()
        generator.init(
            Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(params.memoryKiB)
                .withIterations(params.iterations)
                .withParallelism(params.parallelism)
                .withSalt(salt)
                .build()
        )
        val out = ByteArray(KEY_BYTES)
        generator.generateBytes(secret, out)
        return out
    }

    private fun gcmEncrypt(key: ByteArray, plain: ByteArray, aad: ByteArray): ByteArray {
        val iv = randomBytes(IV_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad)
        return iv + cipher.doFinal(plain)
    }

    private fun gcmDecrypt(key: ByteArray, ivAndCiphertext: ByteArray, aad: ByteArray): ByteArray? {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, ivAndCiphertext, 0, IV_BYTES)
        )
        cipher.updateAAD(aad)
        return try {
            cipher.doFinal(ivAndCiphertext, IV_BYTES, ivAndCiphertext.size - IV_BYTES)
        } catch (_: AEADBadTagException) {
            null
        }
    }

    private fun randomBytes(n: Int) = ByteArray(n).also { random.nextBytes(it) }

    /** NFKC so the same password typed on different keyboards gives the same bytes. */
    private fun passwordBytes(password: String) =
        Normalizer.normalize(password, Normalizer.Form.NFKC).toByteArray(Charsets.UTF_8)
}

/** Human-friendly recovery key: 24 characters (~117 bits) shown as six groups of four. */
object RecoveryKey {
    // No 0/O, 1/I/L or U, so it's hard to misread when typed back in
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789"
    private const val LENGTH = 24

    fun generate(random: SecureRandom): String =
        (1..LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }
            .chunked(4) { it.joinToString("") }
            .joinToString("-")

    /** Accepts any case, spaces and dashes. */
    fun normalize(input: String): String =
        input.uppercase().filter { it.isLetterOrDigit() }

    fun isWellFormed(input: String): Boolean {
        val n = normalize(input)
        return n.length == LENGTH && n.all { it in ALPHABET }
    }

    fun bytes(input: String): ByteArray = normalize(input).toByteArray(Charsets.US_ASCII)
}
