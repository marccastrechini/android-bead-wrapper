package com.beadpay.wrapper.model

import android.os.Parcelable
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize

/**
 * Mirrors the “customer” block in the /payments/crypto request.
 *
 * `demo()` returns a hard-coded sample instance used in debug / sandbox
 * builds until the POS passes real shopper data.
 */
@Parcelize
@JsonClass(generateAdapter = true)
data class Customer(
    val email:       String,
    val firstName:   String,
    val lastName:    String,
    val address:     String,
    val address2:    String? = null,
    val city:        String,
    val state:       String,
    val countryCode: String,
    val postalCode:  String
) : Parcelable {

    companion object {
        /** Debug-only stub customer. Replace once POS supplies details. */
        fun demo() = Customer(
            email       = "shopper@example.com",
            firstName   = "Jane",
            lastName    = "Doe",
            address     = "123 Main St",
            address2    = "Suite 4B",
            city        = "Boston",
            state       = "MA",
            countryCode = "US",
            postalCode  = "02108"
        )
    }
}
