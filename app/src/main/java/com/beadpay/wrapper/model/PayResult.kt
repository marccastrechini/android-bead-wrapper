package com.beadpay.wrapper.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PayResult(
    val status: Status,
    val trackingId: String?,
    val message: String? = null
) : Parcelable {
    enum class Status { COMPLETED, FAILED, CANCELED }
}
