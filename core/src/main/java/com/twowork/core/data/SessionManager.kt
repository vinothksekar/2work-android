package com.twowork.core.data

import com.twowork.core.model.*
import com.twowork.core.net.ApiResult
import com.twowork.core.net.AppCookieJar
import com.twowork.core.net.TwoWorkApi
import com.twowork.core.net.safeApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Holds the authenticated user and drives auth against the cookie session. */
class SessionManager(
    private val api: TwoWorkApi,
    private val cookieJar: AppCookieJar
) {
    private val _me = MutableStateFlow<User?>(null)
    val me: StateFlow<User?> = _me.asStateFlow()

    private val _initialized = MutableStateFlow(false)
    val initialized: StateFlow<Boolean> = _initialized.asStateFlow()

    suspend fun bootstrap(): User? {
        if (cookieJar.hasSession()) {
            (safeApi { api.me() } as? ApiResult.Ok)?.let { _me.value = it.data.user }
        }
        _initialized.value = true
        return _me.value
    }

    suspend fun refresh() {
        (safeApi { api.me() } as? ApiResult.Ok)?.let { _me.value = it.data.user }
    }

    suspend fun login(email: String, password: String, totpCode: String?): ApiResult<User> {
        return when (val result = safeApi { api.login(LoginRequest(email.trim(), password, totpCode?.ifBlank { null })) }) {
            is ApiResult.Ok -> when (val who = safeApi { api.me() }) {
                is ApiResult.Ok -> {
                    val user = who.data.user
                    if (user != null) {
                        _me.value = user
                        ApiResult.Ok(user)
                    } else {
                        ApiResult.Err(401, "Sign-in did not establish a session.")
                    }
                }
                is ApiResult.Err -> who
            }
            is ApiResult.Err -> result
        }
    }

    suspend fun register(email: String, password: String, fullName: String, role: String): ApiResult<RegisterResponse> =
        safeApi { api.register(RegisterRequest(email.trim(), password, fullName.trim(), role)) }

    suspend fun forgotPassword(email: String): ApiResult<MessageResponse> =
        safeApi { api.forgotPassword(EmailRequest(email.trim())) }

    suspend fun verifyEmail(token: String): ApiResult<MessageResponse> =
        safeApi { api.verifyEmail(TokenRequest(token.trim())) }

    suspend fun resendVerification(email: String): ApiResult<MessageResponse> =
        safeApi { api.resendVerification(EmailRequest(email.trim())) }

    suspend fun logout() {
        safeApi { api.logout() }
        cookieJar.clear()
        _me.value = null
    }
}
