package com.sixteenscreener.fakes

import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.outbound.SymbolDirectory
import java.util.concurrent.ConcurrentHashMap

class InMemorySymbolDirectory(listings: Collection<ListedSymbol> = emptyList()) : SymbolDirectory {
    private val listings = ConcurrentHashMap(listings.associateBy { it.symbol })

    override suspend fun find(symbol: Symbol): ListedSymbol? = listings[symbol]

    override suspend fun findAll(symbols: Collection<Symbol>): Map<Symbol, ListedSymbol> =
        symbols.mapNotNull { symbol -> listings[symbol]?.let { symbol to it } }.toMap()

    fun add(listing: ListedSymbol) {
        listings[listing.symbol] = listing
    }
}
