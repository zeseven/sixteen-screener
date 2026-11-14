package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.Market
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import java.time.ZoneOffset

class MarketResolverTest : FunSpec({
    val resolver = MarketResolver()

    data class Case(val mic: String, val name: String, val zone: String, val market: Market?)

    context("maps MICs to exchange name, time zone and market") {
        withData(
            nameFn = { "${it.mic} -> ${it.name} (${it.market})" },
            Case("XNYS", "NYSE", "America/New_York", Market.US),
            Case("XNAS", "NASDAQ", "America/New_York", Market.US),
            Case("XNGS", "NASDAQ", "America/New_York", Market.US),
            Case("XASE", "NYSE American", "America/New_York", Market.US),
            Case("ARCX", "NYSE Arca", "America/New_York", Market.US),
            Case("BATS", "Cboe BZX", "America/New_York", Market.US),
            Case("IEXG", "IEX", "America/New_York", Market.US),
            Case("OTCM", "OTC", "America/New_York", Market.US),
            Case("XETR", "XETRA", "Europe/Berlin", Market.EU),
            Case("XFRA", "Frankfurt", "Europe/Berlin", Market.EU),
            Case("XPAR", "Euronext Paris", "Europe/Paris", Market.EU),
            Case("XAMS", "Euronext Amsterdam", "Europe/Amsterdam", Market.EU),
            Case("XBRU", "Euronext Brussels", "Europe/Brussels", Market.EU),
            Case("XLIS", "Euronext Lisbon", "Europe/Lisbon", Market.EU),
            Case("XMSM", "Euronext Dublin", "Europe/Dublin", Market.EU),
            Case("XOSL", "Oslo Bors", "Europe/Oslo", Market.EU),
            Case("XMIL", "Borsa Italiana", "Europe/Rome", Market.EU),
            Case("XMAD", "BME", "Europe/Madrid", Market.EU),
            Case("XSTO", "Nasdaq Stockholm", "Europe/Stockholm", Market.EU),
            Case("XHEL", "Nasdaq Helsinki", "Europe/Helsinki", Market.EU),
            Case("XCSE", "Nasdaq Copenhagen", "Europe/Copenhagen", Market.EU),
            Case("XWBO", "Vienna", "Europe/Vienna", Market.EU),
            Case("XWAR", "GPW", "Europe/Warsaw", Market.EU),
            Case("XLON", "LSE", "Europe/London", Market.EU),
            Case("XSWX", "SIX", "Europe/Zurich", Market.EU),
            Case("XTKS", "TSE", "Asia/Tokyo", null),
            Case("XTSE", "TSX", "America/Toronto", null),
            Case("XHKG", "HKEX", "Asia/Hong_Kong", null),
            Case("XASX", "ASX", "Australia/Sydney", null),
        ) { case ->
            val info = resolver.resolve(case.mic)
            info.name shouldBe case.name
            info.timeZone.id shouldBe case.zone
            info.market shouldBe case.market
        }
    }

    test("MIC lookup ignores case") {
        resolver.resolve("xetr").market shouldBe Market.EU
    }

    test("unknown MIC has no market, the MIC as name and UTC") {
        val info = resolver.resolve("ZZZZ")
        info.name shouldBe "ZZZZ"
        info.timeZone shouldBe ZoneOffset.UTC
        info.market shouldBe null
    }

    test("time zone of known and unknown exchange codes") {
        resolver.timeZoneOf(ExchangeCode.US).id shouldBe "America/New_York"
        resolver.timeZoneOf(ExchangeCode("L")).id shouldBe "Europe/London"
        resolver.timeZoneOf(ExchangeCode("XX")) shouldBe ZoneOffset.UTC
    }

    test("unknown MIC is not found") {
        resolver.find("ZZZZ") shouldBe null
        resolver.find("xnas")?.name shouldBe "NASDAQ"
    }

    test("time zone of a listing comes from its MIC, otherwise from its exchange code") {
        resolver.timeZoneOf(listing("SAP.DE", mic = "XETR")).id shouldBe "Europe/Berlin"
        resolver.timeZoneOf(listing("SHEL.L", mic = null)).id shouldBe "Europe/London"
        resolver.timeZoneOf(listing("SHEL.L", mic = "ZZZZ")).id shouldBe "Europe/London"
        resolver.timeZoneOf(listing("AAPL", mic = "XNAS")).id shouldBe "America/New_York"
    }
})
