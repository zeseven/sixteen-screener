package com.sixteenscreener.adapter.outbound.time

import com.sixteenscreener.port.outbound.Clock
import java.time.Instant

object SystemClock : Clock {
    override fun now(): Instant = Instant.now()
}
