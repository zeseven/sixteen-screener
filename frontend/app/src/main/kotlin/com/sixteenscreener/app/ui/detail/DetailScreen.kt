package com.sixteenscreener.app.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sixteenscreener.app.R
import com.sixteenscreener.app.domain.model.Company
import com.sixteenscreener.app.domain.model.SourceId
import com.sixteenscreener.app.domain.model.Symbol
import com.sixteenscreener.app.ui.component.AddToWatchlistButton
import com.sixteenscreener.app.ui.component.BackTopAppBar
import com.sixteenscreener.app.ui.component.CompanyRow
import com.sixteenscreener.app.ui.component.QuotePriceAndCacheIndicator
import com.sixteenscreener.app.ui.component.RemoveFromWatchlistButton
import com.sixteenscreener.app.ui.component.apiErrorMessage
import com.sixteenscreener.app.ui.detail.balancesheet.BalanceSheetGrid
import com.sixteenscreener.app.ui.detail.balancesheet.BalanceSheetSourceDropdown
import com.sixteenscreener.app.ui.detail.dividends.DividendsTab
import com.sixteenscreener.app.ui.detail.priceratios.PriceAndRatiosTab
import com.sixteenscreener.app.ui.detail.profile.CompanyProfileTab
import com.sixteenscreener.app.ui.navigation.DetailRoute
import com.sixteenscreener.app.ui.theme.SixteenTheme

object DetailScreenTags {
    const val REFRESH = "detail_refresh"
    const val HEADER_SYMBOL = "detail_header_symbol"
    const val HEADER_TYPE = "detail_header_type"
}

/** Detailed screen of a company with the price and ratio, dividends, balance sheet and company profile tabs. */
@Composable
fun DetailScreen(
    route: DetailRoute,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel<DetailViewModel, DetailViewModel.Factory> { factory -> factory.create(route) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DetailContent(
        uiState = uiState,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onTabSelected = viewModel::onTabSelected,
        onSourceSelected = viewModel::onSourceSelected,
        onToggleWatchlist = viewModel::onToggleWatchlist,
        onMessageShown = viewModel::onMessageShown,
        modifier = modifier,
    )
}

@Composable
fun DetailContent(
    uiState: DetailUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onTabSelected: (DetailTab) -> Unit,
    onSourceSelected: (SourceId?) -> Unit,
    onToggleWatchlist: () -> Unit,
    onMessageShown: (DetailMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    DetailMessageSnackbar(uiState.message, snackbarHostState, onMessageShown)
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            BackTopAppBar(title = uiState.header?.name ?: uiState.symbol.value, onBack = onBack) {
                IconButton(onClick = onRefresh, modifier = Modifier.testTag(DetailScreenTags.REFRESH)) {
                    Icon(painterResource(R.drawable.ic_refresh), contentDescription = stringResource(R.string.detail_refresh))
                }
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Header(uiState, onToggleWatchlist)
            HorizontalDivider()
            DetailTabs(uiState.selectedTab, onTabSelected)
            when (uiState.selectedTab) {
                DetailTab.PRICE_AND_RATIOS -> PriceAndRatiosContent(uiState)
                DetailTab.DIVIDENDS -> DividendsContent(uiState)
                DetailTab.BALANCE_SHEET -> BalanceSheetContent(uiState, onSourceSelected)
                DetailTab.COMPANY_PROFILE -> CompanyProfileContent(uiState)
            }
        }
    }
}

@Composable
private fun Header(uiState: DetailUiState, onToggleWatchlist: () -> Unit) {
    val header = uiState.header
    if (header == null) {
        Text(
            text = uiState.symbol.value,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .testTag(DetailScreenTags.HEADER_SYMBOL),
        )
        return
    }
    Column {
        CompanyRow(company = header, onClick = null, marketData = { QuotePriceAndCacheIndicator(uiState.headerQuote) }) {
            if (uiState.inWatchlist) {
                RemoveFromWatchlistButton(companyName = header.name, onClick = onToggleWatchlist)
            } else {
                AddToWatchlistButton(companyName = header.name, inWatchlist = false, onClick = onToggleWatchlist)
            }
        }
        SecurityType(company = header, homeExchange = uiState.homeExchange)
    }
}

/** Security type of [company] under the header, in the error color when it is not a stock, with the home exchange mark. */
@Composable
private fun SecurityType(company: Company, homeExchange: Boolean) {
    val type = stringResource(R.string.detail_type, company.type ?: stringResource(R.string.detail_type_unknown))
    Text(
        text = if (homeExchange) stringResource(R.string.detail_type_home_exchange, type) else type,
        style = MaterialTheme.typography.labelSmall,
        color = if (company.stock) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            .testTag(DetailScreenTags.HEADER_TYPE),
    )
}

@Composable
private fun DetailTabs(selectedTab: DetailTab, onTabSelected: (DetailTab) -> Unit) {
    PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
        DetailTab.entries.forEach { tab ->
            Tab(
                selected = tab == selectedTab,
                onClick = { onTabSelected(tab) },
                text = { Text(stringResource(tab.title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

private val DetailTab.title: Int
    get() = when (this) {
        DetailTab.PRICE_AND_RATIOS -> R.string.detail_tab_price_and_ratios
        DetailTab.DIVIDENDS -> R.string.detail_tab_dividends
        DetailTab.BALANCE_SHEET -> R.string.detail_tab_balance_sheet
        DetailTab.COMPANY_PROFILE -> R.string.detail_tab_company_profile
    }

@Composable
private fun PriceAndRatiosContent(uiState: DetailUiState) {
    val data = uiState.priceAndRatios
    val meta = data.value?.meta
    SourceDataFrame(meta = meta, sourceName = meta?.let { uiState.sourceName(it.source) }, isLoading = data.isLoading, error = data.error) {
        data.value?.let { PriceAndRatiosTab(it) }
    }
}

@Composable
private fun DividendsContent(uiState: DetailUiState) {
    val data = uiState.dividends
    val meta = data.value?.meta
    SourceDataFrame(meta = meta, sourceName = meta?.let { uiState.sourceName(it.source) }, isLoading = data.isLoading, error = data.error) {
        data.value?.let { DividendsTab(it) }
    }
}

@Composable
private fun BalanceSheetContent(uiState: DetailUiState, onSourceSelected: (SourceId?) -> Unit) {
    val balanceSheet = uiState.balanceSheet
    val data = balanceSheet.data
    val meta = data.value?.meta
    Column {
        BalanceSheetSourceDropdown(
            sources = balanceSheet.sources,
            selectedSource = balanceSheet.selectedSource,
            defaultSource = balanceSheet.defaultSource,
            sourceName = uiState::sourceName,
            onSourceSelected = onSourceSelected,
            modifier = Modifier.padding(top = 8.dp),
        )
        SourceDataFrame(meta = meta, sourceName = meta?.let { uiState.sourceName(it.source) }, isLoading = data.isLoading, error = data.error) {
            data.value?.let { BalanceSheetGrid(it.table) }
        }
    }
}

@Composable
private fun CompanyProfileContent(uiState: DetailUiState) {
    val data = uiState.companyProfile
    val meta = data.value?.meta
    SourceDataFrame(meta = meta, sourceName = meta?.let { uiState.sourceName(it.source) }, isLoading = data.isLoading, error = data.error) {
        CompanyProfileTab(data.value?.profile)
    }
}

@Composable
private fun DetailMessageSnackbar(
    message: DetailMessage?,
    snackbarHostState: SnackbarHostState,
    onMessageShown: (DetailMessage) -> Unit,
) {
    message ?: return
    val text = when (message) {
        is DetailMessage.AddFailed -> stringResource(R.string.detail_add_failed, apiErrorMessage(message.error))
        is DetailMessage.RemoveFailed -> stringResource(R.string.detail_remove_failed, apiErrorMessage(message.error))
    }
    LaunchedEffect(message) {
        snackbarHostState.showSnackbar(text)
        onMessageShown(message)
    }
}

@Preview(showBackground = true)
@Composable
private fun DetailContentPreview() {
    SixteenTheme(dynamicColor = false) {
        DetailContent(
            uiState = DetailUiState(symbol = Symbol("AAPL")),
            onBack = {},
            onRefresh = {},
            onTabSelected = {},
            onSourceSelected = {},
            onToggleWatchlist = {},
            onMessageShown = {},
        )
    }
}
