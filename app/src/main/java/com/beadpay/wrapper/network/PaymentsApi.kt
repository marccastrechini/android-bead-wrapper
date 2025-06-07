package com.beadpay.wrapper.network

import com.beadpay.wrapper.model.PaymentRequest
import com.beadpay.wrapper.model.PaymentResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface PaymentsApi {

    @POST("/payments/crypto")
    suspend fun createPayment(
        @Header("Authorization") bearer: String,
        @Body payload: PaymentRequest
    ): PaymentResponse
}