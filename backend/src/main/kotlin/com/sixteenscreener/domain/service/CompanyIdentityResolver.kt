package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.CompanyIdentity
import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.Symbol

class CompanyIdentityResolver(private val marketResolver: MarketResolver) {

    fun identify(symbol: Symbol, listing: ListedSymbol?, fallbackName: String? = null): CompanyIdentity {
        val exchange = listing?.mic?.let(marketResolver::resolve)
        return CompanyIdentity(
            symbol = symbol,
            exchange = exchange?.name ?: symbol.exchangeCode.value,
            name = listing?.name ?: fallbackName ?: symbol.ticker,
            market = exchange?.market,
        )
    }
}
