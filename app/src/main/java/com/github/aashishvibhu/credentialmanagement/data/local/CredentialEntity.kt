package com.github.aashishvibhu.credentialmanagement.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "credentials")
data class CredentialEntity(
    @PrimaryKey val id: String,
    val encryptedBlob: String,  // AES-GCM encrypted JSON of a single Credential
    val updatedAt: Long
)
