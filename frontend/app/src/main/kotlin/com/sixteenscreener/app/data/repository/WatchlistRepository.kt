package com.sixteenscreener.app.data.repository

import com.sixteenscreener.app.data.remote.ApiClient
import com.sixteenscreener.app.di.ApplicationScope
import com.sixteenscreener.app.domain.WatchlistRowRefresh
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.MarketData
import com.sixteenscreener.app.domain.model.ProfileId
import com.sixteenscreener.app.domain.model.RowMarketData
import com.sixteenscreener.app.domain.model.Symbol
import com.sixteenscreener.app.domain.model.WatchlistItem
import com.sixteenscreener.app.domain.model.failed
import com.sixteenscreener.app.domain.model.loadedWith
import com.sixteenscreener.app.domain.model.loading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watchlist of the active profile, kept in memory and shared by the watchlist, search and detailed screens.
 * Mutations are applied optimistically and reverted when the backend call fails; state is dropped when the active profile changes.
 * Market data is kept per row separately from the entries, survives watchlist reloads and is dropped for removed rows (ADR-058).
 */
interface WatchlistRepository {
    /** Entries of the watchlist in backend order, `null` until the watchlist is loaded for the active profile. */
    val watchlist: StateFlow<List<WatchlistItem>?>

    /** Symbols known to be in the watchlist: those of the loaded watchlist plus companies added or removed since. */
    val watchlistSymbols: StateFlow<Set<Symbol>>

    /** Market data of the watchlist rows that have been requested at least once. */
    val marketData: StateFlow<Map<Symbol, RowMarketData>>

    /** Loads the watchlist with `GET /watchlist`; the previous list is kept when the call fails. */
    suspend fun refresh(): ApiResult<List<WatchlistItem>>

    /**
     * Loads the market data of 1 to [WatchlistRowRefresh.MAX_SYMBOLS_PER_REQUEST] [symbols] with `GET /watchlist/market-data`.
     * The rows are marked as loading at once; a failed call marks them as failed and keeps their previous values.
     */
    suspend fun loadMarketData(symbols: List<Symbol>): ApiResult<List<MarketData>>

    /** Adds [item] with `PUT`, showing it at [index] (or at the end) until the call fails; on success the stored item replaces it. */
    suspend fun add(item: WatchlistItem, index: Int? = null): ApiResult<WatchlistItem>

    /** Removes the company with `DELETE`, hiding it until the call fails. */
    suspend fun remove(symbol: Symbol): ApiResult<Unit>
}

@Singleton
class DefaultWatchlistRepository @Inject constructor(
    private val apiClient: ApiClient,
    private val profileRepository: ProfileRepository,
    @ApplicationScope applicationScope: CoroutineScope,
) : WatchlistRepository {

    private val items = MutableStateFlow<List<WatchlistItem>?>(null)
    private val symbols = MutableStateFlow<Set<Symbol>>(emptySet())
    private val rowMarketData = MutableStateFlow<Map<Symbol, RowMarketData>>(emptyMap())
    private val stateLock = Any()
    private val mutationMutex = Mutex()
    private var stateOwner: ProfileId? = null

    override val watchlist: StateFlow<List<WatchlistItem>?> = items.asStateFlow()
    override val watchlistSymbols: StateFlow<Set<Symbol>> = symbols.asStateFlow()
    override val marketData: StateFlow<Map<Symbol, RowMarketData>> = rowMarketData.asStateFlow()

    init {
        profileRepository.onActiveProfileChange(applicationScope, ::resetUnlessOwnedBy)
    }

    override suspend fun refresh(): ApiResult<List<WatchlistItem>> = profileRepository.withActiveProfile { profileId ->
        apiClient.getWatchlist(profileId)
            .clearProfileIfNotFound(profileRepository, profileId)
            .also { result ->
                if (result is ApiResult.Success && isActive(profileId)) {
                    synchronized(stateLock) {
                        if (stateOwner != profileId) rowMarketData.value = emptyMap()
                        stateOwner = profileId
                        updateState { _, _ -> result.value to result.value.symbols() }
                    }
                }
            }
    }

    override suspend fun loadMarketData(symbols: List<Symbol>): ApiResult<List<MarketData>> {
        require(symbols.size in 1..WatchlistRowRefresh.MAX_SYMBOLS_PER_REQUEST) { "1 to 50 symbols expected, got ${symbols.size}" }
        return profileRepository.withActiveProfile { profileId ->
            updateMarketData(profileId, symbols) { _, previous -> previous.loading() }
            apiClient.getWatchlistMarketData(profileId, symbols)
                .clearProfileIfNotFound(profileRepository, profileId)
                .also { result ->
                    if (isActive(profileId)) {
                        when (result) {
                            is ApiResult.Success -> applyMarketData(profileId, symbols, result.value)
                            is ApiResult.Failure -> updateMarketData(profileId, symbols) { _, previous -> previous.failed() }
                        }
                    }
                }
        }
    }

    override suspend fun add(item: WatchlistItem, index: Int?): ApiResult<WatchlistItem> = profileRepository.withActiveProfile { profileId ->
        val symbol = item.symbol
        val addedNow = updateState { list, known ->
            if (symbol in known) list to known else list?.insertedAt(index, item) to known + symbol
        }
        mutationMutex.withLock { apiClient.addToWatchlist(profileId, symbol) }
            .also { result ->
                if (!isActive(profileId)) return@also
                when (result) {
                    is ApiResult.Success -> updateState { list, known ->
                        list?.map { if (it.symbol == symbol) result.value else it } to known
                    }
                    is ApiResult.Failure -> if (addedNow) {
                        updateState { list, known -> list?.filterNot { it.symbol == symbol } to known - symbol }
                    }
                }
            }
    }

    override suspend fun remove(symbol: Symbol): ApiResult<Unit> = profileRepository.withActiveProfile { profileId ->
        val previousList = items.value
        val removed = previousList?.firstOrNull { it.symbol == symbol }
        val removedIndex = previousList?.indexOfFirst { it.symbol == symbol } ?: -1
        val removedMarketData = rowMarketData.value[symbol]
        val removedNow = updateState { list, known ->
            list?.filterNot { it.symbol == symbol } to known - symbol
        }
        mutationMutex.withLock { apiClient.removeFromWatchlist(profileId, symbol) }
            .also { result ->
                if (result is ApiResult.Failure && removedNow && isActive(profileId)) {
                    synchronized(stateLock) {
                        updateState { list, known ->
                            val restoredList = if (removed == null || list == null || list.any { it.symbol == symbol }) {
                                list
                            } else {
                                list.insertedAt(removedIndex, removed)
                            }
                            restoredList to known + symbol
                        }
                        if (removedMarketData != null && symbol !in rowMarketData.value) {
                            rowMarketData.value += symbol to removedMarketData
                        }
                    }
                }
            }
    }

    private suspend fun isActive(profileId: ProfileId) = profileRepository.activeProfileId() == profileId

    /** Applies [transform] to the market data of the [requested] rows still in the watchlist owned by [profileId]. */
    private fun updateMarketData(
        profileId: ProfileId,
        requested: List<Symbol>,
        transform: (Symbol, RowMarketData?) -> RowMarketData,
    ) = synchronized(stateLock) {
        if (stateOwner == profileId) {
            val current = rowMarketData.value
            rowMarketData.value = current + requested.filter { it in symbols.value }.associateWith { transform(it, current[it]) }
        }
    }

    /** Stores the received [rows]; [requested] rows the backend skipped are marked as failed. */
    private fun applyMarketData(profileId: ProfileId, requested: List<Symbol>, rows: List<MarketData>) {
        val rowsBySymbol = rows.associateBy { it.symbol }
        updateMarketData(profileId, requested) { symbol, previous ->
            rowsBySymbol[symbol]?.let(previous::loadedWith) ?: previous.failed()
        }
    }

    /** Drops the state of the previous profile, unless the new profile's watchlist was already loaded before the change was observed. */
    private fun resetUnlessOwnedBy(profileId: ProfileId?) = synchronized(stateLock) {
        if (stateOwner != profileId) {
            stateOwner = profileId
            updateState { _, _ -> null to emptySet() }
        }
    }

    /**
     * Replaces the state with [transform] of the current one and drops market data of rows no longer known to be in the watchlist;
     * returns whether the known symbols changed.
     */
    private fun updateState(transform: (List<WatchlistItem>?, Set<Symbol>) -> Pair<List<WatchlistItem>?, Set<Symbol>>): Boolean =
        synchronized(stateLock) {
            val previousSymbols = symbols.value
            val (list, known) = transform(items.value, previousSymbols)
            items.value = list
            symbols.value = known
            if (rowMarketData.value.keys.any { it !in known }) rowMarketData.value = rowMarketData.value.filterKeys { it in known }
            known != previousSymbols
        }

    private fun List<WatchlistItem>.symbols(): Set<Symbol> = mapTo(LinkedHashSet()) { it.symbol }

    private fun List<WatchlistItem>.insertedAt(index: Int?, item: WatchlistItem): List<WatchlistItem> =
        toMutableList().apply { add(index?.coerceIn(0, size) ?: size, item) }
}
