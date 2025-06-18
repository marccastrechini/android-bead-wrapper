package com.beadpay.wrapper.ui.checkout

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.beadpay.wrapper.contract.PayContract
import com.beadpay.wrapper.model.Customer
import com.beadpay.wrapper.network.readProblemDetail
import com.beadpay.wrapper.repository.AuthRepository
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
 * 1. Reads **amount** (Double) from the intent extras.
 * 2. Ensures we have a bearer token (password-grant login on first use).
 * 3. Calls POST /payments/crypto and receives the HPP URL + tracking-id.
 * 4. Opens that URL in [PaymentWebViewActivity] and waits for a result;
 *    whatever comes back is forwarded to the POS app.
 */
@AndroidEntryPoint
class CheckoutActivity : ComponentActivity() {

    /* ── DI ─────────────────────────────────────────────────────── */
    @Inject lateinit var createPaymentUseCase: CreatePaymentUseCase
    @Inject lateinit var authRepository: AuthRepository
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

        /* 1. Read & validate amount from the wrapper-intent */
        val amount: Double = intent
            .getFloatExtra(PayContract.EXTRA_AMOUNT, -1f)
            .toDouble()
            .takeIf { it > 0.0 }
            ?: return showErrorAndFinish("Invalid or missing amount")

        Timber.d("Starting payment: %.2f USD", amount)

        /* 2 + 3. Make sure we’re logged-in, then create the payment */
        lifecycleScope.launch {
            try {
                if (!authRepository.isLoggedIn) {
                    Timber.d("No token cached – performing login()")
                    authRepository.login()
                }

                val rsp = createPaymentUseCase(
                    amount     = amount,
                    reference  = "ORDER123",      // TODO replace with real ref
                    customer   = Customer.demo()  // TODO real shopper info
                )

                val hppUrl = rsp.paymentUrls
                    .firstOrNull { it.type == "web" }?.url
                    ?: return@launch showErrorAndFinish("Missing web payment URL")

                /* 4. Launch the Hosted-Payment-Page *for result* */
                val webIntent = Intent(
                    this@CheckoutActivity,
                    PaymentWebViewActivity::class.java
                ).apply {
                    putExtra(PaymentWebViewActivity.EXTRA_HPP_URL,  hppUrl)
                    putExtra(PaymentWebViewActivity.EXTRA_TRACKING_ID, rsp.trackingId)
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
    private fun showErrorAndFinish(message: String) {
        AlertDialog.Builder(this)
            .setTitle("BeadPay")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> finish() }
            .setOnCancelListener   {        finish() }
            .show()
    }
}
