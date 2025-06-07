package com.beadpay.wrapper.ui

import android.app.Activity
import android.content.Intent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.beadpay.wrapper.contract.PayContract
import com.beadpay.wrapper.model.PaymentRequest
import com.beadpay.wrapper.ui.checkout.CheckoutActivity
import org.hamcrest.Matcher
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class FlowE2ETest {

    /** Simple Espresso ViewAction that blocks the test thread for [millis] milliseconds. */
    private fun waitMillis(millis: Long): ViewAction = object : ViewAction {
        override fun getConstraints(): Matcher<View> = isRoot()
        override fun getDescription(): String = "wait for \ millis"
        override fun perform(uiController: UiController, view: View?) {
            uiController.loopMainThreadForAtLeast(millis)
        }
    }

    @Test
    fun hostedPageCallback_returnsResult() {
        // 1. Build a dummy PaymentRequest
        val request = PaymentRequest(BigDecimal("1.00"), "USD", "valor")

        // 2. Launch CheckoutActivity via the public PayContract
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val scenario = launch<CheckoutActivity>(
            PayContract.buildIntent(context, request)
        )

        // 3. Wait up to 15 s for the flow to complete
        onView(isRoot()).perform(waitMillis(TimeUnit.SECONDS.toMillis(15)))

        // 4. Assert the Activity finished with RESULT_OK
        scenario.onActivity {
            assertEquals(Activity.RESULT_OK, it.resultCode)
        }
    }
}