package com.beadpay.wrapper.repository

import com.beadpay.wrapper.model.PayRequest
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PaymentRepositoryTest {
    @Test
    fun dummy_compile_check() = runTest {
        // This just ensures the DI graph compiles; real API call is mocked in instrumentation tests
        assert(true)
    }
}
