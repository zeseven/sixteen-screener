package com.sixteenscreener.app.domain.model

/**
 * Row of the static exchange table: one per symbol suffix, plus one per US exchange with an empty suffix.
 */
data class Exchange(
    /** Symbol suffix without the dot; empty for US exchanges. */
    val suffix: String,
    /** Short exchange name, e.g. `TSXV`. */
    val shortName: String,
    /** Full exchange name. */
    val fullName: String,
    /** ISO 10383 market identifier code. */
    val mic: String,
    /** EODHD exchange code used to build EODHD symbols, e.g. `XETRA` for the `DE` suffix; `null` when EODHD doesn't cover it. */
    val eodhdCode: String?,
    /** Suffix of Yahoo Finance symbols without the dot; empty for US exchanges, `null` when Yahoo Finance isn't used for it. */
    val yahooSuffix: String?,
    /** ISO 3166-1 alpha-2 country code of the exchange; empty when not reported. */
    val country: String,
    /** Whether domestic companies have their primary listing on the exchange. */
    val mainVenue: Boolean,
    /** ISO 4217 currency code. */
    val currency: String,
    /** IANA time zone. */
    val timeZone: String,
    /** Market of the exchange, `null` outside the US and EU markets. */
    val market: Market?,
)
