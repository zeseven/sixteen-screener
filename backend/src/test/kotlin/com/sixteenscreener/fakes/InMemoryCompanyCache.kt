package com.sixteenscreener.fakes

import com.sixteenscreener.domain.model.CacheEntries
import com.sixteenscreener.domain.model.CacheEntry
import com.sixteenscreener.domain.model.CacheKey
import com.sixteenscreener.domain.model.DataType
import com.sixteenscreener.domain.model.Quote
import com.sixteenscreener.domain.model.QuoteHistoryEntry
import com.sixteenscreener.domain.model.QuoteRecord
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.outbound.CompanyCache
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class InMemoryCompanyCache : CompanyCache {
    private val mutex = Mutex()
    private val entries = mutableMapOf<CacheKey<*>, CacheEntry<*>>()
    private val history = mutableMapOf<Triple<SourceId, Symbol, LocalDate>, QuoteHistoryEntry>()

    var getAllCalls: Int = 0
        private set

    override suspend fun <T : Any> get(key: CacheKey<T>): CacheEntry<T>? = mutex.withLock {
        CacheEntries(entries)[key]
    }

    override suspend fun getAll(keys: Collection<CacheKey<*>>): CacheEntries = mutex.withLock {
        getAllCalls++
        CacheEntries(entries.filterKeys { it in keys })
    }

    override suspend fun <T : Any> put(key: CacheKey<T>, entry: CacheEntry<T>) {
        require(key.type != DataType.QUOTE) { "Quotes must be written with putQuote" }
        mutex.withLock { entries[key] = entry }
    }

    override suspend fun putQuote(
        source: SourceId,
        symbol: Symbol,
        quote: Quote,
        fetchedAt: Instant,
        exchangeZone: ZoneId,
    ): CacheEntry<QuoteRecord> = mutex.withLock {
        val key = CacheKey(source, symbol, DataType.QUOTE)
        val marketDate = LocalDate.ofInstant(fetchedAt, exchangeZone)
        val current = CacheEntries(entries)[key]?.data
        val entry = CacheEntry(QuoteRecord.next(current, quote, marketDate), fetchedAt)
        history[Triple(source, symbol, marketDate)] = QuoteHistoryEntry(source, symbol, marketDate, quote, fetchedAt)
        entries[key] = entry
        entry
    }

    fun seedQuote(key: CacheKey<QuoteRecord>, entry: CacheEntry<QuoteRecord>) {
        entries[key] = entry
    }

    fun history(source: SourceId, symbol: Symbol): List<QuoteHistoryEntry> =
        history.values.filter { it.source == source && it.symbol == symbol }.sortedBy { it.marketDate }
}
