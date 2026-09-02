package com.beadpay.wrapper.contract

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.IntentCompat
import com.beadpay.wrapper.model.Customer
import com.beadpay.wrapper.model.PayResult
import com.beadpay.wrapper.ui.checkout.CheckoutActivity

/**
 * The integration surface a POS app uses to take a payment.
 *
 * Both directions of the contract live here so the writer and the reader
 * cannot drift apart — they previously did, in both directions: the amount
 * was written as a `Long` and read as a `Float`, and the result was written
 * as a [PayResult] and read as a `PaymentResponse`. Either mismatch fails
 * silently, because `Intent` extras are typed and a type miss just yields the
 * default value.
 */
object PayContract {

    /** Amount to charge, in dollars. Written as a `Double`; see [readAmount]. */
    const val EXTRA_AMOUNT = "amount"

    /**
     * Optional per-call override of the `redirectUrl` sent at payment creation.
     *
     * Three states, because "not specified" and "specified as none" are
     * different requests:
     *
     * - absent      → the wrapper uses its build default (`BuildConfig.REDIRECT_URL`)
     * - `""`        → no redirectUrl is sent at all
     * - a URL       → that URL is sent, and its scheme is what the WebView intercepts
     *
     * Lets a POS app exercise both hosted-page behaviours without a rebuild:
     * with a redirectUrl the page renders a close control that navigates to it,
     * without one it has nowhere to send the shopper and renders none.
     */
    const val EXTRA_REDIRECT_URL = "redirectUrl"

    /* ── Customer ─────────────────────────────────────────────── */

    /**
     * The shopper block sent as `customer` at payment creation, one string
     * extra per field.
     *
     * Flat strings rather than a Parcelable [Customer]: the POS is a separate
     * process built against its own copy of this contract and cannot see the
     * wrapper's classes, so a Parcelable extra would unmarshal to null and be
     * indistinguishable from "not sent" — the silent-default failure this
     * contract already exists to prevent for the amount and the result.
     */
    const val EXTRA_CUSTOMER_EMAIL        = "customerEmail"
    const val EXTRA_CUSTOMER_FIRST_NAME   = "customerFirstName"
    const val EXTRA_CUSTOMER_LAST_NAME    = "customerLastName"
    const val EXTRA_CUSTOMER_ADDRESS      = "customerAddress"
    const val EXTRA_CUSTOMER_ADDRESS2     = "customerAddress2"
    const val EXTRA_CUSTOMER_CITY         = "customerCity"
    const val EXTRA_CUSTOMER_STATE        = "customerState"
    const val EXTRA_CUSTOMER_COUNTRY_CODE = "customerCountryCode"
    const val EXTRA_CUSTOMER_POSTAL_CODE  = "customerPostalCode"

    /**
     * Address a refund is sent to, written as `refundEmail` on the request.
     *
     * Absent means "use the customer's email", which is what the wrapper has
     * always sent and what a virtual terminal requires. Present and different
     * is a real case — a gift purchase refunds to the buyer, not the recipient
     * whose details went in the customer block — so it is settable on its own
     * rather than derived.
     */
    const val EXTRA_REFUND_EMAIL = "refundEmail"

    /**
     * Send no `customer` block at all.
     *
     * A third state, for the same reason [EXTRA_REDIRECT_URL] has one: "did not
     * specify a shopper" and "specified that there is no shopper" are different
     * requests, and absence alone cannot tell them apart. Absent or false means
     * the wrapper fills the gap with [Customer.demo]; true means the field is
     * left off the request entirely, and `refundEmail` with it unless one was
     * given explicitly — with no shopper there is no address to fall back to.
     *
     * Overrides the customer extras when both are sent.
     */
    const val EXTRA_OMIT_CUSTOMER = "omitCustomer"

    /** Customer extras the API requires; [EXTRA_CUSTOMER_ADDRESS2] is optional. */
    private val REQUIRED_CUSTOMER_EXTRAS = listOf(
        EXTRA_CUSTOMER_EMAIL,
        EXTRA_CUSTOMER_FIRST_NAME,
        EXTRA_CUSTOMER_LAST_NAME,
        EXTRA_CUSTOMER_ADDRESS,
        EXTRA_CUSTOMER_CITY,
        EXTRA_CUSTOMER_STATE,
        EXTRA_CUSTOMER_COUNTRY_CODE,
        EXTRA_CUSTOMER_POSTAL_CODE
    )

    /** [PayResult] describing the finished payment. */
    const val EXTRA_RESULT = "extra_result"

    /* String mirrors of [EXTRA_RESULT], for callers that keep no copy of
     * [PayResult]. Written on every return, alongside the Parcelable. */
    const val EXTRA_PAYMENT_ID = "paymentId"
    const val EXTRA_STATUS     = "status"
    const val EXTRA_ERROR      = "error"

    /** Status written when the wrapper itself failed; [EXTRA_ERROR] says why. */
    const val STATUS_ERROR = "ERROR"

    /** Status written when the shopper backed out before paying. */
    const val STATUS_CANCELLED = "CANCELLED"

    /* ── Exit route ───────────────────────────────────────────── */

    /**
     * How the wrapper learned the payment was over — see the `ROUTE_` values.
     *
     * The status says *what* happened; this says *who said so*. They are not
     * the same question and can disagree: a CANCELLED reported by the hosted
     * page's redirect and one reported by the status poll travel different code
     * paths, and only one of them is authoritative. Diagnostic rather than
     * transactional — a POS should branch on [EXTRA_STATUS], never on this.
     */
    const val EXTRA_EXIT_ROUTE = "exitRoute"

    /** The status poll saw a terminal status; the page never asked to close. */
    const val ROUTE_POLL = "POLL"

    /**
     * The page's redirect asked to close and the status poll supplied the
     * outcome. The normal ending when a redirectUrl was sent.
     */
    const val ROUTE_POLL_AFTER_REDIRECT = "POLL_AFTER_REDIRECT"

    /**
     * The poll never produced a terminal status in time, so the status claimed
     * by the redirect's own query parameter was used. Unverified.
     */
    const val ROUTE_REDIRECT_HINT = "REDIRECT_HINT"

    /** The page closed and no status was ever confirmed. Outcome unknown. */
    const val ROUTE_TIMEOUT = "TIMEOUT"

    /** The shopper used the wrapper's own cancel bar or the back gesture. */
    const val ROUTE_USER_CANCEL = "USER_CANCEL"

    /** The wrapper failed before or during the payment; see [EXTRA_ERROR]. */
    const val ROUTE_ERROR = "ERROR"

    /* ── Request ──────────────────────────────────────────────── */

    /**
     * @param redirectUrl `null` to use the wrapper's build default, `""` to
     *   send no redirectUrl, or an explicit URL. See [EXTRA_REDIRECT_URL].
     * @param customer    `null` to let the wrapper use its own demo shopper.
     * @param refundEmail `null` to refund to the customer's email.
     * @param omitCustomer `true` to send no customer block at all; see
     *   [EXTRA_OMIT_CUSTOMER]. Overrides [customer] when both are given.
     */
    @JvmOverloads
    fun buildIntent(
        context: Context,
        amount: Double,
        redirectUrl: String? = null,
        customer: Customer? = null,
        refundEmail: String? = null,
        omitCustomer: Boolean = false
    ): Intent =
        Intent(context, CheckoutActivity::class.java)
            .putExtra(EXTRA_AMOUNT, amount)
            .apply {
                redirectUrl?.let { putExtra(EXTRA_REDIRECT_URL, it) }
                refundEmail?.let { putExtra(EXTRA_REFUND_EMAIL, it) }
                if (omitCustomer) putExtra(EXTRA_OMIT_CUSTOMER, true)
                customer?.let { c ->
                    putExtra(EXTRA_CUSTOMER_EMAIL, c.email)
                    putExtra(EXTRA_CUSTOMER_FIRST_NAME, c.firstName)
                    putExtra(EXTRA_CUSTOMER_LAST_NAME, c.lastName)
                    putExtra(EXTRA_CUSTOMER_ADDRESS, c.address)
                    c.address2?.let { a2 -> putExtra(EXTRA_CUSTOMER_ADDRESS2, a2) }
                    putExtra(EXTRA_CUSTOMER_CITY, c.city)
                    putExtra(EXTRA_CUSTOMER_STATE, c.state)
                    putExtra(EXTRA_CUSTOMER_COUNTRY_CODE, c.countryCode)
                    putExtra(EXTRA_CUSTOMER_POSTAL_CODE, c.postalCode)
                }
            }

    /**
     * Reads the amount written by [buildIntent], rounded to cents, returning
     * null when it is absent or not a positive amount.
     *
     * Accepts any numeric extra rather than only `Double`. A POS app is a
     * separate process built against its own copy of this contract, and
     * `adb shell am start` can only send `--ef`/`--ei`/`--el`; being strict
     * about the boxed type would reject callers that are otherwise correct.
     *
     * Rounding is not cosmetic. A `Float` extra widens to a `Double` carrying
     * its binary noise — `0.01f` becomes `0.009999999776482582` — and the API
     * rejects that outright: *"'Requested Amount' must not be more than 9
     * digits in total, with allowance for 2 decimals. 19 digits and 18
     * decimals were found."* The amount is money, so cents are the unit.
     */
    fun readAmount(intent: Intent): Double? {
        val extras = intent.extras ?: return null
        if (!extras.containsKey(EXTRA_AMOUNT)) return null

        @Suppress("DEPRECATION") // no typed accessor that preserves the sender's numeric type
        val raw = extras.get(EXTRA_AMOUNT) as? Number ?: return null

        val dollars = Math.round(raw.toDouble() * 100.0) / 100.0
        return dollars.takeIf { it > 0.0 }   // sub-cent amounts round away to nothing
    }

    /**
     * Reads the caller's redirect-url preference.
     *
     * Returns null when the caller expressed none — the wrapper then applies
     * its own default. An empty string is a real answer ("send none") and is
     * returned as such, so callers must not collapse it into null.
     */
    fun readRedirectUrl(intent: Intent): String? =
        intent.getStringExtra(EXTRA_REDIRECT_URL)?.trim()

    /**
     * Required customer extras the caller left blank or omitted.
     *
     * Empty means [readCustomer] will return a shopper; non-empty names
     * exactly what is missing, so the wrapper can say so in the log instead of
     * silently falling back.
     */
    fun missingCustomerFields(intent: Intent): List<String> =
        REQUIRED_CUSTOMER_EXTRAS.filter { intent.getStringExtra(it).isNullOrBlank() }

    /**
     * True when the caller asked for no customer block at all.
     *
     * Accepts the string forms too, so `adb shell am start --es omitCustomer
     * true` works alongside `--ez`; a harness driven from the shell should not
     * need to know which flag the extra was declared with.
     */
    fun readOmitCustomer(intent: Intent): Boolean =
        intent.getBooleanExtra(EXTRA_OMIT_CUSTOMER, false) ||
            intent.getStringExtra(EXTRA_OMIT_CUSTOMER).equals("true", ignoreCase = true)

    /** True when the caller set any customer extra at all, complete or not. */
    fun hasCustomerFields(intent: Intent): Boolean =
        (REQUIRED_CUSTOMER_EXTRAS + EXTRA_CUSTOMER_ADDRESS2)
            .any { !intent.getStringExtra(it).isNullOrBlank() }

    /**
     * Reads the shopper block, or null when the caller did not send a complete
     * one.
     *
     * All-or-nothing on the required fields, and deliberately **not** patched
     * up from [Customer.demo]: a POS that forgot a field would otherwise put
     * a placeholder name and address on a real payment, and nothing in the
     * response would show that half the block was invented. A partial block is
     * treated as no block, and the caller decides what to do about it — see
     * [missingCustomerFields].
     */
    fun readCustomer(intent: Intent): Customer? {
        if (missingCustomerFields(intent).isNotEmpty()) return null

        fun field(key: String) = intent.getStringExtra(key)!!.trim()

        return Customer(
            email       = field(EXTRA_CUSTOMER_EMAIL),
            firstName   = field(EXTRA_CUSTOMER_FIRST_NAME),
            lastName    = field(EXTRA_CUSTOMER_LAST_NAME),
            address     = field(EXTRA_CUSTOMER_ADDRESS),
            address2    = intent.getStringExtra(EXTRA_CUSTOMER_ADDRESS2)?.trim()?.ifBlank { null },
            city        = field(EXTRA_CUSTOMER_CITY),
            state       = field(EXTRA_CUSTOMER_STATE),
            countryCode = field(EXTRA_CUSTOMER_COUNTRY_CODE),
            postalCode  = field(EXTRA_CUSTOMER_POSTAL_CODE)
        )
    }

    /**
     * Reads the refund address, or null to let the wrapper fall back to the
     * customer's email. Blank is treated as absent: an empty refund address is
     * not a meaningful request, unlike an empty [EXTRA_REDIRECT_URL].
     */
    fun readRefundEmail(intent: Intent): String? =
        intent.getStringExtra(EXTRA_REFUND_EMAIL)?.trim()?.ifBlank { null }

    /* ── Result ───────────────────────────────────────────────── */

    /**
     * Builds the intent the wrapper returns to the POS.
     *
     * Carries the same fields twice: once as a [PayResult] and once as plain
     * strings, so a caller can read whichever it built against. An error
     * message is part of the result rather than only a dialog in the wrapper —
     * otherwise the POS sees a bare cancel and cannot tell a shopper who walked
     * away from a terminal that is misconfigured.
     *
     * @param route see [EXTRA_EXIT_ROUTE]; diagnostic, never the thing to
     *   branch on.
     */
    fun resultIntent(
        paymentId: String,
        status: String,
        error: String? = null,
        route: String? = null
    ): Intent =
        Intent()
            .putExtra(EXTRA_RESULT, PayResult(paymentId, status, error, route))
            .putExtra(EXTRA_PAYMENT_ID, paymentId)
            .putExtra(EXTRA_STATUS, status)
            .apply {
                error?.let { putExtra(EXTRA_ERROR, it) }
                route?.let { putExtra(EXTRA_EXIT_ROUTE, it) }
            }

    fun readResult(intent: Intent?): PayResult? =
        intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_RESULT, PayResult::class.java) }

    fun readError(intent: Intent?): String? = intent?.getStringExtra(EXTRA_ERROR)

    /** See [EXTRA_EXIT_ROUTE]. Null when the wrapper did not say. */
    fun readExitRoute(intent: Intent?): String? = intent?.getStringExtra(EXTRA_EXIT_ROUTE)

    /* ── Activity-Result API helper ───────────────────────────── */

    /**
     * Input is the amount plus the optional redirect-url, customer and
     * refund-email overrides; see [buildIntent].
     */
    data class Request(
        val amount: Double,
        val redirectUrl: String? = null,
        val customer: Customer? = null,
        val refundEmail: String? = null,
        /** `true` sends no customer block; see [EXTRA_OMIT_CUSTOMER]. */
        val omitCustomer: Boolean = false
    )

    class CreatePaymentLauncher : ActivityResultContract<Request, PayResult?>() {

        override fun createIntent(context: Context, input: Request): Intent =
            buildIntent(
                context,
                input.amount,
                input.redirectUrl,
                input.customer,
                input.refundEmail,
                input.omitCustomer
            )

        /**
         * Reports an outcome for both result codes.
         *
         * RESULT_CANCELED is not only "the shopper walked away": the wrapper
         * also finishes that way when it failed before a payment existed, and
         * then the intent carries [STATUS_ERROR] and a message. Callers must
         * branch on [PayResult.status], never on non-null alone.
         */
        override fun parseResult(resultCode: Int, intent: Intent?): PayResult? =
            readResult(intent)
                ?: if (resultCode == Activity.RESULT_OK) null
                   else PayResult("", STATUS_CANCELLED, readError(intent), readExitRoute(intent))
    }
}
