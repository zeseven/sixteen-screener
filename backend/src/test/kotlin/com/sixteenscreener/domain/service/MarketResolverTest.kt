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
import java.util.Locale

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

    data class CodeCase(
        val code: String,
        val name: String,
        val zone: String,
        val market: Market?,
        val currency: String,
        val country: String,
        val mainVenue: Boolean = true,
    )

    context("maps exchange codes to the static exchange table") {
        withData(
            nameFn = { "${it.code} -> ${it.name} (${it.market})" },
            CodeCase("US", "US", "America/New_York", Market.US, "USD", "US", mainVenue = false),
            CodeCase("AS", "AMS", "Europe/Amsterdam", Market.EU, "EUR", "NL"),
            CodeCase("PA", "Paris", "Europe/Paris", Market.EU, "EUR", "FR"),
            CodeCase("BR", "BRU", "Europe/Brussels", Market.EU, "EUR", "BE"),
            CodeCase("LS", "Lisbon", "Europe/Lisbon", Market.EU, "EUR", "PT"),
            CodeCase("IR", "Dublin", "Europe/Dublin", Market.EU, "EUR", "IE"),
            CodeCase("DE", "XETRA", "Europe/Berlin", Market.EU, "EUR", "DE"),
            CodeCase("F", "FSE", "Europe/Berlin", Market.EU, "EUR", "DE", mainVenue = false),
            CodeCase("MI", "Milan", "Europe/Rome", Market.EU, "EUR", "IT"),
            CodeCase("MC", "BME", "Europe/Madrid", Market.EU, "EUR", "ES"),
            CodeCase("ST", "STO", "Europe/Stockholm", Market.EU, "SEK", "SE"),
            CodeCase("CO", "CPH", "Europe/Copenhagen", Market.EU, "DKK", "DK"),
            CodeCase("OL", "Oslo", "Europe/Oslo", Market.EU, "NOK", "NO"),
            CodeCase("WA", "GPW", "Europe/Warsaw", Market.EU, "PLN", "PL"),
            CodeCase("L", "LSE", "Europe/London", Market.EU, "GBP", "GB"),
            CodeCase("IL", "LSE", "Europe/London", Market.EU, "GBP", "GB"),
            CodeCase("SW", "SIX", "Europe/Zurich", Market.EU, "CHF", "CH"),
            CodeCase("T", "TSE", "Asia/Tokyo", null, "JPY", "JP"),
            CodeCase("TO", "TSX", "America/Toronto", null, "CAD", "CA"),
            CodeCase("HK", "HKEX", "Asia/Hong_Kong", null, "HKD", "HK"),
            CodeCase("IS", "BIST", "Europe/Istanbul", null, "TRY", "TR"),
            CodeCase("ME", "MOEX", "Europe/Moscow", null, "RUB", "RU"),
        ) { case ->
            resolver.exchangeOf(ExchangeCode(case.code)) shouldBe
                ExchangeInfo(case.name, ZoneId.of(case.zone), case.market, case.currency, case.country, case.mainVenue)
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

    test("every exchange of the static exchange table is in a valid ISO 3166-1 alpha-2 country") {
        val isoCountries = Locale.getISOCountries().toSet()

        allMicsOf(resolver)
            .filterNot { mic -> resolver.find(mic).shouldNotBeNull().country.let { it in isoCountries && it == it.uppercase() } }
            .shouldBeEmpty()
    }

    test("only the German regional exchanges and the US OTC markets are not main venues") {
        allMicsOf(resolver).filterNot { resolver.find(it).shouldNotBeNull().mainVenue }.toSet() shouldBe setOf(
            "XFRA", "XBER", "XMUN", "XSTU", "XDUS", "XHAM", "XHAN",
            "OTCM", "OOTC", "PINX", "PSGM", "EXPM", "OTCB", "OTCQ", "XOTC",
        )
    }

    test("the combined US exchanges are in the US and not a main venue") {
        val combined = resolver.exchangeOf(ExchangeCode.US).shouldNotBeNull()

        combined.country shouldBe "US"
        combined.mainVenue shouldBe false
    }

    context("maps MICs to the country of the exchange") {
        withData(
            nameFn = { "${it.first} -> ${it.second}" },
            "XNYS" to "US",
            "PINX" to "US",
            "XETR" to "DE",
            "XHAN" to "DE",
            "XMSM" to "IE",
            "MTAA" to "IT",
            "XVTX" to "CH",
            "XLON" to "GB",
            "NEOE" to "CA",
            "XSHE" to "CN",
            "XKOS" to "KR",
            "ROCO" to "TW",
            "DSMD" to "QA",
            "XCAR" to "VE",
        ) { (mic, country) ->
            resolver.find(mic)?.country shouldBe country
        }
    }

    data class HomeCase(val mic: String, val companyCountry: String?, val home: Boolean)

    context("an exchange is the home of companies of its country only when it is a main venue") {
        withData(
            nameFn = { "${it.mic} for ${it.companyCountry} -> ${it.home}" },
            HomeCase("XETR", "DE", true),
            HomeCase("XETR", "de", true),
            HomeCase("XETR", " DE ", true),
            HomeCase("XETR", "US", false),
            HomeCase("XETR", null, false),
            HomeCase("XETR", "", false),
            HomeCase("XFRA", "DE", false),
            HomeCase("XNAS", "US", true),
            HomeCase("PINX", "US", false),
        ) { case ->
            resolver.find(case.mic).shouldNotBeNull().isHomeOf(case.companyCountry) shouldBe case.home
        }
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
