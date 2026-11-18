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
        private const val UNITED_STATES = "US"

        private val US_EXCHANGES_COMBINED =
            ExchangeInfo("US", ZoneId.of(NEW_YORK), Market.US, USD, UNITED_STATES, mainVenue = false)

        private val US_EXCHANGES = listOf(
            Exchange("XNYS", "NYSE", "New York Stock Exchange", NEW_YORK, USD, UNITED_STATES),
            Exchange("XNAS", "NASDAQ", "Nasdaq Stock Market", NEW_YORK, USD, UNITED_STATES),
            Exchange("XNGS", "NASDAQ", "Nasdaq Global Select Market", NEW_YORK, USD, UNITED_STATES),
            Exchange("XNMS", "NASDAQ", "Nasdaq Global Market", NEW_YORK, USD, UNITED_STATES),
            Exchange("XNCM", "NASDAQ", "Nasdaq Capital Market", NEW_YORK, USD, UNITED_STATES),
            Exchange("XASE", "AMEX", "NYSE American", NEW_YORK, USD, UNITED_STATES),
            Exchange("ARCX", "Arca", "NYSE Arca", NEW_YORK, USD, UNITED_STATES),
            Exchange("BATS", "BZX", "Cboe BZX Exchange", NEW_YORK, USD, UNITED_STATES),
            Exchange("IEXG", "IEX", "Investors Exchange", NEW_YORK, USD, UNITED_STATES),
            Exchange("OTCM", "OTC", "OTC Markets Group", NEW_YORK, USD, UNITED_STATES, mainVenue = false),
            Exchange("OOTC", "OTC", "OTC Markets Other OTC", NEW_YORK, USD, UNITED_STATES, mainVenue = false),
            Exchange("PINX", "OTC", "OTC Pink Open Market", NEW_YORK, USD, UNITED_STATES, mainVenue = false),
            Exchange("PSGM", "OTC", "OTC Grey Market", NEW_YORK, USD, UNITED_STATES, mainVenue = false),
            Exchange("EXPM", "OTC", "OTC Expert Market", NEW_YORK, USD, UNITED_STATES, mainVenue = false),
            Exchange("OTCB", "OTC", "OTCQB Venture Market", NEW_YORK, USD, UNITED_STATES, mainVenue = false),
            Exchange("OTCQ", "OTC", "OTCQX Best Market", NEW_YORK, USD, UNITED_STATES, mainVenue = false),
            Exchange("XOTC", "OTC", "OTC Bulletin Board", NEW_YORK, USD, UNITED_STATES, mainVenue = false),
        )

        private val EU_EXCHANGES = listOf(
            Exchange("XETR", "XETRA", "Xetra", "Europe/Berlin", "EUR", "DE"),
            Exchange("XFRA", "FSE", "Frankfurt Stock Exchange", "Europe/Berlin", "EUR", "DE", mainVenue = false),
            Exchange("XBER", "Berlin", "Berlin Stock Exchange", "Europe/Berlin", "EUR", "DE", mainVenue = false),
            Exchange("XMUN", "Munich", "Munich Stock Exchange", "Europe/Berlin", "EUR", "DE", mainVenue = false),
            Exchange("XSTU", "STU", "Stuttgart Stock Exchange", "Europe/Berlin", "EUR", "DE", mainVenue = false),
            Exchange("XDUS", "DUS", "Dusseldorf Stock Exchange", "Europe/Berlin", "EUR", "DE", mainVenue = false),
            Exchange("XHAM", "HAM", "Hamburg Stock Exchange", "Europe/Berlin", "EUR", "DE", mainVenue = false),
            Exchange("XHAN", "HAN", "Hanover Stock Exchange", "Europe/Berlin", "EUR", "DE", mainVenue = false),
            Exchange("XPAR", "Paris", "Euronext Paris", "Europe/Paris", "EUR", "FR"),
            Exchange("XAMS", "AMS", "Euronext Amsterdam", "Europe/Amsterdam", "EUR", "NL"),
            Exchange("XBRU", "BRU", "Euronext Brussels", "Europe/Brussels", "EUR", "BE"),
            Exchange("XLIS", "Lisbon", "Euronext Lisbon", "Europe/Lisbon", "EUR", "PT"),
            Exchange("XMSM", "Dublin", "Euronext Dublin", "Europe/Dublin", "EUR", "IE"),
            Exchange("XDUB", "Dublin", "Euronext Dublin", "Europe/Dublin", "EUR", "IE"),
            Exchange("XOSL", "Oslo", "Euronext Oslo Bors", "Europe/Oslo", "NOK", "NO"),
            Exchange("XMIL", "Milan", "Borsa Italiana", "Europe/Rome", "EUR", "IT"),
            Exchange("MTAA", "Milan", "Euronext Milan", "Europe/Rome", "EUR", "IT"),
            Exchange("XMAD", "BME", "Madrid Stock Exchange", "Europe/Madrid", "EUR", "ES"),
            Exchange("XSTO", "STO", "Nasdaq Stockholm", "Europe/Stockholm", "SEK", "SE"),
            Exchange("XHEL", "HEL", "Nasdaq Helsinki", "Europe/Helsinki", "EUR", "FI"),
            Exchange("XCSE", "CPH", "Nasdaq Copenhagen", "Europe/Copenhagen", "DKK", "DK"),
            Exchange("XICE", "ICEX", "Nasdaq Iceland", "Atlantic/Reykjavik", "ISK", "IS"),
            Exchange("XRIS", "Riga", "Nasdaq Riga", "Europe/Riga", "EUR", "LV"),
            Exchange("XTAL", "TAL", "Nasdaq Tallinn", "Europe/Tallinn", "EUR", "EE"),
            Exchange("XLIT", "VIL", "Nasdaq Vilnius", "Europe/Vilnius", "EUR", "LT"),
            Exchange("XWBO", "Vienna", "Vienna Stock Exchange", "Europe/Vienna", "EUR", "AT"),
            Exchange("XWAR", "GPW", "Warsaw Stock Exchange", "Europe/Warsaw", "PLN", "PL"),
            Exchange("XPRA", "Prague", "Prague Stock Exchange", "Europe/Prague", "CZK", "CZ"),
            Exchange("XBUD", "BET", "Budapest Stock Exchange", "Europe/Budapest", "HUF", "HU"),
            Exchange("XATH", "Athens", "Athens Exchange", "Europe/Athens", "EUR", "GR"),
            Exchange("XLON", "LSE", "London Stock Exchange", "Europe/London", "GBP", "GB"),
            Exchange("XSWX", "SIX", "SIX Swiss Exchange", "Europe/Zurich", "CHF", "CH"),
            Exchange("XVTX", "SIX", "SIX Swiss Exchange", "Europe/Zurich", "CHF", "CH"),
        )

        private val OTHER_EXCHANGES = listOf(
            Exchange("XTKS", "TSE", "Tokyo Stock Exchange", "Asia/Tokyo", "JPY", "JP"),
            Exchange("XTSE", "TSX", "Toronto Stock Exchange", "America/Toronto", "CAD", "CA"),
            Exchange("XTSX", "TSXV", "TSX Venture Exchange", "America/Toronto", "CAD", "CA"),
            Exchange("XCNQ", "CSE", "Canadian Securities Exchange", "America/Toronto", "CAD", "CA"),
            Exchange("NEOE", "NEO", "Cboe Canada", "America/Toronto", "CAD", "CA"),
            Exchange("XHKG", "HKEX", "Hong Kong Stock Exchange", "Asia/Hong_Kong", "HKD", "HK"),
            Exchange("XASX", "ASX", "Australian Securities Exchange", "Australia/Sydney", "AUD", "AU"),
            Exchange("XNZE", "NZX", "New Zealand Exchange", "Pacific/Auckland", "NZD", "NZ"),
            Exchange("XSHG", "SSE", "Shanghai Stock Exchange", "Asia/Shanghai", "CNY", "CN"),
            Exchange("XSHE", "SZSE", "Shenzhen Stock Exchange", "Asia/Shanghai", "CNY", "CN"),
            Exchange("XKRX", "KRX", "Korea Exchange", "Asia/Seoul", "KRW", "KR"),
            Exchange("XKOS", "KOSDAQ", "KOSDAQ Market", "Asia/Seoul", "KRW", "KR"),
            Exchange("XBOM", "BSE", "Bombay Stock Exchange", "Asia/Kolkata", "INR", "IN"),
            Exchange("XNSE", "NSE", "National Stock Exchange of India", "Asia/Kolkata", "INR", "IN"),
            Exchange("XSES", "SGX", "Singapore Exchange", "Asia/Singapore", "SGD", "SG"),
            Exchange("XTAI", "TWSE", "Taiwan Stock Exchange", "Asia/Taipei", "TWD", "TW"),
            Exchange("ROCO", "TPEx", "Taipei Exchange", "Asia/Taipei", "TWD", "TW"),
            Exchange("XBKK", "SET", "Stock Exchange of Thailand", "Asia/Bangkok", "THB", "TH"),
            Exchange("XIDX", "IDX", "Indonesia Stock Exchange", "Asia/Jakarta", "IDR", "ID"),
            Exchange("XKLS", "Bursa", "Bursa Malaysia", "Asia/Kuala_Lumpur", "MYR", "MY"),
            Exchange("XPHS", "PSE", "Philippine Stock Exchange", "Asia/Manila", "PHP", "PH"),
            Exchange("XSTC", "HOSE", "Ho Chi Minh Stock Exchange", "Asia/Ho_Chi_Minh", "VND", "VN"),
            Exchange("XIST", "BIST", "Borsa Istanbul", "Europe/Istanbul", "TRY", "TR"),
            Exchange("MISX", "MOEX", "Moscow Exchange", "Europe/Moscow", "RUB", "RU"),
            Exchange("XTAE", "TASE", "Tel Aviv Stock Exchange", "Asia/Jerusalem", "ILS", "IL"),
            Exchange("XSAU", "Saudi", "Saudi Exchange", "Asia/Riyadh", "SAR", "SA"),
            Exchange("DSMD", "QSE", "Qatar Stock Exchange", "Asia/Qatar", "QAR", "QA"),
            Exchange("XDFM", "DFM", "Dubai Financial Market", "Asia/Dubai", "AED", "AE"),
            Exchange("XKUW", "Kuwait", "Boursa Kuwait", "Asia/Kuwait", "KWD", "KW"),
            Exchange("XBAH", "BHB", "Bahrain Bourse", "Asia/Bahrain", "BHD", "BH"),
            Exchange("XJSE", "JSE", "Johannesburg Stock Exchange", "Africa/Johannesburg", "ZAR", "ZA"),
            Exchange("XCAI", "EGX", "Egyptian Exchange", "Africa/Cairo", "EGP", "EG"),
            Exchange("XCAS", "CasaSE", "Casablanca Stock Exchange", "Africa/Casablanca", "MAD", "MA"),
            Exchange("XNSA", "NGX", "Nigerian Exchange", "Africa/Lagos", "NGN", "NG"),
            Exchange("BVMF", "B3", "B3 - Brasil Bolsa Balcao", "America/Sao_Paulo", "BRL", "BR"),
            Exchange("XMEX", "BMV", "Mexican Stock Exchange", "America/Mexico_City", "MXN", "MX"),
            Exchange("XBUE", "BYMA", "Bolsas y Mercados Argentinos", "America/Argentina/Buenos_Aires", "ARS", "AR"),
            Exchange("XSGO", "BCS", "Santiago Stock Exchange", "America/Santiago", "CLP", "CL"),
            Exchange("XBOG", "BVC", "Colombian Stock Exchange", "America/Bogota", "COP", "CO"),
            Exchange("XCAR", "BVCC", "Caracas Stock Exchange", "America/Caracas", "VES", "VE"),
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
        val country: String,
        val mainVenue: Boolean = true,
    ) {
        fun info(market: Market?) = ExchangeInfo(name, ZoneId.of(zone), market, currency, country, mainVenue)
    }
}
