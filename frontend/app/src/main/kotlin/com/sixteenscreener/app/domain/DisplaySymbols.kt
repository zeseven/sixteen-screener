package com.sixteenscreener.app.domain

import com.sixteenscreener.app.domain.model.Market

/**
 * Unicode symbols shown in company rows, written as escapes only.
 */
object DisplaySymbols {
    const val FLAG_US = "\uD83C\uDDFA\uD83C\uDDF8"
    const val FLAG_EU = "\uD83C\uDDEA\uD83C\uDDFA"
    const val CLOCK = "\uD83D\uDD52"

    /** Flag of [market], `null` for companies outside the US and EU markets. */
    fun flagOf(market: Market?): String? = when (market) {
        Market.US -> FLAG_US
        Market.EU -> FLAG_EU
        null -> null
    }
}
