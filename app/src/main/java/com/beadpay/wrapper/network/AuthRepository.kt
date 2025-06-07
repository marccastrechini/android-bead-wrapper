package com.beadpay.wrapper.network

import android.content.Context
import androidx.core.net.toUri
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.TokenRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AuthRepository(private val ctx: Context) {

    private val config = AuthorizationServiceConfiguration(
        "https://identity.beadpay.io/realms/nonprod/protocol/openid-connect/auth".toUri(),
        "https://identity.beadpay.io/realms/nonprod/protocol/openid-connect/token".toUri()
    )

    suspend fun fetchToken(terminalId: String, password: String): String =
        suspendCancellableCoroutine { cont ->
            val service = AuthorizationService(ctx)

            val params = mapOf(
                "username" to "$terminalId@beadpay.io",
                "password" to password,
                "grant_type" to "password",
                "scope" to "openid profile email"
            )

            val tokenReq = TokenRequest.Builder(config, "bead-terminal")
                .setAdditionalParameters(params)
                .build()

            service.performTokenRequest(tokenReq) { resp, ex ->
                if (ex != null) cont.resumeWithException(ex)
                else cont.resume(resp!!.accessToken!!)
            }
            cont.invokeOnCancellation { service.dispose() }
        }
}
