package com.beadpay.wrapper.ui.payment

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import com.beadpay.wrapper.BuildConfig
import com.beadpay.wrapper.contract.PayContract
import com.beadpay.wrapper.model.PayResult

class PaymentWebViewActivity : ComponentActivity() {

    companion object {
        const val EXTRA_HPP_URL = "extra_hpp_url"
        private const val CALLBACK_SCHEME = "beadwrapper"
        private const val CALLBACK_PATH = "/callback"
        private const val TAG = "PaymentWebView"

        fun launch(context: Context, hppUrl: String) {
            val i = Intent(context, PaymentWebViewActivity::class.java)
                .putExtra(EXTRA_HPP_URL, hppUrl)
            context.startActivity(i)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        val hppUrl = intent.getStringExtra(EXTRA_HPP_URL)
            ?: error("PaymentWebViewActivity launched without $EXTRA_HPP_URL")

        val webView = WebView(this).apply {
            configureSettings()
            webViewClient = BeadWebClient()
            loadUrl(hppUrl)
        }

        setContentView(webView)
    }

    private inner class BeadWebClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            Log.d(TAG, "Intercepted request URL → ${request.url}")
            return handleUrl(request.url)
        }

        @Suppress("OverridingDeprecatedMember", "DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            Log.d(TAG, "Intercepted legacy URL → $url")
            return handleUrl(Uri.parse(url))
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            Log.d(TAG, "Page STARTED → $url")
        }

        override fun onPageFinished(view: WebView, url: String) {
            Log.d(TAG, "Page FINISHED → $url")

            view.evaluateJavascript(
                "(function() { return document.body.innerText; })();"
            ) { bodyText ->
                Log.d(TAG, "Page content: ${bodyText.take(100)}") // preview first 100 chars

                if (bodyText.contains("complete", ignoreCase = true) ||
                    bodyText.contains("payment successful", ignoreCase = true)) {

                    Log.i(TAG, "✅ Transaction complete detected in page content.")

                    val result = PayResult(paymentId = "unknown", status = "SUCCESS_FROM_PAGE")

                    setResult(
                        RESULT_OK,
                        Intent().putExtra(PayContract.EXTRA_RESULT, result)
                    )
                    finish()
                }
            }
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            Log.e(TAG, "ERROR ${error.errorCode} on ${request.url} : ${error.description}")
        }

        private fun handleUrl(uri: Uri): Boolean {
            if (uri.scheme == CALLBACK_SCHEME && uri.path == CALLBACK_PATH) {
                val paymentId = uri.getQueryParameter("paymentId") ?: ""
                val statusCode = uri.getQueryParameter("statusCode") ?: "UNKNOWN"

                Log.i(TAG, "Transaction complete → paymentId=$paymentId, status=$statusCode")

                val result = PayResult(paymentId = paymentId, status = statusCode)

                setResult(
                    RESULT_OK,
                    Intent().putExtra(PayContract.EXTRA_RESULT, result)
                )
                finish()
                return true
            }
            return false
        }
    }

    private fun WebView.configureSettings() = settings.run {
        javaScriptEnabled = true
        domStorageEnabled = true
        cacheMode = WebSettings.LOAD_DEFAULT
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            forceDark = WebSettings.FORCE_DARK_AUTO
        }
    }
}
