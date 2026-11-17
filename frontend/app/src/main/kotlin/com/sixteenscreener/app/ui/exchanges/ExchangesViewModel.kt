package com.sixteenscreener.app.ui.exchanges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sixteenscreener.app.data.repository.ExchangeRepository
import com.sixteenscreener.app.domain.ExchangeFilter
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.Exchange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExchangesUiState(
    /** Exchange table in backend order, `null` until loaded. */
    val exchanges: List<Exchange>? = null,
    val isLoading: Boolean = false,
    /** Failure of the last load, `null` after a successful one. */
    val loadError: ApiError? = null,
    /** Filter text as typed. */
    val query: String = "",
) {
    /** Rows of [exchanges] matching [query], empty until loaded. */
    val filteredExchanges: List<Exchange> = exchanges?.let { ExchangeFilter.apply(it, query) }.orEmpty()
}

/**
 * Exchanges tab: loads the exchange table when opened and filters it in memory while typing.
 */
@HiltViewModel
class ExchangesViewModel @Inject constructor(
    private val exchangeRepository: ExchangeRepository,
) : ViewModel() {

    private val state = MutableStateFlow(ExchangesUiState())
    private var loadJob: Job? = null

    val uiState: StateFlow<ExchangesUiState> = state.asStateFlow()

    init {
        load()
    }

    /** Loads the exchange table unless it is loaded or a load is already running. */
    fun load() {
        if (state.value.exchanges != null || loadJob?.isActive == true) return
        state.update { it.copy(isLoading = true, loadError = null) }
        loadJob = viewModelScope.launch {
            when (val result = exchangeRepository.getExchanges()) {
                is ApiResult.Success -> state.update { it.copy(exchanges = result.value, isLoading = false, loadError = null) }
                is ApiResult.Failure -> state.update { it.copy(isLoading = false, loadError = result.error) }
            }
        }
    }

    fun onQueryChange(query: String) = state.update { it.copy(query = query) }
}
