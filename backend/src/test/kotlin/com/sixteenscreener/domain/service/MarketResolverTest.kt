package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.ExchangeInfo
import com.sixteenscreener.domain.model.KnownExchangeCodes
import com.sixteenscreener.domain.model.Market
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.ZoneId
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
            Case("XASE", "AMEX", "America/New_York", Market.US),
            Case("ARCX", "Arca", "America/New_York", Market.US),
            Case("BATS", "BZX", "America/New_York", Market.US),
            Case("IEXG", "IEX", "America/New_York", Market.US),
            Case("OTCM", "OTC", "America/New_York", Market.US),
            Case("XETR", "XETRA", "Europe/Berlin", Market.EU),
            Case("XFRA", "FSE", "Europe/Berlin", Market.EU),
            Case("XPAR", "Paris", "Europe/Paris", Market.EU),
            Case("XAMS", "AMS", "Europe/Amsterdam", Market.EU),
            Case("XBRU", "BRU", "Europe/Brussels", Market.EU),
            Case("XLIS", "Lisbon", "Europe/Lisbon", Market.EU),
            Case("XMSM", "Dublin", "Europe/Dublin", Market.EU),
            Case("XOSL", "Oslo", "Europe/Oslo", Market.EU),
            Case("XMIL", "Milan", "Europe/Rome", Market.EU),
            Case("XMAD", "BME", "Europe/Madrid", Market.EU),
            Case("XSTO", "STO", "Europe/Stockholm", Market.EU),
            Case("XHEL", "HEL", "Europe/Helsinki", Market.EU),
            Case("XCSE", "CPH", "Europe/Copenhagen", Market.EU),
            Case("XWBO", "Vienna", "Europe/Vienna", Market.EU),
            Case("XWAR", "GPW", "Europe/Warsaw", Market.EU),
            Case("XLON", "LSE", "Europe/London", Market.EU),
            Case("XSWX", "SIX", "Europe/Zurich", Market.EU),
            Case("XTKS", "TSE", "Asia/Tokyo", null),
            Case("XTSE", "TSX", "America/Toronto", null),
            Case("XHKG", "HKEX", "Asia/Hong_Kong", null),
            Case("XASX", "ASX", "Australia/Sydney", null),
        ) { case ->
            val info = resolver.find(case.mic).shouldNotBeNull()
            info.name shouldBe case.name
            info.timeZone.id shouldBe case.zone
            info.market shouldBe case.market
        }
    }

    test("MIC lookup ignores case") {
        resolver.find("xetr")?.market shouldBe Market.EU
    }

    test("exchanges have their trading currency") {
        resolver.find("XNAS")?.currency shouldBe "USD"
        resolver.find("XAMS")?.currency shouldBe "EUR"
        resolver.find("XLON")?.currency shouldBe "GBP"
        resolver.find("XSWX")?.currency shouldBe "CHF"
        resolver.find("XSTO")?.currency shouldBe "SEK"
        resolver.find("XTKS")?.currency shouldBe "JPY"
    }

    data class CodeCase(val code: String, val name: String, val zone: String, val market: Market?, val currency: String)

    context("maps exchange codes to the static exchange table") {
        withData(
            nameFn = { "${it.code} -> ${it.name} (${it.market})" },
            CodeCase("US", "US", "America/New_York", Market.US, "USD"),
            CodeCase("AS", "AMS", "Europe/Amsterdam", Market.EU, "EUR"),
            CodeCase("PA", "Paris", "Europe/Paris", Market.EU, "EUR"),
            CodeCase("BR", "BRU", "Europe/Brussels", Market.EU, "EUR"),
            CodeCase("LS", "Lisbon", "Europe/Lisbon", Market.EU, "EUR"),
            CodeCase("IR", "Dublin", "Europe/Dublin", Market.EU, "EUR"),
            CodeCase("DE", "XETRA", "Europe/Berlin", Market.EU, "EUR"),
            CodeCase("F", "FSE", "Europe/Berlin", Market.EU, "EUR"),
            CodeCase("MI", "Milan", "Europe/Rome", Market.EU, "EUR"),
            CodeCase("MC", "BME", "Europe/Madrid", Market.EU, "EUR"),
            CodeCase("ST", "STO", "Europe/Stockholm", Market.EU, "SEK"),
            CodeCase("CO", "CPH", "Europe/Copenhagen", Market.EU, "DKK"),
            CodeCase("OL", "Oslo", "Europe/Oslo", Market.EU, "NOK"),
            CodeCase("WA", "GPW", "Europe/Warsaw", Market.EU, "PLN"),
            CodeCase("L", "LSE", "Europe/London", Market.EU, "GBP"),
            CodeCase("IL", "LSE", "Europe/London", Market.EU, "GBP"),
            CodeCase("SW", "SIX", "Europe/Zurich", Market.EU, "CHF"),
            CodeCase("T", "TSE", "Asia/Tokyo", null, "JPY"),
            CodeCase("TO", "TSX", "America/Toronto", null, "CAD"),
            CodeCase("HK", "HKEX", "Asia/Hong_Kong", null, "HKD"),
            CodeCase("IS", "BIST", "Europe/Istanbul", null, "TRY"),
            CodeCase("ME", "MOEX", "Europe/Moscow", null, "RUB"),
        ) { case ->
            resolver.exchangeOf(ExchangeCode(case.code)) shouldBe
                ExchangeInfo(case.name, ZoneId.of(case.zone), case.market, case.currency)
        }
    }

    test("every known exchange code is in the static exchange table") {
        KnownExchangeCodes.codes.filter { resolver.exchangeOf(it) == null }.shouldBeEmpty()
    }

    test("unknown exchange code is not in the static exchange table") {
        resolver.exchangeOf(ExchangeCode("XX")).shouldBeNull()
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

    context("maps MICs to the official exchange name") {
        withData(
            nameFn = { "${it.first} -> ${it.second}" },
            "XNYS" to "New York Stock Exchange",
            "XNGS" to "Nasdaq Global Select Market",
            "XNMS" to "Nasdaq Global Market",
            "XNCM" to "Nasdaq Capital Market",
            "OTCQ" to "OTCQX Best Market",
            "OTCB" to "OTCQB Venture Market",
            "PINX" to "OTC Pink Open Market",
            "XETR" to "Xetra",
            "XLON" to "London Stock Exchange",
            "XSWX" to "SIX Swiss Exchange",
            "XTSX" to "TSX Venture Exchange",
        ) { (mic, fullName) ->
            resolver.fullNameOf(mic) shouldBe fullName
        }
    }

    test("full name lookup ignores case; unknown MIC has no full name") {
        resolver.fullNameOf("xtsx") shouldBe "TSX Venture Exchange"
        resolver.fullNameOf("ZZZZ").shouldBeNull()
    }

    test("every MIC of the static exchange table has a full name") {
        allMicsOf(resolver).filter { resolver.find(it) == null || resolver.fullNameOf(it).isNullOrBlank() }.shouldBeEmpty()
    }

    test("US MICs are the US exchanges of the static exchange table") {
        resolver.usMics shouldContainExactly listOf(
            "XNYS", "XNAS", "XNGS", "XNMS", "XNCM", "XASE", "ARCX", "BATS", "IEXG",
            "OTCM", "OOTC", "PINX", "PSGM", "EXPM", "OTCB", "OTCQ", "XOTC",
        )
        resolver.usMics.map { resolver.find(it)?.market }.toSet() shouldBe setOf(Market.US)
    }

    test("every short name of the static exchange table is non-blank and fits under the ticker") {
        val exchanges = allMicsOf(resolver).map { resolver.find(it).shouldNotBeNull() }

        exchanges.filter { it.name.isBlank() || it.name.length > MAX_SHORT_NAME_LENGTH }.shouldBeEmpty()
    }

    test("a short name shared by several MICs belongs to one time zone and currency") {
        val exchanges = allMicsOf(resolver).map { resolver.find(it).shouldNotBeNull() }

        exchanges
            .groupBy(ExchangeInfo::name) { it.timeZone to it.currency }
            .filterValues { it.toSet().size > 1 }
            .keys
            .shouldBeEmpty()
    }

    context("maps MICs to short names abbreviated to fit under the ticker") {
        withData(
            nameFn = { "${it.first} -> ${it.second}" },
            "XAMS" to "AMS",
            "XFRA" to "FSE",
            "XBUD" to "BET",
            "XCAS" to "CasaSE",
            "XKUW" to "Kuwait",
            "XDUB" to "Dublin",
            "MTAA" to "Milan",
        ) { (mic, shortName) ->
            resolver.find(mic)?.name shouldBe shortName
        }
    }
})

private const val MAX_SHORT_NAME_LENGTH = 6

private fun allMicsOf(resolver: MarketResolver): List<String> =
    resolver.usMics + KnownExchangeCodes.mics.values + listOf("XMSM", "MTAA", "XVTX")
