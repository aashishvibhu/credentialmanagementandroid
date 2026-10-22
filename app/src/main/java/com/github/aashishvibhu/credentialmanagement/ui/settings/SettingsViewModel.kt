package com.github.aashishvibhu.credentialmanagement.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.aashishvibhu.credentialmanagement.data.auth.AuthRepository
import com.github.aashishvibhu.credentialmanagement.data.local.LocalCredentialRepository
import com.github.aashishvibhu.credentialmanagement.data.vault.VaultAccess
import com.github.aashishvibhu.credentialmanagement.data.vault.VaultKeyStore
import com.github.aashishvibhu.credentialmanagement.sync.SyncScheduler
import com.github.aashishvibhu.credentialmanagement.sync.SyncState
import com.github.aashishvibhu.credentialmanagement.sync.VaultSyncManager
import com.github.aashishvibhu.credentialmanagement.ui.biometric.LockStateManager
import com.github.aashishvibhu.credentialmanagement.util.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val localRepo: LocalCredentialRepository,
    private val vaultSyncManager: VaultSyncManager,
    private val syncScheduler: SyncScheduler,
    private val vaultAccess: VaultAccess,
    private val keyStore: VaultKeyStore,
    private val lockStateManager: LockStateManager
) : ViewModel() {

    val syncState: StateFlow<SyncState> = vaultSyncManager.syncState

    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()

    private val _signedInEmail = MutableStateFlow(
        authRepository.getSignedInAccount()?.email ?: ""
    )
    val signedInEmail: StateFlow<String> = _signedInEmail.asStateFlow()

    private val _biometricUnlock = MutableStateFlow(keyStore.biometricUnlock)
    val biometricUnlock: StateFlow<Boolean> = _biometricUnlock.asStateFlow()

    // New recovery key: asks for the current password first
    private val _recoveryBusy  = MutableStateFlow(false)
    private val _recoveryError = MutableStateFlow<String?>(null)
    private val _recoveryReady = MutableSharedFlow<Unit>()
    val recoveryBusy:  StateFlow<Boolean> = _recoveryBusy.asStateFlow()
    val recoveryError: StateFlow<String?> = _recoveryError.asStateFlow()
    val recoveryReady: SharedFlow<Unit>   = _recoveryReady

    init {
        viewModelScope.launch {
            vaultSyncManager.syncState.collect { state ->
                if (state is SyncState.Success) _lastSyncTime.value = System.currentTimeMillis()
            }
        }
    }

    fun syncNow() = syncScheduler.scheduleImmediateSync()

    fun setBiometricUnlock(enabled: Boolean) {
        keyStore.biometricUnlock = enabled
        _biometricUnlock.value = enabled
    }

    fun newRecoveryKey(currentPassword: String) {
        if (_recoveryBusy.value) return
        viewModelScope.launch {
            _recoveryBusy.value = true
            _recoveryError.value = null
            try {
                vaultAccess.newRecoveryKey(currentPassword)
                _recoveryReady.emit(Unit)
            } catch (e: Exception) {
                _recoveryError.value = e.userMessage("Couldn't create a new recovery key")
            } finally {
                _recoveryBusy.value = false
            }
        }
    }

    fun clearRecoveryError() { _recoveryError.value = null }

    /** The nav graph sends locked sessions to the lock screen. */
    fun lockNow() = lockStateManager.lock()

    fun signOut() {
        viewModelScope.launch {
            syncScheduler.cancelAll()
            localRepo.clearCache()
            vaultAccess.forget()
            authRepository.signOut()
            lockStateManager.lock()
            _signedInEmail.value = ""
        }
    }
}
