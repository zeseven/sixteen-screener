package com.sixteenscreener.adapter.outbound.yahoo

import com.sixteenscreener.Fixtures
import com.sixteenscreener.adapter.outbound.http.BearerTokenProvider
import com.sixteenscreener.adapter.outbound.http.SourceHttpCall
import com.sixteenscreener.adapter.outbound.http.SourceHttpClient
import com.sixteenscreener.domain.model.BalanceSheet
import com.sixteenscreener.domain.model.CompanyProfile
import com.sixteenscreener.domain.model.Dividends
import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.Quote
import com.sixteenscreener.domain.model.RatioCode
import com.sixteenscreener.domain.model.Ratios
import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.fakes.MutableClock
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
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
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

private val aapl = Symbol.of("AAPL")
private val sapDe = Symbol.of("SAP.DE")
private val shelL = Symbol.of("SHEL.L")
private val sberMe = Symbol.of("SBER.ME")
private val aaplListing = ListedSymbol(aapl, "APPLE INC", "XNAS", "USD", "Common Stock")
private val sapListing = ListedSymbol(sapDe, "SAP SE", "XETR", "EUR", "Common Stock")
private val shelListing = ListedSymbol(shelL, "SHELL PLC", "XLON", "GBP", "Common Stock")

private class MockProxy(
    private val status: HttpStatusCode = HttpStatusCode.OK,
    private val body: String = "",
    bearerToken: BearerTokenProvider? = null,
    private val handler: MockRequestHandler = { respond(body, status, jsonHeaders) },
) {
    val clock = MutableClock()
    val requests: MutableList<HttpRequestData> = CopyOnWriteArrayList()
    val client = YahooClient(
        id = SourceId.of("yahoo"),
        baseUrl = "https://yahoo-proxy.test/",
        call = SourceHttpCall(
            SourceHttpClient.create(
                MockEngine { request ->
                    requests += request
                    handler(request)
                },
            ),
            timeout = 200.milliseconds,
            clock = clock,
            bearerToken = bearerToken,
        ),
    )

    val success: SourceCallOutcome get() = SourceCallOutcome.success(clock.now())

    fun single(): HttpRequestData = requests.single()
}

private fun fixture(name: String) = MockProxy(body = Fixtures.text("yahoo/$name"))

private fun HttpRequestData.shouldTarget(path: String) {
    method shouldBe HttpMethod.Get
    url.host shouldBe "yahoo-proxy.test"
    url.encodedPath shouldBe path
    url.parameters.isEmpty() shouldBe true
}

class YahooClientTest : FunSpec({

    context("symbols") {
        withData(
            nameFn = { "${it.first} is ${it.second}" },
            "AAPL" to "AAPL",
            "BRK.B" to "BRK-B",
            "SAP.DE" to "SAP.DE",
            "SHEL.L" to "SHEL.L",
            "SHEL.IL" to "SHEL.IL",
            "7203.T" to "7203.T",
            "NOVO-B.CO" to "NOVO-B.CO",
            "0700.HK" to "0700.HK",
            "RY.TO" to "RY.TO",
        ) { (symbol, expected) ->
            YahooClient.yahooSymbolOf(Symbol.of(symbol)) shouldBe expected
        }

        test("a symbol of an exchange without a Yahoo suffix has no Yahoo symbol") {
            YahooClient.yahooSymbolOf(sberMe) shouldBe null
            YahooClient.yahooSymbolOf(Symbol.of("X.BC")) shouldBe null
        }

        test("every call of a symbol without a Yahoo suffix is not supported without a request") {
            val proxy = MockProxy()
            val listing = ListedSymbol(sberMe, "SBERBANK", "MISX", "RUB", "Common Stock")

            proxy.client.quote(listing) shouldBe SourceResult.NotSupported(outcome = null)
            proxy.client.ratios(sberMe) shouldBe SourceResult.NotSupported(outcome = null)
            proxy.client.dividends(sberMe) shouldBe SourceResult.NotSupported(outcome = null)
            proxy.client.companyProfile(sberMe) shouldBe SourceResult.NotSupported(outcome = null)
            proxy.client.balanceSheet(sberMe) shouldBe SourceResult.NotSupported(outcome = null)
            proxy.requests.shouldBeEmpty()
        }

        test("search and symbol lists are not supported without a request") {
            val proxy = MockProxy()

            proxy.client.search("apple") shouldBe SourceResult.NotSupported(outcome = null)
            proxy.client.exchangeSymbols(ExchangeCode.US) shouldBe SourceResult.NotSupported(outcome = null)
            proxy.requests.shouldBeEmpty()
        }
    }

    context("quote") {
        test("maps the price of a US symbol") {
            val proxy = fixture("quote-aapl.json")

            proxy.client.quote(aaplListing) shouldBe SourceResult.Success(Quote(355.58, "USD"), proxy.success)
            proxy.single().shouldTarget("/symbols/AAPL/quote")
        }

        test("a London price converted to pounds by the proxy is kept") {
            val proxy = fixture("quote-shel-l.json")

            proxy.client.quote(shelListing) shouldBe SourceResult.Success(Quote(36.23576, "GBP"), proxy.success)
            proxy.single().shouldTarget("/symbols/SHEL.L/quote")
        }

        test("a US share class is requested with a dash") {
            val proxy = MockProxy(body = """{"symbol":"BRK-B","price":512.3,"currency":"USD"}""")
            val listing = ListedSymbol(Symbol.of("BRK.B"), "BERKSHIRE HATHAWAY INC-CL B", "XNYS", "USD", "Common Stock")

            proxy.client.quote(listing) shouldBe SourceResult.Success(Quote(512.3, "USD"), proxy.success)
            proxy.single().shouldTarget("/symbols/BRK-B/quote")
        }

        test("the currency of the listing is used when the proxy returns none") {
            val proxy = MockProxy(body = """{"symbol":"SAP.DE","price":193.46,"currency":null}""")

            proxy.client.quote(sapListing) shouldBe SourceResult.Success(Quote(193.46, "EUR"), proxy.success)
        }

        test("a quote without a positive price is no data") {
            MockProxy(body = """{"symbol":"SAP.DE","price":0,"currency":"EUR"}""").let { proxy ->
                proxy.client.quote(sapListing) shouldBe SourceResult.NoData(proxy.success)
            }
            MockProxy(body = """{"symbol":"SAP.DE","price":null,"currency":"EUR"}""").let { proxy ->
                proxy.client.quote(sapListing) shouldBe SourceResult.NoData(proxy.success)
            }
        }
    }

    context("ratios") {
        test("maps the key statistics of SAP.DE") {
            val proxy = fixture("statistics-sap-de.json")

            val result = proxy.client.ratios(sapDe).shouldBeInstanceOf<SourceResult.Success<*>>()

            result.outcome shouldBe proxy.success
            (result.data as Ratios).valueOf(RatioCode.ROE) shouldBe (18.178 plusOrMinus 1e-9)
            proxy.single().shouldTarget("/symbols/SAP.DE/statistics")
        }
    }

    context("dividends") {
        test("maps the full dividend history newest first") {
            val proxy = fixture("dividends-sap-de.json")

            val result = proxy.client.dividends(sapDe).shouldBeInstanceOf<SourceResult.Success<*>>()

            val dividends = (result.data as Dividends).items
            dividends.first().exDate shouldBe LocalDate.parse("2026-05-06")
            dividends.first().amount shouldBe 2.5
            dividends.all { it.currency == "EUR" } shouldBe true
            dividends.map { it.exDate } shouldBe dividends.map { it.exDate }.sortedDescending()
            proxy.single().shouldTarget("/symbols/SAP.DE/dividends")
        }

        test("a company without dividends has an empty list") {
            val proxy = MockProxy(body = """{"symbol":"BRK-B","currency":null,"dividends":[]}""")

            proxy.client.dividends(Symbol.of("BRK.B")) shouldBe
                SourceResult.Success(Dividends(emptyList()), proxy.success)
            proxy.single().shouldTarget("/symbols/BRK-B/dividends")
        }
    }

    context("company profile") {
        test("maps the profile of SAP.DE") {
            val proxy = fixture("profile-sap-de.json")

            val profile = proxy.client.companyProfile(sapDe).shouldBeInstanceOf<SourceResult.Success<CompanyProfile>>().data

            profile.name shouldBe "SAP SE"
            profile.country shouldBe "DE"
            profile.securityType shouldBe "Common Stock"
            proxy.single().shouldTarget("/symbols/SAP.DE/profile")
        }
    }

    context("balance sheet") {
        test("maps the annual balance sheets of SHEL.L in the currency of the financial statements") {
            val proxy = fixture("balance-sheet-shel-l.json")

            val result = proxy.client.balanceSheet(shelL).shouldBeInstanceOf<SourceResult.Success<*>>()

            val periods = (result.data as BalanceSheet).periods
            periods shouldHaveSize 5
            periods.first().periodEnd shouldBe LocalDate.parse("2025-12-31")
            periods.first().items.all { it.unit == "USD" } shouldBe true
            proxy.single().shouldTarget("/symbols/SHEL.L/balance-sheet")
        }
    }

    context("proxy errors") {
        val notFound = """{"error":"NOT_FOUND","message":"Yahoo has no data for the symbol"}"""

        test("404 is a successful call without data") {
            val proxy = MockProxy(HttpStatusCode.NotFound, notFound)

            proxy.client.quote(sapListing) shouldBe SourceResult.NoData(proxy.success)
            proxy.client.ratios(sapDe) shouldBe SourceResult.NoData(proxy.success)
            proxy.client.dividends(sapDe) shouldBe SourceResult.NoData(proxy.success)
            proxy.client.companyProfile(sapDe) shouldBe SourceResult.NoData(proxy.success)
            proxy.client.balanceSheet(sapDe) shouldBe SourceResult.NoData(proxy.success)
        }

        withData(
            nameFn = { "status $it is a source error with the status code" },
            HttpStatusCode.BadRequest,
            HttpStatusCode.TooManyRequests,
            HttpStatusCode.BadGateway,
            HttpStatusCode.GatewayTimeout,
            HttpStatusCode.Forbidden,
            HttpStatusCode.ServiceUnavailable,
        ) { status ->
            val proxy = MockProxy(status, """{"error":"X","message":"x"}""")

            proxy.client.dividends(sapDe) shouldBe
                SourceResult.SourceError(SourceCallOutcome.httpError(status.value, proxy.clock.now()))
        }

        test("no answer within the timeout is a timeout") {
            val proxy = MockProxy(
                handler = {
                    delay(5.seconds)
                    respond("{}", HttpStatusCode.OK, jsonHeaders)
                },
            )

            proxy.client.dividends(sapDe) shouldBe SourceResult.SourceError(SourceCallOutcome.timeout(proxy.clock.now()))
        }

        test("a body of an unexpected shape is an application error") {
            val proxy = MockProxy(body = """{"symbol":"SAP.DE","dividends":"none"}""")

            proxy.client.dividends(sapDe) shouldBe SourceResult.SourceError(SourceCallOutcome.appError(proxy.clock.now()))
        }
    }

    context("authentication") {
        test("the token of the provider is sent as a bearer token with every call") {
            val proxy = MockProxy(body = Fixtures.text("yahoo/quote-aapl.json"), bearerToken = { "id-token" })

            proxy.client.quote(aaplListing).shouldBeInstanceOf<SourceResult.Success<Quote>>()

            proxy.single().headers[HttpHeaders.Authorization] shouldBe "Bearer id-token"
        }

        test("without a provider no token is sent") {
            val proxy = fixture("quote-aapl.json")

            proxy.client.quote(aaplListing)

            proxy.single().headers[HttpHeaders.Authorization] shouldBe null
        }

        test("a token request without an answer is a timeout without a proxy call") {
            val proxy = MockProxy(bearerToken = { throw IOException("metadata server unreachable") })

            proxy.client.quote(aaplListing) shouldBe SourceResult.SourceError(SourceCallOutcome.timeout(proxy.clock.now()))
            proxy.requests.shouldBeEmpty()
        }

        test("any other token failure is an application error without a proxy call") {
            val proxy = MockProxy(bearerToken = { error("metadata server answered 500") })

            proxy.client.quote(aaplListing) shouldBe SourceResult.SourceError(SourceCallOutcome.appError(proxy.clock.now()))
            proxy.requests.shouldBeEmpty()
        }
    }
})
