package com.sixteenscreener.adapter.inbound.http

import com.sixteenscreener.TestPorts
import com.sixteenscreener.config.AppConfig
import com.sixteenscreener.domain.model.CacheEntry
import com.sixteenscreener.domain.model.CacheKey
import com.sixteenscreener.domain.model.Capability
import com.sixteenscreener.domain.model.CompanyProfile
import com.sixteenscreener.domain.model.DataType
import com.sixteenscreener.domain.model.LimitInterval
import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.PreviousPrice
import com.sixteenscreener.domain.model.Profile
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.Quote
import com.sixteenscreener.domain.model.QuoteRecord
import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.testConfig
import com.sixteenscreener.watchlistEntry
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import java.time.LocalDate

private val finnhub = SourceId.of("finnhub")

private fun TestPorts.listed(symbol: String, mic: String? = "XNAS", currency: String = "USD", name: String = "$symbol INC") =
    symbolDirectory.add(ListedSymbol(Symbol.of(symbol), name, mic, currency, "Common Stock"))

private fun TestPorts.cachedQuote(symbol: String, price: Double, age: Duration, previousPrice: Double? = null) {
    val fetchedAt = clock.now() - age
    val marketDate = LocalDate.parse("2026-11-14")
    companyCache.seedQuote(
        CacheKey(finnhub, Symbol.of(symbol), DataType.QUOTE),
        CacheEntry(
            QuoteRecord(Quote(price, "USD"), marketDate, previousPrice?.let { PreviousPrice(it, marketDate.minusDays(1)) }),
            fetchedAt,
        ),
    )
}

private fun TestPorts.quoteFromSource(symbol: String, price: Double) {
    marketData.quotes[Symbol.of(symbol)] = SourceResult.Success(Quote(price, "USD"), SourceCallOutcome.success(clock.now()))
}

private fun marketDataUrl(profile: Profile, symbols: List<String>) =
    "/profiles/${profile.id}/watchlist/market-data?symbols=${symbols.joinToString(",")}"

private fun JsonObject.symbol() = getValue("symbol").jsonPrimitive.content

private fun JsonObject.items() = getValue("items").jsonArray.map { it.jsonObject }

private fun JsonObject.quote(): JsonObject? = (get("quote") as? JsonObject)

class WatchlistRoutesTest : FunSpec({

    test("empty watchlist answers an empty item list") {
        routeTest { ports ->
            val profile = ports.savedProfile()

            val response = client.get("/profiles/${profile.id}/watchlist")

            response.status shouldBe HttpStatusCode.OK
            response.json().items().shouldBeEmpty()
        }
    }

    test("market data row carries every alpha field of MarketData and Quote and requests no company profile") {
        routeTest(config = profileCapableConfig()) { ports ->
            val profile = ports.savedProfile(watchlist = listOf("SAP.DE"))
            ports.listed("SAP.DE", mic = "XETR", currency = "EUR", name = "SAP SE")
            ports.marketData.quotes[Symbol.of("SAP.DE")] =
                SourceResult.Success(Quote(201.5, "EUR"), SourceCallOutcome.success(ports.clock.now()))
            ports.marketData.profiles[Symbol.of("SAP.DE")] = SourceResult.Success(
                CompanyProfile("SAP SE", "DE", "EUR", "Software", null, null, null, null, null, null, null),
                SourceCallOutcome.success(ports.clock.now()),
            )

            val item = client.get(marketDataUrl(profile, listOf("SAP.DE"))).json().items().single()

            item shouldBe JsonObject(
                mapOf(
                    "symbol" to JsonPrimitive("SAP.DE"),
                    "status" to JsonPrimitive("FRESH"),
                    "quote" to JsonObject(
                        mapOf(
                            "price" to JsonPrimitive(201.5),
                            "currency" to JsonPrimitive("EUR"),
                            "fetchedAt" to JsonPrimitive("2026-11-14T12:00:00Z"),
                            "previousCachedPrice" to JsonNull,
                            "trend" to JsonPrimitive("UNCHANGED"),
                            "source" to JsonPrimitive("finnhub"),
                        ),
                    ),
                    "indicators" to JsonArray(emptyList()),
                ),
            )
            ports.marketData.calls shouldContainExactly listOf("quote:SAP.DE")
        }
    }

    context("status of a market data row") {
        data class Case(
            val name: String,
            val status: String,
            val price: Double?,
            val config: AppConfig = testConfig(),
            val arrange: TestPorts.() -> Unit,
        )

        withData(
            nameFn = { it.name },
            Case("fresh cached quote is FRESH without a request", "FRESH", 1.0) {
                cachedQuote("AAPL", 1.0, age = Duration.ofMinutes(5))
            },
            Case("stale cached quote is refreshed to FRESH", "FRESH", 2.0) {
                cachedQuote("AAPL", 1.0, age = Duration.ofHours(2))
                quoteFromSource("AAPL", 2.0)
            },
            Case("limit reached keeps the cached quote", "LIMIT_REACHED", 1.0) {
                cachedQuote("AAPL", 1.0, age = Duration.ofHours(2))
                marketData.quotes[Symbol.of("AAPL")] = SourceResult.LimitReached(SourceCallOutcome.httpError(429, clock.now()))
            },
            Case("limit reached without a cached quote has no quote", "LIMIT_REACHED", null) {
                requestCounters.setCount(finnhub, LimitInterval.MINUTE, clock.now(), 60)
                quoteFromSource("AAPL", 2.0)
            },
            Case("source error without a cached quote has no quote", "SOURCE_ERROR", null) {
                marketData.quotes[Symbol.of("AAPL")] = SourceResult.SourceError(SourceCallOutcome.timeout(clock.now()))
            },
            Case("source without data has no quote", "NO_DATA", null) {
                marketData.quotes[Symbol.of("AAPL")] = SourceResult.NoData(SourceCallOutcome.success(clock.now()))
            },
            Case(
                "source without the quote capability is NOT_SUPPORTED",
                "NOT_SUPPORTED",
                null,
                testConfig().let { config ->
                    config.copy(sources = config.sources.map { it.copy(capabilities = it.capabilities - Capability.QUOTE) })
                },
            ) { quoteFromSource("AAPL", 2.0) },
        ) { case ->
            routeTest(config = case.config) { ports ->
                val profile = ports.savedProfile(watchlist = listOf("AAPL"))
                ports.listed("AAPL")
                case.arrange(ports)

                val response = client.get(marketDataUrl(profile, listOf("AAPL")))

                response.status shouldBe HttpStatusCode.OK
                val item = response.json().items().single()
                item["status"] shouldBe JsonPrimitive(case.status)
                item.quote()?.get("price") shouldBe case.price?.let(::JsonPrimitive)
                item["indicators"] shouldBe JsonArray(emptyList())
            }
        }
    }

    test("company outside the US and EU markets has a null market") {
        routeTest { ports ->
            val profile = ports.savedProfile(watchlist = listOf("7203.T"))
            ports.listed("7203.T", mic = "XTKS", currency = "JPY")

            val item = client.get("/profiles/${profile.id}/watchlist").json().items().single()

            item["market"] shouldBe JsonNull
            item["exchange"] shouldBe JsonPrimitive("TSE")
            item["sector"] shouldBe JsonNull
        }
    }

    context("trend against the previous cached price") {
        data class Case(val name: String, val previous: Double?, val trend: String)

        withData(
            nameFn = { it.name },
            Case("higher price is UP", 100.0, "UP"),
            Case("lower price is DOWN", 120.0, "DOWN"),
            Case("equal price is UNCHANGED", 110.0, "UNCHANGED"),
            Case("missing previous price is UNCHANGED", null, "UNCHANGED"),
        ) { case ->
            routeTest { ports ->
                val profile = ports.savedProfile(watchlist = listOf("AAPL"))
                ports.listed("AAPL")
                ports.cachedQuote("AAPL", 110.0, age = Duration.ofMinutes(5), previousPrice = case.previous)

                val quote = client.get(marketDataUrl(profile, listOf("AAPL"))).json().items().single().quote()!!

                quote["trend"] shouldBe JsonPrimitive(case.trend)
                quote["previousCachedPrice"] shouldBe (case.previous?.let(::JsonPrimitive) ?: JsonNull)
                ports.marketData.calls.shouldBeEmpty()
            }
        }
    }

    test("two market data requests of 50 companies with a budget of 60 calls refresh 60 quotes and return the rest without quote") {
        routeTest { ports ->
            val tickers = (1..100).map { "C%03d".format(it) }
            val profile = ports.savedProfile(watchlist = tickers)
            tickers.forEach { ports.listed(it); ports.quoteFromSource(it, 10.0) }

            val first = client.get(marketDataUrl(profile, tickers.take(50))).json().items()
            val second = client.get(marketDataUrl(profile, tickers.drop(50))).json().items()

            (first + second).map { it.symbol() } shouldContainExactly tickers
            first.count { it.quote() != null } shouldBe 50
            second.count { it.quote() != null } shouldBe 10
            second.drop(10).forEach { it["status"] shouldBe JsonPrimitive("LIMIT_REACHED") }
            ports.marketData.calls shouldHaveSize 60
        }
    }

    test("429 mid-batch of a market data request keeps cached quotes of the skipped companies") {
        routeTest { ports ->
            val tickers = (1..20).map { "C%03d".format(it) }
            val profile = ports.savedProfile(watchlist = tickers)
            tickers.forEachIndexed { index, ticker ->
                ports.listed(ticker)
                ports.cachedQuote(ticker, 5.0, age = Duration.ofHours(30L - index))
                ports.quoteFromSource(ticker, 6.0)
            }
            ports.marketData.quotes[Symbol.of("C005")] =
                SourceResult.LimitReached(SourceCallOutcome.httpError(429, ports.clock.now()))

            val response = client.get(marketDataUrl(profile, profile.watchlistSymbols.map { it.value }))

            response.status shouldBe HttpStatusCode.OK
            val prices = response.json().items().map { it.quote()!!["price"]!!.jsonPrimitive.content.toDouble() }
            prices.count { it == 6.0 } shouldBe ports.marketData.calls.size - 1
            prices.drop(10).forEach { it shouldBe 5.0 }
            ports.sourceCalls.writes.last().second.httpStatus shouldBe 429
        }
    }

    test("source failures during a market data request return cached quotes or none with SOURCE_ERROR, never an error") {
        routeTest { ports ->
            val profile = ports.savedProfile(watchlist = listOf("OK", "CACHED", "EMPTY"))
            listOf("OK", "CACHED", "EMPTY").forEach { ports.listed(it) }
            ports.quoteFromSource("OK", 1.0)
            ports.cachedQuote("CACHED", 2.0, age = Duration.ofHours(2))
            ports.marketData.quotes[Symbol.of("CACHED")] =
                SourceResult.SourceError(SourceCallOutcome.httpError(500, ports.clock.now()))
            ports.marketData.quotes[Symbol.of("EMPTY")] =
                SourceResult.SourceError(SourceCallOutcome.timeout(ports.clock.now()))

            val response = client.get(marketDataUrl(profile, profile.watchlistSymbols.map { it.value }))

            response.status shouldBe HttpStatusCode.OK
            val items = response.json().items()
            items[0].quote()!!["price"] shouldBe JsonPrimitive(1.0)
            items[1].quote()!!["price"] shouldBe JsonPrimitive(2.0)
            items[1].quote()!!["fetchedAt"] shouldBe JsonPrimitive("2026-11-14T10:00:00Z")
            items[2]["quote"] shouldBe JsonNull
            items.map { it["status"] } shouldContainExactly
                listOf(JsonPrimitive("FRESH"), JsonPrimitive("SOURCE_ERROR"), JsonPrimitive("SOURCE_ERROR"))
        }
    }

    test("failed cache write during a market data request still answers the refreshed quote") {
        routeTest { ports ->
            val profile = ports.savedProfile(watchlist = listOf("AAPL"))
            ports.listed("AAPL")
            ports.quoteFromSource("AAPL", 7.0)
            ports.companyCache.failWrites = true

            val response = client.get(marketDataUrl(profile, profile.watchlistSymbols.map { it.value }))

            response.status shouldBe HttpStatusCode.OK
            response.json().items().single().quote()!!["price"] shouldBe JsonPrimitive(7.0)
        }
    }

    test("watchlist item carries every alpha field of WatchlistItem") {
        routeTest { ports ->
            val profile = ports.savedProfile().copy(
                watchlist = listOf(watchlistEntry("SAP.DE", sector = "Software", tags = listOf("eu"))),
            ).also(ports.profiles::save)
            ports.listed("SAP.DE", mic = "XETR", currency = "EUR", name = "SAP SE")

            val item = client.get("/profiles/${profile.id}/watchlist").json().items().single()

            item shouldBe JsonObject(
                mapOf(
                    "symbol" to JsonPrimitive("SAP.DE"),
                    "ticker" to JsonPrimitive("SAP"),
                    "exchange" to JsonPrimitive("XETRA"),
                    "name" to JsonPrimitive("SAP SE"),
                    "market" to JsonPrimitive("EU"),
                    "logoUrl" to JsonPrimitive("/logos/SAP.DE"),
                    "sector" to JsonPrimitive("Software"),
                    "tags" to JsonArray(listOf(JsonPrimitive("eu"))),
                ),
            )
        }
    }

    test("symbol missing from the directory is returned with the ticker as name") {
        routeTest(config = profileCapableConfig()) { ports ->
            val profile = ports.savedProfile(watchlist = listOf("GONE"))
            ports.companyCache.put(
                CacheKey(finnhub, Symbol.of("GONE"), DataType.COMPANY_PROFILE),
                CacheEntry(CompanyProfile("Gone Inc", "US", "USD", "Energy", null, null, null, null, null, null, null), ports.clock.now()),
            )

            val item = client.get("/profiles/${profile.id}/watchlist").json().items().single()

            item["exchange"] shouldBe JsonPrimitive("US")
            item["name"] shouldBe JsonPrimitive("GONE")
            item["market"] shouldBe JsonPrimitive("US")
            item["sector"] shouldBe JsonNull
            ports.marketData.calls.shouldBeEmpty()
        }
    }

    test("watchlist reads no cached company data and makes no source requests") {
        routeTest(config = profileCapableConfig()) { ports ->
            val profile = ports.savedProfile(watchlist = listOf("STALE", "MISSING"))
            ports.listed("STALE")
            ports.listed("MISSING")
            ports.cachedQuote("STALE", 4.0, age = Duration.ofHours(5))
            ports.quoteFromSource("STALE", 5.0)
            ports.quoteFromSource("MISSING", 6.0)
            ports.marketData.profiles[Symbol.of("MISSING")] = SourceResult.Success(
                CompanyProfile("Missing Inc", "US", "USD", "Energy", null, null, null, null, null, null, null),
                SourceCallOutcome.success(ports.clock.now()),
            )

            val items = client.get("/profiles/${profile.id}/watchlist").json().items()

            items.map { it.symbol() } shouldContainExactly listOf("STALE", "MISSING")
            items.forEach { it["sector"] shouldBe JsonNull }
            ports.companyCache.getCalls shouldBe 0
            ports.companyCache.getAllCalls shouldBe 0
            ports.marketData.calls.shouldBeEmpty()
        }
    }

    test("market data covers only requested watchlist symbols, in watchlist order, ignoring duplicates and others") {
        routeTest { ports ->
            val watchlist = listOf("A", "B", "C", "D")
            val profile = ports.savedProfile(watchlist = watchlist)
            (watchlist + "X").forEach { ports.listed(it); ports.quoteFromSource(it, 1.0) }

            val response = client.get(marketDataUrl(profile, listOf("D", "X", "B", "D")))

            response.status shouldBe HttpStatusCode.OK
            val items = response.json().items()
            items.map { it.symbol() } shouldContainExactly listOf("B", "D")
            ports.marketData.calls.toSet() shouldBe setOf("quote:B", "quote:D")
        }
    }

    test("market data of symbols that are all outside the watchlist answers an empty item list") {
        routeTest { ports ->
            val profile = ports.savedProfile(watchlist = listOf("AAPL"))
            ports.listed("MSFT")
            ports.quoteFromSource("MSFT", 1.0)

            val response = client.get(marketDataUrl(profile, listOf("MSFT")))

            response.status shouldBe HttpStatusCode.OK
            response.json().items().shouldBeEmpty()
            ports.marketData.calls.shouldBeEmpty()
        }
    }

    test("market data accepts 50 symbols") {
        routeTest { ports ->
            val profile = ports.savedProfile(watchlist = listOf("C001"))

            val response = client.get(marketDataUrl(profile, (1..50).map { "C%03d".format(it) }))

            response.status shouldBe HttpStatusCode.OK
            response.json().items().map { it.symbol() } shouldContainExactly listOf("C001")
        }
    }

    context("invalid symbols parameter is a bad request") {
        withData(
            nameFn = { "'$it'" },
            "?symbols=",
            "",
            "?symbols=aapl",
            "?symbols=AAPL,,MSFT",
            "?symbols=AAPL,",
            "?symbols=" + (1..51).joinToString(",") { "C%03d".format(it) },
        ) { query ->
            routeTest { ports ->
                val profile = ports.savedProfile(watchlist = listOf("AAPL"))

                client.get("/profiles/${profile.id}/watchlist/market-data$query").shouldBeProblem(400)
                ports.marketData.calls.shouldBeEmpty()
            }
        }
    }

    test("market data for an unknown profile is not found") {
        routeTest {
            client.get("/profiles/${ProfileId.random()}/watchlist/market-data?symbols=AAPL").shouldBeProblem(404)
        }
    }

    test("market data route does not clash with adding and removing watchlist symbols") {
        routeTest { ports ->
            val profile = ports.savedProfile()
            ports.listed("AAPL")

            client.put("/profiles/${profile.id}/watchlist/AAPL").status shouldBe HttpStatusCode.OK
            client.get(marketDataUrl(profile, listOf("AAPL"))).json().items().map { it.symbol() } shouldContainExactly
                listOf("AAPL")
            client.delete("/profiles/${profile.id}/watchlist/AAPL").status shouldBe HttpStatusCode.NoContent
        }
    }
})
