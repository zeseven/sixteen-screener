package com.sixteenscreener.app.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sixteenscreener.app.R
import com.sixteenscreener.app.domain.model.CompanySummary
import com.sixteenscreener.app.domain.model.Market
import com.sixteenscreener.app.domain.model.Symbol
import com.sixteenscreener.app.ui.component.AddToWatchlistButton
import com.sixteenscreener.app.ui.component.CompanyRow
import com.sixteenscreener.app.ui.component.apiErrorMessage
import com.sixteenscreener.app.ui.theme.SixteenTheme

object SearchScreenTags {
    const val QUERY_FIELD = "search_query_field"
    const val NOTICE = "search_notice"
    const val PROGRESS = "search_progress"
}

/** Search tab. */
@Composable
fun SearchScreen(
    onOpenDetail: (CompanySummary) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SearchContent(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onAdd = viewModel::onAdd,
        onMessageShown = viewModel::onMessageShown,
        onOpenDetail = onOpenDetail,
        modifier = modifier,
    )
}

@Composable
fun SearchContent(
    uiState: SearchUiState,
    onQueryChange: (String) -> Unit,
    onAdd: (CompanySummary) -> Unit,
    onMessageShown: (AddFailedMessage) -> Unit,
    onOpenDetail: (CompanySummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    uiState.message?.let { message ->
        val text = stringResource(R.string.search_add_failed, message.company.ticker, apiErrorMessage(message.error))
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(text)
            onMessageShown(message)
        }
    }
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            QueryField(query = uiState.query, onQueryChange = onQueryChange)
            uiState.notice?.let { SearchNoticeText(it) }
            Box(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                if (uiState.isSearching) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag(SearchScreenTags.PROGRESS))
                }
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(uiState.results, key = { it.symbol.value }) { company ->
                    CompanyRow(company = company, onClick = { onOpenDetail(company) }) {
                        AddToWatchlistButton(
                            companyName = company.name,
                            inWatchlist = company.inWatchlist,
                            onClick = { onAdd(company) },
                        )
                    }
                    HorizontalDivider()
                }
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
        label = { Text(stringResource(R.string.search_label)) },
        singleLine = true,
        maxLines = 1,
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.search_clear))
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(SearchScreenTags.QUERY_FIELD),
    )
}

@Composable
private fun SearchNoticeText(notice: SearchNotice) {
    Text(
        text = stringResource(
            when (notice) {
                SearchNotice.LIMIT_REACHED -> R.string.search_limit_reached
                SearchNotice.SOURCE_ERROR -> R.string.search_source_error
                SearchNotice.NOTHING_FOUND -> R.string.search_nothing_found
                SearchNotice.REQUEST_FAILED -> R.string.error_network
            },
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = if (notice == SearchNotice.NOTHING_FOUND) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .testTag(SearchScreenTags.NOTICE),
    )
}

@Preview(showBackground = true)
@Composable
private fun SearchContentPreview() {
    SixteenTheme(dynamicColor = false) {
        SearchContent(
            uiState = SearchUiState(
                query = "app",
                results = listOf(
                    CompanySummary(Symbol("AAPL"), "AAPL", "NASDAQ", "Apple Inc.", Market.US, "Technology", "/logos/AAPL", null, false),
                ),
            ),
            onQueryChange = {},
            onAdd = {},
            onMessageShown = {},
            onOpenDetail = {},
        )
    }
}
