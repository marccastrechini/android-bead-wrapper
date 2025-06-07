package com.beadpay.wrapper.di

import com.beadpay.wrapper.network.PaymentsApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun providePaymentsApi(retrofit: Retrofit): PaymentsApi =
        retrofit.create(PaymentsApi::class.java)
}