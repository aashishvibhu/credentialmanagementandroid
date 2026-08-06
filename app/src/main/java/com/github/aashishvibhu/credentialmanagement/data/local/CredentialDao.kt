package com.github.aashishvibhu.credentialmanagement.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CredentialDao {

    @Query("SELECT * FROM credentials ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<CredentialEntity>>

    @Query("SELECT * FROM credentials ORDER BY updatedAt DESC")
    suspend fun getAllSuspend(): List<CredentialEntity>

    @Query("SELECT * FROM credentials WHERE id = :id")
    suspend fun getById(id: String): CredentialEntity?

    @Upsert
    suspend fun upsert(entity: CredentialEntity)

    @Query("DELETE FROM credentials WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM credentials")
    suspend fun deleteAll()
}
