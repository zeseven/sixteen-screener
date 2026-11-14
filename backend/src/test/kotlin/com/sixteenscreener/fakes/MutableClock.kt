package com.sixteenscreener.fakes

import com.sixteenscreener.port.outbound.Clock
import java.time.Duration
import java.time.Instant

class MutableClock(var instant: Instant = Instant.parse("2026-11-14T12:00:00Z")) : Clock {
    override fun now(): Instant = instant

    fun advanceBy(duration: Duration) {
        instant = instant.plus(duration)
    }
}
