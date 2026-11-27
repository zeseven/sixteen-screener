package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.ExchangeEntry
import com.sixteenscreener.domain.model.KnownExchangeCodes
import com.sixteenscreener.domain.model.Market
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.time.ZoneId

class ExchangeServiceTest : FunSpec({
    val resolver = MarketResolver()
    val entries = ExchangeService(resolver).exchanges()

    fun entry(suffix: String) = entries.single { it.suffix == suffix }

    test("every known non-US suffix gives exactly one row with the MIC of its exchange code") {
        val suffixRows = entries.filter { it.suffix.isNotEmpty() }

        suffixRows.map { it.suffix to it.mic } shouldContainExactlyInAnyOrder
            KnownExchangeCodes.mics.map { (code, mic) -> code.value to mic }
    }

    test("suffixes of the same exchange give separate rows") {
        entries.filter { it.mic == "XLON" }.map { it.suffix } shouldContainExactly listOf("IL", "L")
    }

    test("every US exchange gives one row with an empty suffix") {
        entries.filter { it.suffix.isEmpty() }.map { it.mic } shouldContainExactlyInAnyOrder resolver.usMics
    }

    test("there is no row for the US exchange code") {
        entries.filter { it.suffix == "US" }.shouldBeEmpty()
    }

    test("exchanges that are neither US nor the target of a suffix are not listed") {
        entries.filter { it.mic in setOf("XMSM", "MTAA", "XVTX") }.shouldBeEmpty()
    }

    test("rows are sorted by suffix, then MIC, US exchanges first") {
        entries shouldBe entries.sortedWith(compareBy(ExchangeEntry::suffix, ExchangeEntry::mic))
        entries.first().suffix shouldBe ""
        entries.takeWhile { it.suffix.isEmpty() }.map { it.mic } shouldBe resolver.usMics.sorted()
    }

    test("every row has a full name") {
        entries.filter { it.fullName.isBlank() }.shouldBeEmpty()
    }

    test("rows carry the attributes of the static exchange table") {
        entries.filter { row ->
            val info = resolver.find(row.mic)
            info == null || info.name != row.shortName || info.currency != row.currency ||
                info.timeZone != row.timeZone || info.market != row.market || info.country != row.country ||
                info.mainVenue != row.mainVenue
        }.shouldBeEmpty()
    }

    test("a row outside the US and EU markets has no market") {
        entry("V") shouldBe ExchangeEntry(
            suffix = "V",
            shortName = "TSXV",
            fullName = "TSX Venture Exchange",
            mic = "XTSX",
            eodhdCode = "V",
            yahooSuffix = "V",
            currency = "CAD",
            timeZone = ZoneId.of("America/Toronto"),
            market = null,
            country = "CA",
            mainVenue = true,
        )
    }

    test("a row of an EU exchange belongs to the EU market") {
        entry("AS") shouldBe ExchangeEntry(
            suffix = "AS",
            shortName = "AMS",
            fullName = "Euronext Amsterdam",
            mic = "XAMS",
            eodhdCode = "AS",
            yahooSuffix = "AS",
            currency = "EUR",
            timeZone = ZoneId.of("Europe/Amsterdam"),
            market = Market.EU,
            country = "NL",
            mainVenue = true,
        )
    }

    test("a US row has an empty suffix and belongs to the US market") {
        entries.single { it.mic == "XNGS" } shouldBe ExchangeEntry(
            suffix = "",
            shortName = "NASDAQ",
            fullName = "Nasdaq Global Select Market",
            mic = "XNGS",
            eodhdCode = "US",
            yahooSuffix = "",
            currency = "USD",
            timeZone = ZoneId.of("America/New_York"),
            market = Market.US,
            country = "US",
            mainVenue = true,
        )
    }

    test("a row of a German regional exchange is not a main venue") {
        entry("F").country shouldBe "DE"
        entry("F").mainVenue shouldBe false
        entry("DE").mainVenue shouldBe true
    }

    test("US OTC rows are not main venues") {
        entries.filter { it.suffix.isEmpty() && !it.mainVenue }.map { it.mic } shouldContainExactlyInAnyOrder
            listOf("OTCM", "OOTC", "PINX", "PSGM", "EXPM", "OTCB", "OTCQ", "XOTC")
    }

    test("every row carries the EODHD code of its suffix, US rows the US code") {
        entries.filter { row ->
            val code = if (row.suffix.isEmpty()) ExchangeCode.US else ExchangeCode(row.suffix)
            row.eodhdCode != KnownExchangeCodes.eodhdCodeOf(code)
        }.shouldBeEmpty()
        entries.filter { it.suffix.isEmpty() }.map { it.eodhdCode }.toSet() shouldBe setOf("US")
    }

    test("EODHD codes differ from the suffix where EODHD names the exchange differently") {
        entry("DE").eodhdCode shouldBe "XETRA"
        entry("L").eodhdCode shouldBe "LSE"
        entry("WA").eodhdCode shouldBe "WAR"
        entry("SG").eodhdCode shouldBe "STU"
        entry("AX").eodhdCode shouldBe "AU"
        entry("JO").eodhdCode shouldBe "JSE"
        entry("CS").eodhdCode shouldBe "BC"
    }

    test("exchanges EODHD names like the suffix carry the suffix as EODHD code") {
        entry("HM").eodhdCode shouldBe "HM"
        entry("HA").eodhdCode shouldBe "HA"
    }

    test("exchanges EODHD doesn't cover have no EODHD code") {
        entry("IL").eodhdCode shouldBe null
        entry("T").eodhdCode shouldBe null
        entry("BC").eodhdCode shouldBe null
    }

    test("exchanges EODHD no longer lists have no EODHD code") {
        listOf("BE", "IC", "HK", "TA").map { suffix -> entry(suffix).eodhdCode }.toSet() shouldBe setOf(null)
    }

    test("every row carries the Yahoo suffix of its suffix, US rows an empty one") {
        entries.filter { row ->
            val code = if (row.suffix.isEmpty()) ExchangeCode.US else ExchangeCode(row.suffix)
            row.yahooSuffix != KnownExchangeCodes.yahooSuffixOf(code)
        }.shouldBeEmpty()
        entries.filter { it.suffix.isEmpty() }.map { it.yahooSuffix }.toSet() shouldBe setOf("")
    }

    test("the Yahoo suffix equals the symbol suffix where Yahoo is used, even where the EODHD code differs") {
        listOf("DE", "L", "IL", "T", "HK").forEach { suffix -> entry(suffix).yahooSuffix shouldBe suffix }
        entries.mapNotNull { it.yahooSuffix }.filter { it.isNotEmpty() }.toSet() shouldBe
            entries.filter { it.yahooSuffix?.isNotEmpty() == true }.map { it.suffix }.toSet()
    }

    test("exchanges Yahoo is not used for have no Yahoo suffix") {
        listOf("ME", "PM", "VN", "QA", "CS", "BC").map { suffix -> entry(suffix).yahooSuffix }.toSet() shouldBe setOf(null)
    }

    test("the table is the same on every call") {
        ExchangeService(resolver).exchanges() shouldBe entries
    }
})
