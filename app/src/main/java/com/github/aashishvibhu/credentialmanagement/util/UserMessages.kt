package com.github.aashishvibhu.credentialmanagement.util

import com.github.aashishvibhu.credentialmanagement.security.NoVaultException
import com.github.aashishvibhu.credentialmanagement.security.TooManyAttemptsException
import com.github.aashishvibhu.credentialmanagement.security.VaultLockedException
import com.github.aashishvibhu.credentialmanagement.security.VaultUnreadableException
import com.github.aashishvibhu.credentialmanagement.security.WrongPasswordException
import com.github.aashishvibhu.credentialmanagement.security.WrongRecoveryKeyException
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAuthIOException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import java.io.InterruptedIOException
import java.net.UnknownHostException

/**
 * A sentence the user can act on. Unknown errors keep their class name so a screenshot of the
 * message is enough to diagnose them.
 */
fun Throwable.userMessage(fallback: String): String = when (this) {
    is WrongPasswordException,
    is WrongRecoveryKeyException,
    is TooManyAttemptsException,
    is VaultLockedException,
    is VaultUnreadableException,
    is NoVaultException -> message.orEmpty()
    is UserRecoverableAuthIOException ->
        "Google Drive needs your permission again. Sign out, then sign in."
    is GoogleAuthIOException ->
        "Google didn't recognise this app. Check its OAuth client and SHA-1 in Google Cloud."
    is GoogleJsonResponseException -> when (statusCode) {
        401 -> "Your Google sign-in expired. Sign out, then sign in."
        403 -> "Google Drive refused access. Check that the Drive API is enabled for this app."
        else -> "Google Drive returned an error ($statusCode). Try again."
    }
    is UnknownHostException, is InterruptedIOException ->
        "Couldn't reach Google Drive. Check your connection."
    is IllegalStateException -> message ?: "$fallback (${javaClass.simpleName})"
    else -> "$fallback (${javaClass.simpleName})"
}
