package com.github.aashishvibhu.credentialmanagement.ui.credentialdetail

import androidx.lifecycle.SavedStateHandle
import com.github.aashishvibhu.credentialmanagement.data.local.LocalCredentialRepository
import com.github.aashishvibhu.credentialmanagement.domain.model.Credential
import com.github.aashishvibhu.credentialmanagement.sync.SyncScheduler
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CredentialDetailViewModelTest {

    private val localRepo     = mockk<LocalCredentialRepository>()
    private val syncScheduler = mockk<SyncScheduler>()
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coJustRun { syncScheduler.scheduleImmediateSync() }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm(id: String = "new") = CredentialDetailViewModel(
        SavedStateHandle(mapOf("credentialId" to id)),
        localRepo,
        syncScheduler
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
        // Launch on Main (UnconfinedTestDispatcher) so the WhileSubscribed upstream runs eagerly.
        val job = launch(Dispatchers.Main) { vm.canSave.collect {} }
        vm.onTitleChange("GitHub")
        vm.onUsernameChange("user@example.com")
        assertTrue(vm.canSave.value)
        job.cancel()
    }

    // ── save ─────────────────────────────────────────────────────────────────

    @Test
    fun `save stores credential as dirty and schedules sync`() = runTest {
        coJustRun { localRepo.save(any(), any()) }
        val vm = newVm()
        val job = launch(Dispatchers.Main) { vm.canSave.collect {} }
        vm.onTitleChange("GitHub")
        vm.onUsernameChange("user")
        vm.onPasswordChange("s3cr3t!")

        vm.save()

        coVerify {
            localRepo.save(
                match { it.title == "GitHub" && it.username == "user" && it.password == "s3cr3t!" },
                isDirty = true
            )
        }
        coVerify { syncScheduler.scheduleImmediateSync() }
        job.cancel()
    }

    @Test
    fun `save does nothing when canSave is false`() = runTest {
        val vm = newVm()
        vm.save()
        coVerify(exactly = 0) { localRepo.save(any(), any()) }
        coVerify(exactly = 0) { syncScheduler.scheduleImmediateSync() }
    }

    @Test
    fun `save emits navEvent after saving`() = runTest {
        coJustRun { localRepo.save(any(), any()) }
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
    fun `delete removes credential from repo and schedules sync`() = runTest {
        coEvery { localRepo.getById("id-1") } returns null
        coJustRun { localRepo.delete("id-1") }
        val vm = newVm("id-1")

        vm.delete()

        coVerify { localRepo.delete("id-1") }
        coVerify { syncScheduler.scheduleImmediateSync() }
    }

    @Test
    fun `delete does nothing for a new credential`() = runTest {
        val vm = newVm()
        vm.delete()
        coVerify(exactly = 0) { localRepo.delete(any()) }
        coVerify(exactly = 0) { syncScheduler.scheduleImmediateSync() }
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
