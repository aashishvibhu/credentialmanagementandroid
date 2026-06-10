package com.github.aashishvibhu.credentialmanagement.ui.biometric

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class LockViewModel @Inject constructor(
    private val lockStateManager: LockStateManager
) : ViewModel() {

    val isLocked: StateFlow<Boolean> = lockStateManager.isLocked

    fun unlock() = lockStateManager.unlock()
}
