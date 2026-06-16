package com.twowork.core.net

import com.twowork.core.model.ApiError
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/** A minimal success/error result so the UI never has to catch exceptions itself. */
sealed interface ApiResult<out T> {
    data class Ok<T>(val data: T) : ApiResult<T>
    data class Err(val code: Int, val message: String) : ApiResult<Nothing>
}

val ApiResult<*>.isOk: Boolean get() = this is ApiResult.Ok

private val errorJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** Runs an API call and maps transport/HTTP failures into [ApiResult.Err]. */
suspend fun <T> safeApi(block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Ok(block())
} catch (e: HttpException) {
    ApiResult.Err(e.code(), httpMessage(e))
} catch (e: IOException) {
    ApiResult.Err(0, "Network unavailable. Check your connection.")
} catch (e: Exception) {
    ApiResult.Err(-1, e.message ?: "Unexpected error")
}

private fun httpMessage(e: HttpException): String {
    val raw = try {
        e.response()?.errorBody()?.string()
    } catch (_: Exception) {
        null
    }
    if (!raw.isNullOrBlank()) {
        runCatching { errorJson.decodeFromString(ApiError.serializer(), raw) }
            .getOrNull()?.let { return it.error }
    }
    return when (e.code()) {
        401 -> "Please sign in to continue."
        403 -> "You don't have access to that."
        404 -> "Not found."
        409 -> "That action conflicts with the current state."
        else -> "Request failed (${e.code()})."
    }
}
