package com.twowork.core.net

import android.content.Context
import com.twowork.core.Config
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * A small persistent [CookieJar] backed by SharedPreferences. 2Work only ever
 * talks to a single host, so cookies are serialized with [Cookie.toString] and
 * reconstructed against the base URL — which round-trips the host-only
 * `__Host-2work_session` cookie (secure, path=/, no domain) correctly.
 */
class AppCookieJar(context: Context) : CookieJar {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val store = linkedMapOf<String, Cookie>()

    init {
        (prefs.getStringSet(KEY, emptySet()) ?: emptySet()).forEach { raw ->
            Cookie.parse(Config.BASE_HTTP_URL, raw)?.let { store[keyOf(it)] = it }
        }
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            if (cookie.value.isEmpty()) {
                store.remove(keyOf(cookie))
            } else {
                store[keyOf(cookie)] = cookie
            }
        }
        persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return store.values.filter { it.expiresAt > now && it.matches(url) }
    }

    @Synchronized
    fun hasSession(): Boolean {
        val now = System.currentTimeMillis()
        return store.values.any { it.name.contains("session") && it.expiresAt > now }
    }

    @Synchronized
    fun clear() {
        store.clear()
        prefs.edit().remove(KEY).apply()
    }

    private fun persist() {
        prefs.edit().putStringSet(KEY, store.values.map { it.toString() }.toSet()).apply()
    }

    private fun keyOf(cookie: Cookie): String = "${cookie.name}|${cookie.domain}|${cookie.path}"

    private companion object {
        const val PREFS = "twowork_cookies"
        const val KEY = "cookies"
    }
}
