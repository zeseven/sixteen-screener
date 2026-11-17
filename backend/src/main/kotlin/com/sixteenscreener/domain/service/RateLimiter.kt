package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.RequestLimit
import com.sixteenscreener.domain.model.RequestUsage
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.port.outbound.Clock
import com.sixteenscreener.port.outbound.RequestCounterRepository
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory

class RateLimiter(
    private val limitsBySource: Map<SourceId, List<RequestLimit>>,
    private val counters: RequestCounterRepository,
    private val clock: Clock,
    private val totalCountedSources: Set<SourceId> = emptySet(),
) {

    suspend fun tryAcquire(source: SourceId, requested: Int = 1): Int {
        require(requested >= 0) { "Requested calls must not be negative" }
        val granted = grant(source, requested)
        if (granted > 0 && source in totalCountedSources) countTotal(source, granted)
        return granted
    }

    suspend fun saturate(source: SourceId) {
        val limits = limitsOf(source)
        if (limits.isNotEmpty()) counters.saturate(source, limits, clock.now())
    }

    suspend fun usage(source: SourceId): List<RequestUsage> {
        val limits = limitsOf(source)
        return if (limits.isEmpty()) emptyList() else counters.usage(source, limits, clock.now())
    }

    suspend fun totalCalls(sources: Collection<SourceId>): Map<SourceId, Long> {
        val counted = sources.filter { it in totalCountedSources }
        return if (counted.isEmpty()) emptyMap() else counters.totals(counted)
    }

    fun limitsOf(source: SourceId): List<RequestLimit> =
        requireNotNull(limitsBySource[source]) { "Unknown source '$source'" }

    private suspend fun grant(source: SourceId, requested: Int): Int {
        val limits = limitsOf(source)
        if (requested == 0 || limits.isEmpty()) return requested
        return counters.tryAcquire(source, limits, requested, clock.now())
    }

    private suspend fun countTotal(source: SourceId, calls: Int) {
        try {
            counters.incrementTotal(source, calls)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to count the total calls of source '{}'", source, e)
        }
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(RateLimiter::class.java)
    }
}
