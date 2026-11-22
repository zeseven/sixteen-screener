package com.sixteenscreener.app.data.repository

import com.sixteenscreener.app.domain.model.ActiveProfile
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.ProblemDetails
import com.sixteenscreener.app.domain.model.ProfileId
import com.sixteenscreener.app.domain.model.Symbol
import com.sixteenscreener.app.domain.model.Tag
import com.sixteenscreener.app.testing.Companies.item
import com.sixteenscreener.app.testing.FakeApiClient
import com.sixteenscreener.app.testing.FakeProfileRepository
import com.sixteenscreener.app.testing.FakeWatchlistStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultTagRepositoryTest {

    private val profileId = ProfileId("p1")
    private val apiClient = FakeApiClient()
    private val profileRepository = FakeProfileRepository(ActiveProfile(profileId, "Viktor"))
    private val store = FakeWatchlistStore()

    private val king = Tag("chess-king", "Dividend king")
    private val apple = item("AAPL")
    private val sap = item("SAP.DE", name = "SAP SE", tags = listOf(Tag("coin", "Income")))

    private lateinit var watchlistRepository: DefaultWatchlistRepository

    private fun TestScope.repository(): DefaultTagRepository {
        watchlistRepository = DefaultWatchlistRepository(apiClient, profileRepository, store, backgroundScope)
        return DefaultTagRepository(apiClient, profileRepository, watchlistRepository)
    }

    private fun runRepositoryTest(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher()) { block() }

    @Test
    fun `profile tags are requested for the active profile`() = runRepositoryTest {
        apiClient.getProfileTags = { ApiResult.Success(listOf(king)) }

        val result = repository().getProfileTags()

        assertEquals(ApiResult.Success(listOf(king)), result)
        assertEquals(listOf("getProfileTags(p1)"), apiClient.calls)
    }

    @Test
    fun `no request without an active profile`() = runRepositoryTest {
        profileRepository.currentProfile.value = null

        assertEquals(ApiResult.Failure(ApiError.NoProfile), repository().getProfileTags())
        assertEquals(ApiResult.Failure(ApiError.NoProfile), repository().setCompanyTags(apple.symbol, listOf(king)))
        assertEquals(emptyList<String>(), apiClient.calls)
    }

    @Test
    fun `stored tags replace the tags of the company in the watchlist and on the device`() = runRepositoryTest {
        apiClient.getWatchlist = { ApiResult.Success(listOf(apple, sap)) }
        apiClient.setCompanyTags = { _, _, tags -> ApiResult.Success(tags.map { it.copy(text = it.text.trim()) }) }
        val repository = repository()
        watchlistRepository.refresh()

        val result = repository.setCompanyTags(apple.symbol, listOf(Tag("chess-king", " Dividend king ")))

        assertEquals(ApiResult.Success(listOf(king)), result)
        assertEquals(listOf(apple.copy(tags = listOf(king)), sap), watchlistRepository.watchlist.value)
        assertEquals(listOf(apple.copy(tags = listOf(king)), sap), store.stored[profileId])
    }

    @Test
    fun `failed save keeps the tags of the watchlist`() = runRepositoryTest {
        apiClient.getWatchlist = { ApiResult.Success(listOf(apple, sap)) }
        val rejected = ApiError.Http(400, ProblemDetails(null, "Bad Request", 400, "Not in the watchlist"))
        apiClient.setCompanyTags = { _, _, _ -> ApiResult.Failure(rejected) }
        val repository = repository()
        watchlistRepository.refresh()

        val result = repository.setCompanyTags(sap.symbol, emptyList())

        assertEquals(ApiResult.Failure(rejected), result)
        assertEquals(listOf(apple, sap), watchlistRepository.watchlist.value)
    }

    @Test
    fun `profile not found clears the stored profile`() = runRepositoryTest {
        apiClient.setCompanyTags = { _, _, _ -> ApiResult.Failure(ApiError.NotFound(null)) }

        repository().setCompanyTags(Symbol("AAPL"), listOf(king))

        assertNull(profileRepository.currentProfile.value)
    }
}
