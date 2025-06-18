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
import androidx.lifecycle.lifecycleScope
import com.beadpay.wrapper.BuildConfig
import com.beadpay.wrapper.contract.PayContract
import com.beadpay.wrapper.model.PayResult
import com.beadpay.wrapper.network.PaymentsApi
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PaymentWebViewActivity : ComponentActivity() {

    @Inject lateinit var paymentsApi: PaymentsApi

    companion object {
        const val EXTRA_HPP_URL = "extra_hpp_url"
        const val EXTRA_TRACKING_ID = "tracking_id"
        private const val CALLBACK_SCHEME = "beadwrapper"
        private const val CALLBACK_PATH = "/callback"
        private const val TAG = "PaymentWebView"

        fun launch(context: Context, hppUrl: String, trackingId: String) {
            val i = Intent(context, PaymentWebViewActivity::class.java)
                .putExtra(EXTRA_HPP_URL, hppUrl)
                .putExtra(EXTRA_TRACKING_ID, trackingId)
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

        val trackingId = intent.getStringExtra(EXTRA_TRACKING_ID)
            ?: return showErrorDialog("Missing tracking ID")

        val webView = WebView(this).apply {
            configureSettings()
            webViewClient = BeadWebClient()
            loadUrl(hppUrl)
        }

        setContentView(webView)

        lifecycleScope.launch {
            pollPaymentStatus(trackingId)
        }
    }

    private suspend fun pollPaymentStatus(trackingId: String) {
        var lastStatus: String? = null

        while (true) {
            try {
                val response = paymentsApi.getPaymentStatus(trackingId)
                val status = response.statusCode

                if (status != lastStatus) {
                    Log.i(TAG, "🟢 Payment status changed: $status")
                    lastStatus = status
                } else {
                    Log.d(TAG, "Status unchanged: $status")
                }

                if (status.equals("COMPLETED", ignoreCase = true) ||
                    status.equals("FAILED", ignoreCase = true)) {

                    Log.i(TAG, "🎯 Final status reached: $status — finishing activity.")
                    val result = PayResult(paymentId = trackingId, status = status)
                    setResult(RESULT_OK, Intent().putExtra(PayContract.EXTRA_RESULT, result))
                    finish()
                    break
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error polling payment status", e)
                showErrorDialog("Failed to check payment status.\n${e.localizedMessage}")
                break
            }

            delay(2000)
        }
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
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            Log.e(TAG, "ERROR ${error.errorCode} on ${request.url} : ${error.description}")
            showErrorDialog("Page load error: ${error.description}")
        }

        private fun handleUrl(uri: Uri): Boolean {
            if (uri.scheme == CALLBACK_SCHEME && uri.path == CALLBACK_PATH) {
                val paymentId = uri.getQueryParameter("paymentId") ?: ""
                val statusCode = uri.getQueryParameter("statusCode") ?: "UNKNOWN"

                Log.i(TAG, "Transaction complete → paymentId=$paymentId, status=$statusCode")

                val result = PayResult(paymentId = paymentId, status = statusCode)
                setResult(RESULT_OK, Intent().putExtra(PayContract.EXTRA_RESULT, result))
                finish()
                return true
            }
            return false
        }
    }

    private fun showErrorDialog(message: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Payment Error")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun showErrorAndFinish(message: String) {
        Log.e(TAG, "ERROR: $message")
        showErrorDialog(message)
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
