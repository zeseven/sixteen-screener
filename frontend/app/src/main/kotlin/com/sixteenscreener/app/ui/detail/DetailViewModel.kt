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
import com.sixteenscreener.app.domain.model.CompanyIdentity
import com.sixteenscreener.app.domain.model.Dividends
import com.sixteenscreener.app.domain.model.PriceAndRatios
import com.sixteenscreener.app.domain.model.Quote
import com.sixteenscreener.app.domain.model.RequestTrigger
import com.sixteenscreener.app.domain.model.RowMarketData
import com.sixteenscreener.app.domain.model.Source
import com.sixteenscreener.app.domain.model.SourceDataMeta
import com.sixteenscreener.app.domain.model.SourceId
import com.sixteenscreener.app.domain.model.Symbol
import com.sixteenscreener.app.domain.model.WatchlistItem
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
enum class DetailTab { PRICE_AND_RATIOS, DIVIDENDS, BALANCE_SHEET, COMPANY_PROFILE }

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
    /** Balance sheet sources that can be picked explicitly; empty until loaded. */
    val sources: List<Source> = emptyList(),
    /** Source picked in the dropdown; `null` for the profile's default source of the company's role, picked by the backend. */
    val selectedSource: SourceId? = null,
    /** Source that answered the request for the default source; `null` until it answered. */
    val defaultSource: SourceId? = null,
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
    /** Company of the header: the route's company until the company profile arrives; `null` while neither is known. */
    val header: CompanyIdentity? = null,
    /** Whether the loaded company profile reports the company's exchange as its home exchange; `false` until it arrives. */
    val homeExchange: Boolean = false,
    /** Price of the header: the loaded price, or the last watchlist market data of the company until it arrives. */
    val headerQuote: Quote? = null,
    val inWatchlist: Boolean = false,
    val selectedTab: DetailTab = DetailTab.PRICE_AND_RATIOS,
    val priceAndRatios: TabData<PriceAndRatios> = TabData(),
    val dividends: TabData<Dividends> = TabData(),
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
 * The company profile loads when the screen opens, as the header shows its identity and home exchange.
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
        val dividends: TabData<Dividends> = TabData(),
        val balanceSheetSources: List<Source> = emptyList(),
        val selectedSource: SourceId? = null,
        val balanceSheets: Map<SourceId?, TabData<BalanceSheetView>> = emptyMap(),
        val companyProfile: TabData<CompanyProfileData> = TabData(),
        val message: DetailMessage? = null,
    )

    private sealed interface LoadKey {
        data class Tab(val tab: DetailTab) : LoadKey

        data class BalanceSheetOf(val source: SourceId?) : LoadKey
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
        watchlistRepository.marketData,
        sourceRepository.sources,
    ) { screen, watchlist, watchlistSymbols, marketData, sources ->
        toUiState(screen, watchlist != null, watchlistSymbols, marketData[symbol], sources)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, toUiState(ScreenState(), false, emptySet(), null, null))

    init {
        loadOnce(DetailTab.PRICE_AND_RATIOS)
        loadOnce(DetailTab.COMPANY_PROFILE)
    }

    fun onTabSelected(tab: DetailTab) {
        screenState.update { it.copy(selectedTab = tab) }
        if (tab == DetailTab.BALANCE_SHEET) viewModelScope.launch { loadBalanceSheetSources() }
        loadOnce(tab)
    }

    /**
     * Shows the balance sheet of [source], or of the default source when `null`, loading it unless a result of this source is
     * kept.
     */
    fun onSourceSelected(source: SourceId?) {
        screenState.update { it.copy(selectedSource = source) }
        val kept = screenState.value.balanceSheets[source]
        if (kept == null || (kept.value == null && !kept.isLoading)) loadBalanceSheet(source, RequestTrigger.AUTO)
    }

    /** Reloads every tab from the sources, including tabs not shown yet; other balance sheet sources are dropped (ADR-052). */
    fun refresh() {
        requestedTabs += DetailTab.entries
        loadPriceAndRatios(RequestTrigger.MANUAL)
        loadDividends(RequestTrigger.MANUAL)
        loadCompanyProfile(RequestTrigger.MANUAL)
        val source = screenState.value.selectedSource
        loadJobs.keys.filter { it is LoadKey.BalanceSheetOf && it.source != source }.forEach { loadJobs.remove(it)?.cancel() }
        screenState.update { state -> state.copy(balanceSheets = state.balanceSheets.filterKeys { it == source }) }
        loadBalanceSheet(source, RequestTrigger.MANUAL)
    }

    fun onToggleWatchlist() {
        val state = uiState.value
        val company = state.header ?: return
        val sector = state.companyProfile.value?.profile?.sector
        viewModelScope.launch {
            val result = if (state.inWatchlist) watchlistRepository.remove(symbol) else watchlistRepository.add(WatchlistItem.of(company, sector))
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
            DetailTab.PRICE_AND_RATIOS -> loadPriceAndRatios(RequestTrigger.AUTO)
            DetailTab.DIVIDENDS -> loadDividends(RequestTrigger.AUTO)
            DetailTab.COMPANY_PROFILE -> loadCompanyProfile(RequestTrigger.AUTO)
            DetailTab.BALANCE_SHEET -> loadBalanceSheet(screenState.value.selectedSource, RequestTrigger.AUTO)
        }
    }

    private fun loadPriceAndRatios(trigger: RequestTrigger) = launchLoad(
        key = LoadKey.Tab(DetailTab.PRICE_AND_RATIOS),
        update = { state, transform -> state.copy(priceAndRatios = transform(state.priceAndRatios)) },
        request = { companyRepository.getPriceAndRatios(symbol, trigger = trigger) },
    )

    private fun loadDividends(trigger: RequestTrigger) = launchLoad(
        key = LoadKey.Tab(DetailTab.DIVIDENDS),
        update = { state, transform -> state.copy(dividends = transform(state.dividends)) },
        request = { companyRepository.getDividends(symbol, trigger = trigger) },
    )

    private fun loadCompanyProfile(trigger: RequestTrigger) = launchLoad(
        key = LoadKey.Tab(DetailTab.COMPANY_PROFILE),
        update = { state, transform -> state.copy(companyProfile = transform(state.companyProfile)) },
        request = { companyRepository.getCompanyProfile(symbol, trigger = trigger) },
    )

    private fun loadBalanceSheet(source: SourceId?, trigger: RequestTrigger) = launchLoad(
        key = LoadKey.BalanceSheetOf(source),
        update = { state, transform ->
            state.copy(balanceSheets = state.balanceSheets + (source to transform(state.balanceSheets[source] ?: TabData())))
        },
        request = { companyRepository.getBalanceSheet(symbol, source, trigger).toView() },
    )

    private suspend fun loadBalanceSheetSources() {
        val sources = balanceSheetSources.await()
        screenState.update { it.copy(balanceSheetSources = sources) }
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
        rowMarketData: RowMarketData?,
        sources: List<Source>?,
    ): DetailUiState {
        val inWatchlist = symbol in watchlistSymbols || (!isWatchlistLoaded && route.initiallyInWatchlist)
        return DetailUiState(
            symbol = symbol,
            header = screen.companyProfile.value?.profile?.toIdentity() ?: route.initialCompany,
            homeExchange = screen.companyProfile.value?.profile?.homeExchange == true,
            headerQuote = screen.priceAndRatios.value?.quote ?: rowMarketData?.quote,
            inWatchlist = inWatchlist,
            selectedTab = screen.selectedTab,
            priceAndRatios = screen.priceAndRatios,
            dividends = screen.dividends,
            balanceSheet = BalanceSheetTabState(
                sources = screen.balanceSheetSources,
                selectedSource = screen.selectedSource,
                defaultSource = screen.balanceSheets[null]?.value?.meta?.source,
                data = screen.balanceSheets[screen.selectedSource] ?: TabData(),
            ),
            companyProfile = screen.companyProfile,
            sourceNames = (SourceRepository.FALLBACK_BALANCE_SHEET_SOURCES + screen.balanceSheetSources + sources.orEmpty())
                .associate { it.id to it.name },
            message = screen.message,
        )
    }

    private fun CompanyProfile.toIdentity() = CompanyIdentity(
        symbol = symbol,
        ticker = ticker,
        exchange = exchange,
        name = name,
        market = market,
        type = type,
        stock = stock,
        logoUrl = route.initialCompany?.logoUrl ?: "$LOGO_PATH${symbol.value}",
    )

    private companion object {
        const val LOGO_PATH = "/logos/"
    }
}
