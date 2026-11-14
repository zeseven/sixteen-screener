package com.sixteenscreener.domain.service

import com.sixteenscreener.Fixtures
import com.sixteenscreener.adapter.outbound.finnhub.FinnhubClient
import com.sixteenscreener.adapter.outbound.http.SourceHttpCall
import com.sixteenscreener.adapter.outbound.http.SourceHttpClient
import com.sixteenscreener.domain.model.Capability
import com.sixteenscreener.domain.model.DataStatus
import com.sixteenscreener.domain.model.InvalidInputException
import com.sixteenscreener.domain.model.LimitInterval
import com.sixteenscreener.domain.model.Market
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.ResourceNotFoundException
import com.sixteenscreener.domain.model.SearchHit
import com.sixteenscreener.domain.model.SearchStatus
import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceCallResult
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.service.CompanyDataFixture.Companion.profileKey
import com.sixteenscreener.port.inbound.SearchResults
import com.sixteenscreener.port.outbound.MarketDataSource
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private class SearchFixture(
    capabilities: Set<Capability> = ALL_MARKET_DATA,
    limit: Int = 60,
    searchSource: ((CompanyDataFixture) -> MarketDataSource)? = null,
) {
    val data = CompanyDataFixture(limit = limit, capabilities = capabilities)
    val responses = SearchResponseCache(data.clock)
    val service = SearchService(
        profiles = data.profiles,
        catalog = data.catalog,
        marketDataSources = mapOf(FINNHUB to (searchSource?.invoke(data) ?: data.source)),
        symbolDirectory = data.directory,
        companyData = data.service,
        identities = data.identities,
        guard = SourceCallGuard(data.limiter),
        outcomes = SourceCallOutcomes(data.sourceCalls),
        responses = responses,
    )

    fun hits(query: String, vararg tickers: Pair<String, String>) {
        data.source.searchResults[query] = SourceResult.Success(
            tickers.map { (ticker, name) -> SearchHit(Symbol.of(ticker), name, "Common Stock") },
            data.success(),
        )
    }

    fun fails(query: String, result: SourceResult<List<SearchHit>>) {
        data.source.searchResults[query] = result
    }

    fun searchCalls(): List<String> = data.source.calls.filter { it.startsWith("search:") }

    suspend fun search(query: String): SearchResults = service.search(data.profile.id, query)
}

private fun SearchResults.symbols(): List<String> = items.map { it.company.identity.symbol.value }

private class MockFinnhubSearch(
    private val status: HttpStatusCode,
    private val body: String,
    private val responseDelay: kotlin.time.Duration? = null,
) {
    val requests = AtomicInteger()

    fun client(fixture: CompanyDataFixture) = FinnhubClient(
        id = FINNHUB,
        baseUrl = "https://finnhub.test/api/v1/",
        apiKey = "secret-key",
        call = SourceHttpCall(
            SourceHttpClient.create(
                MockEngine {
                    requests.incrementAndGet()
                    responseDelay?.let { delay(it) }
                    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ),
            timeout = 200.milliseconds,
            clock = fixture.clock,
        ),
    )
}

class SearchServiceTest : FunSpec({

    context("query validation") {
        withData(nameFn = { "'$it' is rejected without a call" }, "ab", "  ab  ", "a".repeat(51), "   ") { raw ->
            val f = SearchFixture()

            shouldThrow<InvalidInputException> { f.search(raw) }

            f.data.source.calls.shouldBeEmpty()
            f.data.countingCounters.tryAcquireCalls shouldBe 0
        }

        withData(nameFn = { "'$it' is accepted" }, "abc", "a".repeat(50), "  abc  ", " ${"a".repeat(50)} ") { raw ->
            val f = SearchFixture()
            f.hits(raw.trim())

            f.search(raw).status shouldBe SearchStatus.OK

            f.searchCalls() shouldContainExactly listOf("search:${raw.trim()}")
        }
    }

    context("results") {
        test("query matching a ticker returns the company with identity from the directory") {
            val f = SearchFixture()
            f.data.listed(listing("AAPL", mic = "XNAS", name = "APPLE INC"))
            f.hits("AAPL", "AAPL" to "Apple Inc")

            val result = f.search("AAPL")

            result.source shouldBe FINNHUB
            result.status shouldBe SearchStatus.OK
            val company = result.items.single().company
            company.identity.symbol shouldBe Symbol.of("AAPL")
            company.identity.exchange shouldBe "NASDAQ"
            company.identity.name shouldBe "APPLE INC"
            company.identity.market shouldBe Market.US
            company.sector.shouldBeNull()
            company.quote.shouldBeNull()
            result.items.single().inWatchlist shouldBe false
        }

        test("query matching a name returns companies from US, EU and other exchanges in source order") {
            val f = SearchFixture()
            f.data.listed(
                listing("AAPL", mic = "XNAS", name = "APPLE INC"),
                listing("APC.DE", mic = "XETR", currency = "EUR", name = "APPLE INC"),
                listing("AAPL.SW", mic = "XSWX", currency = "CHF", name = "APPLE INC"),
                listing("AAPL.TO", mic = "XTSE", currency = "CAD", name = "APPLE INC CDR"),
            )
            f.hits("apple", "AAPL" to "APPLE INC", "APC.DE" to "APPLE INC", "AAPL.SW" to "APPLE INC", "AAPL.TO" to "APPLE INC")

            val result = f.search("apple")

            result.symbols() shouldContainExactly listOf("AAPL", "APC.DE", "AAPL.SW", "AAPL.TO")
            result.items.map { it.company.identity.market } shouldContainExactly listOf(Market.US, Market.EU, Market.EU, null)
            result.items[1].company.identity.exchange shouldBe "XETRA"
            result.items[3].company.identity.name shouldBe "APPLE INC CDR"
        }

        test("result missing from the directory is kept with best-effort identity") {
            val f = SearchFixture()
            f.hits("apple", "AAPL.NE" to "APPLE INC - CDR")

            val company = f.search("apple").items.single().company

            company.identity.symbol shouldBe Symbol.of("AAPL.NE")
            company.identity.exchange shouldBe "NE"
            company.identity.name shouldBe "APPLE INC - CDR"
            company.identity.market.shouldBeNull()
        }

        test("duplicate symbols from the source are returned once") {
            val f = SearchFixture()
            f.hits("apple", "AAPL" to "APPLE INC", "AAPL" to "APPLE INC")

            f.search("apple").symbols() shouldContainExactly listOf("AAPL")
        }

        test("no results is OK with empty items") {
            val f = SearchFixture()
            f.hits("zzzz")

            val result = f.search("zzzz")

            result.status shouldBe SearchStatus.OK
            result.items.shouldBeEmpty()
            f.data.cache.getAllCalls shouldBe 0
        }

        test("source without data for the query is OK with empty items") {
            val f = SearchFixture()
            f.fails("zzzz", SourceResult.NoData(f.data.success()))

            val result = f.search("zzzz")

            result.status shouldBe SearchStatus.OK
            result.items.shouldBeEmpty()
        }

        test("cached quotes and sectors are added without source requests") {
            val f = SearchFixture()
            f.data.listed(listing("AAPL"), listing("MSFT"))
            f.data.cachedQuote("AAPL", 190.0, age = Duration.ofDays(2))
            f.data.cached(profileKey("AAPL"), sampleProfile(sector = "Technology"), age = Duration.ofDays(40))
            f.data.quoteFromSource("MSFT", 400.0)
            f.data.profileFromSource("MSFT")
            f.hits("tech", "AAPL" to "APPLE INC", "MSFT" to "MICROSOFT")

            val items = f.search("tech").items

            items[0].company.quote?.data?.quote?.price shouldBe 190.0
            items[0].company.quote?.status shouldBe DataStatus.CACHED
            items[0].company.sector shouldBe "Technology"
            items[1].company.quote.shouldBeNull()
            items[1].company.sector.shouldBeNull()
            f.data.source.calls shouldContainExactly listOf("search:tech")
            f.data.countingCounters.tryAcquireCalls shouldBe 1
        }

        test("results already in the watchlist are marked") {
            val f = SearchFixture()
            f.data.profiles.save(f.data.profile.copy(watchlist = listOf(Symbol.of("APC.DE"))))
            f.hits("apple", "AAPL" to "APPLE INC", "APC.DE" to "APPLE INC")

            f.search("apple").items.map { it.inWatchlist } shouldContainExactly listOf(false, true)
        }

        test("the profile's default source is searched") {
            val f = SearchFixture()
            f.hits("apple", "AAPL" to "APPLE INC")

            f.search("apple").source shouldBe FINNHUB
            f.data.sourceCalls.writes.map { it.first } shouldContainExactly listOf(FINNHUB)
        }
    }

    context("failures") {
        test("limit reached by our counters gives LIMIT_REACHED without a call") {
            val f = SearchFixture()
            f.data.exhaustBudget()
            f.hits("apple", "AAPL" to "APPLE INC")

            val result = f.search("apple")

            result.status shouldBe SearchStatus.LIMIT_REACHED
            result.items.shouldBeEmpty()
            f.searchCalls().shouldBeEmpty()
            f.data.sourceCalls.writes.shouldBeEmpty()
        }

        test("429 from the source gives LIMIT_REACHED and saturates the counters") {
            val f = SearchFixture()
            f.fails("apple", SourceResult.LimitReached(SourceCallOutcome.httpError(429, f.data.now)))

            f.search("apple").status shouldBe SearchStatus.LIMIT_REACHED

            f.data.limiter.tryAcquire(FINNHUB, 1) shouldBe 0
            f.data.sourceCalls.writes.single().second.httpStatus shouldBe 429
        }

        data class Failure(val name: String, val result: (CompanyDataFixture) -> SourceResult<List<SearchHit>>)

        withData(
            nameFn = { "${it.name} gives SOURCE_ERROR with empty items" },
            Failure("5xx") { SourceResult.SourceError(SourceCallOutcome.httpError(502, it.now)) },
            Failure("timeout") { SourceResult.SourceError(SourceCallOutcome.timeout(it.now)) },
            Failure("malformed response") { SourceResult.SourceError(SourceCallOutcome.appError(it.now)) },
            Failure("403 not supported") { SourceResult.NotSupported(SourceCallOutcome.httpError(403, it.now)) },
        ) { failure ->
            val f = SearchFixture()
            f.fails("apple", failure.result(f.data))

            val result = f.search("apple")

            result.status shouldBe SearchStatus.SOURCE_ERROR
            result.items.shouldBeEmpty()
            f.data.sourceCalls.writes shouldHaveSize 1
        }

        test("default source without the search capability gives SOURCE_ERROR without a call") {
            val f = SearchFixture(capabilities = setOf(Capability.QUOTE))
            f.hits("apple", "AAPL" to "APPLE INC")

            val result = f.search("apple")

            result.status shouldBe SearchStatus.SOURCE_ERROR
            result.items.shouldBeEmpty()
            f.data.source.calls.shouldBeEmpty()
            f.data.countingCounters.tryAcquireCalls shouldBe 0
        }

        test("failed searches are not kept in memory") {
            val f = SearchFixture()
            f.fails("apple", SourceResult.SourceError(SourceCallOutcome.httpError(500, f.data.now)))
            f.search("apple")
            f.hits("apple", "AAPL" to "APPLE INC")

            val result = f.search("apple")

            result.status shouldBe SearchStatus.OK
            result.symbols() shouldContainExactly listOf("AAPL")
            f.searchCalls() shouldHaveSize 2
        }
    }

    context("in-memory answers") {
        test("repeated query is answered from memory with OK while the limit is reached") {
            val f = SearchFixture()
            f.hits("apple", "AAPL" to "APPLE INC")
            f.search("apple")
            f.data.exhaustBudget()

            val result = f.search("apple")

            result.status shouldBe SearchStatus.OK
            result.symbols() shouldContainExactly listOf("AAPL")
            f.searchCalls() shouldHaveSize 1
        }

        test("query differing in case and surrounding whitespace is answered from memory") {
            val f = SearchFixture()
            f.hits("Apple", "AAPL" to "APPLE INC")
            f.search("Apple")

            f.search("  aPPLE ").symbols() shouldContainExactly listOf("AAPL")

            f.searchCalls() shouldContainExactly listOf("search:Apple")
        }

        test("answers from memory get the current watchlist and cached quotes") {
            val f = SearchFixture()
            f.data.listed(listing("AAPL"))
            f.hits("apple", "AAPL" to "APPLE INC")
            f.search("apple")
            f.data.profiles.save(f.data.profile.copy(watchlist = listOf(Symbol.of("AAPL"))))
            f.data.cachedQuote("AAPL", 191.0, age = Duration.ofMinutes(1))

            val item = f.search("apple").items.single()

            item.inWatchlist shouldBe true
            item.company.quote?.data?.quote?.price shouldBe 191.0
            f.searchCalls() shouldHaveSize 1
        }

        test("query is requested again after 5 minutes") {
            val f = SearchFixture()
            f.hits("apple", "AAPL" to "APPLE INC")
            f.search("apple")
            f.data.clock.advanceBy(Duration.ofMinutes(5))

            f.search("apple")

            f.searchCalls() shouldHaveSize 2
        }

        test("the least recently used query is requested again after 200 other queries") {
            val f = SearchFixture(limit = 1000)
            val queries = (0..200).map { "query-$it" }
            queries.forEach { f.hits(it) }
            queries.forEach { f.search(it) }

            f.search("query-1")
            f.search("query-0")

            f.searchCalls() shouldHaveSize 202
            f.searchCalls().last() shouldBe "search:query-0"
            f.data.counters.setCount(FINNHUB, LimitInterval.MINUTE, f.data.now, Int.MAX_VALUE / 2)
            f.search("query-200").status shouldBe SearchStatus.OK
        }
    }

    context("profile") {
        test("unknown profile is not found") {
            val f = SearchFixture()

            shouldThrow<ResourceNotFoundException> { f.service.search(ProfileId.random(), "apple") }

            f.data.source.calls.shouldBeEmpty()
        }
    }

    context("through the Finnhub client") {
        test("recorded search response is resolved against the directory") {
            val mock = MockFinnhubSearch(HttpStatusCode.OK, Fixtures.text("finnhub/search-apple.json"))
            val f = SearchFixture(searchSource = mock::client)
            f.data.listed(listing("AAPL", name = "APPLE INC"), listing("APC.DE", mic = "XETR", currency = "EUR"))

            val result = f.search("apple")

            result.status shouldBe SearchStatus.OK
            result.symbols() shouldContainExactly listOf("AAPL", "AAPL.SW", "APC.DE", "AAPL.NE")
            result.items.map { it.company.identity.market } shouldContainExactly listOf(Market.US, null, Market.EU, null)
            f.data.sourceCalls.writes.single().second.result shouldBe SourceCallResult.SUCCESS
        }

        data class HttpFailure(val name: String, val mock: () -> MockFinnhubSearch, val result: SourceCallResult)

        withData(
            nameFn = { "${it.name} gives SOURCE_ERROR" },
            HttpFailure("5xx", { MockFinnhubSearch(HttpStatusCode.BadGateway, "<html/>") }, SourceCallResult.HTTP_ERROR),
            HttpFailure("timeout", { MockFinnhubSearch(HttpStatusCode.OK, "{}", 2.seconds) }, SourceCallResult.TIMEOUT),
            HttpFailure("malformed JSON", { MockFinnhubSearch(HttpStatusCode.OK, """{"result": [""") }, SourceCallResult.APP_ERROR),
        ) { failure ->
            val mock = failure.mock()
            val f = SearchFixture(searchSource = mock::client)

            val result = f.search("apple")

            result.status shouldBe SearchStatus.SOURCE_ERROR
            result.items.shouldBeEmpty()
            f.data.sourceCalls.writes.single().second.result shouldBe failure.result
        }

        test("429 gives LIMIT_REACHED") {
            val mock = MockFinnhubSearch(HttpStatusCode.TooManyRequests, """{"error":"API limit reached."}""")
            val f = SearchFixture(searchSource = mock::client)

            f.search("apple").status shouldBe SearchStatus.LIMIT_REACHED
        }
    }
})
