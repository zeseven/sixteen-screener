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

    fun fullNameOf(mic: String): String? = FULL_NAMES_BY_MIC[mic.uppercase()]

    val usMics: List<String> get() = US_MICS

    private companion object {
        private const val NEW_YORK = "America/New_York"
        private const val USD = "USD"

        private val US_EXCHANGES_COMBINED = ExchangeInfo("US", ZoneId.of(NEW_YORK), Market.US, USD)

        private val US_EXCHANGES = listOf(
            Exchange("XNYS", "NYSE", "New York Stock Exchange", NEW_YORK, USD),
            Exchange("XNAS", "NASDAQ", "Nasdaq Stock Market", NEW_YORK, USD),
            Exchange("XNGS", "NASDAQ", "Nasdaq Global Select Market", NEW_YORK, USD),
            Exchange("XNMS", "NASDAQ", "Nasdaq Global Market", NEW_YORK, USD),
            Exchange("XNCM", "NASDAQ", "Nasdaq Capital Market", NEW_YORK, USD),
            Exchange("XASE", "AMEX", "NYSE American", NEW_YORK, USD),
            Exchange("ARCX", "Arca", "NYSE Arca", NEW_YORK, USD),
            Exchange("BATS", "BZX", "Cboe BZX Exchange", NEW_YORK, USD),
            Exchange("IEXG", "IEX", "Investors Exchange", NEW_YORK, USD),
            Exchange("OTCM", "OTC", "OTC Markets Group", NEW_YORK, USD),
            Exchange("OOTC", "OTC", "OTC Markets Other OTC", NEW_YORK, USD),
            Exchange("PINX", "OTC", "OTC Pink Open Market", NEW_YORK, USD),
            Exchange("PSGM", "OTC", "OTC Grey Market", NEW_YORK, USD),
            Exchange("EXPM", "OTC", "OTC Expert Market", NEW_YORK, USD),
            Exchange("OTCB", "OTC", "OTCQB Venture Market", NEW_YORK, USD),
            Exchange("OTCQ", "OTC", "OTCQX Best Market", NEW_YORK, USD),
            Exchange("XOTC", "OTC", "OTC Bulletin Board", NEW_YORK, USD),
        )

        private val EU_EXCHANGES = listOf(
            Exchange("XETR", "XETRA", "Xetra", "Europe/Berlin", "EUR"),
            Exchange("XFRA", "FSE", "Frankfurt Stock Exchange", "Europe/Berlin", "EUR"),
            Exchange("XBER", "Berlin", "Berlin Stock Exchange", "Europe/Berlin", "EUR"),
            Exchange("XMUN", "Munich", "Munich Stock Exchange", "Europe/Berlin", "EUR"),
            Exchange("XSTU", "STU", "Stuttgart Stock Exchange", "Europe/Berlin", "EUR"),
            Exchange("XDUS", "DUS", "Dusseldorf Stock Exchange", "Europe/Berlin", "EUR"),
            Exchange("XHAM", "HAM", "Hamburg Stock Exchange", "Europe/Berlin", "EUR"),
            Exchange("XHAN", "HAN", "Hanover Stock Exchange", "Europe/Berlin", "EUR"),
            Exchange("XPAR", "Paris", "Euronext Paris", "Europe/Paris", "EUR"),
            Exchange("XAMS", "AMS", "Euronext Amsterdam", "Europe/Amsterdam", "EUR"),
            Exchange("XBRU", "BRU", "Euronext Brussels", "Europe/Brussels", "EUR"),
            Exchange("XLIS", "Lisbon", "Euronext Lisbon", "Europe/Lisbon", "EUR"),
            Exchange("XMSM", "Dublin", "Euronext Dublin", "Europe/Dublin", "EUR"),
            Exchange("XDUB", "Dublin", "Euronext Dublin", "Europe/Dublin", "EUR"),
            Exchange("XOSL", "Oslo", "Euronext Oslo Bors", "Europe/Oslo", "NOK"),
            Exchange("XMIL", "Milan", "Borsa Italiana", "Europe/Rome", "EUR"),
            Exchange("MTAA", "Milan", "Euronext Milan", "Europe/Rome", "EUR"),
            Exchange("XMAD", "BME", "Madrid Stock Exchange", "Europe/Madrid", "EUR"),
            Exchange("XSTO", "STO", "Nasdaq Stockholm", "Europe/Stockholm", "SEK"),
            Exchange("XHEL", "HEL", "Nasdaq Helsinki", "Europe/Helsinki", "EUR"),
            Exchange("XCSE", "CPH", "Nasdaq Copenhagen", "Europe/Copenhagen", "DKK"),
            Exchange("XICE", "ICEX", "Nasdaq Iceland", "Atlantic/Reykjavik", "ISK"),
            Exchange("XRIS", "Riga", "Nasdaq Riga", "Europe/Riga", "EUR"),
            Exchange("XTAL", "TAL", "Nasdaq Tallinn", "Europe/Tallinn", "EUR"),
            Exchange("XLIT", "VIL", "Nasdaq Vilnius", "Europe/Vilnius", "EUR"),
            Exchange("XWBO", "Vienna", "Vienna Stock Exchange", "Europe/Vienna", "EUR"),
            Exchange("XWAR", "GPW", "Warsaw Stock Exchange", "Europe/Warsaw", "PLN"),
            Exchange("XPRA", "Prague", "Prague Stock Exchange", "Europe/Prague", "CZK"),
            Exchange("XBUD", "BET", "Budapest Stock Exchange", "Europe/Budapest", "HUF"),
            Exchange("XATH", "Athens", "Athens Exchange", "Europe/Athens", "EUR"),
            Exchange("XLON", "LSE", "London Stock Exchange", "Europe/London", "GBP"),
            Exchange("XSWX", "SIX", "SIX Swiss Exchange", "Europe/Zurich", "CHF"),
            Exchange("XVTX", "SIX", "SIX Swiss Exchange", "Europe/Zurich", "CHF"),
        )

        private val OTHER_EXCHANGES = listOf(
            Exchange("XTKS", "TSE", "Tokyo Stock Exchange", "Asia/Tokyo", "JPY"),
            Exchange("XTSE", "TSX", "Toronto Stock Exchange", "America/Toronto", "CAD"),
            Exchange("XTSX", "TSXV", "TSX Venture Exchange", "America/Toronto", "CAD"),
            Exchange("XCNQ", "CSE", "Canadian Securities Exchange", "America/Toronto", "CAD"),
            Exchange("NEOE", "NEO", "Cboe Canada", "America/Toronto", "CAD"),
            Exchange("XHKG", "HKEX", "Hong Kong Stock Exchange", "Asia/Hong_Kong", "HKD"),
            Exchange("XASX", "ASX", "Australian Securities Exchange", "Australia/Sydney", "AUD"),
            Exchange("XNZE", "NZX", "New Zealand Exchange", "Pacific/Auckland", "NZD"),
            Exchange("XSHG", "SSE", "Shanghai Stock Exchange", "Asia/Shanghai", "CNY"),
            Exchange("XSHE", "SZSE", "Shenzhen Stock Exchange", "Asia/Shanghai", "CNY"),
            Exchange("XKRX", "KRX", "Korea Exchange", "Asia/Seoul", "KRW"),
            Exchange("XKOS", "KOSDAQ", "KOSDAQ Market", "Asia/Seoul", "KRW"),
            Exchange("XBOM", "BSE", "Bombay Stock Exchange", "Asia/Kolkata", "INR"),
            Exchange("XNSE", "NSE", "National Stock Exchange of India", "Asia/Kolkata", "INR"),
            Exchange("XSES", "SGX", "Singapore Exchange", "Asia/Singapore", "SGD"),
            Exchange("XTAI", "TWSE", "Taiwan Stock Exchange", "Asia/Taipei", "TWD"),
            Exchange("ROCO", "TPEx", "Taipei Exchange", "Asia/Taipei", "TWD"),
            Exchange("XBKK", "SET", "Stock Exchange of Thailand", "Asia/Bangkok", "THB"),
            Exchange("XIDX", "IDX", "Indonesia Stock Exchange", "Asia/Jakarta", "IDR"),
            Exchange("XKLS", "Bursa", "Bursa Malaysia", "Asia/Kuala_Lumpur", "MYR"),
            Exchange("XPHS", "PSE", "Philippine Stock Exchange", "Asia/Manila", "PHP"),
            Exchange("XSTC", "HOSE", "Ho Chi Minh Stock Exchange", "Asia/Ho_Chi_Minh", "VND"),
            Exchange("XIST", "BIST", "Borsa Istanbul", "Europe/Istanbul", "TRY"),
            Exchange("MISX", "MOEX", "Moscow Exchange", "Europe/Moscow", "RUB"),
            Exchange("XTAE", "TASE", "Tel Aviv Stock Exchange", "Asia/Jerusalem", "ILS"),
            Exchange("XSAU", "Saudi", "Saudi Exchange", "Asia/Riyadh", "SAR"),
            Exchange("DSMD", "QSE", "Qatar Stock Exchange", "Asia/Qatar", "QAR"),
            Exchange("XDFM", "DFM", "Dubai Financial Market", "Asia/Dubai", "AED"),
            Exchange("XKUW", "Kuwait", "Boursa Kuwait", "Asia/Kuwait", "KWD"),
            Exchange("XBAH", "BHB", "Bahrain Bourse", "Asia/Bahrain", "BHD"),
            Exchange("XJSE", "JSE", "Johannesburg Stock Exchange", "Africa/Johannesburg", "ZAR"),
            Exchange("XCAI", "EGX", "Egyptian Exchange", "Africa/Cairo", "EGP"),
            Exchange("XCAS", "CasaSE", "Casablanca Stock Exchange", "Africa/Casablanca", "MAD"),
            Exchange("XNSA", "NGX", "Nigerian Exchange", "Africa/Lagos", "NGN"),
            Exchange("BVMF", "B3", "B3 - Brasil Bolsa Balcao", "America/Sao_Paulo", "BRL"),
            Exchange("XMEX", "BMV", "Mexican Stock Exchange", "America/Mexico_City", "MXN"),
            Exchange("XBUE", "BYMA", "Bolsas y Mercados Argentinos", "America/Argentina/Buenos_Aires", "ARS"),
            Exchange("XSGO", "BCS", "Santiago Stock Exchange", "America/Santiago", "CLP"),
            Exchange("XBOG", "BVC", "Colombian Stock Exchange", "America/Bogota", "COP"),
            Exchange("XCAR", "BVCC", "Caracas Stock Exchange", "America/Caracas", "VES"),
        )

        val EXCHANGES_BY_MIC: Map<String, ExchangeInfo> = (
            US_EXCHANGES.map { it.mic to it.info(Market.US) } +
                EU_EXCHANGES.map { it.mic to it.info(Market.EU) } +
                OTHER_EXCHANGES.map { it.mic to it.info(market = null) }
            ).toMap()

        val FULL_NAMES_BY_MIC: Map<String, String> =
            (US_EXCHANGES + EU_EXCHANGES + OTHER_EXCHANGES).associate { it.mic to it.fullName }

        val US_MICS: List<String> = US_EXCHANGES.map { it.mic }
    }

    private data class Exchange(
        val mic: String,
        val name: String,
        val fullName: String,
        val zone: String,
        val currency: String,
    ) {
        fun info(market: Market?) = ExchangeInfo(name, ZoneId.of(zone), market, currency)
    }
}
