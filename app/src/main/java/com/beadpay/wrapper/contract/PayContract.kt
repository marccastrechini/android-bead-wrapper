package com.beadpay.wrapper.contract

import android.content.Context
import android.content.Intent
import com.beadpay.wrapper.model.PaymentRequest
import com.beadpay.wrapper.model.PaymentResponse
import com.beadpay.wrapper.ui.checkout.CheckoutActivity

object PayContract {
    private const val EXTRA_REQUEST = "extra_request"
    private const val EXTRA_RESULT  = "extra_result"

    fun buildIntent(context: Context, request: PaymentRequest): Intent =
        Intent(context, CheckoutActivity::class.java).putExtra(EXTRA_REQUEST, request)

    fun parseResult(intent: Intent?): PaymentResponse? =
        intent?.getParcelableExtra(EXTRA_RESULT)
}
