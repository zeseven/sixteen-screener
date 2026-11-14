package com.sixteenscreener.domain.model

import java.time.ZoneId

/**
 * Exchange code used as the canonical symbol suffix (`SAP.DE` is `DE`, `SHEL.L` is `L`); `US` for symbols without suffix.
 * Codes follow the Finnhub symbology (ADR-034).
 */
@JvmInline
value class ExchangeCode(val value: String) {
    init {
        require(value.isNotBlank()) { "Exchange code must not be blank" }
    }

    /** Time zone of the exchange, or null for a code that is not in [KnownExchangeCodes]. */
    val timeZone: ZoneId? get() = KnownExchangeCodes.timeZoneOf(this)

    override fun toString(): String = value

    companion object {
        /** Exchange code of symbols without suffix. */
        val US = ExchangeCode("US")
    }
}

/**
 * Exchange codes known to the backend, with the time zone of each exchange.
 * A symbol suffix is treated as an exchange code only when it is listed here, so US share classes such as `BRK.B` stay US symbols.
 */
object KnownExchangeCodes {
    private val zonesByCode: Map<String, ZoneId> = mapOf(
        "US" to "America/New_York",
        "DE" to "Europe/Berlin",
        "F" to "Europe/Berlin",
        "BE" to "Europe/Berlin",
        "DU" to "Europe/Berlin",
        "HM" to "Europe/Berlin",
        "HA" to "Europe/Berlin",
        "MU" to "Europe/Berlin",
        "SG" to "Europe/Berlin",
        "L" to "Europe/London",
        "IL" to "Europe/London",
        "SW" to "Europe/Zurich",
        "PA" to "Europe/Paris",
        "AS" to "Europe/Amsterdam",
        "BR" to "Europe/Brussels",
        "LS" to "Europe/Lisbon",
        "IR" to "Europe/Dublin",
        "OL" to "Europe/Oslo",
        "MI" to "Europe/Rome",
        "MC" to "Europe/Madrid",
        "ST" to "Europe/Stockholm",
        "HE" to "Europe/Helsinki",
        "CO" to "Europe/Copenhagen",
        "IC" to "Atlantic/Reykjavik",
        "VI" to "Europe/Vienna",
        "WA" to "Europe/Warsaw",
        "PR" to "Europe/Prague",
        "BD" to "Europe/Budapest",
        "AT" to "Europe/Athens",
        "RG" to "Europe/Riga",
        "TL" to "Europe/Tallinn",
        "VS" to "Europe/Vilnius",
        "IS" to "Europe/Istanbul",
        "ME" to "Europe/Moscow",
        "T" to "Asia/Tokyo",
        "TO" to "America/Toronto",
        "V" to "America/Toronto",
        "NE" to "America/Toronto",
        "CN" to "America/Toronto",
        "HK" to "Asia/Hong_Kong",
        "AX" to "Australia/Sydney",
        "NZ" to "Pacific/Auckland",
        "SS" to "Asia/Shanghai",
        "SZ" to "Asia/Shanghai",
        "KS" to "Asia/Seoul",
        "KQ" to "Asia/Seoul",
        "BO" to "Asia/Kolkata",
        "NS" to "Asia/Kolkata",
        "SI" to "Asia/Singapore",
        "TW" to "Asia/Taipei",
        "TWO" to "Asia/Taipei",
        "BK" to "Asia/Bangkok",
        "JK" to "Asia/Jakarta",
        "KL" to "Asia/Kuala_Lumpur",
        "PM" to "Asia/Manila",
        "VN" to "Asia/Ho_Chi_Minh",
        "TA" to "Asia/Jerusalem",
        "SR" to "Asia/Riyadh",
        "QA" to "Asia/Qatar",
        "DB" to "Asia/Dubai",
        "KW" to "Asia/Kuwait",
        "BH" to "Asia/Bahrain",
        "JO" to "Africa/Johannesburg",
        "CA" to "Africa/Cairo",
        "CS" to "Africa/Casablanca",
        "NL" to "Africa/Lagos",
        "SA" to "America/Sao_Paulo",
        "MX" to "America/Mexico_City",
        "BA" to "America/Argentina/Buenos_Aires",
        "SN" to "America/Santiago",
        "BC" to "America/Bogota",
        "CR" to "America/Caracas",
    ).mapValues { (_, zone) -> ZoneId.of(zone) }

    /** All known exchange codes. */
    val codes: Set<ExchangeCode> = zonesByCode.keys.map(::ExchangeCode).toSet()

    /** Whether [value] is a known exchange code. */
    fun isKnown(value: String): Boolean = value in zonesByCode

    /** Time zone of the exchange identified by [code], or null when the code is unknown. */
    fun timeZoneOf(code: ExchangeCode): ZoneId? = zonesByCode[code.value]
}
