package com.beadpay.wrapper.di

import com.beadpay.wrapper.BuildConfig
import com.beadpay.wrapper.network.ApiKeyInterceptor
import com.beadpay.wrapper.network.PaymentsApi
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val PAYMENTS_BASE_URL = "https://api.test.devs.beadpay.io/"

    /* ---------- Core dependencies ---------- */

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().build()

    /**
     * OkHttp instance used by the payments Retrofit graph.
     *
     *  • Authenticates with the terminal API key (`ApiKeyInterceptor`)
     *  • Always sends `Accept: application/json`
     *  • Logs full request/response bodies in debug builds
     */
    @Provides
    @Singleton
    fun provideOkHttp(
        apiKeyInterceptor: ApiKeyInterceptor
    ): OkHttpClient {

        // 1) Global Accept header
        val acceptInterceptor = Interceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("Accept", "application/json")
                    .build()
            )
        }

        // 2) Debug logging
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG)
                HttpLoggingInterceptor.Level.BODY
            else
                HttpLoggingInterceptor.Level.NONE
        }

        // Interceptor order matters:
        // 1. Accept header
        // 2. X-Api-Key
        // 3. Logging (so it sees the final headers)
        return OkHttpClient.Builder()
            .addInterceptor(acceptInterceptor)
            .addInterceptor(apiKeyInterceptor)
            .addInterceptor(logging)
            .build()
    }

    /* ---------- Retrofit ---------- */

    @Provides
    @Singleton
    fun providePaymentsRetrofit(
        client: OkHttpClient,
        moshi: Moshi
    ): Retrofit = Retrofit.Builder()
        .baseUrl(PAYMENTS_BASE_URL)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    /* ---------- API interfaces ---------- */

    @Provides
    @Singleton
    fun providePaymentsApi(retrofit: Retrofit): PaymentsApi =
        retrofit.create(PaymentsApi::class.java)
}
