package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.ExchangeInfo
import com.sixteenscreener.domain.model.KnownExchangeCodes
import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.Market
import com.sixteenscreener.domain.model.Symbol
import java.time.ZoneId
import java.time.ZoneOffset

class MarketResolver {

    fun find(mic: String): ExchangeInfo? = EXCHANGES_BY_MIC[mic.uppercase()]

    fun exchangeOf(exchangeCode: ExchangeCode): ExchangeInfo? =
        if (exchangeCode == ExchangeCode.US) US_EXCHANGES_COMBINED else KnownExchangeCodes.micOf(exchangeCode)?.let(::find)

    fun staticListingOf(symbol: Symbol): ListedSymbol? =
        exchangeOf(symbol.exchangeCode)?.let { exchange ->
            ListedSymbol(symbol, symbol.ticker, KnownExchangeCodes.micOf(symbol.exchangeCode), exchange.currency, type = null)
        }

    fun timeZoneOf(exchangeCode: ExchangeCode): ZoneId = exchangeOf(exchangeCode)?.timeZone ?: ZoneOffset.UTC

    fun timeZoneOf(listing: ListedSymbol): ZoneId =
        listing.mic?.let(::find)?.timeZone ?: timeZoneOf(listing.symbol.exchangeCode)

    private companion object {
        private const val NEW_YORK = "America/New_York"

        private val US_EXCHANGES_COMBINED = ExchangeInfo("US", ZoneId.of(NEW_YORK), Market.US, "USD")

        private val US_EXCHANGES = listOf(
            "XNYS" to "NYSE",
            "XNAS" to "NASDAQ",
            "XNGS" to "NASDAQ",
            "XNMS" to "NASDAQ",
            "XNCM" to "NASDAQ",
            "XASE" to "NYSE American",
            "ARCX" to "NYSE Arca",
            "BATS" to "Cboe BZX",
            "IEXG" to "IEX",
            "OTCM" to "OTC",
            "OOTC" to "OTC",
            "PINX" to "OTC",
            "PSGM" to "OTC",
            "EXPM" to "OTC",
            "OTCB" to "OTC",
            "OTCQ" to "OTC",
            "XOTC" to "OTC",
        ).map { (mic, name) -> mic to ExchangeInfo(name, ZoneId.of(NEW_YORK), Market.US, "USD") }

        private val EU_EXCHANGES = listOf(
            Exchange("XETR", "XETRA", "Europe/Berlin", "EUR"),
            Exchange("XFRA", "Frankfurt", "Europe/Berlin", "EUR"),
            Exchange("XBER", "Berlin", "Europe/Berlin", "EUR"),
            Exchange("XMUN", "Munich", "Europe/Berlin", "EUR"),
            Exchange("XSTU", "Stuttgart", "Europe/Berlin", "EUR"),
            Exchange("XDUS", "Dusseldorf", "Europe/Berlin", "EUR"),
            Exchange("XHAM", "Hamburg", "Europe/Berlin", "EUR"),
            Exchange("XHAN", "Hannover", "Europe/Berlin", "EUR"),
            Exchange("XPAR", "Euronext Paris", "Europe/Paris", "EUR"),
            Exchange("XAMS", "Euronext Amsterdam", "Europe/Amsterdam", "EUR"),
            Exchange("XBRU", "Euronext Brussels", "Europe/Brussels", "EUR"),
            Exchange("XLIS", "Euronext Lisbon", "Europe/Lisbon", "EUR"),
            Exchange("XMSM", "Euronext Dublin", "Europe/Dublin", "EUR"),
            Exchange("XDUB", "Euronext Dublin", "Europe/Dublin", "EUR"),
            Exchange("XOSL", "Oslo Bors", "Europe/Oslo", "NOK"),
            Exchange("XMIL", "Borsa Italiana", "Europe/Rome", "EUR"),
            Exchange("MTAA", "Borsa Italiana", "Europe/Rome", "EUR"),
            Exchange("XMAD", "BME", "Europe/Madrid", "EUR"),
            Exchange("XSTO", "Nasdaq Stockholm", "Europe/Stockholm", "SEK"),
            Exchange("XHEL", "Nasdaq Helsinki", "Europe/Helsinki", "EUR"),
            Exchange("XCSE", "Nasdaq Copenhagen", "Europe/Copenhagen", "DKK"),
            Exchange("XICE", "Nasdaq Iceland", "Atlantic/Reykjavik", "ISK"),
            Exchange("XRIS", "Nasdaq Riga", "Europe/Riga", "EUR"),
            Exchange("XTAL", "Nasdaq Tallinn", "Europe/Tallinn", "EUR"),
            Exchange("XLIT", "Nasdaq Vilnius", "Europe/Vilnius", "EUR"),
            Exchange("XWBO", "Vienna", "Europe/Vienna", "EUR"),
            Exchange("XWAR", "GPW", "Europe/Warsaw", "PLN"),
            Exchange("XPRA", "Prague", "Europe/Prague", "CZK"),
            Exchange("XBUD", "Budapest", "Europe/Budapest", "HUF"),
            Exchange("XATH", "Athens", "Europe/Athens", "EUR"),
            Exchange("XLON", "LSE", "Europe/London", "GBP"),
            Exchange("XSWX", "SIX", "Europe/Zurich", "CHF"),
            Exchange("XVTX", "SIX", "Europe/Zurich", "CHF"),
        ).map { it.mic to it.info(Market.EU) }

        private val OTHER_EXCHANGES = listOf(
            Exchange("XTKS", "TSE", "Asia/Tokyo", "JPY"),
            Exchange("XTSE", "TSX", "America/Toronto", "CAD"),
            Exchange("XTSX", "TSXV", "America/Toronto", "CAD"),
            Exchange("XCNQ", "CSE", "America/Toronto", "CAD"),
            Exchange("NEOE", "Cboe Canada", "America/Toronto", "CAD"),
            Exchange("XHKG", "HKEX", "Asia/Hong_Kong", "HKD"),
            Exchange("XASX", "ASX", "Australia/Sydney", "AUD"),
            Exchange("XNZE", "NZX", "Pacific/Auckland", "NZD"),
            Exchange("XSHG", "SSE", "Asia/Shanghai", "CNY"),
            Exchange("XSHE", "SZSE", "Asia/Shanghai", "CNY"),
            Exchange("XKRX", "KRX", "Asia/Seoul", "KRW"),
            Exchange("XKOS", "KOSDAQ", "Asia/Seoul", "KRW"),
            Exchange("XBOM", "BSE", "Asia/Kolkata", "INR"),
            Exchange("XNSE", "NSE", "Asia/Kolkata", "INR"),
            Exchange("XSES", "SGX", "Asia/Singapore", "SGD"),
            Exchange("XTAI", "TWSE", "Asia/Taipei", "TWD"),
            Exchange("ROCO", "TPEx", "Asia/Taipei", "TWD"),
            Exchange("XBKK", "SET", "Asia/Bangkok", "THB"),
            Exchange("XIDX", "IDX", "Asia/Jakarta", "IDR"),
            Exchange("XKLS", "Bursa Malaysia", "Asia/Kuala_Lumpur", "MYR"),
            Exchange("XPHS", "PSE", "Asia/Manila", "PHP"),
            Exchange("XSTC", "HOSE", "Asia/Ho_Chi_Minh", "VND"),
            Exchange("XIST", "Borsa Istanbul", "Europe/Istanbul", "TRY"),
            Exchange("MISX", "MOEX", "Europe/Moscow", "RUB"),
            Exchange("XTAE", "TASE", "Asia/Jerusalem", "ILS"),
            Exchange("XSAU", "Tadawul", "Asia/Riyadh", "SAR"),
            Exchange("DSMD", "QSE", "Asia/Qatar", "QAR"),
            Exchange("XDFM", "DFM", "Asia/Dubai", "AED"),
            Exchange("XKUW", "Boursa Kuwait", "Asia/Kuwait", "KWD"),
            Exchange("XBAH", "Bahrain Bourse", "Asia/Bahrain", "BHD"),
            Exchange("XJSE", "JSE", "Africa/Johannesburg", "ZAR"),
            Exchange("XCAI", "EGX", "Africa/Cairo", "EGP"),
            Exchange("XCAS", "Casablanca SE", "Africa/Casablanca", "MAD"),
            Exchange("XNSA", "NGX", "Africa/Lagos", "NGN"),
            Exchange("BVMF", "B3", "America/Sao_Paulo", "BRL"),
            Exchange("XMEX", "BMV", "America/Mexico_City", "MXN"),
            Exchange("XBUE", "BYMA", "America/Argentina/Buenos_Aires", "ARS"),
            Exchange("XSGO", "Santiago SE", "America/Santiago", "CLP"),
            Exchange("XBOG", "BVC", "America/Bogota", "COP"),
            Exchange("XCAR", "Caracas SE", "America/Caracas", "VES"),
        ).map { it.mic to it.info(market = null) }

        val EXCHANGES_BY_MIC: Map<String, ExchangeInfo> = (US_EXCHANGES + EU_EXCHANGES + OTHER_EXCHANGES).toMap()
    }

    private data class Exchange(val mic: String, val name: String, val zone: String, val currency: String) {
        fun info(market: Market?) = ExchangeInfo(name, ZoneId.of(zone), market, currency)
    }
}
