package com.sixteenscreener.app.data.remote

import com.sixteenscreener.app.data.remote.auth.AuthenticationException
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.BalanceSheetItem
import com.sixteenscreener.app.domain.model.CallResult
import com.sixteenscreener.app.domain.model.Capability
import com.sixteenscreener.app.domain.model.CompanySummary
import com.sixteenscreener.app.domain.model.DataStatus
import com.sixteenscreener.app.domain.model.FiscalPeriod
import com.sixteenscreener.app.domain.model.LimitInterval
import com.sixteenscreener.app.domain.model.LimitUnit
import com.sixteenscreener.app.domain.model.Market
import com.sixteenscreener.app.domain.model.ProblemDetails
import com.sixteenscreener.app.domain.model.Profile
import com.sixteenscreener.app.domain.model.ProfileId
import com.sixteenscreener.app.domain.model.Quote
import com.sixteenscreener.app.domain.model.Ratio
import com.sixteenscreener.app.domain.model.RequestLimit
import com.sixteenscreener.app.domain.model.SearchStatus
import com.sixteenscreener.app.domain.model.SourceCallOutcome
import com.sixteenscreener.app.domain.model.SourceDataMeta
import com.sixteenscreener.app.domain.model.SourceId
import com.sixteenscreener.app.domain.model.Symbol
import com.sixteenscreener.app.domain.model.Trend
import com.sixteenscreener.app.testing.Fixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class OkHttpApiClientTest {

    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true }
    private val profileId = ProfileId("3f2b8c1e-6d4a-4f7e-9b1a-2c5d8e7f9a10")
    private lateinit var client: ApiClient

    @Before
    fun setUp() {
        server.start()
        client = clientWith(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun clientWith(httpClient: OkHttpClient) = OkHttpApiClient(
        httpClient = httpClient,
        backendUrl = server.url("/").toString().trimEnd('/'),
        json = json,
        ioDispatcher = Dispatchers.IO,
    )

    private fun enqueue(code: Int = 200, body: String = "", contentType: String = "application/json") {
        server.enqueue(MockResponse.Builder().code(code).body(body).setHeader("Content-Type", contentType).build())
    }

    private fun enqueueFixture(name: String, code: Int = 200) = enqueue(code, Fixtures.read(name))

    private fun enqueueProblem(name: String, code: Int) = enqueue(code, Fixtures.read(name), "application/problem+json")

    private fun RecordedRequest.pathAndQuery(): String = url.encodedPath + (url.encodedQuery?.let { "?$it" } ?: "")

    private fun <T> ApiResult<T>.value(): T = when (this) {
        is ApiResult.Success -> value
        is ApiResult.Failure -> throw AssertionError("Expected success but was $error")
    }

    private fun ApiResult<*>.error(): ApiError = when (this) {
        is ApiResult.Success -> throw AssertionError("Expected failure but was $value")
        is ApiResult.Failure -> error
    }

    private val alice = Profile(profileId, "Alice", SourceId.FINNHUB)

    @Test
    fun `getOrCreateProfile posts the name as JSON and returns an existing profile`() = runTest {
        enqueueFixture("profile.json", code = 200)

        val result = client.getOrCreateProfile("Alice")

        assertEquals(alice, result.value())
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/profiles", request.pathAndQuery())
        assertEquals("""{"name":"Alice"}""", request.body!!.utf8())
        assertTrue(request.headers["Content-Type"]!!.startsWith("application/json"))
    }

    @Test
    fun `getOrCreateProfile returns a created profile`() = runTest {
        enqueueFixture("profile.json", code = 201)

        assertEquals(alice, client.getOrCreateProfile("Alice").value())
    }

    @Test
    fun `getOrCreateProfile sends Unicode names unchanged`() = runTest {
        enqueueFixture("profile.json", code = 201)

        client.getOrCreateProfile("Zoë 中文")

        assertEquals("{\"name\":\"Zoë 中文\"}", server.takeRequest().body!!.utf8())
    }

    @Test
    fun `400 maps to HTTP error with problem details`() = runTest {
        enqueueProblem("problem_400.json", code = 400)

        val error = client.getOrCreateProfile(" ").error()

        assertEquals(
            ApiError.Http(400, ProblemDetails("about:blank", "Bad Request", 400, "Profile name must not be blank")),
            error,
        )
    }

    @Test
    fun `getProfile requests the profile by ID`() = runTest {
        enqueueFixture("profile.json")

        assertEquals(alice, client.getProfile(profileId).value())

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/profiles/${profileId.value}", request.pathAndQuery())
    }

    @Test
    fun `404 maps to not found with problem details`() = runTest {
        enqueueProblem("problem_404.json", code = 404)

        val error = client.getProfile(profileId).error()

        assertEquals(
            ApiError.NotFound(ProblemDetails(null, "Not Found", 404, "Profile ${profileId.value} not found")),
            error,
        )
    }

    @Test
    fun `getWatchlist parses companies with nullable fields absent or null and ignores beta fields`() = runTest {
        enqueueFixture("watchlist.json")

        val items = client.getWatchlist(profileId).value()

        assertEquals("/profiles/${profileId.value}/watchlist", server.takeRequest().pathAndQuery())
        assertEquals(
            listOf(
                CompanySummary(
                    symbol = Symbol("AAPL"),
                    ticker = "AAPL",
                    exchange = "NASDAQ",
                    name = "Apple Inc",
                    market = Market.US,
                    sector = "Technology",
                    logoUrl = "/logos/AAPL",
                    quote = Quote(
                        price = 228.52,
                        currency = "USD",
                        fetchedAt = Instant.parse("2026-11-14T15:42:07.123Z"),
                        previousCachedPrice = 226.1,
                        trend = Trend.UP,
                        source = SourceId.FINNHUB,
                    ),
                    inWatchlist = true,
                ),
                CompanySummary(
                    symbol = Symbol("SAP.DE"),
                    ticker = "SAP",
                    exchange = "XETRA",
                    name = "SAP SE",
                    market = Market.EU,
                    sector = "Technology",
                    logoUrl = "/logos/SAP.DE",
                    quote = Quote(241.35, "EUR", Instant.parse("2026-11-12T09:15:00Z"), 243.0, Trend.DOWN, SourceId.FINNHUB),
                    inWatchlist = true,
                ),
                CompanySummary(
                    symbol = Symbol("7203.T"),
                    ticker = "7203",
                    exchange = "TSE",
                    name = "Toyota Motor Corp",
                    market = null,
                    sector = null,
                    logoUrl = "/logos/7203.T",
                    quote = null,
                    inWatchlist = true,
                ),
                CompanySummary(
                    symbol = Symbol("NESN.SW"),
                    ticker = "NESN",
                    exchange = "SIX",
                    name = "Nestle SA",
                    market = Market.EU,
                    sector = null,
                    logoUrl = "/logos/NESN.SW",
                    quote = Quote(76.12, "CHF", Instant.parse("2026-11-14T08:00:00Z"), null, Trend.UNCHANGED, null),
                    inWatchlist = false,
                ),
            ),
            items,
        )
    }

    @Test
    fun `empty watchlist is parsed`() = runTest {
        enqueue(body = """{"items":[]}""")

        assertEquals(emptyList<CompanySummary>(), client.getWatchlist(profileId).value())
    }

    @Test
    fun `addToWatchlist sends PUT and accepts 204`() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())

        assertEquals(Unit, client.addToWatchlist(profileId, Symbol("SAP.DE")).value())

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/profiles/${profileId.value}/watchlist/SAP.DE", request.pathAndQuery())
    }

    @Test
    fun `removeFromWatchlist sends DELETE and accepts 204`() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())

        assertEquals(Unit, client.removeFromWatchlist(profileId, Symbol("SAP.DE")).value())

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/profiles/${profileId.value}/watchlist/SAP.DE", request.pathAndQuery())
    }

    @Test
    fun `addToWatchlist failure carries the problem detail`() = runTest {
        enqueue(400, """{"title":"Bad Request","status":400,"detail":"Watchlist is full (100 companies)"}""", "application/problem+json")

        val error = client.addToWatchlist(profileId, Symbol("AAPL")).error()

        assertEquals(ApiError.Http(400, ProblemDetails(null, "Bad Request", 400, "Watchlist is full (100 companies)")), error)
    }

    @Test
    fun `symbol is URL encoded as one path segment`() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())

        client.removeFromWatchlist(profileId, Symbol("BRK/B ?#"))

        assertEquals("/profiles/${profileId.value}/watchlist/BRK%2FB%20%3F%23", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `search sends the URL encoded query`() = runTest {
        enqueueFixture("search_ok.json")

        client.search(profileId, "procter & gamble+co")

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/profiles/${profileId.value}/search", request.url.encodedPath)
        assertEquals("procter & gamble+co", request.url.queryParameter("query"))
        assertEquals("query=procter%20%26%20gamble%2Bco", request.url.encodedQuery)
    }

    @Test
    fun `search parses status and results with and without cached quotes`() = runTest {
        enqueueFixture("search_ok.json")

        val results = client.search(profileId, "she").value()

        assertEquals(SourceId.FINNHUB, results.source)
        assertEquals(SearchStatus.OK, results.status)
        assertEquals(listOf(Symbol("SHEL.L"), Symbol("SHW")), results.items.map { it.symbol })
        assertNull(results.items[0].quote)
        assertEquals(false, results.items[0].inWatchlist)
        assertEquals(Quote(352.4, "USD", Instant.parse("2026-11-14T14:00:00Z"), null, Trend.UNCHANGED, SourceId.FINNHUB), results.items[1].quote)
        assertEquals(true, results.items[1].inWatchlist)
    }

    @Test
    fun `search with limit reached has empty items`() = runTest {
        enqueueFixture("search_limit_reached.json")

        val results = client.search(profileId, "she").value()

        assertEquals(SearchStatus.LIMIT_REACHED, results.status)
        assertTrue(results.items.isEmpty())
    }

    @Test
    fun `getPriceAndRatios requests the default source without refresh`() = runTest {
        enqueueFixture("price_ratios.json")

        client.getPriceAndRatios(profileId, Symbol("AAPL"))

        assertEquals("/profiles/${profileId.value}/companies/AAPL/price-ratios?refresh=false", server.takeRequest().pathAndQuery())
    }

    @Test
    fun `getPriceAndRatios passes source and refresh`() = runTest {
        enqueueFixture("price_ratios.json")

        client.getPriceAndRatios(profileId, Symbol("AAPL"), source = SourceId.FINNHUB, refresh = true)

        assertEquals(
            "/profiles/${profileId.value}/companies/AAPL/price-ratios?source=finnhub&refresh=true",
            server.takeRequest().pathAndQuery(),
        )
    }

    @Test
    fun `getPriceAndRatios parses meta, quote and ratios with absent and null values`() = runTest {
        enqueueFixture("price_ratios.json")

        val data = client.getPriceAndRatios(profileId, Symbol("AAPL")).value()

        assertEquals(SourceDataMeta(SourceId.FINNHUB, DataStatus.FRESH, Instant.parse("2026-11-14T15:42:07Z")), data.meta)
        assertEquals(Trend.UP, data.quote?.trend)
        assertEquals(
            listOf(
                Ratio("PE", "P/E", 34.71),
                Ratio("PB", "P/B", 52.3),
                Ratio("DIVIDEND_YIELD", "Dividend yield", null),
                Ratio("BETA", "Beta", null),
            ),
            data.ratios,
        )
    }

    @Test
    fun `getPriceAndRatios parses empty data with limit reached`() = runTest {
        enqueueFixture("price_ratios_limit_reached.json")

        val data = client.getPriceAndRatios(profileId, Symbol("AAPL")).value()

        assertEquals(SourceDataMeta(SourceId.FINNHUB, DataStatus.LIMIT_REACHED, null), data.meta)
        assertNull(data.quote)
        assertTrue(data.ratios.isEmpty())
    }

    @Test
    fun `getBalanceSheet requests SEC EDGAR and parses periods`() = runTest {
        enqueueFixture("balance_sheet.json")

        val data = client.getBalanceSheet(profileId, Symbol("AAPL"), source = SourceId.SEC_EDGAR).value()

        assertEquals(
            "/profiles/${profileId.value}/companies/AAPL/balance-sheet?source=sec-edgar&refresh=false",
            server.takeRequest().pathAndQuery(),
        )
        assertEquals(SourceDataMeta(SourceId.SEC_EDGAR, DataStatus.CACHED, Instant.parse("2026-11-13T07:30:00Z")), data.meta)
        assertEquals(2, data.periods.size)
        val newest = data.periods[0]
        assertEquals(LocalDate.of(2026, 9, 27), newest.periodEnd)
        assertEquals(2026, newest.fiscalYear)
        assertEquals(FiscalPeriod.FY, newest.fiscalPeriod)
        assertEquals(LocalDate.of(2026, 10, 31), newest.filedAt)
        assertEquals("10-K", newest.form)
        assertEquals(BalanceSheetItem("AssetsCurrent", "Total current assets", 152_987_000_000.0, "USD"), newest.items[0])
        val older = data.periods[1]
        assertNull(older.filedAt)
        assertNull(older.form)
        assertEquals(BalanceSheetItem("Assets", "Total assets", 352_583_000_000.0, null), older.items[1])
    }

    @Test
    fun `getBalanceSheet parses a response without periods`() = runTest {
        enqueue(body = """{"meta":{"source":"sec-edgar","status":"NOT_SUPPORTED","fetchedAt":null},"periods":[]}""")

        val data = client.getBalanceSheet(profileId, Symbol("SAP.DE"), source = SourceId.SEC_EDGAR).value()

        assertEquals(DataStatus.NOT_SUPPORTED, data.meta.status)
        assertTrue(data.periods.isEmpty())
    }

    @Test
    fun `getCompanyProfile parses all profile fields and ignores beta fields`() = runTest {
        enqueueFixture("company_profile.json")

        val data = client.getCompanyProfile(profileId, Symbol("AAPL"), refresh = true).value()

        assertEquals("/profiles/${profileId.value}/companies/AAPL/profile?refresh=true", server.takeRequest().pathAndQuery())
        val profile = data.profile!!
        assertEquals(DataStatus.CACHED, data.meta.status)
        assertEquals(Market.US, profile.market)
        assertEquals("US", profile.country)
        assertEquals("USD", profile.currency)
        assertEquals("Technology", profile.sector)
        assertEquals("Consumer Electronics", profile.industry)
        assertEquals(LocalDate.of(1980, 12, 12), profile.ipoDate)
        assertEquals(3452180.5, profile.marketCapitalization!!, 0.0)
        assertEquals(15115.82, profile.sharesOutstanding!!, 0.0)
        assertEquals(164000L, profile.employees)
        assertEquals("https://www.apple.com/", profile.website)
        assertTrue(profile.description!!.startsWith("Apple Inc."))
    }

    @Test
    fun `getCompanyProfile parses null profile`() = runTest {
        enqueueFixture("company_profile_no_data.json")

        val data = client.getCompanyProfile(profileId, Symbol("AAPL")).value()

        assertEquals(SourceDataMeta(SourceId.FINNHUB, DataStatus.NO_DATA, null), data.meta)
        assertNull(data.profile)
    }

    @Test
    fun `getCompanyProfile parses a profile with only required fields`() = runTest {
        enqueueFixture("company_profile_minimal.json")

        val data = client.getCompanyProfile(profileId, Symbol("7203.T")).value()

        assertEquals(SourceDataMeta(SourceId.FINNHUB, DataStatus.SOURCE_ERROR, null), data.meta)
        val profile = data.profile!!
        assertNull(profile.market)
        assertNull(profile.ipoDate)
        assertNull(profile.employees)
        assertNull(profile.description)
    }

    @Test
    fun `getSources parses limits and last call outcomes`() = runTest {
        enqueueFixture("sources.json")

        val sources = client.getSources(profileId).value()

        assertEquals("/profiles/${profileId.value}/sources", server.takeRequest().pathAndQuery())
        assertEquals(listOf("finnhub", "sec-edgar", "logoapi"), sources.map { it.id.value })
        val finnhub = sources[0]
        assertEquals("Finnhub", finnhub.name)
        assertTrue(finnhub.isDefault)
        assertTrue(finnhub.selectableAsDefault)
        assertEquals(
            listOf(Capability.SEARCH, Capability.QUOTE, Capability.RATIOS, Capability.BALANCE_SHEET, Capability.COMPANY_PROFILE),
            finnhub.capabilities,
        )
        assertEquals(
            listOf(RequestLimit(LimitInterval.MINUTE, LimitUnit.REQUESTS, 60, 12, Instant.parse("2026-11-14T15:43:00Z"))),
            finnhub.limits,
        )
        assertEquals(SourceCallOutcome(CallResult.HTTP_ERROR, 429, Instant.parse("2026-11-14T15:42:07Z")), finnhub.lastCall)
        val secEdgar = sources[1]
        assertNull(secEdgar.lastCall)
        assertNull(secEdgar.limits.single().resetsAt)
        val logoApi = sources[2]
        assertEquals(listOf(LimitInterval.HOUR, LimitInterval.MONTH), logoApi.limits.map { it.interval })
        assertEquals(LimitUnit.UNIQUE_SYMBOLS, logoApi.limits[1].unit)
        assertNull(logoApi.limits[0].resetsAt)
        assertEquals(SourceCallOutcome(CallResult.TIMEOUT, null, Instant.parse("2026-11-14T11:00:00Z")), logoApi.lastCall)
    }

    @Test
    fun `unknown enum values fall back instead of failing`() = runTest {
        enqueue(
            body = """
                {"source":"finnhub","status":"THROTTLED","items":[
                  {"symbol":"X","ticker":"X","exchange":"HKEX","name":"X","market":"ASIA","logoUrl":"/logos/X",
                   "quote":{"price":1.0,"currency":"HKD","fetchedAt":"2026-11-14T10:00:00Z","trend":"SIDEWAYS"}}
                ]}
            """.trimIndent(),
        )
        enqueue(
            body = """
                {"items":[{"id":"fmp","name":"FMP","capabilities":["QUOTE","NEWS"],"selectableAsDefault":true,"isDefault":false,
                  "limits":[{"interval":"WEEK","unit":"CREDITS","limit":1,"used":0}],
                  "lastCall":{"result":"REFUSED","at":"2026-11-14T10:00:00Z"}}]}
            """.trimIndent(),
        )
        enqueue(body = """{"meta":{"source":"tiingo","status":"PARTIAL"},"periods":[{"periodEnd":"2026-06-30","fiscalYear":2026,"fiscalPeriod":"H1","items":[]}]}""")

        val search = client.search(profileId, "xxx").value()
        val source = client.getSources(profileId).value().single()
        val balanceSheet = client.getBalanceSheet(profileId, Symbol("X")).value()

        assertEquals(SearchStatus.UNKNOWN, search.status)
        assertNull(search.items.single().market)
        assertEquals(Trend.UNCHANGED, search.items.single().quote!!.trend)
        assertEquals(listOf(Capability.QUOTE, Capability.UNKNOWN), source.capabilities)
        assertEquals(LimitInterval.UNKNOWN, source.limits.single().interval)
        assertEquals(LimitUnit.UNKNOWN, source.limits.single().unit)
        assertEquals(CallResult.UNKNOWN, source.lastCall!!.result)
        assertEquals(DataStatus.UNKNOWN, balanceSheet.meta.status)
        assertEquals(FiscalPeriod.UNKNOWN, balanceSheet.periods.single().fiscalPeriod)
    }

    @Test
    fun `malformed JSON maps to parse error`() = runTest {
        enqueue(body = """{"items":[{"symbol":""")

        assertTrue(client.getWatchlist(profileId).error() is ApiError.Parse)
    }

    @Test
    fun `missing required field maps to parse error`() = runTest {
        enqueue(body = """{"id":"${profileId.value}","name":"Alice"}""")

        assertTrue(client.getProfile(profileId).error() is ApiError.Parse)
    }

    @Test
    fun `wrong field type maps to parse error`() = runTest {
        enqueue(body = """{"meta":{"source":"finnhub","status":"FRESH"},"ratios":[{"code":"PE","name":"P/E","value":"high"}]}""")

        assertTrue(client.getPriceAndRatios(profileId, Symbol("AAPL")).error() is ApiError.Parse)
    }

    @Test
    fun `invalid timestamp maps to parse error`() = runTest {
        enqueue(body = """{"meta":{"source":"finnhub","status":"FRESH","fetchedAt":"yesterday"},"ratios":[]}""")

        assertTrue(client.getPriceAndRatios(profileId, Symbol("AAPL")).error() is ApiError.Parse)
    }

    @Test
    fun `empty success body where JSON is expected maps to parse error`() = runTest {
        enqueue(body = "")

        assertTrue(client.getSources(profileId).error() is ApiError.Parse)
    }

    @Test
    fun `401 and 403 map to unauthorized`() = runTest {
        enqueue(401, "")
        enqueue(403, "<html>Forbidden</html>", "text/html")

        assertEquals(ApiError.Unauthorized, client.getWatchlist(profileId).error())
        assertEquals(ApiError.Unauthorized, client.getWatchlist(profileId).error())
    }

    @Test
    fun `server error without problem body maps to HTTP error without problem`() = runTest {
        enqueue(503, "<html>Service Unavailable</html>", "text/html")
        enqueue(500, "")

        assertEquals(ApiError.Http(503, null), client.getWatchlist(profileId).error())
        assertEquals(ApiError.Http(500, null), client.getWatchlist(profileId).error())
    }

    @Test
    fun `404 without body maps to not found without problem`() = runTest {
        enqueue(404, "")

        assertEquals(ApiError.NotFound(null), client.getProfile(profileId).error())
    }

    @Test
    fun `ID token failure maps to unauthorized`() = runTest {
        val failingAuth = OkHttpClient.Builder()
            .addInterceptor { throw AuthenticationException("Token endpoint returned HTTP 400") }
            .build()

        assertEquals(ApiError.Unauthorized, clientWith(failingAuth).getWatchlist(profileId).error())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `read timeout maps to network timeout`() = runTest {
        server.enqueue(MockResponse.Builder().body(Fixtures.read("watchlist.json")).headersDelay(2, TimeUnit.SECONDS).build())
        val impatient = OkHttpClient.Builder().readTimeout(Duration.ofMillis(200)).build()

        assertEquals(ApiError.Network(isTimeout = true), clientWith(impatient).getWatchlist(profileId).error())
    }

    @Test
    fun `unreachable server maps to network error`() = runTest {
        val unreachable = clientWith(OkHttpClient())
        server.close()

        assertEquals(ApiError.Network(isTimeout = false), unreachable.getWatchlist(profileId).error())
    }

    @Test
    fun `cancelling the coroutine cancels the call`() = runBlocking {
        server.enqueue(MockResponse.Builder().body(Fixtures.read("search_ok.json")).headersDelay(10, TimeUnit.SECONDS).build())

        val search = async(Dispatchers.Default) { client.search(profileId, "she") }
        server.takeRequest()
        withTimeout(2_000) {
            search.cancel()
            search.join()
        }

        assertTrue(search.isCancelled)
    }
}
