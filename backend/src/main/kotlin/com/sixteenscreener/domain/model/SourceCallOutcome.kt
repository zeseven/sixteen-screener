package com.sixteenscreener.domain.model

import java.time.Instant

/**
 * Classification of a call to a source (ADR-028).
 */
enum class SourceCallResult {
    /** Response in 2XX. */
    SUCCESS,

    /** Response outside 2XX. */
    HTTP_ERROR,

    /** No response received (timeout or connection failure). */
    TIMEOUT,

    /** Unexpected application error while calling the source or processing its response. */
    APP_ERROR,
}

/**
 * Outcome of one call to a source, shown as the connection indicator of the Sources screen.
 *
 * @property result classification of the call
 * @property httpStatus response status code; set for [SourceCallResult.HTTP_ERROR] only
 * @property at when the call finished
 */
data class SourceCallOutcome(
    val result: SourceCallResult,
    val httpStatus: Int?,
    val at: Instant,
) {
    init {
        require((result == SourceCallResult.HTTP_ERROR) == (httpStatus != null)) {
            "httpStatus must be set for HTTP_ERROR and only for it"
        }
    }

    /** Whether the source answered `429 Too Many Requests`. */
    val isRateLimited: Boolean get() = httpStatus == HTTP_TOO_MANY_REQUESTS

    companion object {
        private const val HTTP_TOO_MANY_REQUESTS = 429

        /** Successful call finished at [at]. */
        fun success(at: Instant) = SourceCallOutcome(SourceCallResult.SUCCESS, null, at)

        /** Call answered with [status] outside 2XX at [at]. */
        fun httpError(status: Int, at: Instant) = SourceCallOutcome(SourceCallResult.HTTP_ERROR, status, at)

        /** Call that received no response, detected at [at]. */
        fun timeout(at: Instant) = SourceCallOutcome(SourceCallResult.TIMEOUT, null, at)

        /** Call that failed with an application error at [at]. */
        fun appError(at: Instant) = SourceCallOutcome(SourceCallResult.APP_ERROR, null, at)
    }
}
