package com.beadpay.wrapper.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Nested object returned by the Bead /payments/crypto endpoint.
 * Example JSON:
 * {
 *   "payment_id":  "abc-123",
 *   "hosted_url":  "https://sandbox.beadpay.com/…",
 *   "tracking_id": "xyz-789",
 *   "payment_urls": { "url": "https://merchant.example/callback" }
 * }
 */
@JsonClass(generateAdapter = true)
data class PaymentUrls(
    @Json(name = "url") val url: String
)

@JsonClass(generateAdapter = true)
data class CreatePaymentResponse(
    @Json(name = "payment_id")  val paymentId:   String,
    @Json(name = "hosted_url")  val hostedUrl:   String,
    @Json(name = "tracking_id") val trackingId:  String?        = null,
    @Json(name = "payment_urls") val paymentUrls: PaymentUrls?  = null
)
