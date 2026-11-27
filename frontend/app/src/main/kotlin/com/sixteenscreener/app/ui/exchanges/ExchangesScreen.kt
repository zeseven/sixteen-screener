package com.sixteenscreener.app.ui.exchanges

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sixteenscreener.app.R
import com.sixteenscreener.app.domain.DisplaySymbols
import com.sixteenscreener.app.domain.ExchangeFilter
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.Exchange
import com.sixteenscreener.app.domain.model.Market
import com.sixteenscreener.app.ui.component.CenteredMessage
import com.sixteenscreener.app.ui.component.apiErrorMessage
import com.sixteenscreener.app.ui.component.marketDescription
import com.sixteenscreener.app.ui.theme.SixteenTheme

object ExchangesScreenTags {
    const val QUERY_FIELD = "exchanges_query_field"
    const val PROGRESS = "exchanges_progress"
    const val TABLE = "exchanges_table"
    const val HEADER = "exchanges_header"
    const val ROW = "exchanges_row"
    const val FLAG = "exchanges_flag"
    const val COUNTRY = "exchanges_country"
    const val EODHD_CODE = "exchanges_eodhd_code"
    const val RETRY = "exchanges_retry"
}

private val SuffixColumnWidth = 64.dp
private val MarketColumnWidth = 64.dp
private val ShortNameColumnWidth = 104.dp
private val FullNameColumnWidth = 240.dp
private val MicColumnWidth = 64.dp
private val EodhdCodeColumnWidth = 72.dp
private val CountryColumnWidth = 80.dp
private val CurrencyColumnWidth = 80.dp
private val TimeZoneColumnWidth = 176.dp
private val CellPadding = 8.dp
private val TableWidth = SuffixColumnWidth + MarketColumnWidth + ShortNameColumnWidth + FullNameColumnWidth +
    MicColumnWidth + EodhdCodeColumnWidth + CountryColumnWidth + CurrencyColumnWidth + TimeZoneColumnWidth + CellPadding * 2

/** Exchanges tab; loads the exchange table when opened and again when shown after a failed load. */
@Composable
fun ExchangesScreen(
    modifier: Modifier = Modifier,
    viewModel: ExchangesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.load() }
    ExchangesContent(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onRetry = viewModel::load,
        modifier = modifier,
    )
}

@Composable
fun ExchangesContent(
    uiState: ExchangesUiState,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            QueryField(query = uiState.query, onQueryChange = onQueryChange)
            val exchanges = uiState.exchanges
            when {
                exchanges == null -> uiState.loadError?.let { LoadError(it, onRetry) } ?: Loading()
                exchanges.isEmpty() -> CenteredMessage(stringResource(R.string.exchanges_empty))
                uiState.filteredExchanges.isEmpty() -> CenteredMessage(stringResource(R.string.exchanges_no_matches))
                else -> ExchangeTable(uiState.filteredExchanges)
            }
        }
    }
}

@Composable
private fun QueryField(query: String, onQueryChange: (String) -> Unit) {
    val keyboardController = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text(stringResource(R.string.exchanges_filter_label)) },
        singleLine = true,
        maxLines = 1,
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.exchanges_filter_clear))
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(ExchangesScreenTags.QUERY_FIELD),
    )
}

@Composable
private fun ExchangeTable(exchanges: List<Exchange>) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(rememberScrollState())
            .testTag(ExchangesScreenTags.TABLE),
    ) {
        Column(modifier = Modifier.width(TableWidth).fillMaxHeight()) {
            HeaderRow()
            HorizontalDivider()
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(exchanges, key = { "${it.suffix}|${it.mic}|${it.shortName}" }) { exchange ->
                    ExchangeRow(exchange)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun HeaderRow() {
    TableRow(modifier = Modifier.semantics(mergeDescendants = true) { heading() }.testTag(ExchangesScreenTags.HEADER)) {
        val style = MaterialTheme.typography.labelLarge
        Cell(stringResource(R.string.exchange_column_suffix), SuffixColumnWidth, style)
        Cell(stringResource(R.string.exchange_column_market), MarketColumnWidth, style, textAlign = TextAlign.Center)
        Cell(stringResource(R.string.exchange_column_short_name), ShortNameColumnWidth, style)
        Cell(stringResource(R.string.exchange_column_full_name), FullNameColumnWidth, style)
        Cell(stringResource(R.string.exchange_column_mic), MicColumnWidth, style)
        Cell(stringResource(R.string.exchange_column_eodhd_code), EodhdCodeColumnWidth, style)
        Cell(stringResource(R.string.exchange_column_country), CountryColumnWidth, style)
        Cell(stringResource(R.string.exchange_column_currency), CurrencyColumnWidth, style)
        Cell(stringResource(R.string.exchange_column_time_zone), TimeZoneColumnWidth, style)
    }
}

@Composable
private fun ExchangeRow(exchange: Exchange) {
    TableRow(modifier = Modifier.semantics(mergeDescendants = true) {}.testTag(ExchangesScreenTags.ROW)) {
        val style = MaterialTheme.typography.bodyMedium
        Cell(ExchangeFilter.displaySuffix(exchange), SuffixColumnWidth, style)
        FlagCell(exchange.market)
        Cell(exchange.shortName, ShortNameColumnWidth, style)
        Cell(exchange.fullName, FullNameColumnWidth, style)
        Cell(exchange.mic, MicColumnWidth, style)
        Cell(exchange.eodhdCode.orEmpty(), EodhdCodeColumnWidth, style, modifier = Modifier.testTag(ExchangesScreenTags.EODHD_CODE))
        Cell(
            text = if (exchange.mainVenue) stringResource(R.string.exchange_country_main_venue, exchange.country) else exchange.country,
            width = CountryColumnWidth,
            style = style,
            modifier = Modifier.testTag(ExchangesScreenTags.COUNTRY),
        )
        Cell(exchange.currency, CurrencyColumnWidth, style)
        Cell(exchange.timeZone, TimeZoneColumnWidth, style)
    }
}

@Composable
private fun TableRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CellPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
private fun Cell(
    text: String,
    width: Dp,
    style: TextStyle,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        style = style,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .width(width)
            .padding(end = CellPadding),
    )
}

@Composable
private fun FlagCell(market: Market?) {
    Box(modifier = Modifier.width(MarketColumnWidth), contentAlignment = Alignment.Center) {
        DisplaySymbols.flagOf(market)?.let { flag ->
            val description = marketDescription(market)
            Text(
                text = flag,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .semantics { contentDescription = description }
                    .testTag(ExchangesScreenTags.FLAG),
            )
        }
    }
}

@Composable
private fun LoadError(error: ApiError, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = apiErrorMessage(error),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Button(onClick = onRetry, modifier = Modifier.testTag(ExchangesScreenTags.RETRY)) {
            Text(stringResource(R.string.exchanges_retry))
        }
    }
}

@Composable
private fun Loading() {
    val description = stringResource(R.string.exchanges_loading)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier
                .semantics { contentDescription = description }
                .testTag(ExchangesScreenTags.PROGRESS),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ExchangesContentPreview() {
    SixteenTheme(dynamicColor = false) {
        ExchangesContent(
            uiState = ExchangesUiState(
                exchanges = listOf(
                    Exchange("", "NYSE", "New York Stock Exchange", "XNYS", "US", "", "US", true, "USD", "America/New_York", Market.US),
                    Exchange("DE", "XETRA", "Deutsche Boerse Xetra", "XETR", "XETRA", "DE", "DE", true, "EUR", "Europe/Berlin", Market.EU),
                    Exchange("V", "TSXV", "TSX Venture Exchange", "XTSX", "V", "V", "CA", true, "CAD", "America/Toronto", null),
                ),
            ),
            onQueryChange = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ExchangesContentErrorPreview() {
    SixteenTheme(dynamicColor = false) {
        ExchangesContent(
            uiState = ExchangesUiState(loadError = ApiError.Network(isTimeout = false)),
            onQueryChange = {},
            onRetry = {},
        )
    }
}
