package com.github.aashishvibhu.credentialmanagement.ui.auth

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.aashishvibhu.credentialmanagement.data.auth.AuthRepository
import com.github.aashishvibhu.credentialmanagement.data.auth.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState

    init {
        silentSignIn()
    }

    private fun silentSignIn() {
        viewModelScope.launch { authRepository.silentSignIn() }
    }

    fun getSignInIntent(): Intent = authRepository.getSignInIntent()

    fun handleSignInResult(data: Intent?) {
        viewModelScope.launch { authRepository.handleSignInResult(data) }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }
}
