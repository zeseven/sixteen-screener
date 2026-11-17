package com.sixteenscreener.app.data.repository

import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.Exchange
import com.sixteenscreener.app.testing.Exchanges.all
import com.sixteenscreener.app.testing.FakeApiClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultExchangeRepositoryTest {

    private val apiClient = FakeApiClient()
    private val repository = DefaultExchangeRepository(apiClient)

    @Test
    fun `first call loads the exchanges from the backend`() = runTest {
        apiClient.getExchanges = { ApiResult.Success(all) }

        assertEquals(ApiResult.Success(all), repository.getExchanges())
        assertEquals(listOf("getExchanges()"), apiClient.calls)
    }

    @Test
    fun `later calls return the cached exchanges without a request`() = runTest {
        apiClient.getExchanges = { ApiResult.Success(all) }
        repository.getExchanges()
        apiClient.getExchanges = { ApiResult.Failure(ApiError.Network(isTimeout = false)) }

        assertEquals(ApiResult.Success(all), repository.getExchanges())
        assertEquals(ApiResult.Success(all), repository.getExchanges())
        assertEquals(1, apiClient.calls.size)
    }

    @Test
    fun `failed load is not cached and the next call asks again`() = runTest {
        val failure = ApiResult.Failure(ApiError.Network(isTimeout = true))
        apiClient.getExchanges = { failure }

        assertEquals(failure, repository.getExchanges())

        apiClient.getExchanges = { ApiResult.Success(all) }
        assertEquals(ApiResult.Success(all), repository.getExchanges())
        assertEquals(2, apiClient.calls.size)
    }

    @Test
    fun `empty table is cached like any other`() = runTest {
        apiClient.getExchanges = { ApiResult.Success(emptyList()) }
        repository.getExchanges()

        assertEquals(ApiResult.Success(emptyList<Exchange>()), repository.getExchanges())
        assertEquals(1, apiClient.calls.size)
    }

    @Test
    fun `concurrent first calls make one request`() = runTest(UnconfinedTestDispatcher()) {
        val response = CompletableDeferred<ApiResult<List<Exchange>>>()
        apiClient.getExchanges = { response.await() }

        val first = async { repository.getExchanges() }
        val second = async { repository.getExchanges() }
        response.complete(ApiResult.Success(all))

        assertEquals(ApiResult.Success(all), first.await())
        assertEquals(ApiResult.Success(all), second.await())
        assertEquals(listOf("getExchanges()"), apiClient.calls)
    }
}
