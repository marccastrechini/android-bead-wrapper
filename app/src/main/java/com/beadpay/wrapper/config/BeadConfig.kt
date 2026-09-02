package com.beadpay.wrapper.config

import com.beadpay.wrapper.BuildConfig

/**
 * Terminal credentials, validated once at startup.
 *
 * All three values come from `local.properties` (git-ignored) via
 * `BuildConfig`, and all three must belong to the *same* terminal — the API
 * key is issued per terminal per environment. A key that authenticates but is
 * paired with another terminal's ids yields a bare `403 Forbidden` with no
 * field-level detail, which is why [summary] exists.
 */
object BeadConfig {

    val apiKey: String     = BuildConfig.BEAD_API_KEY
    val merchantId: String = BuildConfig.MERCHANT_ID
    val terminalId: String = BuildConfig.TERMINAL_ID

    /**
     * Where the hosted page sends the shopper when the flow ends or is closed.
     *
     * This is the close mechanism for a native WebView host. The hosted page
     * only offers a bridge-based close to React Native hosts (via the injected
     * `window.ReactNativeWebView`); with no bridge and no redirectUrl it has
     * nowhere to go and renders no close control at all.
     */
    val redirectUrl: String = BuildConfig.REDIRECT_URL

    /**
     * Scheme of [url], which is what the WebView matches on navigation.
     *
     * Blank for a blank url — a caller that asked for no redirectUrl has no
     * scheme to intercept, and the hosted page will render no close control.
     */
    fun schemeOf(url: String): String =
        url.substringBefore("://", missingDelimiterValue = "").trim()

    /** Scheme of the build-default [redirectUrl]. */
    val redirectScheme: String get() = schemeOf(redirectUrl)

    /** Names of any values missing from `local.properties`. */
    val missing: List<String> = buildList {
        if (apiKey.isBlank())     add("BEAD_API_KEY")
        if (merchantId.isBlank()) add("MERCHANT_ID")
        if (terminalId.isBlank()) add("TERMINAL_ID")
    }

    val isValid: Boolean get() = missing.isEmpty()

    /**
     * Human-readable identity for error dialogs and logs.
     *
     * The API key is reduced to its last four characters — enough to tell two
     * keys apart when diagnosing a 403, never enough to authenticate with.
     */
    fun summary(): String = buildString {
        append("terminalId: ").append(terminalId.ifBlank { "(missing)" }).append('\n')
        append("merchantId: ").append(merchantId.ifBlank { "(missing)" }).append('\n')
        append("apiKey: ").append(maskedApiKey())
    }

    private fun maskedApiKey(): String = when {
        apiKey.isBlank()      -> "(missing)"
        apiKey.length <= 4    -> "**** (too short — is this the real key?)"
        else                  -> "****" + apiKey.takeLast(4)
    }
}
