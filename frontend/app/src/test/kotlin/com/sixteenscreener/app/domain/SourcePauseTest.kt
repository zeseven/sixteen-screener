package com.sixteenscreener.app.domain

import com.sixteenscreener.app.testing.MutableClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class SourcePauseTest {

    private val clock = MutableClock(Instant.parse("2026-11-14T10:00:00Z"), ZoneId.of("Europe/Amsterdam"))

    @Test
    fun `source without a pause has no text`() {
        assertNull(SourcePause.endText(null, clock))
    }

    @Test
    fun `pause ending later today shows the local time only`() {
        assertEquals("11:15", SourcePause.endText(Instant.parse("2026-11-14T10:15:00Z"), clock))
    }

    @Test
    fun `pause ending on another day shows date and time`() {
        assertEquals("Nov 15, 00:10", SourcePause.endText(Instant.parse("2026-11-14T23:10:00Z"), clock))
    }

    @Test
    fun `day boundary follows the clock time zone`() {
        val utcClock = MutableClock(Instant.parse("2026-11-14T10:00:00Z"))

        assertEquals("23:10", SourcePause.endText(Instant.parse("2026-11-14T23:10:00Z"), utcClock))
        assertEquals("Nov 15, 00:10", SourcePause.endText(Instant.parse("2026-11-14T23:10:00Z"), clock))
    }

    @Test
    fun `pause that has already ended has no text`() {
        assertNull(SourcePause.endText(Instant.parse("2026-11-14T09:59:00Z"), clock))
    }

    @Test
    fun `pause ending right now has no text`() {
        assertNull(SourcePause.endText(clock.now, clock))
    }
}
