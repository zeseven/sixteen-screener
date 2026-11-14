package com.sixteenscreener.app.domain.model

/**
 * RFC 9457 problem details returned by the backend for a failed request.
 */
data class ProblemDetails(
    /** Problem type URI. */
    val type: String?,
    /** Short summary of the problem. */
    val title: String,
    /** HTTP status code. */
    val status: Int,
    /** Explanation specific to this occurrence, suitable to show to the user. */
    val detail: String?,
)

/**
 * Reason a backend call failed.
 */
sealed interface ApiError {
    /** No connection, connection lost or timeout. */
    data class Network(val isTimeout: Boolean) : ApiError

    /** The request was not authorized: no ID token could be obtained or the backend rejected it. */
    data object Unauthorized : ApiError

    /** Profile, company or source not found (`404`). */
    data class NotFound(val problem: ProblemDetails?) : ApiError

    /** Any other unsuccessful HTTP status, with the problem details when the backend sent them. */
    data class Http(val status: Int, val problem: ProblemDetails?) : ApiError

    /** The response body does not match the API specification. */
    data class Parse(val message: String?) : ApiError
}

/**
 * Result of a backend call.
 */
sealed interface ApiResult<out T> {
    data class Success<out T>(val value: T) : ApiResult<T>

    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
}

fun <T> ApiResult<T>.getOrNull(): T? = (this as? ApiResult.Success)?.value
