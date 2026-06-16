package com.twowork.core.net

import com.twowork.core.Config
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Satisfies the 2Work backend's same-origin guard for a native client: every
 * request carries `Origin: https://2work.in` (which equals the public origin,
 * so mutating requests pass) plus `Referer` and a JSON `Accept`. No CSRF token
 * is required by this backend.
 */
class SessionInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Origin", Config.ORIGIN)
            .header("Referer", "${Config.ORIGIN}/")
            .header("Accept", "application/json")
            .build()
        return chain.proceed(request)
    }
}
