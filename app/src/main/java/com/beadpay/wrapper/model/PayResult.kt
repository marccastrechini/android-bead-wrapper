package com.beadpay.wrapper.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * What the wrapper hands back to the POS.
 *
 * @param status the API's `statusCode` for a real payment, or one of
 *   `PayContract.STATUS_ERROR` / `STATUS_CANCELLED` when the flow ended
 *   before one existed.
 * @param error human-readable detail, present only for `STATUS_ERROR`.
 */
@Parcelize
data class PayResult(
    val paymentId: String,
    val status: String,
    val error: String? = null,
    /**
     * How the wrapper learned the payment was over, as a
     * `PayContract.ROUTE_*` value. Diagnostic only — branch on [status].
     *
     * Trails the other fields and defaults to null so a POS built against the
     * three-field version still constructs one; it will read null here, and
     * `PayContract.EXTRA_EXIT_ROUTE` mirrors the same value as a plain string
     * for callers that keep no copy of this class at all.
     */
    val route: String? = null
) : Parcelable
