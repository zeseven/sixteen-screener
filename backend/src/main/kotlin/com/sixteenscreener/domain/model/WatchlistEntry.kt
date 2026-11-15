package com.sixteenscreener.domain.model

/**
 * Company stored in a profile's watchlist (ADR-057).
 *
 * @property symbol canonical symbol
 * @property sector sector from the company profile of the profile's default source; null when unknown
 * @property tags profile tags of the company (ADR-061)
 */
data class WatchlistEntry(
    val symbol: Symbol,
    val sector: String?,
    val tags: List<String> = emptyList(),
)
