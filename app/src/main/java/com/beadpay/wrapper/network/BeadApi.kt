package com.beadpay.wrapper.network

import com.beadpay.wrapper.network.dto.AuthTokenResponse
import com.beadpay.wrapper.network.dto.CreatePaymentRequest
import com.beadpay.wrapper.network.dto.CreatePaymentResponse
import retrofit2.http.Body
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface BeadApi {

    /** Keycloak password-grant (ROP C) */
    @FormUrlEncoded
    @POST
    suspend fun fetchToken(
        @Url url: String,
        @FieldMap(encoded = true)
        fields: Map<String, @JvmSuppressWildcards String>
    ): AuthTokenResponse

    /** Bead sandbox payment creation */
    @POST("payments/crypto")
    suspend fun createPayment(
        @Header("Authorization") bearer: String,
        @Body request: CreatePaymentRequest
    ): CreatePaymentResponse
}
