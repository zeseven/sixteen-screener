package com.sixteenscreener.port.outbound

import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.model.SymbolLookup

/**
 * Directory of listed symbols per exchange (ADR-034), used to validate symbols and to resolve name, MIC and currency
 * of search results and watchlist rows without source requests per company. It also tells whether the source covers
 * the exchange of a symbol (ADR-062).
 */
interface SymbolDirectory {
    /** Looks up [symbol] in the symbol list of its exchange. */
    suspend fun lookup(symbol: Symbol): SymbolLookup

    /** Looks up every distinct symbol of [symbols]. Each exchange directory is loaded at most once. */
    suspend fun lookupAll(symbols: Collection<Symbol>): Map<Symbol, SymbolLookup>
}
