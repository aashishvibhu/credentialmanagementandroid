package com.github.aashishvibhu.credentialmanagement.di

import android.content.Context
import com.github.aashishvibhu.credentialmanagement.data.auth.AuthRepository
import com.github.aashishvibhu.credentialmanagement.data.auth.AuthRepositoryImpl
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    companion object {

        @Provides
        @Singleton
        fun provideGoogleSignInOptions(): GoogleSignInOptions =
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
                .build()

        @Provides
        @Singleton
        fun provideGoogleSignInClient(
            @ApplicationContext context: Context,
            options: GoogleSignInOptions
        ): GoogleSignInClient = GoogleSignIn.getClient(context, options)
    }
}
