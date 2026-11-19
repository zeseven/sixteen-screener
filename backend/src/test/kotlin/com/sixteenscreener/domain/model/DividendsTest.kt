package com.sixteenscreener.domain.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDate

private val asOf = LocalDate.parse("2026-11-14")

private fun dividend(exDate: String, amount: Double, currency: String = "EUR") =
    Dividend(LocalDate.parse(exDate), paymentDate = null, recordDate = null, declarationDate = null, amount, currency)

class DividendsTest : FunSpec({

    test("the yield sums the dividends of the last year relative to the price") {
        val dividends = Dividends(listOf(dividend("2026-08-01", 1.5), dividend("2026-02-01", 0.5)))

        dividends.trailingYield(Quote(50.0, "EUR"), asOf).shouldNotBeNull() shouldBe (4.0 plusOrMinus 1e-9)
    }

    test("dividends older than a year and later than the reference date are left out") {
        val dividends = Dividends(
            listOf(dividend("2026-12-01", 9.0), dividend("2026-11-14", 1.0), dividend("2025-11-15", 1.0), dividend("2025-11-14", 9.0)),
        )

        dividends.trailingYield(Quote(100.0, "EUR"), asOf).shouldNotBeNull() shouldBe (2.0 plusOrMinus 1e-9)
    }

    test("no dividend within the last year is a yield of 0") {
        Dividends(listOf(dividend("2024-06-01", 1.0))).trailingYield(Quote(100.0, "EUR"), asOf) shouldBe 0.0
        Dividends(emptyList()).trailingYield(Quote(100.0, "EUR"), asOf) shouldBe 0.0
    }

    test("currencies are compared case-insensitively") {
        Dividends(listOf(dividend("2026-08-01", 1.0, "eur"))).trailingYield(Quote(100.0, "EUR"), asOf)
            .shouldNotBeNull() shouldBe (1.0 plusOrMinus 1e-9)
    }

    test("a dividend of the last year in another currency than the quote gives no yield") {
        Dividends(listOf(dividend("2026-08-01", 1.0), dividend("2026-03-01", 1.0, "USD")))
            .trailingYield(Quote(100.0, "EUR"), asOf).shouldBeNull()
    }

    test("a dividend in another currency older than a year doesn't matter") {
        Dividends(listOf(dividend("2026-08-01", 1.0), dividend("2025-03-01", 1.0, "USD")))
            .trailingYield(Quote(100.0, "EUR"), asOf).shouldNotBeNull() shouldBe (1.0 plusOrMinus 1e-9)
    }

    test("a price that is not positive gives no yield") {
        Dividends(listOf(dividend("2026-08-01", 1.0))).trailingYield(Quote(0.0, "EUR"), asOf).shouldBeNull()
        Dividends(listOf(dividend("2026-08-01", 1.0))).trailingYield(Quote(-1.0, "EUR"), asOf).shouldBeNull()
    }
})
