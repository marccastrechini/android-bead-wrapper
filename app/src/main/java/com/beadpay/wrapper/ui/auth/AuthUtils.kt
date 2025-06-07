package com.beadpay.wrapper.ui.auth

import android.content.Context
import android.content.Intent

/** Stub helpers; we’ll wire real AppAuth later. */
fun buildAuthIntent(context: Context): Intent =
    Intent()   // TODO replace with AuthorizationService.getAuthorizationRequestIntent(...)

fun handleRedirect(intent: Intent) {
    // TODO parse intent.data for auth response / error and dispatch accordingly
}
