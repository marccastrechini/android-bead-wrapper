package com.beadpay.wrapper

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** Temporary stub; real WebView logic will be added later. */
class CheckoutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No UI yet – just end immediately
        setResult(RESULT_CANCELED)
        finish()
    }
}
