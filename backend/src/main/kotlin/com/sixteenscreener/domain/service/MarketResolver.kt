package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.ExchangeInfo
import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.Market
import java.time.ZoneId
import java.time.ZoneOffset

class MarketResolver {

    fun resolve(mic: String): ExchangeInfo =
        find(mic) ?: ExchangeInfo(name = mic, timeZone = ZoneOffset.UTC, market = null)

    fun find(mic: String): ExchangeInfo? = EXCHANGES_BY_MIC[mic.uppercase()]

    fun timeZoneOf(exchangeCode: ExchangeCode): ZoneId = exchangeCode.timeZone ?: ZoneOffset.UTC

    fun timeZoneOf(listing: ListedSymbol): ZoneId =
        listing.mic?.let(::find)?.timeZone ?: timeZoneOf(listing.symbol.exchangeCode)

    private companion object {
        private const val NEW_YORK = "America/New_York"

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
        ).map { (mic, name) -> mic to ExchangeInfo(name, ZoneId.of(NEW_YORK), Market.US) }

        private val EU_EXCHANGES = listOf(
            Triple("XETR", "XETRA", "Europe/Berlin"),
            Triple("XFRA", "Frankfurt", "Europe/Berlin"),
            Triple("XBER", "Berlin", "Europe/Berlin"),
            Triple("XMUN", "Munich", "Europe/Berlin"),
            Triple("XSTU", "Stuttgart", "Europe/Berlin"),
            Triple("XDUS", "Dusseldorf", "Europe/Berlin"),
            Triple("XHAM", "Hamburg", "Europe/Berlin"),
            Triple("XHAN", "Hannover", "Europe/Berlin"),
            Triple("XPAR", "Euronext Paris", "Europe/Paris"),
            Triple("XAMS", "Euronext Amsterdam", "Europe/Amsterdam"),
            Triple("XBRU", "Euronext Brussels", "Europe/Brussels"),
            Triple("XLIS", "Euronext Lisbon", "Europe/Lisbon"),
            Triple("XMSM", "Euronext Dublin", "Europe/Dublin"),
            Triple("XDUB", "Euronext Dublin", "Europe/Dublin"),
            Triple("XOSL", "Oslo Bors", "Europe/Oslo"),
            Triple("XMIL", "Borsa Italiana", "Europe/Rome"),
            Triple("MTAA", "Borsa Italiana", "Europe/Rome"),
            Triple("XMAD", "BME", "Europe/Madrid"),
            Triple("XSTO", "Nasdaq Stockholm", "Europe/Stockholm"),
            Triple("XHEL", "Nasdaq Helsinki", "Europe/Helsinki"),
            Triple("XCSE", "Nasdaq Copenhagen", "Europe/Copenhagen"),
            Triple("XICE", "Nasdaq Iceland", "Atlantic/Reykjavik"),
            Triple("XRIS", "Nasdaq Riga", "Europe/Riga"),
            Triple("XTAL", "Nasdaq Tallinn", "Europe/Tallinn"),
            Triple("XLIT", "Nasdaq Vilnius", "Europe/Vilnius"),
            Triple("XWBO", "Vienna", "Europe/Vienna"),
            Triple("XWAR", "GPW", "Europe/Warsaw"),
            Triple("XPRA", "Prague", "Europe/Prague"),
            Triple("XBUD", "Budapest", "Europe/Budapest"),
            Triple("XATH", "Athens", "Europe/Athens"),
            Triple("XLON", "LSE", "Europe/London"),
            Triple("XSWX", "SIX", "Europe/Zurich"),
            Triple("XVTX", "SIX", "Europe/Zurich"),
        ).map { (mic, name, zone) -> mic to ExchangeInfo(name, ZoneId.of(zone), Market.EU) }

        private val OTHER_EXCHANGES = listOf(
            Triple("XTKS", "TSE", "Asia/Tokyo"),
            Triple("XTSE", "TSX", "America/Toronto"),
            Triple("XTSX", "TSXV", "America/Toronto"),
            Triple("XCNQ", "CSE", "America/Toronto"),
            Triple("NEOE", "Cboe Canada", "America/Toronto"),
            Triple("XHKG", "HKEX", "Asia/Hong_Kong"),
            Triple("XASX", "ASX", "Australia/Sydney"),
            Triple("XNZE", "NZX", "Pacific/Auckland"),
            Triple("XSHG", "SSE", "Asia/Shanghai"),
            Triple("XSHE", "SZSE", "Asia/Shanghai"),
            Triple("XKRX", "KRX", "Asia/Seoul"),
            Triple("XKOS", "KOSDAQ", "Asia/Seoul"),
            Triple("XBOM", "BSE", "Asia/Kolkata"),
            Triple("XNSE", "NSE", "Asia/Kolkata"),
            Triple("XSES", "SGX", "Asia/Singapore"),
            Triple("XTAI", "TWSE", "Asia/Taipei"),
            Triple("XIST", "Borsa Istanbul", "Europe/Istanbul"),
            Triple("XTAE", "TASE", "Asia/Jerusalem"),
            Triple("XJSE", "JSE", "Africa/Johannesburg"),
            Triple("BVMF", "B3", "America/Sao_Paulo"),
            Triple("XMEX", "BMV", "America/Mexico_City"),
        ).map { (mic, name, zone) -> mic to ExchangeInfo(name, ZoneId.of(zone), null) }

        val EXCHANGES_BY_MIC: Map<String, ExchangeInfo> = (US_EXCHANGES + EU_EXCHANGES + OTHER_EXCHANGES).toMap()
    }
}
