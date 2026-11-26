package com.sixteenscreener.domain.model

/**
 * Exchange code used as the canonical symbol suffix (`SAP.DE` is `DE`, `SHEL.L` is `L`); `US` for symbols without suffix.
 * Codes follow the Finnhub symbology (ADR-034).
 */
@JvmInline
value class ExchangeCode(val value: String) {
    init {
        require(value.isNotBlank()) { "Exchange code must not be blank" }
    }

    override fun toString(): String = value

    companion object {
        /** Exchange code of symbols without suffix. */
        val US = ExchangeCode("US")
    }
}

/**
 * Exchange codes known to the backend, each mapped to the MIC of its exchange in the static exchange table (ADR-062).
 * A symbol suffix is treated as an exchange code only when it is listed here, so US share classes such as `BRK.B` stay US symbols.
 */
object KnownExchangeCodes {
    private val micsByCode: Map<String, String> = mapOf(
        "DE" to "XETR",
        "F" to "XFRA",
        "BE" to "XBER",
        "DU" to "XDUS",
        "HM" to "XHAM",
        "HA" to "XHAN",
        "MU" to "XMUN",
        "SG" to "XSTU",
        "L" to "XLON",
        "IL" to "XLON",
        "SW" to "XSWX",
        "PA" to "XPAR",
        "AS" to "XAMS",
        "BR" to "XBRU",
        "LS" to "XLIS",
        "IR" to "XDUB",
        "OL" to "XOSL",
        "MI" to "XMIL",
        "MC" to "XMAD",
        "ST" to "XSTO",
        "HE" to "XHEL",
        "CO" to "XCSE",
        "IC" to "XICE",
        "VI" to "XWBO",
        "WA" to "XWAR",
        "PR" to "XPRA",
        "BD" to "XBUD",
        "AT" to "XATH",
        "RG" to "XRIS",
        "TL" to "XTAL",
        "VS" to "XLIT",
        "IS" to "XIST",
        "ME" to "MISX",
        "T" to "XTKS",
        "TO" to "XTSE",
        "V" to "XTSX",
        "NE" to "NEOE",
        "CN" to "XCNQ",
        "HK" to "XHKG",
        "AX" to "XASX",
        "NZ" to "XNZE",
        "SS" to "XSHG",
        "SZ" to "XSHE",
        "KS" to "XKRX",
        "KQ" to "XKOS",
        "BO" to "XBOM",
        "NS" to "XNSE",
        "SI" to "XSES",
        "TW" to "XTAI",
        "TWO" to "ROCO",
        "BK" to "XBKK",
        "JK" to "XIDX",
        "KL" to "XKLS",
        "PM" to "XPHS",
        "VN" to "XSTC",
        "TA" to "XTAE",
        "SR" to "XSAU",
        "QA" to "DSMD",
        "DB" to "XDFM",
        "KW" to "XKUW",
        "BH" to "XBAH",
        "JO" to "XJSE",
        "CA" to "XCAI",
        "CS" to "XCAS",
        "NL" to "XNSA",
        "SA" to "BVMF",
        "MX" to "XMEX",
        "BA" to "XBUE",
        "SN" to "XSGO",
        "BC" to "XBOG",
        "CR" to "XCAR",
    )

    private val eodhdCodesByCode: Map<String, String> = mapOf(
        "US" to "US",
        "DE" to "XETRA",
        "F" to "F",
        "DU" to "DU",
        "HM" to "HM",
        "HA" to "HA",
        "MU" to "MU",
        "SG" to "STU",
        "L" to "LSE",
        "SW" to "SW",
        "PA" to "PA",
        "AS" to "AS",
        "BR" to "BR",
        "LS" to "LS",
        "IR" to "IR",
        "OL" to "OL",
        "MC" to "MC",
        "ST" to "ST",
        "HE" to "HE",
        "CO" to "CO",
        "VI" to "VI",
        "WA" to "WAR",
        "PR" to "PR",
        "BD" to "BUD",
        "AT" to "AT",
        "TO" to "TO",
        "V" to "V",
        "NE" to "NEO",
        "AX" to "AU",
        "SS" to "SHG",
        "SZ" to "SHE",
        "KS" to "KO",
        "KQ" to "KQ",
        "TW" to "TW",
        "TWO" to "TWO",
        "BK" to "BK",
        "JK" to "JK",
        "KL" to "KLSE",
        "PM" to "PSE",
        "VN" to "VN",
        "JO" to "JSE",
        "CA" to "EGX",
        "CS" to "BC",
        "NL" to "XNSA",
        "SA" to "SA",
        "MX" to "MX",
        "BA" to "BA",
        "SN" to "SN",
    )

    private val yahooSuffixesByCode: Map<String, String> = listOf(
        "AS", "BR", "PA", "LS", "IR", "MI", "MC",
        "DE", "F", "BE", "DU", "HM", "HA", "MU", "SG",
        "VI", "SW", "L", "IL",
        "CO", "ST", "HE", "OL", "IC",
        "WA", "PR", "BD", "AT", "TL", "RG", "VS", "IS",
        "TO", "V", "NE", "CN",
        "T", "HK", "KS", "KQ", "TW", "TWO", "SS", "SZ", "NS", "BO", "SI", "BK", "JK", "KL",
        "AX", "NZ", "TA", "SR", "JO",
        "SA", "MX", "BA", "SN",
    ).associateWith { it }

    /** All known exchange codes. */
    val codes: Set<ExchangeCode> = (micsByCode.keys + ExchangeCode.US.value).map(::ExchangeCode).toSet()

    /** MICs of the known exchange codes except [ExchangeCode.US], keyed by exchange code. */
    val mics: Map<ExchangeCode, String> = micsByCode.mapKeys { (code, _) -> ExchangeCode(code) }

    /** Whether [value] is a known exchange code. */
    fun isKnown(value: String): Boolean = value == ExchangeCode.US.value || value in micsByCode

    /** MIC of the exchange identified by [code]; null for [ExchangeCode.US], whose symbols trade on several exchanges, and for unknown codes. */
    fun micOf(code: ExchangeCode): String? = micsByCode[code.value]

    /**
     * EODHD exchange code of the exchange identified by [code], used to build EODHD symbols (`SAP.DE` is `SAP.XETRA`);
     * `US` for [ExchangeCode.US]; null for exchanges EODHD doesn't cover and for unknown codes (ADR-083).
     */
    fun eodhdCodeOf(code: ExchangeCode): String? = eodhdCodesByCode[code.value]

    /**
     * Yahoo Finance suffix of the exchange identified by [code], used to build Yahoo symbols (`SAP.DE` is `SAP.DE`);
     * an empty suffix for [ExchangeCode.US]; null for exchanges without a known Yahoo suffix and for unknown codes (ADR-088).
     */
    fun yahooSuffixOf(code: ExchangeCode): String? =
        if (code == ExchangeCode.US) "" else yahooSuffixesByCode[code.value]
}
