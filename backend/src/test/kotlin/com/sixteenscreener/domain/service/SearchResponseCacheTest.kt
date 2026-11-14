package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.SearchHit
import com.sixteenscreener.domain.model.SearchQuery
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.fakes.MutableClock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Duration

private fun hits(ticker: String) = listOf(SearchHit(Symbol.of(ticker), "$ticker INC", "Common Stock"))

private fun query(index: Int) = SearchQuery.of("query-$index")

class SearchResponseCacheTest : FunSpec({

    test("stored hits are returned for the same source and query") {
        val cache = SearchResponseCache(MutableClock())
        cache.put(FINNHUB, SearchQuery.of("apple"), hits("AAPL"))

        cache.get(FINNHUB, SearchQuery.of("apple")) shouldBe hits("AAPL")
    }

    test("queries are compared case-insensitively after trimming") {
        val cache = SearchResponseCache(MutableClock())
        cache.put(FINNHUB, SearchQuery.of("Apple"), hits("AAPL"))

        cache.get(FINNHUB, SearchQuery.of("  aPPLE ")) shouldBe hits("AAPL")
    }

    test("entries are kept per source") {
        val cache = SearchResponseCache(MutableClock())
        cache.put(FINNHUB, SearchQuery.of("apple"), hits("AAPL"))

        cache.get(SEC_EDGAR, SearchQuery.of("apple")).shouldBeNull()
    }

    test("unknown query is a miss") {
        SearchResponseCache(MutableClock()).get(FINNHUB, SearchQuery.of("apple")).shouldBeNull()
    }

    test("entry expires 5 minutes after it was stored") {
        val clock = MutableClock()
        val cache = SearchResponseCache(clock)
        cache.put(FINNHUB, SearchQuery.of("apple"), hits("AAPL"))

        clock.advanceBy(Duration.ofMinutes(5).minusMillis(1))
        cache.get(FINNHUB, SearchQuery.of("apple")) shouldBe hits("AAPL")
        clock.advanceBy(Duration.ofMillis(1))
        cache.get(FINNHUB, SearchQuery.of("apple")).shouldBeNull()
    }

    test("storing a query again restarts its time to live") {
        val clock = MutableClock()
        val cache = SearchResponseCache(clock)
        cache.put(FINNHUB, SearchQuery.of("apple"), hits("AAPL"))
        clock.advanceBy(Duration.ofMinutes(4))
        cache.put(FINNHUB, SearchQuery.of("apple"), hits("APC.DE"))
        clock.advanceBy(Duration.ofMinutes(4))

        cache.get(FINNHUB, SearchQuery.of("apple")) shouldBe hits("APC.DE")
    }

    test("the 201st query evicts the least recently used one") {
        val cache = SearchResponseCache(MutableClock())
        (1..200).forEach { cache.put(FINNHUB, query(it), hits("AAPL")) }
        cache.get(FINNHUB, query(1)) shouldBe hits("AAPL")

        cache.put(FINNHUB, query(201), hits("AAPL"))

        cache.get(FINNHUB, query(2)).shouldBeNull()
        cache.get(FINNHUB, query(1)) shouldBe hits("AAPL")
        cache.get(FINNHUB, query(3)) shouldBe hits("AAPL")
        cache.get(FINNHUB, query(201)) shouldBe hits("AAPL")
    }

    test("200 queries are all kept") {
        val cache = SearchResponseCache(MutableClock())
        (1..200).forEach { cache.put(FINNHUB, query(it), hits("AAPL")) }

        (1..200).forEach { cache.get(FINNHUB, query(it)) shouldBe hits("AAPL") }
    }
})
