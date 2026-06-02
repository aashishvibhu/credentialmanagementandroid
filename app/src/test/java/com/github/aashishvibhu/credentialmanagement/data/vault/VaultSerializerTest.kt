package com.github.aashishvibhu.credentialmanagement.data.vault

import com.github.aashishvibhu.credentialmanagement.domain.model.Credential
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VaultSerializerTest {

    private lateinit var serializer: VaultSerializer

    @Before
    fun setUp() {
        serializer = VaultSerializer()
    }

    @Test
    fun `serialize then deserialize returns equal list`() {
        val credentials = listOf(
            Credential(id = "1", title = "GitHub", username = "user", password = "pass"),
            Credential(id = "2", title = "Gmail",  username = "me@gmail.com", password = "secret",
                url = "https://mail.google.com", notes = "work account")
        )
        val json = serializer.serialize(credentials)
        val result = serializer.deserialize(json)
        assertEquals(credentials, result)
    }

    @Test
    fun `empty list serializes and deserializes correctly`() {
        val json = serializer.serialize(emptyList())
        val result = serializer.deserialize(json)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `optional fields default correctly on deserialization`() {
        val minimal = """[{"id":"x","title":"T","username":"U","password":"P",
            "url":"","notes":"","createdAt":0,"updatedAt":0}]"""
        val result = serializer.deserialize(minimal)
        assertEquals(1, result.size)
        assertEquals("", result[0].url)
        assertEquals("", result[0].notes)
    }

    @Test
    fun `unknown keys in json are ignored`() {
        val withExtra = """[{"id":"1","title":"A","username":"B","password":"C",
            "url":"","notes":"","createdAt":0,"updatedAt":0,"unknownField":"x"}]"""
        val result = serializer.deserialize(withExtra)
        assertEquals(1, result.size)
    }
}
