package com.sixteenscreener.app.domain

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Cache indicator of a quote: hidden for data younger than [FRESH_PERIOD], otherwise the clock symbol with the number of
 * calendar days the data spans in the clock's time zone (1 for today's data, 2 for yesterday's, and so on).
 */
object CacheIndicator {
    val FRESH_PERIOD: Duration = Duration.ofHours(1)

    /** Days to show for data fetched at [fetchedAt], `null` when the indicator is hidden (fresh data or [fetchedAt] in the future). */
    fun days(fetchedAt: Instant, clock: Clock): Int? {
        val now = clock.instant()
        if (fetchedAt > now || Duration.between(fetchedAt, now) < FRESH_PERIOD) return null
        val zone = clock.zone
        val fetchedDate = fetchedAt.atZone(zone).toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(fetchedDate, today).toInt() + 1
    }

    /** Indicator text for [days], e.g. the clock symbol followed by `2`. */
    fun label(days: Int): String = "${DisplaySymbols.CLOCK}$days"
}
