package com.beadpay.wrapper.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Thin wrapper around EncryptedSharedPreferences.
 */
class EncryptedPrefs(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "bead_wrapper.secure_prefs",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_ACCESS_TOKEN) else putString(KEY_ACCESS_TOKEN, value)
        }.apply()

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_REFRESH_TOKEN) else putString(KEY_REFRESH_TOKEN, value)
        }.apply()

    var idToken: String?
        get() = prefs.getString(KEY_ID_TOKEN, null)
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_ID_TOKEN) else putString(KEY_ID_TOKEN, value)
        }.apply()

    private companion object {
        const val KEY_ACCESS_TOKEN  = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_ID_TOKEN      = "id_token"
    }
}
