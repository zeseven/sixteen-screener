package com.sixteenscreener.app.ui.search

import com.sixteenscreener.app.data.repository.DefaultSearchRepository
import com.sixteenscreener.app.data.repository.DefaultWatchlistRepository
import com.sixteenscreener.app.domain.model.ActiveProfile
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.ProblemDetails
import com.sixteenscreener.app.domain.model.ProfileId
import com.sixteenscreener.app.domain.model.SearchItem
import com.sixteenscreener.app.domain.model.SearchResults
import com.sixteenscreener.app.domain.model.SearchStatus
import com.sixteenscreener.app.domain.model.SourceId
import com.sixteenscreener.app.domain.model.WatchlistItem
import com.sixteenscreener.app.testing.Companies.item
import com.sixteenscreener.app.testing.Companies.searchItem
import com.sixteenscreener.app.testing.FakeApiClient
import com.sixteenscreener.app.testing.FakeProfileRepository
import com.sixteenscreener.app.testing.MainDispatcherRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val apiClient = FakeApiClient()
    private val profileRepository = FakeProfileRepository(ActiveProfile(ProfileId("p1"), "Viktor"))

    private val apple = searchItem("AAPL", name = "Apple Inc.")
    private val applied = searchItem("AMAT", name = "Applied Materials")
    private val ford = searchItem("F", name = "Ford Motor Company")
    private val generalMotors = searchItem("GM", name = "General Motors Company")

    private lateinit var watchlistRepository: DefaultWatchlistRepository

    private fun TestScope.viewModel(): SearchViewModel {
        watchlistRepository = DefaultWatchlistRepository(apiClient, profileRepository, backgroundScope)
        return SearchViewModel(DefaultSearchRepository(apiClient, profileRepository), watchlistRepository, profileRepository)
    }

    private fun results(status: SearchStatus = SearchStatus.OK, vararg items: SearchItem) =
        ApiResult.Success(SearchResults(SourceId.FINNHUB, status, items.toList()))

    private fun stubSearch(vararg items: SearchItem) {
        apiClient.search = { _, _ -> results(SearchStatus.OK, *items) }
    }

    private val searches get() = apiClient.calls.filter { it.startsWith("search") }

    private val SearchViewModel.state get() = uiState.value

    @Test
    fun `one character makes an immediate request`() = runTest {
        stubSearch(ford)
        val viewModel = viewModel()

        viewModel.onQueryChange("f")

        assertEquals(listOf("search(p1, f)"), searches)
        assertEquals(listOf(ford), viewModel.state.results)
        assertNull(viewModel.state.notice)
    }

    @Test
    fun `two characters make an immediate request`() = runTest {
        stubSearch(generalMotors)
        val viewModel = viewModel()

        viewModel.onQueryChange("gm")

        assertEquals(listOf("search(p1, gm)"), searches)
        assertEquals(listOf(generalMotors), viewModel.state.results)
    }

    @Test
    fun `query is sent trimmed`() = runTest {
        stubSearch(ford)
        val viewModel = viewModel()

        viewModel.onQueryChange("  f ")

        assertEquals(listOf("search(p1, f)"), searches)
        assertEquals("  f ", viewModel.state.query)
    }

    @Test
    fun `input is capped at fifty characters`() = runTest {
        stubSearch()
        val viewModel = viewModel()

        viewModel.onQueryChange("a".repeat(60))

        assertEquals("a".repeat(50), viewModel.state.query)
        assertEquals(listOf("search(p1, ${"a".repeat(50)})"), searches)
    }

    @Test
    fun `whitespace only input makes no request`() = runTest {
        stubSearch(apple)
        val viewModel = viewModel()

        viewModel.onQueryChange("      ")
        advanceTimeBy(5_000)

        assertTrue(searches.isEmpty())
        assertEquals("      ", viewModel.state.query)
    }

    @Test
    fun `fast typing from the first letter sends at most one request per second and the last query`() = runTest {
        stubSearch(apple)
        val viewModel = viewModel()

        viewModel.onQueryChange("a")
        advanceTimeBy(200)
        viewModel.onQueryChange("ap")
        advanceTimeBy(200)
        viewModel.onQueryChange("app")
        advanceTimeBy(200)
        viewModel.onQueryChange("appl")
        advanceTimeBy(200)
        viewModel.onQueryChange("apple")
        advanceTimeBy(199)
        assertEquals(listOf("search(p1, a)"), searches)

        advanceTimeBy(2)
        assertEquals(listOf("search(p1, a)", "search(p1, apple)"), searches)

        advanceTimeBy(5_000)
        assertEquals(2, searches.size)
        assertEquals(listOf(apple), viewModel.state.results)
    }

    @Test
    fun `newer query cancels the running request`() = runTest {
        var cancelled = false
        apiClient.search = { _, query ->
            if (query == "app") {
                try {
                    CompletableDeferred<ApiResult<SearchResults>>().await()
                } catch (e: CancellationException) {
                    cancelled = true
                    throw e
                }
            } else {
                results(SearchStatus.OK, applied)
            }
        }
        val viewModel = viewModel()

        viewModel.onQueryChange("app")
        assertTrue(viewModel.state.isSearching)
        viewModel.onQueryChange("appli")
        advanceTimeBy(1_001)

        assertTrue(cancelled)
        assertFalse(viewModel.state.isSearching)
        assertEquals(listOf(applied), viewModel.state.results)
    }

    @Test
    fun `deleting back to empty clears results and cancels the running request without a new one`() = runTest {
        stubSearch(apple)
        val viewModel = viewModel()
        viewModel.onQueryChange("a")
        advanceTimeBy(2_000)
        var cancelled = false
        apiClient.search = { _, _ ->
            try {
                CompletableDeferred<ApiResult<SearchResults>>().await()
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            }
        }
        viewModel.onQueryChange("ap")
        assertTrue(viewModel.state.isSearching)

        viewModel.onQueryChange("a")
        viewModel.onQueryChange("")
        advanceTimeBy(5_000)

        assertTrue(cancelled)
        assertEquals(SearchUiState(query = ""), viewModel.state)
        assertEquals(listOf("search(p1, a)", "search(p1, ap)"), searches)
    }

    @Test
    fun `whitespace only input after results clears them without a request`() = runTest {
        stubSearch(ford)
        val viewModel = viewModel()
        viewModel.onQueryChange("f")
        assertEquals(listOf(ford), viewModel.state.results)

        viewModel.onQueryChange("   ")
        advanceTimeBy(5_000)

        assertEquals(SearchUiState(query = "   "), viewModel.state)
        assertEquals(listOf("search(p1, f)"), searches)
    }

    @Test
    fun `limit reached is shown under the input`() = runTest {
        apiClient.search = { _, _ -> results(SearchStatus.LIMIT_REACHED) }
        val viewModel = viewModel()

        viewModel.onQueryChange("app")

        assertEquals(SearchNotice.LIMIT_REACHED, viewModel.state.notice)
    }

    @Test
    fun `source error is shown under the input`() = runTest {
        apiClient.search = { _, _ -> results(SearchStatus.SOURCE_ERROR) }
        val viewModel = viewModel()

        viewModel.onQueryChange("app")

        assertEquals(SearchNotice.SOURCE_ERROR, viewModel.state.notice)
    }

    @Test
    fun `unknown status is shown as source error`() = runTest {
        apiClient.search = { _, _ -> results(SearchStatus.UNKNOWN) }
        val viewModel = viewModel()

        viewModel.onQueryChange("app")

        assertEquals(SearchNotice.SOURCE_ERROR, viewModel.state.notice)
    }

    @Test
    fun `empty result is shown as nothing found`() = runTest {
        stubSearch()
        val viewModel = viewModel()

        viewModel.onQueryChange("zzz")

        assertEquals(SearchNotice.NOTHING_FOUND, viewModel.state.notice)
    }

    @Test
    fun `network error is shown under the input`() = runTest {
        apiClient.search = { _, _ -> ApiResult.Failure(ApiError.Network(isTimeout = true)) }
        val viewModel = viewModel()

        viewModel.onQueryChange("app")

        assertEquals(SearchNotice.REQUEST_FAILED, viewModel.state.notice)
        assertEquals(emptyList<SearchItem>(), viewModel.state.results)
    }

    @Test
    fun `backend HTTP error is shown under the input`() = runTest {
        apiClient.search = { _, _ -> ApiResult.Failure(ApiError.Http(503, null)) }
        val viewModel = viewModel()

        viewModel.onQueryChange("app")

        assertEquals(SearchNotice.REQUEST_FAILED, viewModel.state.notice)
    }

    @Test
    fun `next successful search clears the notice`() = runTest {
        apiClient.search = { _, _ -> ApiResult.Failure(ApiError.Network(isTimeout = false)) }
        val viewModel = viewModel()
        viewModel.onQueryChange("app")
        stubSearch(apple)

        viewModel.onQueryChange("apple")
        advanceTimeBy(1_001)

        assertNull(viewModel.state.notice)
        assertEquals(listOf(apple), viewModel.state.results)
    }

    @Test
    fun `add updates in watchlist at once`() = runTest {
        stubSearch(apple, applied)
        val response = CompletableDeferred<ApiResult<WatchlistItem>>()
        apiClient.addToWatchlist = { _, _ -> response.await() }
        val viewModel = viewModel()
        viewModel.onQueryChange("app")

        viewModel.onAdd(apple)

        assertEquals(listOf(true, false), viewModel.state.results.map { it.inWatchlist })
        response.complete(ApiResult.Success(item("AAPL", name = "Apple Inc.")))
        runCurrent()
        assertEquals(listOf(true, false), viewModel.state.results.map { it.inWatchlist })
        assertTrue("addToWatchlist(p1, AAPL)" in apiClient.calls)
    }

    @Test
    fun `added company gets the sector of the stored item in the loaded watchlist`() = runTest {
        apiClient.getWatchlist = { ApiResult.Success(emptyList()) }
        stubSearch(apple)
        val response = CompletableDeferred<ApiResult<WatchlistItem>>()
        apiClient.addToWatchlist = { _, _ -> response.await() }
        val viewModel = viewModel()
        watchlistRepository.refresh()
        viewModel.onQueryChange("app")

        viewModel.onAdd(apple)

        assertEquals(listOf(WatchlistItem.of(apple)), watchlistRepository.watchlist.value)
        assertNull(watchlistRepository.watchlist.value?.single()?.sector)
        val stored = item("AAPL", name = "Apple Inc.", sector = "Technology")
        response.complete(ApiResult.Success(stored))
        runCurrent()
        assertEquals(listOf(stored), watchlistRepository.watchlist.value)
    }

    @Test
    fun `add failure shows an error message and reverts the row`() = runTest {
        stubSearch(apple)
        val full = ApiError.Http(409, ProblemDetails(null, "Conflict", 409, "Watchlist is full"))
        apiClient.addToWatchlist = { _, _ -> ApiResult.Failure(full) }
        val viewModel = viewModel()
        viewModel.onQueryChange("app")

        viewModel.onAdd(apple)

        assertEquals(listOf(false), viewModel.state.results.map { it.inWatchlist })
        val message = AddFailedMessage(apple, full)
        assertEquals(message, viewModel.state.message)

        viewModel.onMessageShown(message)
        assertNull(viewModel.state.message)
    }

    @Test
    fun `watchlist changes in the other tab update the rows`() = runTest {
        apiClient.getWatchlist = { ApiResult.Success(listOf(WatchlistItem.of(apple))) }
        apiClient.removeFromWatchlist = { _, _ -> ApiResult.Success(Unit) }
        stubSearch(apple, applied)
        val viewModel = viewModel()
        viewModel.onQueryChange("app")
        assertEquals(listOf(false, false), viewModel.state.results.map { it.inWatchlist })

        watchlistRepository.refresh()
        assertEquals(listOf(true, false), viewModel.state.results.map { it.inWatchlist })

        watchlistRepository.remove(apple.symbol)
        assertEquals(listOf(false, false), viewModel.state.results.map { it.inWatchlist })
    }

    @Test
    fun `search flag is used until the watchlist is loaded`() = runTest {
        stubSearch(apple.copy(inWatchlist = true), applied)
        val viewModel = viewModel()

        viewModel.onQueryChange("app")

        assertEquals(listOf(true, false), viewModel.state.results.map { it.inWatchlist })
    }

    @Test
    fun `profile change clears the query and the results`() = runTest {
        stubSearch(apple)
        val viewModel = viewModel()
        viewModel.onQueryChange("app")
        assertEquals(listOf(apple), viewModel.state.results)

        profileRepository.currentProfile.value = ActiveProfile(ProfileId("p2"), "Anna")
        advanceTimeBy(5_000)

        assertEquals("", viewModel.state.query)
        assertEquals(emptyList<SearchItem>(), viewModel.state.results)
        assertNull(viewModel.state.notice)
        assertEquals(listOf("search(p1, app)"), searches)
    }

    @Test
    fun `profile change cancels the running search`() = runTest {
        val response = CompletableDeferred<ApiResult<SearchResults>>()
        apiClient.search = { _, _ -> response.await() }
        val viewModel = viewModel()
        viewModel.onQueryChange("app")
        assertTrue(viewModel.state.isSearching)

        profileRepository.currentProfile.value = ActiveProfile(ProfileId("p2"), "Anna")
        runCurrent()
        response.complete(results(SearchStatus.OK, apple))

        assertFalse(viewModel.state.isSearching)
        assertEquals(emptyList<SearchItem>(), viewModel.state.results)
    }

    @Test
    fun `search after a profile change uses the new profile ID`() = runTest {
        stubSearch(apple)
        val viewModel = viewModel()
        profileRepository.currentProfile.value = ActiveProfile(ProfileId("p2"), "Anna")
        runCurrent()

        viewModel.onQueryChange("app")
        advanceTimeBy(1_000)

        assertEquals(listOf("search(p2, app)"), searches)
    }
}
