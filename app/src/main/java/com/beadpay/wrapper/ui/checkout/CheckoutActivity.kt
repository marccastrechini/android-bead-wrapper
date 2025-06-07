package com.beadpay.wrapper.ui.checkout

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.beadpay.wrapper.model.PaymentRequest
import com.beadpay.wrapper.usecase.CreatePaymentUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@AndroidEntryPoint
class CheckoutActivity : AppCompatActivity() {

    @Inject lateinit var createPaymentUseCase: CreatePaymentUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TODO: setContentView(R.layout.activity_checkout)

        // Demo: immediately create a \.00 USD payment, then open the hosted page
        lifecycleScope.launch {
            val resp = createPaymentUseCase(
                PaymentRequest(
                    amount = BigDecimal("1.00"),
                    currency = "USD",
                    partnerId = "valor"      // ? replace with real value
                )
            )

            startActivity(
                Intent(
                    this@CheckoutActivity,
                    com.beadpay.wrapper.ui.payment.PaymentWebViewActivity::class.java
                ).putExtra("url", resp.hostedPaymentPageUrl)
            )
        }
    }
}