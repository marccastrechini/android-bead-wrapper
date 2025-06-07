package com.beadpay.wrapper.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedPrefsTest {

    @Test
    fun putAndReadToken() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs   = EncryptedPrefs(context)

        prefs.accessToken = "abc123"
        assertEquals("abc123", prefs.accessToken)
    }
}