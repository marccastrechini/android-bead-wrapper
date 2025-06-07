package com.beadpay.wrapper.ui.payment

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import com.beadpay.wrapper.contract.PayContract
import com.beadpay.wrapper.model.PaymentResponse

/**
 * Displays the Bead Hosted Payment Page and listens for the callback URI:
 *
 *     beadwrapper://callback?paymentId=…&statusCode=…
 *
 * When detected, converts the query parameters into a [PaymentResponse],
 * returns it to the caller via `setResult(RESULT_OK, …)`, then finishes.
 */
class PaymentWebViewActivity : ComponentActivity() {

    companion object {
        /** Internal extra (wrapper-only) for the HPP URL to load. */
        const val EXTRA_HPP_URL = "hppUrl"

        private const val CALLBACK_SCHEME = "beadwrapper"
        private const val CALLBACK_PATH   = "/callback"
    }

    // ────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ────────────────────────────────────────────────────────────────────
    @SuppressLint("SetJavaScriptEnabled")   // HPP typically requires JS
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val hppUrl = intent.getStringExtra(EXTRA_HPP_URL)
            ?: error("PaymentWebViewActivity launched without \$EXTRA_HPP_URL")

        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ) = handleUrl(request.url)
            }
            loadUrl(hppUrl)
        }

        setContentView(webView)
    }

    // ────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────
    /** Intercepts the custom callback URL and finishes the Activity. */
    private fun handleUrl(uri: Uri): Boolean {
        if (uri.scheme == CALLBACK_SCHEME && uri.path == CALLBACK_PATH) {

            val paymentId  = uri.getQueryParameter("paymentId") ?: ""
            val statusCode = uri.getQueryParameter("statusCode") ?: "UNKNOWN"

            val response = PaymentResponse(
                id = paymentId,
                hostedPaymentPageUrl = "", // HPP flow is completed
                expiresAt = "",
                status = statusCode
            )

            setResult(
                RESULT_OK,
                Intent().putExtra(PayContract.EXTRA_RESULT, response)
            )
            finish()
            return true   // prevent WebView from loading the URL
        }
        return false
    }
}
