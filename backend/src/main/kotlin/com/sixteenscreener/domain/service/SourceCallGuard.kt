package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult

class SourceCallGuard(private val rateLimiter: RateLimiter) {

    suspend fun <T> single(
        source: SourceId,
        recorder: SourceCallRecorder,
        call: suspend () -> SourceResult<T>,
    ): SourceResult<T> =
        if (rateLimiter.tryAcquire(source, 1) == 0) {
            SourceResult.LimitReached(outcome = null)
        } else {
            completed(source, recorder, call())
        }

    suspend fun <T> completed(source: SourceId, recorder: SourceCallRecorder, result: SourceResult<T>): SourceResult<T> {
        recorder.record(source, result)
        if (result is SourceResult.LimitReached && result.rejectedBySource) rateLimiter.saturate(source)
        return result
    }
}
