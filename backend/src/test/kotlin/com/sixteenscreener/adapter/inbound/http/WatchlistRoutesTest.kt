package com.sixteenscreener.adapter.inbound.http

import com.sixteenscreener.TestPorts
import com.sixteenscreener.domain.model.CacheEntry
import com.sixteenscreener.domain.model.CacheKey
import com.sixteenscreener.domain.model.Capability
import com.sixteenscreener.domain.model.CompanyProfile
import com.sixteenscreener.domain.model.DataType
import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.PreviousPrice
import com.sixteenscreener.domain.model.Quote
import com.sixteenscreener.domain.model.QuoteRecord
import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.testConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
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

    test("company row carries every alpha field of CompanySummary and Quote") {
        val config = testConfig().let { config ->
            config.copy(
                sources = config.sources.map { it.copy(capabilities = it.capabilities + Capability.COMPANY_PROFILE) },
            )
        }
        routeTest(config = config) { ports ->
            val profile = ports.savedProfile(watchlist = listOf("SAP.DE"))
            ports.listed("SAP.DE", mic = "XETR", currency = "EUR", name = "SAP SE")
            ports.marketData.quotes[Symbol.of("SAP.DE")] =
                SourceResult.Success(Quote(201.5, "EUR"), SourceCallOutcome.success(ports.clock.now()))
            ports.marketData.profiles[Symbol.of("SAP.DE")] = SourceResult.Success(
                CompanyProfile("SAP SE", "DE", "EUR", "Software", null, null, null, null, null, null, null),
                SourceCallOutcome.success(ports.clock.now()),
            )

            val item = client.get("/profiles/${profile.id}/watchlist").json().items().single()

            item.keys shouldBe setOf(
                "symbol", "ticker", "exchange", "name", "market", "sector", "logoUrl", "quote", "inWatchlist",
            )
            item["symbol"] shouldBe JsonPrimitive("SAP.DE")
            item["ticker"] shouldBe JsonPrimitive("SAP")
            item["exchange"] shouldBe JsonPrimitive("XETRA")
            item["name"] shouldBe JsonPrimitive("SAP SE")
            item["market"] shouldBe JsonPrimitive("EU")
            item["sector"] shouldBe JsonPrimitive("Software")
            item["logoUrl"] shouldBe JsonPrimitive("/logos/SAP.DE")
            item["inWatchlist"] shouldBe JsonPrimitive(true)
            item.quote() shouldBe JsonObject(
                mapOf(
                    "price" to JsonPrimitive(201.5),
                    "currency" to JsonPrimitive("EUR"),
                    "fetchedAt" to JsonPrimitive("2026-11-14T12:00:00Z"),
                    "previousCachedPrice" to JsonNull,
                    "trend" to JsonPrimitive("UNCHANGED"),
                    "source" to JsonPrimitive("finnhub"),
                ),
            )
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

                val quote = client.get("/profiles/${profile.id}/watchlist").json().items().single().quote()!!

                quote["trend"] shouldBe JsonPrimitive(case.trend)
                quote["previousCachedPrice"] shouldBe (case.previous?.let(::JsonPrimitive) ?: JsonNull)
                ports.marketData.calls.shouldBeEmpty()
            }
        }
    }

    test("100 companies with a budget of 60 calls refresh 60 quotes and return the rest without quote") {
        routeTest { ports ->
            val tickers = (1..100).map { "C%03d".format(it) }
            val profile = ports.savedProfile(watchlist = tickers)
            tickers.forEach { ports.listed(it); ports.quoteFromSource(it, 10.0) }

            val items = client.get("/profiles/${profile.id}/watchlist").json().items()

            items shouldHaveSize 100
            items.map { it["symbol"]!!.jsonPrimitive.content } shouldContainExactly tickers
            items.count { it.quote() != null } shouldBe 60
            ports.marketData.calls shouldHaveSize 60
        }
    }

    test("429 mid-batch keeps cached quotes of the skipped companies") {
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

            val response = client.get("/profiles/${profile.id}/watchlist")

            response.status shouldBe HttpStatusCode.OK
            val prices = response.json().items().map { it.quote()!!["price"]!!.jsonPrimitive.content.toDouble() }
            prices.count { it == 6.0 } shouldBe ports.marketData.calls.size - 1
            prices.drop(10).forEach { it shouldBe 5.0 }
            ports.sourceCalls.writes.last().second.httpStatus shouldBe 429
        }
    }

    test("source failures return cached quotes or none, never an error") {
        routeTest { ports ->
            val profile = ports.savedProfile(watchlist = listOf("OK", "CACHED", "EMPTY"))
            listOf("OK", "CACHED", "EMPTY").forEach { ports.listed(it) }
            ports.quoteFromSource("OK", 1.0)
            ports.cachedQuote("CACHED", 2.0, age = Duration.ofHours(2))
            ports.marketData.quotes[Symbol.of("CACHED")] =
                SourceResult.SourceError(SourceCallOutcome.httpError(500, ports.clock.now()))
            ports.marketData.quotes[Symbol.of("EMPTY")] =
                SourceResult.SourceError(SourceCallOutcome.timeout(ports.clock.now()))

            val response = client.get("/profiles/${profile.id}/watchlist")

            response.status shouldBe HttpStatusCode.OK
            val items = response.json().items()
            items[0].quote()!!["price"] shouldBe JsonPrimitive(1.0)
            items[1].quote()!!["price"] shouldBe JsonPrimitive(2.0)
            items[1].quote()!!["fetchedAt"] shouldBe JsonPrimitive("2026-11-14T10:00:00Z")
            items[2]["quote"] shouldBe JsonNull
        }
    }

    test("failed cache write still answers the refreshed quote") {
        routeTest { ports ->
            val profile = ports.savedProfile(watchlist = listOf("AAPL"))
            ports.listed("AAPL")
            ports.quoteFromSource("AAPL", 7.0)
            ports.companyCache.failWrites = true

            val response = client.get("/profiles/${profile.id}/watchlist")

            response.status shouldBe HttpStatusCode.OK
            response.json().items().single().quote()!!["price"] shouldBe JsonPrimitive(7.0)
        }
    }

    test("symbol missing from the directory is returned with best-effort data") {
        routeTest { ports ->
            val profile = ports.savedProfile(watchlist = listOf("GONE"))
            ports.cachedQuote("GONE", 3.0, age = Duration.ofHours(2))

            val item = client.get("/profiles/${profile.id}/watchlist").json().items().single()

            item["exchange"] shouldBe JsonPrimitive("US")
            item["name"] shouldBe JsonPrimitive("GONE")
            item["market"] shouldBe JsonNull
            item.quote()!!["price"] shouldBe JsonPrimitive(3.0)
            ports.marketData.calls.shouldBeEmpty()
        }
    }
})
