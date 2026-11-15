package com.sixteenscreener.domain.model

/**
 * Company stored in a profile's watchlist (ADR-057, ADR-062).
 *
 * @property symbol canonical symbol
 * @property name company name: from the exchange directory when the symbol is listed there, otherwise the name of the
 * search result sent by the app, otherwise the ticker; updated from the company profile of the profile's default source
 * @property sector sector from the company profile of the profile's default source; null when unknown
 * @property tags profile tags of the company (ADR-061)
 */
data class WatchlistEntry(
    val symbol: Symbol,
    val name: String,
    val sector: String?,
    val tags: List<String> = emptyList(),
) {
    companion object {
        /** Maximum length in characters (Unicode code points) of a company name sent by the app (spec `maxLength`). */
        const val MAX_NAME_LENGTH = 200
    }
}
