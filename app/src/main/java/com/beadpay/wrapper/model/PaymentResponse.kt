package com.beadpay.wrapper.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PaymentResponse(
    val paymentId: String,
    val trackingId: String,
    val paymentPageId: String,
    /**
     * Hosted-payment-page URLs. The API returns a plain array of strings
     * (one entry, matching the requested `paymentUrlType`), not the
     * `{type, url}` objects an earlier revision of the API sent.
     */
    val paymentUrls: List<String>
)
