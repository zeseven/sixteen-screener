package com.sixteenscreener.port.inbound

import com.sixteenscreener.domain.model.CompanySummary
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.model.WatchlistItem

/**
 * Watchlist rows (ADR-057) and their market data from the profile's default source (ADR-031, ADR-055).
 */
interface WatchlistUseCase {
    /**
     * Watchlist items of every watchlist entry of profile [profileId], in watchlist order.
     *
     * Sector and tags come from the watchlist entries; ticker, exchange, name and market from the exchange directory.
     * No cached company data is read and no source requests are made. A symbol no longer found in the exchange
     * directory is still returned with best-effort data: the exchange code as exchange name, the ticker as name, and
     * no market (ADR-057).
     *
     * Throws [com.sixteenscreener.domain.model.ResourceNotFoundException] for an unknown profile.
     */
    suspend fun watchlist(profileId: ProfileId): List<WatchlistItem>

    /**
     * Company summaries of the [symbols] that are in the watchlist of profile [profileId], in watchlist order.
     *
     * Symbols outside the watchlist are skipped and duplicates are ignored. Stale quotes (older than 1 hour) are
     * refreshed oldest first within the source request limits; absent or expired company profiles are requested too. Companies that
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
