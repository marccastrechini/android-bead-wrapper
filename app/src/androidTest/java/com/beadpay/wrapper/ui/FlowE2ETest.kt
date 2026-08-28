package com.beadpay.wrapper.ui

import android.app.Instrumentation
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.beadpay.wrapper.config.BeadConfig
import com.beadpay.wrapper.contract.PayContract
import com.beadpay.wrapper.ui.checkout.CheckoutActivity
import com.beadpay.wrapper.ui.payment.PaymentWebViewActivity
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Live smoke test: POS intent → authenticated payment creation → hosted page.
 *
 * Reaching [PaymentWebViewActivity] is the strongest assertion available
 * without a human, and it covers the whole chain that actually breaks —
 * API-key auth, the request body the API accepts, and parsing `paymentUrls`
 * out of the response. Asserting a *completed* payment is not possible here:
 * that requires someone to scan the QR and pay.
 *
 * Hits the sandbox and creates a real payment on each run. Skips rather than
 * fails when credentials are absent, so a checkout without `local.properties`
 * (CI, a fresh clone) stays green.
 */
@RunWith(AndroidJUnit4::class)
class FlowE2ETest {

    @Test
    fun posIntent_createsPayment_andOpensHostedPage() {
        assumeTrue(
            "Terminal credentials missing (${BeadConfig.missing.joinToString()}) — skipping live flow",
            BeadConfig.isValid
        )

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor: Instrumentation.ActivityMonitor =
            instrumentation.addMonitor(PaymentWebViewActivity::class.java.name, null, false)

        val context: Context = ApplicationProvider.getApplicationContext()
        val scenario = ActivityScenario.launch<CheckoutActivity>(
            PayContract.buildIntent(context, AMOUNT)
        )

        try {
            val hostedPage = instrumentation.waitForMonitorWithTimeout(monitor, TIMEOUT_MS)

            assertNotNull(
                "Hosted payment page did not open within ${TIMEOUT_MS}ms — " +
                    "check logcat for the API response",
                hostedPage
            )

            // Stop the status-polling loop rather than leaving it running.
            instrumentation.runOnMainSync { hostedPage.finish() }
        } finally {
            // CheckoutActivity forwards the hosted page's result and finishes
            // itself, so by now the scenario often has no activity left to
            // close and close() would throw. Closing is still needed on the
            // failure path, where CheckoutActivity is very much alive.
            runCatching { scenario.close() }
            instrumentation.removeMonitor(monitor)
        }
    }

    private companion object {
        const val AMOUNT     = 25.0
        const val TIMEOUT_MS = 30_000L
    }
}
