package com.twowork.app

import androidx.activity.ComponentActivity
import com.razorpay.Checkout
import com.twowork.core.model.RazorpayOrder
import org.json.JSONObject

object RazorpayBridge {
    private var pending: ((success: Boolean, paymentIdOrError: String?) -> Unit)? = null

    fun launch(
        activity: ComponentActivity,
        order: RazorpayOrder,
        description: String,
        callback: (success: Boolean, paymentIdOrError: String?) -> Unit
    ) {
        pending = callback
        val co = Checkout()
        co.setKeyID(order.keyId)
        val opts = JSONObject().apply {
            put("name", "2Work")
            put("description", description)
            put("order_id", order.orderId)
            put("amount", order.amount)
            put("currency", order.currency)
        }
        co.open(activity, opts)
    }

    internal fun onSuccess(paymentId: String?) {
        val cb = pending; pending = null
        cb?.invoke(true, paymentId)
    }

    internal fun onError(code: Int, description: String?) {
        val cb = pending; pending = null
        cb?.invoke(false, description ?: "Payment cancelled (code $code)")
    }
}
