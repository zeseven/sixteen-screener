package com.sixteenscreener.app.data.repository

import com.sixteenscreener.app.data.remote.ApiClient
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.Exchange
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Static exchange table, kept in memory for the lifetime of the app process after the first successful load; never persisted.
 */
interface ExchangeRepository {
    /**
     * The exchange table in backend order. Loads it with `GET /exchanges` only while nothing is cached; a failed load is not
     * cached, so the next call asks the backend again.
     */
    suspend fun getExchanges(): ApiResult<List<Exchange>>
}

@Singleton
class DefaultExchangeRepository @Inject constructor(
    private val apiClient: ApiClient,
) : ExchangeRepository {

    private val loadMutex = Mutex()

    @Volatile
    private var cached: List<Exchange>? = null

    override suspend fun getExchanges(): ApiResult<List<Exchange>> =
        cached?.let { ApiResult.Success(it) } ?: loadMutex.withLock {
            cached?.let { ApiResult.Success(it) } ?: apiClient.getExchanges().also { result ->
                if (result is ApiResult.Success) cached = result.value
            }
        }
}
