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
 * Exchange attributes from the static exchange table, resolved from a MIC or an exchange code (ADR-062).
 *
 * @property name short exchange name shown under the ticker
 * @property timeZone time zone of the exchange, used for market days
 * @property market market of the exchange; null outside the US and EU markets (ADR-035)
 * @property currency ISO 4217 trading currency of the exchange
 * @property country ISO 3166-1 alpha-2 country of the exchange
 * @property mainVenue true when domestic companies have their primary listing on the exchange; false for regional and OTC
 * exchanges
 */
data class ExchangeInfo(
    val name: String,
    val timeZone: ZoneId,
    val market: Market?,
    val currency: String,
    val country: String,
    val mainVenue: Boolean,
) {
    /** True when the exchange is a main venue in [companyCountry], an ISO 3166-1 alpha-2 code compared case-insensitively. */
    fun isHomeOf(companyCountry: String?): Boolean =
        mainVenue && companyCountry != null && country.equals(companyCountry.trim(), ignoreCase = true)
}
