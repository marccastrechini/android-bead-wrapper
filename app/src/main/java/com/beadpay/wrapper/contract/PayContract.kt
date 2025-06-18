package com.beadpay.wrapper.contract

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.beadpay.wrapper.model.PaymentResponse
import com.beadpay.wrapper.ui.checkout.CheckoutActivity

object PayContract {

    /* ── Extras ───────────────────────────────────────────── */
    const val EXTRA_AMOUNT = "amount"                 // long
    const val EXTRA_RESULT = "extra_result"           // Parcelable<PaymentResponse>

    /* ── Classic helper (startActivityForResult) ─────────── */
    fun buildIntent(context: Context, amount: Long): Intent =
        Intent(context, CheckoutActivity::class.java)
            .putExtra(EXTRA_AMOUNT, amount)

    /* ── Activity-Result API helper ───────────────────────── */
    class CreatePaymentLauncher :
        ActivityResultContract<Long /* amount */, PaymentResponse?>() {

        override fun createIntent(context: Context, input: Long): Intent =
            buildIntent(context, input)

        override fun parseResult(resultCode: Int, intent: Intent?): PaymentResponse? =
            if (resultCode == Activity.RESULT_OK) {
                intent?.getParcelableExtra(EXTRA_RESULT)
            } else null
    }
}
