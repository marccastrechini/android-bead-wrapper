package com.beadpay.wrapper.contract

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.beadpay.wrapper.model.PayResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Round-trips the POS-facing contract in both directions.
 *
 * These are the mismatches that shipped undetected: an amount written as one
 * numeric type and read as another, and a result written as one Parcelable
 * and read as a different class. Both fail silently at runtime, so they are
 * only catchable by asserting the round trip.
 */
@RunWith(AndroidJUnit4::class)
class PayContractTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun buildIntent_amountSurvivesRoundTrip() {
        val intent = PayContract.buildIntent(context, 25.0)
        assertEquals(25.0, PayContract.readAmount(intent)!!, 0.0001)
    }

    @Test
    fun readAmount_acceptsOtherNumericTypes() {
        // `adb shell am start --ef` and POS apps built against an older copy of
        // this contract send float/int/long rather than double.
        val float = Intent().putExtra(PayContract.EXTRA_AMOUNT, 25.0f)
        val long  = Intent().putExtra(PayContract.EXTRA_AMOUNT, 25L)
        val int   = Intent().putExtra(PayContract.EXTRA_AMOUNT, 25)

        assertEquals(25.0, PayContract.readAmount(float)!!, 0.0001)
        assertEquals(25.0, PayContract.readAmount(long)!!,  0.0001)
        assertEquals(25.0, PayContract.readAmount(int)!!,   0.0001)
    }

    @Test
    fun readAmount_rejectsMissingZeroAndNegative() {
        assertNull(PayContract.readAmount(Intent()))
        assertNull(PayContract.readAmount(Intent().putExtra(PayContract.EXTRA_AMOUNT, 0.0)))
        assertNull(PayContract.readAmount(Intent().putExtra(PayContract.EXTRA_AMOUNT, -1.0)))
        assertNull(PayContract.readAmount(Intent().putExtra(PayContract.EXTRA_AMOUNT, "25.0")))
    }

    @Test
    fun parseResult_readsWhatTheWebViewWrites() {
        val written = PayResult(paymentId = "pay_123", status = "COMPLETED")
        val data    = Intent().putExtra(PayContract.EXTRA_RESULT, written)

        val parsed = PayContract.CreatePaymentLauncher()
            .parseResult(Activity.RESULT_OK, data)

        assertEquals(written, parsed)
    }

    @Test
    fun parseResult_returnsNullWhenCancelled() {
        val data = Intent().putExtra(
            PayContract.EXTRA_RESULT,
            PayResult(paymentId = "pay_123", status = "COMPLETED")
        )

        assertNull(PayContract.CreatePaymentLauncher().parseResult(Activity.RESULT_CANCELED, data))
    }
}
