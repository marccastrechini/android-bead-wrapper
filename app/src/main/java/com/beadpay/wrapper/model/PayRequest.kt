package com.beadpay.wrapper.model

import android.os.Parcelable
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal

@Parcelize
@JsonClass(generateAdapter = true)
data class PayRequest(
    val amountMinor: Long,
    val currency:    String
) : Parcelable {
    constructor(amount: BigDecimal, currency: String) :
            this(amount.movePointRight(2).longValueExact(), currency)
}
