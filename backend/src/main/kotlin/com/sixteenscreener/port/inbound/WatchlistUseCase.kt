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
     * Symbols outside the watchlist are skipped and duplicates are ignored. Only quotes are loaded: stale quotes
     * (older than 1 hour) are refreshed oldest first within the source request limits; company profiles are not
     * requested. A row whose quote couldn't be refreshed keeps its cached quote, or none, with the status describing
     * the reason. A symbol no longer found in the exchange directory keeps its cached quote without refresh.
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
