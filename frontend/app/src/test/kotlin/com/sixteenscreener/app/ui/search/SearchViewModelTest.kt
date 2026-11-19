package com.sixteenscreener.app.ui.search

import com.sixteenscreener.app.data.repository.DefaultSearchRepository
import com.sixteenscreener.app.data.repository.DefaultWatchlistRepository
import com.sixteenscreener.app.domain.SearchDebounce
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
import com.sixteenscreener.app.testing.FakeWatchlistStore
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
        watchlistRepository = DefaultWatchlistRepository(apiClient, profileRepository, FakeWatchlistStore(), backgroundScope)
        return SearchViewModel(DefaultSearchRepository(apiClient, profileRepository), watchlistRepository, profileRepository)
    }

    private fun results(status: SearchStatus = SearchStatus.OK, vararg items: SearchItem) =
        ApiResult.Success(SearchResults(SourceId.FINNHUB, status, items.toList()))

    private fun stubSearch(vararg items: SearchItem) {
        apiClient.search = { _, _ -> results(SearchStatus.OK, *items) }
    }

    private val searches get() = apiClient.calls.filter { it.startsWith("search") }

    private val SearchViewModel.state get() = uiState.value

    private fun TestScope.typeAndPause(viewModel: SearchViewModel, text: String) {
        viewModel.onQueryChange(text)
        advanceTimeBy(SearchDebounce.PAUSE)
        runCurrent()
    }

    private fun blockingSearch(onCancel: () -> Unit): suspend (ProfileId, String) -> ApiResult<SearchResults> = { _, _ ->
        try {
            CompletableDeferred<ApiResult<SearchResults>>().await()
        } catch (e: CancellationException) {
            onCancel()
            throw e
        }
    }

    @Test
    fun `one character is searched after the pause`() = runTest {
        stubSearch(ford)
        val viewModel = viewModel()

        viewModel.onQueryChange("f")
        advanceTimeBy(499)
        assertTrue(searches.isEmpty())

        advanceTimeBy(2)
        assertEquals(listOf("search(p1, f)"), searches)
        assertEquals(listOf(ford), viewModel.state.results)
        assertNull(viewModel.state.notice)
    }

    @Test
    fun `two characters are searched after the pause`() = runTest {
        stubSearch(generalMotors)
        val viewModel = viewModel()

        typeAndPause(viewModel, "gm")

        assertEquals(listOf("search(p1, gm)"), searches)
        assertEquals(listOf(generalMotors), viewModel.state.results)
    }

    @Test
    fun `query is sent trimmed`() = runTest {
        stubSearch(ford)
        val viewModel = viewModel()

        typeAndPause(viewModel, "  f ")

        assertEquals(listOf("search(p1, f)"), searches)
        assertEquals("  f ", viewModel.state.query)
    }

    @Test
    fun `input is capped at fifty characters`() = runTest {
        stubSearch()
        val viewModel = viewModel()

        typeAndPause(viewModel, "a".repeat(60))

        assertEquals("a".repeat(50), viewModel.state.query)
        assertEquals(listOf("search(p1, ${"a".repeat(50)})"), searches)
    }

    @Test
    fun `whitespace only input makes no request`() = runTest {
        stubSearch(apple)
        val viewModel = viewModel()

        viewModel.onQueryChange("      ")
        viewModel.onSearch()
        advanceTimeBy(5_000)

        assertTrue(searches.isEmpty())
        assertEquals("      ", viewModel.state.query)
    }

    @Test
    fun `fast typing sends nothing until the pause and then only the last query`() = runTest {
        stubSearch(apple)
        val viewModel = viewModel()

        viewModel.onQueryChange("a")
        advanceTimeBy(400)
        viewModel.onQueryChange("ap")
        advanceTimeBy(400)
        viewModel.onQueryChange("app")
        advanceTimeBy(400)
        viewModel.onQueryChange("appl")
        advanceTimeBy(400)
        viewModel.onQueryChange("apple")
        advanceTimeBy(499)
        assertTrue(searches.isEmpty())

        advanceTimeBy(2)
        assertEquals(listOf("search(p1, apple)"), searches)

        advanceTimeBy(5_000)
        assertEquals(1, searches.size)
        assertEquals(listOf(apple), viewModel.state.results)
    }

    @Test
    fun `search action searches the current query at once`() = runTest {
        stubSearch(apple)
        val viewModel = viewModel()

        viewModel.onQueryChange("apple")
        advanceTimeBy(100)
        viewModel.onSearch()

        assertEquals(listOf("search(p1, apple)"), searches)
        assertEquals(listOf(apple), viewModel.state.results)
    }

    @Test
    fun `pause after the search action sends no duplicate request`() = runTest {
        stubSearch(apple)
        val viewModel = viewModel()

        viewModel.onQueryChange("apple")
        viewModel.onSearch()
        advanceTimeBy(5_000)

        assertEquals(listOf("search(p1, apple)"), searches)
    }

    @Test
    fun `search action for an already searched query sends no request`() = runTest {
        stubSearch(apple)
        val viewModel = viewModel()
        typeAndPause(viewModel, "apple")

        viewModel.onSearch()
        advanceTimeBy(5_000)

        assertEquals(listOf("search(p1, apple)"), searches)
        assertEquals(listOf(apple), viewModel.state.results)
    }

    @Test
    fun `query typed back to the searched one after a short edit is not searched again`() = runTest {
        stubSearch(apple)
        val viewModel = viewModel()
        typeAndPause(viewModel, "abc")

        viewModel.onQueryChange("abcd")
        advanceTimeBy(200)
        viewModel.onQueryChange("abc")
        advanceTimeBy(5_000)

        assertEquals(listOf("search(p1, abc)"), searches)
        assertEquals(listOf(apple), viewModel.state.results)
    }

    @Test
    fun `query typed back to the searched one after a searched edit is searched again`() = runTest {
        stubSearch(apple)
        val viewModel = viewModel()
        typeAndPause(viewModel, "abc")
        typeAndPause(viewModel, "abcd")

        typeAndPause(viewModel, "abc")

        assertEquals(listOf("search(p1, abc)", "search(p1, abcd)", "search(p1, abc)"), searches)
    }

    @Test
    fun `newer query cancels the running request`() = runTest {
        var cancelled = false
        val pending = blockingSearch { cancelled = true }
        apiClient.search = { profileId, query -> if (query == "app") pending(profileId, query) else results(SearchStatus.OK, applied) }
        val viewModel = viewModel()

        typeAndPause(viewModel, "app")
        assertTrue(viewModel.state.isSearching)
        typeAndPause(viewModel, "appli")

        assertTrue(cancelled)
        assertFalse(viewModel.state.isSearching)
        assertEquals(listOf(applied), viewModel.state.results)
    }

    @Test
    fun `search action cancels the running request`() = runTest {
        var cancelled = false
        val pending = blockingSearch { cancelled = true }
        apiClient.search = { profileId, query -> if (query == "app") pending(profileId, query) else results(SearchStatus.OK, applied) }
        val viewModel = viewModel()
        typeAndPause(viewModel, "app")
        assertTrue(viewModel.state.isSearching)

        viewModel.onQueryChange("appli")
        viewModel.onSearch()

        assertTrue(cancelled)
        assertEquals(listOf(applied), viewModel.state.results)
    }

    @Test
    fun `typing during a running request does not cancel it before the pause`() = runTest {
        var cancelled = false
        apiClient.search = blockingSearch { cancelled = true }
        val viewModel = viewModel()
        typeAndPause(viewModel, "app")

        viewModel.onQueryChange("appl")
        advanceTimeBy(499)

        assertFalse(cancelled)
        assertTrue(viewModel.state.isSearching)
    }

    @Test
    fun `deleting back to empty clears results and cancels the running request without a new one`() = runTest {
        stubSearch(apple)
        val viewModel = viewModel()
        typeAndPause(viewModel, "a")
        var cancelled = false
        apiClient.search = blockingSearch { cancelled = true }
        typeAndPause(viewModel, "ap")
        assertTrue(viewModel.state.isSearching)

        viewModel.onQueryChange("a")
        viewModel.onQueryChange("")
        advanceTimeBy(5_000)

        assertTrue(cancelled)
        assertEquals(SearchUiState(query = ""), viewModel.state)
        assertEquals(listOf("search(p1, a)", "search(p1, ap)"), searches)
    }

    @Test
    fun `empty input drops the pending query`() = runTest {
        stubSearch(apple)
        val viewModel = viewModel()

        viewModel.onQueryChange("app")
        advanceTimeBy(300)
        viewModel.onQueryChange("")
        advanceTimeBy(5_000)

        assertTrue(searches.isEmpty())
        assertEquals(SearchUiState(query = ""), viewModel.state)
    }

    @Test
    fun `whitespace only input after results clears them at once without a request`() = runTest {
        stubSearch(ford)
        val viewModel = viewModel()
        typeAndPause(viewModel, "f")
        assertEquals(listOf(ford), viewModel.state.results)

        viewModel.onQueryChange("   ")
        assertEquals(SearchUiState(query = "   "), viewModel.state)

        advanceTimeBy(5_000)
        assertEquals(SearchUiState(query = "   "), viewModel.state)
        assertEquals(listOf("search(p1, f)"), searches)
    }

    @Test
    fun `limit reached is shown under the input`() = runTest {
        apiClient.search = { _, _ -> results(SearchStatus.LIMIT_REACHED) }
        val viewModel = viewModel()

        typeAndPause(viewModel, "app")

        assertEquals(SearchNotice.LIMIT_REACHED, viewModel.state.notice)
    }

    @Test
    fun `source error is shown under the input`() = runTest {
        apiClient.search = { _, _ -> results(SearchStatus.SOURCE_ERROR) }
        val viewModel = viewModel()

        typeAndPause(viewModel, "app")

        assertEquals(SearchNotice.SOURCE_ERROR, viewModel.state.notice)
    }

    @Test
    fun `unknown status is shown as source error`() = runTest {
        apiClient.search = { _, _ -> results(SearchStatus.UNKNOWN) }
        val viewModel = viewModel()

        typeAndPause(viewModel, "app")

        assertEquals(SearchNotice.SOURCE_ERROR, viewModel.state.notice)
    }

    @Test
    fun `empty result is shown as nothing found`() = runTest {
        stubSearch()
        val viewModel = viewModel()

        typeAndPause(viewModel, "zzz")

        assertEquals(SearchNotice.NOTHING_FOUND, viewModel.state.notice)
    }

    @Test
    fun `network error is shown under the input`() = runTest {
        apiClient.search = { _, _ -> ApiResult.Failure(ApiError.Network(isTimeout = true)) }
        val viewModel = viewModel()

        typeAndPause(viewModel, "app")

        assertEquals(SearchNotice.REQUEST_FAILED, viewModel.state.notice)
        assertEquals(emptyList<SearchItem>(), viewModel.state.results)
    }

    @Test
    fun `backend HTTP error is shown under the input`() = runTest {
        apiClient.search = { _, _ -> ApiResult.Failure(ApiError.Http(503, null)) }
        val viewModel = viewModel()

        typeAndPause(viewModel, "app")

        assertEquals(SearchNotice.REQUEST_FAILED, viewModel.state.notice)
    }

    @Test
    fun `next successful search clears the notice`() = runTest {
        apiClient.search = { _, _ -> ApiResult.Failure(ApiError.Network(isTimeout = false)) }
        val viewModel = viewModel()
        typeAndPause(viewModel, "app")
        stubSearch(apple)

        typeAndPause(viewModel, "apple")

        assertNull(viewModel.state.notice)
        assertEquals(listOf(apple), viewModel.state.results)
    }

    @Test
    fun `add updates in watchlist at once`() = runTest {
        stubSearch(apple, applied)
        val response = CompletableDeferred<ApiResult<WatchlistItem>>()
        apiClient.addToWatchlist = { _, _, _ -> response.await() }
        val viewModel = viewModel()
        typeAndPause(viewModel, "app")

        viewModel.onAdd(apple)

        assertEquals(listOf(true, false), viewModel.state.results.map { it.inWatchlist })
        response.complete(ApiResult.Success(item("AAPL", name = "Apple Inc.")))
        runCurrent()
        assertEquals(listOf(true, false), viewModel.state.results.map { it.inWatchlist })
        assertTrue("addToWatchlist(p1, AAPL)" in apiClient.calls)
    }

    @Test
    fun `add sends the name of the search result`() = runTest {
        apiClient.getWatchlist = { ApiResult.Success(emptyList()) }
        stubSearch(apple)
        val sentNames = mutableListOf<String>()
        apiClient.addToWatchlist = { _, _, name -> sentNames += name; ApiResult.Success(item("AAPL", name = "Apple Inc.")) }
        val viewModel = viewModel()
        typeAndPause(viewModel, "app")

        viewModel.onAdd(apple)
        runCurrent()

        assertEquals(listOf("Apple Inc."), sentNames)
    }

    @Test
    fun `added company gets the sector of the stored item in the loaded watchlist`() = runTest {
        apiClient.getWatchlist = { ApiResult.Success(emptyList()) }
        stubSearch(apple)
        val response = CompletableDeferred<ApiResult<WatchlistItem>>()
        apiClient.addToWatchlist = { _, _, _ -> response.await() }
        val viewModel = viewModel()
        watchlistRepository.refresh()
        typeAndPause(viewModel, "app")

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
        apiClient.addToWatchlist = { _, _, _ -> ApiResult.Failure(full) }
        val viewModel = viewModel()
        typeAndPause(viewModel, "app")

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
        typeAndPause(viewModel, "app")
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

        typeAndPause(viewModel, "app")

        assertEquals(listOf(true, false), viewModel.state.results.map { it.inWatchlist })
    }

    @Test
    fun `profile change clears the query and the results`() = runTest {
        stubSearch(apple)
        val viewModel = viewModel()
        typeAndPause(viewModel, "app")
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
        typeAndPause(viewModel, "app")
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

        typeAndPause(viewModel, "app")

        assertEquals(listOf("search(p2, app)"), searches)
    }
}
