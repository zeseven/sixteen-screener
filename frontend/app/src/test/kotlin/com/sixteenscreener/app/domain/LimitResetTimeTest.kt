package com.sixteenscreener.app.domain

import com.sixteenscreener.app.testing.MutableClock
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class LimitResetTimeTest {

    private val clock = MutableClock(Instant.parse("2026-11-14T10:00:00Z"), ZoneId.of("Europe/Amsterdam"))

    @Test
    fun `reset later today shows the local time only`() {
        assertEquals("11:01", LimitResetTime.format(Instant.parse("2026-11-14T10:01:00Z"), clock))
    }

    @Test
    fun `reset on another day shows date and time`() {
        assertEquals("Dec 1, 01:00", LimitResetTime.format(Instant.parse("2026-12-01T00:00:00Z"), clock))
    }

    @Test
    fun `day boundary follows the clock time zone`() {
        assertEquals("Nov 15, 00:30", LimitResetTime.format(Instant.parse("2026-11-14T23:30:00Z"), clock))
        assertEquals("23:30", LimitResetTime.format(Instant.parse("2026-11-14T22:30:00Z"), clock))
    }
}
