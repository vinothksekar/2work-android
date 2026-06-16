package com.twowork.core

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Single source of truth for talking to the live 2Work API.
 *
 * The backend's CSRF defense for non-GET /api requests rejects cross-site
 * `Sec-Fetch-Site` and any `Origin` that is not the public origin. A native
 * client simply sends `Origin: https://2work.in` (matching the public origin)
 * and omits `Sec-Fetch-Site` — which the guard treats as same-origin. No
 * double-submit token is required. The session is a host-only `__Host-` cookie.
 */
object Config {
    const val BASE_URL = "https://2work.in/"
    const val ORIGIN = "https://2work.in"

    val BASE_HTTP_URL: HttpUrl = BASE_URL.toHttpUrl()
}
