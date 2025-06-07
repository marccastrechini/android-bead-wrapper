package com.beadpay.wrapper.contract

import android.content.Intent
import android.net.Uri
import com.beadpay.wrapper.model.PayRequest
import com.beadpay.wrapper.model.PayResult

object PayContract {
    const val ACTION_PAY       = "com.valor.intent.action.PAY"
    const val EXTRA_REQUEST    = "com.beadpay.wrapper.extra.PAY_REQUEST"

    private const val PARAM_STATUS_CODE = "statusCode"
    private const val PARAM_PAYMENT_ID  = "paymentId"
    private const val PARAM_ERROR_MSG   = "error"

    fun buildPayIntent(request: PayRequest) =
        Intent(ACTION_PAY).apply { putExtra(EXTRA_REQUEST, request) }

    fun parseResultUri(uri: Uri) = PayResult(
        statusCode   = uri.getQueryParameter(PARAM_STATUS_CODE)?.toInt() ?: -1,
        paymentId    = uri.getQueryParameter(PARAM_PAYMENT_ID),
        errorMessage = uri.getQueryParameter(PARAM_ERROR_MSG)
    )
}
