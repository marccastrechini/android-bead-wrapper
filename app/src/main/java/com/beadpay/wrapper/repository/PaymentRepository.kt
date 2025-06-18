package com.beadpay.wrapper.repository

import com.beadpay.wrapper.BuildConfig
import com.beadpay.wrapper.model.Customer
import com.beadpay.wrapper.model.PaymentRequest
import com.beadpay.wrapper.model.PaymentResponse
import com.beadpay.wrapper.network.PaymentsApi
import com.squareup.moshi.Moshi
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentRepository @Inject constructor(
    private val api:      PaymentsApi,
    private val authRepo: AuthRepository,   // provides (and refreshes) JWT
    private val moshi:    Moshi
) {

    /**
     * Thin wrapper over **POST /payments/crypto**.
     *
     * @param amount     total to charge (dollars; `Double`)
     * @param reference  your internal order / invoice number
     * @param customer   customer-details block
     */
    suspend fun createPayment(
        amount:    Double,
        reference: String,
        customer:  Customer
    ): PaymentResponse {

        /* ── 1️⃣  Build request body ─────────────────────────── */
        val body = PaymentRequest(
            merchantId      = BuildConfig.MERCHANT_ID,
            terminalId      = BuildConfig.TERMINAL_ID,
            requestedAmount = amount,
            paymentUrlType  = "web",
            reference       = reference,
            customer        = customer,
            redirectUrl     = ""                // omitted if unused
            // description / refundEmail intentionally left null
        )

        /* ── 2️⃣  Pretty-print payload in debug builds ───────── */
        if (BuildConfig.DEBUG) {
            val json = moshi.adapter(PaymentRequest::class.java)
                .indent("  ")
                .toJson(body)
            Timber.tag("PaymentRepository").d("→ POST /payments/crypto\n%s", json)
        }

        /* ── 3️⃣  Ensure **fresh** bearer token exists ───────── */
        authRepo.getOrRefreshToken()   // suspends & refreshes when expired
        // (The AuthInterceptor reads the token from EncryptedPrefs and
        //  adds `Authorization: Bearer …` to every request automatically.)

        /* ── 4️⃣  Network call — no manual header needed ─────── */
        return api.createPayment(body)
    }
}
