package com.beadpay.wrapper.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AuthTokenResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Int,
    val scope: String? = null
)
