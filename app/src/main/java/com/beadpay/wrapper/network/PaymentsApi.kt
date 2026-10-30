package com.beadpay.wrapper.network

import com.beadpay.wrapper.model.PaymentRequest
import com.beadpay.wrapper.model.PaymentResponse
import com.beadpay.wrapper.model.PaymentStatusResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface PaymentsApi {

    @POST("payments/crypto")
    suspend fun createPayment(
        /* X-Api-Key header is injected by ApiKeyInterceptor */
        @Body request: PaymentRequest
    ): PaymentResponse

    @GET("Payments/tracking/{trackingId}")
    suspend fun getPaymentStatus(
        @Path("trackingId") trackingId: String
    ): PaymentStatusResponse
}
