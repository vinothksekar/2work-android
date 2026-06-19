package com.twowork.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.razorpay.PaymentResultListener
import com.twowork.app.ui.AppRoot
import com.twowork.core.di.LocalGraph
import com.twowork.core.ui.TwoWorkTheme

class MainActivity : ComponentActivity(), PaymentResultListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = (application as TwoWorkApp).graph
        setContent {
            TwoWorkTheme {
                CompositionLocalProvider(LocalGraph provides graph) {
                    AppRoot()
                }
            }
        }
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?) {
        RazorpayBridge.onSuccess(razorpayPaymentId)
    }

    override fun onPaymentError(code: Int, description: String?) {
        RazorpayBridge.onError(code, description)
    }
}
