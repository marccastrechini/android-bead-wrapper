package com.beadpay.wrapper.repository

import com.beadpay.wrapper.network.BeadApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: BeadApi,
    private val store: AuthLocalStore
) {
    fun requireAccessToken(): String =
        store.accessToken ?: error("Not authenticated — call login() first")

    /** Example login; expand scopes/PKCE later. */
    suspend fun login(username: String, password: String, clientId: String) {
        val rsp = api.getAuthToken(username, password, clientId = clientId)
        store.save(rsp.access_token)
    }
}
