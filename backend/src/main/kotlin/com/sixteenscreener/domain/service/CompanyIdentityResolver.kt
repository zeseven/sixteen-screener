package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.CompanyIdentity
import com.sixteenscreener.domain.model.ExchangeInfo
import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.Symbol

class CompanyIdentityResolver(private val marketResolver: MarketResolver) {

    fun identify(
        symbol: Symbol,
        listing: ListedSymbol?,
        fallbackName: String? = null,
        fallbackType: String? = null,
    ): CompanyIdentity {
        val exchange = exchangeOf(symbol, listing)
        return CompanyIdentity(
            symbol = symbol,
            exchange = exchange?.name ?: listing?.mic ?: symbol.exchangeCode.value,
            name = listing?.name ?: fallbackName ?: symbol.ticker,
            market = exchange?.market,
            type = listing?.type ?: fallbackType,
        )
    }

    fun exchangeOf(symbol: Symbol, listing: ListedSymbol?): ExchangeInfo? {
        val mic = listing?.mic
        return if (mic != null) marketResolver.find(mic) else marketResolver.exchangeOf(symbol.exchangeCode)
    }
}
