package com.beadpay.wrapper.repository

import com.beadpay.wrapper.model.PaymentRequest
import com.beadpay.wrapper.network.PaymentsApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class PaymentRepository @Inject constructor(
    private val paymentsApi: PaymentsApi,
    private val authRepository: AuthRepository
) {
    suspend fun createPayment(req: PaymentRequest) = withContext(Dispatchers.IO) {
        val token = authRepository.requireAccessToken()
        paymentsApi.createPayment("Bearer $token", req)
    }
}
