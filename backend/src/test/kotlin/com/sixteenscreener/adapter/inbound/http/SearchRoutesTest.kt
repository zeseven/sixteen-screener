package com.sixteenscreener.adapter.inbound.http

import com.sixteenscreener.TestPorts
import com.sixteenscreener.domain.model.CacheEntry
import com.sixteenscreener.domain.model.CacheKey
import com.sixteenscreener.domain.model.DataType
import com.sixteenscreener.domain.model.LimitInterval
import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.Quote
import com.sixteenscreener.domain.model.QuoteRecord
import com.sixteenscreener.domain.model.SearchHit
import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate
import java.util.UUID

private val finnhub = SourceId.of("finnhub")

private fun TestPorts.searchHits(query: String, vararg tickers: String) {
    marketData.searchResults[query] = SourceResult.Success(
        tickers.map { SearchHit(Symbol.of(it), "$it INC", "Common Stock") },
        SourceCallOutcome.success(clock.now()),
    )
}

private fun JsonObject.items() = getValue("items").jsonArray.map { it.jsonObject }

class SearchRoutesTest : FunSpec({

    test("search result carries every alpha field of SearchResults and SearchItem and no company data") {
        routeTest { ports ->
            val profile = ports.savedProfile(watchlist = listOf("SAP.DE"))
            ports.symbolDirectory.add(ListedSymbol(Symbol.of("SAP.DE"), "SAP SE", "XETR", "EUR", "Common Stock"))
            ports.companyCache.seedQuote(
                CacheKey(finnhub, Symbol.of("SAP.DE"), DataType.QUOTE),
                CacheEntry(QuoteRecord(Quote(201.5, "EUR"), LocalDate.parse("2026-11-14"), null), ports.clock.now()),
            )
            ports.searchHits("sap", "SAP.DE", "7203.T")

            val response = client.get("/profiles/${profile.id}/search") { parameter("query", "sap") }

            response.status shouldBe HttpStatusCode.OK
            val body = response.json()
            body.keys shouldBe setOf("source", "status", "items")
            body["source"] shouldBe JsonPrimitive("finnhub")
            body["status"] shouldBe JsonPrimitive("OK")
            val (sap, toyota) = body.items()
            sap.keys shouldBe setOf("symbol", "ticker", "exchange", "name", "market", "logoUrl", "inWatchlist")
            sap["symbol"] shouldBe JsonPrimitive("SAP.DE")
            sap["ticker"] shouldBe JsonPrimitive("SAP")
            sap["exchange"] shouldBe JsonPrimitive("XETRA")
            sap["name"] shouldBe JsonPrimitive("SAP SE")
            sap["market"] shouldBe JsonPrimitive("EU")
            sap["logoUrl"] shouldBe JsonPrimitive("/logos/SAP.DE")
            sap["inWatchlist"] shouldBe JsonPrimitive(true)
            toyota["market"] shouldBe JsonNull
            toyota["exchange"] shouldBe JsonPrimitive("T")
            toyota["name"] shouldBe JsonPrimitive("7203.T INC")
            toyota.keys shouldBe sap.keys
            ports.companyCache.getAllCalls shouldBe 0
            toyota["inWatchlist"] shouldBe JsonPrimitive(false)
        }
    }

    test("query is trimmed before it is sent to the source") {
        routeTest { ports ->
            val profile = ports.savedProfile()
            ports.searchHits("apple", "AAPL")

            val response = client.get("/profiles/${profile.id}/search") { parameter("query", "  apple ") }

            response.json().items().map { it["symbol"] } shouldContainExactly listOf(JsonPrimitive("AAPL"))
        }
    }

    test("1-character query is accepted and matches tickers only") {
        routeTest { ports ->
            val profile = ports.savedProfile()
            ports.searchHits("F", "FDX", "AAPL", "F")

            val response = client.get("/profiles/${profile.id}/search") { parameter("query", " F ") }

            response.status shouldBe HttpStatusCode.OK
            response.json().items().map { it["symbol"] } shouldContainExactly
                listOf(JsonPrimitive("F"), JsonPrimitive("FDX"))
        }
    }

    test("limit reached answers 200 with empty items and LIMIT_REACHED") {
        routeTest { ports ->
            val profile = ports.savedProfile()
            ports.searchHits("apple", "AAPL")
            ports.requestCounters.setCount(finnhub, LimitInterval.MINUTE, ports.clock.now(), 60)

            val response = client.get("/profiles/${profile.id}/search") { parameter("query", "apple") }

            response.status shouldBe HttpStatusCode.OK
            response.json()["status"] shouldBe JsonPrimitive("LIMIT_REACHED")
            response.json().items().shouldBeEmpty()
        }
    }

    test("source failure answers 200 with empty items and SOURCE_ERROR") {
        routeTest { ports ->
            val profile = ports.savedProfile()
            ports.marketData.searchResults["apple"] =
                SourceResult.SourceError(SourceCallOutcome.httpError(503, ports.clock.now()))

            val response = client.get("/profiles/${profile.id}/search") { parameter("query", "apple") }

            response.status shouldBe HttpStatusCode.OK
            response.json()["status"] shouldBe JsonPrimitive("SOURCE_ERROR")
            response.json().items().shouldBeEmpty()
        }
    }

    context("invalid query is a bad request") {
        withData(nameFn = { "'$it'" }, "", "   ", "a".repeat(51)) { query ->
            routeTest { ports ->
                val profile = ports.savedProfile()

                client.get("/profiles/${profile.id}/search") { parameter("query", query) }.shouldBeProblem(400)
                ports.marketData.calls.shouldBeEmpty()
            }
        }
    }

    test("missing query is a bad request") {
        routeTest { ports ->
            val profile = ports.savedProfile()

            client.get("/profiles/${profile.id}/search").shouldBeProblem(400)
        }
    }

    test("malformed profile ID is a bad request") {
        routeTest {
            client.get("/profiles/not-a-uuid/search") { parameter("query", "apple") }.shouldBeProblem(400)
        }
    }

    test("unknown profile is not found") {
        routeTest {
            client.get("/profiles/${UUID.randomUUID()}/search") { parameter("query", "apple") }.shouldBeProblem(404)
        }
    }
})
