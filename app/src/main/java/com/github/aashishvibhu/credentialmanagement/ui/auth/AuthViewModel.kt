package com.github.aashishvibhu.credentialmanagement.ui.auth

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.aashishvibhu.credentialmanagement.data.auth.AuthRepository
import com.github.aashishvibhu.credentialmanagement.data.auth.AuthState
import com.github.aashishvibhu.credentialmanagement.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState

    init {
        silentSignIn()
        observeAuthForSync()
    }

    private fun silentSignIn() {
        viewModelScope.launch { authRepository.silentSignIn() }
    }

    /** Starts periodic background sync whenever the user is signed in. */
    private fun observeAuthForSync() {
        viewModelScope.launch {
            authRepository.authState.collect { state ->
                if (state is AuthState.SignedIn) {
                    syncScheduler.schedulePeriodicSync()
                    syncScheduler.scheduleImmediateSync()
                }
            }
        }
    }

    fun getSignInIntent(): Intent = authRepository.getSignInIntent()

    fun handleSignInResult(data: Intent?) {
        viewModelScope.launch { authRepository.handleSignInResult(data) }
    }

    fun signOut() {
        viewModelScope.launch {
            syncScheduler.cancelAll()
            authRepository.signOut()
        }
    }
}
