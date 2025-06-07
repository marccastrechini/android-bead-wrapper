package com.beadpay.wrapper.model

import android.os.Parcelable
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonClass(generateAdapter = true)
data class PayResult(
    val statusCode:  Int,
    val paymentId:   String?,
    val errorMessage:String?
) : Parcelable
