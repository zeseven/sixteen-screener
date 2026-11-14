package com.sixteenscreener.port.outbound

import com.sixteenscreener.domain.model.RequestLimit
import com.sixteenscreener.domain.model.RequestUsage
import com.sixteenscreener.domain.model.SourceId
import java.time.Instant

/**
 * Per-source request counters shared between instances (`requestCounters` collection, ADR-031).
 *
 * One counter exists per source, limit interval and window; windows are given by
 * [com.sixteenscreener.domain.model.LimitInterval.windowStart] of [now] (UTC). A counter document expires at its window end.
 */
interface RequestCounterRepository {
    /**
     * Reserves up to [requested] calls to [source] in one transaction: reads the current window of every limit in [limits],
     * grants `k = min(requested, remaining)` where remaining is the smallest `limit - count` (never negative), adds `k`
     * to every window and returns `k`. Without limits all [requested] calls are granted and nothing is stored.
     */
    suspend fun tryAcquire(source: SourceId, limits: List<RequestLimit>, requested: Int, now: Instant): Int

    /**
     * Marks the current windows of every limit in [limits] as exhausted after an upstream `429`, in one transaction:
     * each count is raised to its limit; a count already above the limit is kept.
     */
    suspend fun saturate(source: SourceId, limits: List<RequestLimit>, now: Instant)

    /** Usage of every limit in [limits] in its current window; a window without a counter has `used = 0`. */
    suspend fun usage(source: SourceId, limits: List<RequestLimit>, now: Instant): List<RequestUsage>
}
