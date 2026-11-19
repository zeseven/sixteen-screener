package com.sixteenscreener.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sixteenscreener.app.data.repository.ProfileRepository
import com.sixteenscreener.app.data.repository.SearchRepository
import com.sixteenscreener.app.data.repository.WatchlistRepository
import com.sixteenscreener.app.data.repository.onActiveProfileChange
import com.sixteenscreener.app.domain.SearchRequest
import com.sixteenscreener.app.domain.SearchDebounce
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.SearchItem
import com.sixteenscreener.app.domain.model.SearchResults
import com.sixteenscreener.app.domain.model.SearchStatus
import com.sixteenscreener.app.domain.model.WatchlistItem
import com.sixteenscreener.app.domain.debounceSearch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Status line under the search input. */
enum class SearchNotice { LIMIT_REACHED, SOURCE_ERROR, NOTHING_FOUND, REQUEST_FAILED }

/** Adding [company] to the watchlist failed; shown as a snackbar. */
data class AddFailedMessage(val company: SearchItem, val error: ApiError)

data class SearchUiState(
    /** Input as typed, at most [SearchDebounce.MAX_INPUT_LENGTH] characters. */
    val query: String = "",
    /** Found companies; `inWatchlist` follows the shared watchlist state. */
    val results: List<SearchItem> = emptyList(),
    val isSearching: Boolean = false,
    val notice: SearchNotice? = null,
    val message: AddFailedMessage? = null,
)

/**
 * Search tab: the query is searched after a pause in typing or at once on the keyboard's search action ([debounceSearch]), a newer
 * query cancels the running request and an empty input clears the results without a request. Adding a result updates the shared
 * watchlist at once (the sector is known once the backend returns the stored item) and reverts on failure.
 * A profile change clears the query and the results.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val watchlistRepository: WatchlistRepository,
    profileRepository: ProfileRepository,
) : ViewModel() {

    private data class SearchOutcome(
        val hits: List<SearchItem> = emptyList(),
        val isSearching: Boolean = false,
        val notice: SearchNotice? = null,
    )

    private val query = MutableStateFlow("")
    private val submittedQuery = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val outcome = MutableStateFlow(SearchOutcome())
    private val message = MutableStateFlow<AddFailedMessage?>(null)

    val uiState: StateFlow<SearchUiState> = combine(
        query,
        outcome,
        watchlistRepository.watchlist,
        watchlistRepository.watchlistSymbols,
        message,
    ) { query, outcome, watchlist, symbols, message ->
        SearchUiState(
            query = query,
            results = outcome.hits.map { hit ->
                hit.copy(inWatchlist = hit.symbol in symbols || (watchlist == null && hit.inWatchlist))
            },
            isSearching = outcome.isSearching,
            notice = outcome.notice,
            message = message,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SearchUiState())

    init {
        profileRepository.onActiveProfileChange(viewModelScope) { resetForNewProfile() }
        viewModelScope.launch {
            query.debounceSearch(submittedQuery).collectLatest { request ->
                when (request) {
                    SearchRequest.Clear -> outcome.value = SearchOutcome()
                    is SearchRequest.Query -> {
                        outcome.update { it.copy(isSearching = true) }
                        outcome.value = searchRepository.search(request.query).toOutcome()
                    }
                }
            }
        }
    }

    fun onQueryChange(text: String) {
        query.value = text.take(SearchDebounce.MAX_INPUT_LENGTH)
    }

    /** Keyboard's search action: searches the current query at once. */
    fun onSearch() {
        submittedQuery.tryEmit(query.value)
    }

    fun onAdd(company: SearchItem) {
        viewModelScope.launch {
            val result = watchlistRepository.add(WatchlistItem.of(company))
            if (result is ApiResult.Failure) message.value = AddFailedMessage(company, result.error)
        }
    }

    /** Clears [shown] unless a newer message replaced it. */
    fun onMessageShown(shown: AddFailedMessage) = message.update { if (it == shown) null else it }

    private fun resetForNewProfile() {
        query.value = ""
        outcome.value = SearchOutcome()
        message.value = null
    }

    private fun ApiResult<SearchResults>.toOutcome(): SearchOutcome = when (this) {
        is ApiResult.Failure -> SearchOutcome(notice = SearchNotice.REQUEST_FAILED)
        is ApiResult.Success -> when (value.status) {
            SearchStatus.OK -> SearchOutcome(hits = value.items, notice = SearchNotice.NOTHING_FOUND.takeIf { value.items.isEmpty() })
            SearchStatus.LIMIT_REACHED -> SearchOutcome(notice = SearchNotice.LIMIT_REACHED)
            SearchStatus.SOURCE_ERROR, SearchStatus.UNKNOWN -> SearchOutcome(notice = SearchNotice.SOURCE_ERROR)
        }
    }
}
