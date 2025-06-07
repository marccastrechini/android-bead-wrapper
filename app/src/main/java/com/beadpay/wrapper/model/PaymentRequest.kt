package com.beadpay.wrapper.model

import android.os.Parcelable
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal

@Parcelize
@JsonClass(generateAdapter = true)
data class PaymentRequest(
    val amount: BigDecimal,
    val currency: String,
    val partnerId: String
) : Parcelable
