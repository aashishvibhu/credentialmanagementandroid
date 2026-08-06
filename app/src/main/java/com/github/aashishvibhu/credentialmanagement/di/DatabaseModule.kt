package com.github.aashishvibhu.credentialmanagement.di

import android.content.Context
import androidx.room.Room
import com.github.aashishvibhu.credentialmanagement.data.local.CredentialDao
import com.github.aashishvibhu.credentialmanagement.data.local.CredentialDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideCredentialDatabase(
        @ApplicationContext context: Context
    ): CredentialDatabase = Room.databaseBuilder(
        context,
        CredentialDatabase::class.java,
        "credential_db"
    )
        .fallbackToDestructiveMigration()
        .build()

    @Provides
    fun provideCredentialDao(database: CredentialDatabase): CredentialDao =
        database.credentialDao()
}
