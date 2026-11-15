package com.sixteenscreener.app.ui.watchlist

import com.sixteenscreener.app.data.repository.DefaultWatchlistRepository
import com.sixteenscreener.app.domain.MarketFilter
import com.sixteenscreener.app.domain.VisibleRows
import com.sixteenscreener.app.domain.WatchlistFilter
import com.sixteenscreener.app.domain.model.ActiveProfile
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.Market
import com.sixteenscreener.app.domain.model.MarketData
import com.sixteenscreener.app.domain.model.MarketDataLoad
import com.sixteenscreener.app.domain.model.ProfileId
import com.sixteenscreener.app.domain.model.WatchlistItem
import com.sixteenscreener.app.testing.Companies.item
import com.sixteenscreener.app.testing.Companies.marketData
import com.sixteenscreener.app.testing.Companies.quote
import com.sixteenscreener.app.testing.FakeApiClient
import com.sixteenscreener.app.testing.FakeProfileRepository
import com.sixteenscreener.app.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchlistViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val apiClient = FakeApiClient()
    private val profileRepository = FakeProfileRepository(ActiveProfile(ProfileId("p1"), "Viktor"))

    private val apple = item("AAPL", market = Market.US, sector = "Technology")
    private val sap = item("SAP.DE", name = "SAP SE", market = Market.EU, sector = "Technology")
    private val shell = item("SHEL", name = "Shell", market = Market.EU, sector = "Energy")
    private val unknownMarket = item("7203.T", name = "Toyota", market = null, sector = null)

    private fun TestScope.viewModel(): WatchlistViewModel =
        WatchlistViewModel(DefaultWatchlistRepository(apiClient, profileRepository, backgroundScope), profileRepository)

    private var stubbedWatchlist = emptyList<WatchlistItem>()

    private fun stubWatchlist(vararg companies: WatchlistItem) {
        stubbedWatchlist = companies.toList()
        apiClient.getWatchlist = { ApiResult.Success(companies.toList()) }
    }

    private val WatchlistViewModel.state get() = uiState.value

    private val refreshRequests = mutableListOf<List<String>>()

    private fun rows(count: Int, market: (Int) -> Market = { Market.US }) =
        (0 until count).map { item("S$it", name = "Company $it", market = market(it)) }

    private fun stubRowRefresh(result: (List<MarketData>) -> ApiResult<List<MarketData>> = { ApiResult.Success(it) }) {
        apiClient.getWatchlistMarketData = { _, symbols ->
            refreshRequests += symbols.map { it.value }
            result(stubbedWatchlist.filter { it.symbol in symbols }.map { marketData(it.symbol.value, quote = quote(price = 1.0)) })
        }
    }

    private fun WatchlistViewModel.priceOf(index: Int): Double? = state.marketData[state.companies[index].symbol]?.quote?.price

    private fun symbols(range: IntRange) = range.map { "S$it" }

    private fun TestScope.showRows(viewModel: WatchlistViewModel, first: Int, last: Int) {
        viewModel.onVisibleRowsChanged(VisibleRows(first, last))
        advanceUntilIdle()
    }

    @Test
    fun `nothing is loaded before the tab is shown`() = runTest {
        val viewModel = viewModel()

        assertFalse(viewModel.state.isLoaded)
        assertTrue(apiClient.calls.isEmpty())
    }

    @Test
    fun `empty watchlist is loaded`() = runTest {
        stubWatchlist()
        val viewModel = viewModel()

        viewModel.refresh()

        assertTrue(viewModel.state.isLoaded)
        assertFalse(viewModel.state.hasCompanies)
        assertEquals(emptyList<WatchlistItem>(), viewModel.state.companies)
        assertFalse(viewModel.state.isRefreshing)
    }

    @Test
    fun `refresh shows the loading state until the backend answers and ignores repeated requests`() = runTest {
        val response = CompletableDeferred<ApiResult<List<WatchlistItem>>>()
        apiClient.getWatchlist = { response.await() }
        val viewModel = viewModel()

        viewModel.refresh()
        viewModel.refresh()

        assertTrue(viewModel.state.isRefreshing)
        assertEquals(1, apiClient.calls.size)
        response.complete(ApiResult.Success(listOf(apple)))
        assertFalse(viewModel.state.isRefreshing)
        assertEquals(listOf(apple), viewModel.state.companies)
    }

    @Test
    fun `market filter shows only companies of the market`() = runTest {
        stubWatchlist(apple, sap, shell, unknownMarket)
        val viewModel = viewModel()
        viewModel.refresh()

        viewModel.onMarketSelected(MarketFilter.US)
        assertEquals(listOf(apple), viewModel.state.companies)

        viewModel.onMarketSelected(MarketFilter.EU)
        assertEquals(listOf(sap, shell), viewModel.state.companies)

        viewModel.onMarketSelected(MarketFilter.ALL)
        assertEquals(listOf(apple, sap, shell, unknownMarket), viewModel.state.companies)
    }

    @Test
    fun `sector filter combines with the market filter`() = runTest {
        stubWatchlist(apple, sap, shell, unknownMarket)
        val viewModel = viewModel()
        viewModel.refresh()

        assertEquals(listOf("Energy", "Technology"), viewModel.state.sectors)

        viewModel.onSectorSelected("Technology")
        assertEquals(listOf(apple, sap), viewModel.state.companies)

        viewModel.onMarketSelected(MarketFilter.EU)
        assertEquals(listOf(sap), viewModel.state.companies)
        assertTrue(viewModel.state.hasCompanies)

        viewModel.onSectorSelected(null)
        assertEquals(listOf(sap, shell), viewModel.state.companies)
    }

    @Test
    fun `no match keeps the companies flag for the no match hint`() = runTest {
        stubWatchlist(unknownMarket)
        val viewModel = viewModel()
        viewModel.refresh()

        viewModel.onMarketSelected(MarketFilter.US)

        assertEquals(emptyList<WatchlistItem>(), viewModel.state.companies)
        assertTrue(viewModel.state.hasCompanies)
    }

    @Test
    fun `sector filter is reset when the selected sector disappears after a reload`() = runTest {
        stubWatchlist(apple, shell)
        val viewModel = viewModel()
        viewModel.refresh()
        viewModel.onMarketSelected(MarketFilter.EU)
        viewModel.onSectorSelected("Energy")

        stubWatchlist(apple, sap)
        viewModel.refresh()

        assertEquals(WatchlistFilter(market = MarketFilter.EU, sector = null), viewModel.state.filter)
        assertEquals(listOf(sap), viewModel.state.companies)
        assertEquals(listOf("Technology"), viewModel.state.sectors)
    }

    @Test
    fun `selected sector stays offered after its last company is removed`() = runTest {
        stubWatchlist(apple, shell)
        apiClient.removeFromWatchlist = { _, _ -> ApiResult.Success(Unit) }
        val viewModel = viewModel()
        viewModel.refresh()
        viewModel.onSectorSelected("Energy")

        viewModel.onRemove(shell)

        assertEquals("Energy", viewModel.state.filter.sector)
        assertEquals(listOf("Technology", "Energy"), viewModel.state.sectors)
    }

    @Test
    fun `remove hides the row and offers undo which adds it back at its position`() = runTest {
        stubWatchlist(apple, sap, shell)
        apiClient.removeFromWatchlist = { _, _ -> ApiResult.Success(Unit) }
        apiClient.addToWatchlist = { _, symbol -> ApiResult.Success(stubbedWatchlist.first { it.symbol == symbol }) }
        val viewModel = viewModel()
        viewModel.refresh()

        viewModel.onRemove(sap)

        assertEquals(listOf(apple, shell), viewModel.state.companies)
        val removed = WatchlistMessage.Removed(sap, 1)
        assertEquals(removed, viewModel.state.message)

        viewModel.onUndo(removed)

        assertEquals(listOf(apple, sap, shell), viewModel.state.companies)
        assertNull(viewModel.state.message)
        assertEquals(
            listOf("getWatchlist(p1)", "removeFromWatchlist(p1, SAP.DE)", "addToWatchlist(p1, SAP.DE)"),
            apiClient.calls,
        )
    }

    @Test
    fun `remove failure restores the row with an error message`() = runTest {
        stubWatchlist(apple, sap)
        val failure = ApiError.Network(isTimeout = false)
        apiClient.removeFromWatchlist = { _, _ -> ApiResult.Failure(failure) }
        val viewModel = viewModel()
        viewModel.refresh()

        viewModel.onRemove(apple)

        assertEquals(listOf(apple, sap), viewModel.state.companies)
        assertEquals(WatchlistMessage.RemoveFailed(apple, failure), viewModel.state.message)
    }

    @Test
    fun `undo failure leaves the row removed with an error message`() = runTest {
        stubWatchlist(apple)
        val failure = ApiError.Http(500, null)
        apiClient.removeFromWatchlist = { _, _ -> ApiResult.Success(Unit) }
        apiClient.addToWatchlist = { _, _ -> ApiResult.Failure(failure) }
        val viewModel = viewModel()
        viewModel.refresh()

        viewModel.onRemove(apple)
        viewModel.onUndo(WatchlistMessage.Removed(apple, 0))

        assertEquals(emptyList<WatchlistItem>(), viewModel.state.companies)
        assertEquals(WatchlistMessage.UndoFailed(apple, failure), viewModel.state.message)
    }

    @Test
    fun `load error keeps the previous list with an error message`() = runTest {
        stubWatchlist(apple, sap)
        val viewModel = viewModel()
        viewModel.refresh()
        val failure = ApiError.Network(isTimeout = true)
        apiClient.getWatchlist = { ApiResult.Failure(failure) }

        viewModel.refresh()

        assertEquals(listOf(apple, sap), viewModel.state.companies)
        assertEquals(failure, viewModel.state.loadError)
        assertEquals(WatchlistMessage.LoadFailed(failure), viewModel.state.message)
        assertFalse(viewModel.state.isRefreshing)
    }

    @Test
    fun `first load error leaves the watchlist not loaded`() = runTest {
        apiClient.getWatchlist = { ApiResult.Failure(ApiError.Unauthorized) }
        val viewModel = viewModel()

        viewModel.refresh()

        assertFalse(viewModel.state.isLoaded)
        assertEquals(ApiError.Unauthorized, viewModel.state.loadError)
    }

    @Test
    fun `successful reload clears the load error`() = runTest {
        apiClient.getWatchlist = { ApiResult.Failure(ApiError.Unauthorized) }
        val viewModel = viewModel()
        viewModel.refresh()
        stubWatchlist(apple)

        viewModel.refresh()

        assertNull(viewModel.state.loadError)
    }

    @Test
    fun `shown message is cleared unless a newer one replaced it`() = runTest {
        stubWatchlist(apple, sap)
        apiClient.removeFromWatchlist = { _, _ -> ApiResult.Success(Unit) }
        val viewModel = viewModel()
        viewModel.refresh()
        viewModel.onRemove(apple)
        val first = viewModel.state.message!!
        viewModel.onRemove(sap)

        viewModel.onMessageShown(first)
        assertEquals(WatchlistMessage.Removed(sap, 0), viewModel.state.message)

        viewModel.onMessageShown(viewModel.state.message!!)
        assertNull(viewModel.state.message)
    }

    @Test
    fun `profile change resets the filters and reloads the watchlist with the new profile ID`() = runTest {
        apiClient.getWatchlist = { profileId -> ApiResult.Success(if (profileId.value == "p1") listOf(apple, shell) else listOf(sap)) }
        val viewModel = viewModel()
        viewModel.refresh()
        viewModel.onMarketSelected(MarketFilter.US)
        viewModel.onSectorSelected("Technology")
        apiClient.calls.clear()

        profileRepository.currentProfile.value = ActiveProfile(ProfileId("p2"), "Anna")
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("getWatchlist(p2)"), apiClient.calls)
        assertEquals(WatchlistFilter(), viewModel.state.filter)
        assertEquals(listOf(sap), viewModel.state.companies)
        assertTrue(viewModel.state.isLoaded)
    }

    @Test
    fun `profile change drops the previous error and message`() = runTest {
        apiClient.getWatchlist = { ApiResult.Failure(ApiError.Network(isTimeout = false)) }
        val viewModel = viewModel()
        viewModel.refresh()
        apiClient.getWatchlist = { ApiResult.Success(emptyList()) }

        profileRepository.currentProfile.value = ActiveProfile(ProfileId("p2"), "Anna")
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.state.loadError)
        assertNull(viewModel.state.message)
        assertTrue(viewModel.state.isLoaded)
    }

    @Test
    fun `load of the previous profile still running is replaced by the new profile's load`() = runTest {
        val previousLoad = CompletableDeferred<ApiResult<List<WatchlistItem>>>()
        apiClient.getWatchlist = { profileId -> if (profileId.value == "p1") previousLoad.await() else ApiResult.Success(listOf(sap)) }
        val viewModel = viewModel()
        viewModel.refresh()

        profileRepository.currentProfile.value = ActiveProfile(ProfileId("p2"), "Anna")
        testScheduler.advanceUntilIdle()
        previousLoad.complete(ApiResult.Success(listOf(apple)))

        assertEquals(listOf(sap), viewModel.state.companies)
        assertFalse(viewModel.state.isRefreshing)
    }

    @Test
    fun `shown watchlist refreshes the visible rows plus the buffer once the range settles`() = runTest {
        stubWatchlist(*rows(20).toTypedArray())
        stubRowRefresh()
        val viewModel = viewModel()
        viewModel.refresh()

        viewModel.onVisibleRowsChanged(VisibleRows(0, 4))
        advanceTimeBy(200)
        assertTrue(refreshRequests.isEmpty())
        advanceUntilIdle()

        assertEquals(listOf(symbols(0..9)), refreshRequests)
        assertEquals(1.0, viewModel.priceOf(0))
        assertNull(viewModel.state.marketData[viewModel.state.companies[10].symbol])
    }

    @Test
    fun `fast scrolling sends one request for the settled range`() = runTest {
        stubWatchlist(*rows(40).toTypedArray())
        stubRowRefresh()
        val viewModel = viewModel()
        viewModel.refresh()

        (0..20).forEach { first ->
            viewModel.onVisibleRowsChanged(VisibleRows(first, first + 4))
            advanceTimeBy(16)
        }
        advanceUntilIdle()

        assertEquals(listOf(symbols(20..29)), refreshRequests)
    }

    @Test
    fun `scrolling refreshes only rows not refreshed since the load`() = runTest {
        stubWatchlist(*rows(20).toTypedArray())
        stubRowRefresh()
        val viewModel = viewModel()
        viewModel.refresh()
        showRows(viewModel, 0, 4)

        showRows(viewModel, 3, 7)
        showRows(viewModel, 0, 4)

        assertEquals(listOf(symbols(0..9), symbols(10..12)), refreshRequests)
    }

    @Test
    fun `few rows are refreshed without a buffer beyond the end`() = runTest {
        stubWatchlist(*rows(3).toTypedArray())
        stubRowRefresh()
        val viewModel = viewModel()
        viewModel.refresh()

        showRows(viewModel, 0, 2)

        assertEquals(listOf(symbols(0..2)), refreshRequests)
    }

    @Test
    fun `empty watchlist sends no row refresh`() = runTest {
        stubWatchlist()
        val viewModel = viewModel()
        viewModel.refresh()

        showRows(viewModel, 0, 4)

        assertEquals(listOf("getWatchlist(p1)"), apiClient.calls)
    }

    @Test
    fun `filter change refreshes all displayed rows plus the buffer again`() = runTest {
        stubWatchlist(*rows(20) { if (it % 2 == 0) Market.US else Market.EU }.toTypedArray())
        stubRowRefresh()
        val viewModel = viewModel()
        viewModel.refresh()
        showRows(viewModel, 0, 9)

        viewModel.onMarketSelected(MarketFilter.US)
        advanceUntilIdle()

        assertEquals(listOf(symbols(0..14), (0..19 step 2).map { "S$it" }), refreshRequests)
    }

    @Test
    fun `reload refreshes the displayed rows again`() = runTest {
        stubWatchlist(*rows(20).toTypedArray())
        stubRowRefresh()
        val viewModel = viewModel()
        viewModel.refresh()
        showRows(viewModel, 0, 4)

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(listOf(symbols(0..9), symbols(0..9)), refreshRequests)
    }

    @Test
    fun `more than 50 rows are refreshed in chunks of 50`() = runTest {
        stubWatchlist(*rows(70).toTypedArray())
        stubRowRefresh()
        val viewModel = viewModel()
        viewModel.refresh()

        showRows(viewModel, 0, 59)

        assertEquals(listOf(symbols(0..49), symbols(50..64)), refreshRequests)
    }

    @Test
    fun `failed rows keep the cached data and are retried when next in view`() = runTest {
        stubWatchlist(*rows(20).toTypedArray())
        stubRowRefresh { ApiResult.Failure(ApiError.Network(isTimeout = true)) }
        val viewModel = viewModel()
        viewModel.refresh()
        showRows(viewModel, 0, 4)
        assertEquals(MarketDataLoad.Failed, viewModel.state.marketData[viewModel.state.companies[0].symbol]?.lastLoad)
        assertNull(viewModel.state.message)

        stubRowRefresh()
        showRows(viewModel, 1, 5)

        assertEquals(listOf(symbols(0..9), symbols(1..10)), refreshRequests)
    }

    @Test
    fun `rows in flight are not requested again`() = runTest {
        stubWatchlist(*rows(20) { if (it < 3) Market.US else Market.EU }.toTypedArray())
        val response = CompletableDeferred<ApiResult<List<MarketData>>>()
        apiClient.getWatchlistMarketData = { _, symbols ->
            refreshRequests += symbols.map { it.value }
            if (refreshRequests.size == 1) response.await() else ApiResult.Success(emptyList())
        }
        val viewModel = viewModel()
        viewModel.refresh()
        showRows(viewModel, 0, 4)

        viewModel.onMarketSelected(MarketFilter.EU)
        advanceUntilIdle()
        response.complete(ApiResult.Success(emptyList()))

        assertEquals(listOf(symbols(0..9), symbols(10..12)), refreshRequests)
    }

    @Test
    fun `removing a row refreshes the row moving into the buffer`() = runTest {
        stubWatchlist(*rows(20).toTypedArray())
        stubRowRefresh()
        apiClient.removeFromWatchlist = { _, _ -> ApiResult.Success(Unit) }
        val viewModel = viewModel()
        viewModel.refresh()
        showRows(viewModel, 0, 4)

        viewModel.onRemove(viewModel.state.companies[0])
        advanceUntilIdle()

        assertEquals(listOf(symbols(0..9), listOf("S10")), refreshRequests)
    }

    @Test
    fun `profile change forgets the refreshed rows`() = runTest {
        stubWatchlist(*rows(20).toTypedArray())
        stubRowRefresh()
        val viewModel = viewModel()
        viewModel.refresh()
        showRows(viewModel, 0, 4)

        profileRepository.currentProfile.value = ActiveProfile(ProfileId("p2"), "Anna")
        advanceUntilIdle()

        assertEquals(listOf(symbols(0..9), symbols(0..9)), refreshRequests)
    }

    @Test
    fun `rows are marked as loading while their market data is requested`() = runTest {
        stubWatchlist(*rows(3).toTypedArray())
        val response = CompletableDeferred<ApiResult<List<MarketData>>>()
        apiClient.getWatchlistMarketData = { _, _ -> response.await() }
        val viewModel = viewModel()
        viewModel.refresh()

        showRows(viewModel, 0, 2)

        assertEquals(List(3) { MarketDataLoad.Loading }, viewModel.state.companies.map { viewModel.state.marketData[it.symbol]?.lastLoad })
        response.complete(ApiResult.Success(emptyList()))
    }

    @Test
    fun `market data is kept when the watchlist is reloaded`() = runTest {
        stubWatchlist(*rows(3).toTypedArray())
        stubRowRefresh()
        val viewModel = viewModel()
        viewModel.refresh()
        showRows(viewModel, 0, 2)
        val response = CompletableDeferred<ApiResult<List<MarketData>>>()
        apiClient.getWatchlistMarketData = { _, _ -> response.await() }

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(1.0, viewModel.priceOf(0))
        response.complete(ApiResult.Success(emptyList()))
    }

    @Test
    fun `undone removal loads the market data of the row again`() = runTest {
        stubWatchlist(*rows(3).toTypedArray())
        stubRowRefresh()
        apiClient.removeFromWatchlist = { _, _ -> ApiResult.Success(Unit) }
        apiClient.addToWatchlist = { _, symbol -> ApiResult.Success(stubbedWatchlist.first { it.symbol == symbol }) }
        val viewModel = viewModel()
        viewModel.refresh()
        showRows(viewModel, 0, 2)

        viewModel.onRemove(viewModel.state.companies[0])
        advanceUntilIdle()
        viewModel.onUndo(viewModel.state.message as WatchlistMessage.Removed)
        advanceUntilIdle()

        assertEquals(listOf(symbols(0..2), listOf("S0")), refreshRequests)
        assertEquals(1.0, viewModel.priceOf(0))
    }

    @Test
    fun `sector filter uses the sector of the watchlist entries`() = runTest {
        stubWatchlist(apple, shell)
        val viewModel = viewModel()
        viewModel.refresh()

        viewModel.onSectorSelected("Energy")

        assertEquals(listOf(shell), viewModel.state.companies)
    }
}
