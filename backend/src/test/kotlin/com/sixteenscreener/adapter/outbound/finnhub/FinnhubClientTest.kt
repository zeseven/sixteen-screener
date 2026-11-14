package com.sixteenscreener.adapter.outbound.finnhub

import com.sixteenscreener.Fixtures
import com.sixteenscreener.adapter.outbound.http.SourceHttpCall
import com.sixteenscreener.adapter.outbound.http.SourceHttpClient
import com.sixteenscreener.domain.model.BalanceSheet
import com.sixteenscreener.domain.model.BalanceSheetItem
import com.sixteenscreener.domain.model.CompanyProfile
import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.FiscalPeriod
import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.Quote
import com.sixteenscreener.domain.model.Ratio
import com.sixteenscreener.domain.model.RatioCode
import com.sixteenscreener.domain.model.Ratios
import com.sixteenscreener.domain.model.SearchHit
import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.fakes.MutableClock
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

private val aapl = Symbol.of("AAPL")
private val sapDe = Symbol.of("SAP.DE")
private val aaplListing = ListedSymbol(aapl, "APPLE INC", "XNAS", "USD", "Common Stock")
private val sapListing = ListedSymbol(sapDe, "SAP SE", "XETR", "EUR", "Common Stock")

private class MockFinnhub(
    private val status: HttpStatusCode = HttpStatusCode.OK,
    private val body: String = "",
    private val delay: Duration? = null,
) {
    val clock = MutableClock()
    val requests: MutableList<HttpRequestData> = CopyOnWriteArrayList()
    val client = FinnhubClient(
        id = SourceId.of("finnhub"),
        baseUrl = "https://finnhub.test/api/v1/",
        apiKey = "secret-key",
        call = SourceHttpCall(
            SourceHttpClient.create(
                MockEngine { request ->
                    requests += request
                    delay?.let { delay(it) }
                    respond(body, status, jsonHeaders)
                },
            ),
            timeout = 200.milliseconds,
            clock = clock,
        ),
        directoryTimeout = 400.milliseconds,
    )

    val success: SourceCallOutcome get() = SourceCallOutcome.success(clock.now())

    fun single(): HttpRequestData = requests.single()
}

private fun fixture(name: String) = MockFinnhub(body = Fixtures.text("finnhub/$name"))

private fun HttpRequestData.shouldTarget(path: String, vararg parameters: Pair<String, String>) {
    method shouldBe HttpMethod.Get
    url.host shouldBe "finnhub.test"
    url.encodedPath shouldBe "/api/v1$path"
    url.parameters.entries().map { (name, values) -> name to values.single() }.toSet() shouldBe parameters.toSet()
    headers[FinnhubClient.TOKEN_HEADER] shouldBe "secret-key"
    url.parameters["token"] shouldBe null
}

class FinnhubClientTest : FunSpec({

    context("search") {
        test("maps results with valid symbols and sends the query") {
            val finnhub = fixture("search-apple.json")

            val result = finnhub.client.search("apple inc")

            result shouldBe SourceResult.Success(
                listOf(
                    SearchHit(aapl, "APPLE INC", "Common Stock"),
                    SearchHit(Symbol.of("AAPL.SW"), "APPLE INC", "Common Stock"),
                    SearchHit(Symbol.of("APC.DE"), "APPLE INC", "Common Stock"),
                    SearchHit(Symbol.of("AAPL.NE"), "APPLE INC - CDR", null),
                ),
                finnhub.success,
            )
            finnhub.single().shouldTarget("/search", "q" to "apple inc")
        }

        test("no results is an empty success") {
            val finnhub = fixture("search-empty.json")

            finnhub.client.search("zzzz") shouldBe SourceResult.Success(emptyList(), finnhub.success)
        }
    }

    context("quote") {
        test("US symbol: price from c, currency from the listing") {
            val finnhub = fixture("quote-aapl.json")

            finnhub.client.quote(aaplListing) shouldBe SourceResult.Success(Quote(228.52, "USD"), finnhub.success)
            finnhub.single().shouldTarget("/quote", "symbol" to "AAPL")
        }

        test("European symbol is sent with its suffix") {
            val finnhub = fixture("quote-sap-de.json")

            finnhub.client.quote(sapListing) shouldBe SourceResult.Success(Quote(231.85, "EUR"), finnhub.success)
            finnhub.single().shouldTarget("/quote", "symbol" to "SAP.DE")
        }

        test("empty quote of an unknown symbol is no data") {
            val finnhub = fixture("quote-empty.json")

            finnhub.client.quote(aaplListing) shouldBe SourceResult.NoData(finnhub.success)
        }

        test("price with a zero timestamp is no data") {
            val finnhub = MockFinnhub(body = """{"c": 10.5, "t": 0}""")

            finnhub.client.quote(aaplListing) shouldBe SourceResult.NoData(finnhub.success)
        }
    }

    context("ratios") {
        test("all ratio codes mapped from the preferred metric keys") {
            val finnhub = fixture("metric-aapl.json")

            val result = finnhub.client.ratios(aapl)

            result shouldBe SourceResult.Success(
                Ratios(
                    listOf(
                        Ratio(RatioCode.PE, 34.6693),
                        Ratio(RatioCode.PB, 51.4949),
                        Ratio(RatioCode.PS, 8.2981),
                        Ratio(RatioCode.EV_EBITDA, 24.8761),
                        Ratio(RatioCode.ROE, 160.5833),
                        Ratio(RatioCode.ROA, 29.5583),
                        Ratio(RatioCode.NET_MARGIN, 24.3),
                        Ratio(RatioCode.DEBT_TO_EQUITY, 1.5408),
                        Ratio(RatioCode.CURRENT_RATIO, 0.868),
                        Ratio(RatioCode.DIVIDEND_YIELD, 0.4473),
                        Ratio(RatioCode.EPS, 6.5915),
                        Ratio(RatioCode.BETA, 1.2381),
                    ),
                ),
                finnhub.success,
            )
            finnhub.single().shouldTarget("/stock/metric", "symbol" to "AAPL", "metric" to "all")
        }

        test("missing metric keys fall back to alternatives or a null value") {
            val finnhub = fixture("metric-sap-de-partial.json")

            val ratios = finnhub.client.ratios(sapDe).shouldBeInstanceOf<SourceResult.Success<Ratios>>()
                .data.items.associate { it.code to it.value }

            ratios.keys.toList() shouldContainExactly RatioCode.entries
            ratios shouldBe mapOf(
                RatioCode.PE to 48.1203,
                RatioCode.PB to 7.6614,
                RatioCode.PS to 7.9052,
                RatioCode.EV_EBITDA to null,
                RatioCode.ROE to 15.9921,
                RatioCode.ROA to null,
                RatioCode.NET_MARGIN to 9.8114,
                RatioCode.DEBT_TO_EQUITY to null,
                RatioCode.CURRENT_RATIO to 1.1203,
                RatioCode.DIVIDEND_YIELD to null,
                RatioCode.EPS to 4.8183,
                RatioCode.BETA to 0.9871,
            )
            finnhub.single().shouldTarget("/stock/metric", "symbol" to "SAP.DE", "metric" to "all")
        }

        test("empty metric object is no data") {
            val finnhub = fixture("metric-empty.json")

            finnhub.client.ratios(aapl) shouldBe SourceResult.NoData(finnhub.success)
        }
    }

    context("company profile") {
        test("US company with sector, market capitalization and shares converted from millions") {
            val finnhub = fixture("profile2-aapl.json")

            finnhub.client.companyProfile(aapl) shouldBe SourceResult.Success(
                CompanyProfile(
                    name = "Apple Inc",
                    country = "US",
                    currency = "USD",
                    sector = "Technology",
                    industry = null,
                    ipoDate = LocalDate.of(1980, 12, 12),
                    marketCapitalization = 3_393_841_200_000.0,
                    sharesOutstanding = 14_840_390_000.0,
                    employees = null,
                    website = "https://www.apple.com/",
                    description = null,
                ),
                finnhub.success,
            )
            finnhub.single().shouldTarget("/stock/profile2", "symbol" to "AAPL")
        }

        test("European company with blank IPO date") {
            val finnhub = fixture("profile2-sap-de.json")

            val profile = finnhub.client.companyProfile(sapDe).shouldBeInstanceOf<SourceResult.Success<CompanyProfile>>().data

            profile.name shouldBe "SAP SE"
            profile.currency shouldBe "EUR"
            profile.ipoDate shouldBe null
            profile.marketCapitalization shouldBe 270_512_500_000.0
            finnhub.single().shouldTarget("/stock/profile2", "symbol" to "SAP.DE")
        }

        test("empty object is no data") {
            val finnhub = fixture("profile2-empty.json")

            finnhub.client.companyProfile(aapl) shouldBe SourceResult.NoData(finnhub.success)
        }
    }

    context("balance sheet") {
        test("annual periods newest first with items as provided") {
            val finnhub = fixture("financials-reported-aapl.json")

            val sheet = finnhub.client.balanceSheet(aapl).shouldBeInstanceOf<SourceResult.Success<BalanceSheet>>().data

            sheet.periods.map { it.periodEnd } shouldBe listOf(LocalDate.of(2024, 9, 28), LocalDate.of(2023, 9, 30))
            val latest = sheet.periods.first()
            latest.fiscalYear shouldBe 2024
            latest.fiscalPeriod shouldBe FiscalPeriod.FY
            latest.filedAt shouldBe LocalDate.of(2024, 11, 1)
            latest.form shouldBe "10-K"
            latest.items.first() shouldBe
                BalanceSheetItem("us-gaap_CashAndCashEquivalentsAtCarryingValue", "Cash and cash equivalents", 29_943_000_000.0, "usd")
            latest.items.map { it.concept } shouldContainExactly listOf(
                "us-gaap_CashAndCashEquivalentsAtCarryingValue",
                "us-gaap_AssetsCurrent",
                "us-gaap_Assets",
                "us-gaap_CommonStockSharesOutstanding",
                "us-gaap_LiabilitiesCurrent",
                "us-gaap_StockholdersEquity",
            )
            latest.items.single { it.concept == "us-gaap_CommonStockSharesOutstanding" }.unit shouldBe "shares"
            finnhub.single().shouldTarget("/stock/financials-reported", "symbol" to "AAPL", "freq" to "annual")
        }

        test("balance sheet given as a concept map is mapped with the concept as label") {
            val finnhub = MockFinnhub(
                body = """
                    {"data": [{"year": 2019, "quarter": 0, "form": "10-K", "endDate": "2019-09-28 00:00:00",
                      "filedDate": "2019-10-31 00:00:00", "report": {"bs": {"Assets": 338516000000, "Note": "text"}}}]}
                """.trimIndent(),
            )

            val period = finnhub.client.balanceSheet(aapl)
                .shouldBeInstanceOf<SourceResult.Success<BalanceSheet>>().data.periods.single()

            period.items shouldBe listOf(BalanceSheetItem("Assets", "Assets", 338_516_000_000.0, null))
        }

        test("quarterly reports and amended duplicates are dropped, the latest filing of a period wins") {
            val finnhub = MockFinnhub(
                body = """
                    {"data": [
                      {"year": 2024, "quarter": 0, "form": "10-K", "endDate": "2024-12-31 00:00:00", "filedDate": "2025-02-01 00:00:00",
                       "report": {"bs": [{"concept": "Assets", "label": "Assets", "unit": "usd", "value": 1}]}},
                      {"year": 2024, "quarter": 0, "form": "10-K/A", "endDate": "2024-12-31 00:00:00", "filedDate": "2025-04-01 00:00:00",
                       "report": {"bs": [{"concept": "Assets", "label": "Assets", "unit": "usd", "value": 2}]}},
                      {"year": 2025, "quarter": 1, "form": "10-Q", "endDate": "2025-03-31 00:00:00", "filedDate": "2025-05-01 00:00:00",
                       "report": {"bs": [{"concept": "Assets", "label": "Assets", "unit": "usd", "value": 3}]}}
                    ]}
                """.trimIndent(),
            )

            val periods = finnhub.client.balanceSheet(aapl)
                .shouldBeInstanceOf<SourceResult.Success<BalanceSheet>>().data.periods

            periods.map { it.form to it.items.single().value } shouldBe listOf("10-K/A" to 2.0)
        }

        test("empty balance sheet is no data") {
            val finnhub = fixture("financials-reported-empty.json")

            finnhub.client.balanceSheet(sapDe) shouldBe SourceResult.NoData(finnhub.success)
            finnhub.single().shouldTarget("/stock/financials-reported", "symbol" to "SAP.DE", "freq" to "annual")
        }
    }

    context("exchange symbols") {
        test("valid listings with currency are mapped, others skipped") {
            val finnhub = fixture("symbols-de.json")

            finnhub.client.exchangeSymbols(ExchangeCode("DE")) shouldBe SourceResult.Success(
                listOf(
                    ListedSymbol(sapDe, "SAP SE", "XETR", "EUR", "Common Stock"),
                    ListedSymbol(Symbol.of("SIE.DE"), "SIEMENS AG-REG", "XETR", "EUR", "Common Stock"),
                    ListedSymbol(Symbol.of("APC.DE"), "APC.DE", "XETR", "EUR", "Common Stock"),
                ),
                finnhub.success,
            )
            finnhub.single().shouldTarget("/stock/symbol", "exchange" to "DE")
        }

        test("empty list is no data") {
            val finnhub = fixture("symbols-empty.json")

            finnhub.client.exchangeSymbols(ExchangeCode("XX")) shouldBe SourceResult.NoData(finnhub.success)
        }

        test("the directory download uses its own, longer timeout") {
            val finnhub = MockFinnhub(body = Fixtures.text("finnhub/symbols-de.json"), delay = 300.milliseconds)

            finnhub.client.exchangeSymbols(ExchangeCode("DE")).shouldBeInstanceOf<SourceResult.Success<*>>()
            finnhub.client.quote(aaplListing) shouldBe SourceResult.SourceError(SourceCallOutcome.timeout(finnhub.clock.now()))
        }
    }

    context("failures") {
        val calls: List<Pair<String, suspend (FinnhubClient) -> SourceResult<*>>> = listOf(
            "search" to { it.search("apple") },
            "quote" to { it.quote(aaplListing) },
            "ratios" to { it.ratios(aapl) },
            "company profile" to { it.companyProfile(aapl) },
            "balance sheet" to { it.balanceSheet(aapl) },
            "exchange symbols" to { it.exchangeSymbols(ExchangeCode.US) },
        )

        withData(nameFn = { "429 on ${it.first} is limit reached rejected by the source" }, calls) { (_, call) ->
            val finnhub = MockFinnhub(HttpStatusCode.TooManyRequests, """{"error":"API limit reached."}""")

            val result = call(finnhub.client).shouldBeInstanceOf<SourceResult.LimitReached>()

            result.outcome shouldBe SourceCallOutcome.httpError(429, finnhub.clock.now())
            result.rejectedBySource shouldBe true
        }

        withData(nameFn = { "403 on ${it.first} is not supported with the outcome kept" }, calls) { (_, call) ->
            val finnhub = MockFinnhub(HttpStatusCode.Forbidden, """{"error":"You don't have access to this resource."}""")

            call(finnhub.client) shouldBe SourceResult.NotSupported(SourceCallOutcome.httpError(403, finnhub.clock.now()))
        }

        withData(nameFn = { "5xx on ${it.first} is a source error" }, calls) { (_, call) ->
            val finnhub = MockFinnhub(HttpStatusCode.BadGateway, "<html>Bad gateway</html>")

            call(finnhub.client) shouldBe SourceResult.SourceError(SourceCallOutcome.httpError(502, finnhub.clock.now()))
        }

        withData(nameFn = { "timeout on ${it.first} is a source error" }, calls) { (_, call) ->
            val finnhub = MockFinnhub(body = "{}", delay = 2.seconds)

            call(finnhub.client) shouldBe SourceResult.SourceError(SourceCallOutcome.timeout(finnhub.clock.now()))
        }

        withData(nameFn = { "malformed JSON on ${it.first} is an application error" }, calls) { (_, call) ->
            val finnhub = MockFinnhub(body = """{"c": 12.5, "result": [""")

            call(finnhub.client) shouldBe SourceResult.SourceError(SourceCallOutcome.appError(finnhub.clock.now()))
        }
    }
})
