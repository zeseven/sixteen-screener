package com.sixteenscreener.port.outbound

import com.sixteenscreener.domain.model.CacheEntries
import com.sixteenscreener.domain.model.CacheEntry
import com.sixteenscreener.domain.model.CacheKey
import com.sixteenscreener.domain.model.Quote
import com.sixteenscreener.domain.model.QuoteRecord
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.Symbol
import java.time.Instant
import java.time.ZoneId

/**
 * Company data cached per source, symbol and data type (`sourceData` and `quoteHistory` collections), shared between users.
 *
 * Entries expire 14 days after `fetchedAt` via Firestore TTL; freshness is always computed from `fetchedAt`,
 * never from TTL deletion.
 */
interface CompanyCache {
    /** Cached entry for [key], or null when nothing is cached. */
    suspend fun <T : Any> get(key: CacheKey<T>): CacheEntry<T>?

    /** Cached entries for [keys] (of any data types) in one read; keys without cached data are absent from the result. */
    suspend fun getAll(keys: Collection<CacheKey<*>>): CacheEntries

    /**
     * Stores [entry] under [key], replacing a previous entry. Quotes must be written with [putQuote];
     * a key of type `QUOTE` is rejected with [IllegalArgumentException].
     */
    suspend fun <T : Any> put(key: CacheKey<T>, entry: CacheEntry<T>)

    /**
     * Writes a quote received at [fetchedAt] atomically (ADR-033):
     * - the market day is the date of [fetchedAt] in [exchangeZone];
     * - the history entry of that market day is created or overwritten with [quote];
     * - the latest quote is replaced as defined by [QuoteRecord.next]: when the stored latest quote belongs to an earlier
     *   market day, its price and day become the previous price; otherwise the stored previous price is kept.
     *
     * Returns the written latest quote entry.
     */
    suspend fun putQuote(
        source: SourceId,
        symbol: Symbol,
        quote: Quote,
        fetchedAt: Instant,
        exchangeZone: ZoneId,
    ): CacheEntry<QuoteRecord>
}
