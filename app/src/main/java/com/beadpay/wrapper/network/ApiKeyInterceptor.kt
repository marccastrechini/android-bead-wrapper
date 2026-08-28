package com.beadpay.wrapper.network

import com.beadpay.wrapper.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authenticates every request with the terminal API key.
 *
 * Bead's current standard is a static `X-Api-Key` header — see
 * https://developers.bead.xyz/payments/create-payment ("Preferred for new
 * integrations"). This replaces the previous OAuth password-grant flow, so
 * there is no token to cache, refresh, or invalidate on 401.
 */
@Singleton
class ApiKeyInterceptor @Inject constructor() : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header(HEADER_API_KEY, BuildConfig.BEAD_API_KEY)
            .build()

        return chain.proceed(request)
    }

    private companion object {
        const val HEADER_API_KEY = "X-Api-Key"
    }
}
