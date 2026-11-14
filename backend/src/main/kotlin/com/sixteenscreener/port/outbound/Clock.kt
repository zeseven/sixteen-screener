package com.sixteenscreener.port.outbound

import java.time.Instant

/**
 * Source of the current time. Injected wherever time matters (freshness, counter windows, TTL, market day).
 */
fun interface Clock {
    /** Current instant. */
    fun now(): Instant
}
