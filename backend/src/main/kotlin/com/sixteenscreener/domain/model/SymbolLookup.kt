package com.sixteenscreener.domain.model

/**
 * Result of looking up a symbol in the exchange directory (ADR-034, ADR-062).
 */
sealed interface SymbolLookup {
    /** Listing of the symbol, or null when it is not listed. */
    val listing: ListedSymbol? get() = null

    /**
     * The symbol is in the symbol list of its exchange.
     *
     * @property listing listing of the symbol
     */
    data class Listed(override val listing: ListedSymbol) : SymbolLookup

    /** The symbol list of the exchange is available and doesn't contain the symbol. */
    data object NotListed : SymbolLookup

    /**
     * The exchange is not covered: the source answered that it has no symbol list for it (`NOT_SUPPORTED` or `NO_DATA`).
     *
     * @property source source of the exchange directory
     */
    data class NotCovered(val source: SourceId) : SymbolLookup

    /** The symbol list of the exchange is temporarily unavailable: its download failed and no list is stored. */
    data object Unavailable : SymbolLookup
}
