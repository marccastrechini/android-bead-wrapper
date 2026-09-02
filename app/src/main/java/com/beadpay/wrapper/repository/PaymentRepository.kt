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
    private val api:   PaymentsApi,
    private val moshi: Moshi
) {

    /**
     * Thin wrapper over **POST /payments/crypto**.
     *
     * @param amount       total to charge (dollars; `Double`)
     * @param reference    your internal order / invoice number
     * @param customer     customer-details block, or null to send none
     * @param redirectUrl  where the hosted page sends the shopper when the
     *   flow ends; null omits the field, which leaves the page with no close
     *   control (it only offers a bridge-based close to React Native hosts).
     * @param refundEmail  where a refund is sent; null means the customer's
     *   own email, which is the common case and what a virtual terminal needs.
     *   With no customer there is no fallback, so the field is omitted too.
     */
    suspend fun createPayment(
        amount:      Double,
        reference:   String,
        customer:    Customer?,
        redirectUrl: String?,
        refundEmail: String? = null
    ): PaymentResponse {

        /* ── 1️⃣  Build request body ─────────────────────────── */
        // Assembled by PaymentRequest.of so the harness can offer a copy of the
        // very same body rather than a reconstruction of it.
        val body = PaymentRequest.of(
            amount      = amount,
            reference   = reference,
            customer    = customer,
            redirectUrl = redirectUrl,
            refundEmail = refundEmail
        )

        /* ── 2️⃣  Pretty-print payload in debug builds ───────── */
        if (BuildConfig.DEBUG) {
            val json = moshi.adapter(PaymentRequest::class.java)
                .indent("  ")
                .toJson(body)
            Timber.tag("PaymentRepository").d("→ POST /payments/crypto\n%s", json)
        }

        /* ── 3️⃣  Network call ───────────────────────────────── */
        // ApiKeyInterceptor adds the `X-Api-Key` header automatically.
        return api.createPayment(body)
    }
}
