package com.beadpay.wrapper.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PaymentResponse(
    val trackingId: String,
    val paymentPageId: String,
    val paymentUrls: List<PaymentUrl>
)

@JsonClass(generateAdapter = true)
data class PaymentUrl(
    val type: String,
    val url: String
)
