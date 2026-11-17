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
    /** ISO 4217 currency code. */
    val currency: String,
    /** IANA time zone. */
    val timeZone: String,
    /** Market of the exchange, `null` outside the US and EU markets. */
    val market: Market?,
)
