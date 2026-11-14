package com.sixteenscreener.app.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sixteenscreener.app.data.repository.ProfileRepository
import com.sixteenscreener.app.data.repository.WatchlistRepository
import com.sixteenscreener.app.data.repository.onActiveProfileChange
import com.sixteenscreener.app.domain.MarketFilter
import com.sixteenscreener.app.domain.WatchlistFilter
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.CompanySummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One-time message of the watchlist tab, shown as a snackbar. */
sealed interface WatchlistMessage {
    data class LoadFailed(val error: ApiError) : WatchlistMessage

    /** [company] was removed from [index]; offers undo. */
    data class Removed(val company: CompanySummary, val index: Int) : WatchlistMessage

    data class RemoveFailed(val company: CompanySummary, val error: ApiError) : WatchlistMessage

    data class UndoFailed(val company: CompanySummary, val error: ApiError) : WatchlistMessage
}

data class WatchlistUiState(
    /** Companies matching [filter]. */
    val companies: List<CompanySummary> = emptyList(),
    /** Whether the watchlist has been loaded for the active profile. */
    val isLoaded: Boolean = false,
    /** Whether the loaded watchlist has any company, regardless of the filter. */
    val hasCompanies: Boolean = false,
    val filter: WatchlistFilter = WatchlistFilter(),
    /** Sectors offered by the sector filter. */
    val sectors: List<String> = emptyList(),
    val isRefreshing: Boolean = false,
    /** Failure of the last load, `null` after a successful one. */
    val loadError: ApiError? = null,
    val message: WatchlistMessage? = null,
)

/**
 * Watchlist tab: loads the watchlist when shown, filters it in memory (filters are not persisted, ADR-045) and removes companies
 * optimistically with undo. A profile change resets the screen and loads the new profile's watchlist.
 */
@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val watchlistRepository: WatchlistRepository,
    profileRepository: ProfileRepository,
) : ViewModel() {

    private data class ScreenState(
        val isRefreshing: Boolean = false,
        val loadError: ApiError? = null,
        val message: WatchlistMessage? = null,
    )

    private val filter = MutableStateFlow(WatchlistFilter())
    private val screenState = MutableStateFlow(ScreenState())
    private var refreshJob: Job? = null

    val uiState: StateFlow<WatchlistUiState> =
        combine(watchlistRepository.watchlist, filter, screenState) { watchlist, filter, screen ->
            val companies = watchlist.orEmpty()
            val sectors = WatchlistFilter.sectorsOf(companies)
            WatchlistUiState(
                companies = filter.apply(companies),
                isLoaded = watchlist != null,
                hasCompanies = companies.isNotEmpty(),
                filter = filter,
                sectors = filter.sector?.takeUnless { it in sectors }?.let { sectors + it } ?: sectors,
                isRefreshing = screen.isRefreshing,
                loadError = screen.loadError,
                message = screen.message,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, WatchlistUiState())

    init {
        profileRepository.onActiveProfileChange(viewModelScope) { resetForNewProfile() }
    }

    /** Loads the watchlist unless a load is already running; a failure keeps the previous list. */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        screenState.update { it.copy(isRefreshing = true) }
        refreshJob = viewModelScope.launch {
            when (val result = watchlistRepository.refresh()) {
                is ApiResult.Success -> {
                    filter.update { it.reconciledWith(result.value) }
                    screenState.update { it.copy(isRefreshing = false, loadError = null) }
                }
                is ApiResult.Failure -> screenState.update {
                    it.copy(isRefreshing = false, loadError = result.error, message = WatchlistMessage.LoadFailed(result.error))
                }
            }
        }
    }

    fun onMarketSelected(market: MarketFilter) = filter.update { it.copy(market = market) }

    /** Selects [sector], `null` for all sectors. */
    fun onSectorSelected(sector: String?) = filter.update { it.copy(sector = sector) }

    fun onRemove(company: CompanySummary) {
        val index = watchlistRepository.watchlist.value?.indexOfFirst { it.symbol == company.symbol } ?: -1
        showMessage(WatchlistMessage.Removed(company, index.coerceAtLeast(0)))
        viewModelScope.launch {
            val result = watchlistRepository.remove(company.symbol)
            if (result is ApiResult.Failure) showMessage(WatchlistMessage.RemoveFailed(company, result.error))
        }
    }

    fun onUndo(removed: WatchlistMessage.Removed) {
        onMessageShown(removed)
        viewModelScope.launch {
            val result = watchlistRepository.add(removed.company, removed.index)
            if (result is ApiResult.Failure) showMessage(WatchlistMessage.UndoFailed(removed.company, result.error))
        }
    }

    /** Clears [message] once shown, unless a newer message replaced it. */
    fun onMessageShown(message: WatchlistMessage) = screenState.update { if (it.message == message) it.copy(message = null) else it }

    private fun resetForNewProfile() {
        refreshJob?.cancel()
        filter.value = WatchlistFilter()
        screenState.value = ScreenState()
        refresh()
    }

    private fun showMessage(message: WatchlistMessage) = screenState.update { it.copy(message = message) }
}
