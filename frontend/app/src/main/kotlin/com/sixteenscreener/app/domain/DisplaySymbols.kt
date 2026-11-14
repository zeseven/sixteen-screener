package com.sixteenscreener.app.domain

import com.sixteenscreener.app.domain.model.Market

/**
 * Unicode symbols shown in company rows, written as escapes only.
 */
object DisplaySymbols {
    const val FLAG_US = "🇺🇸"
    const val FLAG_EU = "🇪🇺"
    const val CLOCK = "🕒"

    /** Flag of [market], `null` for companies outside the US and EU markets. */
    fun flagOf(market: Market?): String? = when (market) {
        Market.US -> FLAG_US
        Market.EU -> FLAG_EU
        null -> null
    }
}
