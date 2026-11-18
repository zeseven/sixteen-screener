package com.sixteenscreener.domain.model

import java.time.ZoneId

/**
 * Row of the static exchange table as shown on the Exchanges screen (ADR-070).
 *
 * @property suffix symbol suffix without the dot; empty for US exchanges
 * @property shortName short exchange name shown under the ticker
 * @property fullName official exchange name
 * @property mic ISO 10383 market identifier code
 * @property currency ISO 4217 trading currency of the exchange
 * @property timeZone time zone of the exchange
 * @property market market of the exchange; null outside the US and EU markets (ADR-035)
 * @property country ISO 3166-1 alpha-2 country of the exchange
 * @property mainVenue true when domestic companies have their primary listing on the exchange; false for regional and OTC
 * exchanges
 */
data class ExchangeEntry(
    val suffix: String,
    val shortName: String,
    val fullName: String,
    val mic: String,
    val currency: String,
    val timeZone: ZoneId,
    val market: Market?,
    val country: String,
    val mainVenue: Boolean,
)
