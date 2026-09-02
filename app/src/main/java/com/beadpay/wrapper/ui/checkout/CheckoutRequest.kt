package com.beadpay.wrapper.ui.checkout

import android.content.Intent
import com.beadpay.wrapper.config.BeadConfig
import com.beadpay.wrapper.contract.PayContract
import com.beadpay.wrapper.model.Customer
import com.beadpay.wrapper.model.PaymentRequest

/**
 * Turns a PAY intent into the request the wrapper will actually send.
 *
 * [PayContract] says what a caller may ask for; this says what the wrapper
 * does about it — which defaults fill the gaps the caller left. Both
 * [CheckoutActivity], which sends the request, and the harness screen, which
 * offers a copy of it, read it from here, so the copy cannot drift from the
 * call the way a hand-rolled second version would.
 */
object CheckoutRequest {

    /** Stands in for a real order number until the POS sends one. */
    const val DEFAULT_REFERENCE = "ORDER123"

    /**
     * The caller's answer wins, including an explicit `""` meaning "send none";
     * otherwise the build default. Blank at the end of that means the field is
     * omitted from the request entirely, and the hosted page then renders no
     * close control.
     */
    fun redirectUrl(intent: Intent): String? =
        (PayContract.readRedirectUrl(intent) ?: BeadConfig.redirectUrl)
            .takeIf { it.isNotBlank() }

    /**
     * The caller's shopper, the demo one, or none at all.
     *
     * Null only when the caller explicitly asked for none — a partial block
     * reads as "not specified" and still gets the demo shopper, see
     * [PayContract.readCustomer] for why it is not patched up field by field.
     */
    fun customer(intent: Intent): Customer? =
        if (PayContract.readOmitCustomer(intent)) null
        else PayContract.readCustomer(intent) ?: Customer.demo()

    /** The caller's refund address, or null to use the shopper's own. */
    fun refundEmail(intent: Intent): String? =
        PayContract.readRefundEmail(intent)

    /**
     * The exact body that would be POSTed for [intent], or null when the
     * amount is missing or not a positive amount.
     */
    fun body(intent: Intent): PaymentRequest? {
        val amount = PayContract.readAmount(intent) ?: return null

        return PaymentRequest.of(
            amount      = amount,
            reference   = DEFAULT_REFERENCE,
            customer    = customer(intent),
            redirectUrl = redirectUrl(intent),
            refundEmail = refundEmail(intent)
        )
    }
}
