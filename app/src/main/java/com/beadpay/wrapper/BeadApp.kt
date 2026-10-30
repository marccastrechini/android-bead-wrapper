package com.beadpay.wrapper

import android.app.Application
import android.os.Build
import android.os.StrictMode
import android.webkit.WebView
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class BeadApp : Application() {

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            // WebView & Timber debug helpers
            WebView.setWebContentsDebuggingEnabled(true)
            Timber.plant(Timber.DebugTree())

            // Optional: enable StrictMode to catch accidental disk/network access on main thread
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
        }
    }
}
