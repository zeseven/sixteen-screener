package com.sixteenscreener.fakes

import com.sixteenscreener.domain.model.LimitInterval
import com.sixteenscreener.domain.model.RequestLimit
import com.sixteenscreener.domain.model.RequestUsage
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.port.outbound.RequestCounterRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

class InMemoryRequestCounterRepository : RequestCounterRepository {
    private data class WindowKey(val source: SourceId, val interval: LimitInterval, val windowStart: Instant)

    private val mutex = Mutex()
    private val counts = mutableMapOf<WindowKey, Int>()
    private val totals = mutableMapOf<SourceId, Long>()

    var failTotals: Boolean = false

    override suspend fun tryAcquire(source: SourceId, limits: List<RequestLimit>, requested: Int, now: Instant): Int {
        require(requested >= 0) { "Requested calls must not be negative" }
        if (limits.isEmpty()) return requested
        return mutex.withLock {
            val windows = limits.map { it to windowKey(source, it, now) }
            val remaining = windows.minOf { (limit, key) -> (limit.limit - counts.getOrDefault(key, 0)).coerceAtLeast(0) }
            val granted = minOf(requested, remaining)
            if (granted > 0) windows.forEach { (_, key) -> counts.merge(key, granted, Int::plus) }
            granted
        }
    }

    override suspend fun saturate(source: SourceId, limits: List<RequestLimit>, now: Instant) {
        mutex.withLock {
            limits.forEach { limit ->
                counts.merge(windowKey(source, limit, now), limit.limit, ::maxOf)
            }
        }
    }

    var failUsage: Boolean = false

    override suspend fun usage(source: SourceId, limits: List<RequestLimit>, now: Instant): List<RequestUsage> =
        mutex.withLock {
            check(!failUsage) { "Simulated counter read failure" }
            limits.map { limit ->
                RequestUsage(
                    limit = limit,
                    used = counts.getOrDefault(windowKey(source, limit, now), 0),
                    resetsAt = limit.interval.windowEnd(now),
                )
            }
        }

    override suspend fun incrementTotal(source: SourceId, calls: Int) {
        require(calls >= 0) { "Calls must not be negative" }
        mutex.withLock {
            check(!failTotals) { "Simulated total counter failure" }
            if (calls > 0) totals.merge(source, calls.toLong(), Long::plus)
        }
    }

    override suspend fun totals(sources: Collection<SourceId>): Map<SourceId, Long> =
        mutex.withLock {
            check(!failTotals) { "Simulated total counter failure" }
            sources.associateWith { totals.getOrDefault(it, 0L) }
        }

    fun setCount(source: SourceId, interval: LimitInterval, now: Instant, count: Int) {
        counts[WindowKey(source, interval, interval.windowStart(now))] = count
    }

    private fun windowKey(source: SourceId, limit: RequestLimit, now: Instant) =
        WindowKey(source, limit.interval, limit.interval.windowStart(now))
}
