package com.sixteenscreener.domain.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant

private val source = SourceId.of("finnhub")
private val older = Instant.parse("2026-11-13T10:00:00Z")
private val newer = Instant.parse("2026-11-14T10:00:00Z")
private val record = QuoteRecord(Quote(1.0, "USD"), java.time.LocalDate.parse("2026-11-14"), previousPrice = null)
private val ratios = Ratios(emptyList())

private fun quote(status: DataStatus, fetchedAt: Instant? = newer) =
    SourceData(source, status, record.takeIf { fetchedAt != null }, fetchedAt)

private fun ratios(status: DataStatus, fetchedAt: Instant? = older) =
    SourceData(source, status, ratios.takeIf { fetchedAt != null }, fetchedAt)

class PriceAndRatiosTest : FunSpec({

    data class Case(val quote: DataStatus, val ratios: DataStatus, val expected: DataStatus)

    context("combined status") {
        withData(
            nameFn = { "${it.quote} + ${it.ratios} -> ${it.expected}" },
            Case(DataStatus.FRESH, DataStatus.FRESH, DataStatus.FRESH),
            Case(DataStatus.FRESH, DataStatus.CACHED, DataStatus.CACHED),
            Case(DataStatus.CACHED, DataStatus.LIMIT_REACHED, DataStatus.LIMIT_REACHED),
            Case(DataStatus.LIMIT_REACHED, DataStatus.SOURCE_ERROR, DataStatus.SOURCE_ERROR),
            Case(DataStatus.SOURCE_ERROR, DataStatus.FRESH, DataStatus.SOURCE_ERROR),
            Case(DataStatus.NO_DATA, DataStatus.FRESH, DataStatus.FRESH),
            Case(DataStatus.FRESH, DataStatus.NOT_SUPPORTED, DataStatus.FRESH),
            Case(DataStatus.NO_DATA, DataStatus.CACHED, DataStatus.CACHED),
            Case(DataStatus.NO_DATA, DataStatus.NO_DATA, DataStatus.NO_DATA),
            Case(DataStatus.NOT_SUPPORTED, DataStatus.NO_DATA, DataStatus.NO_DATA),
            Case(DataStatus.NOT_SUPPORTED, DataStatus.NOT_SUPPORTED, DataStatus.NOT_SUPPORTED),
        ) { case ->
            PriceAndRatios(quote(case.quote), ratios(case.ratios)).status shouldBe case.expected
        }
    }

    test("fetch time is the oldest of the returned data") {
        PriceAndRatios(quote(DataStatus.FRESH, newer), ratios(DataStatus.CACHED, older)).fetchedAt shouldBe older
    }

    test("fetch time of a part without data is ignored") {
        PriceAndRatios(quote(DataStatus.FRESH, newer), ratios(DataStatus.SOURCE_ERROR, null)).fetchedAt shouldBe newer
    }

    test("fetch time is null when no data is returned") {
        PriceAndRatios(quote(DataStatus.LIMIT_REACHED, null), ratios(DataStatus.LIMIT_REACHED, null)).fetchedAt.shouldBeNull()
    }
})
