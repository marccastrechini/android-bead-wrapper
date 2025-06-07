package com.beadpay.wrapper.ui.payment

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

/**
 * Loads the Hosted Payment Page and listens for beadwrapper://callback redirects.
 *
 * Expected Intent extras:
 *   - "url" (String): full HPP URL to load.
 */
class PaymentWebViewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL         = "url"
        const val EXTRA_CALLBACK_URL = "callbackUrl"
        const val CUSTOM_SCHEME     = "beadwrapper"
    }

    @SuppressLint("SetJavaScriptEnabled") // HPP usually needs JS enabled
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val startUrl = intent.getStringExtra(EXTRA_URL)
            ?: error("Missing / in intent")

        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    if (Uri.parse(url).scheme == CUSTOM_SCHEME) {
                        setResult(RESULT_OK, Intent().putExtra(EXTRA_CALLBACK_URL, url))
                        finish()
                        return true   // we handled it
                    }
                    return false       // let WebView load it
                }
            }
            loadUrl(startUrl)
        }

        setContentView(webView)
    }
}