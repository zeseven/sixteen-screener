package com.sixteenscreener.app.ui.indicators

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sixteenscreener.app.data.repository.IndicatorSettingsRepository
import com.sixteenscreener.app.di.ApplicationScope
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.IndicatorIntervals
import com.sixteenscreener.app.domain.model.IndicatorSetting
import com.sixteenscreener.app.domain.model.RatioCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/** Settings of one category, in settings order. */
data class IndicatorSettingsGroup(val category: RatioCategory, val settings: List<IndicatorSetting>)

data class IndicatorSettingsUiState(
    val isLoading: Boolean = true,
    /** Why the last load failed; the screen offers a retry. */
    val loadError: ApiError? = null,
    /** Settings with the local edits, in settings order. */
    val settings: List<IndicatorSetting> = emptyList(),
    /** Why the last save failed, shown once; the edits stay and are saved again with the next change. */
    val saveError: ApiError? = null,
) {
    /** Settings grouped by category; categories in the order of their first setting. */
    val groups: List<IndicatorSettingsGroup>
        get() = settings.groupBy { it.category }.map { (category, settings) -> IndicatorSettingsGroup(category, settings) }
}

/**
 * Indicator settings screen: loads the settings of the active profile, applies edits immediately and saves the whole set with
 * `PUT` [SAVE_DEBOUNCE_MILLIS] after the last edit. Pending edits are saved at once when the screen stops ([flush]) and, in the
 * application scope, when the screen is closed. A failed save keeps the edits and is retried with the next edit or flush.
 */
@HiltViewModel
class IndicatorSettingsViewModel @Inject constructor(
    private val repository: IndicatorSettingsRepository,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val state = MutableStateFlow(IndicatorSettingsUiState())
    private val editVersion = AtomicLong(0)
    private val savedVersion = AtomicLong(0)
    private val saveMutex = Mutex()
    private var debounceJob: Job? = null

    val uiState: StateFlow<IndicatorSettingsUiState> = state.asStateFlow()

    private val hasPendingEdits: Boolean get() = editVersion.get() != savedVersion.get()

    init {
        load()
    }

    fun load() {
        state.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            when (val result = repository.getSettings()) {
                is ApiResult.Success -> state.update { it.copy(isLoading = false, settings = result.value) }
                is ApiResult.Failure -> state.update { it.copy(isLoading = false, loadError = result.error) }
            }
        }
    }

    fun onEnabledChange(ratioCode: String, enabled: Boolean) = edit(ratioCode) { it.copy(enabled = enabled) }

    fun onIntervalsChange(ratioCode: String, intervals: IndicatorIntervals) = edit(ratioCode) { it.copy(intervals = intervals) }

    fun onSaveErrorShown() = state.update { it.copy(saveError = null) }

    /** Saves pending edits now instead of after the debounce, e.g. when the screen stops. */
    fun flush() {
        if (!hasPendingEdits) return
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch { save() }
    }

    override fun onCleared() {
        debounceJob?.cancel()
        if (hasPendingEdits) applicationScope.launch { save() }
    }

    private fun edit(ratioCode: String, transform: (IndicatorSetting) -> IndicatorSetting) {
        val before = state.value.settings
        state.update { current ->
            current.copy(settings = current.settings.map { if (it.ratioCode == ratioCode) transform(it) else it })
        }
        if (state.value.settings == before) return
        editVersion.incrementAndGet()
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MILLIS)
            save()
        }
    }

    private suspend fun save() {
        saveMutex.withLock {
            val version = editVersion.get()
            if (version == savedVersion.get()) return
            when (val result = repository.replaceSettings(state.value.settings)) {
                is ApiResult.Success -> savedVersion.accumulateAndGet(version, ::maxOf)
                is ApiResult.Failure -> state.update { it.copy(saveError = result.error) }
            }
        }
    }

    companion object {
        const val SAVE_DEBOUNCE_MILLIS = 500L
    }
}
