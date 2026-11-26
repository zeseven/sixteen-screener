package com.sixteenscreener.adapter.outbound.http

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Spaces the starts of requests to one source at least [minInterval] apart within this backend instance (ADR-089).
 * Callers suspend until it is their turn, in the order they arrived; nothing is refused.
 */
class RequestPacer(
    private val minInterval: Duration,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) {
    private val mutex = Mutex()
    private var nextStart: ComparableTimeMark? = null

    init {
        require(minInterval.isPositive()) { "Minimum request interval must be positive" }
    }

    /** Suspends until a request may start and reserves that start; the next turn comes [minInterval] later. */
    suspend fun awaitTurn() {
        mutex.withLock {
            nextStart?.let { start -> (start - timeSource.markNow()).takeIf { it.isPositive() }?.let { delay(it) } }
            nextStart = timeSource.markNow() + minInterval
        }
    }
}
