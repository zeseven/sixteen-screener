package com.sixteenscreener.app.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sixteenscreener.app.data.repository.ProfileRepository
import com.sixteenscreener.app.data.repository.WatchlistRepository
import com.sixteenscreener.app.data.repository.onActiveProfileChange
import com.sixteenscreener.app.domain.MarketFilter
import com.sixteenscreener.app.domain.TagRules
import com.sixteenscreener.app.domain.VisibleRows
import com.sixteenscreener.app.domain.WatchlistFilter
import com.sixteenscreener.app.domain.WatchlistRowRefresh
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.RowMarketData
import com.sixteenscreener.app.domain.model.Symbol
import com.sixteenscreener.app.domain.model.Tag
import com.sixteenscreener.app.domain.model.WatchlistItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One-time message of the watchlist tab, shown as a snackbar. */
sealed interface WatchlistMessage {
    data class LoadFailed(val error: ApiError) : WatchlistMessage

    /** [company] was removed from [index]; offers undo. */
    data class Removed(val company: WatchlistItem, val index: Int) : WatchlistMessage

    data class RemoveFailed(val company: WatchlistItem, val error: ApiError) : WatchlistMessage

    data class UndoFailed(val company: WatchlistItem, val error: ApiError) : WatchlistMessage
}

data class WatchlistUiState(
    /** Companies matching [filter]. */
    val companies: List<WatchlistItem> = emptyList(),
    /** Market data of the rows that have been requested at least once. */
    val marketData: Map<Symbol, RowMarketData> = emptyMap(),
    /** Whether the watchlist of the active profile is known, from the device or from the backend. */
    val isLoaded: Boolean = false,
    /** Whether the loaded watchlist has any company, regardless of the filter. */
    val hasCompanies: Boolean = false,
    val filter: WatchlistFilter = WatchlistFilter(),
    /** Sectors offered by the sector filter. */
    val sectors: List<String> = emptyList(),
    /** Tags offered by the tag filter. */
    val tags: List<Tag> = emptyList(),
    val isRefreshing: Boolean = false,
    /** Failure of the last load, `null` after a successful one. */
    val loadError: ApiError? = null,
    val message: WatchlistMessage? = null,
)

/**
 * Watchlist tab: shows the watchlist stored on the device and loads it from the backend when shown (ADR-076), filters it in memory
 * (filters are not persisted, ADR-045) and removes companies optimistically with undo. A profile change resets the screen and loads the
 * new profile's watchlist.
 * Market data is loaded only for the displayed rows plus a buffer (ADR-055, ADR-058): each row once per loaded watchlist when it comes
 * into view, and all of them again when a filter changes. Rows loaded while the stored list was shown are not loaded again when the
 * first backend snapshot replaces it. A failed load is shown on its rows; they are retried when next in view.
 */
@OptIn(FlowPreview::class)
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

    private class RowRefreshTracker {
        val refreshed = mutableSetOf<Symbol>()
        val inFlight = mutableSetOf<Symbol>()
    }

    private data class RowRefreshTrigger(val filter: WatchlistFilter, val rows: List<Symbol>, val visible: VisibleRows, val loadCount: Int)

    private val visibleRows = MutableStateFlow<VisibleRows?>(null)
    private val loadCount = MutableStateFlow(0)
    private var rowRefreshTracker = RowRefreshTracker()
    private var lastRowRefreshFilter: WatchlistFilter? = null
    private var isCachedListShown = false

    val uiState: StateFlow<WatchlistUiState> =
        combine(
            watchlistRepository.watchlist,
            watchlistRepository.marketData,
            filter,
            screenState,
        ) { watchlist, marketData, filter, screen ->
            val companies = watchlist.orEmpty()
            val sectors = WatchlistFilter.sectorsOf(companies)
            val tags = WatchlistFilter.tagsOf(companies)
            WatchlistUiState(
                companies = filter.apply(companies),
                marketData = marketData,
                isLoaded = watchlist != null,
                hasCompanies = companies.isNotEmpty(),
                filter = filter,
                sectors = filter.sector?.takeUnless { it in sectors }?.let { sectors + it } ?: sectors,
                tags = filter.tag?.takeUnless { TagRules.contains(tags, it) }?.let { tags + it } ?: tags,
                isRefreshing = screen.isRefreshing,
                loadError = screen.loadError,
                message = screen.message,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, WatchlistUiState())

    init {
        profileRepository.onActiveProfileChange(viewModelScope) { resetForNewProfile() }
        viewModelScope.launch {
            watchlistRepository.isWatchlistCached.collect { cached -> if (cached) isCachedListShown = true }
        }
        viewModelScope.launch {
            combine(
                uiState.map { it.filter to it.companies.map(WatchlistItem::symbol) }.distinctUntilChanged(),
                visibleRows.filterNotNull(),
                loadCount,
            ) { (filter, rows), visible, loads -> RowRefreshTrigger(filter, rows, visible, loads) }
                .debounce(WatchlistRowRefresh.SETTLE_DELAY)
                .collect(::refreshRowsInView)
        }
    }

    /** Reports the rows currently displayed; their market data is loaded once the visible range settles. */
    fun onVisibleRowsChanged(visible: VisibleRows) {
        visibleRows.value = visible
    }

    /** Loads the watchlist unless a load is already running; a failure keeps the previous list. */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        screenState.update { it.copy(isRefreshing = true) }
        refreshJob = viewModelScope.launch {
            when (val result = watchlistRepository.refresh()) {
                is ApiResult.Success -> {
                    if (isCachedListShown) {
                        isCachedListShown = false
                    } else {
                        rowRefreshTracker.refreshed.clear()
                        loadCount.update { it + 1 }
                    }
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

    /** Selects [tag], `null` for all companies. */
    fun onTagSelected(tag: Tag?) = filter.update { it.copy(tag = tag) }

    fun onRemove(company: WatchlistItem) {
        val index = watchlistRepository.watchlist.value?.indexOfFirst { it.symbol == company.symbol } ?: -1
        rowRefreshTracker.refreshed -= company.symbol
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

    private fun refreshRowsInView(trigger: RowRefreshTrigger) {
        val force = lastRowRefreshFilter.let { it != null && it != trigger.filter }
        lastRowRefreshFilter = trigger.filter
        val tracker = rowRefreshTracker
        WatchlistRowRefresh.symbolsToRefresh(trigger.rows, trigger.visible, tracker.refreshed, tracker.inFlight, force)
            .chunked(WatchlistRowRefresh.MAX_SYMBOLS_PER_REQUEST)
            .forEach { symbols ->
                tracker.inFlight += symbols
                viewModelScope.launch {
                    val result = watchlistRepository.loadMarketData(symbols)
                    tracker.inFlight -= symbols
                    if (result is ApiResult.Success) tracker.refreshed += symbols
                }
            }
    }

    private fun resetForNewProfile() {
        refreshJob?.cancel()
        rowRefreshTracker = RowRefreshTracker()
        lastRowRefreshFilter = null
        isCachedListShown = false
        filter.value = WatchlistFilter()
        screenState.value = ScreenState()
        refresh()
    }

    private fun showMessage(message: WatchlistMessage) = screenState.update { it.copy(message = message) }
}
