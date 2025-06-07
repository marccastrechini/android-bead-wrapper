package com.beadpay.wrapper.repository

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authService: AuthorizationService,
    private val config: AuthorizationServiceConfiguration
) {

    private var authState = AuthState(config)

    private val clientId    = "bead-terminal"
    private val redirectUri = Uri.parse("beadwrapper://authcallback")

    /** Intent that starts the browser-based PKCE flow. */
    fun buildAuthIntent(): Intent {
        val req = AuthorizationRequest.Builder(
            config,
            clientId,
            /* responseType = */ "code",
            redirectUri
        )
            .setScope("openid profile email")
            .build()

        return authService.getAuthorizationRequestIntent(req)
    }

    /** Exchanges the auth-code for tokens. Call from the redirect Activity. */
    suspend fun handleRedirect(intent: Intent?) = withContext(Dispatchers.IO) {
        val resp = AuthorizationResponse.fromIntent(intent ?: return@withContext)
        val ex   = AuthorizationException.fromIntent(intent)

        authState.update(resp, ex)

        if (resp != null) {
            val tokenReq = resp.createTokenExchangeRequest()
            authService.performTokenRequest(tokenReq) { tokenResp, tokenEx ->
                authState.update(tokenResp, tokenEx)
            }
        }
    }

    /** Returns a valid access token, refreshing if necessary. */
    suspend fun getValidToken(): String = withContext(Dispatchers.IO) {
        if (authState.needsTokenRefresh) {
            val refreshReq = authState.createTokenRefreshRequest()
            authService.performTokenRequest(refreshReq) { resp, ex ->
                authState.update(resp, ex)
            }
        }
        return@withContext authState.accessToken
            ?: error("User not authenticated – launch buildAuthIntent() first.")
    }
}
