package com.beadpay.wrapper.ui.checkout

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.lifecycle.lifecycleScope
import com.beadpay.wrapper.contract.PayContract
import com.beadpay.wrapper.model.PaymentRequest
import com.beadpay.wrapper.model.PaymentResponse
import com.beadpay.wrapper.ui.payment.PaymentWebViewActivity
import com.beadpay.wrapper.usecase.CreatePaymentUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CheckoutActivity : ComponentActivity() {

    @Inject lateinit var createPaymentUseCase: CreatePaymentUseCase

    /** Launcher for PaymentWebViewActivity */
    private val webViewLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            // Forward the PaymentResponse back to the host app
            setResult(
                RESULT_OK,
                Intent().putExtra(
                    PayContract.EXTRA_RESULT,
                    result.data?.getParcelableExtra<PaymentResponse>(PayContract.EXTRA_RESULT)
                )
            )
        } else {
            setResult(result.resultCode)   // pass CANCELLED or other codes as-is
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Extract PaymentRequest from host app
        val request: PaymentRequest =
            intent.getParcelableExtra(PayContract.EXTRA_REQUEST)
                ?: error("CheckoutActivity launched without PaymentRequest")

        // 2. Call Bead /payments/crypto
        lifecycleScope.launch {
            try {
                val resp = createPaymentUseCase(request)

                // 3. Open Hosted Payment Page
                val webIntent = Intent(
                    this@CheckoutActivity,
                    PaymentWebViewActivity::class.java
                ).putExtra(
                    PaymentWebViewActivity.EXTRA_HPP_URL,
                    resp.hostedPaymentPageUrl
                )

                webViewLauncher.launch(webIntent)

            } catch (ex: Exception) {
                // Bubble error back to host
                setResult(RESULT_CANCELED, Intent().putExtra("error", ex.message))
                finish()
            }
        }
    }
}
