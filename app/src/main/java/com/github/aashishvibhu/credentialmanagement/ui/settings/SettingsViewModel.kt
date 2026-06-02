package com.github.aashishvibhu.credentialmanagement.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.aashishvibhu.credentialmanagement.data.auth.AuthRepository
import com.github.aashishvibhu.credentialmanagement.data.local.LocalCredentialRepository
import com.github.aashishvibhu.credentialmanagement.sync.SyncManager
import com.github.aashishvibhu.credentialmanagement.sync.SyncScheduler
import com.github.aashishvibhu.credentialmanagement.sync.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val localRepo: LocalCredentialRepository,
    private val syncManager: SyncManager,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    val syncState: StateFlow<SyncState> = syncManager.syncState

    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()

    val signedInEmail: String get() = authRepository.getSignedInAccount()?.email ?: ""

    init {
        viewModelScope.launch {
            syncManager.syncState.collect { state ->
                if (state is SyncState.Success) _lastSyncTime.value = System.currentTimeMillis()
            }
        }
    }

    fun syncNow() = syncScheduler.scheduleImmediateSync()

    fun signOut() {
        viewModelScope.launch {
            syncScheduler.cancelAll()
            localRepo.replaceAll(emptyList())
            authRepository.signOut()
        }
    }
}
