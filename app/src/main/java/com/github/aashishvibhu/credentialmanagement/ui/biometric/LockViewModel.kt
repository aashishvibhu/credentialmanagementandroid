package com.github.aashishvibhu.credentialmanagement.ui.biometric

import androidx.lifecycle.ViewModel
import com.github.aashishvibhu.credentialmanagement.data.vault.VaultKeyStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class LockViewModel @Inject constructor(
    private val lockStateManager: LockStateManager,
    private val keyStore: VaultKeyStore
) : ViewModel() {

    val isLocked: StateFlow<Boolean> = lockStateManager.isLocked

    /** Off in Settings → Security means the master password is always asked. */
    val biometricEnabled: Boolean get() = keyStore.biometricUnlock

    fun unlock() = lockStateManager.unlock()
}
