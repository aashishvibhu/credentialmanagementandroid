package com.github.aashishvibhu.credentialmanagement.security

/** Master password didn't unlock the vault key. */
class WrongPasswordException : Exception("That password isn't right.")

/** Recovery key didn't unlock the vault key. */
class WrongRecoveryKeyException : Exception("That recovery key isn't right.")

/** The vault exists but this phone doesn't have its key yet; the master password is needed. */
class VaultLockedException : Exception("Enter your master password to open your vault on this phone.")

/** A version 1 vault encrypted with a Keystore key this install doesn't have. */
class VaultUnreadableException :
    Exception("This vault was locked by an earlier install of the app and can't be opened.")

/** Unlocking is paused after repeated wrong passwords. */
class TooManyAttemptsException(val remainingMs: Long) :
    Exception("Too many tries. Wait ${(remainingMs + 999) / 1000} seconds and try again.")

/** Drive has no vault yet; one must be created with a master password first. */
class NoVaultException : Exception("Create a master password to set up your vault.")
