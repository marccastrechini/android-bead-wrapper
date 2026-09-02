package com.beadpay.wrapper.ui.payment

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.beadpay.wrapper.BuildConfig
import com.beadpay.wrapper.config.BeadConfig
import com.beadpay.wrapper.contract.PayContract
import com.beadpay.wrapper.network.PaymentsApi
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

/**
 * Hosts the hosted payment page and watches the payment's status.
 *
 * Two things are deliberately visible rather than silent:
 *
 * - **Errors render in the page**, not in a dialog that dismisses to a blank
 *   screen. A failure here happens in front of a shopper and is diagnosed by
 *   whoever is standing at the terminal, so the url, tracking id and terminal
 *   identity are on screen with it — and the same message is handed back to
 *   the POS.
 * - **The wrapper owns a Cancel control.** The hosted page renders its own
 *   close control only when it was given a `redirectUrl`, so with none the
 *   shopper would otherwise have no way out but the system back gesture.
 */
@AndroidEntryPoint
class PaymentWebViewActivity : ComponentActivity() {

    @Inject lateinit var paymentsApi: PaymentsApi

    companion object {
        const val EXTRA_HPP_URL = "extra_hpp_url"
        const val EXTRA_TRACKING_ID = "tracking_id"
        const val EXTRA_PAYMENT_ID = "extra_payment_id"

        /**
         * The redirect url actually sent at payment creation ("" if none).
         * Its scheme is what navigation is matched against — reading the build
         * default here instead would miss a per-call override from the POS.
         */
        const val EXTRA_REDIRECT_URL = "extra_redirect_url"

        private const val TAG = "PaymentWebView"

        /** Transient poll failures to ride out before surfacing an error. */
        private const val MAX_POLL_FAILURES = 3

        /**
         * How long the poller gets to produce a real status after the page's
         * redirect asked to close.
         *
         * Generous because the page's own "Payment Cancelled" screen has been
         * measured running ~25-30s ahead of the API: cutting the wait short
         * would report "don't know" for a payment that was about to resolve
         * cleanly. The shopper is looking at a "finishing" panel meanwhile, not
         * a frozen page.
         */
        private const val REDIRECT_GRACE_MS = 30_000L

        /**
         * The API's final states, and the result code each one returns.
         *
         * Taken from https://developers.bead.xyz/payments/payment-statuses —
         * every status that page marks final is here, because a status missing
         * from this map is not an error the poller reports, it is a poll loop
         * that runs forever behind an unchanging screen. That is what happened
         * to `underpaid`, and before it to `cancelled`.
         *
         * This is the only exit that works for **every** way a payment can end.
         * The redirect interception in [BeadWebClient.handleUrl] needs the page
         * to navigate somewhere, and the page only offers that when it was given
         * a redirectUrl — without one it renders its own "Cancelled" card and
         * stays put, and a WebView that is never told to close never closes.
         *
         * `RESULT_OK` means the payment reached a real outcome, not that it
         * succeeded — **only `COMPLETED` is a fulfilment trigger**. `underpaid`,
         * `overpaid` and `invalid` are failures the docs say to treat as not
         * completed, but they are `RESULT_OK` all the same: the shopper's funds
         * moved and are sitting in a reclaim process, and a POS told
         * `RESULT_CANCELED` would reasonably read that as "nothing happened" and
         * never look. `RESULT_CANCELED` is kept for the two endings where no
         * funds were taken.
         *
         * Compared case-insensitively: the API answers in lower case
         * (`"cancelled"`), and the value handed to the POS is upper-cased so a
         * cancel reported by the poller reads the same as one reported by the
         * back button ([PayContract.STATUS_CANCELLED]).
         */
        private val TERMINAL_STATUSES: Map<String, Int> = mapOf(
            "COMPLETED" to Activity.RESULT_OK,        // the only success
            "UNDERPAID" to Activity.RESULT_OK,        // funds moved, awaiting reclaim
            "OVERPAID"  to Activity.RESULT_OK,        // funds moved, awaiting reclaim
            "INVALID"   to Activity.RESULT_OK,        // irregular; funds may be involved
            "CANCELLED" to Activity.RESULT_CANCELED,  // no funds converted
            "EXPIRED"   to Activity.RESULT_CANCELED   // nothing arrived in the window
        )

        /**
         * States the API is still working through. Anything outside these and
         * [TERMINAL_STATUSES] is a status this build has never heard of — the
         * docs already reserve `fullyRefunded` and `partiallyRefunded` — so it
         * is surfaced on screen rather than waited on in silence.
         *
         * `processing` explicitly is **not** a fulfilment trigger.
         */
        private val TRANSITIONAL_STATUSES = setOf("CREATED", "PROCESSING")

        fun launch(
            context: Context,
            hppUrl: String,
            trackingId: String,
            paymentId: String = "",
            redirectUrl: String = BeadConfig.redirectUrl
        ) {
            val i = Intent(context, PaymentWebViewActivity::class.java)
                .putExtra(EXTRA_HPP_URL, hppUrl)
                .putExtra(EXTRA_TRACKING_ID, trackingId)
                .putExtra(EXTRA_PAYMENT_ID, paymentId)
                .putExtra(EXTRA_REDIRECT_URL, redirectUrl)
            context.startActivity(i)
        }
    }

    private var hppUrl: String = ""
    private var trackingId: String = ""
    private var paymentId: String = ""

    /** "" when the caller asked for no redirectUrl. */
    private var redirectUrl: String = ""
    private val redirectScheme: String get() = BeadConfig.schemeOf(redirectUrl)

    private var webView: WebView? = null

    /**
     * Shows the last status the API reported, under the page.
     *
     * The hosted page looks identical whether the payment is `created` or
     * `underpaid`, so without this the only sign of a status the poller cannot
     * act on is a screen that never changes — indistinguishable from a hang.
     */
    private var statusView: TextView? = null
    private var pollJob: Job? = null
    private var finished = false

    /**
     * When the page's redirect was intercepted, and what it claimed.
     *
     * The redirect says *close*, not *what happened* — the page hands over
     * whatever it feels like, and on the shopper's own close control that has
     * been nothing at all. So it stops the page and starts a clock; the poll
     * loop then has [REDIRECT_GRACE_MS] to get the answer from the API, which
     * is the only party that actually knows.
     */
    private var redirectDeadlineMs: Long = 0L
    private var redirectStatusHint: String? = null
    private val redirectSeen: Boolean get() = redirectDeadlineMs > 0L

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        hppUrl = intent.getStringExtra(EXTRA_HPP_URL).orEmpty()
        trackingId = intent.getStringExtra(EXTRA_TRACKING_ID).orEmpty()
        paymentId = intent.getStringExtra(EXTRA_PAYMENT_ID).orEmpty()
        redirectUrl = intent.getStringExtra(EXTRA_REDIRECT_URL).orEmpty()

        // Launched wrong: show it here rather than crashing on an `error(…)`,
        // because a crash tells the POS nothing at all.
        if (hppUrl.isBlank() || trackingId.isBlank()) {
            val missing = buildString {
                if (hppUrl.isBlank()) appendLine("- Missing hosted-page URL")
                if (trackingId.isBlank()) appendLine("- Missing tracking ID")
            }.trim()
            setContentView(errorView("Cannot open payment page", missing, onRetry = null))
            return
        }

        Timber.tag(TAG).i(
            "Opening hosted page. redirectUrl=%s so the page's own close control %s " +
                "(no ReactNativeWebView bridge exists in a native WebView).",
            redirectUrl.ifBlank { "(none)" },
            if (redirectUrl.isBlank()) "will NOT render" else "should render"
        )

        setContentView(paymentView())
        startPolling()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Once the page has closed and we are waiting on the API, back
                // must not report CANCELLED — the payment may be completing.
                if (redirectSeen) {
                    Timber.tag(TAG).d("Back ignored: waiting for the final payment status.")
                    return
                }
                val web = webView
                if (web != null && web.canGoBack()) web.goBack() else cancel()
            }
        })
    }

    /* ── UI ─────────────────────────────────────────────────────── */

    /**
     * The hosted page, with the wrapper's cancel bar beneath it.
     *
     * Below rather than above so it cannot cover the hosted page's own close
     * control, which the page draws in its top corner when it was given a
     * redirectUrl. Both are then visible at once, which is what lets the two
     * be told apart while checking which screens render the page's own.
     */
    private fun paymentView(): View {
        val web = WebView(this).apply {
            configureSettings()
            webViewClient = BeadWebClient()
            loadUrl(hppUrl)
        }
        webView = web

        val cancel = Button(this).apply {
            text = getString(com.beadpay.wrapper.R.string.cancel_payment)
            setOnClickListener { cancel() }
        }

        val status = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            val pad = (4 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            text = getString(com.beadpay.wrapper.R.string.status_waiting)
        }
        statusView = status

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(web, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            addView(status, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(cancel, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
    }

    /**
     * Full-screen error panel: what went wrong, then the context needed to act
     * on it — which page, which payment, which terminal.
     */
    private fun errorView(title: String, message: String, onRetry: (() -> Unit)?): View {
        val pad = (16 * resources.displayMetrics.density).toInt()

        val heading = TextView(this).apply {
            text = title
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        }
        val body = TextView(this).apply {
            text = message
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(0, pad, 0, pad)
        }
        val detail = TextView(this).apply {
            text = diagnostics()
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.GRAY)
            setTextIsSelectable(true)   // so it can be copied into a bug report
        }

        return ScrollView(this).apply {
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.START
                    setPadding(pad, pad * 2, pad, pad)
                    addView(heading)
                    addView(body)
                    addView(detail)
                    onRetry?.let { retry ->
                        addView(
                            Button(context).apply {
                                text = getString(com.beadpay.wrapper.R.string.retry)
                                setOnClickListener { retry() }
                            },
                            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                        )
                    }
                    addView(
                        Button(context).apply {
                            text = getString(com.beadpay.wrapper.R.string.close)
                            setOnClickListener { finishWithError(message) }
                        },
                        LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                    )
                }
            )
        }
    }

    private fun diagnostics(): String = buildString {
        append("url: ").append(hppUrl.ifBlank { "(missing)" }).append('\n')
        append("paymentId: ").append(paymentId.ifBlank { "(unknown)" }).append('\n')
        append("trackingId: ").append(trackingId.ifBlank { "(missing)" }).append('\n')
        append("redirectUrl: ").append(redirectUrl.ifBlank { "(none sent)" }).append('\n')
        append(BeadConfig.summary())
    }

    /** Swaps the page out for the error panel and stops watching the payment. */
    private fun showError(title: String, message: String, onRetry: (() -> Unit)? = null) {
        if (finished) return
        Timber.tag(TAG).e("%s : %s", title, message)
        pollJob?.cancel()
        webView?.stopLoading()
        setContentView(errorView(title, message, onRetry))
    }

    /**
     * Shown between the page asking to close and the API saying what happened.
     *
     * Without it the shopper stares at a dead hosted page for as long as the
     * backend takes to catch up, with no sign anything is still happening.
     */
    private fun finishingView(): View {
        val pad = (24 * resources.displayMetrics.density).toInt()

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)

            addView(ProgressBar(this@PaymentWebViewActivity))
            addView(TextView(this@PaymentWebViewActivity).apply {
                text = getString(com.beadpay.wrapper.R.string.finishing_payment)
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setPadding(0, pad, 0, 0)
            })
        }
    }

    /** Rebuilds the page and restarts the watch, after a Retry. */
    private fun reload() {
        setContentView(paymentView())
        startPolling()
    }

    /* ── Status polling ─────────────────────────────────────────── */

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = lifecycleScope.launch { pollPaymentStatus(trackingId) }
    }

    private suspend fun pollPaymentStatus(trackingId: String) {
        var lastStatus: String? = null
        var consecutiveFailures = 0

        while (true) {
            try {
                val statusCode = paymentsApi.getPaymentStatus(trackingId).statusCode
                consecutiveFailures = 0

                if (statusCode != lastStatus) {
                    Timber.tag(TAG).i("Payment status changed: %s", statusCode)
                    lastStatus = statusCode
                } else {
                    Timber.tag(TAG).d("Status unchanged: %s", statusCode)
                }

                val normalised = statusCode.uppercase(Locale.US)
                val resultCode = TERMINAL_STATUSES[normalised]

                /* An unrecognised status is the dangerous one: not terminal, so
                 * the loop keeps going, and previously with nothing on screen to
                 * say so. Waiting is still the safe response — it may be a new
                 * transitional state — but it is now a visible wait. */
                val unknown = resultCode == null && normalised !in TRANSITIONAL_STATUSES
                if (unknown) {
                    Timber.tag(TAG).w(
                        "Unrecognised status '%s' - not in TERMINAL_STATUSES or " +
                            "TRANSITIONAL_STATUSES. Still polling; check the status docs.",
                        statusCode
                    )
                }

                statusView?.text = when {
                    unknown -> getString(com.beadpay.wrapper.R.string.status_unknown, statusCode)
                    else    -> getString(com.beadpay.wrapper.R.string.status_current, statusCode)
                }
                if (resultCode != null) {
                    Timber.tag(TAG).i(
                        "Final status reached: %s (resultCode=%s) - finishing activity.",
                        normalised,
                        if (resultCode == Activity.RESULT_OK) "RESULT_OK" else "RESULT_CANCELED"
                    )
                    finishWith(
                        resultCode = resultCode,
                        id         = paymentId.ifBlank { trackingId },
                        status     = normalised,
                        error      = null,
                        route      = if (redirectSeen) PayContract.ROUTE_POLL_AFTER_REDIRECT
                                     else PayContract.ROUTE_POLL
                    )
                    break
                }

                // The page has closed and the API still has nothing final to
                // say. Waiting forever would strand the shopper on the spinner.
                if (redirectSeen && SystemClock.elapsedRealtime() >= redirectDeadlineMs) {
                    finishAfterRedirectGrace(lastStatus)
                    break
                }

            } catch (e: Exception) {
                // One dropped request on a till's wifi is not a failed payment;
                // give up only once it is clearly not coming back.
                consecutiveFailures++
                Timber.tag(TAG).w(e, "Status poll failed (%d/%d)", consecutiveFailures, MAX_POLL_FAILURES)

                if (consecutiveFailures >= MAX_POLL_FAILURES) {
                    showError(
                        title = "Lost contact with the payment service",
                        message = "Could not check the payment status after $MAX_POLL_FAILURES " +
                            "attempts.\n" + (e.localizedMessage ?: e.javaClass.simpleName) +
                            "\n\nThe shopper may still have paid - check the payment in the " +
                            "Bead dashboard before retrying.",
                        onRetry = { reload() }
                    )
                    break
                }
            }

            delay(2000)
        }
    }

    /* ── WebView ────────────────────────────────────────────────── */

    private inner class BeadWebClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            Timber.tag(TAG).d("Intercepted request URL -> %s", request.url)
            return handleUrl(request.url)
        }

        @Deprecated("Deprecated in Java")
        @Suppress("OverridingDeprecatedMember")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            Timber.tag(TAG).d("Intercepted legacy URL -> %s", url)
            return handleUrl(url.toUri())
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            Timber.tag(TAG).d("Page STARTED -> %s", url)
        }

        override fun onPageFinished(view: WebView, url: String) {
            Timber.tag(TAG).d("Page FINISHED -> %s", url)
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            Timber.tag(TAG).e(
                "ERROR %d on %s : %s", error.errorCode, request.url, error.description
            )

            // A failed image or analytics beacon must not take down a payment
            // the shopper can still complete; only the main document counts.
            if (!request.isForMainFrame) return

            showError(
                title = "Could not load the payment page",
                message = "${error.description} (error ${error.errorCode})",
                onRetry = { reload() }
            )
        }

        /**
         * Intercepts navigation to the redirect url this payment was created
         * with.
         *
         * Matches on scheme *or* host, and on nothing else. The page does not
         * navigate to the URI it was handed, so every narrower test has failed
         * against a different mangling of it:
         *
         * - requiring `path == "/callback"` never held for `beadwrapper://callback`
         *   — there "callback" parses as the authority and the path is empty, so
         *   the redirect fell through to a page load and hit ERR_UNKNOWN_URL_SCHEME.
         * - requiring the scheme never held either: the page emits
         *   `https://beadwrapper//callback/?…`, moving our scheme name to the host,
         *   which fell through to a page load and hit ERR_NAME_NOT_RESOLVED.
         *
         * Both failures looked identical to the shopper — an error panel on exit,
         * and no result delivered to the POS. Our private scheme appearing in
         * either position is enough to claim the URI as ours.
         */
        private fun handleUrl(uri: Uri): Boolean {
            if (redirectScheme.isBlank()) return false   // nothing to intercept

            // Matched on host as well as scheme because the hosted page does not
            // navigate to the URI it was given: `beadwrapper://callback/?…` comes
            // back as `https://beadwrapper//callback/?…`, with our scheme name
            // demoted to the host. On scheme alone that fell through to a normal
            // page load and died on DNS (ERR_NAME_NOT_RESOLVED), so the shopper's
            // exit surfaced an error panel and the POS got no result at all.
            // A private scheme makes a real host of the same name a non-risk.
            val isCallback = uri.scheme.equals(redirectScheme, ignoreCase = true) ||
                uri.host.equals(redirectScheme, ignoreCase = true)

            if (!isCallback) {
                Timber.tag(TAG).d(
                    "NOT the callback -> scheme=%s host=%s path=%s (expecting scheme or host=%s)",
                    uri.scheme, uri.host, uri.path, redirectScheme
                )
                return false
            }

            val id = uri.getQueryParameter("paymentId")
                ?: paymentId.ifBlank { uri.getQueryParameter("paymentPageId").orEmpty() }
            val statusCode = uri.getQueryParameter("statusCode") ?: "UNKNOWN"

            Timber.tag(TAG).i(
                "CALLBACK -> uri=%s host=%s path=%s paymentId=%s status=%s params=%s",
                uri, uri.host, uri.path, id, statusCode, uri.queryParameterNames
            )

            onRedirectIntercepted(id, uri.getQueryParameter("statusCode"))
            return true
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun WebView.configureSettings() = settings.run {
        javaScriptEnabled = true
        domStorageEnabled = true
        cacheMode = WebSettings.LOAD_DEFAULT
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            forceDark = WebSettings.FORCE_DARK_AUTO
        }
    }

    /* ── Exits ──────────────────────────────────────────────────── */

    /**
     * The page asked to close. Take the page down, keep watching the payment.
     *
     * Deliberately does **not** report the `statusCode` on the redirect as the
     * outcome. It is a client-side query parameter: the shopper's close control
     * sends none at all (which is where the old `UNKNOWN` came from), and
     * anything it does send is a claim by a web page about whether money moved.
     * The API is asked instead, and the hint is kept only as a last resort for
     * [finishAfterRedirectGrace].
     */
    private fun onRedirectIntercepted(id: String, statusHint: String?) {
        if (finished || redirectSeen) return

        if (id.isNotBlank()) paymentId = id
        redirectStatusHint = statusHint
        redirectDeadlineMs = SystemClock.elapsedRealtime() + REDIRECT_GRACE_MS

        Timber.tag(TAG).i(
            "Redirect intercepted (hint=%s). Closing the page and waiting up to %dms " +
                "for the API to report a real status.",
            statusHint ?: "(none)",
            REDIRECT_GRACE_MS
        )

        webView?.stopLoading()
        setContentView(finishingView())
    }

    /**
     * Called when the grace period expired with the payment still not in a
     * terminal state.
     *
     * A terminal hint from the redirect is used here and only here — after the
     * API has had its chance and declined to answer, an unverified claim beats
     * nothing. Anything else is reported as an error rather than guessed at: a
     * shopper who paid on a lagging backend must not come back as CANCELLED,
     * so "I do not know, go and look" is the only honest answer left.
     */
    private fun finishAfterRedirectGrace(lastStatus: String?) {
        val hint = redirectStatusHint?.uppercase(Locale.US)
        val hintResult = hint?.let { TERMINAL_STATUSES[it] }

        if (hint != null && hintResult != null) {
            Timber.tag(TAG).w(
                "Grace expired; falling back to the redirect's unverified status %s " +
                    "(API last said %s).",
                hint,
                lastStatus ?: "nothing"
            )
            finishWith(
                hintResult,
                paymentId.ifBlank { trackingId },
                hint,
                error = null,
                route = PayContract.ROUTE_REDIRECT_HINT
            )
            return
        }

        val last = lastStatus?.uppercase(Locale.US) ?: "UNKNOWN"
        Timber.tag(TAG).w(
            "Grace expired with no terminal status (API last said %s, redirect hint %s).",
            last,
            hint ?: "(none)"
        )
        finishWithError(
            "The payment page closed, but the payment was still \"$last\" after " +
                "${REDIRECT_GRACE_MS / 1000}s.\n\nThe shopper may still have paid - " +
                "check this payment in the Bead dashboard before retrying.",
            route = PayContract.ROUTE_TIMEOUT
        )
    }

    private fun cancel() {
        Timber.tag(TAG).i("Shopper cancelled the payment (paymentId=%s)", paymentId)
        finishWith(
            RESULT_CANCELED,
            paymentId,
            PayContract.STATUS_CANCELLED,
            error = null,
            route = PayContract.ROUTE_USER_CANCEL
        )
    }

    private fun finishWithError(
        message: String,
        route: String = PayContract.ROUTE_ERROR
    ) {
        finishWith(RESULT_CANCELED, paymentId, PayContract.STATUS_ERROR, message, route)
    }

    private fun finishWith(
        resultCode: Int,
        id: String,
        status: String,
        error: String?,
        route: String
    ) {
        if (finished) return
        finished = true
        pollJob?.cancel()
        Timber.tag(TAG).i("Exit: status=%s route=%s paymentId=%s", status, route, id)
        setResult(resultCode, PayContract.resultIntent(id, status, error, route))
        finish()
    }

    override fun onDestroy() {
        pollJob?.cancel()
        webView?.destroy()
        webView = null
        super.onDestroy()
    }
}
