package com.beadpay.wrapper.di

import android.content.Context
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideAuthService(
        @ApplicationContext ctx: Context
    ) = AuthorizationService(ctx)

    @Provides
    @Singleton
    fun provideAuthConfig(): AuthorizationServiceConfiguration =
        AuthorizationServiceConfiguration(
            /* auth endpoint  */ android.net.Uri.parse("https://identity.beadpay.io/realms/nonprod/protocol/openid-connect/auth"),
            /* token endpoint */ android.net.Uri.parse("https://identity.beadpay.io/realms/nonprod/protocol/openid-connect/token")
        )
}
