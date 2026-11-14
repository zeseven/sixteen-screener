package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.outbound.SymbolDirectory

object NoSymbolDirectory : SymbolDirectory {
    override suspend fun find(symbol: Symbol): ListedSymbol? = null

    override suspend fun findAll(symbols: Collection<Symbol>): Map<Symbol, ListedSymbol> = emptyMap()
}
