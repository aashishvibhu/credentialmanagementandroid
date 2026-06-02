package com.github.aashishvibhu.credentialmanagement.data.drive

import com.github.aashishvibhu.credentialmanagement.data.auth.AuthRepository
import com.github.aashishvibhu.credentialmanagement.domain.repository.DriveRepository
import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveRepositoryImpl @Inject constructor(
    private val authRepository: AuthRepository,
    private val driveServiceFactory: DriveServiceFactory
) : DriveRepository {

    companion object {
        private const val VAULT_FILE_NAME = "vault.enc"
        private const val SPACES = "appDataFolder"
        private const val CONTENT_TYPE = "application/octet-stream"
        private const val FILE_FIELDS = "files(id,modifiedTime)"
        private const val META_FIELDS = "modifiedTime"
    }

    override suspend fun uploadVault(encryptedContent: String): Unit = withContext(Dispatchers.IO) {
        val drive = requireDriveService()
        val content = ByteArrayContent(CONTENT_TYPE, encryptedContent.toByteArray(Charsets.UTF_8))
        val existing = findVaultFile(drive)
        if (existing == null) {
            val metadata = File().apply {
                name = VAULT_FILE_NAME
                parents = listOf(SPACES)
            }
            drive.files().create(metadata, content).execute()
        } else {
            drive.files().update(existing.id, File(), content).execute()
        }
    }

    override suspend fun downloadVault(): String? = withContext(Dispatchers.IO) {
        val drive = requireDriveService()
        val file = findVaultFile(drive) ?: return@withContext null
        drive.files().get(file.id)
            .executeMediaAsInputStream()
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
    }

    override suspend fun getVaultModifiedTime(): Long? = withContext(Dispatchers.IO) {
        val drive = requireDriveService()
        findVaultFile(drive)?.modifiedTime?.value
    }

    override suspend fun deleteVault(): Unit = withContext(Dispatchers.IO) {
        val drive = requireDriveService()
        findVaultFile(drive)?.let { drive.files().delete(it.id).execute() }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun requireDriveService(): Drive {
        val account = authRepository.getSignedInAccount()
            ?: throw IllegalStateException("Drive operations require a signed-in account")
        return driveServiceFactory.build(account)
    }

    private fun findVaultFile(drive: Drive): File? =
        drive.files().list()
            .setSpaces(SPACES)
            .setQ("name = '$VAULT_FILE_NAME' and trashed = false")
            .setFields(FILE_FIELDS)
            .execute()
            .files
            ?.firstOrNull()
}
