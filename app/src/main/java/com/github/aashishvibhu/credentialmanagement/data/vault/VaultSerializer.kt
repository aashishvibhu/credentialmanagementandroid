package com.github.aashishvibhu.credentialmanagement.data.vault

import com.github.aashishvibhu.credentialmanagement.domain.model.Credential
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultSerializer @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun serialize(credentials: List<Credential>): String =
        json.encodeToString(ListSerializer(Credential.serializer()), credentials)

    fun deserialize(jsonString: String): List<Credential> =
        json.decodeFromString(ListSerializer(Credential.serializer()), jsonString)

    fun serializeOne(credential: Credential): String =
        json.encodeToString(Credential.serializer(), credential)

    fun deserializeOne(jsonString: String): Credential =
        json.decodeFromString(Credential.serializer(), jsonString)
}
