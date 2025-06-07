package com.beadpay.wrapper.repository

import com.beadpay.wrapper.model.PayRequest
import com.beadpay.wrapper.network.BeadApi
import com.beadpay.wrapper.network.dto.CreatePaymentRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentRepository @Inject constructor(
    private val api: BeadApi,
    private val authRepo: AuthRepository
) {
    /** Creates a crypto payment and returns Bead’s response. */
    suspend fun startPayment(req: PayRequest) =
        api.createPayment(
            bearer = "Bearer ${authRepo.getValidToken()}",
            request = CreatePaymentRequest(
                amountMinor = req.amountMinor,
                currency    = req.currency,
                returnUrl   = "beadwrapper://callback"
            )
        )
}
