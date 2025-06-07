package com.beadpay.wrapper.model

import android.os.Parcelable
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonClass(generateAdapter = true)
data class PaymentResponse(
    val id: String,
    val hostedPaymentPageUrl: String,
    val expiresAt: String,
    val status: String
) : Parcelable
