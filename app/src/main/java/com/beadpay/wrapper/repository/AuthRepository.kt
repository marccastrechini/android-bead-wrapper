package com.beadpay.wrapper.repository

import android.util.Base64
import androidx.annotation.VisibleForTesting
import com.beadpay.wrapper.BuildConfig
import com.beadpay.wrapper.network.BeadApi
import com.beadpay.wrapper.storage.EncryptedPrefs
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: BeadApi,
    private val prefs: EncryptedPrefs
) {

    /* ── Public API ─────────────────────────────────────────── */

    /** `true` when we hold a **non-expired** bearer token. */
    val isLoggedIn: Boolean
        get() = prefs.accessToken?.let { !it.isExpired() } == true

    /**
     * Returns a **valid** bearer token or throws if we have none.
     *
     * Callers who can run suspending code should prefer
     * `getOrRefreshToken()` instead so that an expired token is
     * transparently refreshed.
     */
    @Throws(IllegalStateException::class)
    fun requireAccessToken(): String =
        prefs.accessToken
            ?.takeIf { !it.isExpired() }
            ?: error("Not authenticated — call login() first")

    /**
     * Ensures we have a fresh token, performing the password-grant login
     * when either (a) we never logged in or (b) the cached token expired.
     */
    suspend fun getOrRefreshToken(
        username: String = BuildConfig.USERNAME,
        password: String = BuildConfig.PASSWORD,
        clientId: String = DEFAULT_CLIENT_ID
    ): String {
        if (!isLoggedIn) {
            login(username, password, clientId)
        }
        return prefs.accessToken!!  // now guaranteed non-null & fresh
    }

    /**
     * Performs the password-grant flow against the “nonprod” realm.
     */
    suspend fun login(
        username: String = BuildConfig.USERNAME,
        password: String = BuildConfig.PASSWORD,
        clientId: String = DEFAULT_CLIENT_ID
    ) {
        if (BuildConfig.DEBUG) {
            Timber.tag(LOG).d("login(): user=%s  client_id=%s", username, clientId)
        }

        val rsp = api.getAuthToken(
            username = username,
            password = password,
            clientId = clientId
        )

        // Store all returned tokens
        prefs.accessToken  = rsp.access_token
        prefs.refreshToken = rsp.refresh_token
        prefs.idToken      = rsp.id_token

        if (BuildConfig.DEBUG) {
            Timber.tag(LOG).i("Access token stored; expires in ~${rsp.expires_in} sec")
        }
    }

    /** Clears all cached tokens. */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    fun logout() {
        prefs.accessToken = null
        prefs.refreshToken = null
        prefs.idToken = null
    }

    /* ── Internals ──────────────────────────────────────────── */

    private companion object {
        const val DEFAULT_CLIENT_ID = "bead-terminal"
        const val LOG               = "AuthRepository"
    }
}

/* ──────────────────────────────────────────────────────────────
 * Helper: quick-and-dirty JWT exp-claim check
 * ──────────────────────────────────────────────────────────── */
private fun String.isExpired(): Boolean = try {
    val payloadBase64 = split('.')[1]
    val payloadJson = String(
        Base64.decode(payloadBase64, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    )
    val expSeconds = JSONObject(payloadJson).optLong("exp", 0L)
    if (expSeconds == 0L) true
    else {
        val nowSeconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
        nowSeconds >= expSeconds
    }
} catch (_: Exception) {
    true  // malformed token → expired
}
