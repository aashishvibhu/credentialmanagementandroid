package com.github.aashishvibhu.credentialmanagement.data.vault

import android.content.Context
import com.github.aashishvibhu.credentialmanagement.security.VaultCrypto
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What this phone remembers about the vault after the master password was entered once:
 * the vault key (encrypted with the device-bound Keystore key, so it's useless off this phone),
 * the last-seen vault file, and unlock preferences. Excluded from Android backups.
 */
@Singleton
class VaultKeyStore @Inject constructor(
    @ApplicationContext context: Context,
    private val vaultCrypto: VaultCrypto
) {
    private val prefs = context.getSharedPreferences("vault_key_store", Context.MODE_PRIVATE)

    private companion object {
        const val KEY_VAULT_KEY      = "vault_key"
        const val KEY_KEY_ID         = "key_id"
        const val KEY_ENVELOPE       = "envelope"
        const val KEY_BIOMETRIC      = "biometric_unlock"
        const val KEY_FAILED         = "failed_attempts"
        const val KEY_LOCKOUT_UNTIL  = "lockout_until"
    }

    // ── Vault key ─────────────────────────────────────────────────────────────

    fun saveVaultKey(keyId: String, vaultKey: ByteArray) {
        val wrapped = vaultCrypto.encrypt(Base64.getEncoder().encodeToString(vaultKey))
        prefs.edit().putString(KEY_VAULT_KEY, wrapped).putString(KEY_KEY_ID, keyId).apply()
    }

    /** The vault key, or null if this phone hasn't been unlocked with the master password yet. */
    fun vaultKey(): ByteArray? {
        val wrapped = prefs.getString(KEY_VAULT_KEY, null) ?: return null
        // A Keystore key that was invalidated (e.g. lock screen removed) can't decrypt it
        return runCatching { Base64.getDecoder().decode(vaultCrypto.decrypt(wrapped)) }.getOrNull()
    }

    fun keyId(): String? = prefs.getString(KEY_KEY_ID, null)

    fun hasVaultKey(): Boolean = vaultKey() != null

    fun clearVaultKey() {
        prefs.edit().remove(KEY_VAULT_KEY).remove(KEY_KEY_ID).apply()
    }

    // ── Last-seen vault file ──────────────────────────────────────────────────

    /** Encrypted vault text as last downloaded or uploaded; lets the lock screen check the password offline. */
    var envelope: String?
        get() = prefs.getString(KEY_ENVELOPE, null)
        set(value) { prefs.edit().putString(KEY_ENVELOPE, value).apply() }

    // ── Unlock preferences ────────────────────────────────────────────────────

    var biometricUnlock: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC, true)
        set(value) { prefs.edit().putBoolean(KEY_BIOMETRIC, value).apply() }

    var failedAttempts: Int
        get() = prefs.getInt(KEY_FAILED, 0)
        set(value) { prefs.edit().putInt(KEY_FAILED, value).apply() }

    var lockoutUntil: Long
        get() = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        set(value) { prefs.edit().putLong(KEY_LOCKOUT_UNTIL, value).apply() }

    /** Forget everything (sign-out). The vault in Drive is untouched. */
    fun clear() {
        prefs.edit().clear().apply()
    }
}
