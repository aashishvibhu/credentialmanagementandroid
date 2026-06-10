package com.github.aashishvibhu.credentialmanagement.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Credential(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val username: String,
    val password: String,
    val url: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
