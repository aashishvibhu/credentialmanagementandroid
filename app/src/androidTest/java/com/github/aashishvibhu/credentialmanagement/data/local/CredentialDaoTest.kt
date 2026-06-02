package com.github.aashishvibhu.credentialmanagement.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CredentialDaoTest {

    private lateinit var database: CredentialDatabase
    private lateinit var dao: CredentialDao

    @Before
    fun createDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CredentialDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.credentialDao()
    }

    @After
    fun closeDb() = database.close()

    @Test
    fun upsert_then_getAll_returns_entity() = runTest {
        val entity = CredentialEntity("1", "blob1", 1000L, true)
        dao.upsert(entity)
        val result = dao.getAll().first()
        assertEquals(1, result.size)
        assertEquals(entity, result[0])
    }

    @Test
    fun upsert_existing_id_updates_in_place() = runTest {
        dao.upsert(CredentialEntity("1", "old", 1000L, true))
        dao.upsert(CredentialEntity("1", "new", 2000L, false))
        val result = dao.getAll().first()
        assertEquals(1, result.size)
        assertEquals("new", result[0].encryptedBlob)
        assertEquals(false, result[0].isDirty)
    }

    @Test
    fun getById_returns_correct_entity() = runTest {
        dao.upsert(CredentialEntity("a", "blob_a", 1000L, false))
        dao.upsert(CredentialEntity("b", "blob_b", 2000L, false))
        val result = dao.getById("a")
        assertEquals("blob_a", result?.encryptedBlob)
    }

    @Test
    fun getById_returns_null_when_not_found() = runTest {
        assertNull(dao.getById("missing"))
    }

    @Test
    fun getDirty_returns_only_dirty_entities() = runTest {
        dao.upsert(CredentialEntity("1", "b1", 1000L, true))
        dao.upsert(CredentialEntity("2", "b2", 2000L, false))
        dao.upsert(CredentialEntity("3", "b3", 3000L, true))
        val dirty = dao.getDirty()
        assertEquals(2, dirty.size)
        assertTrue(dirty.all { it.isDirty })
    }

    @Test
    fun markAllClean_clears_dirty_flag() = runTest {
        dao.upsert(CredentialEntity("1", "b1", 1000L, true))
        dao.upsert(CredentialEntity("2", "b2", 2000L, true))
        dao.markAllClean()
        assertTrue(dao.getDirty().isEmpty())
    }

    @Test
    fun delete_removes_entity() = runTest {
        dao.upsert(CredentialEntity("1", "blob", 1000L, false))
        dao.delete("1")
        assertTrue(dao.getAll().first().isEmpty())
    }

    @Test
    fun deleteAll_clears_table() = runTest {
        dao.upsert(CredentialEntity("1", "b1", 1000L, false))
        dao.upsert(CredentialEntity("2", "b2", 2000L, false))
        dao.deleteAll()
        assertTrue(dao.getAllSuspend().isEmpty())
    }

    @Test
    fun getAll_orders_by_updatedAt_desc() = runTest {
        dao.upsert(CredentialEntity("old", "b1", 1000L, false))
        dao.upsert(CredentialEntity("new", "b2", 9000L, false))
        val result = dao.getAll().first()
        assertEquals("new", result[0].id)
        assertEquals("old", result[1].id)
    }
}
