package com.beadpay.wrapper.network

import com.beadpay.wrapper.repository.AuthRepository
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton
import dagger.Lazy

/**
 * Adds the cached bearer token to every request.
 *
 * • _Never_ calls Retrofit — avoids dependency-cycle problems.
 * • If the server answers **401** the token is cleared; the next call will
 *   trigger a fresh `login()` through AuthRepository.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val authRepo: Lazy<AuthRepository>
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        // ── 1. Attach token if we have one ───────────────────────
        val token: String? = runCatching {
            authRepo.get().requireAccessToken()
        }.getOrNull()

        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }

        val response: Response = chain.proceed(request)

        // ── 2. If the server says “Unauthorized” clear the cache ─
        if (response.code == 401) {
            response.close()
            authRepo.get().logout()
        }

        return response
    }
}
