package com.beadpay.wrapper.repository

import com.beadpay.wrapper.network.BeadApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: BeadApi,
    private val store: AuthLocalStore
) {
    /** Returns a valid access token or throws if none is present. */
    fun requireAccessToken(): String =
        store.accessToken ?: error("Not authenticated — call login() first")

    /** Simple password grant login; replace with PKCE once wired. */
    suspend fun login(username: String, password: String, clientId: String) {
        val rsp = api.getAuthToken(username, password, clientId = clientId)
        store.accessToken = rsp.access_token          // ← write via property
    }

    /** Call on logout if needed. */
    fun clear() {
        store.accessToken = null
    }
}
