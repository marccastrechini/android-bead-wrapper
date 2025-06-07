package com.beadpay.wrapper.usecase

import com.beadpay.wrapper.model.PaymentRequest
import com.beadpay.wrapper.repository.PaymentRepository
import javax.inject.Inject

class CreatePaymentUseCase @Inject constructor(
    private val repo: PaymentRepository
) {
    /** Creates a crypto payment and returns the API response. */
    suspend operator fun invoke(request: PaymentRequest) =
        repo.createPayment(request)
}