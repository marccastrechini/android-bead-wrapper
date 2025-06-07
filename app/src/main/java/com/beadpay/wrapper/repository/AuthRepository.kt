package com.beadpay.wrapper.repository

import com.beadpay.wrapper.network.BeadApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: BeadApi
) {

    private var cachedToken: String? = null

    /** Non-prod Keycloak token endpoint from Bead docs */
    private val tokenUrl =
        "https://identity.beadpay.io/realms/nonprod/protocol/openid-connect/token"

    /**
     * Login once via password grant.
     * In production add expiry/refresh handling.
     */
    suspend fun login(
        username: String,
        password: String,
        isTerminal: Boolean = false
    ): String {
        val clientId = if (isTerminal) "bead-terminal" else "bead-integrator"

        val form = mapOf(
            "grant_type" to "password",
            "client_id"  to clientId,
            "username"   to username,
            "password"   to password,
            "scope"      to "openid profile email"
        )

        val rsp = api.fetchToken(tokenUrl, form)
        cachedToken = rsp.accessToken
        return rsp.accessToken
    }

    /** Lazily obtain a token for internal calls */
    suspend fun getToken(): String =
        cachedToken ?: login("dev-user@beadpay.io", "dev-pass", isTerminal = true)
}
