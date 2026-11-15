package com.sixteenscreener.app.data.repository

import com.sixteenscreener.app.data.remote.ApiClient
import com.sixteenscreener.app.di.ApplicationScope
import com.sixteenscreener.app.domain.WatchlistRowRefresh
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.CompanySummary
import com.sixteenscreener.app.domain.model.ProfileId
import com.sixteenscreener.app.domain.model.Symbol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watchlist of the active profile, kept in memory and shared by the watchlist and search screens.
 * Mutations are applied optimistically and reverted when the backend call fails; state is dropped when the active profile changes.
 */
interface WatchlistRepository {
    /** Companies of the watchlist in backend order, `null` until the watchlist is loaded for the active profile. */
    val watchlist: StateFlow<List<CompanySummary>?>

    /** Symbols known to be in the watchlist: those of the loaded watchlist plus companies added or removed since. */
    val watchlistSymbols: StateFlow<Set<Symbol>>

    /** Loads the watchlist with `GET /watchlist`; the previous list is kept when the call fails. */
    suspend fun refresh(): ApiResult<List<CompanySummary>>

    /**
     * Refreshes the rows of 1 to [WatchlistRowRefresh.MAX_SYMBOLS_PER_REQUEST] [symbols] with `GET /watchlist/refresh` and replaces
     * them in the loaded watchlist by symbol; rows removed meanwhile stay removed and the order is kept.
     */
    suspend fun refreshRows(symbols: List<Symbol>): ApiResult<List<CompanySummary>>

    /** Adds [company] with `PUT`, showing it at [index] (or at the end) until the call fails. */
    suspend fun add(company: CompanySummary, index: Int? = null): ApiResult<Unit>

    /** Removes the company with `DELETE`, hiding it until the call fails. */
    suspend fun remove(symbol: Symbol): ApiResult<Unit>
}

@Singleton
class DefaultWatchlistRepository @Inject constructor(
    private val apiClient: ApiClient,
    private val profileRepository: ProfileRepository,
    @ApplicationScope applicationScope: CoroutineScope,
) : WatchlistRepository {

    private val companies = MutableStateFlow<List<CompanySummary>?>(null)
    private val symbols = MutableStateFlow<Set<Symbol>>(emptySet())
    private val stateLock = Any()
    private val mutationMutex = Mutex()
    private var stateOwner: ProfileId? = null

    override val watchlist: StateFlow<List<CompanySummary>?> = companies.asStateFlow()
    override val watchlistSymbols: StateFlow<Set<Symbol>> = symbols.asStateFlow()

    init {
        profileRepository.onActiveProfileChange(applicationScope, ::resetUnlessOwnedBy)
    }

    override suspend fun refresh(): ApiResult<List<CompanySummary>> = profileRepository.withActiveProfile { profileId ->
        apiClient.getWatchlist(profileId)
            .clearProfileIfNotFound(profileRepository, profileId)
            .also { result ->
                if (result is ApiResult.Success && isActive(profileId)) {
                    synchronized(stateLock) {
                        stateOwner = profileId
                        updateState { _, _ -> result.value to result.value.symbols() }
                    }
                }
            }
    }

    override suspend fun refreshRows(symbols: List<Symbol>): ApiResult<List<CompanySummary>> {
        require(symbols.size in 1..WatchlistRowRefresh.MAX_SYMBOLS_PER_REQUEST) { "1 to 50 symbols expected, got ${symbols.size}" }
        return profileRepository.withActiveProfile { profileId ->
            apiClient.refreshWatchlistRows(profileId, symbols)
                .clearProfileIfNotFound(profileRepository, profileId)
                .also { result ->
                    if (result is ApiResult.Success && isActive(profileId)) mergeRows(profileId, result.value)
                }
        }
    }

    override suspend fun add(company: CompanySummary, index: Int?): ApiResult<Unit> = profileRepository.withActiveProfile { profileId ->
        val symbol = company.symbol
        val addedNow = updateState { list, known ->
            if (symbol in known) {
                list to known
            } else {
                list?.insertedAt(index, company.copy(inWatchlist = true)) to known + symbol
            }
        }
        mutationMutex.withLock { apiClient.addToWatchlist(profileId, symbol) }
            .also { result ->
                if (result is ApiResult.Failure && addedNow && isActive(profileId)) {
                    updateState { list, known -> list?.filterNot { it.symbol == symbol } to known - symbol }
                }
            }
    }

    override suspend fun remove(symbol: Symbol): ApiResult<Unit> = profileRepository.withActiveProfile { profileId ->
        val previousList = companies.value
        val removed = previousList?.firstOrNull { it.symbol == symbol }
        val removedIndex = previousList?.indexOfFirst { it.symbol == symbol } ?: -1
        val removedNow = updateState { list, known ->
            list?.filterNot { it.symbol == symbol } to known - symbol
        }
        mutationMutex.withLock { apiClient.removeFromWatchlist(profileId, symbol) }
            .also { result ->
                if (result is ApiResult.Failure && removedNow && isActive(profileId)) {
                    updateState { list, known ->
                        val restoredList = if (removed == null || list == null || list.any { it.symbol == symbol }) {
                            list
                        } else {
                            list.insertedAt(removedIndex, removed)
                        }
                        restoredList to known + symbol
                    }
                }
            }
    }

    private suspend fun isActive(profileId: ProfileId) = profileRepository.activeProfileId() == profileId

    /** Replaces rows of the watchlist owned by [profileId] with [rows] of the same symbol; rows not in the watchlist are ignored. */
    private fun mergeRows(profileId: ProfileId, rows: List<CompanySummary>) = synchronized(stateLock) {
        if (stateOwner == profileId) {
            val rowsBySymbol = rows.associateBy { it.symbol }
            updateState { list, known -> list?.map { rowsBySymbol[it.symbol] ?: it } to known }
        }
    }

    /** Drops the state of the previous profile, unless the new profile's watchlist was already loaded before the change was observed. */
    private fun resetUnlessOwnedBy(profileId: ProfileId?) = synchronized(stateLock) {
        if (stateOwner != profileId) {
            stateOwner = profileId
            updateState { _, _ -> null to emptySet() }
        }
    }

    /** Replaces the state with [transform] of the current one; returns whether the known symbols changed. */
    private fun updateState(transform: (List<CompanySummary>?, Set<Symbol>) -> Pair<List<CompanySummary>?, Set<Symbol>>): Boolean =
        synchronized(stateLock) {
            val previousSymbols = symbols.value
            val (list, known) = transform(companies.value, previousSymbols)
            companies.value = list
            symbols.value = known
            known != previousSymbols
        }

    private fun List<CompanySummary>.symbols(): Set<Symbol> = mapTo(LinkedHashSet()) { it.symbol }

    private fun List<CompanySummary>.insertedAt(index: Int?, company: CompanySummary): List<CompanySummary> =
        toMutableList().apply { add(index?.coerceIn(0, size) ?: size, company) }
}
