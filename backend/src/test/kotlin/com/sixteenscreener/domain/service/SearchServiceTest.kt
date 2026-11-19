package com.sixteenscreener.domain.service

import com.sixteenscreener.Fixtures
import com.sixteenscreener.adapter.outbound.finnhub.FinnhubClient
import com.sixteenscreener.adapter.outbound.http.SourceHttpCall
import com.sixteenscreener.adapter.outbound.http.SourceHttpClient
import com.sixteenscreener.domain.model.Capability
import com.sixteenscreener.domain.model.CompanyIdentity
import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.InvalidInputException
import com.sixteenscreener.domain.model.LimitInterval
import com.sixteenscreener.domain.model.Market
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.ResourceNotFoundException
import com.sixteenscreener.domain.model.SearchHit
import com.sixteenscreener.domain.model.SearchQuery
import com.sixteenscreener.domain.model.SearchStatus
import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceCallResult
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.service.CompanyDataFixture.Companion.profileKey
import com.sixteenscreener.fakes.InMemoryExchangeDirectoryStore
import com.sixteenscreener.port.inbound.SearchResults
import com.sixteenscreener.port.outbound.MarketDataSource
import com.sixteenscreener.port.outbound.SymbolDirectory
import com.sixteenscreener.watchlistEntries
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
    symbolDirectory: ((CompanyDataFixture) -> SymbolDirectory)? = null,
) {
    val data = CompanyDataFixture(limit = limit, capabilities = capabilities)
    val responses = SearchResponseCache(data.clock)
    val service = SearchService(
        profiles = data.profiles,
        catalog = data.catalog,
        marketDataSources = mapOf(FINNHUB to (searchSource?.invoke(data) ?: data.source)),
        symbolDirectory = symbolDirectory?.invoke(data) ?: data.directory,
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

private fun SearchResults.symbols(): List<String> = items.map { it.identity.symbol.value }

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
        withData(nameFn = { "'$it' is rejected without a call" }, "", "   ", "a".repeat(51), " ${"a".repeat(51)} ") { raw ->
            val f = SearchFixture()

            shouldThrow<InvalidInputException> { f.search(raw) }

            f.data.source.calls.shouldBeEmpty()
            f.data.countingCounters.tryAcquireCalls shouldBe 0
        }

        withData(
            nameFn = { "'$it' is accepted" },
            "a",
            " F ",
            "ab",
            "abc",
            "a".repeat(50),
            "  abc  ",
            " ${"a".repeat(50)} ",
        ) { raw ->
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
            val identity = result.items.single().identity
            identity.symbol shouldBe Symbol.of("AAPL")
            identity.exchange shouldBe "NASDAQ"
            identity.name shouldBe "APPLE INC"
            identity.market shouldBe Market.US
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
            result.items.map { it.identity.market } shouldContainExactly listOf(Market.US, Market.EU, Market.EU, null)
            result.items[1].identity.exchange shouldBe "XETRA"
            result.items[3].identity.name shouldBe "APPLE INC CDR"
        }

        test("results of exchanges outside the directory exchanges cost no symbol list request") {
            val f = SearchFixture(
                symbolDirectory = { data ->
                    ExchangeDirectoryService(
                        source = data.source,
                        store = InMemoryExchangeDirectoryStore(),
                        guard = SourceCallGuard(data.limiter),
                        outcomes = SourceCallOutcomes(data.sourceCalls),
                        clock = data.clock,
                        directoryExchanges = setOf(ExchangeCode.US),
                    )
                },
            )
            f.data.source.exchanges[ExchangeCode.US] = SourceResult.Success(listOf(listing("AAPL", name = "APPLE INC")), f.data.success())
            f.hits("apple", "AAPL" to "Apple Inc", "APC.DE" to "APPLE INC", "AAPL.SW" to "APPLE INC", "AAPL.NE" to "APPLE INC - CDR")

            val result = f.search("apple")

            result.symbols() shouldContainExactly listOf("AAPL", "APC.DE", "AAPL.SW", "AAPL.NE")
            result.items.map { it.identity.exchange } shouldContainExactly listOf("NASDAQ", "XETRA", "SIX", "NEO")
            f.data.source.calls shouldContainExactly listOf("search:apple", "exchange:US")
        }

        test("result missing from the directory gets exchange and market from the static exchange table") {
            val f = SearchFixture()
            f.hits("apple", "AAPL.NE" to "APPLE INC - CDR")

            val identity = f.search("apple").items.single().identity

            identity.symbol shouldBe Symbol.of("AAPL.NE")
            identity.exchange shouldBe "NEO"
            identity.name shouldBe "APPLE INC - CDR"
            identity.market.shouldBeNull()
        }

        test("EU result missing from the directory gets the exchange name and the EU market from the static exchange table") {
            val f = SearchFixture()
            f.hits("ahold", "AD.AS" to "Koninklijke Ahold Delhaize NV")

            f.search("ahold").items.single().identity shouldBe
                CompanyIdentity(Symbol.of("AD.AS"), "AMS", "Koninklijke Ahold Delhaize NV", Market.EU, "Common Stock")
        }

        test("security type of the directory listing wins over the type of the search hit") {
            val f = SearchFixture()
            f.data.listed(listing("TM", mic = "XNYS", name = "TOYOTA MOTOR CORP").copy(type = "ADR"))
            f.hits("toyota", "TM" to "Toyota Motor Corp")

            val identity = f.search("toyota").items.single().identity

            identity.type shouldBe "ADR"
            identity.stock shouldBe false
        }

        test("result missing from the directory takes the security type of the search hit") {
            val f = SearchFixture()
            f.data.source.searchResults["toyota"] = SourceResult.Success(
                listOf(SearchHit(Symbol.of("TM"), "Toyota Motor Corp", "ADR")),
                f.data.success(),
            )

            val identity = f.search("toyota").items.single().identity

            identity.type shouldBe "ADR"
            identity.stock shouldBe false
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

        test("no cached company data is read and no requests other than the search are made") {
            val f = SearchFixture()
            f.data.listed(listing("AAPL"), listing("MSFT"))
            f.data.cachedQuote("AAPL", 190.0, age = Duration.ofDays(2))
            f.data.cached(profileKey("AAPL"), sampleProfile(sector = "Technology"), age = Duration.ofDays(1))
            f.data.quoteFromSource("MSFT", 400.0)
            f.data.profileFromSource("MSFT")
            f.hits("tech", "AAPL" to "APPLE INC", "MSFT" to "MICROSOFT")

            val items = f.search("tech").items

            items.map { it.identity.symbol } shouldContainExactly listOf(Symbol.of("AAPL"), Symbol.of("MSFT"))
            f.data.cache.getCalls shouldBe 0
            f.data.cache.getAllCalls shouldBe 0
            f.data.source.calls shouldContainExactly listOf("search:tech")
            f.data.countingCounters.tryAcquireCalls shouldBe 1
        }

        test("results already in the watchlist are marked") {
            val f = SearchFixture()
            f.data.profiles.save(f.data.profile.copy(watchlist = watchlistEntries("APC.DE")))
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

    context("ticker-only queries of 1 or 2 characters") {
        test("1-character query keeps only tickers starting with it, exact ticker first") {
            val f = SearchFixture()
            f.hits(
                "F",
                "FORD.MX" to "FORD MOTOR CO",
                "FDX" to "FEDEX CORP",
                "AAPL" to "FIRST APPLE",
                "F" to "FORD MOTOR CO",
                "FB" to "META",
            )

            f.search("F").symbols() shouldContainExactly listOf("F", "FORD.MX", "FDX", "FB")
        }

        test("2-character query keeps only tickers starting with it, exact ticker first") {
            val f = SearchFixture()
            f.hits(
                "GM",
                "GME" to "GAMESTOP CORP",
                "XOM" to "GMEXICO",
                "GM" to "GENERAL MOTORS CO",
                "GMAB" to "GENMAB",
            )

            f.search("GM").symbols() shouldContainExactly listOf("GM", "GME", "GMAB")
        }

        test("tickers are matched ignoring case") {
            val f = SearchFixture()
            f.hits("gm", "GME" to "GAMESTOP CORP", "GM" to "GENERAL MOTORS CO", "AGM" to "FARMER MAC")

            f.search("gm").symbols() shouldContainExactly listOf("GM", "GME")
        }

        test("suffixed symbols are matched by their ticker") {
            val f = SearchFixture()
            f.hits(
                "sa",
                "DSY.PA" to "DASSAULT SYSTEMES SA",
                "SAP.DE" to "SAP SE",
                "SAN.PA" to "SANOFI SA",
                "SA" to "SEABRIDGE GOLD",
            )

            f.search("sa").symbols() shouldContainExactly listOf("SA", "SAP.DE", "SAN.PA")
        }

        test("exact ticker matches on several exchanges keep the source order among themselves") {
            val f = SearchFixture()
            f.hits("F", "FDX" to "FEDEX CORP", "F.DE" to "FORD MOTOR CO", "F" to "FORD MOTOR CO")

            f.search("F").symbols() shouldContainExactly listOf("F.DE", "F", "FDX")
        }

        test("name-only matches give OK with empty items") {
            val f = SearchFixture()
            f.hits("X", "AAPL" to "XAPPLE", "MSFT" to "X MICROSOFT")

            val result = f.search("X")

            result.status shouldBe SearchStatus.OK
            result.items.shouldBeEmpty()
            f.data.cache.getAllCalls shouldBe 0
        }

        test("dropped hits are not looked up in the directory") {
            val f = SearchFixture()
            f.data.listed(listing("AAPL"), listing("F"))
            f.hits("F", "AAPL" to "FRUIT", "F" to "FORD MOTOR CO")

            f.search("F").items.single().identity.symbol shouldBe Symbol.of("F")

            f.data.directory.lookupAllRequests shouldContainExactly listOf(listOf(Symbol.of("F")))
        }

        test("raw source hits are kept in memory and filtered on every answer") {
            val f = SearchFixture()
            f.hits("F", "AAPL" to "FRUIT", "FDX" to "FEDEX CORP", "F" to "FORD MOTOR CO")
            f.search("F")

            f.search(" f ").symbols() shouldContainExactly listOf("F", "FDX")
            f.searchCalls() shouldContainExactly listOf("search:F")
            f.responses.get(FINNHUB, SearchQuery.of("F"))
                ?.map { it.symbol.value } shouldBe listOf("AAPL", "FDX", "F")
        }

        test("3-character query keeps name matches in the order of the source") {
            val f = SearchFixture()
            f.hits("for", "F" to "FORD MOTOR CO", "FORTY" to "FORTY", "ABC" to "FORMULA ONE")

            f.search("for").symbols() shouldContainExactly listOf("F", "FORTY", "ABC")
        }
    }

    context("result limit") {
        fun tickers(count: Int) = (1..count).map { "T$it" to "COMPANY $it" }.toTypedArray()

        test("long result lists are cut to the first 25 in the order of the source") {
            val f = SearchFixture()
            f.hits("company", *tickers(40))

            f.search("company").symbols() shouldContainExactly (1..25).map { "T$it" }
        }

        withData(nameFn = { "$it results are returned in full" }, 0, 1, 24, 25) { count ->
            val f = SearchFixture()
            f.hits("company", *tickers(count))

            f.search("company").symbols() shouldContainExactly (1..count).map { "T$it" }
        }

        test("ticker-only results are limited to 25 after filtering, exact match first") {
            val f = SearchFixture()
            f.hits("T", "AAPL" to "TAPPLE", *tickers(30), "T" to "AT&T INC")

            val symbols = f.search("T").symbols()

            symbols shouldHaveSize SearchService.MAX_RESULTS
            symbols shouldContainExactly listOf("T") + (1..24).map { "T$it" }
        }

        test("duplicates are removed before the limit") {
            val f = SearchFixture()
            f.hits("company", "T1" to "COMPANY 1", *tickers(30))

            f.search("company").symbols() shouldContainExactly (1..25).map { "T$it" }
        }

        test("dropped results are not looked up in the directory") {
            val f = SearchFixture()
            f.hits("company", *tickers(26))

            f.search("company")

            f.data.directory.lookupAllRequests.single() shouldContainExactly (1..25).map { Symbol.of("T$it") }
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

        test("answers from memory get the current watchlist") {
            val f = SearchFixture()
            f.data.listed(listing("AAPL"))
            f.hits("apple", "AAPL" to "APPLE INC")
            f.search("apple")
            f.data.profiles.save(f.data.profile.copy(watchlist = watchlistEntries("AAPL")))

            val item = f.search("apple").items.single()

            item.inWatchlist shouldBe true
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
            result.items.map { it.identity.market } shouldContainExactly listOf(Market.US, Market.EU, Market.EU, null)
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
