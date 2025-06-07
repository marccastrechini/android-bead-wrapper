package com.beadpay.wrapper.network

import com.beadpay.wrapper.network.dto.CreatePaymentResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class CreatePaymentResponseParseTest {

    private val sampleJson = """
        {
          "payment_id":  "abc-123",
          "hosted_url":  "https://sandbox.beadpay.com/pay/abc-123",
          "tracking_id": "xyz-789",
          "payment_urls": { "url": "https://merchant.example/callback" }
        }
    """.trimIndent()

    @Test
    fun parse_payment_response_json() {
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())   // ← reflection-friendly adapter
            .build()

        val adapter = moshi.adapter(CreatePaymentResponse::class.java)
        val rsp     = adapter.fromJson(sampleJson)!!

        assertEquals("abc-123", rsp.paymentId)
        assertEquals(
            "https://sandbox.beadpay.com/pay/abc-123",
            rsp.hostedUrl
        )
        assertEquals("xyz-789", rsp.trackingId)
        assertEquals(
            "https://merchant.example/callback",
            rsp.paymentUrls?.url
        )
    }
}
