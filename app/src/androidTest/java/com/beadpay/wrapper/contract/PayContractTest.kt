package com.beadpay.wrapper.contract

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.beadpay.wrapper.model.PayResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun readAmount_roundsFloatNoiseAwayToCents() {
        // The sample POS sends a Float. 0.01f widens to 0.009999999776482582,
        // which the API rejected with "19 digits and 18 decimals were found."
        val cent = Intent().putExtra(PayContract.EXTRA_AMOUNT, 0.01f)
        val odd  = Intent().putExtra(PayContract.EXTRA_AMOUNT, 19.99f)

        assertEquals("0.01", PayContract.readAmount(cent)!!.toString())
        assertEquals("19.99", PayContract.readAmount(odd)!!.toString())
    }

    @Test
    fun readAmount_rejectsSubCentAmounts() {
        // Rounds to 0.00, which the API would reject as a zero payment.
        assertNull(PayContract.readAmount(Intent().putExtra(PayContract.EXTRA_AMOUNT, 0.004)))
    }

    @Test
    fun readAmount_rejectsMissingZeroAndNegative() {
        assertNull(PayContract.readAmount(Intent()))
        assertNull(PayContract.readAmount(Intent().putExtra(PayContract.EXTRA_AMOUNT, 0.0)))
        assertNull(PayContract.readAmount(Intent().putExtra(PayContract.EXTRA_AMOUNT, -1.0)))
        assertNull(PayContract.readAmount(Intent().putExtra(PayContract.EXTRA_AMOUNT, "25.0")))
    }

    /* ── redirectUrl: three states, not two ───────────────────────── */

    @Test
    fun readRedirectUrl_absentMeansUseTheBuildDefault() {
        val intent = PayContract.buildIntent(context, 25.0)

        assertFalse(intent.hasExtra(PayContract.EXTRA_REDIRECT_URL))
        assertNull(PayContract.readRedirectUrl(intent))
    }

    @Test
    fun readRedirectUrl_emptyIsAnAnswerAndMustNotCollapseToNull() {
        // "" is the POS asking for no redirectUrl at all, which is a different
        // request from not mentioning one — the hosted page then renders no
        // close control. Collapsing it to null would silently send the default.
        val intent = PayContract.buildIntent(context, 25.0, redirectUrl = "")

        assertEquals("", PayContract.readRedirectUrl(intent))
    }

    @Test
    fun readRedirectUrl_explicitUrlSurvivesRoundTrip() {
        val intent = PayContract.buildIntent(context, 25.0, redirectUrl = "beadwrapper://callback")

        assertEquals("beadwrapper://callback", PayContract.readRedirectUrl(intent))
    }

    @Test
    fun readRedirectUrl_trimsSurroundingWhitespace() {
        // Typed into a POS text field; a stray space would break scheme matching.
        val intent = Intent().putExtra(PayContract.EXTRA_REDIRECT_URL, "  beadwrapper://callback  ")

        assertEquals("beadwrapper://callback", PayContract.readRedirectUrl(intent))
    }

    /* ── Result ───────────────────────────────────────────────────── */

    @Test
    fun parseResult_readsWhatTheWebViewWrites() {
        val written = PayResult(paymentId = "pay_123", status = "COMPLETED")
        val data    = PayContract.resultIntent("pay_123", "COMPLETED")

        val parsed = PayContract.CreatePaymentLauncher()
            .parseResult(Activity.RESULT_OK, data)

        assertEquals(written, parsed)
    }

    @Test
    fun resultIntent_alsoWritesTheStringMirrors() {
        // The sample POS reads strings, not the Parcelable, because it keeps no
        // copy of PayResult. Both must be present on every return.
        val data = PayContract.resultIntent("pay_123", "COMPLETED")

        assertEquals("pay_123",   data.getStringExtra(PayContract.EXTRA_PAYMENT_ID))
        assertEquals("COMPLETED", data.getStringExtra(PayContract.EXTRA_STATUS))
        assertNull(data.getStringExtra(PayContract.EXTRA_ERROR))
    }

    @Test
    fun parseResult_cancelledWithAnErrorCarriesTheReason() {
        // A wrapper-side failure finishes RESULT_CANCELED but is not a shopper
        // walking away: the POS has to be able to tell the two apart.
        val data = PayContract.resultIntent("", PayContract.STATUS_ERROR, "401 Unauthorized")

        val parsed = PayContract.CreatePaymentLauncher()
            .parseResult(Activity.RESULT_CANCELED, data)!!

        assertEquals(PayContract.STATUS_ERROR, parsed.status)
        assertEquals("401 Unauthorized", parsed.error)
        assertEquals("401 Unauthorized", PayContract.readError(data))
    }

    @Test
    fun parseResult_bareCancelReportsCancelled() {
        val parsed = PayContract.CreatePaymentLauncher()
            .parseResult(Activity.RESULT_CANCELED, null)!!

        assertEquals(PayContract.STATUS_CANCELLED, parsed.status)
        assertNull(parsed.error)
    }
}

/**
 * Documents how Android parses the callback URI forms, because the WebView's
 * interception test (`uri.path == "/callback"`) depends on it and the two
 * forms below do NOT parse the same way.
 */
@RunWith(AndroidJUnit4::class)
class CallbackUriParsingTest {

    @Test
    fun authorityForm_hasEmptyPath() {
        val uri = android.net.Uri.parse("beadwrapper://callback?paymentId=p1&statusCode=CANCELLED")
        assertEquals("beadwrapper", uri.scheme)
        assertEquals("callback", uri.host)
        assertEquals("", uri.path)          // NOT "/callback"
        assertEquals("p1", uri.getQueryParameter("paymentId"))
    }

    @Test
    fun rootedForm_hasCallbackPath() {
        val uri = android.net.Uri.parse("beadwrapper:///callback?paymentId=p1")
        assertEquals("beadwrapper", uri.scheme)
        assertEquals("/callback", uri.path)
    }
}
