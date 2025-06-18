package com.beadpay.wrapper.ui.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Builds an Intent that launches the browser-based PKCE flow. */
fun Activity.buildAuthIntent(): Intent {
    val config = AuthorizationServiceConfiguration(
        Uri.parse("https://sandbox.beadpay.com/oauth2/authorize"),
        Uri.parse("https://sandbox.beadpay.com/oauth2/token")
    )

    val request = AuthorizationRequest.Builder(
        config,
        "beadwrapper-android",            // client_id
        ResponseTypeValues.CODE,
        Uri.parse("beadwrapper://authcallback")
    ).build()

    return AuthorizationService(this).getAuthorizationRequestIntent(request)
}

/** Handles the custom-scheme redirect and returns the auth code (suspending). */
suspend fun Activity.handleRedirect(intent: Intent): String =
    suspendCancellableCoroutine { cont ->
        val resp = AuthorizationResponse.fromIntent(intent)
        val ex   = net.openid.appauth.AuthorizationException.fromIntent(intent)

        when {
            ex != null -> cont.resumeWithException(ex)
            resp != null -> cont.resume(resp.authorizationCode ?: "")
            else -> cont.resumeWithException(IllegalStateException("No auth response"))
        }
    }
