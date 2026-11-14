package com.sixteenscreener.fakes

import com.sixteenscreener.domain.model.CacheEntry
import com.sixteenscreener.domain.model.CacheKey
import com.sixteenscreener.domain.model.DataType
import com.sixteenscreener.domain.model.PreviousPrice
import com.sixteenscreener.domain.model.Quote
import com.sixteenscreener.domain.model.QuoteRecord
import com.sixteenscreener.domain.model.Ratio
import com.sixteenscreener.domain.model.RatioCode
import com.sixteenscreener.domain.model.Ratios
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.Symbol
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class InMemoryCompanyCacheTest : FunSpec({
    val finnhub = SourceId.of("finnhub")
    val apple = Symbol.of("AAPL")
    val newYork = ZoneId.of("America/New_York")
    val quoteKey = CacheKey(finnhub, apple, DataType.QUOTE)

    test("put and get a typed entry") {
        val cache = InMemoryCompanyCache()
        val key = CacheKey(finnhub, apple, DataType.RATIOS)
        val entry = CacheEntry(Ratios(listOf(Ratio(RatioCode.PE, 30.0))), Instant.parse("2026-11-14T10:00:00Z"))

        cache.put(key, entry)

        cache.get(key) shouldBe entry
        cache.get(CacheKey(finnhub, Symbol.of("MSFT"), DataType.RATIOS)).shouldBeNull()
    }

    test("put rejects quotes") {
        shouldThrow<IllegalArgumentException> {
            InMemoryCompanyCache().put(quoteKey, CacheEntry(mockQuoteRecord(), Instant.EPOCH))
        }
    }

    test("getAll returns entries of mixed types and omits missing keys") {
        val cache = InMemoryCompanyCache()
        val ratiosKey = CacheKey(finnhub, apple, DataType.RATIOS)
        cache.put(ratiosKey, CacheEntry(Ratios(emptyList()), Instant.EPOCH))
        cache.putQuote(finnhub, apple, Quote(1.0, "USD"), Instant.EPOCH, newYork)

        val entries = cache.getAll(listOf(ratiosKey, quoteKey, CacheKey(finnhub, apple, DataType.COMPANY_PROFILE)))

        entries.size shouldBe 2
        entries[quoteKey]?.data?.quote?.price shouldBe 1.0
        entries[ratiosKey]?.data shouldBe Ratios(emptyList())
    }

    test("first ever quote has no previous price and one history entry") {
        val cache = InMemoryCompanyCache()

        val written = cache.putQuote(finnhub, apple, Quote(100.0, "USD"), Instant.parse("2026-11-10T15:00:00Z"), newYork)

        written.data.previousPrice.shouldBeNull()
        written.data.marketDate shouldBe LocalDate.parse("2026-11-10")
        cache.history(finnhub, apple) shouldHaveSize 1
    }

    test("same-day refresh overwrites the history entry and keeps the previous price") {
        val cache = InMemoryCompanyCache()
        cache.putQuote(finnhub, apple, Quote(90.0, "USD"), Instant.parse("2026-11-09T15:00:00Z"), newYork)
        cache.putQuote(finnhub, apple, Quote(100.0, "USD"), Instant.parse("2026-11-10T14:00:00Z"), newYork)

        val written = cache.putQuote(finnhub, apple, Quote(101.0, "USD"), Instant.parse("2026-11-10T20:00:00Z"), newYork)

        written.data.previousPrice shouldBe PreviousPrice(90.0, LocalDate.parse("2026-11-09"))
        cache.history(finnhub, apple).map { it.quote.price } shouldBe listOf(90.0, 101.0)
    }

    test("next-day refresh moves the latest price to the previous price") {
        val cache = InMemoryCompanyCache()
        cache.putQuote(finnhub, apple, Quote(100.0, "USD"), Instant.parse("2026-11-06T20:00:00Z"), newYork)

        val written = cache.putQuote(finnhub, apple, Quote(95.0, "USD"), Instant.parse("2026-11-09T15:00:00Z"), newYork)

        written.data.previousPrice shouldBe PreviousPrice(100.0, LocalDate.parse("2026-11-06"))
        cache.history(finnhub, apple) shouldHaveSize 2
    }

    test("market day is computed in the exchange time zone, not UTC") {
        val cache = InMemoryCompanyCache()
        cache.putQuote(finnhub, apple, Quote(100.0, "USD"), Instant.parse("2026-11-10T15:00:00Z"), newYork)

        val lateEvening = cache.putQuote(finnhub, apple, Quote(101.0, "USD"), Instant.parse("2026-11-11T04:30:00Z"), newYork)

        lateEvening.data.marketDate shouldBe LocalDate.parse("2026-11-10")
        lateEvening.data.previousPrice.shouldBeNull()
    }

    test("same instant gives different market days on different exchanges") {
        val cache = InMemoryCompanyCache()
        val at = Instant.parse("2026-11-10T23:30:00Z")

        cache.putQuote(finnhub, apple, Quote(1.0, "USD"), at, newYork).data.marketDate shouldBe LocalDate.parse("2026-11-10")
        cache.putQuote(finnhub, Symbol.of("7203.T"), Quote(1.0, "JPY"), at, ZoneId.of("Asia/Tokyo"))
            .data.marketDate shouldBe LocalDate.parse("2026-11-11")
    }
})

private fun mockQuoteRecord() =
    QuoteRecord(Quote(1.0, "USD"), LocalDate.EPOCH, null)
