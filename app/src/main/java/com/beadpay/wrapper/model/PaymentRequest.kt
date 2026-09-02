package com.beadpay.wrapper.model

import android.os.Parcelable
import com.beadpay.wrapper.BuildConfig
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
    /** Omitted from the request body when null; see PayContract.EXTRA_OMIT_CUSTOMER. */
    val customer:        Customer? = null,
    /** Omitted from the request body when null; see PayContract.EXTRA_REDIRECT_URL. */
    val redirectUrl:     String? = null,
    val description: String? = null,
    val refundEmail: String? = null
) : Parcelable {

    companion object {

        /**
         * The body POSTed to `/payments/crypto`.
         *
         * The single place the request is assembled, so the copy shown in the
         * harness and the bytes actually sent cannot disagree — a copy that
         * merely resembled the request would be worse than none when it is
         * being used to reproduce a failure.
         */
        fun of(
            amount: Double,
            reference: String,
            customer: Customer?,
            redirectUrl: String?,
            refundEmail: String?
        ): PaymentRequest = PaymentRequest(
            merchantId      = BuildConfig.MERCHANT_ID,
            terminalId      = BuildConfig.TERMINAL_ID,
            requestedAmount = amount,
            paymentUrlType  = "web",
            reference       = reference,
            customer        = customer,
            redirectUrl     = redirectUrl,   // drives the hosted page's close control
            // Falls back to the shopper's own address, and with no shopper there
            // is nothing to fall back to: sending no customer means sending no
            // refundEmail either, so the API is asked the question cleanly.
            refundEmail     = refundEmail ?: customer?.email
        )
    }
}
