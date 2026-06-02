package com.github.aashishvibhu.credentialmanagement.data.auth

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class SignedIn(val email: String) : AuthState()
    data class Error(val message: String) : AuthState()
}
