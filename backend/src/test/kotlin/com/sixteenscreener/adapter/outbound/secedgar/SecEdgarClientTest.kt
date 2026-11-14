package com.sixteenscreener.adapter.outbound.secedgar

import com.sixteenscreener.Fixtures
import com.sixteenscreener.adapter.outbound.http.SourceHttpCall
import com.sixteenscreener.adapter.outbound.http.SourceHttpClient
import com.sixteenscreener.domain.model.BalanceSheet
import com.sixteenscreener.domain.model.BalanceSheetItem
import com.sixteenscreener.domain.model.BalanceSheetPeriod
import com.sixteenscreener.domain.model.FiscalPeriod
import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceCallResult
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.fakes.InMemorySecCikRepository
import com.sixteenscreener.fakes.MutableClock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

private const val USER_AGENT = "Sixteen Screener test@example.com"
private const val TICKERS_URL = "https://www.sec.test/files/company_tickers.json"

private fun companyFactsUrl(cik: String) = "https://data.sec.test/api/xbrl/companyfacts/CIK$cik.json"

private data class MockResponse(
    val body: String,
    val status: HttpStatusCode = HttpStatusCode.OK,
    val delay: kotlin.time.Duration? = null,
)

private class MockSec(
    val repository: InMemorySecCikRepository = InMemorySecCikRepository(),
    grantedCalls: Int = Int.MAX_VALUE,
) {
    val clock = MutableClock()
    val responses = ConcurrentHashMap<String, MockResponse>()
    val requests: MutableList<HttpRequestData> = CopyOnWriteArrayList()
    private val remainingBudget = AtomicInteger(grantedCalls)
    val budgetRequests = AtomicInteger()

    private val call = SourceHttpCall(
        SourceHttpClient.create(
            MockEngine { request ->
                requests += request
                val response = responses[request.url.toString()]
                    ?: return@MockEngine respond("", HttpStatusCode.NotFound)
                response.delay?.let { delay(it) }
                respond(response.body, response.status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ),
        timeout = 200.milliseconds,
        clock = clock,
    )

    val client = SecEdgarClient(
        id = SourceId.of("sec-edgar"),
        baseUrl = "https://data.sec.test/",
        userAgent = USER_AGENT,
        call = call,
        ciks = SecCikResolver(
            filesBaseUrl = "https://www.sec.test/",
            userAgent = USER_AGENT,
            call = call,
            repository = repository,
            budget = {
                budgetRequests.incrementAndGet()
                remainingBudget.getAndDecrement() > 0
            },
            clock = clock,
            downloadTimeout = 400.milliseconds,
        ),
    )

    init {
        responses[TICKERS_URL] = MockResponse(Fixtures.text("secedgar/company_tickers.json"))
    }

    val success: SourceCallOutcome get() = SourceCallOutcome.success(clock.now())

    fun companyFacts(cik: String, fixture: String) {
        responses[companyFactsUrl(cik)] = MockResponse(Fixtures.text("secedgar/$fixture"))
    }

    fun requestedUrls(): List<String> = requests.map { it.url.toString() }

    suspend fun balanceSheet(symbol: String) = client.balanceSheet(Symbol.of(symbol))
}

private fun item(concept: String, label: String, value: Double, unit: String = "USD") =
    BalanceSheetItem(concept, label, value, unit)

private fun annual(end: String, fiscalYear: Int, filed: String, form: String, vararg items: BalanceSheetItem) =
    BalanceSheetPeriod(LocalDate.parse(end), fiscalYear, FiscalPeriod.FY, LocalDate.parse(filed), form, items.toList())

private fun SourceResult<BalanceSheet>.periods(): List<BalanceSheetPeriod> =
    shouldBeInstanceOf<SourceResult.Success<BalanceSheet>>().data.periods

private const val EQUITY_LABEL = "Stockholders' Equity Attributable to Parent"

class SecEdgarClientTest : FunSpec({

    context("balance sheet extraction") {
        test("company with full data: annual instant facts per period end, newest first, labels and units as provided") {
            val sec = MockSec()
            sec.companyFacts("0000320193", "companyfacts-aapl.json")

            val result = sec.balanceSheet("AAPL")

            result shouldBe SourceResult.Success(
                BalanceSheet(
                    listOf(
                        annual(
                            "2025-09-27", 2025, "2025-10-31", "10-K",
                            item("Assets", "Assets", 359241000000.0),
                            item("AssetsCurrent", "Assets, Current", 147957000000.0),
                            item("CashAndCashEquivalentsAtCarryingValue", "Cash and Cash Equivalents, at Carrying Value", 35934000000.0),
                            item("Liabilities", "Liabilities", 285508000000.0),
                            item("StockholdersEquity", EQUITY_LABEL, 73733000000.0),
                            item("CommonStockSharesOutstanding", "Common Stock, Shares, Outstanding", 14776353000.0, "shares"),
                        ),
                        annual(
                            "2024-09-28", 2024, "2024-11-01", "10-K",
                            item("Assets", "Assets", 364980000000.0),
                            item("AssetsCurrent", "Assets, Current", 152987000000.0),
                            item("CashAndCashEquivalentsAtCarryingValue", "Cash and Cash Equivalents, at Carrying Value", 29943000000.0),
                            item("Liabilities", "Liabilities", 308030000000.0),
                            item("StockholdersEquity", EQUITY_LABEL, 56950000000.0),
                            item("CommonStockSharesOutstanding", "Common Stock, Shares, Outstanding", 15116786000.0, "shares"),
                        ),
                        annual(
                            "2023-09-30", 2023, "2024-11-01", "10-K",
                            item("Assets", "Assets", 352583000000.0),
                            item("AssetsCurrent", "Assets, Current", 143566000000.0),
                            item("CashAndCashEquivalentsAtCarryingValue", "Cash and Cash Equivalents, at Carrying Value", 29965000000.0),
                            item("Liabilities", "Liabilities", 290437000000.0),
                            item("StockholdersEquity", EQUITY_LABEL, 62146000000.0),
                            item("CommonStockSharesOutstanding", "Common Stock, Shares, Outstanding", 15550061000.0, "shares"),
                        ),
                        annual("2022-09-24", 2022, "2024-11-01", "10-K", item("StockholdersEquity", EQUITY_LABEL, 50672000000.0)),
                    ),
                ),
                sec.success,
            )
        }

        test("quarterly facts, duration facts and other taxonomies are ignored") {
            val sec = MockSec()
            sec.companyFacts("0000320193", "companyfacts-aapl.json")

            val periods = sec.balanceSheet("AAPL").periods()

            periods.map { it.periodEnd } shouldContainExactly
                listOf("2025-09-27", "2024-09-28", "2023-09-30", "2022-09-24").map(LocalDate::parse)
            periods.flatMap { it.items }.map { it.concept }.toSet() shouldBe setOf(
                "Assets", "AssetsCurrent", "CashAndCashEquivalentsAtCarryingValue", "Liabilities",
                "StockholdersEquity", "CommonStockSharesOutstanding",
            )
        }

        test("amended filing (10-K/A) wins for the values it reports; other values stay from the original 10-K") {
            val sec = MockSec()
            sec.companyFacts("0001000002", "companyfacts-amended.json")

            sec.balanceSheet("AMND").periods() shouldBe listOf(
                annual(
                    "2024-12-31", 2024, "2025-04-15", "10-K/A",
                    item("Assets", "Assets", 510000.0),
                    item("Liabilities", "Liabilities", 300000.0),
                    item("StockholdersEquity", EQUITY_LABEL, 210000.0),
                ),
            )
        }

        test("facts duplicated across filings: the filing of the period wins, prior-year values fill only gaps") {
            val sec = MockSec()
            sec.companyFacts("0001000001", "companyfacts-duplicated.json")

            sec.balanceSheet("DUPE").periods() shouldBe listOf(
                annual(
                    "2024-12-31", 2024, "2025-02-28", "10-K",
                    item("Assets", "Assets", 1200000.0),
                    item("Goodwill", "Goodwill", 45000.0),
                ),
                annual(
                    "2023-12-31", 2023, "2024-02-29", "10-K",
                    item("Assets", "Assets", 1100000.0),
                    item("Goodwill", "Goodwill", 40000.0),
                ),
                annual("2022-12-31", 2022, "2024-02-29", "10-K", item("Assets", "Assets", 1000000.0)),
            )
        }

        test("company with quarterly filings only has no data") {
            val sec = MockSec()
            sec.companyFacts("0001000003", "companyfacts-quarterly.json")

            sec.balanceSheet("QTRS") shouldBe SourceResult.NoData(sec.success)
        }

        test("company facts without us-gaap facts have no data") {
            val sec = MockSec()
            sec.responses[companyFactsUrl("0000789019")] = MockResponse("""{"cik": 789019, "facts": {"dei": {}}}""")

            sec.balanceSheet("MSFT") shouldBe SourceResult.NoData(sec.success)
        }
    }

    context("requests") {
        test("both hosts are called with the User-Agent header and a zero-padded CIK") {
            val sec = MockSec()
            sec.companyFacts("0000320193", "companyfacts-aapl.json")

            sec.balanceSheet("AAPL")

            sec.requestedUrls() shouldContainExactly listOf(TICKERS_URL, companyFactsUrl("0000320193"))
            sec.requests.forEach { request ->
                request.method shouldBe HttpMethod.Get
                request.headers[HttpHeaders.UserAgent] shouldBe USER_AGENT
            }
        }

        test("share class ticker BRK.B is looked up as BRK-B") {
            val sec = MockSec()
            sec.companyFacts("0001067983", "companyfacts-amended.json")

            sec.balanceSheet("BRK.B").shouldBeInstanceOf<SourceResult.Success<BalanceSheet>>()

            sec.requestedUrls().last() shouldBe companyFactsUrl("0001067983")
            sec.repository.stored() shouldBe mapOf("BRK-B" to 1067983L)
        }

        test("non-US symbol is not supported without any call") {
            val sec = MockSec()

            sec.balanceSheet("SAP.DE") shouldBe SourceResult.NotSupported(outcome = null)

            sec.requests.shouldBeEmpty()
            sec.budgetRequests.get() shouldBe 0
        }
    }

    context("ticker to CIK") {
        test("ticker without CIK has no data after the ticker table download") {
            val sec = MockSec()

            sec.balanceSheet("ZZZZ") shouldBe SourceResult.NoData(sec.success)

            sec.requestedUrls() shouldContainExactly listOf(TICKERS_URL)
            sec.repository.stored().shouldBeEmpty()
        }

        test("the ticker table is downloaded once and kept in memory for all tickers") {
            val sec = MockSec()
            sec.companyFacts("0000320193", "companyfacts-aapl.json")
            sec.companyFacts("0001000002", "companyfacts-amended.json")

            sec.balanceSheet("AAPL")
            sec.balanceSheet("AMND")
            sec.balanceSheet("AAPL")

            sec.requestedUrls().count { it == TICKERS_URL } shouldBe 1
            sec.budgetRequests.get() shouldBe 1
            sec.repository.stored() shouldBe mapOf("AAPL" to 320193L, "AMND" to 1000002L)
        }

        test("a CIK stored in the repository avoids the ticker table download") {
            val sec = MockSec()
            sec.repository.save("AAPL", 320193, sec.clock.now())
            sec.companyFacts("0000320193", "companyfacts-aapl.json")

            sec.balanceSheet("AAPL").shouldBeInstanceOf<SourceResult.Success<BalanceSheet>>()

            sec.requestedUrls() shouldContainExactly listOf(companyFactsUrl("0000320193"))
            sec.budgetRequests.get() shouldBe 0
        }

        test("an expired stored CIK is resolved from the ticker table again") {
            val sec = MockSec()
            sec.repository.save("AAPL", 320193, sec.clock.now().minus(Duration.ofDays(15)))
            sec.companyFacts("0000320193", "companyfacts-aapl.json")

            sec.balanceSheet("AAPL")

            sec.requestedUrls() shouldContainExactly listOf(TICKERS_URL, companyFactsUrl("0000320193"))
        }

        test("a ticker missing from a table downloaded less than a day ago is no data without a call") {
            val sec = MockSec()
            sec.balanceSheet("ZZZZ")
            sec.clock.advanceBy(Duration.ofHours(23))

            sec.balanceSheet("YYYY") shouldBe SourceResult.NoData(outcome = null)

            sec.requests.size shouldBe 1
        }

        test("the ticker table is downloaded again for a missing ticker after a day") {
            val sec = MockSec()
            sec.balanceSheet("ZZZZ")
            sec.clock.advanceBy(Duration.ofDays(1))

            sec.balanceSheet("ZZZZ") shouldBe SourceResult.NoData(sec.success)

            sec.requestedUrls() shouldContainExactly listOf(TICKERS_URL, TICKERS_URL)
        }

        test("concurrent callers share one ticker table download") {
            val sec = MockSec()
            sec.responses[TICKERS_URL] = MockResponse(Fixtures.text("secedgar/company_tickers.json"), delay = 50.milliseconds)
            sec.companyFacts("0000320193", "companyfacts-aapl.json")
            sec.companyFacts("0001000002", "companyfacts-amended.json")

            val results = coroutineScope {
                listOf("AAPL", "AMND", "AAPL", "ZZZZ").map { symbol -> async { sec.balanceSheet(symbol) } }.awaitAll()
            }

            sec.requestedUrls().count { it == TICKERS_URL } shouldBe 1
            sec.budgetRequests.get() shouldBe 1
            results.take(3).forEach { it.shouldBeInstanceOf<SourceResult.Success<BalanceSheet>>() }
            results.last().shouldBeInstanceOf<SourceResult.NoData>()
        }

        test("concurrent callers share a failed download; a later call downloads again") {
            val sec = MockSec()
            sec.responses[TICKERS_URL] = MockResponse("", HttpStatusCode.ServiceUnavailable, delay = 50.milliseconds)

            val results = coroutineScope {
                listOf("AAPL", "AMND").map { symbol -> async { sec.balanceSheet(symbol) } }.awaitAll()
            }

            results.forEach { it shouldBe SourceResult.SourceError(SourceCallOutcome.httpError(503, sec.clock.now())) }
            sec.requests.size shouldBe 1

            sec.responses[TICKERS_URL] = MockResponse(Fixtures.text("secedgar/company_tickers.json"))
            sec.companyFacts("0000320193", "companyfacts-aapl.json")
            sec.balanceSheet("AAPL").shouldBeInstanceOf<SourceResult.Success<BalanceSheet>>()
        }

        test("the ticker table download needs its own request budget; without it the limit is reached without a call") {
            val sec = MockSec(grantedCalls = 0)

            sec.balanceSheet("AAPL") shouldBe SourceResult.LimitReached(outcome = null)

            sec.requests.shouldBeEmpty()
            sec.budgetRequests.get() shouldBe 1
        }

        test("an empty ticker table is no data and is not kept") {
            val sec = MockSec()
            sec.responses[TICKERS_URL] = MockResponse("{}")

            sec.balanceSheet("AAPL") shouldBe SourceResult.NoData(sec.success)
            sec.balanceSheet("AAPL")

            sec.requests.size shouldBe 2
        }

        test("a failing repository doesn't fail the lookup") {
            val sec = MockSec(InMemorySecCikRepository().apply { failing = true })
            sec.companyFacts("0000320193", "companyfacts-aapl.json")

            sec.balanceSheet("AAPL").shouldBeInstanceOf<SourceResult.Success<BalanceSheet>>()
        }
    }

    context("failures") {
        test("403 for a request without an accepted User-Agent is a source error with the status recorded") {
            val sec = MockSec()
            sec.responses[TICKERS_URL] = MockResponse("Request Rate Threshold Exceeded", HttpStatusCode.Forbidden)

            sec.balanceSheet("AAPL") shouldBe SourceResult.SourceError(SourceCallOutcome.httpError(403, sec.clock.now()))
        }

        test("403 for company facts is a source error") {
            val sec = MockSec()
            sec.responses[companyFactsUrl("0000320193")] = MockResponse("", HttpStatusCode.Forbidden)

            sec.balanceSheet("AAPL") shouldBe SourceResult.SourceError(SourceCallOutcome.httpError(403, sec.clock.now()))
        }

        test("404 for company facts is no data") {
            val sec = MockSec()

            sec.balanceSheet("MSFT") shouldBe SourceResult.NoData(SourceCallOutcome.httpError(404, sec.clock.now()))
        }

        test("429 is a limit reached by the source") {
            val sec = MockSec()
            sec.responses[companyFactsUrl("0000320193")] = MockResponse("", HttpStatusCode.TooManyRequests)

            val result = sec.balanceSheet("AAPL").shouldBeInstanceOf<SourceResult.LimitReached>()

            result.rejectedBySource shouldBe true
        }

        test("timeout of the company facts request") {
            val sec = MockSec()
            sec.responses[companyFactsUrl("0000320193")] =
                MockResponse(Fixtures.text("secedgar/companyfacts-aapl.json"), delay = 1000.milliseconds)

            val result = sec.balanceSheet("AAPL").shouldBeInstanceOf<SourceResult.SourceError>()

            result.outcome.result shouldBe SourceCallResult.TIMEOUT
        }

        test("timeout of the ticker table download") {
            val sec = MockSec()
            sec.responses[TICKERS_URL] = MockResponse(Fixtures.text("secedgar/company_tickers.json"), delay = 1000.milliseconds)

            val result = sec.balanceSheet("AAPL").shouldBeInstanceOf<SourceResult.SourceError>()

            result.outcome.result shouldBe SourceCallResult.TIMEOUT
        }

        test("malformed company facts are an application error") {
            val sec = MockSec()
            sec.responses[companyFactsUrl("0000320193")] = MockResponse("""{"facts": {"us-gaap": {"Assets": [}}""")

            val result = sec.balanceSheet("AAPL").shouldBeInstanceOf<SourceResult.SourceError>()

            result.outcome.result shouldBe SourceCallResult.APP_ERROR
        }

        test("malformed ticker table is an application error") {
            val sec = MockSec()
            sec.responses[TICKERS_URL] = MockResponse("""[1, 2]""")

            val result = sec.balanceSheet("AAPL").shouldBeInstanceOf<SourceResult.SourceError>()

            result.outcome.result shouldBe SourceCallResult.APP_ERROR
        }
    }
})
