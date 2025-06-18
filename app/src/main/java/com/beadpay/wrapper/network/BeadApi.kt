package com.beadpay.wrapper.network

import com.beadpay.wrapper.model.AuthTokenResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * Interface for authentication calls (hosted on Keycloak/identity.beadpay.io).
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * Example request body sent by this method
 *
 *   grant_type=password
 *   client_id=bead-terminal
 *   username=YOUR_TERMINAL_ID@beadpay.io
 *   password=YOUR_TERMINAL_PASSWORD
 *   scope=openid profile email
 * ──────────────────────────────────────────────────────────────────────────────
 */
@JvmSuppressWildcards
interface BeadApi {

    @FormUrlEncoded
    @POST("protocol/openid-connect/token")
    suspend fun getAuthToken(
        @Field("grant_type") grantType: String = GRANT_TYPE,
        @Field("client_id")  clientId:  String = DEFAULT_CLIENT_ID,
        @Field("username")   username:  String,
        @Field("password")   password:  String,
        @Field("scope")      scope:     String = DEFAULT_SCOPE
    ): AuthTokenResponse

    companion object {
        private const val GRANT_TYPE = "password"
        private const val DEFAULT_CLIENT_ID = "bead-terminal"
        private const val DEFAULT_SCOPE = "openid profile email"
    }
}
