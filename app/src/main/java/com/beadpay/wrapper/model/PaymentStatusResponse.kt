package com.beadpay.wrapper.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PaymentStatusResponse(
    val statusCode: String
)
