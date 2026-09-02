package com.beadpay.wrapper

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.beadpay.wrapper.config.BeadConfig
import com.beadpay.wrapper.contract.PayContract
import com.beadpay.wrapper.databinding.ActivityMainBinding
import com.beadpay.wrapper.model.PaymentRequest
import com.beadpay.wrapper.model.PayResult
import com.beadpay.wrapper.ui.checkout.CheckoutRequest
import com.squareup.moshi.Moshi
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * In-app entry point: the same payment the POS app starts over ACTION_PAY,
 * started here in-process instead.
 *
 * Deliberately goes through [PayContract.CreatePaymentLauncher] rather than
 * building an Intent for [com.beadpay.wrapper.ui.checkout.CheckoutActivity]
 * directly. The contract is the thing that has drifted before (see the class
 * doc on [PayContract]), so the single-app path exercises it exactly as the
 * cross-process path does — a mismatch that would break the POS breaks here
 * too, in Android Studio, instead of on a terminal.
 *
 * There is no "single app mode" switch: the two models are just two entry
 * points into one flow, and Android already dispatches between them by intent
 * — MAIN/LAUNCHER lands here, ACTION_PAY lands on CheckoutActivity.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var moshi: Moshi

    private lateinit var binding: ActivityMainBinding

    /** When the current payment was launched, for the elapsed time in the modal. */
    private var launchedAtMs: Long = 0L

    private val pay = registerForActivityResult(PayContract.CreatePaymentLauncher()) { result ->
        showResult(result)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        /* Which terminal these payments land on. A 403 from the API carries no
         * field-level detail, so the identity in use is most of the diagnosis
         * — worth having on screen before the payment, not only in the error. */
        binding.configSummary.text = BeadConfig.summary()

        binding.redirectGroup.setOnCheckedChangeListener { _, checkedId ->
            binding.redirectCustomLayout.visibility =
                if (checkedId == R.id.redirectCustom) View.VISIBLE else View.GONE
        }

        binding.demoCustomerSwitch.setOnCheckedChangeListener { _, _ -> updateCustomerCaption() }
        updateCustomerCaption()

        binding.payButton.setOnClickListener { startPayment() }
        binding.copyJsonButton.setOnClickListener { copyRequestJson() }
    }

    /* ── launch ─────────────────────────────────────────────────── */

    private fun startPayment() {
        val request = currentRequest() ?: return

        Timber.d(
            "Harness → amount=%.2f redirectUrl=%s customer=%s refundEmail=%s",
            request.amount,
            request.redirectUrl ?: "(build default)",
            if (request.omitCustomer) "(omitted)" else "(demo)",
            request.refundEmail ?: "(customer email)"
        )

        launchedAtMs = SystemClock.elapsedRealtime()
        pay.launch(request)
    }

    /**
     * Puts the exact body that [startPayment] would POST on the clipboard.
     *
     * Routed through [PayContract.buildIntent] and [CheckoutRequest.body] — the
     * real writer and the real reader — rather than assembled here, so what gets
     * pasted into a bug report or another harness is the request, not a
     * plausible-looking imitation of it.
     */
    private fun copyRequestJson() {
        val request = currentRequest() ?: return

        // The launcher's own createIntent, not a hand-listed buildIntent call:
        // spelling the arguments out here once cost a silently dropped
        // omitCustomer, and the copy claimed a customer the payment would not
        // have sent. This is the same intent pay.launch builds, by construction.
        val payIntent = PayContract.CreatePaymentLauncher().createIntent(this, request)

        val body = CheckoutRequest.body(payIntent) ?: run {
            Toast.makeText(this, R.string.harness_copy_needs_amount, Toast.LENGTH_SHORT).show()
            return
        }

        val json = moshi.adapter(PaymentRequest::class.java).indent("  ").toJson(body)

        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Bead payment request", json))

        Timber.d("Copied request JSON:\n%s", json)
        Toast.makeText(
            this,
            getString(R.string.harness_copied, json.length),
            Toast.LENGTH_SHORT
        ).show()
    }

    /** The form as a contract request, or null having marked what is wrong. */
    private fun currentRequest(): PayContract.Request? {
        val amount = binding.amountInput.text.toString().trim().toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            binding.amountLayout.error = getString(R.string.harness_error_amount)
            return null
        }
        binding.amountLayout.error = null

        /* null / "" / a url — the three states EXTRA_REDIRECT_URL distinguishes.
         * "" must not collapse to null: it is a real request for "send none". */
        val redirectUrl = when (binding.redirectGroup.checkedRadioButtonId) {
            R.id.redirectNone   -> ""
            R.id.redirectCustom -> binding.redirectCustomInput.text.toString().trim()
            else                -> null
        }

        /* On: the wrapper substitutes its demo shopper. Off: no customer block
         * is sent at all, and refundEmail goes with it unless typed in — there
         * is then no shopper address for it to fall back to. */
        val omitCustomer = !binding.demoCustomerSwitch.isChecked

        val refundEmail = binding.refundEmailInput.text.toString().trim().ifBlank { null }

        return PayContract.Request(
            amount       = amount,
            redirectUrl  = redirectUrl,
            customer     = null,   // the harness never hand-builds one
            refundEmail  = refundEmail,
            omitCustomer = omitCustomer
        )
    }

    /** Spells out what the switch will actually put on the wire. */
    private fun updateCustomerCaption() {
        binding.customerCaption.setText(
            if (binding.demoCustomerSwitch.isChecked) R.string.harness_customer_demo_caption
            else R.string.harness_customer_none_caption
        )
    }

    /* ── result ─────────────────────────────────────────────────── */

    /**
     * Renders whatever came back, including the null the contract returns for
     * a RESULT_OK carrying no result extra — that is a contract bug, and
     * printing it is the point of the harness.
     */
    private fun showResult(result: PayResult?) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val elapsed = if (launchedAtMs == 0L) null
                      else (SystemClock.elapsedRealtime() - launchedAtMs) / 1000.0

        val status = result?.status ?: "(no result extra)"
        val route  = result?.route

        binding.resultView.text = buildString {
            append(stamp).append("  status: ").append(status)
            append("   via: ").append(route ?: "(not reported)")
            elapsed?.let { append(String.format(Locale.US, "   %.1fs", it)) }
            result?.paymentId?.takeIf { it.isNotBlank() }
                ?.let { appendLine(); append("paymentId: ").append(it) }
        }

        Timber.d("Harness <- status=%s route=%s", status, route)

        AlertDialog.Builder(this)
            .setTitle(status)
            .setMessage(resultDetail(result, elapsed))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /**
     * What came back and who said so.
     *
     * The route matters as much as the status here: the same CANCELLED reaches
     * the POS from the hosted page's redirect, from the status poll, and from
     * the wrapper's own cancel bar, and those are three different integration
     * paths that fail in different ways. Without this the harness could only
     * ever show the "what".
     */
    private fun resultDetail(result: PayResult?, elapsedSeconds: Double?): String = buildString {
        if (result == null) {
            appendLine("RESULT_OK carrying no result extra — nothing was reported.")
            appendLine()
        }

        append("Reported by: ").appendLine(routeDescription(result?.route))
        appendLine()

        append("status: ").appendLine(result?.status ?: "(none)")
        append("paymentId: ").appendLine(result?.paymentId?.ifBlank { "(none)" } ?: "(none)")
        append("route: ").appendLine(result?.route ?: "(not reported)")
        elapsedSeconds?.let { append(String.format(Locale.US, "elapsed: %.1fs%n", it)) }

        result?.error?.let {
            appendLine()
            append("error: ").appendLine(it)
        }
    }.trim()

    /** Plain-English gloss for each `PayContract.ROUTE_*` value. */
    private fun routeDescription(route: String?): String = when (route) {
        PayContract.ROUTE_POLL ->
            "the status poll. The page never asked to close; the API reported a " +
                "final status and the wrapper closed the WebView itself."

        PayContract.ROUTE_POLL_AFTER_REDIRECT ->
            "the status poll, after the page's redirect asked to close. The " +
                "redirect closed the page; the API supplied the outcome."

        PayContract.ROUTE_REDIRECT_HINT ->
            "the redirect's own statusCode parameter — UNVERIFIED. The poll " +
                "never confirmed a final status in time, so the page's claim was used."

        PayContract.ROUTE_TIMEOUT ->
            "nobody. The page closed and no final status was ever confirmed. " +
                "The payment may still have completed — check the dashboard."

        PayContract.ROUTE_USER_CANCEL ->
            "the wrapper itself — its cancel bar or the back gesture. The hosted " +
                "page was never consulted and the API was not asked."

        PayContract.ROUTE_ERROR ->
            "the wrapper, which failed before the payment could finish."

        null -> "nothing — no route was reported."
        else -> route
    }
}
