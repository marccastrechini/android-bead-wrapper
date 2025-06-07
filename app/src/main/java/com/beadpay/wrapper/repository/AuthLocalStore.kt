package com.beadpay.wrapper.repository

import com.beadpay.wrapper.storage.EncryptedPrefs
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around EncryptedPrefs that the rest of the app can depend on.
 */
@Singleton
class AuthLocalStore @Inject constructor(
    private val prefs: EncryptedPrefs
) {
    var accessToken: String?
        get() = prefs.accessToken
        set(value) { prefs.accessToken = value }
}