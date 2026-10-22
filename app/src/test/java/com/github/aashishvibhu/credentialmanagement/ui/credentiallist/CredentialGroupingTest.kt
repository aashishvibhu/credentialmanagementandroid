package com.github.aashishvibhu.credentialmanagement.ui.credentiallist

import com.github.aashishvibhu.credentialmanagement.domain.model.Credential
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialGroupingTest {

    private fun cred(title: String, user: String, updatedAt: Long = 0L) =
        Credential(title = title, username = user, password = "p", updatedAt = updatedAt)

    @Test
    fun `single logins stay single rows`() {
        val result = groupCredentials(listOf(cred("Netflix", "a"), cred("Amazon", "b")))
        assertEquals(2, result.size)
        assertTrue(result.all { it is VaultEntry.Single })
    }

    @Test
    fun `same service name groups case and whitespace insensitively`() {
        val result = groupCredentials(
            listOf(cred("Google", "work@x.com"), cred(" google ", "me@gmail.com"), cred("GOOGLE", "old@gmail.com"))
        )
        val group = result.single() as VaultEntry.Group
        assertEquals(listOf("me@gmail.com", "old@gmail.com", "work@x.com"), group.accounts.map { it.username })
    }

    @Test
    fun `group uses most recently edited title as display name`() {
        val result = groupCredentials(listOf(cred("google", "a", updatedAt = 1), cred("Google", "b", updatedAt = 5)))
        assertEquals("Google", (result.single() as VaultEntry.Group).name)
    }

    @Test
    fun `groups and singles are sorted together alphabetically`() {
        val result = groupCredentials(
            listOf(cred("Zoom", "a"), cred("google", "a"), cred("Google", "b", updatedAt = 5), cred("Amazon", "a"))
        )
        assertEquals(listOf("Amazon", "Google", "Zoom"), result.map { it.name })
    }

    @Test
    fun `serviceKey collapses inner whitespace`() {
        assertEquals("hdfc bank", serviceKey("  HDFC   Bank "))
    }
}
