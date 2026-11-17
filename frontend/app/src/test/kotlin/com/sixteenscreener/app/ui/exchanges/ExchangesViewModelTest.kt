package com.sixteenscreener.app.ui.exchanges

import com.sixteenscreener.app.data.repository.DefaultExchangeRepository
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.Exchange
import com.sixteenscreener.app.testing.Exchanges.all
import com.sixteenscreener.app.testing.Exchanges.paris
import com.sixteenscreener.app.testing.Exchanges.tsxVenture
import com.sixteenscreener.app.testing.Exchanges.xetra
import com.sixteenscreener.app.testing.FakeApiClient
import com.sixteenscreener.app.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExchangesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val apiClient = FakeApiClient()
    private val repository = DefaultExchangeRepository(apiClient)

    private fun viewModel() = ExchangesViewModel(repository)

    @Test
    fun `opening the screen loads the exchanges`() = runTest {
        val response = CompletableDeferred<ApiResult<List<Exchange>>>()
        apiClient.getExchanges = { response.await() }

        val viewModel = viewModel()
        assertTrue(viewModel.uiState.value.isLoading)
        viewModel.load()
        assertEquals(1, apiClient.calls.size)

        response.complete(ApiResult.Success(all))
        val state = viewModel.uiState.value
        assertEquals(ExchangesUiState(exchanges = all), state)
        assertEquals(all, state.filteredExchanges)
    }

    @Test
    fun `loaded table is not requested again`() = runTest {
        apiClient.getExchanges = { ApiResult.Success(all) }
        val viewModel = viewModel()

        viewModel.load()
        viewModel()

        assertEquals(1, apiClient.calls.size)
    }

    @Test
    fun `typing filters the rows in memory`() = runTest {
        apiClient.getExchanges = { ApiResult.Success(all) }
        val viewModel = viewModel()

        viewModel.onQueryChange("eur")
        assertEquals(listOf(xetra, paris), viewModel.uiState.value.filteredExchanges)
        assertEquals("eur", viewModel.uiState.value.query)

        viewModel.onQueryChange(" .v ")
        assertEquals(listOf(tsxVenture), viewModel.uiState.value.filteredExchanges)

        viewModel.onQueryChange("")
        assertEquals(all, viewModel.uiState.value.filteredExchanges)
        assertEquals(all, viewModel.uiState.value.exchanges)
        assertEquals(1, apiClient.calls.size)
    }

    @Test
    fun `filter typed before the load applies to the loaded rows`() = runTest {
        val response = CompletableDeferred<ApiResult<List<Exchange>>>()
        apiClient.getExchanges = { response.await() }
        val viewModel = viewModel()

        viewModel.onQueryChange("xtsx")
        assertEquals(emptyList<Exchange>(), viewModel.uiState.value.filteredExchanges)
        response.complete(ApiResult.Success(all))

        assertEquals(listOf(tsxVenture), viewModel.uiState.value.filteredExchanges)
    }

    @Test
    fun `failed load shows the error and retry loads again`() = runTest {
        val failure = ApiError.Network(isTimeout = false)
        apiClient.getExchanges = { ApiResult.Failure(failure) }
        val viewModel = viewModel()

        val failed = viewModel.uiState.value
        assertEquals(failure, failed.loadError)
        assertFalse(failed.isLoading)
        assertNull(failed.exchanges)

        apiClient.getExchanges = { ApiResult.Success(all) }
        viewModel.load()

        assertEquals(ExchangesUiState(exchanges = all), viewModel.uiState.value)
        assertEquals(2, apiClient.calls.size)
    }

    @Test
    fun `retry clears the error while loading`() = runTest {
        apiClient.getExchanges = { ApiResult.Failure(ApiError.Unauthorized) }
        val viewModel = viewModel()
        val response = CompletableDeferred<ApiResult<List<Exchange>>>()
        apiClient.getExchanges = { response.await() }

        viewModel.load()

        assertTrue(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.loadError)
        response.complete(ApiResult.Success(all))
    }
}
