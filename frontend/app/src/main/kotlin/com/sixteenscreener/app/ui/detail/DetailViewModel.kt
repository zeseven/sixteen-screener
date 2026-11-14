package com.sixteenscreener.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sixteenscreener.app.data.repository.CompanyRepository
import com.sixteenscreener.app.data.repository.SourceRepository
import com.sixteenscreener.app.data.repository.WatchlistRepository
import com.sixteenscreener.app.domain.BalanceSheetTable
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.BalanceSheet
import com.sixteenscreener.app.domain.model.CompanyProfile
import com.sixteenscreener.app.domain.model.CompanyProfileData
import com.sixteenscreener.app.domain.model.CompanySummary
import com.sixteenscreener.app.domain.model.PriceAndRatios
import com.sixteenscreener.app.domain.model.Source
import com.sixteenscreener.app.domain.model.SourceDataMeta
import com.sixteenscreener.app.domain.model.SourceId
import com.sixteenscreener.app.domain.model.Symbol
import com.sixteenscreener.app.ui.navigation.DetailRoute
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Tabs of the detailed screen, in display order. */
enum class DetailTab { PRICE_AND_RATIOS, BALANCE_SHEET, COMPANY_PROFILE }

/** Data of one tab: the last successful response, whether a request is running and the failure of the last request. */
data class TabData<out T>(
    val value: T? = null,
    val isLoading: Boolean = false,
    val error: ApiError? = null,
)

/** Balance sheet response of one source with the table built from its periods. */
data class BalanceSheetView(
    val meta: SourceDataMeta,
    val table: BalanceSheetTable,
)

data class BalanceSheetTabState(
    /** Sources offered by the dropdown, the default source first; empty until loaded. */
    val sources: List<Source> = emptyList(),
    val selectedSource: SourceId? = null,
    /** Data of [selectedSource]. */
    val data: TabData<BalanceSheetView> = TabData(),
)

/** One-time message of the detailed screen, shown as a snackbar. */
sealed interface DetailMessage {
    data class AddFailed(val error: ApiError) : DetailMessage

    data class RemoveFailed(val error: ApiError) : DetailMessage
}

data class DetailUiState(
    val symbol: Symbol,
    /** Company row data of the header: the route's summary until the company profile arrives; `null` while neither is known. */
    val header: CompanySummary? = null,
    val inWatchlist: Boolean = false,
    val selectedTab: DetailTab = DetailTab.PRICE_AND_RATIOS,
    val priceAndRatios: TabData<PriceAndRatios> = TabData(),
    val balanceSheet: BalanceSheetTabState = BalanceSheetTabState(),
    val companyProfile: TabData<CompanyProfileData> = TabData(),
    /** Display names of the known sources. */
    val sourceNames: Map<SourceId, String> = emptyMap(),
    val message: DetailMessage? = null,
) {
    fun sourceName(id: SourceId): String = sourceNames[id] ?: id.value
}

/**
 * Detailed screen: each tab loads its data once when first shown, the refresh button reloads every tab from the sources (ADR-052).
 * The balance sheet keeps the result of each selected source while the screen is open (ADR-007, ADR-047).
 */
@HiltViewModel(assistedFactory = DetailViewModel.Factory::class)
class DetailViewModel @AssistedInject constructor(
    @Assisted private val route: DetailRoute,
    private val companyRepository: CompanyRepository,
    private val sourceRepository: SourceRepository,
    private val watchlistRepository: WatchlistRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: DetailRoute): DetailViewModel
    }

    private data class ScreenState(
        val selectedTab: DetailTab = DetailTab.PRICE_AND_RATIOS,
        val priceAndRatios: TabData<PriceAndRatios> = TabData(),
        val balanceSheetSources: List<Source> = emptyList(),
        val selectedSource: SourceId? = null,
        val balanceSheets: Map<SourceId, TabData<BalanceSheetView>> = emptyMap(),
        val companyProfile: TabData<CompanyProfileData> = TabData(),
        val message: DetailMessage? = null,
    )

    private sealed interface LoadKey {
        data class Tab(val tab: DetailTab) : LoadKey

        data class BalanceSheetOf(val source: SourceId) : LoadKey
    }

    private val symbol = route.symbol
    private val screenState = MutableStateFlow(ScreenState())
    private val requestedTabs = mutableSetOf<DetailTab>()
    private val loadJobs = mutableMapOf<LoadKey, Job>()
    private val balanceSheetSources = viewModelScope.async(start = CoroutineStart.LAZY) {
        sourceRepository.getBalanceSheetSources().ifEmpty { SourceRepository.FALLBACK_BALANCE_SHEET_SOURCES }
    }

    val uiState: StateFlow<DetailUiState> = combine(
        screenState,
        watchlistRepository.watchlist,
        watchlistRepository.watchlistSymbols,
        sourceRepository.sources,
    ) { screen, watchlist, watchlistSymbols, sources -> toUiState(screen, watchlist != null, watchlistSymbols, sources) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, toUiState(ScreenState(), false, emptySet(), null))

    init {
        loadOnce(DetailTab.PRICE_AND_RATIOS)
        if (route.initialSummary == null) loadOnce(DetailTab.COMPANY_PROFILE)
    }

    fun onTabSelected(tab: DetailTab) {
        screenState.update { it.copy(selectedTab = tab) }
        loadOnce(tab)
    }

    /** Shows the balance sheet of [source], loading it unless a result of this source is kept. */
    fun onSourceSelected(source: SourceId) {
        screenState.update { it.copy(selectedSource = source) }
        val kept = screenState.value.balanceSheets[source]
        if (kept == null || (kept.value == null && !kept.isLoading)) loadBalanceSheet(source, refresh = false)
    }

    /** Reloads every tab from the sources, including tabs not shown yet; other balance sheet sources are dropped (ADR-052). */
    fun refresh() {
        requestedTabs += DetailTab.entries
        loadPriceAndRatios(refresh = true)
        loadCompanyProfile(refresh = true)
        viewModelScope.launch {
            val source = selectedSourceOrDefault()
            loadJobs.keys.filter { it is LoadKey.BalanceSheetOf && it.source != source }.forEach { loadJobs.remove(it)?.cancel() }
            screenState.update { state -> state.copy(balanceSheets = state.balanceSheets.filterKeys { it == source }) }
            loadBalanceSheet(source, refresh = true)
        }
    }

    fun onToggleWatchlist() {
        val state = uiState.value
        val company = state.header ?: return
        viewModelScope.launch {
            val result = if (state.inWatchlist) watchlistRepository.remove(symbol) else watchlistRepository.add(company)
            if (result is ApiResult.Failure) {
                val message = if (state.inWatchlist) DetailMessage.RemoveFailed(result.error) else DetailMessage.AddFailed(result.error)
                screenState.update { it.copy(message = message) }
            }
        }
    }

    /** Clears [shown] unless a newer message replaced it. */
    fun onMessageShown(shown: DetailMessage) = screenState.update { if (it.message == shown) it.copy(message = null) else it }

    private fun loadOnce(tab: DetailTab) {
        if (!requestedTabs.add(tab)) return
        when (tab) {
            DetailTab.PRICE_AND_RATIOS -> loadPriceAndRatios(refresh = false)
            DetailTab.COMPANY_PROFILE -> loadCompanyProfile(refresh = false)
            DetailTab.BALANCE_SHEET -> viewModelScope.launch { loadBalanceSheet(selectedSourceOrDefault(), refresh = false) }
        }
    }

    private fun loadPriceAndRatios(refresh: Boolean) = launchLoad(
        key = LoadKey.Tab(DetailTab.PRICE_AND_RATIOS),
        update = { state, transform -> state.copy(priceAndRatios = transform(state.priceAndRatios)) },
        request = { companyRepository.getPriceAndRatios(symbol, refresh) },
    )

    private fun loadCompanyProfile(refresh: Boolean) = launchLoad(
        key = LoadKey.Tab(DetailTab.COMPANY_PROFILE),
        update = { state, transform -> state.copy(companyProfile = transform(state.companyProfile)) },
        request = { companyRepository.getCompanyProfile(symbol, refresh) },
    )

    private fun loadBalanceSheet(source: SourceId, refresh: Boolean) = launchLoad(
        key = LoadKey.BalanceSheetOf(source),
        update = { state, transform ->
            state.copy(balanceSheets = state.balanceSheets + (source to transform(state.balanceSheets[source] ?: TabData())))
        },
        request = { companyRepository.getBalanceSheet(symbol, source, refresh).toView() },
    )

    private suspend fun selectedSourceOrDefault(): SourceId {
        val sources = balanceSheetSources.await()
        screenState.update { state ->
            state.copy(balanceSheetSources = sources, selectedSource = state.selectedSource ?: sources.first().id)
        }
        return checkNotNull(screenState.value.selectedSource)
    }

    private fun <T> launchLoad(
        key: LoadKey,
        update: (ScreenState, (TabData<T>) -> TabData<T>) -> ScreenState,
        request: suspend () -> ApiResult<T>,
    ) {
        loadJobs.remove(key)?.cancel()
        screenState.update { state -> update(state) { it.copy(isLoading = true, error = null) } }
        loadJobs[key] = viewModelScope.launch {
            val result = request()
            screenState.update { state -> update(state) { it.loadedWith(result) } }
        }
    }

    private fun <T> TabData<T>.loadedWith(result: ApiResult<T>): TabData<T> = when (result) {
        is ApiResult.Success -> TabData(value = result.value)
        is ApiResult.Failure -> copy(isLoading = false, error = result.error)
    }

    private fun ApiResult<BalanceSheet>.toView(): ApiResult<BalanceSheetView> = when (this) {
        is ApiResult.Success -> ApiResult.Success(BalanceSheetView(value.meta, BalanceSheetTable.of(value.periods)))
        is ApiResult.Failure -> this
    }

    private fun toUiState(
        screen: ScreenState,
        isWatchlistLoaded: Boolean,
        watchlistSymbols: Set<Symbol>,
        sources: List<Source>?,
    ): DetailUiState {
        val inWatchlist = symbol in watchlistSymbols || (!isWatchlistLoaded && route.initialSummary?.inWatchlist == true)
        return DetailUiState(
            symbol = symbol,
            header = header(screen)?.copy(inWatchlist = inWatchlist),
            inWatchlist = inWatchlist,
            selectedTab = screen.selectedTab,
            priceAndRatios = screen.priceAndRatios,
            balanceSheet = BalanceSheetTabState(
                sources = screen.balanceSheetSources,
                selectedSource = screen.selectedSource,
                data = screen.selectedSource?.let { screen.balanceSheets[it] } ?: TabData(),
            ),
            companyProfile = screen.companyProfile,
            sourceNames = (SourceRepository.FALLBACK_BALANCE_SHEET_SOURCES + screen.balanceSheetSources + sources.orEmpty())
                .associate { it.id to it.name },
            message = screen.message,
        )
    }

    private fun header(screen: ScreenState): CompanySummary? {
        val initial = route.initialSummary
        val quote = screen.priceAndRatios.value?.quote ?: initial?.quote
        return screen.companyProfile.value?.profile?.toSummary(initial)?.copy(quote = quote)
            ?: initial?.copy(quote = quote)
    }

    private fun CompanyProfile.toSummary(initial: CompanySummary?) = CompanySummary(
        symbol = symbol,
        ticker = ticker,
        exchange = exchange,
        name = name,
        market = market,
        sector = sector ?: initial?.sector,
        logoUrl = initial?.logoUrl ?: "$LOGO_PATH${symbol.value}",
        quote = initial?.quote,
        inWatchlist = initial?.inWatchlist ?: false,
    )

    private companion object {
        const val LOGO_PATH = "/logos/"
    }
}
