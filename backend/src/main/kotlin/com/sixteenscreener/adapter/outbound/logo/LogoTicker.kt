package com.sixteenscreener.adapter.outbound.logo

import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.Symbol

object LogoTicker {
    fun of(symbol: Symbol): String =
        if (symbol.exchangeCode == ExchangeCode.US) symbol.ticker else symbol.value
}
