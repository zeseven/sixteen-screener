package com.sixteenscreener.app.domain

import com.sixteenscreener.app.testing.MutableClock
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class DataAgeTest {

    private val now = Instant.parse("2026-11-14T12:00:00Z")

    private fun ageOf(age: Duration) = DataAge.of(now - age, MutableClock(now))

    @Test
    fun `younger than a minute is just now`() {
        assertEquals(DataAge.JustNow, ageOf(Duration.ofSeconds(59)))
        assertEquals(DataAge.JustNow, ageOf(Duration.ZERO))
    }

    @Test
    fun `fetched in the future is just now`() {
        assertEquals(DataAge.JustNow, ageOf(Duration.ofMinutes(-5)))
    }

    @Test
    fun `minutes below an hour`() {
        assertEquals(DataAge.Minutes(1), ageOf(Duration.ofMinutes(1)))
        assertEquals(DataAge.Minutes(59), ageOf(Duration.ofMinutes(60).minusSeconds(1)))
    }

    @Test
    fun `hours below a day`() {
        assertEquals(DataAge.Hours(1), ageOf(Duration.ofHours(1)))
        assertEquals(DataAge.Hours(23), ageOf(Duration.ofDays(1).minusSeconds(1)))
    }

    @Test
    fun `days from one day`() {
        assertEquals(DataAge.Days(1), ageOf(Duration.ofDays(1)))
        assertEquals(DataAge.Days(400), ageOf(Duration.ofDays(400).plusHours(5)))
    }
}
