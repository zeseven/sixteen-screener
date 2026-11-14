package com.sixteenscreener.app.ui.sources

import com.sixteenscreener.app.data.repository.DefaultSourceRepository
import com.sixteenscreener.app.domain.model.ActiveProfile
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.ProfileId
import com.sixteenscreener.app.domain.model.Source
import com.sixteenscreener.app.testing.FakeApiClient
import com.sixteenscreener.app.testing.FakeProfileRepository
import com.sixteenscreener.app.testing.MainDispatcherRule
import com.sixteenscreener.app.testing.Sources.source
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SourcesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val apiClient = FakeApiClient()
    private val profileRepository = FakeProfileRepository(ActiveProfile(ProfileId("p1"), "Viktor"))
    private val finnhub = source(isDefault = true)

    private lateinit var repository: DefaultSourceRepository

    private fun TestScope.viewModel(): SourcesViewModel {
        if (!::repository.isInitialized) repository = DefaultSourceRepository(apiClient, profileRepository, backgroundScope)
        return SourcesViewModel(repository)
    }

    @Test
    fun `refresh loads the sources`() = runTest {
        val response = CompletableDeferred<ApiResult<List<Source>>>()
        apiClient.getSources = { response.await() }
        val viewModel = viewModel()

        viewModel.refresh()
        viewModel.refresh()
        assertTrue(viewModel.uiState.value.isRefreshing)
        assertEquals(1, apiClient.calls.size)

        response.complete(ApiResult.Success(listOf(finnhub)))
        assertEquals(SourcesUiState(sources = listOf(finnhub)), viewModel.uiState.value)
    }

    @Test
    fun `cached sources are shown at once`() = runTest {
        apiClient.getSources = { ApiResult.Success(listOf(finnhub)) }
        repository = DefaultSourceRepository(apiClient, profileRepository, backgroundScope)
        repository.getBalanceSheetSources()

        val viewModel = viewModel()

        assertEquals(listOf(finnhub), viewModel.uiState.value.sources)
        assertEquals(1, apiClient.calls.size)
    }

    @Test
    fun `failed refresh keeps the cached sources with an error message`() = runTest {
        apiClient.getSources = { ApiResult.Success(listOf(finnhub)) }
        val viewModel = viewModel()
        viewModel.refresh()
        val failure = ApiError.Network(isTimeout = false)
        apiClient.getSources = { ApiResult.Failure(failure) }

        viewModel.refresh()

        val state = viewModel.uiState.value
        assertEquals(listOf(finnhub), state.sources)
        assertEquals(failure, state.loadError)
        assertEquals(failure, state.message)
        assertFalse(state.isRefreshing)

        viewModel.onMessageShown(failure)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun `successful refresh clears the load error`() = runTest {
        apiClient.getSources = { ApiResult.Failure(ApiError.Unauthorized) }
        val viewModel = viewModel()
        viewModel.refresh()
        apiClient.getSources = { ApiResult.Success(listOf(finnhub)) }

        viewModel.refresh()

        assertNull(viewModel.uiState.value.loadError)
    }
}
