package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.RequestLimit
import com.sixteenscreener.domain.model.RequestUsage
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.port.outbound.Clock
import com.sixteenscreener.port.outbound.RequestCounterRepository

class RateLimiter(
    private val limitsBySource: Map<SourceId, List<RequestLimit>>,
    private val counters: RequestCounterRepository,
    private val clock: Clock,
) {

    suspend fun tryAcquire(source: SourceId, requested: Int = 1): Int {
        require(requested >= 0) { "Requested calls must not be negative" }
        val limits = limitsOf(source)
        if (requested == 0 || limits.isEmpty()) return requested
        return counters.tryAcquire(source, limits, requested, clock.now())
    }

    suspend fun saturate(source: SourceId) {
        val limits = limitsOf(source)
        if (limits.isNotEmpty()) counters.saturate(source, limits, clock.now())
    }

    suspend fun usage(source: SourceId): List<RequestUsage> {
        val limits = limitsOf(source)
        return if (limits.isEmpty()) emptyList() else counters.usage(source, limits, clock.now())
    }

    private fun limitsOf(source: SourceId): List<RequestLimit> =
        requireNotNull(limitsBySource[source]) { "Unknown source '$source'" }
}
