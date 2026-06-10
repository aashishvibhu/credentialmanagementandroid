package com.github.aashishvibhu.credentialmanagement.di

import com.github.aashishvibhu.credentialmanagement.data.drive.DriveRepositoryImpl
import com.github.aashishvibhu.credentialmanagement.domain.repository.DriveRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DriveModule {

    @Binds
    @Singleton
    abstract fun bindDriveRepository(impl: DriveRepositoryImpl): DriveRepository
}
