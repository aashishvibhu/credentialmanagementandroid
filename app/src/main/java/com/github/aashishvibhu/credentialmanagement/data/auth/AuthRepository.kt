package com.github.aashishvibhu.credentialmanagement.data.auth

import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val authState: StateFlow<AuthState>
    fun getSignInIntent(): Intent
    suspend fun handleSignInResult(data: Intent?)
    suspend fun silentSignIn()
    suspend fun signOut()
    fun getSignedInAccount(): GoogleSignInAccount?
}
