package com.beadpay.wrapper.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.beadpay.wrapper.repository.AuthRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

// helper extensions / functions
import com.beadpay.wrapper.ui.auth.buildAuthIntent
import com.beadpay.wrapper.ui.auth.handleRedirect

@AndroidEntryPoint
class AuthActivity : ComponentActivity() {

    @Inject lateinit var authRepo: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Launch the browser-based PKCE flow.
        // NOTE: For SDK 35 no change is required here.
        startActivity(buildAuthIntent(this))
    }

    /**
     * Called when the custom-scheme redirect brings us back from the browser.
     * Starting with Activity 1.9 (SDK 34+) the parameter is @NonNull, so the
     * signature must use a non-nullable Intent.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        lifecycleScope.launch {
            handleRedirect(intent)   // Intent is guaranteed non-null
            finish()
        }
    }
}
