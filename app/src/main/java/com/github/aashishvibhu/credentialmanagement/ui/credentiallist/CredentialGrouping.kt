package com.github.aashishvibhu.credentialmanagement.ui.credentiallist

import com.github.aashishvibhu.credentialmanagement.domain.model.Credential

/**
 * One row in the vault list: either a single login, or several logins that share the same
 * service name (e.g. three Google accounts) shown under one expandable row.
 */
sealed interface VaultEntry {
    val key: String
    val name: String

    data class Single(val credential: Credential) : VaultEntry {
        override val key get() = credential.id
        override val name get() = credential.title
    }

    data class Group(
        override val key: String,
        override val name: String,
        val accounts: List<Credential>
    ) : VaultEntry
}

/** Normalised service name: "  Google ", "google" and "GOOGLE" all group together. */
fun serviceKey(title: String): String = title.trim().lowercase().replace(Regex("\\s+"), " ")

/**
 * Groups credentials by service name. Services with one login stay a [VaultEntry.Single];
 * services with two or more become a [VaultEntry.Group]. Entries are sorted alphabetically,
 * accounts within a group by username.
 */
fun groupCredentials(credentials: List<Credential>): List<VaultEntry> =
    credentials
        .groupBy { serviceKey(it.title) }
        .map { (key, items) ->
            if (items.size == 1) {
                VaultEntry.Single(items.first())
            } else {
                VaultEntry.Group(
                    key = "group:$key",
                    // Most recently edited title wins as the display name
                    name = items.maxBy { it.updatedAt }.title.trim(),
                    accounts = items.sortedBy { it.username.lowercase() }
                )
            }
        }
        .sortedBy { it.name.lowercase() }
