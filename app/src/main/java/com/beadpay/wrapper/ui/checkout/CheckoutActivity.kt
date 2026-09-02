package com.beadpay.wrapper.ui.checkout

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.beadpay.wrapper.config.BeadConfig
import com.beadpay.wrapper.contract.PayContract
import com.beadpay.wrapper.model.Customer
import com.beadpay.wrapper.network.readProblemDetail
import com.beadpay.wrapper.ui.payment.PaymentWebViewActivity
import com.beadpay.wrapper.usecase.CreatePaymentUseCase
import com.squareup.moshi.Moshi
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import retrofit2.HttpException
import timber.log.Timber
import javax.inject.Inject

/**
 * Entry-point Activity launched by the POS via ACTION_PAY.
 *
 * 1. Reads **amount** (Double) and the optional **redirectUrl**, **customer**
 *    and **refundEmail** overrides from the intent extras, falling back to a
 *    demo shopper when the POS sends none.
 * 2. Calls POST /payments/crypto (authenticated with the terminal API key)
 *    and receives the HPP URL + tracking-id.
 * 3. Opens that URL in [PaymentWebViewActivity] and waits for a result;
 *    whatever comes back is forwarded to the POS app.
 *
 * Every exit path sets a result. A POS that only ever saw RESULT_CANCELED
 * could not distinguish a shopper who walked away from a terminal whose key
 * is wrong, and the second one is the failure worth showing the cashier.
 */
@AndroidEntryPoint
class CheckoutActivity : ComponentActivity() {

    /* ── DI ─────────────────────────────────────────────────────── */
    @Inject lateinit var createPaymentUseCase: CreatePaymentUseCase
    @Inject lateinit var moshi: Moshi

    /* ── Activity-Result launcher for the Web-View ──────────────── */
    private val webLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // simply forward whatever the Web-View produced (or cancelled)
        setResult(result.resultCode, result.data)
        finish()
    }

    /* ── onCreate ───────────────────────────────────────────────── */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* 1. Fail fast if the terminal credentials are not configured */
        if (!BeadConfig.isValid) {
            Timber.e("Missing terminal config: %s", BeadConfig.missing.joinToString())
            return showErrorAndFinish(
                """
                Missing configuration: ${BeadConfig.missing.joinToString()}

                Add the value(s) to local.properties, then rebuild.
                """.trimIndent()
            )
        }

        /* 2. Read & validate amount from the wrapper-intent */
        val amount: Double = PayContract.readAmount(intent)
            ?: return showErrorAndFinish("Invalid or missing amount")

        /* 3. Resolve the redirect url: the caller's answer wins, including
         *    an explicit "" meaning send none; otherwise the build default. */
        val redirectUrl: String? = CheckoutRequest.redirectUrl(intent)

        Timber.d(
            "Starting payment: %.2f USD, redirectUrl=%s (%s)",
            amount,
            redirectUrl ?: "(none)",
            if (PayContract.readRedirectUrl(intent) != null) "from POS" else "build default"
        )

        /* 4. Resolve the shopper. A partial block is treated as none rather
         *    than patched up from the demo, so a POS that forgot a field gets
         *    it said out loud here instead of shipping half-invented details
         *    to the API. See PayContract.readCustomer. */
        val customer = CheckoutRequest.customer(intent)
        if (customer != null && PayContract.readCustomer(intent) == null) {
            if (PayContract.hasCustomerFields(intent)) {
                Timber.w(
                    "Incomplete customer block from POS, falling back to demo. Missing: %s",
                    PayContract.missingCustomerFields(intent).joinToString()
                )
            } else {
                Timber.d("No customer block from POS, using demo shopper.")
            }
        }

        /* 5. Refund address: the caller's if given, else the shopper's own —
         *    and nothing at all when there is no shopper to borrow it from. */
        val refundEmail = CheckoutRequest.refundEmail(intent)

        if (customer == null) {
            Timber.d(
                "Customer: (omitted at the caller's request), refundEmail=%s",
                refundEmail ?: "(omitted - no customer to fall back to)"
            )
        } else {
            Timber.d(
                "Customer: %s <%s> (%s), refundEmail=%s (%s)",
                "${customer.firstName} ${customer.lastName}",
                customer.email,
                if (PayContract.readCustomer(intent) != null) "from POS" else "demo",
                refundEmail ?: customer.email,
                if (refundEmail != null) "from POS" else "customer email"
            )
        }

        /* 6. Create the payment */
        lifecycleScope.launch {
            try {
                val rsp = createPaymentUseCase(
                    amount      = amount,
                    reference   = CheckoutRequest.DEFAULT_REFERENCE,
                    customer    = customer,
                    redirectUrl = redirectUrl,
                    refundEmail = refundEmail
                )

                val hppUrl = rsp.paymentUrls.firstOrNull()
                    ?: return@launch showErrorAndFinish("Missing web payment URL")

                /* 7. Launch the Hosted-Payment-Page *for result* */
                val webIntent = Intent(
                    this@CheckoutActivity,
                    PaymentWebViewActivity::class.java
                ).apply {
                    putExtra(PaymentWebViewActivity.EXTRA_HPP_URL,  hppUrl)
                    putExtra(PaymentWebViewActivity.EXTRA_TRACKING_ID, rsp.trackingId)
                    putExtra(PaymentWebViewActivity.EXTRA_PAYMENT_ID, rsp.paymentId)
                    // The WebView intercepts this url's scheme; it must be the
                    // one actually sent to the API, not the build default.
                    putExtra(PaymentWebViewActivity.EXTRA_REDIRECT_URL, redirectUrl.orEmpty())
                }

                webLauncher.launch(webIntent)   // ⬅️ wait for result

            } catch (t: Throwable) {
                val friendly = when (t) {
                    is HttpException -> t.readProblemDetail(moshi)
                    else             -> t.localizedMessage ?: "Unknown error"
                }
                Timber.e(t, "Payment flow failed → %s", friendly)
                showErrorAndFinish(friendly)
            }
        }
    }

    /* ── helpers ───────────────────────────────────────────────── */
    /**
     * Shows [message] followed by the terminal identity in use, hands the same
     * message back to the POS, then finishes.
     *
     * The identity is appended to every failure because the API's most common
     * rejections (401/403) come back with no field-level detail — knowing which
     * terminal and which key were used is usually the whole diagnosis.
     */
    private fun showErrorAndFinish(message: String) {
        val body = message.trim() + "\n\n" + BeadConfig.summary()

        setResult(
            RESULT_CANCELED,
            PayContract.resultIntent(
                paymentId = "",
                status    = PayContract.STATUS_ERROR,
                error     = body,
                // Failed before a hosted page ever opened, so no page and no
                // poll had a chance to say anything.
                route     = PayContract.ROUTE_ERROR
            )
        )

        AlertDialog.Builder(this)
            .setTitle("BeadPay")
            .setMessage(body)
            .setPositiveButton("OK") { _, _ -> finish() }
            .setOnCancelListener   {        finish() }
            .show()
    }
}
