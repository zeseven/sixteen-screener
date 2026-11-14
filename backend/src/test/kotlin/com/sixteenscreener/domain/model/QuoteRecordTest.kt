package com.sixteenscreener.domain.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class QuoteRecordTest : FunSpec({
    val monday = LocalDate.parse("2026-11-09")
    val tuesday = monday.plusDays(1)

    test("first quote has no previous price") {
        val record = QuoteRecord.next(null, Quote(10.0, "USD"), monday)
        record.previousPrice.shouldBeNull()
        record.trend shouldBe PriceTrend.UNCHANGED
    }

    test("same-day quote keeps the previous price") {
        val previous = PreviousPrice(9.0, monday.minusDays(3))
        val record = QuoteRecord(Quote(10.0, "USD"), monday, previous).advance(Quote(11.0, "USD"), monday)
        record.previousPrice shouldBe previous
        record.quote.price shouldBe 11.0
    }

    test("next-day quote moves the latest price into the previous price") {
        val record = QuoteRecord(Quote(10.0, "USD"), monday, null).advance(Quote(9.5, "USD"), tuesday)
        record.previousPrice shouldBe PreviousPrice(10.0, monday)
        record.trend shouldBe PriceTrend.DOWN
    }

    test("quote of an earlier market day keeps the previous price") {
        val previous = PreviousPrice(8.0, monday)
        val record = QuoteRecord(Quote(10.0, "USD"), tuesday, previous).advance(Quote(9.0, "USD"), monday)
        record.previousPrice shouldBe previous
    }

    context("trend against the previous price") {
        withData(
            nameFn = { (price, previous, trend) -> "$price vs $previous is $trend" },
            Triple(10.0, 9.0, PriceTrend.UP),
            Triple(9.0, 10.0, PriceTrend.DOWN),
            Triple(10.0, 10.0, PriceTrend.UNCHANGED),
            Triple(10.0, null, PriceTrend.UNCHANGED),
        ) { (price, previous, trend) ->
            PriceTrend.of(price, previous) shouldBe trend
        }
    }
})
