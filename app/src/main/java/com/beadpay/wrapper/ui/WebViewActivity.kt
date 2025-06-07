package com.beadpay.wrapper.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import com.beadpay.wrapper.contract.PayContract

private const val EXTRA_HOSTED_URL = "hosted_url"

class WebViewActivity : ComponentActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra(EXTRA_HOSTED_URL)
            ?: error("Missing hosted_url")

        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return false
                    if (uri.scheme == "beadwrapper") {
                        // Parse callback & finish
                        val result = PayContract.parseResultUri(uri)
                        setResult(RESULT_OK, intent.apply {
                            data = Uri.parse(uri.toString())
                            putExtra("PAY_RESULT", result)
                        })
                        finish()
                        return true
                    }
                    return false
                }
            }
        }

        setContentView(webView)
        webView.loadUrl(url)
    }

    companion object {
        fun newIntent(context: android.content.Context, hostedUrl: String) =
            android.content.Intent(context, WebViewActivity::class.java).apply {
                putExtra(EXTRA_HOSTED_URL, hostedUrl)
            }
    }
}
