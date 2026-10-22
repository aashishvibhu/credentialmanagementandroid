package com.github.aashishvibhu.credentialmanagement.data.vault

import kotlinx.serialization.Serializable

/**
 * Format of `vault.enc` in Drive (version 2).
 *
 * A random vault key encrypts [payload]. The vault key itself is stored twice, wrapped by a key
 * derived from the master password and by one derived from the recovery key. Nothing in this
 * file is readable without one of those two secrets. Version 1 files were a bare Base64 string
 * encrypted with a device-bound Keystore key.
 *
 * No property has a default value: the vault serializer omits defaults, and every field here
 * must always be written.
 */
@Serializable
data class VaultEnvelope(
    val v: Int,
    /** Random id of the vault key; changes only when a brand-new vault is created. */
    val keyId: String,
    val kdf: KdfParams,
    val password: KeySlot,
    val recovery: KeySlot,
    /** Base64 of IV + AES-GCM ciphertext of the credentials JSON. */
    val payload: String
)

@Serializable
data class KdfParams(
    val alg: String,
    val memoryKiB: Int,
    val iterations: Int,
    val parallelism: Int
)

/** The vault key encrypted with a key derived from a secret and [salt] (both Base64). */
@Serializable
data class KeySlot(
    val salt: String,
    val wrappedKey: String
)
