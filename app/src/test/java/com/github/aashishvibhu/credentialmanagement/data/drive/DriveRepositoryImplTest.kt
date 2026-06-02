package com.github.aashishvibhu.credentialmanagement.data.drive

import com.github.aashishvibhu.credentialmanagement.data.auth.AuthRepository
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.util.DateTime
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

class DriveRepositoryImplTest {

    private val authRepository = mockk<AuthRepository>()
    private val driveServiceFactory = mockk<DriveServiceFactory>()
    private val drive = mockk<Drive>(relaxed = true)
    private val driveFiles = mockk<Drive.Files>(relaxed = true)

    private lateinit var repository: DriveRepositoryImpl

    @Before
    fun setUp() {
        val account = mockk<GoogleSignInAccount>()
        every { authRepository.getSignedInAccount() } returns account
        every { driveServiceFactory.build(account) } returns drive
        every { drive.files() } returns driveFiles
        repository = DriveRepositoryImpl(authRepository, driveServiceFactory)
    }

    // ── uploadVault ───────────────────────────────────────────────────────────

    @Test
    fun `uploadVault creates new file when none exists`() = runTest {
        stubFileList(emptyList())
        val createRequest = mockk<Drive.Files.Create>(relaxed = true)
        every { driveFiles.create(any<com.google.api.services.drive.model.File>(), any()) } returns createRequest
        every { createRequest.execute() } returns mockk(relaxed = true)

        repository.uploadVault("encrypted-content")

        verify { driveFiles.create(any<com.google.api.services.drive.model.File>(), any()) }
    }

    @Test
    fun `uploadVault updates existing file when vault already exists`() = runTest {
        stubFileList(listOf(driveFile("file-id-123")))
        val updateRequest = mockk<Drive.Files.Update>(relaxed = true)
        every { driveFiles.update(eq("file-id-123"), any(), any()) } returns updateRequest
        every { updateRequest.execute() } returns File()

        repository.uploadVault("new-content")

        verify { driveFiles.update(eq("file-id-123"), any(), any()) }
    }

    // ── downloadVault ─────────────────────────────────────────────────────────

    @Test
    fun `downloadVault returns null when no vault file exists`() = runTest {
        stubFileList(emptyList())

        val result = repository.downloadVault()

        assertNull(result)
    }

    @Test
    fun `downloadVault returns file content when vault exists`() = runTest {
        stubFileList(listOf(driveFile("file-id-456")))
        val getRequest = mockk<Drive.Files.Get>(relaxed = true)
        every { driveFiles.get("file-id-456") } returns getRequest
        every { getRequest.executeMediaAsInputStream() } returns
            ByteArrayInputStream("vault-data".toByteArray(Charsets.UTF_8))

        val result = repository.downloadVault()

        assertEquals("vault-data", result)
    }

    // ── getVaultModifiedTime ──────────────────────────────────────────────────

    @Test
    fun `getVaultModifiedTime returns null when no vault file exists`() = runTest {
        stubFileList(emptyList())

        assertNull(repository.getVaultModifiedTime())
    }

    @Test
    fun `getVaultModifiedTime returns epoch ms from file metadata`() = runTest {
        val file = driveFile("file-id-789").apply { modifiedTime = DateTime(1_700_000_000_000L) }
        stubFileList(listOf(file))

        assertEquals(1_700_000_000_000L, repository.getVaultModifiedTime())
    }

    // ── deleteVault ───────────────────────────────────────────────────────────

    @Test
    fun `deleteVault does nothing when no vault file exists`() = runTest {
        stubFileList(emptyList())
        repository.deleteVault()
        verify(exactly = 0) { driveFiles.delete(any()) }
    }

    @Test
    fun `deleteVault deletes file when vault exists`() = runTest {
        stubFileList(listOf(driveFile("del-id")))
        val deleteRequest = mockk<Drive.Files.Delete>(relaxed = true)
        every { driveFiles.delete("del-id") } returns deleteRequest

        repository.deleteVault()

        verify { driveFiles.delete("del-id") }
    }

    // ── auth guard ────────────────────────────────────────────────────────────

    @Test(expected = IllegalStateException::class)
    fun `uploadVault throws when not signed in`() = runTest {
        every { authRepository.getSignedInAccount() } returns null
        repository.uploadVault("content")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun driveFile(id: String) = File().apply { this.id = id }

    private fun stubFileList(files: List<File>) {
        val listRequest = mockk<Drive.Files.List>(relaxed = true)
        every { driveFiles.list() } returns listRequest
        every { listRequest.setSpaces(any()) } returns listRequest
        every { listRequest.setQ(any()) } returns listRequest
        every { listRequest.setFields(any()) } returns listRequest
        every { listRequest.execute() } returns FileList().apply { setFiles(files) }
    }
}
