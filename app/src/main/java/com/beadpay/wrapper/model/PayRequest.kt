package com.beadpay.wrapper.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PayRequest(
    val amountCents: Long,
    val currency: String = "USD",
    val externalTxnId: String? = null
) : Parcelable
