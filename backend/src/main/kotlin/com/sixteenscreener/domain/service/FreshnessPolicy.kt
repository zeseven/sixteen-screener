package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.DataType
import com.sixteenscreener.domain.model.RequestTrigger
import com.sixteenscreener.port.outbound.Clock
import java.time.Duration
import java.time.Instant

class FreshnessPolicy(private val clock: Clock) {

    fun isFresh(fetchedAt: Instant): Boolean = ageOf(fetchedAt) < FRESHNESS_WINDOW

    fun isWatchlistRatiosUpToDate(fetchedAt: Instant): Boolean = ageOf(fetchedAt) < WATCHLIST_RATIOS_MAX_AGE

    fun isProfileExpired(fetchedAt: Instant): Boolean = ageOf(fetchedAt) >= PROFILE_MAX_AGE

    fun shouldCallSource(
        automaticRequests: Boolean,
        trigger: RequestTrigger,
        cachedAt: Instant?,
        autoRefreshAfter: Duration?,
    ): Boolean = when {
        automaticRequests && trigger == RequestTrigger.MANUAL -> true
        automaticRequests -> cachedAt == null || (autoRefreshAfter != null && ageOf(cachedAt) >= autoRefreshAfter)
        trigger == RequestTrigger.MANUAL -> cachedAt == null || ageOf(cachedAt) >= MANUAL_ONLY_REFRESH_AGE
        else -> false
    }

    fun cacheExpireAt(type: DataType<*>, fetchedAt: Instant): Instant = fetchedAt.plus(cacheTtlOf(type))

    private fun ageOf(fetchedAt: Instant): Duration = Duration.between(fetchedAt, clock.now())

    companion object {
        val FRESHNESS_WINDOW: Duration = Duration.ofHours(1)
        val WATCHLIST_RATIOS_MAX_AGE: Duration = Duration.ofDays(1)
        val CACHE_TTL: Duration = Duration.ofDays(30)
        val COMPANY_PROFILE_TTL: Duration = Duration.ofDays(90)
        val PROFILE_MAX_AGE: Duration = COMPANY_PROFILE_TTL
        val MANUAL_ONLY_REFRESH_AGE: Duration = FRESHNESS_WINDOW

        fun cacheTtlOf(type: DataType<*>): Duration =
            if (type == DataType.COMPANY_PROFILE) COMPANY_PROFILE_TTL else CACHE_TTL
    }
}
