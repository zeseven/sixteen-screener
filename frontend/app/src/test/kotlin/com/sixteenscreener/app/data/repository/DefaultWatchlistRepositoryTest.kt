package com.sixteenscreener.app.data.repository

import com.sixteenscreener.app.domain.model.ActiveProfile
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.DataStatus
import com.sixteenscreener.app.domain.model.MarketData
import com.sixteenscreener.app.domain.model.MarketDataLoad
import com.sixteenscreener.app.domain.model.ProfileId
import com.sixteenscreener.app.domain.model.RowMarketData
import com.sixteenscreener.app.domain.model.Symbol
import com.sixteenscreener.app.domain.model.WatchlistItem
import com.sixteenscreener.app.testing.Companies.item
import com.sixteenscreener.app.testing.Companies.marketData
import com.sixteenscreener.app.testing.Companies.quote
import com.sixteenscreener.app.testing.FakeApiClient
import com.sixteenscreener.app.testing.FakeProfileRepository
import com.sixteenscreener.app.testing.FakeWatchlistStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultWatchlistRepositoryTest {

    private val profile = ActiveProfile(ProfileId("p1"), "Viktor")
    private val apiClient = FakeApiClient()
    private val profileRepository = FakeProfileRepository(profile)
    private val store = FakeWatchlistStore()

    private val apple = item("AAPL")
    private val sap = item("SAP.DE", name = "SAP SE")
    private val microsoft = item("MSFT", name = "Microsoft", sector = null)
    private val storedMicrosoft = microsoft.copy(sector = "Technology")

    private fun TestScope.repository() = DefaultWatchlistRepository(apiClient, profileRepository, store, backgroundScope)

    private fun runRepositoryTest(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher()) { block() }

    private fun stubWatchlist(vararg companies: WatchlistItem) {
        apiClient.getWatchlist = { ApiResult.Success(companies.toList()) }
    }

    private fun stubMarketData(vararg rows: MarketData) {
        apiClient.getWatchlistMarketData = { _, _ -> ApiResult.Success(rows.toList()) }
    }

    private fun loaded(row: MarketData) = RowMarketData(row.quote, row.indicators, MarketDataLoad.Loaded(row.status))

    @Test
    fun `nothing is known before the first load`() = runRepositoryTest {
        val repository = repository()

        assertNull(repository.watchlist.value)
        assertEquals(emptySet<Symbol>(), repository.watchlistSymbols.value)
        assertEquals(emptyMap<Symbol, RowMarketData>(), repository.marketData.value)
    }

    @Test
    fun `refresh loads the watchlist of the active profile`() = runRepositoryTest {
        stubWatchlist(apple, sap)
        val repository = repository()

        repository.refresh()

        assertEquals(listOf("getWatchlist(p1)"), apiClient.calls)
        assertEquals(listOf(apple, sap), repository.watchlist.value)
        assertEquals(setOf(apple.symbol, sap.symbol), repository.watchlistSymbols.value)
    }

    @Test
    fun `failed refresh keeps the previous list`() = runRepositoryTest {
        stubWatchlist(apple)
        val repository = repository()
        repository.refresh()
        apiClient.getWatchlist = { ApiResult.Failure(ApiError.Network(isTimeout = true)) }

        val result = repository.refresh()

        assertEquals(ApiResult.Failure(ApiError.Network(isTimeout = true)), result)
        assertEquals(listOf(apple), repository.watchlist.value)
    }

    @Test
    fun `refresh without a profile fails without a request`() = runRepositoryTest {
        profileRepository.currentProfile.value = null
        val repository = repository()

        assertEquals(ApiResult.Failure(ApiError.NoProfile), repository.refresh())
        assertTrue(apiClient.calls.isEmpty())
    }

    @Test
    fun `not found clears the stored profile`() = runRepositoryTest {
        apiClient.getWatchlist = { ApiResult.Failure(ApiError.NotFound(null)) }
        val repository = repository()

        repository.refresh()

        assertNull(profileRepository.currentProfile.value)
    }

    @Test
    fun `loadMarketData stores the received rows and requests the symbols in the given order`() = runRepositoryTest {
        stubWatchlist(apple, sap, microsoft)
        val appleData = marketData("AAPL", quote = quote(price = 200.0))
        val microsoftData = marketData("MSFT", status = DataStatus.CACHED, quote = quote(price = 400.0))
        stubMarketData(appleData, microsoftData)
        val repository = repository()
        repository.refresh()

        val result = repository.loadMarketData(listOf(microsoft.symbol, apple.symbol))

        assertEquals(ApiResult.Success(listOf(appleData, microsoftData)), result)
        assertEquals(mapOf(apple.symbol to loaded(appleData), microsoft.symbol to loaded(microsoftData)), repository.marketData.value)
        assertEquals(listOf(apple, sap, microsoft), repository.watchlist.value)
        assertEquals("getWatchlistMarketData(p1, MSFT,AAPL)", apiClient.calls.last())
    }

    @Test
    fun `loadMarketData marks the rows as loading until the response arrives`() = runRepositoryTest {
        stubWatchlist(apple, sap)
        val response = CompletableDeferred<ApiResult<List<MarketData>>>()
        apiClient.getWatchlistMarketData = { _, _ -> response.await() }
        val repository = repository()
        repository.refresh()

        val call = async { repository.loadMarketData(listOf(apple.symbol)) }

        assertEquals(mapOf(apple.symbol to RowMarketData()), repository.marketData.value)
        val appleData = marketData("AAPL")
        response.complete(ApiResult.Success(listOf(appleData)))
        call.await()
        assertEquals(mapOf(apple.symbol to loaded(appleData)), repository.marketData.value)
    }

    @Test
    fun `reloading market data keeps the previous values while loading`() = runRepositoryTest {
        stubWatchlist(apple)
        val first = marketData("AAPL", quote = quote(price = 200.0))
        stubMarketData(first)
        val repository = repository()
        repository.refresh()
        repository.loadMarketData(listOf(apple.symbol))
        val response = CompletableDeferred<ApiResult<List<MarketData>>>()
        apiClient.getWatchlistMarketData = { _, _ -> response.await() }

        val call = async { repository.loadMarketData(listOf(apple.symbol)) }

        assertEquals(RowMarketData(first.quote, lastLoad = MarketDataLoad.Loading), repository.marketData.value[apple.symbol])
        response.complete(ApiResult.Failure(ApiError.Network(isTimeout = true)))
        call.await()
    }

    @Test
    fun `failed loadMarketData marks the rows as failed and keeps the previous values`() = runRepositoryTest {
        stubWatchlist(apple, sap)
        val appleData = marketData("AAPL", quote = quote(price = 200.0))
        stubMarketData(appleData)
        val repository = repository()
        repository.refresh()
        repository.loadMarketData(listOf(apple.symbol))
        apiClient.getWatchlistMarketData = { _, _ -> ApiResult.Failure(ApiError.Network(isTimeout = false)) }

        val result = repository.loadMarketData(listOf(apple.symbol, sap.symbol))

        assertEquals(ApiResult.Failure(ApiError.Network(isTimeout = false)), result)
        assertEquals(
            mapOf(
                apple.symbol to RowMarketData(appleData.quote, lastLoad = MarketDataLoad.Failed),
                sap.symbol to RowMarketData(lastLoad = MarketDataLoad.Failed),
            ),
            repository.marketData.value,
        )
    }

    @Test
    fun `error response of the backend marks the rows as failed`() = runRepositoryTest {
        stubWatchlist(apple)
        apiClient.getWatchlistMarketData = { _, _ -> ApiResult.Failure(ApiError.Http(500, null)) }
        val repository = repository()
        repository.refresh()

        repository.loadMarketData(listOf(apple.symbol))

        assertEquals(RowMarketData(lastLoad = MarketDataLoad.Failed), repository.marketData.value[apple.symbol])
    }

    @Test
    fun `limit reached keeps the previous quote when no cached quote is returned`() = runRepositoryTest {
        stubWatchlist(apple)
        val first = marketData("AAPL", quote = quote(price = 200.0))
        stubMarketData(first)
        val repository = repository()
        repository.refresh()
        repository.loadMarketData(listOf(apple.symbol))
        stubMarketData(marketData("AAPL", status = DataStatus.LIMIT_REACHED, quote = null))

        repository.loadMarketData(listOf(apple.symbol))

        assertEquals(
            RowMarketData(first.quote, lastLoad = MarketDataLoad.Loaded(DataStatus.LIMIT_REACHED)),
            repository.marketData.value[apple.symbol],
        )
    }

    @Test
    fun `rows skipped by the backend are marked as failed`() = runRepositoryTest {
        stubWatchlist(apple, sap)
        val appleData = marketData("AAPL")
        stubMarketData(appleData)
        val repository = repository()
        repository.refresh()

        repository.loadMarketData(listOf(apple.symbol, sap.symbol))

        assertEquals(
            mapOf(apple.symbol to loaded(appleData), sap.symbol to RowMarketData(lastLoad = MarketDataLoad.Failed)),
            repository.marketData.value,
        )
    }

    @Test
    fun `loadMarketData ignores rows not in the watchlist`() = runRepositoryTest {
        stubWatchlist(apple)
        stubMarketData(marketData("MSFT"))
        val repository = repository()
        repository.refresh()

        repository.loadMarketData(listOf(microsoft.symbol))

        assertEquals(emptyMap<Symbol, RowMarketData>(), repository.marketData.value)
        assertEquals(listOf(apple), repository.watchlist.value)
    }

    @Test
    fun `loadMarketData does not bring back a row removed meanwhile`() = runRepositoryTest {
        stubWatchlist(apple, sap)
        val response = CompletableDeferred<ApiResult<List<MarketData>>>()
        apiClient.getWatchlistMarketData = { _, _ -> response.await() }
        apiClient.removeFromWatchlist = { _, _ -> ApiResult.Success(Unit) }
        val repository = repository()
        repository.refresh()

        val call = async { repository.loadMarketData(listOf(apple.symbol, sap.symbol)) }
        repository.remove(apple.symbol)
        val sapData = marketData("SAP.DE", quote = quote(price = 250.0))
        response.complete(ApiResult.Success(listOf(marketData("AAPL"), sapData)))
        call.await()

        assertEquals(mapOf(sap.symbol to loaded(sapData)), repository.marketData.value)
        assertEquals(listOf(sap), repository.watchlist.value)
    }

    @Test
    fun `market data is kept across watchlist reloads`() = runRepositoryTest {
        stubWatchlist(apple, sap)
        val appleData = marketData("AAPL")
        stubMarketData(appleData)
        val repository = repository()
        repository.refresh()
        repository.loadMarketData(listOf(apple.symbol))

        repository.refresh()

        assertEquals(mapOf(apple.symbol to loaded(appleData)), repository.marketData.value)
    }

    @Test
    fun `reload drops market data of rows no longer in the watchlist`() = runRepositoryTest {
        stubWatchlist(apple, sap)
        stubMarketData(marketData("AAPL"), marketData("SAP.DE"))
        val repository = repository()
        repository.refresh()
        repository.loadMarketData(listOf(apple.symbol, sap.symbol))
        stubWatchlist(sap)

        repository.refresh()

        assertEquals(setOf(sap.symbol), repository.marketData.value.keys)
    }

    @Test
    fun `remove drops the market data of the row and a failed remove restores it`() = runRepositoryTest {
        stubWatchlist(apple, sap)
        val appleData = marketData("AAPL")
        stubMarketData(appleData)
        val response = CompletableDeferred<ApiResult<Unit>>()
        apiClient.removeFromWatchlist = { _, _ -> response.await() }
        val repository = repository()
        repository.refresh()
        repository.loadMarketData(listOf(apple.symbol))

        val call = async { repository.remove(apple.symbol) }

        assertEquals(emptyMap<Symbol, RowMarketData>(), repository.marketData.value)
        response.complete(ApiResult.Failure(ApiError.Network(isTimeout = false)))
        call.await()
        assertEquals(mapOf(apple.symbol to loaded(appleData)), repository.marketData.value)
    }

    @Test
    fun `profile change drops the market data`() = runRepositoryTest {
        stubWatchlist(apple)
        stubMarketData(marketData("AAPL"))
        val repository = repository()
        repository.refresh()
        repository.loadMarketData(listOf(apple.symbol))

        profileRepository.currentProfile.value = ActiveProfile(ProfileId("p2"), "Other")

        assertEquals(emptyMap<Symbol, RowMarketData>(), repository.marketData.value)
    }

    @Test
    fun `market data of the previous profile is not kept when the new watchlist loads before the change is observed`() = runTest {
        apiClient.getWatchlist = { ApiResult.Success(listOf(apple)) }
        stubMarketData(marketData("AAPL"))
        val repository = DefaultWatchlistRepository(apiClient, profileRepository, store, backgroundScope)
        runCurrent()
        repository.refresh()
        repository.loadMarketData(listOf(apple.symbol))

        profileRepository.currentProfile.value = ActiveProfile(ProfileId("p2"), "Other")
        repository.refresh()
        runCurrent()

        assertEquals(listOf(apple), repository.watchlist.value)
        assertEquals(emptyMap<Symbol, RowMarketData>(), repository.marketData.value)
    }

    @Test
    fun `loadMarketData of a previous profile is ignored`() = runRepositoryTest {
        apiClient.getWatchlist = { ApiResult.Success(listOf(apple)) }
        val response = CompletableDeferred<ApiResult<List<MarketData>>>()
        apiClient.getWatchlistMarketData = { _, _ -> response.await() }
        val repository = repository()
        repository.refresh()

        val call = async { repository.loadMarketData(listOf(apple.symbol)) }
        profileRepository.currentProfile.value = ActiveProfile(ProfileId("p2"), "Other")
        repository.refresh()
        response.complete(ApiResult.Success(listOf(marketData("AAPL"))))
        call.await()

        assertEquals(emptyMap<Symbol, RowMarketData>(), repository.marketData.value)
    }

    @Test
    fun `loadMarketData not found clears the stored profile`() = runRepositoryTest {
        stubWatchlist(apple)
        apiClient.getWatchlistMarketData = { _, _ -> ApiResult.Failure(ApiError.NotFound(null)) }
        val repository = repository()
        repository.refresh()

        repository.loadMarketData(listOf(apple.symbol))

        assertNull(profileRepository.currentProfile.value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `loadMarketData rejects more than 50 symbols`() = runRepositoryTest {
        repository().loadMarketData((0..50).map { Symbol("S$it") })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `loadMarketData rejects an empty symbol list`() = runRepositoryTest {
        repository().loadMarketData(emptyList())
    }

    @Test
    fun `add shows the company at once and replaces it with the stored item on success`() = runRepositoryTest {
        stubWatchlist(apple)
        val response = CompletableDeferred<ApiResult<WatchlistItem>>()
        apiClient.addToWatchlist = { _, _, _ -> response.await() }
        val repository = repository()
        repository.refresh()

        val call = async { repository.add(microsoft) }

        assertEquals(listOf(apple, microsoft), repository.watchlist.value)
        assertEquals(setOf(apple.symbol, microsoft.symbol), repository.watchlistSymbols.value)
        response.complete(ApiResult.Success(storedMicrosoft))
        assertEquals(ApiResult.Success(storedMicrosoft), call.await())
        assertEquals(listOf(apple, storedMicrosoft), repository.watchlist.value)
        assertEquals(setOf(apple.symbol, microsoft.symbol), repository.watchlistSymbols.value)
        assertEquals(listOf("getWatchlist(p1)", "addToWatchlist(p1, MSFT)"), apiClient.calls)
    }

    @Test
    fun `add sends the name of the company and the stored name replaces it`() = runRepositoryTest {
        stubWatchlist(apple)
        val sentNames = mutableListOf<String>()
        val storedSap = sap.copy(name = "SAP SE (stored)")
        apiClient.addToWatchlist = { _, _, name -> sentNames += name; ApiResult.Success(storedSap) }
        val repository = repository()
        repository.refresh()

        repository.add(sap)

        assertEquals(listOf("SAP SE"), sentNames)
        assertEquals(listOf(apple, storedSap), repository.watchlist.value)
    }

    @Test
    fun `add at an index restores the original position`() = runRepositoryTest {
        stubWatchlist(apple, microsoft)
        apiClient.addToWatchlist = { _, _, _ -> ApiResult.Success(sap) }
        val repository = repository()
        repository.refresh()

        repository.add(sap, index = 1)

        assertEquals(listOf(apple.symbol, sap.symbol, microsoft.symbol), repository.watchlist.value?.map { it.symbol })
    }

    @Test
    fun `failed add is reverted`() = runRepositoryTest {
        stubWatchlist(apple)
        val full = ApiError.Http(400, null)
        apiClient.addToWatchlist = { _, _, _ -> ApiResult.Failure(full) }
        val repository = repository()
        repository.refresh()

        assertEquals(ApiResult.Failure(full), repository.add(microsoft))

        assertEquals(listOf(apple), repository.watchlist.value)
        assertEquals(setOf(apple.symbol), repository.watchlistSymbols.value)
    }

    @Test
    fun `add of a company already in the watchlist updates it with the stored item`() = runRepositoryTest {
        stubWatchlist(apple, microsoft)
        apiClient.addToWatchlist = { _, _, _ -> ApiResult.Success(storedMicrosoft) }
        val repository = repository()
        repository.refresh()

        repository.add(microsoft)

        assertEquals(listOf(apple, storedMicrosoft), repository.watchlist.value)
    }

    @Test
    fun `stored item of a previous profile is not inserted`() = runRepositoryTest {
        apiClient.getWatchlist = { profileId -> ApiResult.Success(if (profileId.value == "p1") listOf(apple) else listOf(sap)) }
        val response = CompletableDeferred<ApiResult<WatchlistItem>>()
        apiClient.addToWatchlist = { _, _, _ -> response.await() }
        val repository = repository()
        repository.refresh()

        val call = async { repository.add(microsoft) }
        profileRepository.currentProfile.value = ActiveProfile(ProfileId("p2"), "Other")
        repository.refresh()
        response.complete(ApiResult.Success(storedMicrosoft))
        call.await()

        assertEquals(listOf(sap), repository.watchlist.value)
    }

    @Test
    fun `failed add of a company already in the watchlist keeps it`() = runRepositoryTest {
        stubWatchlist(apple)
        apiClient.addToWatchlist = { _, _, _ -> ApiResult.Failure(ApiError.Network(isTimeout = false)) }
        val repository = repository()
        repository.refresh()

        repository.add(apple)

        assertEquals(listOf(apple), repository.watchlist.value)
    }

    @Test
    fun `add before the first load is reflected in the symbols`() = runRepositoryTest {
        apiClient.addToWatchlist = { _, _, _ -> ApiResult.Success(storedMicrosoft) }
        val repository = repository()

        repository.add(microsoft)

        assertNull(repository.watchlist.value)
        assertEquals(setOf(microsoft.symbol), repository.watchlistSymbols.value)
    }

    @Test
    fun `remove hides the company at once`() = runRepositoryTest {
        stubWatchlist(apple, sap)
        val response = CompletableDeferred<ApiResult<Unit>>()
        apiClient.removeFromWatchlist = { _, _ -> response.await() }
        val repository = repository()
        repository.refresh()

        val call = async { repository.remove(apple.symbol) }

        assertEquals(listOf(sap), repository.watchlist.value)
        assertEquals(setOf(sap.symbol), repository.watchlistSymbols.value)
        response.complete(ApiResult.Success(Unit))
        call.await()
        assertEquals(listOf(sap), repository.watchlist.value)
    }

    @Test
    fun `failed remove restores the company at its position`() = runRepositoryTest {
        stubWatchlist(apple, sap, microsoft)
        apiClient.removeFromWatchlist = { _, _ -> ApiResult.Failure(ApiError.Network(isTimeout = false)) }
        val repository = repository()
        repository.refresh()

        repository.remove(sap.symbol)

        assertEquals(listOf(apple, sap, microsoft), repository.watchlist.value)
        assertEquals(setOf(apple.symbol, sap.symbol, microsoft.symbol), repository.watchlistSymbols.value)
    }

    @Test
    fun `mutations reach the backend in call order`() = runRepositoryTest {
        stubWatchlist(apple)
        val removeResponse = CompletableDeferred<ApiResult<Unit>>()
        apiClient.removeFromWatchlist = { _, _ -> removeResponse.await() }
        apiClient.addToWatchlist = { _, _, _ -> ApiResult.Success(apple) }
        val repository = repository()
        repository.refresh()

        val remove = async { repository.remove(apple.symbol) }
        val undo = async { repository.add(apple, index = 0) }

        assertEquals(listOf("getWatchlist(p1)", "removeFromWatchlist(p1, AAPL)"), apiClient.calls)
        removeResponse.complete(ApiResult.Success(Unit))
        remove.await()
        undo.await()
        assertEquals(listOf("getWatchlist(p1)", "removeFromWatchlist(p1, AAPL)", "addToWatchlist(p1, AAPL)"), apiClient.calls)
        assertEquals(listOf(apple), repository.watchlist.value)
    }

    @Test
    fun `profile change drops the watchlist of the previous profile`() = runRepositoryTest {
        stubWatchlist(apple)
        val repository = repository()
        repository.refresh()

        profileRepository.currentProfile.value = ActiveProfile(ProfileId("p2"), "Other")

        assertNull(repository.watchlist.value)
        assertEquals(emptySet<Symbol>(), repository.watchlistSymbols.value)
    }

    @Test
    fun `response for a previous profile is ignored`() = runRepositoryTest {
        val response = CompletableDeferred<ApiResult<List<WatchlistItem>>>()
        apiClient.getWatchlist = { response.await() }
        val repository = repository()

        val call = async { repository.refresh() }
        profileRepository.currentProfile.value = ActiveProfile(ProfileId("p2"), "Other")
        response.complete(ApiResult.Success(listOf(apple)))
        call.await()

        assertNull(repository.watchlist.value)
    }

    @Test
    fun `watchlist of the new profile loaded before the change is observed is kept`() = runTest {
        apiClient.getWatchlist = { profileId -> ApiResult.Success(if (profileId.value == "p1") listOf(apple) else listOf(sap)) }
        val repository = DefaultWatchlistRepository(apiClient, profileRepository, store, backgroundScope)
        runCurrent()
        repository.refresh()

        profileRepository.currentProfile.value = ActiveProfile(ProfileId("p2"), "Other")
        repository.refresh()
        runCurrent()

        assertEquals(listOf(sap), repository.watchlist.value)
        assertEquals(setOf(sap.symbol), repository.watchlistSymbols.value)
    }

    private val other = ActiveProfile(ProfileId("p2"), "Other")

    private fun storeCache(profileId: ProfileId, vararg companies: WatchlistItem) {
        store.stored[profileId] = companies.toList()
    }

    private fun getWatchlistCalls() = apiClient.calls.count { it.startsWith("getWatchlist") }

    @Test
    fun `cached watchlist is shown before the first load`() = runRepositoryTest {
        storeCache(profile.id, apple, sap)

        val repository = repository()

        assertEquals(listOf(apple, sap), repository.watchlist.value)
        assertEquals(setOf(apple.symbol, sap.symbol), repository.watchlistSymbols.value)
        assertTrue(repository.isWatchlistCached.value)
        assertTrue(apiClient.calls.isEmpty())
    }

    @Test
    fun `load replaces the cached watchlist and stores it`() = runRepositoryTest {
        storeCache(profile.id, apple)
        stubWatchlist(sap, microsoft)
        val repository = repository()

        repository.refresh()

        assertEquals(listOf(sap, microsoft), repository.watchlist.value)
        assertEquals(setOf(sap.symbol, microsoft.symbol), repository.watchlistSymbols.value)
        assertFalse(repository.isWatchlistCached.value)
        assertEquals(listOf(sap, microsoft), store.stored[profile.id])
    }

    @Test
    fun `failed load keeps the cached watchlist`() = runRepositoryTest {
        storeCache(profile.id, apple)
        apiClient.getWatchlist = { ApiResult.Failure(ApiError.Network(isTimeout = true)) }
        val repository = repository()

        repository.refresh()

        assertEquals(listOf(apple), repository.watchlist.value)
        assertTrue(repository.isWatchlistCached.value)
        assertTrue(store.writes.isEmpty())
    }

    @Test
    fun `empty cached watchlist is shown as loaded`() = runRepositoryTest {
        storeCache(profile.id)

        val repository = repository()

        assertEquals(emptyList<WatchlistItem>(), repository.watchlist.value)
        assertTrue(repository.isWatchlistCached.value)
    }

    @Test
    fun `unreadable cache leaves the watchlist unknown`() = runRepositoryTest {
        store.read = { null }

        val repository = repository()

        assertNull(repository.watchlist.value)
        assertFalse(repository.isWatchlistCached.value)
    }

    @Test
    fun `cache read completing after the load does not replace the loaded watchlist`() = runRepositoryTest {
        val cacheRead = CompletableDeferred<List<WatchlistItem>?>()
        store.read = { cacheRead.await() }
        stubWatchlist(sap)
        val repository = repository()

        repository.refresh()
        cacheRead.complete(listOf(apple))

        assertEquals(listOf(sap), repository.watchlist.value)
        assertFalse(repository.isWatchlistCached.value)
    }

    @Test
    fun `add before anything is loaded is replayed on the cached watchlist read later`() = runRepositoryTest {
        val cacheRead = CompletableDeferred<List<WatchlistItem>?>()
        store.read = { cacheRead.await() }
        apiClient.addToWatchlist = { _, _, _ -> ApiResult.Success(storedMicrosoft) }
        val repository = repository()

        repository.add(microsoft)
        assertNull(repository.watchlist.value)
        assertEquals(setOf(microsoft.symbol), repository.watchlistSymbols.value)
        cacheRead.complete(listOf(apple))

        assertEquals(listOf(apple, storedMicrosoft), repository.watchlist.value)
        assertEquals(setOf(apple.symbol, microsoft.symbol), repository.watchlistSymbols.value)
        assertTrue(store.writes.isEmpty())
    }

    @Test
    fun `add confirmed during a slow load stays and a follow-up load applies the backend watchlist`() = runRepositoryTest {
        val firstLoad = CompletableDeferred<ApiResult<List<WatchlistItem>>>()
        var loads = 0
        apiClient.getWatchlist = { if (++loads == 1) firstLoad.await() else ApiResult.Success(listOf(storedMicrosoft, apple)) }
        apiClient.addToWatchlist = { _, _, _ -> ApiResult.Success(storedMicrosoft) }
        val repository = repository()

        val load = async { repository.refresh() }
        repository.add(microsoft)
        firstLoad.complete(ApiResult.Success(listOf(apple)))
        load.await()

        assertEquals(2, getWatchlistCalls())
        assertEquals(listOf(storedMicrosoft, apple), repository.watchlist.value)
        assertEquals(listOf(storedMicrosoft, apple), store.stored[profile.id])
    }

    @Test
    fun `add confirmed during a slow load is shown on its snapshot until the follow-up load returns`() = runRepositoryTest {
        val firstLoad = CompletableDeferred<ApiResult<List<WatchlistItem>>>()
        val followUp = CompletableDeferred<ApiResult<List<WatchlistItem>>>()
        var loads = 0
        apiClient.getWatchlist = { if (++loads == 1) firstLoad.await() else followUp.await() }
        apiClient.addToWatchlist = { _, _, _ -> ApiResult.Success(storedMicrosoft) }
        val repository = repository()

        val load = async { repository.refresh() }
        repository.add(microsoft)
        firstLoad.complete(ApiResult.Success(listOf(apple)))
        load.await()

        assertEquals(listOf(apple, storedMicrosoft), repository.watchlist.value)
        assertEquals(listOf(apple, storedMicrosoft), store.stored[profile.id])
        followUp.complete(ApiResult.Success(listOf(apple, storedMicrosoft, sap)))
        assertEquals(listOf(apple, storedMicrosoft, sap), repository.watchlist.value)
        assertEquals(2, getWatchlistCalls())
    }

    @Test
    fun `add pending when a slow load returns stays visible and a failure afterwards removes it`() = runRepositoryTest {
        val loadResponse = CompletableDeferred<ApiResult<List<WatchlistItem>>>()
        val addResponse = CompletableDeferred<ApiResult<WatchlistItem>>()
        apiClient.getWatchlist = { loadResponse.await() }
        apiClient.addToWatchlist = { _, _, _ -> addResponse.await() }
        val repository = repository()

        val load = async { repository.refresh() }
        val add = async { repository.add(microsoft) }
        loadResponse.complete(ApiResult.Success(listOf(apple)))
        load.await()

        assertEquals(listOf(apple, microsoft), repository.watchlist.value)
        assertEquals(listOf(apple), store.stored[profile.id])
        addResponse.complete(ApiResult.Failure(ApiError.Network(isTimeout = false)))
        add.await()
        assertEquals(listOf(apple), repository.watchlist.value)
        assertEquals(setOf(apple.symbol), repository.watchlistSymbols.value)
        assertEquals(1, getWatchlistCalls())
        assertEquals(listOf("save(p1, AAPL)"), store.writes)
    }

    @Test
    fun `follow-up load waits until the change pending when a slow load returned is confirmed`() = runRepositoryTest {
        val firstLoad = CompletableDeferred<ApiResult<List<WatchlistItem>>>()
        val addResponse = CompletableDeferred<ApiResult<WatchlistItem>>()
        var loads = 0
        apiClient.getWatchlist = { if (++loads == 1) firstLoad.await() else ApiResult.Success(listOf(apple, storedMicrosoft)) }
        apiClient.addToWatchlist = { _, _, _ -> addResponse.await() }
        val repository = repository()

        val load = async { repository.refresh() }
        val add = async { repository.add(microsoft) }
        firstLoad.complete(ApiResult.Success(listOf(apple)))
        load.await()
        assertEquals(1, getWatchlistCalls())

        addResponse.complete(ApiResult.Success(storedMicrosoft))
        add.await()

        assertEquals(2, getWatchlistCalls())
        assertEquals(listOf(apple, storedMicrosoft), repository.watchlist.value)
    }

    @Test
    fun `remove during a slow load stays hidden although the snapshot still contains the company`() = runRepositoryTest {
        stubWatchlist(apple, sap)
        apiClient.removeFromWatchlist = { _, _ -> ApiResult.Success(Unit) }
        val repository = repository()
        repository.refresh()
        val slowLoad = CompletableDeferred<ApiResult<List<WatchlistItem>>>()
        var loads = 0
        apiClient.getWatchlist = { if (++loads == 1) slowLoad.await() else ApiResult.Success(listOf(apple)) }

        val load = async { repository.refresh() }
        repository.remove(sap.symbol)
        slowLoad.complete(ApiResult.Success(listOf(apple, sap)))
        load.await()

        assertEquals(listOf(apple), repository.watchlist.value)
        assertEquals(setOf(apple.symbol), repository.watchlistSymbols.value)
        assertEquals(3, getWatchlistCalls())
        assertEquals(listOf(apple), store.stored[profile.id])
    }

    @Test
    fun `remove and undo during a slow load keep the company at its position`() = runRepositoryTest {
        stubWatchlist(apple, sap, microsoft)
        apiClient.removeFromWatchlist = { _, _ -> ApiResult.Success(Unit) }
        apiClient.addToWatchlist = { _, _, _ -> ApiResult.Success(sap) }
        val repository = repository()
        repository.refresh()
        val slowLoad = CompletableDeferred<ApiResult<List<WatchlistItem>>>()
        val followUp = CompletableDeferred<ApiResult<List<WatchlistItem>>>()
        var loads = 0
        apiClient.getWatchlist = { if (++loads == 1) slowLoad.await() else followUp.await() }

        val load = async { repository.refresh() }
        repository.remove(sap.symbol)
        assertEquals(listOf(apple, microsoft), repository.watchlist.value)
        repository.add(sap, index = 1)
        slowLoad.complete(ApiResult.Success(listOf(apple, microsoft)))
        load.await()

        assertEquals(listOf(apple, sap, microsoft), repository.watchlist.value)
        followUp.complete(ApiResult.Success(listOf(apple, microsoft, sap)))
        assertEquals(listOf(apple, microsoft, sap), repository.watchlist.value)
        assertEquals(listOf(apple, microsoft, sap), store.stored[profile.id])
    }

    @Test
    fun `add confirmed before the load was sent and missing from its snapshot is dropped`() = runRepositoryTest {
        stubWatchlist(apple)
        apiClient.addToWatchlist = { _, _, _ -> ApiResult.Success(storedMicrosoft) }
        val repository = repository()
        repository.refresh()
        repository.add(microsoft)

        repository.refresh()

        assertEquals(listOf(apple), repository.watchlist.value)
        assertEquals(setOf(apple.symbol), repository.watchlistSymbols.value)
        assertEquals(2, getWatchlistCalls())
        assertEquals(listOf(apple), store.stored[profile.id])
    }

    @Test
    fun `snapshot of a load sent before the last applied one is ignored`() = runRepositoryTest {
        val olderLoad = CompletableDeferred<ApiResult<List<WatchlistItem>>>()
        val newerLoad = CompletableDeferred<ApiResult<List<WatchlistItem>>>()
        var loads = 0
        apiClient.getWatchlist = { if (++loads == 1) olderLoad.await() else newerLoad.await() }
        val repository = repository()

        val older = async { repository.refresh() }
        val newer = async { repository.refresh() }
        newerLoad.complete(ApiResult.Success(listOf(sap)))
        newer.await()
        olderLoad.complete(ApiResult.Success(listOf(apple)))
        older.await()

        assertEquals(listOf(sap), repository.watchlist.value)
        assertEquals(listOf(sap), store.stored[profile.id])
    }

    @Test
    fun `pending changes are not stored and confirmed ones are`() = runRepositoryTest {
        stubWatchlist(apple, sap)
        val addResponse = CompletableDeferred<ApiResult<WatchlistItem>>()
        val removeResponse = CompletableDeferred<ApiResult<Unit>>()
        apiClient.addToWatchlist = { _, _, _ -> addResponse.await() }
        apiClient.removeFromWatchlist = { _, _ -> removeResponse.await() }
        val repository = repository()
        repository.refresh()

        val remove = async { repository.remove(sap.symbol) }
        val add = async { repository.add(microsoft) }
        assertEquals(listOf(apple, sap), store.stored[profile.id])

        removeResponse.complete(ApiResult.Success(Unit))
        remove.await()
        assertEquals(listOf(apple), store.stored[profile.id])

        addResponse.complete(ApiResult.Success(storedMicrosoft))
        add.await()
        assertEquals(listOf(apple, storedMicrosoft), store.stored[profile.id])
    }

    @Test
    fun `failed changes are never stored`() = runRepositoryTest {
        stubWatchlist(apple, sap)
        apiClient.addToWatchlist = { _, _, _ -> ApiResult.Failure(ApiError.Http(500, null)) }
        apiClient.removeFromWatchlist = { _, _ -> ApiResult.Failure(ApiError.Http(500, null)) }
        val repository = repository()
        repository.refresh()

        repository.add(microsoft)
        repository.remove(sap.symbol)

        assertEquals(listOf("save(p1, AAPL,SAP.DE)"), store.writes)
        assertEquals(listOf(apple, sap), repository.watchlist.value)
    }

    @Test
    fun `remove from the cached watchlist is stored once confirmed`() = runRepositoryTest {
        storeCache(profile.id, apple, sap)
        apiClient.removeFromWatchlist = { _, _ -> ApiResult.Success(Unit) }
        val repository = repository()

        repository.remove(apple.symbol)

        assertEquals(listOf(sap), repository.watchlist.value)
        assertEquals(listOf(sap), store.stored[profile.id])
    }

    @Test
    fun `not found deletes the cached watchlist of the profile`() = runRepositoryTest {
        storeCache(profile.id, apple)
        storeCache(other.id, sap)
        apiClient.getWatchlist = { ApiResult.Failure(ApiError.NotFound(null)) }
        val repository = repository()

        repository.refresh()

        assertEquals(listOf("clear(p1)"), store.writes)
        assertNull(store.stored[profile.id])
        assertEquals(listOf(sap), store.stored[other.id])
        assertNull(repository.watchlist.value)
    }

    @Test
    fun `profile change shows the cached watchlist of the new profile`() = runRepositoryTest {
        storeCache(profile.id, apple)
        storeCache(other.id, sap)
        val repository = repository()

        profileRepository.currentProfile.value = other

        assertEquals(listOf(sap), repository.watchlist.value)
        assertEquals(setOf(sap.symbol), repository.watchlistSymbols.value)
        assertTrue(repository.isWatchlistCached.value)
    }

    @Test
    fun `load of the new profile stores its watchlist under its ID`() = runRepositoryTest {
        storeCache(profile.id, apple)
        stubWatchlist(sap)
        val repository = repository()

        profileRepository.currentProfile.value = other
        repository.refresh()

        assertEquals(listOf(apple), store.stored[profile.id])
        assertEquals(listOf(sap), store.stored[other.id])
    }

    @Test
    fun `change of a previous profile confirmed after the profile change is not stored`() = runRepositoryTest {
        stubWatchlist(apple)
        val addResponse = CompletableDeferred<ApiResult<WatchlistItem>>()
        apiClient.addToWatchlist = { _, _, _ -> addResponse.await() }
        val repository = repository()
        repository.refresh()

        val add = async { repository.add(microsoft) }
        profileRepository.currentProfile.value = other
        addResponse.complete(ApiResult.Success(storedMicrosoft))
        add.await()

        assertEquals(listOf(apple), store.stored[profile.id])
        assertNull(repository.watchlist.value)
        assertEquals(emptySet<Symbol>(), repository.watchlistSymbols.value)
    }
}
