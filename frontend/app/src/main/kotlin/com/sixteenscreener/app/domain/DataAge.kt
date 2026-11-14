package com.sixteenscreener.app.domain

import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Age of data received from a source, in the largest whole unit: minutes below an hour, hours below a day, days otherwise.
 */
sealed interface DataAge {
    /** Younger than a minute, or fetched in the future because of clock skew. */
    data object JustNow : DataAge

    data class Minutes(val count: Long) : DataAge

    data class Hours(val count: Long) : DataAge

    data class Days(val count: Long) : DataAge

    companion object {
        fun of(fetchedAt: Instant, clock: Clock): DataAge {
            val age = Duration.between(fetchedAt, clock.instant())
            return when {
                age < Duration.ofMinutes(1) -> JustNow
                age < Duration.ofHours(1) -> Minutes(age.toMinutes())
                age < Duration.ofDays(1) -> Hours(age.toHours())
                else -> Days(age.toDays())
            }
        }
    }
}
