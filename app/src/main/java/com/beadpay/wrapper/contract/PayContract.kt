package com.beadpay.wrapper.contract

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.beadpay.wrapper.CheckoutActivity
import com.beadpay.wrapper.model.PayRequest
import com.beadpay.wrapper.model.PayResult

class PayContract : ActivityResultContract<PayRequest, PayResult?>() {

    override fun createIntent(context: Context, input: PayRequest): Intent =
        Intent(context, CheckoutActivity::class.java).apply {
            putExtra(EXTRA_REQUEST, input)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): PayResult? =
        intent?.getParcelableExtra(EXTRA_RESULT)

    companion object {
        const val EXTRA_REQUEST = "bead.REQUEST"
        const val EXTRA_RESULT = "bead.RESULT"
    }
}
