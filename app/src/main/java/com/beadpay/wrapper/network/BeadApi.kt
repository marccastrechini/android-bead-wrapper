package com.beadpay.wrapper.network

import com.beadpay.wrapper.network.dto.AuthTokenResponse
import com.beadpay.wrapper.network.dto.CreatePaymentRequest
import com.beadpay.wrapper.network.dto.CreatePaymentResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface BeadApi {
    @POST("auth/realms/bead/protocol/openid-connect/token")
    suspend fun fetchToken(
        @Header("Content-Type") contentType: String = "application/x-www-form-urlencoded",
        @Body body: String
    ): AuthTokenResponse

    @POST("payments/crypto")
    suspend fun createPayment(
        @Header("Authorization") bearer: String,
        @Body request: CreatePaymentRequest
    ): CreatePaymentResponse
}
