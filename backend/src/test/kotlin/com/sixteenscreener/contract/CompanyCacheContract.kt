package com.sixteenscreener.contract

import com.sixteenscreener.domain.model.BalanceSheet
import com.sixteenscreener.domain.model.BalanceSheetItem
import com.sixteenscreener.domain.model.BalanceSheetPeriod
import com.sixteenscreener.domain.model.CacheEntry
import com.sixteenscreener.domain.model.CacheKey
import com.sixteenscreener.domain.model.CompanyProfile
import com.sixteenscreener.domain.model.DataType
import com.sixteenscreener.domain.model.FiscalPeriod
import com.sixteenscreener.domain.model.PreviousPrice
import com.sixteenscreener.domain.model.Quote
import com.sixteenscreener.domain.model.QuoteHistoryEntry
import com.sixteenscreener.domain.model.QuoteRecord
import com.sixteenscreener.domain.model.Ratio
import com.sixteenscreener.domain.model.RatioCode
import com.sixteenscreener.domain.model.Ratios
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.outbound.CompanyCache
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.funSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

interface CompanyCacheUnderTest {
    val cache: CompanyCache

    suspend fun history(source: SourceId, symbol: Symbol): List<QuoteHistoryEntry>
}

val sampleRatios = Ratios(listOf(Ratio(RatioCode.PE, 30.5), Ratio(RatioCode.EPS, null), Ratio(RatioCode.BETA, 1.0)))

val sampleBalanceSheet = BalanceSheet(
    listOf(
        BalanceSheetPeriod(
            periodEnd = LocalDate.parse("2025-09-27"),
            fiscalYear = 2025,
            fiscalPeriod = FiscalPeriod.FY,
            filedAt = LocalDate.parse("2025-10-31"),
            form = "10-K",
            items = listOf(
                BalanceSheetItem("Assets", "Total assets", 3.6498E11, "USD"),
                BalanceSheetItem("SharesOutstanding", "Shares", 1.5E10, null),
            ),
        ),
        BalanceSheetPeriod(LocalDate.parse("2024-09-28"), 2024, FiscalPeriod.FY, null, null, emptyList()),
    ),
)

val sampleProfile = CompanyProfile(
    name = "Apple Inc",
    country = "US",
    currency = "USD",
    sector = "Technology",
    industry = null,
    ipoDate = LocalDate.parse("1980-12-12"),
    marketCapitalization = 3.5E12,
    sharesOutstanding = 1.5E10,
    employees = 164000,
    website = "https://www.apple.com/",
    description = null,
)

private val emptyProfile = CompanyProfile(null, null, null, null, null, null, null, null, null, null, null)

fun companyCacheContract(newCache: suspend () -> CompanyCacheUnderTest) = funSpec {
    val finnhub = SourceId.of("finnhub")
    val secEdgar = SourceId.of("sec-edgar")
    val apple = Symbol.of("AAPL")
    val newYork = ZoneId.of("America/New_York")
    val quoteKey = CacheKey(finnhub, apple, DataType.QUOTE)
    val fetchedAt = Instant.parse("2026-11-14T10:00:00Z")

    test("get of an absent key returns null") {
        newCache().cache.get(CacheKey(finnhub, apple, DataType.RATIOS)).shouldBeNull()
    }

    test("ratios, balance sheet and profiles round-trip") {
        val cache = newCache().cache
        val ratiosKey = CacheKey(finnhub, apple, DataType.RATIOS)
        val balanceSheetKey = CacheKey(secEdgar, apple, DataType.BALANCE_SHEET)
        val profileKey = CacheKey(finnhub, apple, DataType.COMPANY_PROFILE)
        val emptyProfileKey = CacheKey(finnhub, Symbol.of("BRK.B"), DataType.COMPANY_PROFILE)

        cache.put(ratiosKey, CacheEntry(sampleRatios, fetchedAt))
        cache.put(balanceSheetKey, CacheEntry(sampleBalanceSheet, fetchedAt))
        cache.put(profileKey, CacheEntry(sampleProfile, fetchedAt))
        cache.put(emptyProfileKey, CacheEntry(emptyProfile, fetchedAt))

        cache.get(ratiosKey) shouldBe CacheEntry(sampleRatios, fetchedAt)
        cache.get(balanceSheetKey) shouldBe CacheEntry(sampleBalanceSheet, fetchedAt)
        cache.get(profileKey) shouldBe CacheEntry(sampleProfile, fetchedAt)
        cache.get(emptyProfileKey) shouldBe CacheEntry(emptyProfile, fetchedAt)
    }

    test("put replaces a previous entry") {
        val cache = newCache().cache
        val key = CacheKey(finnhub, apple, DataType.RATIOS)
        cache.put(key, CacheEntry(sampleRatios, fetchedAt))

        cache.put(key, CacheEntry(Ratios(emptyList()), fetchedAt.plusSeconds(3600)))

        cache.get(key) shouldBe CacheEntry(Ratios(emptyList()), fetchedAt.plusSeconds(3600))
    }

    test("entries are kept per source, symbol and data type") {
        val cache = newCache().cache
        cache.put(CacheKey(finnhub, apple, DataType.RATIOS), CacheEntry(sampleRatios, fetchedAt))

        cache.get(CacheKey(secEdgar, apple, DataType.RATIOS)).shouldBeNull()
        cache.get(CacheKey(finnhub, Symbol.of("MSFT"), DataType.RATIOS)).shouldBeNull()
        cache.get(CacheKey(finnhub, apple, DataType.COMPANY_PROFILE)).shouldBeNull()
    }

    test("put rejects quotes") {
        val cache = newCache().cache
        shouldThrow<IllegalArgumentException> {
            cache.put(quoteKey, CacheEntry(QuoteRecord(Quote(1.0, "USD"), LocalDate.EPOCH, null), fetchedAt))
        }
    }

    test("getAll returns entries of mixed types and omits missing keys") {
        val cache = newCache().cache
        val ratiosKey = CacheKey(finnhub, apple, DataType.RATIOS)
        cache.put(ratiosKey, CacheEntry(sampleRatios, fetchedAt))
        cache.putQuote(finnhub, apple, Quote(1.0, "USD"), fetchedAt, newYork)

        val entries = cache.getAll(listOf(ratiosKey, quoteKey, CacheKey(finnhub, apple, DataType.COMPANY_PROFILE)))

        entries.size shouldBe 2
        entries[quoteKey]?.data?.quote shouldBe Quote(1.0, "USD")
        entries[ratiosKey] shouldBe CacheEntry(sampleRatios, fetchedAt)
    }

    test("getAll of no keys is empty") {
        newCache().cache.getAll(emptyList()).size shouldBe 0
    }

    test("getAll of many keys with duplicates") {
        val cache = newCache().cache
        val keys = (1..100).map { CacheKey(finnhub, Symbol.of("S$it"), DataType.QUOTE) }
        keys.take(10).forEach { cache.putQuote(finnhub, it.symbol, Quote(it.symbol.value.length.toDouble(), "USD"), fetchedAt, newYork) }

        val entries = cache.getAll(keys + keys.take(5))

        entries.size shouldBe 10
        entries.keys shouldBe keys.take(10).toSet()
    }

    test("first ever quote has no previous price and one history entry") {
        val underTest = newCache()

        val written = underTest.cache.putQuote(finnhub, apple, Quote(100.0, "USD"), Instant.parse("2026-11-10T15:00:00Z"), newYork)

        written shouldBe CacheEntry(
            QuoteRecord(Quote(100.0, "USD"), LocalDate.parse("2026-11-10"), previousPrice = null),
            Instant.parse("2026-11-10T15:00:00Z"),
        )
        underTest.cache.get(quoteKey) shouldBe written
        underTest.history(finnhub, apple) shouldContainExactly listOf(
            QuoteHistoryEntry(finnhub, apple, LocalDate.parse("2026-11-10"), Quote(100.0, "USD"), Instant.parse("2026-11-10T15:00:00Z")),
        )
    }

    test("same-day refresh overwrites the history entry and keeps the previous price") {
        val underTest = newCache()
        val cache = underTest.cache
        cache.putQuote(finnhub, apple, Quote(90.0, "USD"), Instant.parse("2026-11-09T15:00:00Z"), newYork)
        cache.putQuote(finnhub, apple, Quote(100.0, "USD"), Instant.parse("2026-11-10T14:00:00Z"), newYork)

        val written = cache.putQuote(finnhub, apple, Quote(101.0, "USD"), Instant.parse("2026-11-10T20:00:00Z"), newYork)

        written.data.previousPrice shouldBe PreviousPrice(90.0, LocalDate.parse("2026-11-09"))
        cache.get(quoteKey) shouldBe written
        underTest.history(finnhub, apple).map { it.marketDate to it.quote.price } shouldContainExactly listOf(
            LocalDate.parse("2026-11-09") to 90.0,
            LocalDate.parse("2026-11-10") to 101.0,
        )
    }

    test("next-day refresh moves the latest price to the previous price") {
        val underTest = newCache()
        underTest.cache.putQuote(finnhub, apple, Quote(100.0, "USD"), Instant.parse("2026-11-10T20:00:00Z"), newYork)

        val written = underTest.cache.putQuote(finnhub, apple, Quote(95.0, "USD"), Instant.parse("2026-11-11T15:00:00Z"), newYork)

        written.data.previousPrice shouldBe PreviousPrice(100.0, LocalDate.parse("2026-11-10"))
        underTest.history(finnhub, apple).map { it.marketDate } shouldContainExactly
            listOf(LocalDate.parse("2026-11-10"), LocalDate.parse("2026-11-11"))
    }

    test("a gap of several days and a weekend move the last known price to the previous price") {
        val underTest = newCache()
        underTest.cache.putQuote(finnhub, apple, Quote(100.0, "USD"), Instant.parse("2026-11-04T20:00:00Z"), newYork)
        underTest.cache.putQuote(finnhub, apple, Quote(102.0, "USD"), Instant.parse("2026-11-06T20:00:00Z"), newYork)

        val monday = underTest.cache.putQuote(finnhub, apple, Quote(99.0, "USD"), Instant.parse("2026-11-09T15:00:00Z"), newYork)

        monday.data.previousPrice shouldBe PreviousPrice(102.0, LocalDate.parse("2026-11-06"))
        underTest.history(finnhub, apple) shouldContainExactly listOf(
            QuoteHistoryEntry(finnhub, apple, LocalDate.parse("2026-11-04"), Quote(100.0, "USD"), Instant.parse("2026-11-04T20:00:00Z")),
            QuoteHistoryEntry(finnhub, apple, LocalDate.parse("2026-11-06"), Quote(102.0, "USD"), Instant.parse("2026-11-06T20:00:00Z")),
            QuoteHistoryEntry(finnhub, apple, LocalDate.parse("2026-11-09"), Quote(99.0, "USD"), Instant.parse("2026-11-09T15:00:00Z")),
        )
    }

    test("market day is computed in the exchange time zone, not UTC") {
        val underTest = newCache()
        underTest.cache.putQuote(finnhub, apple, Quote(100.0, "USD"), Instant.parse("2026-11-10T15:00:00Z"), newYork)

        val lateEvening = underTest.cache.putQuote(finnhub, apple, Quote(101.0, "USD"), Instant.parse("2026-11-11T04:30:00Z"), newYork)

        lateEvening.data.marketDate shouldBe LocalDate.parse("2026-11-10")
        lateEvening.data.previousPrice.shouldBeNull()
        underTest.history(finnhub, apple).map { it.quote.price } shouldContainExactly listOf(101.0)
    }

    test("the same instant gives different market days on different exchanges") {
        val underTest = newCache()
        val at = Instant.parse("2026-11-10T23:30:00Z")
        val toyota = Symbol.of("7203.T")

        underTest.cache.putQuote(finnhub, apple, Quote(1.0, "USD"), at, newYork).data.marketDate shouldBe LocalDate.parse("2026-11-10")
        underTest.cache.putQuote(finnhub, toyota, Quote(1.0, "JPY"), at, ZoneId.of("Asia/Tokyo"))
            .data.marketDate shouldBe LocalDate.parse("2026-11-11")
        underTest.history(finnhub, toyota).map { it.marketDate } shouldContainExactly listOf(LocalDate.parse("2026-11-11"))
    }

    test("quotes of other sources and symbols are independent") {
        val underTest = newCache()
        underTest.cache.putQuote(finnhub, apple, Quote(100.0, "USD"), fetchedAt, newYork)

        underTest.cache.get(CacheKey(secEdgar, apple, DataType.QUOTE)).shouldBeNull()
        underTest.history(secEdgar, apple).shouldBeEmpty()
        underTest.history(finnhub, Symbol.of("MSFT")).shouldBeEmpty()
    }
}
