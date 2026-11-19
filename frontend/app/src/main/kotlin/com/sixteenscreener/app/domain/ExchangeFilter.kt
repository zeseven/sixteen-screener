package com.sixteenscreener.app.domain

import com.sixteenscreener.app.domain.model.Exchange

/**
 * In-memory filter of the exchange table: case-insensitive substring match of the trimmed query against every displayed text
 * column; the market flag is not matched.
 */
object ExchangeFilter {

    fun apply(exchanges: List<Exchange>, query: String): List<Exchange> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) exchanges else exchanges.filter { matches(it, trimmed) }
    }

    fun matches(exchange: Exchange, query: String): Boolean = searchableTexts(exchange).any { it.contains(query.trim(), ignoreCase = true) }

    /** Suffix as displayed, with a leading dot; empty for US exchanges. */
    fun displaySuffix(exchange: Exchange): String = exchange.suffix.takeIf { it.isNotEmpty() }?.let { ".$it" }.orEmpty()

    private fun searchableTexts(exchange: Exchange): List<String> = with(exchange) {
        listOfNotNull(displaySuffix(this), shortName, fullName, mic, eodhdCode, country, currency, timeZone)
    }
}
