package com.sixteenscreener.domain.service

import com.sixteenscreener.port.outbound.Clock
import java.time.Duration
import java.time.Instant

class FreshnessPolicy(private val clock: Clock) {

    fun isFresh(fetchedAt: Instant): Boolean = ageOf(fetchedAt) < FRESHNESS_WINDOW

    fun isProfileExpired(fetchedAt: Instant): Boolean = ageOf(fetchedAt) >= PROFILE_MAX_AGE

    fun cacheExpireAt(fetchedAt: Instant): Instant = fetchedAt.plus(CACHE_TTL)

    private fun ageOf(fetchedAt: Instant): Duration = Duration.between(fetchedAt, clock.now())

    companion object {
        val FRESHNESS_WINDOW: Duration = Duration.ofHours(1)
        val PROFILE_MAX_AGE: Duration = Duration.ofDays(14)
        val CACHE_TTL: Duration = Duration.ofDays(14)
    }
}
