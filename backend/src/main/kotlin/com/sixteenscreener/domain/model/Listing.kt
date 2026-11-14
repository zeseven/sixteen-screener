package com.sixteenscreener.domain.model

import java.time.ZoneId

/**
 * A symbol listed on an exchange, as found in the exchange directory (ADR-034).
 *
 * @property symbol canonical symbol
 * @property name company name
 * @property mic ISO 10383 market identifier code of the listing exchange; null when the source doesn't provide it
 * @property currency ISO 4217 trading currency
 * @property type security type as provided by the source, e.g. `Common Stock`
 */
data class ListedSymbol(
    val symbol: Symbol,
    val name: String,
    val mic: String?,
    val currency: String,
    val type: String?,
)

/**
 * One result of a source search.
 *
 * @property symbol canonical symbol
 * @property name company name as returned by the search
 * @property type security type as provided by the source
 */
data class SearchHit(
    val symbol: Symbol,
    val name: String,
    val type: String?,
)

/**
 * Exchange attributes resolved from a MIC.
 *
 * @property name short exchange name shown under the ticker
 * @property timeZone time zone of the exchange, used for market days
 * @property market market of the exchange; null outside the US and EU markets (ADR-035)
 */
data class ExchangeInfo(
    val name: String,
    val timeZone: ZoneId,
    val market: Market?,
)
