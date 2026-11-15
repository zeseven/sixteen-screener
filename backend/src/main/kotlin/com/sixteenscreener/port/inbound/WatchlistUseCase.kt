package com.sixteenscreener.port.inbound

import com.sixteenscreener.domain.model.CompanySummary
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.Symbol

/**
 * Watchlist rows with data from the profile's default source (ADR-031, ADR-032, ADR-055).
 */
interface WatchlistUseCase {
    /**
     * Company summaries of every watchlist symbol of profile [profileId], in watchlist order.
     *
     * No quote requests are made: quotes are returned from the cache as is, and a company without any cached quote has
     * no quote. Company profiles (sector) are requested within the source request limits when absent or older than
     * 14 days. A symbol no longer found in the exchange directory is still returned with best-effort data: the exchange
     * code as exchange name, the cached profile name or the ticker as name, and no market.
     *
     * Throws [com.sixteenscreener.domain.model.ResourceNotFoundException] for an unknown profile.
     */
    suspend fun watchlist(profileId: ProfileId): List<CompanySummary>

    /**
     * Company summaries of the [symbols] that are in the watchlist of profile [profileId], in watchlist order.
     *
     * Symbols outside the watchlist are skipped and duplicates are ignored. Stale quotes (older than 1 hour) are
     * refreshed oldest first within the source request limits; company profiles as in [watchlist]. Companies that
     * couldn't be refreshed keep their cached data. A symbol no longer found in the exchange directory keeps its cached
     * quote without refresh.
     *
     * Throws [com.sixteenscreener.domain.model.InvalidInputException] when [symbols] is empty or holds more than
     * [MAX_REFRESH_SYMBOLS] entries, and [com.sixteenscreener.domain.model.ResourceNotFoundException] for an unknown
     * profile.
     */
    suspend fun refreshWatchlistRows(profileId: ProfileId, symbols: List<Symbol>): List<CompanySummary>

    companion object {
        /** Maximum number of symbols in one [refreshWatchlistRows] request. */
        const val MAX_REFRESH_SYMBOLS = 50
    }
}
