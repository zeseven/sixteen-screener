package com.sixteenscreener.domain.service

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
                info.timeZone != row.timeZone || info.market != row.market
        }.shouldBeEmpty()
    }

    test("a row outside the US and EU markets has no market") {
        entry("V") shouldBe ExchangeEntry(
            suffix = "V",
            shortName = "TSXV",
            fullName = "TSX Venture Exchange",
            mic = "XTSX",
            currency = "CAD",
            timeZone = ZoneId.of("America/Toronto"),
            market = null,
        )
    }

    test("a row of an EU exchange belongs to the EU market") {
        entry("AS") shouldBe ExchangeEntry(
            suffix = "AS",
            shortName = "Euronext Amsterdam",
            fullName = "Euronext Amsterdam",
            mic = "XAMS",
            currency = "EUR",
            timeZone = ZoneId.of("Europe/Amsterdam"),
            market = Market.EU,
        )
    }

    test("a US row has an empty suffix and belongs to the US market") {
        entries.single { it.mic == "XNGS" } shouldBe ExchangeEntry(
            suffix = "",
            shortName = "NASDAQ",
            fullName = "Nasdaq Global Select Market",
            mic = "XNGS",
            currency = "USD",
            timeZone = ZoneId.of("America/New_York"),
            market = Market.US,
        )
    }

    test("the table is the same on every call") {
        ExchangeService(resolver).exchanges() shouldBe entries
    }
})
