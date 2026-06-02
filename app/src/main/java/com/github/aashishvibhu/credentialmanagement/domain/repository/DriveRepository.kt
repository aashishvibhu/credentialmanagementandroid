package com.github.aashishvibhu.credentialmanagement.domain.repository

interface DriveRepository {
    /** Upload (create or replace) the encrypted vault file in appDataFolder. */
    suspend fun uploadVault(encryptedContent: String)

    /** Download the vault file. Returns null if no file exists yet. */
    suspend fun downloadVault(): String?

    /** Epoch-ms of the last remote modification, or null if no file exists. */
    suspend fun getVaultModifiedTime(): Long?

    /** Permanently delete the vault file (used on sign-out). */
    suspend fun deleteVault()
}
