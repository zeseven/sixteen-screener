package com.sixteenscreener.adapter.outbound.eodhd

import com.sixteenscreener.Fixtures
import com.sixteenscreener.adapter.outbound.http.SourceHttpCall
import com.sixteenscreener.adapter.outbound.http.SourceHttpClient
import com.sixteenscreener.domain.model.Dividend
import com.sixteenscreener.domain.model.Dividends
import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.Quote
import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.fakes.MutableClock
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
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
private val toyota = Symbol.of("7203.T")
private val aaplListing = ListedSymbol(aapl, "APPLE INC", "XNAS", "USD", "Common Stock")
private val sapListing = ListedSymbol(sapDe, "SAP SE", "XETR", "EUR", "Common Stock")
private val toyotaListing = ListedSymbol(toyota, "TOYOTA MOTOR CORP", "XTKS", "JPY", "Common Stock")
private val shelListing = ListedSymbol(Symbol.of("SHEL.L"), "SHELL PLC", "XLON", "GBP", "Common Stock")
private val naspersListing = ListedSymbol(Symbol.of("NPN.JO"), "NASPERS LTD", "XJSE", "ZAR", "Common Stock")

private class MockEodhd(
    private val status: HttpStatusCode = HttpStatusCode.OK,
    private val body: String = "",
    private val delay: Duration? = null,
    private val handler: MockRequestHandler = { respond(body, status, jsonHeaders) },
) {
    val clock = MutableClock()
    val requests: MutableList<HttpRequestData> = CopyOnWriteArrayList()
    val client = EodhdClient(
        id = SourceId.of("eodhd"),
        baseUrl = "https://eodhd.test/api/",
        apiToken = "secret-token",
        call = SourceHttpCall(
            SourceHttpClient.create(
                MockEngine { request ->
                    requests += request
                    delay?.let { delay(it) }
                    handler(request)
                },
            ),
            timeout = 200.milliseconds,
            clock = clock,
        ),
        clock = clock,
    )

    val success: SourceCallOutcome get() = SourceCallOutcome.success(clock.now())

    fun single(): HttpRequestData = requests.single()
}

private fun fixture(name: String) = MockEodhd(body = Fixtures.text("eodhd/$name"))

private fun HttpRequestData.shouldTarget(path: String, vararg parameters: Pair<String, String>) {
    method shouldBe HttpMethod.Get
    url.host shouldBe "eodhd.test"
    url.encodedPath shouldBe "/api$path"
    url.parameters.entries().map { (name, values) -> name to values.single() }.toSet() shouldBe
        (parameters.toSet() + ("fmt" to "json"))
    headers[HttpHeaders.Authorization] shouldBe "Bearer secret-token"
    url.parameters["api_token"] shouldBe null
}

class EodhdClientTest : FunSpec({

    context("symbols") {
        withData(
            nameFn = { "${it.first} is ${it.second}" },
            "SAP.DE" to "SAP.XETRA",
            "SHEL.L" to "SHEL.LSE",
            "PKO.WA" to "PKO.WAR",
            "MC.PA" to "MC.PA",
            "AAPL" to "AAPL.US",
            "BRK.B" to "BRK-B.US",
            "X.HM" to "X.HM",
            "X.HA" to "X.HA",
            "NPN.JO" to "NPN.JSE",
            "X.CS" to "X.BC",
        ) { (symbol, expected) ->
            EodhdClient.eodhdSymbolOf(Symbol.of(symbol)) shouldBe expected
        }

        withData(
            nameFn = { (symbol, expected) -> "EODHD ticker of $symbol is $expected" },
            "AAPL" to "AAPL",
            "BRK.B" to "BRK-B",
            "NOVO-B.CO" to "NOVO-B",
            "SAP.DE" to "SAP",
            "7203.T" to "7203",
        ) { (symbol, expected) ->
            EodhdClient.eodhdTickerOf(Symbol.of(symbol)) shouldBe expected
        }

        test("a symbol of an exchange without an EODHD code has no EODHD symbol") {
            EodhdClient.eodhdSymbolOf(toyota) shouldBe null
            EodhdClient.eodhdSymbolOf(Symbol.of("SHEL.IL")) shouldBe null
            EodhdClient.eodhdSymbolOf(Symbol.of("X.BC")) shouldBe null
        }

        withData(
            nameFn = { "$it, which EODHD no longer lists, has no EODHD symbol" },
            "BAS.BE",
            "MAREL.IC",
            "0700.HK",
            "TEVA.TA",
        ) { symbol ->
            EodhdClient.eodhdSymbolOf(Symbol.of(symbol)) shouldBe null
        }
    }

    context("quote") {
        test("maps the delayed price with the currency of the listing and sends the EODHD symbol") {
            val eodhd = fixture("real-time-aapl.json")

            eodhd.client.quote(sapListing) shouldBe SourceResult.Success(Quote(352.45, "EUR"), eodhd.success)
            eodhd.single().shouldTarget("/real-time/SAP.XETRA")
        }

        test("a US listing is requested on the US exchange code") {
            val eodhd = fixture("real-time-aapl.json")

            eodhd.client.quote(aaplListing) shouldBe SourceResult.Success(Quote(352.45, "USD"), eodhd.success)
            eodhd.single().shouldTarget("/real-time/AAPL.US")
        }

        test("a London price quoted in pence is converted to pounds") {
            val eodhd = fixture("real-time-shel-lse.json")

            eodhd.client.quote(shelListing) shouldBe SourceResult.Success(Quote(35.745, "GBP"), eodhd.success)
            eodhd.single().shouldTarget("/real-time/SHEL.LSE")
        }

        test("a Johannesburg price quoted in cents is converted to rand") {
            val eodhd = MockEodhd(body = """{"code":"NPN.JSE","timestamp":1795106160,"close":512345}""")

            eodhd.client.quote(naspersListing) shouldBe SourceResult.Success(Quote(5123.45, "ZAR"), eodhd.success)
            eodhd.single().shouldTarget("/real-time/NPN.JSE")
        }

        test("a price of an exchange quoted in major units is kept") {
            val eodhd = MockEodhd(body = """{"code":"SAP.XETRA","timestamp":1795106160,"close":3574.5}""")

            eodhd.client.quote(sapListing) shouldBe SourceResult.Success(Quote(3574.5, "EUR"), eodhd.success)
        }

        test("a London price below one penny is converted without being dropped") {
            val eodhd = MockEodhd(body = """{"code":"SHEL.LSE","timestamp":1795106160,"close":0.5}""")

            eodhd.client.quote(shelListing) shouldBe SourceResult.Success(Quote(0.005, "GBP"), eodhd.success)
        }

        test("a batch answer takes the first quote") {
            val eodhd = fixture("real-time-batch.json")

            eodhd.client.quote(aaplListing) shouldBe SourceResult.Success(Quote(352.45, "USD"), eodhd.success)
        }

        test("a quote of an unknown symbol with NA values is no data") {
            val eodhd = fixture("real-time-na.json")

            eodhd.client.quote(sapListing) shouldBe SourceResult.NoData(eodhd.success)
        }

        test("a zero price or a zero timestamp is no data") {
            MockEodhd(body = """{"code":"SAP.XETRA","timestamp":1795035600,"close":0}""").let { eodhd ->
                eodhd.client.quote(sapListing) shouldBe SourceResult.NoData(eodhd.success)
            }
            MockEodhd(body = """{"code":"SAP.XETRA","timestamp":0,"close":12.5}""").let { eodhd ->
                eodhd.client.quote(sapListing) shouldBe SourceResult.NoData(eodhd.success)
            }
        }

        test("an empty batch answer is no data") {
            val eodhd = MockEodhd(body = "[]")

            eodhd.client.quote(sapListing) shouldBe SourceResult.NoData(eodhd.success)
        }

        test("a symbol of an exchange without an EODHD code is not supported without a request") {
            val eodhd = MockEodhd()

            eodhd.client.quote(toyotaListing) shouldBe SourceResult.NotSupported(outcome = null)
            eodhd.requests.shouldBeEmpty()
        }

        test("a symbol of an exchange EODHD no longer lists is not supported without a request") {
            val eodhd = MockEodhd()
            val tencent = ListedSymbol(Symbol.of("0700.HK"), "TENCENT HOLDINGS LTD", "XHKG", "HKD", "Common Stock")

            eodhd.client.quote(tencent) shouldBe SourceResult.NotSupported(outcome = null)
            eodhd.requests.shouldBeEmpty()
        }
    }

    context("dividends") {
        test("maps the dividends of the last year newest first and requests them from a year ago") {
            val eodhd = fixture("div-aapl.json")

            eodhd.client.dividends(aapl) shouldBe SourceResult.Success(
                Dividends(
                    listOf(
                        Dividend(
                            exDate = LocalDate.parse("2025-08-11"),
                            paymentDate = LocalDate.parse("2025-08-14"),
                            recordDate = LocalDate.parse("2025-08-11"),
                            declarationDate = LocalDate.parse("2025-07-31"),
                            amount = 0.26,
                            currency = "USD",
                        ),
                        Dividend(
                            exDate = LocalDate.parse("2025-05-12"),
                            paymentDate = LocalDate.parse("2025-05-15"),
                            recordDate = LocalDate.parse("2025-05-12"),
                            declarationDate = LocalDate.parse("2025-05-01"),
                            amount = 0.26,
                            currency = "USD",
                        ),
                        Dividend(
                            exDate = LocalDate.parse("2025-02-10"),
                            paymentDate = LocalDate.parse("2025-02-13"),
                            recordDate = LocalDate.parse("2025-02-10"),
                            declarationDate = LocalDate.parse("2025-01-30"),
                            amount = 0.25,
                            currency = "USD",
                        ),
                    ),
                ),
                eodhd.success,
            )
            eodhd.single().shouldTarget("/div/AAPL.US", "from" to "2025-11-14")
        }

        test("an empty list is a company without dividends") {
            val eodhd = MockEodhd(body = "[]")

            eodhd.client.dividends(sapDe) shouldBe SourceResult.Success(Dividends(emptyList()), eodhd.success)
            eodhd.single().shouldTarget("/div/SAP.XETRA", "from" to "2025-11-14")
        }

        test("entries without an ex-date, a value or a currency are skipped; missing dates are null") {
            val eodhd = MockEodhd(
                body = """
                    [
                      {"date": "2026-05-04", "declarationDate": null, "recordDate": "", "paymentDate": "0000-00-00",
                       "value": 2.35, "currency": "EUR"},
                      {"date": null, "value": 1.0, "currency": "EUR"},
                      {"date": "2026-01-02", "value": "NA", "currency": "EUR"},
                      {"date": "2026-01-03", "value": 1.0}
                    ]
                """.trimIndent(),
            )

            eodhd.client.dividends(sapDe) shouldBe SourceResult.Success(
                Dividends(listOf(Dividend(LocalDate.parse("2026-05-04"), null, null, null, 2.35, "EUR"))),
                eodhd.success,
            )
        }

        test("a symbol of an exchange without an EODHD code is not supported without a request") {
            val eodhd = MockEodhd()

            eodhd.client.dividends(toyota) shouldBe SourceResult.NotSupported(outcome = null)
            eodhd.requests.shouldBeEmpty()
        }
    }

    context("data the free plan doesn't provide") {
        val calls: List<Pair<String, suspend (EodhdClient) -> SourceResult<*>>> = listOf(
            "search" to { it.search("sap") },
            "ratios" to { it.ratios(sapDe) },
            "company profile" to { it.companyProfile(sapDe) },
            "exchange symbols" to { it.exchangeSymbols(ExchangeCode("DE")) },
        )

        withData(nameFn = { "${it.first} is not supported without a request" }, calls) { (_, call) ->
            val eodhd = MockEodhd()

            call(eodhd.client) shouldBe SourceResult.NotSupported(outcome = null)
            eodhd.requests.shouldBeEmpty()
        }
    }

    context("failures") {
        val calls: List<Pair<String, suspend (EodhdClient) -> SourceResult<*>>> = listOf(
            "quote" to { it.quote(sapListing) },
            "dividends" to { it.dividends(sapDe) },
        )

        withData(nameFn = { "402 (daily limit) on ${it.first} is limit reached rejected by the source" }, calls) { (_, call) ->
            val eodhd = MockEodhd(HttpStatusCode.PaymentRequired, """{"error":"Daily limit reached"}""")

            val result = call(eodhd.client).shouldBeInstanceOf<SourceResult.LimitReached>()

            result.outcome shouldBe SourceCallOutcome.httpError(402, eodhd.clock.now())
            result.rejectedBySource shouldBe true
        }

        withData(nameFn = { "429 on ${it.first} is limit reached rejected by the source" }, calls) { (_, call) ->
            val eodhd = MockEodhd(HttpStatusCode.TooManyRequests, "")

            call(eodhd.client).shouldBeInstanceOf<SourceResult.LimitReached>().rejectedBySource shouldBe true
        }

        withData(nameFn = { "403 on ${it.first} is not supported with the outcome kept" }, calls) { (_, call) ->
            val eodhd = MockEodhd(HttpStatusCode.Forbidden, """{"error":"Forbidden","code":403}""")

            call(eodhd.client) shouldBe SourceResult.NotSupported(SourceCallOutcome.httpError(403, eodhd.clock.now()))
        }

        withData(nameFn = { "404 on ${it.first} is no data" }, calls) { (_, call) ->
            val eodhd = MockEodhd(HttpStatusCode.NotFound, """{"error":"Symbol not found","code":404}""")

            call(eodhd.client) shouldBe SourceResult.NoData(SourceCallOutcome.httpError(404, eodhd.clock.now()))
        }

        withData(nameFn = { "401 on ${it.first} is a source error" }, calls) { (_, call) ->
            val eodhd = MockEodhd(HttpStatusCode.Unauthorized, "Unauthenticated")

            call(eodhd.client) shouldBe SourceResult.SourceError(SourceCallOutcome.httpError(401, eodhd.clock.now()))
        }

        withData(nameFn = { "timeout on ${it.first} is a source error" }, calls) { (_, call) ->
            val eodhd = MockEodhd(body = "{}", delay = 2.seconds)

            call(eodhd.client) shouldBe SourceResult.SourceError(SourceCallOutcome.timeout(eodhd.clock.now()))
        }

        withData(nameFn = { "malformed JSON on ${it.first} is an application error" }, calls) { (_, call) ->
            val eodhd = MockEodhd(body = """{"close": 12.5, [""")

            call(eodhd.client) shouldBe SourceResult.SourceError(SourceCallOutcome.appError(eodhd.clock.now()))
        }
    }
})
