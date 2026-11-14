package com.sixteenscreener.port.inbound

import com.sixteenscreener.domain.model.CompanySummary
import com.sixteenscreener.domain.model.ProfileId

/**
 * Watchlist rows with data from the profile's default source (ADR-031, ADR-032).
 */
interface WatchlistUseCase {
    /**
     * Company summaries of every watchlist symbol of profile [profileId], in watchlist order.
     *
     * Stale quotes (older than 1 hour) are refreshed oldest first within the source request limits; company profiles
     * (sector) only when absent or older than 14 days. Companies that couldn't be refreshed keep their cached data.
     * A symbol no longer found in the exchange directory is still returned with best-effort data: the exchange code
     * as exchange name, the cached profile name or the ticker as name, no market, and its cached quote without refresh.
     *
     * Throws [com.sixteenscreener.domain.model.ResourceNotFoundException] for an unknown profile.
     */
    suspend fun watchlist(profileId: ProfileId): List<CompanySummary>
}
