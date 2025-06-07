package com.beadpay.wrapper.ui

import android.app.Activity
import android.content.Context
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

    private fun waitMillis(millis: Long): ViewAction = object : ViewAction {
        override fun getConstraints(): Matcher<View> = isRoot()
        override fun getDescription() = "wait for \ ms"
        override fun perform(uiController: UiController, view: View?) {
            uiController.loopMainThreadForAtLeast(millis)
        }
    }

    @Test
    fun hostedPageCallback_returnsResult() {
        val request = PaymentRequest(BigDecimal("1.00"), "USD", "valor")
        val context: Context = ApplicationProvider.getApplicationContext()

        val scenario = launch<CheckoutActivity>(
            PayContract.buildIntent(context, request)
        )

        onView(isRoot()).perform(waitMillis(TimeUnit.SECONDS.toMillis(15)))

        scenario.onActivity {
            assertEquals(Activity.RESULT_OK, it.resultCode)
        }
    }
}