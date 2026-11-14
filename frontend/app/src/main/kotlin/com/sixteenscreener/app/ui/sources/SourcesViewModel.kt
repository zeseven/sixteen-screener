package com.sixteenscreener.app.ui.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sixteenscreener.app.data.repository.ProfileRepository
import com.sixteenscreener.app.data.repository.SourceRepository
import com.sixteenscreener.app.data.repository.onActiveProfileChange
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.Source
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

data class SourcesUiState(
    /** Cached sources, `null` until loaded. */
    val sources: List<Source>? = null,
    val isRefreshing: Boolean = false,
    /** Failure of the last load, `null` after a successful one. */
    val loadError: ApiError? = null,
    /** Load failure to show as a snackbar. */
    val message: ApiError? = null,
)

/**
 * Sources tab: shows the cached sources at once and reloads them when shown and on pull-to-refresh.
 */
@HiltViewModel
class SourcesViewModel @Inject constructor(
    private val sourceRepository: SourceRepository,
    profileRepository: ProfileRepository,
) : ViewModel() {

    private data class ScreenState(
        val isRefreshing: Boolean = false,
        val loadError: ApiError? = null,
        val message: ApiError? = null,
    )

    private val screenState = MutableStateFlow(ScreenState())
    private var refreshJob: Job? = null

    val uiState: StateFlow<SourcesUiState> = combine(sourceRepository.sources, screenState) { sources, screen ->
        SourcesUiState(sources = sources, isRefreshing = screen.isRefreshing, loadError = screen.loadError, message = screen.message)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SourcesUiState(sources = sourceRepository.sources.value))

    init {
        profileRepository.onActiveProfileChange(viewModelScope) {
            refreshJob?.cancel()
            screenState.value = ScreenState()
        }
    }

    /** Reloads the sources unless a load is already running; a failure keeps the cached list. */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        screenState.update { it.copy(isRefreshing = true) }
        refreshJob = viewModelScope.launch {
            val error = (sourceRepository.refresh() as? ApiResult.Failure)?.error
            screenState.update { it.copy(isRefreshing = false, loadError = error, message = error ?: it.message) }
        }
    }

    /** Clears [shown] unless a newer message replaced it. */
    fun onMessageShown(shown: ApiError) = screenState.update { if (it.message == shown) it.copy(message = null) else it }
}
