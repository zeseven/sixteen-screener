package com.sixteenscreener.app.ui.detail

import com.sixteenscreener.app.data.repository.DefaultCompanyRepository
import com.sixteenscreener.app.data.repository.DefaultSourceRepository
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
        assertEquals(listOf("getBalanceSheet(p1, AAPL, finnhub, AUTO)"), callsOf("getBalanceSheet"))
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
                "getBalanceSheet(p1, AAPL, finnhub, MANUAL)",
            ),
            apiClient.calls.filterNot { it.startsWith("getSources") }.toSet(),
        )
        apiClient.calls.clear()
        DetailTab.entries.forEach(viewModel::onTabSelected)
        assertTrue(apiClient.calls.isEmpty())
    }

    @Test
    fun `balance sheet sources are loaded lazily when the tab is first shown`() = runTest {
        val viewModel = viewModel()
        assertTrue(callsOf("getSources").isEmpty())
        assertTrue(viewModel.state.balanceSheet.sources.isEmpty())

        viewModel.onTabSelected(DetailTab.BALANCE_SHEET)

        assertEquals(listOf("getSources(p1)"), callsOf("getSources"))
        assertEquals(listOf(finnhub, secEdgar), viewModel.state.balanceSheet.sources)
        assertEquals(SourceId.FINNHUB, viewModel.state.balanceSheet.selectedSource)
        assertEquals(finnhubSheet.meta, viewModel.state.balanceSheet.data.value?.meta)
        assertEquals("SEC EDGAR", viewModel.state.sourceName(SourceId.SEC_EDGAR))
    }

    @Test
    fun `balance sheet falls back to Finnhub and SEC EDGAR when sources cannot be loaded`() = runTest {
        apiClient.getSources = { ApiResult.Failure(ApiError.Network(isTimeout = false)) }
        val viewModel = viewModel()

        viewModel.onTabSelected(DetailTab.BALANCE_SHEET)

        assertEquals(listOf(SourceId.FINNHUB, SourceId.SEC_EDGAR), viewModel.state.balanceSheet.sources.map { it.id })
        assertEquals(listOf("getBalanceSheet(p1, AAPL, finnhub, AUTO)"), callsOf("getBalanceSheet"))
    }

    @Test
    fun `selecting SEC EDGAR requests it and switching back uses the kept result`() = runTest {
        val viewModel = viewModel()
        viewModel.onTabSelected(DetailTab.BALANCE_SHEET)

        viewModel.onSourceSelected(SourceId.SEC_EDGAR)
        assertEquals(secEdgarSheet.meta, viewModel.state.balanceSheet.data.value?.meta)
        assertEquals(listOf(2.0), viewModel.state.balanceSheet.data.value?.table?.rows?.single()?.values)

        viewModel.onSourceSelected(SourceId.FINNHUB)
        viewModel.onSourceSelected(SourceId.SEC_EDGAR)

        assertEquals(
            listOf("getBalanceSheet(p1, AAPL, finnhub, AUTO)", "getBalanceSheet(p1, AAPL, sec-edgar, AUTO)"),
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

        viewModel.onSourceSelected(SourceId.FINNHUB)
        assertEquals(
            listOf("getBalanceSheet(p1, AAPL, sec-edgar, MANUAL)", "getBalanceSheet(p1, AAPL, finnhub, AUTO)"),
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
        assertEquals(listOf("getBalanceSheet(p1, SAP.DE, finnhub, AUTO)", "getBalanceSheet(p1, SAP.DE, sec-edgar, AUTO)"), callsOf("getBalanceSheet"))
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

        viewModel.onSourceSelected(SourceId.FINNHUB)

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
}
