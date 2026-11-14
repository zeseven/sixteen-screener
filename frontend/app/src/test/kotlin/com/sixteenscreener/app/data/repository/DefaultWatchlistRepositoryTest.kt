package com.sixteenscreener.app.data.repository

import com.sixteenscreener.app.domain.model.ActiveProfile
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.CompanySummary
import com.sixteenscreener.app.domain.model.ProfileId
import com.sixteenscreener.app.domain.model.Symbol
import com.sixteenscreener.app.testing.Companies.summary
import com.sixteenscreener.app.testing.FakeApiClient
import com.sixteenscreener.app.testing.FakeProfileRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultWatchlistRepositoryTest {

    private val profile = ActiveProfile(ProfileId("p1"), "Viktor")
    private val apiClient = FakeApiClient()
    private val profileRepository = FakeProfileRepository(profile)

    private val apple = summary("AAPL")
    private val sap = summary("SAP.DE", name = "SAP SE")
    private val microsoft = summary("MSFT", name = "Microsoft", inWatchlist = false)

    private fun TestScope.repository() = DefaultWatchlistRepository(apiClient, profileRepository, backgroundScope)

    private fun runRepositoryTest(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher()) { block() }

    private fun stubWatchlist(vararg companies: CompanySummary) {
        apiClient.getWatchlist = { ApiResult.Success(companies.toList()) }
    }

    @Test
    fun `nothing is known before the first load`() = runRepositoryTest {
        val repository = repository()

        assertNull(repository.watchlist.value)
        assertEquals(emptySet<Symbol>(), repository.watchlistSymbols.value)
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
    fun `add shows the company at once and keeps it on success`() = runRepositoryTest {
        stubWatchlist(apple)
        val response = CompletableDeferred<ApiResult<Unit>>()
        apiClient.addToWatchlist = { _, _ -> response.await() }
        val repository = repository()
        repository.refresh()

        val call = async { repository.add(microsoft) }

        assertEquals(listOf(apple, microsoft.copy(inWatchlist = true)), repository.watchlist.value)
        assertEquals(setOf(apple.symbol, microsoft.symbol), repository.watchlistSymbols.value)
        response.complete(ApiResult.Success(Unit))
        assertEquals(ApiResult.Success(Unit), call.await())
        assertEquals(setOf(apple.symbol, microsoft.symbol), repository.watchlistSymbols.value)
        assertEquals(listOf("getWatchlist(p1)", "addToWatchlist(p1, MSFT)"), apiClient.calls)
    }

    @Test
    fun `add at an index restores the original position`() = runRepositoryTest {
        stubWatchlist(apple, microsoft)
        apiClient.addToWatchlist = { _, _ -> ApiResult.Success(Unit) }
        val repository = repository()
        repository.refresh()

        repository.add(sap, index = 1)

        assertEquals(listOf(apple.symbol, sap.symbol, microsoft.symbol), repository.watchlist.value?.map { it.symbol })
    }

    @Test
    fun `failed add is reverted`() = runRepositoryTest {
        stubWatchlist(apple)
        val full = ApiError.Http(409, null)
        apiClient.addToWatchlist = { _, _ -> ApiResult.Failure(full) }
        val repository = repository()
        repository.refresh()

        assertEquals(ApiResult.Failure(full), repository.add(microsoft))

        assertEquals(listOf(apple), repository.watchlist.value)
        assertEquals(setOf(apple.symbol), repository.watchlistSymbols.value)
    }

    @Test
    fun `failed add of a company already in the watchlist keeps it`() = runRepositoryTest {
        stubWatchlist(apple)
        apiClient.addToWatchlist = { _, _ -> ApiResult.Failure(ApiError.Network(isTimeout = false)) }
        val repository = repository()
        repository.refresh()

        repository.add(apple)

        assertEquals(listOf(apple), repository.watchlist.value)
    }

    @Test
    fun `add before the first load is reflected in the symbols`() = runRepositoryTest {
        apiClient.addToWatchlist = { _, _ -> ApiResult.Success(Unit) }
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
        apiClient.addToWatchlist = { _, _ -> ApiResult.Success(Unit) }
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
        val response = CompletableDeferred<ApiResult<List<CompanySummary>>>()
        apiClient.getWatchlist = { response.await() }
        val repository = repository()

        val call = async { repository.refresh() }
        profileRepository.currentProfile.value = ActiveProfile(ProfileId("p2"), "Other")
        response.complete(ApiResult.Success(listOf(apple)))
        call.await()

        assertNull(repository.watchlist.value)
    }
}
