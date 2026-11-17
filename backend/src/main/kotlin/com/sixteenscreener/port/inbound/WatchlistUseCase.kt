package com.sixteenscreener.port.inbound

import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.model.WatchlistMarketData
import com.sixteenscreener.domain.model.WatchlistItem

/**
 * Watchlist rows (ADR-057) and their market data from the profile's default source (ADR-031, ADR-055).
 */
interface WatchlistUseCase {
    /**
     * Watchlist items of every watchlist entry of profile [profileId], in watchlist order.
     *
     * Name, sector and tags come from the watchlist entries; ticker, exchange and market from the exchange directory, or
     * from the static exchange table for a symbol not found in the directory (ADR-057, ADR-062). No cached company data
     * is read and no source requests are made.
     *
     * Throws [com.sixteenscreener.domain.model.ResourceNotFoundException] for an unknown profile.
     */
    suspend fun watchlist(profileId: ProfileId): List<WatchlistItem>

    /**
     * Market data of the [symbols] that are in the watchlist of profile [profileId], in watchlist order (ADR-058).
     *
     * Symbols outside the watchlist are skipped and duplicates are ignored. Quotes and ratios are loaded (ADR-075):
     * quotes older than 1 hour and ratios older than 1 day are refreshed oldest first within one source budget
     * reservation, quotes first; company profiles are not requested. A row whose quote or ratios couldn't be refreshed
     * keeps its cached data, or none, with the status describing the reason. A symbol no longer found in the exchange
     * directory keeps its cached data without refresh. Indicators are calculated from the ratios and the quote with the
     * profile's indicator settings, which are copied from the defaults first when the profile misses some (ADR-072).
     *
     * Throws [com.sixteenscreener.domain.model.InvalidInputException] when [symbols] is empty or holds more than
     * [MAX_MARKET_DATA_SYMBOLS] entries, and [com.sixteenscreener.domain.model.ResourceNotFoundException] for an unknown
     * profile.
     */
    suspend fun marketData(profileId: ProfileId, symbols: List<Symbol>): List<WatchlistMarketData>

    companion object {
        /** Maximum number of symbols in one [marketData] request. */
        const val MAX_MARKET_DATA_SYMBOLS = 50
    }
}
