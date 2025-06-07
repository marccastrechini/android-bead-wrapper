package com.beadpay.wrapper.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CreatePaymentRequest(
    @Json(name = "amount")     val amountMinor: Long,
    @Json(name = "currency")   val currency:   String,
    @Json(name = "return_url") val returnUrl:  String
)
