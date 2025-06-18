package com.beadpay.wrapper.usecase

import com.beadpay.wrapper.model.Customer
import com.beadpay.wrapper.model.PaymentResponse
import com.beadpay.wrapper.repository.PaymentRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreatePaymentUseCase @Inject constructor(
    private val repo: PaymentRepository
) {

    /**
     * Wraps [PaymentRepository.createPayment] so the UI layer can invoke it with
     * strong, explicit parameters.
     *
     * @param amount      Total in **major units** (e.g. `BigDecimal(100.00)` for $100)
     * @param reference   Merchant-side order / invoice number
     * @param customer    Full customer object (email, address, …)
     */
    suspend operator fun invoke(
        amount: Double,
        reference: String = "ORDER123",
        customer: Customer
    ): PaymentResponse =
        repo.createPayment(amount, reference, customer)
}
