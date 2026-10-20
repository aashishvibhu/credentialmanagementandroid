package com.github.aashishvibhu.credentialmanagement.domain.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import java.util.UUID

// id and timestamps have non-constant defaults: always encode them, otherwise a value that happens
// to equal a freshly evaluated default is dropped and replaced with a new one when read back.
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Credential(
    @EncodeDefault val id: String = UUID.randomUUID().toString(),
    val title: String,
    val username: String,
    val password: String,
    val url: String = "",
    val notes: String = "",
    @EncodeDefault val createdAt: Long = System.currentTimeMillis(),
    @EncodeDefault val updatedAt: Long = System.currentTimeMillis()
)
