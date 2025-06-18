package com.beadpay.wrapper.ui.checkout

import android.os.Bundle
import androidx.activity.ComponentActivity
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
 * 2. Ensures we have a bearer-token (password-grant login on first use).
 * 3. Calls POST /payments/crypto and receives the Hosted-Payment-Page URL.
 * 4. Opens that URL in [PaymentWebViewActivity].
 */
@AndroidEntryPoint
class CheckoutActivity : ComponentActivity() {

    @Inject lateinit var createPaymentUseCase: CreatePaymentUseCase
    @Inject lateinit var authRepository:      AuthRepository
    @Inject lateinit var moshi:               Moshi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* ── 1. Parse intent extras ─────────────────────────────── */
        val amount: Double = intent
            .getFloatExtra(PayContract.EXTRA_AMOUNT, -1f)
            .toDouble()
            .takeIf { it > 0.0 }
            ?: return showErrorAndFinish("Invalid or missing amount")

        Timber.d("Starting payment: %.2f USD", amount)

        /* ── 2 + 3. Network work in a coroutine ─────────────────── */
        lifecycleScope.launch {
            try {
                if (!authRepository.isLoggedIn) {
                    Timber.d("No token cached – performing login()")
                    authRepository.login()
                }

                val rsp = createPaymentUseCase(
                    amount    = amount,
                    reference = "ORDER123",          // TODO: replace with real reference
                    customer  = Customer.demo()      // TODO: real customer data
                )

                val hppUrl = rsp.paymentUrls.firstOrNull { it.type == "web" }?.url
                    ?: return@launch showErrorAndFinish("Missing web payment URL")

                /* ── 4. Launch Hosted-Payment-Page ─────────────── */
                PaymentWebViewActivity.launch(
                    context = this@CheckoutActivity,
                    hppUrl  = hppUrl
                )
                finish()

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

    private fun showErrorAndFinish(message: String) {
        AlertDialog.Builder(this)
            .setTitle("BeadPay")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> finish() }
            .setOnCancelListener   {        finish() }
            .show()
    }
}
