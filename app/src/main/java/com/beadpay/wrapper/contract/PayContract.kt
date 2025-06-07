package com.beadpay.wrapper.contract

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.beadpay.wrapper.model.PaymentRequest
import com.beadpay.wrapper.model.PaymentResponse
import com.beadpay.wrapper.ui.checkout.CheckoutActivity

/**
 * Public-facing contract a host app (Valor POS) uses to start a payment
 * and receive the result.
 */
object PayContract {

    /* Keys for crossing the wrapper/host boundary */
    const val EXTRA_REQUEST = "extra_request"   // Parcelable<PaymentRequest>
    const val EXTRA_RESULT  = "extra_result"    // Parcelable<PaymentResponse>

    /** Classic helper for startActivityForResult */
    fun buildIntent(context: Context, request: PaymentRequest): Intent =
        Intent(context, CheckoutActivity::class.java)
            .putExtra(EXTRA_REQUEST, request)

    /** Modern Activity-Result API wrapper */
    class CreatePaymentLauncher :
        ActivityResultContract<PaymentRequest, PaymentResponse?>() {

        override fun createIntent(context: Context, input: PaymentRequest): Intent =
            buildIntent(context, input)

        override fun parseResult(resultCode: Int, intent: Intent?): PaymentResponse? =
            if (resultCode == Activity.RESULT_OK) {
                intent?.getParcelableExtra(EXTRA_RESULT)
            } else null
    }
}
