package com.beadpay.wrapper.di

import com.beadpay.wrapper.BuildConfig
import com.beadpay.wrapper.network.AuthInterceptor
import com.beadpay.wrapper.network.BeadApi
import com.beadpay.wrapper.network.PaymentsApi
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/* ── Qualifiers ────────────────────────────────────────────────── */

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PaymentsRetrofit

/* ── Module ────────────────────────────────────────────────────── */

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val AUTH_BASE_URL     = "https://identity.beadpay.io/realms/nonprod/"
    private const val PAYMENTS_BASE_URL = "https://api.test.devs.beadpay.io/"

    /* ---------- Core dependencies ---------- */

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().build()

    /**
     * Shared OkHttp instance used by *both* Retrofit graphs.
     *
     *  • Adds the bearer token when present (`AuthInterceptor`)
     *  • Always sends `Accept: application/json`
     *  • Logs full request/response bodies in debug builds
     *
     *  Note: AuthInterceptor depends on Lazy<AuthRepository> to break
     *  Dagger dependency cycle. No change is needed in this module.
     */
    @Provides
    @Singleton
    fun provideOkHttp(
        authInterceptor: AuthInterceptor
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
        // 2. Authorization
        // 3. Logging
        return OkHttpClient.Builder()
            .addInterceptor(acceptInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()
    }

    /* ---------- Retrofit instances ---------- */

    @AuthRetrofit
    @Provides
    @Singleton
    fun provideAuthRetrofit(
        client: OkHttpClient,
        moshi: Moshi
    ): Retrofit = Retrofit.Builder()
        .baseUrl(AUTH_BASE_URL)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    @PaymentsRetrofit
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
    fun provideBeadApi(@AuthRetrofit retrofit: Retrofit): BeadApi =
        retrofit.create(BeadApi::class.java)

    @Provides
    @Singleton
    fun providePaymentsApi(@PaymentsRetrofit retrofit: Retrofit): PaymentsApi =
        retrofit.create(PaymentsApi::class.java)
}
