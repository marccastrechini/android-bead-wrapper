package com.beadpay.wrapper.network

import com.beadpay.wrapper.model.AuthTokenResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * Auth-only interface.  Payment calls live in PaymentsApi.
 */
interface BeadApi {

    @FormUrlEncoded
    @POST("/auth/realms/bead/protocol/openid-connect/token")
    suspend fun getAuthToken(
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("grant_type") grantType: String = "password",
        @Field("client_id")  clientId: String
    ): AuthTokenResponse
}
