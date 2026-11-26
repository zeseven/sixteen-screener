package com.sixteenscreener.adapter.inbound.http

import com.sixteenscreener.config.AppConfig
import com.sixteenscreener.domain.model.Capability
import com.sixteenscreener.domain.model.LimitInterval
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.RequestLimit
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceRole
import com.sixteenscreener.testConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val ALL_DATA = setOf(
    Capability.QUOTE,
    Capability.RATIOS,
    Capability.DIVIDENDS,
    Capability.BALANCE_SHEET,
    Capability.COMPANY_PROFILE,
)

private fun dropdownConfig(): AppConfig = testConfig().let { config ->
    val (finnhub, eodhd) = config.sources
    config.copy(
        sources = listOf(
            finnhub.copy(
                capabilities = setOf(Capability.SEARCH, Capability.QUOTE, Capability.RATIOS, Capability.BALANCE_SHEET, Capability.COMPANY_PROFILE),
            ),
            eodhd,
            finnhub.copy(
                id = SourceId.of("yahoo"),
                name = "Yahoo Finance",
                capabilities = ALL_DATA,
                roles = setOf(SourceRole.US, SourceRole.INTERNATIONAL),
                automaticRequests = false,
                limits = listOf(RequestLimit(LimitInterval.DAY, null)),
            ),
            finnhub.copy(
                id = SourceId.of("sec-edgar"),
                name = "SEC EDGAR",
                capabilities = setOf(Capability.BALANCE_SHEET),
                roles = emptySet(),
            ),
            finnhub.copy(
                id = SourceId.of("tiingo"),
                name = "Tiingo",
                capabilities = setOf(Capability.QUOTE, Capability.DIVIDENDS),
                roles = setOf(SourceRole.US, SourceRole.INTERNATIONAL),
            ),
            finnhub.copy(
                id = SourceId.of("fmp"),
                name = "FMP",
                enabled = false,
                capabilities = ALL_DATA,
                roles = setOf(SourceRole.US, SourceRole.INTERNATIONAL),
            ),
            finnhub.copy(
                id = SourceId.of("logoapi"),
                name = "logoapi.sh",
                capabilities = setOf(Capability.LOGO),
                roles = emptySet(),
            ),
        ),
    )
}

private fun JsonObject.idsOf(tab: String): List<String> =
    getValue(tab).jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }

private fun option(id: String, name: String, isDefault: Boolean, automaticRequests: Boolean) = JsonObject(
    mapOf(
        "id" to JsonPrimitive(id),
        "name" to JsonPrimitive(name),
        "isDefault" to JsonPrimitive(isDefault),
        "automaticRequests" to JsonPrimitive(automaticRequests),
    ),
)

class CompanySourcesRoutesTest : FunSpec({

    test("US company: default source first, SEC EDGAR for the balance sheet, Yahoo Finance last") {
        routeTest(config = dropdownConfig()) { ports ->
            val saved = ports.savedProfile()

            val body = client.get("/profiles/${saved.id}/companies/AAPL/sources").json()

            body.keys shouldBe setOf("priceRatios", "dividends", "balanceSheet", "profile")
            body.idsOf("priceRatios") shouldContainExactly listOf("finnhub", "tiingo", "yahoo")
            body.idsOf("dividends") shouldContainExactly listOf("tiingo", "yahoo")
            body.idsOf("balanceSheet") shouldContainExactly listOf("finnhub", "sec-edgar", "yahoo")
            body.idsOf("profile") shouldContainExactly listOf("finnhub", "yahoo")
            body.getValue("priceRatios") shouldBe JsonArray(
                listOf(
                    option("finnhub", "Finnhub", isDefault = true, automaticRequests = true),
                    option("tiingo", "Tiingo", isDefault = false, automaticRequests = true),
                    option("yahoo", "Yahoo Finance", isDefault = false, automaticRequests = false),
                ),
            )
        }
    }

    test("international company: the international default first, no SEC EDGAR, Yahoo Finance last") {
        routeTest(config = dropdownConfig()) { ports ->
            val saved = ports.savedProfile()

            val body = client.get("/profiles/${saved.id}/companies/SAP.DE/sources").json()

            body.idsOf("priceRatios") shouldContainExactly listOf("eodhd", "tiingo", "yahoo")
            body.idsOf("dividends") shouldContainExactly listOf("eodhd", "tiingo", "yahoo")
            body.idsOf("balanceSheet") shouldContainExactly listOf("yahoo")
            body.idsOf("profile") shouldContainExactly listOf("yahoo")
            body.getValue("dividends").jsonArray.first() shouldBe
                option("eodhd", "EODHD", isDefault = true, automaticRequests = false)
        }
    }

    test("a company of an exchange outside the static exchange table has the US role") {
        routeTest(config = dropdownConfig()) { ports ->
            val saved = ports.savedProfile()

            client.get("/profiles/${saved.id}/companies/BRK.B/sources").json().idsOf("balanceSheet") shouldContainExactly
                listOf("finnhub", "sec-edgar", "yahoo")
        }
    }

    test("a tab without a source of the role is an empty list") {
        val config = testConfig()
        routeTest(config = config) { ports ->
            val saved = ports.savedProfile()

            val body = client.get("/profiles/${saved.id}/companies/SAP.DE/sources").json()

            body.idsOf("priceRatios") shouldContainExactly listOf("eodhd")
            body.idsOf("balanceSheet").shouldBeEmpty()
            body.idsOf("profile").shouldBeEmpty()
        }
    }

    test("no source is called and nothing is read from the cache") {
        routeTest(config = dropdownConfig()) { ports ->
            val saved = ports.savedProfile()

            client.get("/profiles/${saved.id}/companies/AAPL/sources").status shouldBe HttpStatusCode.OK

            ports.marketData.calls.shouldBeEmpty()
            ports.internationalMarketData.calls.shouldBeEmpty()
            ports.balanceSheets.calls.shouldBeEmpty()
            ports.companyCache.getCalls shouldBe 0
            ports.companyCache.getAllCalls shouldBe 0
        }
    }

    test("unknown profile is not found") {
        routeTest(config = dropdownConfig()) {
            client.get("/profiles/${ProfileId.random()}/companies/AAPL/sources").shouldBeProblem(404)
        }
    }

    test("invalid symbol is a bad request") {
        routeTest(config = dropdownConfig()) { ports ->
            val saved = ports.savedProfile()

            client.get("/profiles/${saved.id}/companies/sap.de/sources").shouldBeProblem(400)
        }
    }
})
