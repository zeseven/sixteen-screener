package com.sixteenscreener.app.ui.detail

import com.sixteenscreener.app.data.repository.DefaultCompanyRepository
import com.sixteenscreener.app.data.repository.DefaultSourceRepository
import com.sixteenscreener.app.data.repository.DefaultTagRepository
import com.sixteenscreener.app.data.repository.DefaultWatchlistRepository
import com.sixteenscreener.app.domain.model.ActiveProfile
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.BalanceSheet
import com.sixteenscreener.app.domain.model.CompanyProfileData
import com.sixteenscreener.app.domain.model.CompanySources
import com.sixteenscreener.app.domain.model.DataStatus
import com.sixteenscreener.app.domain.model.Dividends
import com.sixteenscreener.app.domain.model.PriceAndRatios
import com.sixteenscreener.app.domain.model.ProfileId
import com.sixteenscreener.app.domain.model.RequestTrigger
import com.sixteenscreener.app.domain.model.SourceId
import com.sixteenscreener.app.domain.model.SourceOption
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
    private val sap = Companies.identity("SAP.DE", name = "SAP SE", exchange = "XETRA")

    private val yahooId = SourceId("yahoo")
    private val finnhub = SourceOption(SourceId.FINNHUB, "Finnhub", isDefault = true, automaticRequests = true)
    private val secEdgar = SourceOption(SourceId.SEC_EDGAR, "SEC EDGAR", isDefault = false, automaticRequests = true)
    private val yahoo = SourceOption(yahooId, "Yahoo Finance", isDefault = false, automaticRequests = false)
    private val eodhd = SourceOption(SourceId.EODHD, "EODHD", isDefault = true, automaticRequests = false)

    private val usSources = CompanySources(
        priceRatios = listOf(finnhub, yahoo),
        dividends = listOf(yahoo),
        balanceSheet = listOf(finnhub, secEdgar, yahoo),
        profile = listOf(finnhub, yahoo),
    )
    private val internationalSources = CompanySources(
        priceRatios = listOf(eodhd, yahoo),
        dividends = listOf(eodhd, yahoo),
        balanceSheet = listOf(yahoo),
        profile = listOf(yahoo),
    )

    private val finnhubSheet = CompanyData.balanceSheet(period(2026, item("Assets", 1.0)), source = SourceId.FINNHUB)
    private val secEdgarSheet = CompanyData.balanceSheet(period(2026, item("Assets", 2.0)), source = SourceId.SEC_EDGAR)
    private val yahooProfile = CompanyData.companyProfileData(CompanyData.companyProfile(type = "Fund", stock = false, homeExchange = true))
    private val notRequestedDividends = CompanyData.dividends(dividendYield = null, status = DataStatus.NOT_REQUESTED)

    private lateinit var watchlistRepository: DefaultWatchlistRepository

    init {
        apiClient.getCompanySources = { _, _ -> ApiResult.Success(usSources) }
        apiClient.getPriceAndRatios = { _, _, _, _ -> ApiResult.Success(CompanyData.priceAndRatios()) }
        apiClient.getCompanyProfile = { _, _, source, _ ->
            ApiResult.Success(if (source == yahooId) yahooProfile else CompanyData.companyProfileData())
        }
        apiClient.getDividends = { _, _, _, _ -> ApiResult.Success(CompanyData.dividends()) }
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

    private fun companyDataCalls() = apiClient.calls.filterNot { it.startsWith("getCompanySources") }

    @Test
    fun `sources are requested once and the price and the company profile load from their first option when the screen opens`() = runTest {
        val viewModel = viewModel()

        assertEquals(
            listOf("getCompanySources(p1, AAPL)", "getPriceAndRatios(p1, AAPL, finnhub, AUTO)", "getCompanyProfile(p1, AAPL, finnhub, AUTO)"),
            apiClient.calls,
        )
        assertEquals(CompanyData.priceAndRatios(), viewModel.state.priceAndRatios.data.value)
        assertFalse(viewModel.state.priceAndRatios.data.isLoading)
    }

    @Test
    fun `first option of every tab is selected and the options keep the backend order`() = runTest {
        val viewModel = viewModel()

        with(viewModel.state) {
            assertEquals(listOf(finnhub, yahoo), priceAndRatios.options)
            assertEquals(SourceId.FINNHUB, priceAndRatios.selectedSource)
            assertEquals(yahooId, dividends.selectedSource)
            assertEquals(listOf(finnhub, secEdgar, yahoo), balanceSheet.options)
            assertEquals(SourceId.FINNHUB, balanceSheet.selectedSource)
            assertEquals(SourceId.FINNHUB, companyProfile.selectedSource)
            assertFalse(balanceSheet.hasNoSource)
            assertEquals("Yahoo Finance", sourceName(yahooId))
        }
    }

    @Test
    fun `tabs show loading and nothing is requested while the sources request runs`() = runTest {
        val sources = CompletableDeferred<ApiResult<CompanySources>>()
        apiClient.getCompanySources = { _, _ -> sources.await() }
        val viewModel = viewModel()

        viewModel.onTabSelected(DetailTab.DIVIDENDS)

        assertTrue(companyDataCalls().isEmpty())
        assertTrue(viewModel.state.priceAndRatios.data.isLoading)
        assertTrue(viewModel.state.dividends.data.isLoading)
        assertEquals("Apple Inc.", viewModel.state.header?.name)

        sources.complete(ApiResult.Success(usSources))

        assertEquals(
            setOf(
                "getPriceAndRatios(p1, AAPL, finnhub, AUTO)",
                "getCompanyProfile(p1, AAPL, finnhub, AUTO)",
                "getDividends(p1, AAPL, yahoo, AUTO)",
            ),
            companyDataCalls().toSet(),
        )
        assertEquals(CompanyData.dividends(), viewModel.state.dividends.data.value)
    }

    @Test
    fun `each tab loads once from its selected source when first shown`() = runTest {
        val viewModel = viewModel()

        DetailTab.entries.forEach(viewModel::onTabSelected)
        DetailTab.entries.forEach(viewModel::onTabSelected)

        assertEquals(listOf("getPriceAndRatios(p1, AAPL, finnhub, AUTO)"), callsOf("getPriceAndRatios"))
        assertEquals(listOf("getDividends(p1, AAPL, yahoo, AUTO)"), callsOf("getDividends"))
        assertEquals(listOf("getBalanceSheet(p1, AAPL, finnhub, AUTO)"), callsOf("getBalanceSheet"))
        assertEquals(listOf("getCompanyProfile(p1, AAPL, finnhub, AUTO)"), callsOf("getCompanyProfile"))
        assertEquals(listOf("getCompanySources(p1, AAPL)"), callsOf("getCompanySources"))
        assertEquals(DetailTab.COMPANY_PROFILE, viewModel.state.selectedTab)
    }

    @Test
    fun `dividends are not requested before the dividends tab is shown`() = runTest {
        val viewModel = viewModel()

        assertTrue(callsOf("getDividends").isEmpty())

        viewModel.onTabSelected(DetailTab.DIVIDENDS)

        assertEquals(listOf("getDividends(p1, AAPL, yahoo, AUTO)"), callsOf("getDividends"))
        assertEquals(CompanyData.dividends(), viewModel.state.dividends.data.value)
        assertFalse(viewModel.state.dividends.data.isLoading)
    }

    @Test
    fun `manual-only first option shows its not requested data as loaded data`() = runTest {
        apiClient.getDividends = { _, _, _, _ -> ApiResult.Success(notRequestedDividends) }
        val viewModel = viewModel()

        viewModel.onTabSelected(DetailTab.DIVIDENDS)

        assertEquals(notRequestedDividends, viewModel.state.dividends.data.value)
        assertNull(viewModel.state.dividends.data.error)
    }

    @Test
    fun `failed dividends request keeps the shown dividends with the error`() = runTest {
        val viewModel = viewModel()
        viewModel.onTabSelected(DetailTab.DIVIDENDS)
        apiClient.getDividends = { _, _, _, _ -> ApiResult.Failure(ApiError.Network(isTimeout = true)) }

        viewModel.refresh()

        assertEquals(CompanyData.dividends(), viewModel.state.dividends.data.value)
        assertEquals(ApiError.Network(isTimeout = true), viewModel.state.dividends.data.error)
    }

    @Test
    fun `selecting another source requests the tab from it automatically and shows it`() = runTest {
        val yahooPrice = CompanyData.priceAndRatios(quote = null, status = DataStatus.NOT_REQUESTED)
        apiClient.getPriceAndRatios = { _, _, source, _ ->
            ApiResult.Success(if (source == yahooId) yahooPrice else CompanyData.priceAndRatios())
        }
        val viewModel = viewModel()

        viewModel.onSourceSelected(DetailTab.PRICE_AND_RATIOS, yahooId)

        assertEquals(
            listOf("getPriceAndRatios(p1, AAPL, finnhub, AUTO)", "getPriceAndRatios(p1, AAPL, yahoo, AUTO)"),
            callsOf("getPriceAndRatios"),
        )
        assertEquals(yahooId, viewModel.state.priceAndRatios.selectedSource)
        assertEquals(yahooPrice, viewModel.state.priceAndRatios.data.value)
    }

    @Test
    fun `switching back uses the kept result of a source`() = runTest {
        val viewModel = viewModel()
        viewModel.onTabSelected(DetailTab.BALANCE_SHEET)

        viewModel.onSourceSelected(DetailTab.BALANCE_SHEET, SourceId.SEC_EDGAR)
        assertEquals(secEdgarSheet.meta, viewModel.state.balanceSheet.data.value?.meta)
        assertEquals(listOf(2.0), viewModel.state.balanceSheet.data.value?.table?.rows?.single()?.values)

        viewModel.onSourceSelected(DetailTab.BALANCE_SHEET, SourceId.FINNHUB)
        assertEquals(finnhubSheet.meta, viewModel.state.balanceSheet.data.value?.meta)
        viewModel.onSourceSelected(DetailTab.BALANCE_SHEET, SourceId.SEC_EDGAR)

        assertEquals(
            listOf("getBalanceSheet(p1, AAPL, finnhub, AUTO)", "getBalanceSheet(p1, AAPL, sec-edgar, AUTO)"),
            callsOf("getBalanceSheet"),
        )
        assertEquals(SourceId.SEC_EDGAR, viewModel.state.balanceSheet.selectedSource)
    }

    @Test
    fun `selecting the selected source or a source that is not an option of the tab sends nothing`() = runTest {
        val viewModel = viewModel()
        apiClient.calls.clear()

        viewModel.onSourceSelected(DetailTab.PRICE_AND_RATIOS, SourceId.FINNHUB)
        viewModel.onSourceSelected(DetailTab.PRICE_AND_RATIOS, SourceId.SEC_EDGAR)

        assertTrue(apiClient.calls.isEmpty())
        assertEquals(SourceId.FINNHUB, viewModel.state.priceAndRatios.selectedSource)
    }

    @Test
    fun `failed result of a source is loaded again when the source is selected again`() = runTest {
        apiClient.getBalanceSheet = { _, _, _, _ -> ApiResult.Failure(ApiError.Network(isTimeout = false)) }
        val viewModel = viewModel()
        viewModel.onTabSelected(DetailTab.BALANCE_SHEET)
        viewModel.onSourceSelected(DetailTab.BALANCE_SHEET, SourceId.SEC_EDGAR)
        apiClient.getBalanceSheet = { _, _, _, _ -> ApiResult.Success(finnhubSheet) }

        viewModel.onSourceSelected(DetailTab.BALANCE_SHEET, SourceId.FINNHUB)

        assertEquals(3, callsOf("getBalanceSheet").size)
        assertEquals(finnhubSheet.meta, viewModel.state.balanceSheet.data.value?.meta)
        assertNull(viewModel.state.balanceSheet.data.error)
    }

    @Test
    fun `refresh requests every tab from its selected source with the manual trigger`() = runTest {
        val viewModel = viewModel()
        viewModel.onTabSelected(DetailTab.BALANCE_SHEET)
        viewModel.onSourceSelected(DetailTab.PRICE_AND_RATIOS, yahooId)
        viewModel.onSourceSelected(DetailTab.BALANCE_SHEET, SourceId.SEC_EDGAR)
        apiClient.calls.clear()

        viewModel.refresh()

        assertEquals(
            setOf(
                "getPriceAndRatios(p1, AAPL, yahoo, MANUAL)",
                "getDividends(p1, AAPL, yahoo, MANUAL)",
                "getBalanceSheet(p1, AAPL, sec-edgar, MANUAL)",
                "getCompanyProfile(p1, AAPL, finnhub, MANUAL)",
            ),
            apiClient.calls.toSet(),
        )
        assertEquals(4, apiClient.calls.size)
        apiClient.calls.clear()
        DetailTab.entries.forEach(viewModel::onTabSelected)
        assertTrue(apiClient.calls.isEmpty())
    }

    @Test
    fun `refresh drops kept results of other sources so they load again when selected`() = runTest {
        val viewModel = viewModel()
        viewModel.onTabSelected(DetailTab.BALANCE_SHEET)
        viewModel.onSourceSelected(DetailTab.BALANCE_SHEET, SourceId.SEC_EDGAR)
        apiClient.calls.clear()

        viewModel.refresh()
        assertEquals(listOf("getBalanceSheet(p1, AAPL, sec-edgar, MANUAL)"), callsOf("getBalanceSheet"))

        viewModel.onSourceSelected(DetailTab.BALANCE_SHEET, SourceId.FINNHUB)
        assertEquals(
            listOf("getBalanceSheet(p1, AAPL, sec-edgar, MANUAL)", "getBalanceSheet(p1, AAPL, finnhub, AUTO)"),
            callsOf("getBalanceSheet"),
        )
    }

    @Test
    fun `refresh before the sources arrive is sent with the manual trigger once they are known`() = runTest {
        val sources = CompletableDeferred<ApiResult<CompanySources>>()
        apiClient.getCompanySources = { _, _ -> sources.await() }
        val viewModel = viewModel()

        viewModel.refresh()
        assertTrue(companyDataCalls().isEmpty())
        sources.complete(ApiResult.Success(usSources))

        assertEquals(
            setOf(
                "getPriceAndRatios(p1, AAPL, finnhub, MANUAL)",
                "getDividends(p1, AAPL, yahoo, MANUAL)",
                "getBalanceSheet(p1, AAPL, finnhub, MANUAL)",
                "getCompanyProfile(p1, AAPL, finnhub, MANUAL)",
            ),
            companyDataCalls().toSet(),
        )
        assertEquals(4, companyDataCalls().size)
    }

    @Test
    fun `tab with no source shows no options and requests nothing`() = runTest {
        apiClient.getCompanySources = { _, _ -> ApiResult.Success(usSources.copy(dividends = emptyList())) }
        val viewModel = viewModel()

        viewModel.onTabSelected(DetailTab.DIVIDENDS)
        viewModel.refresh()

        assertTrue(callsOf("getDividends").isEmpty())
        with(viewModel.state.dividends) {
            assertTrue(hasNoSource)
            assertTrue(options.isEmpty())
            assertNull(selectedSource)
            assertEquals(TabData<Dividends>(), data)
        }
    }

    @Test
    fun `failed sources request falls back to the default source for every tab without blocking the screen`() = runTest {
        apiClient.getCompanySources = { _, _ -> ApiResult.Failure(ApiError.Network(isTimeout = true)) }
        val viewModel = viewModel()

        DetailTab.entries.forEach(viewModel::onTabSelected)
        viewModel.refresh()

        assertEquals(
            listOf(
                "getPriceAndRatios(p1, AAPL, null, AUTO)",
                "getCompanyProfile(p1, AAPL, null, AUTO)",
                "getDividends(p1, AAPL, null, AUTO)",
                "getBalanceSheet(p1, AAPL, null, AUTO)",
            ),
            companyDataCalls().take(4),
        )
        assertEquals(
            setOf(
                "getPriceAndRatios(p1, AAPL, null, MANUAL)",
                "getDividends(p1, AAPL, null, MANUAL)",
                "getBalanceSheet(p1, AAPL, null, MANUAL)",
                "getCompanyProfile(p1, AAPL, null, MANUAL)",
            ),
            companyDataCalls().drop(4).toSet(),
        )
        assertEquals(8, companyDataCalls().size)
        with(viewModel.state) {
            assertTrue(priceAndRatios.options.isEmpty())
            assertFalse(priceAndRatios.hasNoSource)
            assertNull(priceAndRatios.selectedSource)
            assertEquals(CompanyData.priceAndRatios(), priceAndRatios.data.value)
            assertEquals(finnhubSheet.meta, balanceSheet.data.value?.meta)
            assertEquals("Apple Inc.", header?.name)
        }
    }

    @Test
    fun `international company opens with the first options and requests nothing from a manual-only source automatically`() = runTest {
        apiClient.getCompanySources = { _, _ -> ApiResult.Success(internationalSources) }
        val viewModel = viewModel(DetailRoute(sap.symbol, sap))

        viewModel.onTabSelected(DetailTab.BALANCE_SHEET)

        assertEquals(
            listOf(
                "getPriceAndRatios(p1, SAP.DE, eodhd, AUTO)",
                "getCompanyProfile(p1, SAP.DE, yahoo, AUTO)",
                "getCompanyProfile(p1, SAP.DE, null, AUTO)",
                "getBalanceSheet(p1, SAP.DE, yahoo, AUTO)",
            ),
            companyDataCalls(),
        )
        assertEquals(yahooId, viewModel.state.companyProfile.selectedSource)
        assertEquals(yahooId, viewModel.state.balanceSheet.selectedSource)
    }

    @Test
    fun `header comes from the default source profile when the profile tab has no default option`() = runTest {
        apiClient.getCompanySources = { _, _ -> ApiResult.Success(internationalSources) }
        val defaultProfile = CompanyData.companyProfileData(
            CompanyData.companyProfile(symbol = "SAP.DE", name = "SAP SE", type = "Common Stock", homeExchange = false),
        )
        apiClient.getCompanyProfile = { _, _, source, _ -> ApiResult.Success(if (source == null) defaultProfile else yahooProfile) }
        val viewModel = viewModel(DetailRoute(sap.symbol, sap))

        assertEquals("Common Stock", viewModel.state.header?.type)
        assertTrue(viewModel.state.header?.stock == true)
        assertFalse(viewModel.state.homeExchange)
        assertEquals(yahooProfile, viewModel.state.companyProfile.data.value)
    }

    @Test
    fun `header keeps the default source profile when another profile source is selected`() = runTest {
        val viewModel = viewModel(DetailRoute(Symbol("AAPL")))

        viewModel.onSourceSelected(DetailTab.COMPANY_PROFILE, yahooId)

        assertEquals(yahooProfile, viewModel.state.companyProfile.data.value)
        assertEquals("Common Stock", viewModel.state.header?.type)
        assertFalse(viewModel.state.homeExchange)
        assertEquals(
            listOf("getCompanyProfile(p1, AAPL, finnhub, AUTO)", "getCompanyProfile(p1, AAPL, yahoo, AUTO)"),
            callsOf("getCompanyProfile"),
        )
    }

    @Test
    fun `refresh with another profile source selected refreshes the header's default source profile too`() = runTest {
        val viewModel = viewModel()
        viewModel.onSourceSelected(DetailTab.COMPANY_PROFILE, yahooId)
        apiClient.calls.clear()

        viewModel.refresh()

        assertEquals(
            setOf("getCompanyProfile(p1, AAPL, yahoo, MANUAL)", "getCompanyProfile(p1, AAPL, finnhub, MANUAL)"),
            callsOf("getCompanyProfile").toSet(),
        )
        assertEquals(yahooProfile, viewModel.state.companyProfile.data.value)
        assertEquals("Common Stock", viewModel.state.header?.type)
    }

    @Test
    fun `profile tab without a source keeps the header and the tags from the default source profile`() = runTest {
        val king = Tag("chess-king", "Dividend king")
        apiClient.getCompanySources = { _, _ -> ApiResult.Success(usSources.copy(profile = emptyList())) }
        apiClient.getCompanyProfile = { _, _, _, _ ->
            ApiResult.Success(CompanyData.companyProfileData(profile = null, status = DataStatus.NOT_SUPPORTED, tags = listOf(king)))
        }
        apiClient.getWatchlist = { ApiResult.Success(listOf(Companies.item("AAPL"))) }
        val viewModel = viewModel(DetailRoute(apple.symbol, apple, initiallyInWatchlist = true))
        watchlistRepository.refresh()

        assertEquals(listOf("getCompanyProfile(p1, AAPL, null, AUTO)"), callsOf("getCompanyProfile"))
        assertTrue(viewModel.state.companyProfile.hasNoSource)
        assertEquals(listOf(king), viewModel.state.tags.tags)
        assertEquals(apple, viewModel.state.header)

        viewModel.refresh()
        assertEquals("getCompanyProfile(p1, AAPL, null, MANUAL)", callsOf("getCompanyProfile").last())
    }

    @Test
    fun `header price follows the selected price source`() = runTest {
        val yahooQuote = Companies.quote(price = 99.0)
        apiClient.getPriceAndRatios = { _, _, source, _ ->
            ApiResult.Success(if (source == yahooId) CompanyData.priceAndRatios(quote = yahooQuote) else CompanyData.priceAndRatios())
        }
        val viewModel = viewModel()

        viewModel.onSourceSelected(DetailTab.PRICE_AND_RATIOS, yahooId)

        assertEquals(yahooQuote, viewModel.state.headerQuote)
    }

    @Test
    fun `company profile is requested on open with an initial summary and the price tab selected`() = runTest {
        val response = CompletableDeferred<ApiResult<CompanyProfileData>>()
        apiClient.getCompanyProfile = { _, _, _, _ -> response.await() }

        val viewModel = viewModel()

        assertEquals(DetailTab.PRICE_AND_RATIOS, viewModel.state.selectedTab)
        assertEquals(listOf("getCompanyProfile(p1, AAPL, finnhub, AUTO)"), callsOf("getCompanyProfile"))
        assertTrue(viewModel.state.companyProfile.data.isLoading)
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

        assertEquals(listOf("getCompanyProfile(p1, AAPL, finnhub, AUTO)"), callsOf("getCompanyProfile"))
        assertEquals(CompanyData.companyProfileData(), viewModel.state.companyProfile.data.value)
    }

    @Test
    fun `opening the profile tab while the profile loads on open sends no second request`() = runTest {
        val response = CompletableDeferred<ApiResult<CompanyProfileData>>()
        apiClient.getCompanyProfile = { _, _, _, _ -> response.await() }
        val viewModel = viewModel()

        viewModel.onTabSelected(DetailTab.COMPANY_PROFILE)
        response.complete(ApiResult.Success(CompanyData.companyProfileData()))

        assertEquals(1, callsOf("getCompanyProfile").size)
        assertEquals(CompanyData.companyProfileData(), viewModel.state.companyProfile.data.value)
    }

    @Test
    fun `failed profile on open keeps the route header without the home exchange mark and the price tab`() = runTest {
        apiClient.getCompanyProfile = { _, _, _, _ -> ApiResult.Failure(ApiError.Network(isTimeout = false)) }

        val viewModel = viewModel()

        assertEquals(apple, viewModel.state.header)
        assertFalse(viewModel.state.homeExchange)
        assertEquals(CompanyData.priceAndRatios(), viewModel.state.priceAndRatios.data.value)
        assertNull(viewModel.state.priceAndRatios.data.error)
        assertEquals(ApiError.Network(isTimeout = false), viewModel.state.companyProfile.data.error)
    }

    @Test
    fun `header without a summary is loaded from the company profile`() = runTest {
        val viewModel = viewModel(DetailRoute(Symbol("AAPL")))

        assertEquals(listOf("getCompanyProfile(p1, AAPL, finnhub, AUTO)"), callsOf("getCompanyProfile"))
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
        assertEquals(ApiError.Network(isTimeout = true), viewModel.state.companyProfile.data.error)
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
        val viewModel = viewModel(DetailRoute(sap.symbol, sap))
        viewModel.onTabSelected(DetailTab.BALANCE_SHEET)

        viewModel.onSourceSelected(DetailTab.BALANCE_SHEET, SourceId.SEC_EDGAR)

        val view = viewModel.state.balanceSheet.data.value
        assertEquals(DataStatus.NOT_SUPPORTED, view?.meta?.status)
        assertTrue(view?.table?.isEmpty == true)
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

        assertEquals(CompanyData.priceAndRatios(), viewModel.state.priceAndRatios.data.value)
        assertEquals(ApiError.Network(isTimeout = false), viewModel.state.priceAndRatios.data.error)
    }

    @Test
    fun `tab shows loading while its request runs`() = runTest {
        val response = CompletableDeferred<ApiResult<CompanyProfileData>>()
        apiClient.getCompanyProfile = { _, _, _, _ -> response.await() }
        val viewModel = viewModel()

        viewModel.onTabSelected(DetailTab.COMPANY_PROFILE)
        assertTrue(viewModel.state.companyProfile.data.isLoading)

        response.complete(ApiResult.Success(CompanyData.companyProfileData()))
        assertFalse(viewModel.state.companyProfile.data.isLoading)
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

        assertEquals("Fresh", viewModel.state.companyProfile.data.value?.profile?.name)
    }

    @Test
    fun `every status is kept with cached data and with empty data`() = runTest {
        DataStatus.entries.forEach { status ->
            apiClient.getPriceAndRatios = { _, _, _, _ -> ApiResult.Success(CompanyData.priceAndRatios(quote = null, ratios = emptyList(), status = status)) }
            val viewModel = viewModel()

            assertEquals(status, viewModel.state.priceAndRatios.data.value?.meta?.status)
            assertNull(viewModel.state.priceAndRatios.data.value?.quote)
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
