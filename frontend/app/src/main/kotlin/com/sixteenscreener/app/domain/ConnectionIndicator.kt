package com.sixteenscreener.app.domain

import com.sixteenscreener.app.domain.model.CallResult
import com.sixteenscreener.app.domain.model.SourceCallOutcome

/**
 * Connection indicator of a source from the outcome of its last call.
 */
object ConnectionIndicator {
    const val TIMEOUT = "T"
    const val APP_ERROR = "NPE"

    /** Empty for a successful call or a source never called, the HTTP status code, [TIMEOUT] or [APP_ERROR] otherwise. */
    fun of(lastCall: SourceCallOutcome?): String = when (lastCall?.result) {
        null, CallResult.SUCCESS, CallResult.UNKNOWN -> ""
        CallResult.HTTP_ERROR -> lastCall.httpStatus?.toString().orEmpty()
        CallResult.TIMEOUT -> TIMEOUT
        CallResult.APP_ERROR -> APP_ERROR
    }
}
