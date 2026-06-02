package com.github.aashishvibhu.credentialmanagement.data.drive

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveServiceFactory @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun build(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential
            .usingOAuth2(context, listOf(DriveScopes.DRIVE_APPDATA))
            .apply { selectedAccount = account.account }

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("CredentialManager")
            .build()
    }
}
