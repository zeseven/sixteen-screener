package com.sixteenscreener.fakes

import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.model.SymbolLookup
import com.sixteenscreener.port.outbound.SymbolDirectory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class InMemorySymbolDirectory(listings: Collection<ListedSymbol> = emptyList()) : SymbolDirectory {
    private val listings = ConcurrentHashMap(listings.associateBy { it.symbol })
    private val notCovered = ConcurrentHashMap<ExchangeCode, SourceId>()
    private val unavailable = ConcurrentHashMap.newKeySet<ExchangeCode>()

    val lookupAllRequests: MutableList<List<Symbol>> = CopyOnWriteArrayList()

    override suspend fun lookup(symbol: Symbol): SymbolLookup =
        listings[symbol]?.let(SymbolLookup::Listed)
            ?: notCovered[symbol.exchangeCode]?.let(SymbolLookup::NotCovered)
            ?: if (symbol.exchangeCode in unavailable) SymbolLookup.Unavailable else SymbolLookup.NotListed

    override suspend fun lookupAll(symbols: Collection<Symbol>): Map<Symbol, SymbolLookup> {
        lookupAllRequests += symbols.toList()
        return symbols.distinct().associateWith { lookup(it) }
    }

    fun add(listing: ListedSymbol) {
        listings[listing.symbol] = listing
    }

    fun notCovered(exchangeCode: ExchangeCode, source: SourceId = SourceId.of("finnhub")) {
        notCovered[exchangeCode] = source
    }

    fun unavailable(exchangeCode: ExchangeCode) {
        unavailable += exchangeCode
    }
}
