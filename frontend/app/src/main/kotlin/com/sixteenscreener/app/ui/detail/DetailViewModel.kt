package com.sixteenscreener.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sixteenscreener.app.data.repository.CompanyRepository
import com.sixteenscreener.app.data.repository.SourceRepository
import com.sixteenscreener.app.data.repository.TagRepository
import com.sixteenscreener.app.data.repository.WatchlistRepository
import com.sixteenscreener.app.domain.BalanceSheetTable
import com.sixteenscreener.app.domain.TagRules
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.BalanceSheet
import com.sixteenscreener.app.domain.model.CompanyIdentity
import com.sixteenscreener.app.domain.model.CompanyProfile
import com.sixteenscreener.app.domain.model.CompanyProfileData
import com.sixteenscreener.app.domain.model.CompanySources
import com.sixteenscreener.app.domain.model.Dividends
import com.sixteenscreener.app.domain.model.PriceAndRatios
import com.sixteenscreener.app.domain.model.Quote
import com.sixteenscreener.app.domain.model.RequestTrigger
import com.sixteenscreener.app.domain.model.RowMarketData
import com.sixteenscreener.app.domain.model.Source
import com.sixteenscreener.app.domain.model.SourceDataMeta
import com.sixteenscreener.app.domain.model.SourceId
import com.sixteenscreener.app.domain.model.SourceOption
import com.sixteenscreener.app.domain.model.Symbol
import com.sixteenscreener.app.domain.model.Tag
import com.sixteenscreener.app.domain.model.WatchlistItem
import com.sixteenscreener.app.ui.navigation.DetailRoute
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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

/** Source dropdown of a tab and the data of its selected source. */
data class SourceTabState<out T>(
    /** Dropdown options in backend order; empty while unknown, when the sources request failed or when no source has the data. */
    val options: List<SourceOption> = emptyList(),
    /** Source of [data]; `null` for the profile's default source picked by the backend when the options could not be loaded. */
    val selectedSource: SourceId? = null,
    /** Whether no source provides this tab's data for the company; nothing is requested then. */
    val hasNoSource: Boolean = false,
    /** Data of [selectedSource]. */
    val data: TabData<T> = TabData(),
)

/** One-time message of the detailed screen, shown as a snackbar. */
sealed interface DetailMessage {
    data class AddFailed(val error: ApiError) : DetailMessage

    data class RemoveFailed(val error: ApiError) : DetailMessage

    data class TagsSaveFailed(val error: ApiError) : DetailMessage
}

/** User tags of the company on the company profile tab and in the header. */
data class TagsUiState(
    val tags: List<Tag> = emptyList(),
    /** Whether the company is in the watchlist; only watchlist companies can have tags (ADR-061). */
    val isEditable: Boolean = false,
    /** Whether the changed tags are being saved; no other change is accepted meanwhile. */
    val isSaving: Boolean = false,
    val isAddDialogOpen: Boolean = false,
    /** Tags of the profile the company doesn't have, offered for reuse in the add dialog. */
    val suggestions: List<Tag> = emptyList(),
    /** Failure of the last save while the add dialog is open. */
    val dialogError: ApiError? = null,
) {
    val isLimitReached: Boolean get() = tags.size >= TagRules.MAX_TAGS

    val canAdd: Boolean get() = isEditable && !isSaving && !isLimitReached

    val canRemove: Boolean get() = isEditable && !isSaving
}

data class DetailUiState(
    val symbol: Symbol,
    /** Company of the header: the route's company until the company profile arrives; `null` while neither is known. */
    val header: CompanyIdentity? = null,
    /** Whether the loaded company profile reports the company's exchange as its home exchange; `false` until it arrives. */
    val homeExchange: Boolean = false,
    /** Price of the header: the price of the selected source, or the last watchlist market data of the company without it. */
    val headerQuote: Quote? = null,
    val inWatchlist: Boolean = false,
    val selectedTab: DetailTab = DetailTab.PRICE_AND_RATIOS,
    val priceAndRatios: SourceTabState<PriceAndRatios> = SourceTabState(),
    val dividends: SourceTabState<Dividends> = SourceTabState(),
    val balanceSheet: SourceTabState<BalanceSheetView> = SourceTabState(),
    val companyProfile: SourceTabState<CompanyProfileData> = SourceTabState(),
    val tags: TagsUiState = TagsUiState(),
    /** Display names of the known sources. */
    val sourceNames: Map<SourceId, String> = emptyMap(),
    val message: DetailMessage? = null,
) {
    fun sourceName(id: SourceId): String = sourceNames[id] ?: id.value
}

/**
 * Detailed screen. The source dropdown options of all tabs are requested once when the screen opens; each tab selects its first
 * option and loads its data from it when first shown, the price and ratio and the company profile tabs at once (ADR-089).
 * Selecting another source loads the tab from it unless a result of that source is kept; the refresh button reloads every tab from
 * its selected source with the manual trigger (ADR-052). Selections and kept results live while the screen is open (ADR-007).
 * Without the options the tabs fall back to the profile's default source, picked by the backend.
 *
 * The header (identity, security type, home exchange) comes from the company profile of the profile's default source, as before
 * the dropdown: the profile tab's request when its default option is selected, otherwise a request without a source.
 * Tags come with every company profile response, from the watchlist until one arrives, and are saved as a whole list after each
 * change; they are shown and edited only while the company is in the watchlist (ADR-061, ADR-086).
 */
@HiltViewModel(assistedFactory = DetailViewModel.Factory::class)
class DetailViewModel @AssistedInject constructor(
    @Assisted private val route: DetailRoute,
    private val companyRepository: CompanyRepository,
    sourceRepository: SourceRepository,
    private val watchlistRepository: WatchlistRepository,
    private val tagRepository: TagRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: DetailRoute): DetailViewModel
    }

    private sealed interface SourceOptions {
        data object Loading : SourceOptions

        /** The options could not be loaded: every tab uses the profile's default source without naming it. */
        data object Unavailable : SourceOptions

        data class Loaded(val sources: CompanySources) : SourceOptions
    }

    private data class ScreenState(
        val selectedTab: DetailTab = DetailTab.PRICE_AND_RATIOS,
        val sourceOptions: SourceOptions = SourceOptions.Loading,
        /** Selected source of each tab with options; a missing tab uses the profile's default source. */
        val selectedSources: Map<DetailTab, SourceId> = emptyMap(),
        val priceAndRatios: Map<SourceId?, TabData<PriceAndRatios>> = emptyMap(),
        val dividends: Map<SourceId?, TabData<Dividends>> = emptyMap(),
        val balanceSheets: Map<SourceId?, TabData<BalanceSheetView>> = emptyMap(),
        val companyProfiles: Map<SourceId?, TabData<CompanyProfileData>> = emptyMap(),
        /** Tags from the company profile or the last change, `null` until known. */
        val tags: List<Tag>? = null,
        /** Number of tag changes, so a company profile requested before a change doesn't bring back the old tags. */
        val tagChanges: Int = 0,
        val isSavingTags: Boolean = false,
        val isAddTagDialogOpen: Boolean = false,
        val profileTags: List<Tag> = emptyList(),
        val tagDialogError: ApiError? = null,
        val message: DetailMessage? = null,
    ) {
        fun optionsOf(tab: DetailTab): List<SourceOption> = (sourceOptions as? SourceOptions.Loaded)?.sources?.of(tab).orEmpty()

        fun hasNoSource(tab: DetailTab): Boolean = sourceOptions is SourceOptions.Loaded && optionsOf(tab).isEmpty()

        fun selectedSourceOf(tab: DetailTab): SourceId? = selectedSources[tab]

        /** Source of the header's company profile: the default option of the profile tab, or the backend's default without it. */
        fun headerSource(): SourceId? = optionsOf(DetailTab.COMPANY_PROFILE).firstOrNull { it.isDefault }?.id

        fun headerProfile(): CompanyProfile? = companyProfiles[headerSource()]?.value?.profile

        fun dataOf(tab: DetailTab): Map<SourceId?, TabData<*>> = when (tab) {
            DetailTab.PRICE_AND_RATIOS -> priceAndRatios
            DetailTab.DIVIDENDS -> dividends
            DetailTab.BALANCE_SHEET -> balanceSheets
            DetailTab.COMPANY_PROFILE -> companyProfiles
        }

        /** Sources whose results a refresh keeps: the selected one, and the header's for the company profile. */
        fun refreshedSourcesOf(tab: DetailTab): Set<SourceId?> =
            if (tab == DetailTab.COMPANY_PROFILE) setOf(selectedSourceOf(tab), headerSource()) else setOf(selectedSourceOf(tab))
    }

    private data class LoadKey(val tab: DetailTab, val source: SourceId?)

    private val symbol = route.symbol
    private val screenState = MutableStateFlow(ScreenState())
    private val requestedTabs = mutableSetOf(DetailTab.PRICE_AND_RATIOS, DetailTab.COMPANY_PROFILE)
    private val loadJobs = mutableMapOf<LoadKey, Job>()
    private var isRefreshPending = false

    val uiState: StateFlow<DetailUiState> = combine(
        screenState,
        watchlistRepository.watchlist,
        watchlistRepository.watchlistSymbols,
        watchlistRepository.marketData,
        sourceRepository.sources,
    ) { screen, watchlist, watchlistSymbols, marketData, sources ->
        toUiState(screen, watchlist, watchlistSymbols, marketData[symbol], sources)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, toUiState(ScreenState(), null, emptySet(), null, null))

    init {
        viewModelScope.launch { loadSourceOptions() }
    }

    fun onTabSelected(tab: DetailTab) {
        screenState.update { it.copy(selectedTab = tab) }
        if (requestedTabs.add(tab)) loadTab(tab, RequestTrigger.AUTO)
    }

    /** Shows the data of [source] on [tab], loading it unless a result of this source is kept or loading. */
    fun onSourceSelected(tab: DetailTab, source: SourceId) {
        val state = screenState.value
        if (state.optionsOf(tab).none { it.id == source } || state.selectedSourceOf(tab) == source) return
        screenState.update { it.copy(selectedSources = it.selectedSources + (tab to source)) }
        requestedTabs += tab
        val kept = state.dataOf(tab)[source]
        if (kept == null || (kept.value == null && !kept.isLoading)) load(tab, source, RequestTrigger.AUTO)
    }

    /**
     * Reloads every tab from its selected source, including tabs not shown yet; kept results of other sources are dropped (ADR-052).
     * A refresh before the options arrive is sent once they are known.
     */
    fun refresh() {
        requestedTabs += DetailTab.entries
        if (screenState.value.sourceOptions is SourceOptions.Loading) {
            isRefreshPending = true
            return
        }
        dropResultsOfOtherSources()
        DetailTab.entries.forEach { loadTab(it, RequestTrigger.MANUAL) }
    }

    fun onToggleWatchlist() {
        val state = uiState.value
        val company = state.header ?: return
        val sector = screenState.value.headerProfile()?.sector
        viewModelScope.launch {
            val result = if (state.inWatchlist) watchlistRepository.remove(symbol) else watchlistRepository.add(WatchlistItem.of(company, sector))
            when (result) {
                is ApiResult.Success -> {
                    val tags = (result.value as? WatchlistItem)?.tags.orEmpty()
                    screenState.update { it.copy(tags = tags, tagChanges = it.tagChanges + 1) }
                }
                is ApiResult.Failure -> {
                    val message = if (state.inWatchlist) DetailMessage.RemoveFailed(result.error) else DetailMessage.AddFailed(result.error)
                    screenState.update { it.copy(message = message) }
                }
            }
        }
    }

    /** Opens the dialog adding a tag and loads the profile's tags offered for reuse. */
    fun onAddTagRequested() {
        if (!uiState.value.tags.canAdd) return
        screenState.update { it.copy(isAddTagDialogOpen = true, tagDialogError = null) }
        viewModelScope.launch {
            val result = tagRepository.getProfileTags()
            if (result is ApiResult.Success) screenState.update { it.copy(profileTags = result.value) }
        }
    }

    fun onAddTagDismissed() = screenState.update { it.copy(isAddTagDialogOpen = false, tagDialogError = null) }

    /** Adds the tag with [icon] and the trimmed [text], unless it breaks the tag rules; the dialog closes once the tags are saved. */
    fun onAddTag(icon: String, text: String) {
        val tags = uiState.value.tags
        if (!tags.canAdd || TagRules.problemOfAdding(tags.tags, icon, text) != null) return
        saveTags(tags.tags + Tag(icon, text.trim()))
    }

    fun onRemoveTag(tag: Tag) {
        val tags = uiState.value.tags
        if (!tags.canRemove || !TagRules.contains(tags.tags, tag)) return
        saveTags(tags.tags.filterNot { it.isSameAs(tag) })
    }

    /** Clears [shown] unless a newer message replaced it. */
    fun onMessageShown(shown: DetailMessage) = screenState.update { if (it.message == shown) it.copy(message = null) else it }

    private fun saveTags(tags: List<Tag>) {
        screenState.update { it.copy(isSavingTags = true, tagDialogError = null) }
        viewModelScope.launch {
            when (val result = tagRepository.setCompanyTags(symbol, tags)) {
                is ApiResult.Success -> screenState.update {
                    it.copy(tags = result.value, tagChanges = it.tagChanges + 1, isSavingTags = false, isAddTagDialogOpen = false)
                }
                is ApiResult.Failure -> screenState.update {
                    if (it.isAddTagDialogOpen) {
                        it.copy(isSavingTags = false, tagDialogError = result.error)
                    } else {
                        it.copy(isSavingTags = false, message = DetailMessage.TagsSaveFailed(result.error))
                    }
                }
            }
        }
    }

    private suspend fun loadSourceOptions() {
        val result = companyRepository.getCompanySources(symbol)
        screenState.update { state ->
            when (result) {
                is ApiResult.Success -> state.copy(
                    sourceOptions = SourceOptions.Loaded(result.value),
                    selectedSources = DetailTab.entries
                        .mapNotNull { tab -> result.value.of(tab).firstOrNull()?.let { tab to it.id } }
                        .toMap(),
                )
                is ApiResult.Failure -> state.copy(sourceOptions = SourceOptions.Unavailable)
            }
        }
        val trigger = if (isRefreshPending) RequestTrigger.MANUAL else RequestTrigger.AUTO
        isRefreshPending = false
        requestedTabs.toList().forEach { loadTab(it, trigger) }
    }

    /** Loads [tab] from its selected source; the company profile tab also loads the header's profile when it is another source. */
    private fun loadTab(tab: DetailTab, trigger: RequestTrigger) {
        val state = screenState.value
        if (state.sourceOptions is SourceOptions.Loading) return
        val selected = state.selectedSourceOf(tab)
        if (!state.hasNoSource(tab)) load(tab, selected, trigger)
        val headerSource = state.headerSource()
        if (tab == DetailTab.COMPANY_PROFILE && (state.hasNoSource(tab) || headerSource != selected)) {
            loadCompanyProfile(headerSource, trigger)
        }
    }

    private fun dropResultsOfOtherSources() {
        val state = screenState.value
        val refreshed = DetailTab.entries.associateWith(state::refreshedSourcesOf)
        loadJobs.keys.filterNot { it.source in refreshed.getValue(it.tab) }.forEach { loadJobs.remove(it)?.cancel() }
        screenState.update {
            it.copy(
                priceAndRatios = it.priceAndRatios.filterKeys { source -> source in refreshed.getValue(DetailTab.PRICE_AND_RATIOS) },
                dividends = it.dividends.filterKeys { source -> source in refreshed.getValue(DetailTab.DIVIDENDS) },
                balanceSheets = it.balanceSheets.filterKeys { source -> source in refreshed.getValue(DetailTab.BALANCE_SHEET) },
                companyProfiles = it.companyProfiles.filterKeys { source -> source in refreshed.getValue(DetailTab.COMPANY_PROFILE) },
            )
        }
    }

    private fun load(tab: DetailTab, source: SourceId?, trigger: RequestTrigger) {
        val key = LoadKey(tab, source)
        when (tab) {
            DetailTab.PRICE_AND_RATIOS -> launchLoad(
                key = key,
                update = { state, transform -> state.copy(priceAndRatios = state.priceAndRatios.updated(source, transform)) },
                request = { companyRepository.getPriceAndRatios(symbol, source, trigger) },
            )
            DetailTab.DIVIDENDS -> launchLoad(
                key = key,
                update = { state, transform -> state.copy(dividends = state.dividends.updated(source, transform)) },
                request = { companyRepository.getDividends(symbol, source, trigger) },
            )
            DetailTab.BALANCE_SHEET -> launchLoad(
                key = key,
                update = { state, transform -> state.copy(balanceSheets = state.balanceSheets.updated(source, transform)) },
                request = { companyRepository.getBalanceSheet(symbol, source, trigger).toView() },
            )
            DetailTab.COMPANY_PROFILE -> loadCompanyProfile(source, trigger)
        }
    }

    private fun loadCompanyProfile(source: SourceId?, trigger: RequestTrigger) {
        val tagChangesAtRequest = screenState.value.tagChanges
        launchLoad(
            key = LoadKey(DetailTab.COMPANY_PROFILE, source),
            update = { state, transform -> state.copy(companyProfiles = state.companyProfiles.updated(source, transform)) },
            request = { companyRepository.getCompanyProfile(symbol, source, trigger) },
            onLoaded = { state, data -> if (state.tagChanges == tagChangesAtRequest) state.copy(tags = data.tags) else state },
        )
    }

    private fun <T> launchLoad(
        key: LoadKey,
        update: (ScreenState, (TabData<T>) -> TabData<T>) -> ScreenState,
        request: suspend () -> ApiResult<T>,
        onLoaded: (ScreenState, T) -> ScreenState = { state, _ -> state },
    ) {
        loadJobs.remove(key)?.cancel()
        screenState.update { state -> update(state) { it.copy(isLoading = true, error = null) } }
        loadJobs[key] = viewModelScope.launch {
            val result = request()
            screenState.update { state ->
                update(state) { it.loadedWith(result) }.let { updated -> if (result is ApiResult.Success) onLoaded(updated, result.value) else updated }
            }
        }
    }

    private fun <T> Map<SourceId?, TabData<T>>.updated(source: SourceId?, transform: (TabData<T>) -> TabData<T>) =
        this + (source to transform(this[source] ?: TabData()))

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
        watchlist: List<WatchlistItem>?,
        watchlistSymbols: Set<Symbol>,
        rowMarketData: RowMarketData?,
        sources: List<Source>?,
    ): DetailUiState {
        val inWatchlist = symbol in watchlistSymbols || (watchlist == null && route.initiallyInWatchlist)
        val tags = if (inWatchlist) screen.tags ?: watchlist?.firstOrNull { it.symbol == symbol }?.tags.orEmpty() else emptyList()
        val headerProfile = screen.headerProfile()
        val priceAndRatios = screen.tabState(DetailTab.PRICE_AND_RATIOS, screen.priceAndRatios)
        return DetailUiState(
            symbol = symbol,
            header = headerProfile?.toIdentity() ?: route.initialCompany,
            homeExchange = headerProfile?.homeExchange == true,
            headerQuote = priceAndRatios.data.value?.quote ?: rowMarketData?.quote,
            inWatchlist = inWatchlist,
            selectedTab = screen.selectedTab,
            priceAndRatios = priceAndRatios,
            dividends = screen.tabState(DetailTab.DIVIDENDS, screen.dividends),
            balanceSheet = screen.tabState(DetailTab.BALANCE_SHEET, screen.balanceSheets),
            companyProfile = screen.tabState(DetailTab.COMPANY_PROFILE, screen.companyProfiles),
            tags = TagsUiState(
                tags = tags,
                isEditable = inWatchlist,
                isSaving = screen.isSavingTags,
                isAddDialogOpen = screen.isAddTagDialogOpen,
                suggestions = screen.profileTags.filterNot { TagRules.contains(tags, it) },
                dialogError = screen.tagDialogError,
            ),
            sourceNames = sources.orEmpty().associate { it.id to it.name } +
                DetailTab.entries.flatMap(screen::optionsOf).associate { it.id to it.name },
            message = screen.message,
        )
    }

    private fun <T> ScreenState.tabState(tab: DetailTab, data: Map<SourceId?, TabData<T>>): SourceTabState<T> {
        val selected = selectedSourceOf(tab)
        return SourceTabState(
            options = optionsOf(tab),
            selectedSource = selected,
            hasNoSource = hasNoSource(tab),
            data = if (sourceOptions is SourceOptions.Loading) TabData(isLoading = true) else data[selected] ?: TabData(),
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

/** Dropdown options of [tab]. */
fun CompanySources.of(tab: DetailTab): List<SourceOption> = when (tab) {
    DetailTab.PRICE_AND_RATIOS -> priceRatios
    DetailTab.DIVIDENDS -> dividends
    DetailTab.BALANCE_SHEET -> balanceSheet
    DetailTab.COMPANY_PROFILE -> profile
}
