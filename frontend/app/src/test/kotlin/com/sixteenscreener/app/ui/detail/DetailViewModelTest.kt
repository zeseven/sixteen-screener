package com.sixteenscreener.app.ui.detail

import com.sixteenscreener.app.data.repository.DefaultCompanyRepository
import com.sixteenscreener.app.data.repository.DefaultSourceRepository
import com.sixteenscreener.app.data.repository.DefaultTagRepository
import com.sixteenscreener.app.data.repository.DefaultWatchlistRepository
import com.sixteenscreener.app.domain.model.ActiveProfile
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.BalanceSheet
import com.sixteenscreener.app.domain.model.Capability
import com.sixteenscreener.app.domain.model.CompanyProfileData
import com.sixteenscreener.app.domain.model.DataStatus
import com.sixteenscreener.app.domain.model.PriceAndRatios
import com.sixteenscreener.app.domain.model.ProfileId
import com.sixteenscreener.app.domain.model.RequestTrigger
import com.sixteenscreener.app.domain.model.SourceId
import com.sixteenscreener.app.domain.model.Symbol
import com.sixteenscreener.app.domain.model.Tag
import com.sixteenscreener.app.domain.model.WatchlistItem
import com.sixteenscreener.app.testing.Companies
import com.sixteenscreener.app.testing.CompanyData
import com.sixteenscreener.app.testing.CompanyData.item
import com.sixteenscreener.app.testing.CompanyData.period
import com.sixteenscreener.app.testing.FakeApiClient
import com.sixteenscreener.app.testing.FakeProfileRepository
import com.sixteenscreener.app.testing.FakeWatchlistStore
import com.sixteenscreener.app.testing.MainDispatcherRule
import com.sixteenscreener.app.testing.Sources.source
import com.sixteenscreener.app.ui.navigation.DetailRoute
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val apiClient = FakeApiClient()
    private val profileRepository = FakeProfileRepository(ActiveProfile(ProfileId("p1"), "Viktor"))
    private val apple = Companies.identity("AAPL", name = "Apple Inc.")

    private val finnhub = source("finnhub", "Finnhub", isDefault = true)
    private val secEdgar = source("sec-edgar", "SEC EDGAR", capabilities = listOf(Capability.BALANCE_SHEET))

    private val finnhubSheet = CompanyData.balanceSheet(period(2026, item("Assets", 1.0)), source = SourceId.FINNHUB)
    private val secEdgarSheet = CompanyData.balanceSheet(period(2026, item("Assets", 2.0)), source = SourceId.SEC_EDGAR)

    private lateinit var watchlistRepository: DefaultWatchlistRepository

    init {
        apiClient.getPriceAndRatios = { _, _, _, _ -> ApiResult.Success(CompanyData.priceAndRatios()) }
        apiClient.getCompanyProfile = { _, _, _, _ -> ApiResult.Success(CompanyData.companyProfileData()) }
        apiClient.getDividends = { _, _, _, _ -> ApiResult.Success(CompanyData.dividends()) }
        apiClient.getSources = { ApiResult.Success(listOf(finnhub, secEdgar)) }
        apiClient.getBalanceSheet = { _, _, source, _ -> ApiResult.Success(if (source == SourceId.SEC_EDGAR) secEdgarSheet else finnhubSheet) }
    }

    private fun TestScope.viewModel(route: DetailRoute = DetailRoute(apple.symbol, apple)): DetailViewModel {
        watchlistRepository = DefaultWatchlistRepository(apiClient, profileRepository, FakeWatchlistStore(), backgroundScope)
        return DetailViewModel(
            route = route,
            companyRepository = DefaultCompanyRepository(apiClient, profileRepository),
            sourceRepository = DefaultSourceRepository(apiClient, profileRepository, backgroundScope),
            watchlistRepository = watchlistRepository,
            tagRepository = DefaultTagRepository(apiClient, profileRepository, watchlistRepository),
        )
    }

    private val DetailViewModel.state get() = uiState.value

    private fun callsOf(prefix: String) = apiClient.calls.filter { it.startsWith(prefix) }

    @Test
    fun `price and ratios and the company profile load when the screen opens`() = runTest {
        val viewModel = viewModel()

        assertEquals(listOf("getPriceAndRatios(p1, AAPL, null, AUTO)", "getCompanyProfile(p1, AAPL, null, AUTO)"), apiClient.calls)
        assertEquals(CompanyData.priceAndRatios(), viewModel.state.priceAndRatios.value)
        assertFalse(viewModel.state.priceAndRatios.isLoading)
    }

    @Test
    fun `each tab loads once when first shown`() = runTest {
        val viewModel = viewModel()

        DetailTab.entries.forEach(viewModel::onTabSelected)
        DetailTab.entries.forEach(viewModel::onTabSelected)

        assertEquals(listOf("getPriceAndRatios(p1, AAPL, null, AUTO)"), callsOf("getPriceAndRatios"))
        assertEquals(listOf("getDividends(p1, AAPL, null, AUTO)"), callsOf("getDividends"))
        assertEquals(listOf("getBalanceSheet(p1, AAPL, null, AUTO)"), callsOf("getBalanceSheet"))
        assertEquals(listOf("getCompanyProfile(p1, AAPL, null, AUTO)"), callsOf("getCompanyProfile"))
        assertEquals(DetailTab.COMPANY_PROFILE, viewModel.state.selectedTab)
    }

    @Test
    fun `dividends are not requested before the dividends tab is shown`() = runTest {
        val viewModel = viewModel()

        assertTrue(callsOf("getDividends").isEmpty())

        viewModel.onTabSelected(DetailTab.DIVIDENDS)

        assertEquals(listOf("getDividends(p1, AAPL, null, AUTO)"), callsOf("getDividends"))
        assertEquals(CompanyData.dividends(), viewModel.state.dividends.value)
        assertFalse(viewModel.state.dividends.isLoading)
    }

    @Test
    fun `failed dividends request keeps the shown dividends with the error`() = runTest {
        val viewModel = viewModel()
        viewModel.onTabSelected(DetailTab.DIVIDENDS)
        apiClient.getDividends = { _, _, _, _ -> ApiResult.Failure(ApiError.Network(isTimeout = true)) }

        viewModel.refresh()

        assertEquals(CompanyData.dividends(), viewModel.state.dividends.value)
        assertEquals(ApiError.Network(isTimeout = true), viewModel.state.dividends.error)
    }

    @Test
    fun `not requested dividends are kept as loaded data`() = runTest {
        val notRequested = CompanyData.dividends(dividendYield = null, status = DataStatus.NOT_REQUESTED)
        apiClient.getDividends = { _, _, _, _ -> ApiResult.Success(notRequested) }
        val viewModel = viewModel()

        viewModel.onTabSelected(DetailTab.DIVIDENDS)

        assertEquals(notRequested, viewModel.state.dividends.value)
        assertNull(viewModel.state.dividends.error)
    }

    @Test
    fun `company profile is requested on open with an initial summary and the price tab selected`() = runTest {
        val response = CompletableDeferred<ApiResult<CompanyProfileData>>()
        apiClient.getCompanyProfile = { _, _, _, _ -> response.await() }

        val viewModel = viewModel()

        assertEquals(DetailTab.PRICE_AND_RATIOS, viewModel.state.selectedTab)
        assertEquals(listOf("getCompanyProfile(p1, AAPL, null, AUTO)"), callsOf("getCompanyProfile"))
        assertTrue(viewModel.state.companyProfile.isLoading)
        assertEquals("Apple Inc.", viewModel.state.header?.name)
    }

    @Test
    fun `home exchange mark is shown when the profile arrives without opening the profile tab`() = runTest {
        val response = CompletableDeferred<ApiResult<CompanyProfileData>>()
        apiClient.getCompanyProfile = { _, _, _, _ -> response.await() }
        val viewModel = viewModel()
        assertFalse(viewModel.state.homeExchange)

        response.complete(ApiResult.Success(CompanyData.companyProfileData(CompanyData.companyProfile(homeExchange = true))))

        assertTrue(viewModel.state.homeExchange)
        assertEquals(DetailTab.PRICE_AND_RATIOS, viewModel.state.selectedTab)
    }

    @Test
    fun `opening the profile tab after the profile loaded on open sends no second request`() = runTest {
        val viewModel = viewModel()

        viewModel.onTabSelected(DetailTab.COMPANY_PROFILE)

        assertEquals(listOf("getCompanyProfile(p1, AAPL, null, AUTO)"), callsOf("getCompanyProfile"))
        assertEquals(CompanyData.companyProfileData(), viewModel.state.companyProfile.value)
    }

    @Test
    fun `opening the profile tab while the profile loads on open sends no second request`() = runTest {
        val response = CompletableDeferred<ApiResult<CompanyProfileData>>()
        apiClient.getCompanyProfile = { _, _, _, _ -> response.await() }
        val viewModel = viewModel()

        viewModel.onTabSelected(DetailTab.COMPANY_PROFILE)
        response.complete(ApiResult.Success(CompanyData.companyProfileData()))

        assertEquals(1, callsOf("getCompanyProfile").size)
        assertEquals(CompanyData.companyProfileData(), viewModel.state.companyProfile.value)
    }

    @Test
    fun `failed profile on open keeps the route header without the home exchange mark and the price tab`() = runTest {
        apiClient.getCompanyProfile = { _, _, _, _ -> ApiResult.Failure(ApiError.Network(isTimeout = false)) }

        val viewModel = viewModel()

        assertEquals(apple, viewModel.state.header)
        assertFalse(viewModel.state.homeExchange)
        assertEquals(CompanyData.priceAndRatios(), viewModel.state.priceAndRatios.value)
        assertNull(viewModel.state.priceAndRatios.error)
        assertEquals(ApiError.Network(isTimeout = false), viewModel.state.companyProfile.error)
    }

    @Test
    fun `header without a summary is loaded from the company profile`() = runTest {
        val viewModel = viewModel(DetailRoute(Symbol("AAPL")))

        assertEquals(listOf("getCompanyProfile(p1, AAPL, null, AUTO)"), callsOf("getCompanyProfile"))
        val header = viewModel.state.header
        assertEquals("Apple Inc.", header?.name)
        assertEquals("NASDAQ", header?.exchange)
        assertEquals("/logos/AAPL", header?.logoUrl)
        assertEquals(CompanyData.priceAndRatios().quote, viewModel.state.headerQuote)

        viewModel.onTabSelected(DetailTab.COMPANY_PROFILE)
        assertEquals(1, callsOf("getCompanyProfile").size)
    }

    @Test
    fun `header is empty while neither summary nor profile is known`() = runTest {
        apiClient.getCompanyProfile = { _, _, _, _ -> ApiResult.Failure(ApiError.Network(isTimeout = true)) }

        val viewModel = viewModel(DetailRoute(Symbol("AAPL")))

        assertNull(viewModel.state.header)
        assertEquals(ApiError.Network(isTimeout = true), viewModel.state.companyProfile.error)
    }

    @Test
    fun `header is updated when the company profile arrives`() = runTest {
        apiClient.getCompanyProfile = { _, _, _, _ ->
            ApiResult.Success(CompanyData.companyProfileData(CompanyData.companyProfile(name = "Apple Incorporated")))
        }
        val viewModel = viewModel()

        assertEquals("Apple Incorporated", viewModel.state.header?.name)
        assertEquals(apple.logoUrl, viewModel.state.header?.logoUrl)
    }

    @Test
    fun `header carries the security type of the route company and no home exchange before the profile arrives`() = runTest {
        val adr = Companies.identity("NSRGY", name = "Nestle SA", type = "ADR", stock = false)
        apiClient.getCompanyProfile = { _, _, _, _ -> CompletableDeferred<ApiResult<CompanyProfileData>>().await() }

        val viewModel = viewModel(DetailRoute(adr.symbol, adr))

        assertEquals("ADR", viewModel.state.header?.type)
        assertEquals(false, viewModel.state.header?.stock)
        assertFalse(viewModel.state.homeExchange)
    }

    @Test
    fun `header takes the security type and home exchange from the loaded company profile`() = runTest {
        apiClient.getCompanyProfile = { _, _, _, _ ->
            ApiResult.Success(CompanyData.companyProfileData(CompanyData.companyProfile(type = "ADR", stock = false, homeExchange = true)))
        }
        val viewModel = viewModel()

        assertEquals("ADR", viewModel.state.header?.type)
        assertEquals(false, viewModel.state.header?.stock)
        assertTrue(viewModel.state.homeExchange)
    }

    @Test
    fun `profile outside the home exchange has no home exchange mark`() = runTest {
        val viewModel = viewModel(DetailRoute(Symbol("AAPL")))

        assertEquals("Common Stock", viewModel.state.header?.type)
        assertFalse(viewModel.state.homeExchange)
    }

    @Test
    fun `refresh sends the manual trigger for all tabs including tabs not opened yet`() = runTest {
        val viewModel = viewModel()
        apiClient.calls.clear()

        viewModel.refresh()

        assertEquals(
            setOf(
                "getPriceAndRatios(p1, AAPL, null, MANUAL)",
                "getDividends(p1, AAPL, null, MANUAL)",
                "getCompanyProfile(p1, AAPL, null, MANUAL)",
                "getBalanceSheet(p1, AAPL, null, MANUAL)",
            ),
            apiClient.calls.filterNot { it.startsWith("getSources") }.toSet(),
        )
        apiClient.calls.clear()
        DetailTab.entries.forEach(viewModel::onTabSelected)
        assertEquals(listOf("getSources(p1)"), apiClient.calls)
    }

    @Test
    fun `balance sheet sources are loaded lazily when the tab is first shown`() = runTest {
        val viewModel = viewModel()
        assertTrue(callsOf("getSources").isEmpty())
        assertTrue(viewModel.state.balanceSheet.sources.isEmpty())

        viewModel.onTabSelected(DetailTab.BALANCE_SHEET)

        assertEquals(listOf("getSources(p1)"), callsOf("getSources"))
        assertEquals(listOf(finnhub, secEdgar), viewModel.state.balanceSheet.sources)
        assertNull(viewModel.state.balanceSheet.selectedSource)
        assertEquals(SourceId.FINNHUB, viewModel.state.balanceSheet.defaultSource)
        assertEquals(finnhubSheet.meta, viewModel.state.balanceSheet.data.value?.meta)
        assertEquals("SEC EDGAR", viewModel.state.sourceName(SourceId.SEC_EDGAR))
    }

    @Test
    fun `balance sheet falls back to Finnhub and SEC EDGAR when sources cannot be loaded`() = runTest {
        apiClient.getSources = { ApiResult.Failure(ApiError.Network(isTimeout = false)) }
        val viewModel = viewModel()

        viewModel.onTabSelected(DetailTab.BALANCE_SHEET)

        assertEquals(listOf(SourceId.FINNHUB, SourceId.SEC_EDGAR), viewModel.state.balanceSheet.sources.map { it.id })
        assertEquals(listOf("getBalanceSheet(p1, AAPL, null, AUTO)"), callsOf("getBalanceSheet"))
    }

    @Test
    fun `selecting SEC EDGAR requests it and switching back to the default uses the kept result`() = runTest {
        val viewModel = viewModel()
        viewModel.onTabSelected(DetailTab.BALANCE_SHEET)

        viewModel.onSourceSelected(SourceId.SEC_EDGAR)
        assertEquals(secEdgarSheet.meta, viewModel.state.balanceSheet.data.value?.meta)
        assertEquals(listOf(2.0), viewModel.state.balanceSheet.data.value?.table?.rows?.single()?.values)

        viewModel.onSourceSelected(null)
        assertEquals(finnhubSheet.meta, viewModel.state.balanceSheet.data.value?.meta)
        viewModel.onSourceSelected(SourceId.SEC_EDGAR)

        assertEquals(
            listOf("getBalanceSheet(p1, AAPL, null, AUTO)", "getBalanceSheet(p1, AAPL, sec-edgar, AUTO)"),
            callsOf("getBalanceSheet"),
        )
        assertEquals(SourceId.SEC_EDGAR, viewModel.state.balanceSheet.selectedSource)
    }

    @Test
    fun `refresh on the balance sheet uses the selected source and drops kept results of other sources`() = runTest {
        val viewModel = viewModel()
        viewModel.onTabSelected(DetailTab.BALANCE_SHEET)
        viewModel.onSourceSelected(SourceId.SEC_EDGAR)
        apiClient.calls.clear()

        viewModel.refresh()
        assertEquals(listOf("getBalanceSheet(p1, AAPL, sec-edgar, MANUAL)"), callsOf("getBalanceSheet"))

        viewModel.onSourceSelected(null)
        assertEquals(
            listOf("getBalanceSheet(p1, AAPL, sec-edgar, MANUAL)", "getBalanceSheet(p1, AAPL, null, AUTO)"),
            callsOf("getBalanceSheet"),
        )
    }

    @Test
    fun `non US company with SEC EDGAR shows the not supported status`() = runTest {
        apiClient.getBalanceSheet = { _, _, source, _ ->
            ApiResult.Success(
                if (source == SourceId.SEC_EDGAR) {
                    BalanceSheet(CompanyData.meta(SourceId.SEC_EDGAR, DataStatus.NOT_SUPPORTED, fetchedAt = null), emptyList())
                } else {
                    finnhubSheet
                },
            )
        }
        val viewModel = viewModel(DetailRoute(Symbol("SAP.DE"), Companies.identity("SAP.DE", name = "SAP SE")))
        viewModel.onTabSelected(DetailTab.BALANCE_SHEET)

        viewModel.onSourceSelected(SourceId.SEC_EDGAR)

        val view = viewModel.state.balanceSheet.data.value
        assertEquals(DataStatus.NOT_SUPPORTED, view?.meta?.status)
        assertTrue(view?.table?.isEmpty == true)
        assertEquals(listOf("getBalanceSheet(p1, SAP.DE, null, AUTO)", "getBalanceSheet(p1, SAP.DE, sec-edgar, AUTO)"), callsOf("getBalanceSheet"))
    }

    @Test
    fun `no request names a source unless one is picked in the balance sheet dropdown`() = runTest {
        val viewModel = viewModel()

        DetailTab.entries.forEach(viewModel::onTabSelected)
        viewModel.refresh()

        val companyDataCalls = apiClient.calls.filterNot { it.startsWith("getSources") }
        assertEquals(8, companyDataCalls.size)
        assertTrue(companyDataCalls.all { it.contains(", null, ") })
    }

    @Test
    fun `international company takes the default balance sheet from the source the backend picks`() = runTest {
        val eodhdSheet = CompanyData.balanceSheet(source = SourceId.EODHD, status = DataStatus.NOT_SUPPORTED)
        apiClient.getBalanceSheet = { _, _, source, _ -> ApiResult.Success(if (source == null) eodhdSheet else finnhubSheet) }
        val viewModel = viewModel(DetailRoute(Symbol("SAP.DE"), Companies.identity("SAP.DE", name = "SAP SE")))

        viewModel.onTabSelected(DetailTab.BALANCE_SHEET)

        assertEquals(listOf("getBalanceSheet(p1, SAP.DE, null, AUTO)"), callsOf("getBalanceSheet"))
        assertEquals(SourceId.EODHD, viewModel.state.balanceSheet.defaultSource)
        assertEquals(eodhdSheet.meta, viewModel.state.balanceSheet.data.value?.meta)
    }

    @Test
    fun `picking a source explicitly sends it even when it is the source that answered the default`() = runTest {
        val viewModel = viewModel()
        viewModel.onTabSelected(DetailTab.BALANCE_SHEET)

        viewModel.onSourceSelected(SourceId.FINNHUB)

        assertEquals(
            listOf("getBalanceSheet(p1, AAPL, null, AUTO)", "getBalanceSheet(p1, AAPL, finnhub, AUTO)"),
            callsOf("getBalanceSheet"),
        )
        assertEquals(SourceId.FINNHUB, viewModel.state.balanceSheet.selectedSource)
    }

    @Test
    fun `refresh before the balance sheet tab is shown requests the default and the tab still loads its sources`() = runTest {
        val viewModel = viewModel()
        viewModel.refresh()
        assertTrue(callsOf("getSources").isEmpty())

        viewModel.onTabSelected(DetailTab.BALANCE_SHEET)

        assertEquals(listOf("getBalanceSheet(p1, AAPL, null, MANUAL)"), callsOf("getBalanceSheet"))
        assertEquals(listOf(finnhub, secEdgar), viewModel.state.balanceSheet.sources)
    }

    @Test
    fun `balance sheet with no periods makes an empty table`() = runTest {
        apiClient.getBalanceSheet = { _, _, _, _ -> ApiResult.Success(CompanyData.balanceSheet(status = DataStatus.NO_DATA)) }
        val viewModel = viewModel()

        viewModel.onTabSelected(DetailTab.BALANCE_SHEET)

        assertTrue(viewModel.state.balanceSheet.data.value?.table?.isEmpty == true)
        assertEquals(DataStatus.NO_DATA, viewModel.state.balanceSheet.data.value?.meta?.status)
    }

    @Test
    fun `failed load keeps the previous data and reports the error`() = runTest {
        val viewModel = viewModel()
        apiClient.getPriceAndRatios = { _, _, _, _ -> ApiResult.Failure(ApiError.Network(isTimeout = false)) }

        viewModel.refresh()

        assertEquals(CompanyData.priceAndRatios(), viewModel.state.priceAndRatios.value)
        assertEquals(ApiError.Network(isTimeout = false), viewModel.state.priceAndRatios.error)
    }

    @Test
    fun `failed balance sheet without a result is loaded again when its source is selected`() = runTest {
        apiClient.getBalanceSheet = { _, _, _, _ -> ApiResult.Failure(ApiError.Network(isTimeout = false)) }
        val viewModel = viewModel()
        viewModel.onTabSelected(DetailTab.BALANCE_SHEET)
        apiClient.getBalanceSheet = { _, _, _, _ -> ApiResult.Success(finnhubSheet) }

        viewModel.onSourceSelected(null)

        assertEquals(2, callsOf("getBalanceSheet").size)
        assertEquals(finnhubSheet.meta, viewModel.state.balanceSheet.data.value?.meta)
        assertNull(viewModel.state.balanceSheet.data.error)
    }

    @Test
    fun `tab shows loading while its request runs`() = runTest {
        val response = CompletableDeferred<ApiResult<CompanyProfileData>>()
        apiClient.getCompanyProfile = { _, _, _, _ -> response.await() }
        val viewModel = viewModel()

        viewModel.onTabSelected(DetailTab.COMPANY_PROFILE)
        assertTrue(viewModel.state.companyProfile.isLoading)

        response.complete(ApiResult.Success(CompanyData.companyProfileData()))
        assertFalse(viewModel.state.companyProfile.isLoading)
    }

    @Test
    fun `refresh while a tab loads replaces the running request`() = runTest {
        val firstResponse = CompletableDeferred<ApiResult<CompanyProfileData>>()
        apiClient.getCompanyProfile = { _, _, _, trigger ->
            if (trigger == RequestTrigger.MANUAL) ApiResult.Success(CompanyData.companyProfileData(CompanyData.companyProfile(name = "Fresh"))) else firstResponse.await()
        }
        val viewModel = viewModel()
        viewModel.onTabSelected(DetailTab.COMPANY_PROFILE)

        viewModel.refresh()
        firstResponse.complete(ApiResult.Success(CompanyData.companyProfileData(CompanyData.companyProfile(name = "Stale"))))

        assertEquals("Fresh", viewModel.state.companyProfile.value?.profile?.name)
    }

    @Test
    fun `every status is kept with cached data and with empty data`() = runTest {
        DataStatus.entries.forEach { status ->
            apiClient.getPriceAndRatios = { _, _, _, _ -> ApiResult.Success(CompanyData.priceAndRatios(quote = null, ratios = emptyList(), status = status)) }
            val viewModel = viewModel()

            assertEquals(status, viewModel.state.priceAndRatios.value?.meta?.status)
            assertNull(viewModel.state.priceAndRatios.value?.quote)
        }
    }

    @Test
    fun `watchlist state follows the route until the watchlist is loaded`() = runTest {
        val viewModel = viewModel(DetailRoute(apple.symbol, apple, initiallyInWatchlist = true))

        assertTrue(viewModel.state.inWatchlist)
    }

    @Test
    fun `header price falls back to the watchlist market data until the price is loaded`() = runTest {
        val rowQuote = Companies.quote(price = 150.0)
        apiClient.getWatchlist = { ApiResult.Success(listOf(Companies.item("AAPL"))) }
        apiClient.getWatchlistMarketData = { _, _ -> ApiResult.Success(listOf(Companies.marketData("AAPL", quote = rowQuote))) }
        val response = CompletableDeferred<ApiResult<PriceAndRatios>>()
        apiClient.getPriceAndRatios = { _, _, _, _ -> response.await() }
        val viewModel = viewModel()
        watchlistRepository.refresh()
        watchlistRepository.loadMarketData(listOf(apple.symbol))

        assertEquals(rowQuote, viewModel.state.headerQuote)
        response.complete(ApiResult.Success(CompanyData.priceAndRatios()))
        assertEquals(CompanyData.priceAndRatios().quote, viewModel.state.headerQuote)
    }

    @Test
    fun `added company carries the sector of the loaded company profile until the stored item arrives`() = runTest {
        apiClient.getWatchlist = { ApiResult.Success(emptyList()) }
        val response = CompletableDeferred<ApiResult<WatchlistItem>>()
        apiClient.addToWatchlist = { _, _, _ -> response.await() }
        val viewModel = viewModel(DetailRoute(Symbol("AAPL")))
        watchlistRepository.refresh()

        viewModel.onToggleWatchlist()

        val sector = CompanyData.companyProfileData().profile?.sector
        assertEquals(listOf(sector), watchlistRepository.watchlist.value?.map { it.sector })
        response.complete(ApiResult.Success(Companies.item("AAPL", sector = "Stored")))
        assertEquals(listOf("Stored"), watchlistRepository.watchlist.value?.map { it.sector })
    }

    @Test
    fun `add sends the name of the route company shown in the header`() = runTest {
        val sentNames = mutableListOf<String>()
        apiClient.addToWatchlist = { _, _, name -> sentNames += name; ApiResult.Success(Companies.item("AAPL")) }
        apiClient.getCompanyProfile = { _, _, _, _ -> CompletableDeferred<ApiResult<CompanyProfileData>>().await() }
        val viewModel = viewModel()

        viewModel.onToggleWatchlist()

        assertEquals(listOf("Apple Inc."), sentNames)
    }

    @Test
    fun `add sends the name of the loaded company profile shown in the header`() = runTest {
        apiClient.getCompanyProfile = { _, _, _, _ ->
            ApiResult.Success(CompanyData.companyProfileData(CompanyData.companyProfile(name = "Apple Incorporated")))
        }
        val sentNames = mutableListOf<String>()
        apiClient.addToWatchlist = { _, _, name -> sentNames += name; ApiResult.Success(Companies.item("AAPL")) }
        val viewModel = viewModel()

        viewModel.onToggleWatchlist()

        assertEquals(listOf("Apple Incorporated"), sentNames)
    }

    @Test
    fun `toggling adds the company to the watchlist and removes it again`() = runTest {
        apiClient.addToWatchlist = { _, _, _ -> ApiResult.Success(Companies.item("AAPL")) }
        apiClient.removeFromWatchlist = { _, _ -> ApiResult.Success(Unit) }
        val viewModel = viewModel()

        viewModel.onToggleWatchlist()
        assertTrue(viewModel.state.inWatchlist)

        viewModel.onToggleWatchlist()
        assertFalse(viewModel.state.inWatchlist)
        assertEquals(listOf("addToWatchlist(p1, AAPL)", "removeFromWatchlist(p1, AAPL)"), callsOf("addToWatchlist") + callsOf("removeFromWatchlist"))
    }

    @Test
    fun `failed add reverts and shows a message`() = runTest {
        apiClient.addToWatchlist = { _, _, _ -> ApiResult.Failure(ApiError.Http(409, null)) }
        val viewModel = viewModel()

        viewModel.onToggleWatchlist()

        assertFalse(viewModel.state.inWatchlist)
        val message = viewModel.state.message
        assertEquals(DetailMessage.AddFailed(ApiError.Http(409, null)), message)
        viewModel.onMessageShown(message!!)
        assertNull(viewModel.state.message)
    }

    @Test
    fun `watchlist toggle does nothing while the header is unknown`() = runTest {
        apiClient.getCompanyProfile = { _, _, _, _ -> ApiResult.Failure(ApiError.Network(isTimeout = false)) }
        val viewModel = viewModel(DetailRoute(Symbol("AAPL")))

        viewModel.onToggleWatchlist()

        assertTrue(callsOf("addToWatchlist").isEmpty())
    }

    private val king = Tag("chess-king", "Dividend king")
    private val income = Tag("coin", "Income")

    private suspend fun TestScope.inWatchlist(vararg tags: Tag, profileHasTags: Boolean = true): DetailViewModel {
        if (profileHasTags) {
            apiClient.getCompanyProfile = { _, _, _, _ -> ApiResult.Success(CompanyData.companyProfileData(tags = tags.toList())) }
        }
        apiClient.getWatchlist = { ApiResult.Success(listOf(Companies.item("AAPL", tags = tags.toList()))) }
        val viewModel = viewModel(DetailRoute(apple.symbol, apple, initiallyInWatchlist = true))
        watchlistRepository.refresh()
        return viewModel
    }

    private fun stubSavedTags() {
        apiClient.setCompanyTags = { _, _, tags -> ApiResult.Success(tags) }
    }

    @Test
    fun `tags come with the company profile whatever its status`() = runTest {
        apiClient.getCompanyProfile = { _, _, _, _ ->
            ApiResult.Success(CompanyData.companyProfileData(profile = null, status = DataStatus.LIMIT_REACHED, tags = listOf(king)))
        }
        val viewModel = inWatchlist(profileHasTags = false)

        assertEquals(listOf(king), viewModel.state.tags.tags)
        assertTrue(viewModel.state.tags.isEditable)
    }

    @Test
    fun `tags of the watchlist are shown until the company profile arrives`() = runTest {
        val response = CompletableDeferred<ApiResult<CompanyProfileData>>()
        apiClient.getCompanyProfile = { _, _, _, _ -> response.await() }
        val viewModel = inWatchlist(income, profileHasTags = false)

        assertEquals(listOf(income), viewModel.state.tags.tags)

        response.complete(ApiResult.Success(CompanyData.companyProfileData(tags = listOf(king))))

        assertEquals(listOf(king), viewModel.state.tags.tags)
    }

    @Test
    fun `tags of the watchlist are kept when the company profile fails`() = runTest {
        apiClient.getCompanyProfile = { _, _, _, _ -> ApiResult.Failure(ApiError.Network(isTimeout = false)) }

        val viewModel = inWatchlist(income, profileHasTags = false)

        assertEquals(listOf(income), viewModel.state.tags.tags)
        assertTrue(viewModel.state.tags.canAdd)
    }

    @Test
    fun `company outside the watchlist has no tags and cannot be tagged`() = runTest {
        apiClient.getWatchlist = { ApiResult.Success(emptyList()) }
        val viewModel = viewModel()
        watchlistRepository.refresh()

        viewModel.onAddTagRequested()
        viewModel.onAddTag("coin", "Income")

        assertEquals(TagsUiState(), viewModel.state.tags)
        assertTrue(callsOf("setCompanyTags").isEmpty() && callsOf("getProfileTags").isEmpty())
    }

    @Test
    fun `adding a tag opens the dialog with the profile tags the company doesn't have`() = runTest {
        apiClient.getProfileTags = { ApiResult.Success(listOf(king, income)) }
        val viewModel = inWatchlist(Tag("coin", "INCOME"))

        viewModel.onAddTagRequested()

        assertTrue(viewModel.state.tags.isAddDialogOpen)
        assertEquals(listOf(king), viewModel.state.tags.suggestions)
    }

    @Test
    fun `failed profile tags leave the dialog without suggestions`() = runTest {
        apiClient.getProfileTags = { ApiResult.Failure(ApiError.Network(isTimeout = true)) }
        val viewModel = inWatchlist()

        viewModel.onAddTagRequested()

        assertTrue(viewModel.state.tags.isAddDialogOpen)
        assertEquals(emptyList<Tag>(), viewModel.state.tags.suggestions)
    }

    @Test
    fun `added tag is saved with the existing ones, closes the dialog and reaches the watchlist`() = runTest {
        apiClient.getProfileTags = { ApiResult.Success(emptyList()) }
        stubSavedTags()
        val viewModel = inWatchlist(income)
        viewModel.onAddTagRequested()

        viewModel.onAddTag("chess-king", "  Dividend king ")

        assertEquals(listOf("setCompanyTags(p1, AAPL, coin:Income,chess-king:Dividend king)"), callsOf("setCompanyTags"))
        assertEquals(listOf(income, king), viewModel.state.tags.tags)
        assertFalse(viewModel.state.tags.isAddDialogOpen)
        assertFalse(viewModel.state.tags.isSaving)
        assertEquals(listOf(listOf(income, king)), watchlistRepository.watchlist.value?.map { it.tags })
    }

    @Test
    fun `duplicate, blank or 21st tag is not saved`() = runTest {
        stubSavedTags()
        val viewModel = inWatchlist(king)

        viewModel.onAddTag("chess-king", "DIVIDEND KING")
        viewModel.onAddTag("coin", "   ")
        assertTrue(callsOf("setCompanyTags").isEmpty())

        val full = (1..20).map { Tag("star", "Tag $it") }
        val fullViewModel = inWatchlist(*full.toTypedArray())
        fullViewModel.onAddTag("coin", "Income")

        assertTrue(callsOf("setCompanyTags").isEmpty())
        assertTrue(fullViewModel.state.tags.isLimitReached)
        assertFalse(fullViewModel.state.tags.canAdd)
    }

    @Test
    fun `failed add keeps the dialog open with the error and the tags unchanged`() = runTest {
        apiClient.getProfileTags = { ApiResult.Success(emptyList()) }
        apiClient.setCompanyTags = { _, _, _ -> ApiResult.Failure(ApiError.Network(isTimeout = true)) }
        val viewModel = inWatchlist(income)
        viewModel.onAddTagRequested()

        viewModel.onAddTag("chess-king", "Dividend king")

        assertEquals(listOf(income), viewModel.state.tags.tags)
        assertTrue(viewModel.state.tags.isAddDialogOpen)
        assertEquals(ApiError.Network(isTimeout = true), viewModel.state.tags.dialogError)
        assertNull(viewModel.state.message)
    }

    @Test
    fun `removing a tag saves the others`() = runTest {
        stubSavedTags()
        val viewModel = inWatchlist(king, income)

        viewModel.onRemoveTag(Tag("chess-king", "dividend KING"))

        assertEquals(listOf("setCompanyTags(p1, AAPL, coin:Income)"), callsOf("setCompanyTags"))
        assertEquals(listOf(income), viewModel.state.tags.tags)
    }

    @Test
    fun `removing a tag the company doesn't have sends nothing`() = runTest {
        val viewModel = inWatchlist(king)

        viewModel.onRemoveTag(income)

        assertTrue(callsOf("setCompanyTags").isEmpty())
    }

    @Test
    fun `failed removal keeps the tag and shows a message`() = runTest {
        apiClient.setCompanyTags = { _, _, _ -> ApiResult.Failure(ApiError.Network(isTimeout = false)) }
        val viewModel = inWatchlist(king)

        viewModel.onRemoveTag(king)

        assertEquals(listOf(king), viewModel.state.tags.tags)
        assertEquals(DetailMessage.TagsSaveFailed(ApiError.Network(isTimeout = false)), viewModel.state.message)
    }

    @Test
    fun `no other change is accepted while tags are being saved`() = runTest {
        val response = CompletableDeferred<ApiResult<List<Tag>>>()
        apiClient.setCompanyTags = { _, _, _ -> response.await() }
        val viewModel = inWatchlist(king, income)

        viewModel.onRemoveTag(king)
        viewModel.onRemoveTag(income)
        viewModel.onAddTag("gem", "Quality")

        assertTrue(viewModel.state.tags.isSaving)
        assertEquals(1, callsOf("setCompanyTags").size)
        response.complete(ApiResult.Success(listOf(income)))
        assertFalse(viewModel.state.tags.isSaving)
    }

    @Test
    fun `company profile requested before a tag change doesn't bring back the old tags`() = runTest {
        stubSavedTags()
        val viewModel = inWatchlist(king)
        val response = CompletableDeferred<ApiResult<CompanyProfileData>>()
        apiClient.getCompanyProfile = { _, _, _, _ -> response.await() }
        viewModel.refresh()

        viewModel.onRemoveTag(king)
        response.complete(ApiResult.Success(CompanyData.companyProfileData(tags = listOf(king))))

        assertEquals(emptyList<Tag>(), viewModel.state.tags.tags)
    }

    @Test
    fun `removing the company from the watchlist drops its tags and re-adding shows the stored ones`() = runTest {
        apiClient.getCompanyProfile = { _, _, _, _ -> ApiResult.Success(CompanyData.companyProfileData(tags = listOf(king))) }
        apiClient.removeFromWatchlist = { _, _ -> ApiResult.Success(Unit) }
        apiClient.addToWatchlist = { _, _, _ -> ApiResult.Success(Companies.item("AAPL")) }
        val viewModel = inWatchlist(king, profileHasTags = false)

        viewModel.onToggleWatchlist()

        assertEquals(TagsUiState(), viewModel.state.tags)

        viewModel.onToggleWatchlist()

        assertEquals(emptyList<Tag>(), viewModel.state.tags.tags)
        assertTrue(viewModel.state.tags.isEditable)
    }
}
