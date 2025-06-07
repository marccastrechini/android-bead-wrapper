package com.beadpay.wrapper.repository

import javax.inject.Inject
import javax.inject.Singleton

/** Very thin in-memory token cache; replace with EncryptedSharedPrefs later. */
@Singleton
class AuthLocalStore @Inject constructor() {
    var accessToken: String? = null
        private set

    fun save(token: String)  { accessToken = token }
    fun clear()              { accessToken = null  }
}
