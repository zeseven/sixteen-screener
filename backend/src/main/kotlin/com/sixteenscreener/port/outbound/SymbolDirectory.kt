package com.sixteenscreener.port.outbound

import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.Symbol

/**
 * Directory of listed symbols per exchange (ADR-034), used to validate symbols and to resolve name, MIC and currency
 * of search results and watchlist rows without source requests per company.
 */
interface SymbolDirectory {
    /** Listing of [symbol], or null when the symbol is unknown or its exchange directory is unavailable. */
    suspend fun find(symbol: Symbol): ListedSymbol?

    /** Listings of [symbols]; unknown symbols are absent from the result. Each exchange directory is loaded at most once. */
    suspend fun findAll(symbols: Collection<Symbol>): Map<Symbol, ListedSymbol>
}
