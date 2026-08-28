package com.beadpay.wrapper.contract

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.IntentCompat
import com.beadpay.wrapper.model.PayResult
import com.beadpay.wrapper.ui.checkout.CheckoutActivity

/**
 * The integration surface a POS app uses to take a payment.
 *
 * Both directions of the contract live here so the writer and the reader
 * cannot drift apart — they previously did, in both directions: the amount
 * was written as a `Long` and read as a `Float`, and the result was written
 * as a [PayResult] and read as a `PaymentResponse`. Either mismatch fails
 * silently, because `Intent` extras are typed and a type miss just yields the
 * default value.
 */
object PayContract {

    /** Amount to charge, in dollars. Written as a `Double`; see [readAmount]. */
    const val EXTRA_AMOUNT = "amount"

    /** [PayResult] describing the finished payment. */
    const val EXTRA_RESULT = "extra_result"

    /* ── Request ──────────────────────────────────────────────── */

    fun buildIntent(context: Context, amount: Double): Intent =
        Intent(context, CheckoutActivity::class.java)
            .putExtra(EXTRA_AMOUNT, amount)

    /**
     * Reads the amount written by [buildIntent], returning null when it is
     * absent or not a positive number.
     *
     * Accepts any numeric extra rather than only `Double`. A POS app is a
     * separate process built against its own copy of this contract, and
     * `adb shell am start` can only send `--ef`/`--ei`/`--el`; being strict
     * about the boxed type would reject callers that are otherwise correct.
     */
    fun readAmount(intent: Intent): Double? {
        val extras = intent.extras ?: return null
        if (!extras.containsKey(EXTRA_AMOUNT)) return null

        @Suppress("DEPRECATION") // no typed accessor that preserves the sender's numeric type
        val raw = extras.get(EXTRA_AMOUNT) as? Number ?: return null

        return raw.toDouble().takeIf { it > 0.0 }
    }

    /* ── Result ───────────────────────────────────────────────── */

    fun readResult(intent: Intent?): PayResult? =
        intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_RESULT, PayResult::class.java) }

    /* ── Activity-Result API helper ───────────────────────────── */

    class CreatePaymentLauncher : ActivityResultContract<Double, PayResult?>() {

        override fun createIntent(context: Context, input: Double): Intent =
            buildIntent(context, input)

        override fun parseResult(resultCode: Int, intent: Intent?): PayResult? =
            if (resultCode == Activity.RESULT_OK) readResult(intent) else null
    }
}
