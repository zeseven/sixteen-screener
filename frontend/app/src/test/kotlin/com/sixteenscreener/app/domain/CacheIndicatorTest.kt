package com.sixteenscreener.app.domain

import com.sixteenscreener.app.testing.MutableClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class CacheIndicatorTest {

    private val amsterdam = ZoneId.of("Europe/Amsterdam")

    private fun local(dateTime: String, zone: ZoneId = amsterdam): Instant = LocalDateTime.parse(dateTime).atZone(zone).toInstant()

    private fun days(fetchedAt: Instant, now: Instant, zone: ZoneId = amsterdam) = CacheIndicator.days(fetchedAt, MutableClock(now, zone))

    @Test
    fun `hidden for data fetched 59 minutes 59 seconds ago`() {
        val now = local("2026-11-14T12:00:00")

        assertNull(days(now - Duration.ofMinutes(59).plusSeconds(59), now))
    }

    @Test
    fun `shown for data fetched exactly one hour ago`() {
        val now = local("2026-11-14T12:00:00")

        assertEquals(1, days(now - Duration.ofHours(1), now))
    }

    @Test
    fun `hidden for data fetched just now`() {
        val now = local("2026-11-14T12:00:00")

        assertNull(days(now, now))
    }

    @Test
    fun `today's data older than one hour is day 1`() {
        assertEquals(1, days(local("2026-11-14T00:00:00"), local("2026-11-14T23:59:59")))
    }

    @Test
    fun `yesterday's data is day 2`() {
        assertEquals(2, days(local("2026-11-13T09:00:00"), local("2026-11-14T12:00:00")))
    }

    @Test
    fun `data of several days ago counts calendar days`() {
        assertEquals(6, days(local("2026-11-09T23:59:00"), local("2026-11-14T00:01:00")))
    }

    @Test
    fun `midnight in the device time zone starts a new day`() {
        val fetchedAt = local("2026-11-13T22:30:00")

        assertEquals(1, days(fetchedAt, local("2026-11-13T23:59:59")))
        assertEquals(2, days(fetchedAt, local("2026-11-14T00:00:00")))
    }

    @Test
    fun `device time zone decides the calendar day, not UTC`() {
        val fetchedAt = Instant.parse("2026-11-13T22:30:00Z")
        val now = Instant.parse("2026-11-14T00:30:00Z")

        assertEquals(1, days(fetchedAt, now, ZoneId.of("America/New_York")))
        assertEquals(2, days(fetchedAt, now, ZoneOffset.UTC))
    }

    @Test
    fun `fetchedAt in the future is hidden`() {
        val now = local("2026-11-14T12:00:00")

        assertNull(days(now + Duration.ofSeconds(1), now))
        assertNull(days(now + Duration.ofDays(2), now))
    }

    @Test
    fun `spring DST change counts calendar days`() {
        assertEquals(2, days(local("2026-03-28T23:30:00"), local("2026-03-29T03:30:00")))
        assertEquals(1, days(local("2026-03-29T01:30:00"), local("2026-03-29T03:30:00")))
    }

    @Test
    fun `one hour threshold uses elapsed time across the spring DST change`() {
        val fetchedAt = local("2026-03-29T01:30:00")

        assertNull(days(fetchedAt, local("2026-03-29T03:29:59")))
    }

    @Test
    fun `autumn DST change counts calendar days`() {
        assertEquals(2, days(local("2026-10-24T23:30:00"), local("2026-10-25T02:30:00")))
        assertEquals(1, days(local("2026-10-25T00:30:00"), local("2026-10-25T23:30:00")))
    }

    @Test
    fun `label is the clock escape followed by the days`() {
        assertEquals("\uD83D\uDD52" + "1", CacheIndicator.label(1))
        assertEquals("\uD83D\uDD52" + "14", CacheIndicator.label(14))
    }

    @Test
    fun `device clock follows the zone provider on every call`() {
        var zone: ZoneId = ZoneOffset.UTC
        val clock = DeviceClock { zone }

        assertEquals(ZoneOffset.UTC, clock.zone)
        zone = amsterdam
        assertEquals(amsterdam, clock.zone)
    }
}
