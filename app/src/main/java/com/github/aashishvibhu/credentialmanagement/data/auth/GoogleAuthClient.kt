package com.github.aashishvibhu.credentialmanagement.data.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleAuthClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val signInClient: GoogleSignInClient
) {
    fun getSignInIntent(): Intent = signInClient.signInIntent

    fun getLastSignedInAccount(): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    fun hasRequiredScopes(account: GoogleSignInAccount): Boolean =
        GoogleSignIn.hasPermissions(account, com.google.android.gms.common.api.Scope(DriveScopes.DRIVE_APPDATA))

    suspend fun silentSignIn(): GoogleSignInAccount? = withContext(Dispatchers.IO) {
        try {
            signInClient.silentSignIn().await()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        signInClient.signOut().await()
    }
}
