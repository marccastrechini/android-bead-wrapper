package com.beadpay.wrapper.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.beadpay.wrapper.repository.AuthRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

// explicit imports for the helpers
import com.beadpay.wrapper.ui.auth.buildAuthIntent
import com.beadpay.wrapper.ui.auth.handleRedirect

@AndroidEntryPoint
class AuthActivity : ComponentActivity() {

    @Inject lateinit var authRepo: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Launch the browser-based PKCE flow
        startActivity(buildAuthIntent(this))
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        lifecycleScope.launch {
            intent?.let { handleRedirect(it) }   // handle the custom-scheme redirect
            finish()
        }
    }
}
