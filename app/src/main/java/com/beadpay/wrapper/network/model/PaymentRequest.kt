package com.beadpay.wrapper.network.model

data class PaymentRequest(
    val terminalId: String,
    val merchantId: String,
    val requestedAmount: Double,
    val paymentUrlType: String = "web",
    val reference: String,
    val description: String? = null,
    val redirectUrl: String,
    val emailReceipt: Boolean = false,
    val smsReceipt: Boolean = false
)
