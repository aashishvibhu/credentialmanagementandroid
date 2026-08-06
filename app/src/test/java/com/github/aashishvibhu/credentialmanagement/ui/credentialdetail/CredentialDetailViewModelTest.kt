package com.github.aashishvibhu.credentialmanagement.ui.credentialdetail

import androidx.lifecycle.SavedStateHandle
import com.github.aashishvibhu.credentialmanagement.data.local.LocalCredentialRepository
import com.github.aashishvibhu.credentialmanagement.domain.model.Credential
import com.github.aashishvibhu.credentialmanagement.sync.VaultSyncManager
import com.github.aashishvibhu.credentialmanagement.util.ConnectivityChecker
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CredentialDetailViewModelTest {

    private val localRepo          = mockk<LocalCredentialRepository>()
    private val vaultSyncManager   = mockk<VaultSyncManager>()
    private val connectivityChecker = mockk<ConnectivityChecker>()
    private val testDispatcher     = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { connectivityChecker.isOnline() } returns true
        coJustRun { localRepo.save(any()) }
        coJustRun { vaultSyncManager.pushFullVault(any()) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm(id: String = "new") = CredentialDetailViewModel(
        SavedStateHandle(mapOf("credentialId" to id)),
        localRepo,
        vaultSyncManager,
        connectivityChecker
    )

    // ── canSave ───────────────────────────────────────────────────────────────

    @Test
    fun `canSave is false when title and username are blank`() {
        val vm = newVm()
        assertFalse(vm.canSave.value)
    }

    @Test
    fun `canSave is false when only title is set`() {
        val vm = newVm()
        vm.onTitleChange("GitHub")
        assertFalse(vm.canSave.value)
    }

    @Test
    fun `canSave becomes true when both title and username are set`() = runTest {
        val vm = newVm()
        val job = launch(Dispatchers.Main) { vm.canSave.collect {} }
        vm.onTitleChange("GitHub")
        vm.onUsernameChange("user@example.com")
        assertTrue(vm.canSave.value)
        job.cancel()
    }

    // ── save ─────────────────────────────────────────────────────────────────

    @Test
    fun `save pulls latest vault and uploads merged result`() = runTest {
        val existingRemote = Credential(
            id = "existing-1", title = "Old", username = "old",
            password = "old", updatedAt = 1000L
        )
        coEvery { vaultSyncManager.pullLatestCredentials() } returns listOf(existingRemote)
        coJustRun { vaultSyncManager.pushFullVault(any()) }

        val vm = newVm()
        val job = launch(Dispatchers.Main) { vm.canSave.collect {} }
        vm.onTitleChange("GitHub")
        vm.onUsernameChange("user")
        vm.onPasswordChange("s3cr3t!")

        vm.save()

        coVerify { vaultSyncManager.pullLatestCredentials() }
        coVerify {
            vaultSyncManager.pushFullVault(
                match { list -> list.size == 2 }
            )
        }
        coVerify { localRepo.save(match { it.title == "GitHub" }) }
        job.cancel()
    }

    @Test
    fun `save does nothing when canSave is false`() = runTest {
        val vm = newVm()
        vm.save()
        coVerify(exactly = 0) { localRepo.save(any()) }
        coVerify(exactly = 0) { vaultSyncManager.pushFullVault(any()) }
    }

    @Test
    fun `save emits navEvent after saving`() = runTest {
        coEvery { vaultSyncManager.pullLatestCredentials() } returns emptyList()
        val vm = newVm()
        val canSaveJob = launch(Dispatchers.Main) { vm.canSave.collect {} }
        vm.onTitleChange("T")
        vm.onUsernameChange("U")

        var eventFired = false
        val navJob = launch(Dispatchers.Main) { vm.navEvent.collect { eventFired = true } }

        vm.save()
        assertTrue(eventFired)
        canSaveJob.cancel()
        navJob.cancel()
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    fun `delete pulls vault removes credential and uploads reduced vault`() = runTest {
        val existingRemote = Credential(
            id = "id-1", title = "T", username = "U", password = "P", updatedAt = 1000L
        )
        coEvery { localRepo.getById("id-1") } returns null
        coEvery { vaultSyncManager.pullLatestCredentials() } returns listOf(existingRemote)
        coJustRun { vaultSyncManager.pushFullVault(any()) }
        coJustRun { localRepo.delete("id-1") }
        val vm = newVm("id-1")

        vm.delete()

        coVerify { vaultSyncManager.pullLatestCredentials() }
        coVerify { vaultSyncManager.pushFullVault(match { it.isEmpty() }) }
        coVerify { localRepo.delete("id-1") }
    }

    @Test
    fun `delete does nothing for a new credential`() = runTest {
        val vm = newVm()
        vm.delete()
        coVerify(exactly = 0) { localRepo.delete(any()) }
        coVerify(exactly = 0) { vaultSyncManager.pushFullVault(any()) }
    }

    // ── load ─────────────────────────────────────────────────────────────────

    @Test
    fun `loading an existing credential populates all fields`() = runTest {
        val existing = Credential(
            id = "id-1", title = "GitHub", username = "octocat",
            password = "gh_token", url = "https://github.com", notes = "personal"
        )
        coEvery { localRepo.getById("id-1") } returns existing
        val vm = newVm("id-1")

        assertEquals("GitHub",               vm.title.value)
        assertEquals("octocat",              vm.username.value)
        assertEquals("gh_token",             vm.password.value)
        assertEquals("https://github.com",   vm.url.value)
        assertEquals("personal",             vm.notes.value)
        assertFalse(vm.isLoading.value)
    }

    @Test
    fun `loading a missing id leaves fields blank`() = runTest {
        coEvery { localRepo.getById("missing") } returns null
        val vm = newVm("missing")

        assertEquals("", vm.title.value)
        assertEquals("", vm.username.value)
        assertFalse(vm.isLoading.value)
    }

    // ── generatePassword ──────────────────────────────────────────────────────

    @Test
    fun `generatePassword produces a 16-character string`() {
        val vm = newVm()
        vm.generatePassword()
        assertEquals(16, vm.password.value.length)
    }

    @Test
    fun `generatePassword produces distinct values on successive calls`() {
        val vm = newVm()
        vm.generatePassword()
        val first = vm.password.value
        vm.generatePassword()
        assertNotEquals(first, vm.password.value)
    }
}
    }

    // ── passwordStrength ──────────────────────────────────────────────────────

    @Test
    fun `passwordStrength is zero for an empty password`() {
        val vm = newVm()
        assertEquals(0f, vm.passwordStrength.value)
    }

    @Test
    fun `passwordStrength is above 0_5 for a strong password`() = runTest {
        val vm = newVm()
        val job = launch(Dispatchers.Main) { vm.passwordStrength.collect {} }
        vm.onPasswordChange("Str0ng!Pass#99")
        assertTrue(vm.passwordStrength.value > 0.5f)
        job.cancel()
    }

    @Test
    fun `passwordStrength is low for a short simple password`() {
        val vm = newVm()
        vm.onPasswordChange("abc")
        assertTrue(vm.passwordStrength.value < 0.5f)
    }
}
