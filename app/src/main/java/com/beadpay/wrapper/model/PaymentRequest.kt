package com.beadpay.wrapper.model

import android.os.Parcelable
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonClass(generateAdapter = true)
data class PaymentRequest(
    val terminalId:      String,
    val merchantId:      String,
    val requestedAmount: Double,          // 100.00
    val paymentUrlType:  String = "web",
    val reference:       String,
    val customer:        Customer,
    val redirectUrl:     String,
    val description: String? = null,
    val refundEmail: String? = null
) : Parcelable
