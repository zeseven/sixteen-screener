package com.sixteenscreener.app.data.remote

import com.sixteenscreener.app.data.remote.auth.AuthenticationException
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.BalanceSheetItem
import com.sixteenscreener.app.domain.model.CallResult
import com.sixteenscreener.app.domain.model.Capability
import com.sixteenscreener.app.domain.model.DataStatus
import com.sixteenscreener.app.domain.model.DefaultSources
import com.sixteenscreener.app.domain.model.Exchange
import com.sixteenscreener.app.domain.model.FiscalPeriod
import com.sixteenscreener.app.domain.model.Indicator
import com.sixteenscreener.app.domain.model.IndicatorIntervals
import com.sixteenscreener.app.domain.model.IndicatorLevel
import com.sixteenscreener.app.domain.model.IndicatorScale
import com.sixteenscreener.app.domain.model.IndicatorSetting
import com.sixteenscreener.app.domain.model.LimitInterval
import com.sixteenscreener.app.domain.model.LimitUnit
import com.sixteenscreener.app.domain.model.Market
import com.sixteenscreener.app.domain.model.MarketData
import com.sixteenscreener.app.domain.model.ProblemDetails
import com.sixteenscreener.app.domain.model.Profile
import com.sixteenscreener.app.domain.model.ProfileId
import com.sixteenscreener.app.domain.model.Quote
import com.sixteenscreener.app.domain.model.Ratio
import com.sixteenscreener.app.domain.model.RatioCategory
import com.sixteenscreener.app.domain.model.RatioUnit
import com.sixteenscreener.app.domain.model.RequestLimit
import com.sixteenscreener.app.domain.model.RequestTrigger
import com.sixteenscreener.app.domain.model.SearchItem
import com.sixteenscreener.app.domain.model.SearchStatus
import com.sixteenscreener.app.domain.model.SourceCallOutcome
import com.sixteenscreener.app.domain.model.SourceDataMeta
import com.sixteenscreener.app.domain.model.SourceId
import com.sixteenscreener.app.domain.model.Symbol
import com.sixteenscreener.app.domain.model.Trend
import com.sixteenscreener.app.domain.model.WatchlistItem
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
import org.junit.Assert.assertFalse
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

    private val alice = Profile(profileId, "Alice", DefaultSources(SourceId.FINNHUB, SourceId.FINNHUB, SourceId.EODHD))

    private val peSetting = IndicatorSetting(
        ratioCode = "PE",
        name = "P/E",
        shortName = "P/E",
        unit = RatioUnit.RATIO,
        category = RatioCategory.VALUATION,
        scale = IndicatorScale(max = 60.0, step = 1.0),
        enabled = true,
        intervals = IndicatorIntervals(
            thresholds = listOf(15.0, 25.0, 40.0),
            levels = listOf(IndicatorLevel.GOOD, IndicatorLevel.NEUTRAL, IndicatorLevel.WARNING, IndicatorLevel.BAD),
            belowZeroLevel = IndicatorLevel.BAD,
        ),
    )

    @Test
    fun `getIndicatorSettings parses the settings in backend order`() = runTest {
        enqueueFixture("indicator_settings.json")

        val settings = client.getIndicatorSettings(profileId).value()

        assertEquals("/profiles/${profileId.value}/indicator-settings", server.takeRequest().pathAndQuery())
        assertEquals(listOf("PE", "ROE", "FUTURE_RATIO"), settings.map { it.ratioCode })
        assertEquals(peSetting, settings[0])
        assertEquals(
            peSetting.copy(
                ratioCode = "ROE",
                name = "Return on equity",
                shortName = "ROE",
                unit = RatioUnit.PERCENT,
                category = RatioCategory.PROFITABILITY,
                scale = IndicatorScale(max = 50.0, step = 0.5),
                enabled = false,
                intervals = IndicatorIntervals(
                    thresholds = listOf(5.0, 12.5, 20.0),
                    levels = listOf(IndicatorLevel.BAD, IndicatorLevel.WARNING, IndicatorLevel.NEUTRAL, IndicatorLevel.GOOD),
                    belowZeroLevel = IndicatorLevel.BAD,
                ),
            ),
            settings[1],
        )
    }

    @Test
    fun `getIndicatorSettings maps unknown unit, category and levels without failing`() = runTest {
        enqueueFixture("indicator_settings.json")

        val future = client.getIndicatorSettings(profileId).value().single { it.ratioCode == "FUTURE_RATIO" }

        assertEquals(RatioUnit.UNKNOWN, future.unit)
        assertEquals(RatioCategory.UNKNOWN, future.category)
        assertEquals(
            listOf(IndicatorLevel.GOOD, IndicatorLevel.NEUTRAL, IndicatorLevel.NEUTRAL, IndicatorLevel.BAD),
            future.intervals.levels,
        )
        assertEquals(IndicatorLevel.NEUTRAL, future.intervals.belowZeroLevel)
    }

    @Test
    fun `getIndicatorSettings drops settings without three thresholds and four levels`() = runTest {
        enqueueFixture("indicator_settings.json")

        assertTrue(client.getIndicatorSettings(profileId).value().none { it.ratioCode == "BROKEN" })
    }

    @Test
    fun `getIndicatorSettings maps 404 to not found`() = runTest {
        enqueueProblem("problem_404.json", code = 404)

        assertTrue(client.getIndicatorSettings(profileId).error() is ApiError.NotFound)
    }

    @Test
    fun `replaceIndicatorSettings puts only the configurable values and parses the stored settings`() = runTest {
        enqueueFixture("indicator_settings.json")
        val edited = peSetting.copy(
            enabled = false,
            intervals = peSetting.intervals.copy(thresholds = listOf(12.5, 25.0, 40.0), belowZeroLevel = IndicatorLevel.UNKNOWN),
        )

        val stored = client.replaceIndicatorSettings(profileId, listOf(edited)).value()

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/profiles/${profileId.value}/indicator-settings", request.pathAndQuery())
        assertTrue(request.headers["Content-Type"]!!.startsWith("application/json"))
        assertEquals(
            """{"settings":[{"ratioCode":"PE","enabled":false,"thresholds":[12.5,25.0,40.0],""" +
                """"levels":["GOOD","NEUTRAL","WARNING","BAD"],"belowZeroLevel":"NEUTRAL"}]}""",
            request.body!!.utf8(),
        )
        assertEquals(peSetting, stored.first())
    }

    @Test
    fun `replaceIndicatorSettings maps a rejected update to an HTTP error with the problem`() = runTest {
        enqueueProblem("problem_400.json", code = 400)

        val error = client.replaceIndicatorSettings(profileId, listOf(peSetting)).error()

        assertTrue(error is ApiError.Http && error.status == 400 && error.problem != null)
    }

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
    fun `getProfile fails to parse a profile with the single default source of the old contract`() = runTest {
        enqueue(body = """{"id":"${profileId.value}","name":"Alice","defaultSource":"finnhub"}""")

        assertTrue(client.getProfile(profileId).error() is ApiError.Parse)
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
    fun `getWatchlist parses items with nullable fields absent or null and ignores beta fields`() = runTest {
        enqueueFixture("watchlist.json")

        val items = client.getWatchlist(profileId).value()

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/profiles/${profileId.value}/watchlist", request.pathAndQuery())
        assertEquals(
            listOf(
                WatchlistItem(Symbol("AAPL"), "AAPL", "NASDAQ", "Apple Inc", Market.US, "Common Stock", true, "/logos/AAPL", "Technology", listOf("core", "dividend")),
                WatchlistItem(Symbol("SAP.DE"), "SAP", "XETRA", "SAP SE", Market.EU, null, true, "/logos/SAP.DE", "Technology", emptyList()),
                WatchlistItem(Symbol("7203.T"), "7203", "TSE", "Toyota Motor Corp", null, null, true, "/logos/7203.T", null, emptyList()),
                WatchlistItem(Symbol("NESN.SW"), "NESN", "SIX", "Nestle SA", Market.EU, "ADR", false, "/logos/NESN.SW", null, emptyList()),
            ),
            items,
        )
    }

    @Test
    fun `empty watchlist is parsed`() = runTest {
        enqueue(body = """{"items":[]}""")

        assertEquals(emptyList<WatchlistItem>(), client.getWatchlist(profileId).value())
    }

    @Test
    fun `getWatchlistMarketData sends the symbols comma separated and parses the rows with every status`() = runTest {
        enqueueFixture("watchlist_market_data.json")

        val items = client.getWatchlistMarketData(profileId, listOf(Symbol("AAPL"), Symbol("SAP.DE"))).value()

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/profiles/${profileId.value}/watchlist/market-data?symbols=AAPL,SAP.DE", request.pathAndQuery())
        assertEquals(
            listOf(
                MarketData(
                    symbol = Symbol("AAPL"),
                    status = DataStatus.FRESH,
                    quote = Quote(229.1, "USD", Instant.parse("2026-11-15T10:05:00Z"), 228.52, Trend.UP, SourceId.FINNHUB),
                    indicators = listOf(Indicator("PE", "P/E", IndicatorLevel.WARNING)),
                    indicatorsStatus = DataStatus.CACHED,
                ),
                MarketData(
                    symbol = Symbol("SAP.DE"),
                    status = DataStatus.LIMIT_REACHED,
                    quote = Quote(241.35, "EUR", Instant.parse("2026-11-12T09:15:00Z"), null, Trend.DOWN, null),
                    indicators = emptyList(),
                    indicatorsStatus = DataStatus.SOURCE_ERROR,
                ),
                MarketData(Symbol("7203.T"), DataStatus.NO_DATA, null, emptyList(), DataStatus.NO_DATA),
                MarketData(Symbol("NESN.SW"), DataStatus.SOURCE_ERROR, null, emptyList(), DataStatus.UNKNOWN),
                MarketData(
                    Symbol("SHEL.L"),
                    DataStatus.NOT_SUPPORTED,
                    null,
                    listOf(Indicator("ROE", "ROE", IndicatorLevel.UNKNOWN)),
                    DataStatus.UNKNOWN,
                ),
            ),
            items,
        )
    }

    @Test
    fun `getWatchlistMarketData maps unknown status and missing indicators`() = runTest {
        enqueue(body = """{"items":[{"symbol":"AAPL","status":"STALE","quote":null}]}""")

        val items = client.getWatchlistMarketData(profileId, listOf(Symbol("AAPL"))).value()

        assertEquals(listOf(MarketData(Symbol("AAPL"), DataStatus.UNKNOWN, null, emptyList(), DataStatus.UNKNOWN)), items)
    }

    @Test
    fun `getWatchlistMarketData encodes each symbol so that separators stay unambiguous`() = runTest {
        enqueue(body = """{"items":[]}""")

        client.getWatchlistMarketData(profileId, listOf(Symbol("BRK/B"), Symbol("A,B"), Symbol("C D+&#"))).value()

        assertEquals(
            "/profiles/${profileId.value}/watchlist/market-data?symbols=BRK%2FB,A%2CB,C%20D%2B%26%23",
            server.takeRequest().pathAndQuery(),
        )
    }

    @Test
    fun `getWatchlistMarketData failure carries the problem detail`() = runTest {
        enqueueProblem("problem_400.json", code = 400)

        val error = client.getWatchlistMarketData(profileId, listOf(Symbol("AAPL"))).error()

        assertTrue(error is ApiError.Http && error.status == 400)
    }

    @Test
    fun `getWatchlistMarketData with an invalid quote time is a parse error`() = runTest {
        enqueue(body = """{"items":[{"symbol":"AAPL","status":"FRESH","quote":{"price":1.0,"currency":"USD","fetchedAt":"yesterday","trend":"UP"},"indicators":[]}]}""")

        val error = client.getWatchlistMarketData(profileId, listOf(Symbol("AAPL"))).error()

        assertTrue(error is ApiError.Parse)
    }

    @Test
    fun `addToWatchlist sends PUT and parses the stored item`() = runTest {
        enqueueFixture("watchlist_item.json")

        val item = client.addToWatchlist(profileId, Symbol("SAP.DE"), "SAP SE").value()

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/profiles/${profileId.value}/watchlist/SAP.DE", request.pathAndQuery())
        assertEquals(WatchlistItem(Symbol("SAP.DE"), "SAP", "XETRA", "SAP SE", Market.EU, null, true, "/logos/SAP.DE", "Technology", emptyList()), item)
    }

    @Test
    fun `addToWatchlist sends the company name as JSON`() = runTest {
        enqueueFixture("watchlist_item.json")

        client.addToWatchlist(profileId, Symbol("AD.AS"), "Koninklijke Ahold Delhaize NV")

        val request = server.takeRequest()
        assertEquals("""{"name":"Koninklijke Ahold Delhaize NV"}""", request.body!!.utf8())
        assertTrue(request.headers["Content-Type"]!!.startsWith("application/json"))
    }

    @Test
    fun `addToWatchlist cuts a long company name to 200 characters`() = runTest {
        enqueueFixture("watchlist_item.json")

        client.addToWatchlist(profileId, Symbol("AD.AS"), "a".repeat(199) + "bc")

        assertEquals("{\"name\":\"${"a".repeat(199)}b\"}", server.takeRequest().body!!.utf8())
    }

    @Test
    fun `addToWatchlist keeps a company name of exactly 200 characters`() = runTest {
        enqueueFixture("watchlist_item.json")

        client.addToWatchlist(profileId, Symbol("AD.AS"), "a".repeat(200))

        assertEquals("{\"name\":\"${"a".repeat(200)}\"}", server.takeRequest().body!!.utf8())
    }

    @Test
    fun `addToWatchlist with a blank company name sends no body`() = runTest {
        enqueueFixture("watchlist_item.json")
        enqueueFixture("watchlist_item.json")

        client.addToWatchlist(profileId, Symbol("SAP.DE"), "")
        client.addToWatchlist(profileId, Symbol("SAP.DE"), "  ")

        repeat(2) {
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals(0L, request.bodySize)
            assertNull(request.headers["Content-Type"])
        }
    }

    @Test
    fun `addToWatchlist parses a stored item without sector`() = runTest {
        enqueue(
            body = """{"symbol":"SAP.DE","ticker":"SAP","exchange":"XETRA","name":"SAP SE","market":"EU","logoUrl":"/logos/SAP.DE","sector":null,"tags":[]}""",
        )

        assertNull(client.addToWatchlist(profileId, Symbol("SAP.DE"), "").value().sector)
    }

    @Test
    fun `addToWatchlist without a body is a parse error`() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())

        assertTrue(client.addToWatchlist(profileId, Symbol("SAP.DE"), "").error() is ApiError.Parse)
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
        enqueue(400, """{"title":"Bad Request","status":400,"detail":"Watchlist is full (250 companies)"}""", "application/problem+json")

        val error = client.addToWatchlist(profileId, Symbol("AAPL"), "").error()

        assertEquals(ApiError.Http(400, ProblemDetails(null, "Bad Request", 400, "Watchlist is full (250 companies)")), error)
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
    fun `search parses status and directory data of the results`() = runTest {
        enqueueFixture("search_ok.json")

        val results = client.search(profileId, "she").value()

        assertEquals(SourceId.FINNHUB, results.source)
        assertEquals(SearchStatus.OK, results.status)
        assertEquals(
            listOf(
                SearchItem(Symbol("SHEL.L"), "SHEL", "LSE", "Shell PLC", Market.EU, "ADR", false, "/logos/SHEL.L", inWatchlist = false),
                SearchItem(Symbol("SHW"), "SHW", "NYSE", "Sherwin-Williams Co", null, "Common Stock", true, "/logos/SHW", inWatchlist = true),
            ),
            results.items,
        )
    }

    @Test
    fun `search result without inWatchlist is not in the watchlist`() = runTest {
        enqueue(
            body = """{"source":"finnhub","status":"OK","items":[{"symbol":"F","ticker":"F","exchange":"NYSE","name":"Ford","logoUrl":"/logos/F"}]}""",
        )

        val item = client.search(profileId, "F").value().items.single()

        assertEquals(false, item.inWatchlist)
        assertNull(item.market)
        assertNull(item.type)
        assertTrue(item.stock)
    }

    @Test
    fun `search with limit reached has empty items`() = runTest {
        enqueueFixture("search_limit_reached.json")

        val results = client.search(profileId, "she").value()

        assertEquals(SearchStatus.LIMIT_REACHED, results.status)
        assertTrue(results.items.isEmpty())
    }

    @Test
    fun `getPriceAndRatios requests the default source with the automatic trigger`() = runTest {
        enqueueFixture("price_ratios.json")

        client.getPriceAndRatios(profileId, Symbol("AAPL"))

        assertEquals("/profiles/${profileId.value}/companies/AAPL/price-ratios?trigger=AUTO", server.takeRequest().pathAndQuery())
    }

    @Test
    fun `getPriceAndRatios passes source and the manual trigger`() = runTest {
        enqueueFixture("price_ratios.json")

        client.getPriceAndRatios(profileId, Symbol("AAPL"), source = SourceId.FINNHUB, trigger = RequestTrigger.MANUAL)

        assertEquals(
            "/profiles/${profileId.value}/companies/AAPL/price-ratios?source=finnhub&trigger=MANUAL",
            server.takeRequest().pathAndQuery(),
        )
    }

    @Test
    fun `getBalanceSheet sends the manual trigger`() = runTest {
        enqueueFixture("balance_sheet.json")

        client.getBalanceSheet(profileId, Symbol("AAPL"), trigger = RequestTrigger.MANUAL)

        assertEquals("/profiles/${profileId.value}/companies/AAPL/balance-sheet?trigger=MANUAL", server.takeRequest().pathAndQuery())
    }

    @Test
    fun `getPriceAndRatios parses meta, quote and ratios with absent and null values`() = runTest {
        enqueueFixture("price_ratios.json")

        val data = client.getPriceAndRatios(profileId, Symbol("AAPL")).value()

        assertEquals(SourceDataMeta(SourceId.FINNHUB, DataStatus.FRESH, Instant.parse("2026-11-14T15:42:07Z")), data.meta)
        assertEquals(Trend.UP, data.quote?.trend)
        assertEquals(
            listOf(
                Ratio("PE", "P/E", RatioUnit.RATIO, 34.71),
                Ratio("PB", "P/B", RatioUnit.RATIO, 52.3),
                Ratio("DIVIDEND_YIELD", "Dividend yield", RatioUnit.PERCENT, null),
                Ratio("EPS", "EPS", RatioUnit.CURRENCY, 6.42),
                Ratio("BETA", "Beta", RatioUnit.UNKNOWN, null),
                Ratio("FUTURE_RATIO", "Future ratio", RatioUnit.UNKNOWN, 1.5),
            ),
            data.ratios,
        )
        assertEquals(
            listOf(Indicator("PE", "P/E", IndicatorLevel.BAD), Indicator("PB", "P/B", IndicatorLevel.UNKNOWN)),
            data.indicators,
        )
    }

    @Test
    fun `getPriceAndRatios parses empty data with limit reached`() = runTest {
        enqueueFixture("price_ratios_limit_reached.json")

        val data = client.getPriceAndRatios(profileId, Symbol("AAPL")).value()

        assertEquals(SourceDataMeta(SourceId.FINNHUB, DataStatus.LIMIT_REACHED, null), data.meta)
        assertNull(data.quote)
        assertTrue(data.ratios.isEmpty())
        assertTrue(data.indicators.isEmpty())
    }

    @Test
    fun `getBalanceSheet requests SEC EDGAR and parses periods`() = runTest {
        enqueueFixture("balance_sheet.json")

        val data = client.getBalanceSheet(profileId, Symbol("AAPL"), source = SourceId.SEC_EDGAR).value()

        assertEquals(
            "/profiles/${profileId.value}/companies/AAPL/balance-sheet?source=sec-edgar&trigger=AUTO",
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

        val data = client.getCompanyProfile(profileId, Symbol("AAPL"), trigger = RequestTrigger.MANUAL).value()

        assertEquals("/profiles/${profileId.value}/companies/AAPL/profile?trigger=MANUAL", server.takeRequest().pathAndQuery())
        val profile = data.profile!!
        assertEquals(DataStatus.CACHED, data.meta.status)
        assertEquals(Market.US, profile.market)
        assertEquals("Common Stock", profile.type)
        assertTrue(profile.stock)
        assertTrue(profile.homeExchange)
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
        assertNull(profile.type)
        assertTrue(profile.stock)
        assertFalse(profile.homeExchange)
        assertNull(profile.ipoDate)
        assertNull(profile.employees)
        assertNull(profile.description)
    }

    @Test
    fun `getSources parses limits, last call outcomes and total calls`() = runTest {
        enqueueFixture("sources.json")

        val sources = client.getSources(profileId).value()

        assertEquals("/profiles/${profileId.value}/sources", server.takeRequest().pathAndQuery())
        assertEquals(listOf("finnhub", "sec-edgar", "logoapi", "finnhub-logos"), sources.map { it.id.value })
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
        assertNull(finnhub.totalCalls)
        assertNull(logoApi.totalCalls)
        val finnhubLogos = sources[3]
        assertEquals("Finnhub logos", finnhubLogos.name)
        assertEquals(emptyList<RequestLimit>(), finnhubLogos.limits)
        assertEquals(SourceCallOutcome(CallResult.SUCCESS, null, Instant.parse("2026-11-14T11:05:00Z")), finnhubLogos.lastCall)
        assertEquals(5_000_000_000L, finnhubLogos.totalCalls)
    }

    @Test
    fun `getExchanges requests the exchanges without a profile and keeps the backend order`() = runTest {
        enqueueFixture("exchanges.json")

        val exchanges = client.getExchanges().value()

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/exchanges", request.pathAndQuery())
        assertEquals(
            listOf(
                Exchange("", "NYSE", "New York Stock Exchange", "XNYS", "US", true, "USD", "America/New_York", Market.US),
                Exchange("DE", "XETRA", "Deutsche Boerse Xetra", "XETR", "DE", true, "EUR", "Europe/Berlin", Market.EU),
                Exchange("V", "TSXV", "TSX Venture Exchange", "XTSX", "CA", false, "CAD", "America/Toronto", null),
            ),
            exchanges,
        )
    }

    @Test
    fun `getExchanges maps an unknown market to no market`() = runTest {
        enqueue(
            body = """
                {"items":[{"suffix":"HK","shortName":"HKEX","fullName":"Hong Kong Exchanges","mic":"XHKG","currency":"HKD",
                  "timeZone":"Asia/Hong_Kong","market":"ASIA"}]}
            """.trimIndent(),
        )

        assertNull(client.getExchanges().value().single().market)
    }

    @Test
    fun `getExchanges defaults country and main venue when absent`() = runTest {
        enqueue(
            body = """
                {"items":[{"suffix":"HK","shortName":"HKEX","fullName":"Hong Kong Exchanges","mic":"XHKG","currency":"HKD",
                  "timeZone":"Asia/Hong_Kong","market":null}]}
            """.trimIndent(),
        )

        val exchange = client.getExchanges().value().single()

        assertEquals("", exchange.country)
        assertFalse(exchange.mainVenue)
    }

    @Test
    fun `getExchanges without a required field is a parse error`() = runTest {
        enqueue(body = """{"items":[{"suffix":"V","shortName":"TSXV"}]}""")

        assertTrue(client.getExchanges().error() is ApiError.Parse)
    }

    @Test
    fun `getExchanges failure carries the HTTP status`() = runTest {
        enqueue(code = 503)

        assertEquals(ApiError.Http(503, null), client.getExchanges().error())
    }

    @Test
    fun `unknown enum values fall back instead of failing`() = runTest {
        enqueue(
            body = """
                {"source":"finnhub","status":"THROTTLED","items":[
                  {"symbol":"X","ticker":"X","exchange":"HKEX","name":"X","market":"ASIA","logoUrl":"/logos/X"}
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
        enqueue(
            body = """
                {"items":[{"symbol":"X","status":"FRESH","indicators":[],
                  "quote":{"price":1.0,"currency":"HKD","fetchedAt":"2026-11-14T10:00:00Z","trend":"SIDEWAYS"}}]}
            """.trimIndent(),
        )

        val search = client.search(profileId, "xxx").value()
        val source = client.getSources(profileId).value().single()
        val balanceSheet = client.getBalanceSheet(profileId, Symbol("X")).value()
        val marketData = client.getWatchlistMarketData(profileId, listOf(Symbol("X"))).value().single()

        assertEquals(SearchStatus.UNKNOWN, search.status)
        assertNull(search.items.single().market)
        assertEquals(Trend.UNCHANGED, marketData.quote!!.trend)
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
