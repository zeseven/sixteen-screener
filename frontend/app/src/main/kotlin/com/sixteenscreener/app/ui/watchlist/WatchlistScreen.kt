package com.sixteenscreener.app.ui.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sixteenscreener.app.R
import com.sixteenscreener.app.domain.MarketFilter
import com.sixteenscreener.app.domain.VisibleRows
import com.sixteenscreener.app.domain.WatchlistFilter
import com.sixteenscreener.app.domain.model.Market
import com.sixteenscreener.app.domain.model.RowMarketData
import com.sixteenscreener.app.domain.model.Symbol
import com.sixteenscreener.app.domain.model.WatchlistItem
import com.sixteenscreener.app.ui.component.CenteredMessage
import com.sixteenscreener.app.ui.component.CompanyRow
import com.sixteenscreener.app.ui.component.FilterDropdown
import com.sixteenscreener.app.ui.component.WatchlistMarketData
import com.sixteenscreener.app.ui.component.apiErrorMessage
import com.sixteenscreener.app.ui.theme.SixteenTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

object WatchlistScreenTags {
    const val LIST = "watchlist_list"
    const val ADD_BUTTON = "watchlist_add_button"
    const val SWIPE_BACKGROUND = "watchlist_swipe_background"
}

/** Watchlist tab; loads the watchlist when shown and when the app returns to the foreground. */
@Composable
fun WatchlistScreen(
    onOpenDetail: (WatchlistItem) -> Unit,
    onAddCompany: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WatchlistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.refresh() }
    WatchlistContent(
        uiState = uiState,
        onRefresh = viewModel::refresh,
        onMarketSelected = viewModel::onMarketSelected,
        onSectorSelected = viewModel::onSectorSelected,
        onVisibleRowsChanged = viewModel::onVisibleRowsChanged,
        onRemove = viewModel::onRemove,
        onUndo = viewModel::onUndo,
        onMessageShown = viewModel::onMessageShown,
        onOpenDetail = onOpenDetail,
        onAddCompany = onAddCompany,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistContent(
    uiState: WatchlistUiState,
    onRefresh: () -> Unit,
    onMarketSelected: (MarketFilter) -> Unit,
    onSectorSelected: (String?) -> Unit,
    onRemove: (WatchlistItem) -> Unit,
    onUndo: (WatchlistMessage.Removed) -> Unit,
    onMessageShown: (WatchlistMessage) -> Unit,
    onOpenDetail: (WatchlistItem) -> Unit,
    onAddCompany: () -> Unit,
    modifier: Modifier = Modifier,
    onVisibleRowsChanged: (VisibleRows) -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    WatchlistMessageSnackbar(uiState.message, snackbarHostState, onUndo, onMessageShown)
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddCompany, modifier = Modifier.testTag(WatchlistScreenTags.ADD_BUTTON)) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.watchlist_add_company))
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            WatchlistFilters(
                filter = uiState.filter,
                sectors = uiState.sectors,
                onMarketSelected = onMarketSelected,
                onSectorSelected = onSectorSelected,
            )
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    uiState.companies.isNotEmpty() -> CompanyList(
                        companies = uiState.companies,
                        marketData = uiState.marketData,
                        failedRemoval = uiState.message as? WatchlistMessage.RemoveFailed,
                        onVisibleRowsChanged = onVisibleRowsChanged,
                        onOpenDetail = onOpenDetail,
                        onRemove = onRemove,
                    )
                    uiState.hasCompanies -> CenteredMessage(stringResource(R.string.watchlist_no_matches))
                    uiState.isLoaded -> CenteredMessage(stringResource(R.string.watchlist_empty))
                    uiState.loadError != null -> CenteredMessage(apiErrorMessage(uiState.loadError))
                    else -> CenteredMessage("")
                }
            }
        }
    }
}

@Composable
private fun WatchlistMessageSnackbar(
    message: WatchlistMessage?,
    snackbarHostState: SnackbarHostState,
    onUndo: (WatchlistMessage.Removed) -> Unit,
    onMessageShown: (WatchlistMessage) -> Unit,
) {
    message ?: return
    val text = watchlistMessageText(message)
    val undoLabel = stringResource(R.string.undo)
    LaunchedEffect(message) {
        val isRemoval = message is WatchlistMessage.Removed
        val result = snackbarHostState.showSnackbar(
            message = text,
            actionLabel = undoLabel.takeIf { isRemoval },
            duration = if (isRemoval) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed && message is WatchlistMessage.Removed) onUndo(message) else onMessageShown(message)
    }
}

@Composable
private fun watchlistMessageText(message: WatchlistMessage): String = when (message) {
    is WatchlistMessage.LoadFailed -> apiErrorMessage(message.error)
    is WatchlistMessage.Removed -> stringResource(R.string.watchlist_removed, message.company.name)
    is WatchlistMessage.RemoveFailed ->
        stringResource(R.string.watchlist_remove_failed, message.company.ticker, apiErrorMessage(message.error))
    is WatchlistMessage.UndoFailed ->
        stringResource(R.string.watchlist_undo_failed, message.company.ticker, apiErrorMessage(message.error))
}

@Composable
private fun WatchlistFilters(
    filter: WatchlistFilter,
    sectors: List<String>,
    onMarketSelected: (MarketFilter) -> Unit,
    onSectorSelected: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterDropdown(
            label = stringResource(R.string.filter_market),
            selected = filter.market,
            options = MarketFilter.entries,
            optionLabel = { marketFilterLabel(it) },
            onSelect = onMarketSelected,
            isActive = filter.market != MarketFilter.ALL,
        )
        FilterDropdown(
            label = stringResource(R.string.filter_sector),
            selected = filter.sector,
            options = listOf<String?>(null) + sectors,
            optionLabel = { it ?: stringResource(R.string.filter_all) },
            onSelect = onSectorSelected,
            isActive = filter.sector != null,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun marketFilterLabel(market: MarketFilter): String = when (market) {
    MarketFilter.ALL -> stringResource(R.string.filter_all)
    MarketFilter.US -> stringResource(R.string.market_filter_us)
    MarketFilter.EU -> stringResource(R.string.market_filter_eu)
}

@Composable
private fun CompanyList(
    companies: List<WatchlistItem>,
    marketData: Map<Symbol, RowMarketData>,
    failedRemoval: WatchlistMessage.RemoveFailed?,
    onVisibleRowsChanged: (VisibleRows) -> Unit,
    onOpenDetail: (WatchlistItem) -> Unit,
    onRemove: (WatchlistItem) -> Unit,
) {
    val listState = rememberLazyListState()
    val currentOnVisibleRowsChanged by rememberUpdatedState(onVisibleRowsChanged)
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.visibleRows() }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { currentOnVisibleRowsChanged(it) }
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag(WatchlistScreenTags.LIST)) {
        items(companies, key = { it.symbol.value }) { company ->
            SwipeToRemoveCompanyRow(
                company = company,
                marketData = marketData[company.symbol],
                removalFailed = failedRemoval?.takeIf { it.company.symbol == company.symbol },
                onOpenDetail = { onOpenDetail(company) },
                onRemove = { onRemove(company) },
            )
            HorizontalDivider()
        }
    }
}

/**
 * Watchlist row removed by swiping it to the start; screen readers get the removal as a custom action instead.
 * The row slides back when [removalFailed] reports that its removal failed.
 */
@Composable
private fun SwipeToRemoveCompanyRow(
    company: WatchlistItem,
    marketData: RowMarketData?,
    removalFailed: WatchlistMessage.RemoveFailed?,
    onOpenDetail: () -> Unit,
    onRemove: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(removalFailed) {
        if (removalFailed != null) dismissState.reset()
    }
    val removeLabel = stringResource(R.string.watchlist_remove_description, company.name)
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) SwipeToRemoveBackground()
        },
        enableDismissFromStartToEnd = false,
        onDismiss = { onRemove() },
    ) {
        CompanyRow(
            company = company,
            onClick = onOpenDetail,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .semantics {
                    customActions = listOf(CustomAccessibilityAction(removeLabel) { onRemove(); true })
                },
            marketData = { WatchlistMarketData(marketData) },
        )
    }
}

@Composable
private fun SwipeToRemoveBackground() {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp)
            .testTag(WatchlistScreenTags.SWIPE_BACKGROUND)
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_delete),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(
            text = stringResource(R.string.watchlist_swipe_remove),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

private fun List<LazyListItemInfo>.visibleRows(): VisibleRows? =
    if (isEmpty()) null else VisibleRows(first = first().index, last = last().index)

@Preview(showBackground = true)
@Composable
private fun WatchlistContentPreview() {
    SixteenTheme(dynamicColor = false) {
        WatchlistContent(
            uiState = WatchlistUiState(
                companies = listOf(
                    WatchlistItem(Symbol("AAPL"), "AAPL", "NASDAQ", "Apple Inc.", Market.US, "/logos/AAPL", "Technology", emptyList()),
                ),
                isLoaded = true,
                hasCompanies = true,
                sectors = listOf("Technology"),
            ),
            onRefresh = {},
            onMarketSelected = {},
            onSectorSelected = {},
            onRemove = {},
            onUndo = {},
            onMessageShown = {},
            onOpenDetail = {},
            onAddCompany = {},
        )
    }
}
