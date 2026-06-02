package com.github.aashishvibhu.credentialmanagement.data.auth

import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authClient: GoogleAuthClient
) : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override fun getSignInIntent(): Intent = authClient.getSignInIntent()

    override suspend fun handleSignInResult(data: Intent?) {
        try {
            val account = GoogleSignIn
                .getSignedInAccountFromIntent(data)
                .getResult(ApiException::class.java)
            _authState.value = AuthState.SignedIn(account.email ?: "")
        } catch (e: ApiException) {
            _authState.value = AuthState.Error("Sign-in failed (code ${e.statusCode})")
        }
    }

    override suspend fun silentSignIn() {
        _authState.value = AuthState.Loading

        // Check if a valid account is already cached with the required scope
        val cached = authClient.getLastSignedInAccount()
        if (cached != null && authClient.hasRequiredScopes(cached)) {
            _authState.value = AuthState.SignedIn(cached.email ?: "")
            return
        }

        // Attempt a silent token refresh
        val refreshed = authClient.silentSignIn()
        _authState.value = if (refreshed != null && authClient.hasRequiredScopes(refreshed)) {
            AuthState.SignedIn(refreshed.email ?: "")
        } else {
            AuthState.Idle
        }
    }

    override suspend fun signOut() {
        authClient.signOut()
        _authState.value = AuthState.Idle
    }

    override fun getSignedInAccount(): GoogleSignInAccount? =
        authClient.getLastSignedInAccount()
}
