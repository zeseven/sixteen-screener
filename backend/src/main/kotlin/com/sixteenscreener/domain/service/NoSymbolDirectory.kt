package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.model.SymbolLookup
import com.sixteenscreener.port.outbound.SymbolDirectory

object NoSymbolDirectory : SymbolDirectory {
    override suspend fun lookup(symbol: Symbol): SymbolLookup = SymbolLookup.Unavailable

    override suspend fun lookupAll(symbols: Collection<Symbol>): Map<Symbol, SymbolLookup> =
        symbols.associateWith { SymbolLookup.Unavailable }
}
