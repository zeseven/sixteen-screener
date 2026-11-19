package com.sixteenscreener.app.ui.sources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sixteenscreener.app.R
import com.sixteenscreener.app.domain.ConnectionIndicator
import com.sixteenscreener.app.domain.LimitResetTime
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.CallResult
import com.sixteenscreener.app.domain.model.Capability
import com.sixteenscreener.app.domain.model.LimitInterval
import com.sixteenscreener.app.domain.model.LimitUnit
import com.sixteenscreener.app.domain.model.RequestLimit
import com.sixteenscreener.app.domain.model.Source
import com.sixteenscreener.app.domain.model.SourceCallOutcome
import com.sixteenscreener.app.domain.model.SourceId
import com.sixteenscreener.app.domain.model.SourceRole
import com.sixteenscreener.app.ui.component.CenteredMessage
import com.sixteenscreener.app.ui.component.LocalClock
import com.sixteenscreener.app.ui.component.apiErrorMessage
import com.sixteenscreener.app.ui.theme.SixteenTheme
import java.time.Instant

object SourcesScreenTags {
    const val CARD = "source_card"
    const val NAME = "source_name"
    const val DEFAULT_MARKER = "source_default_marker"
    const val MANUAL_ONLY_MARKER = "source_manual_only_marker"
    const val DEFAULT_FOR = "source_default_for"
    const val CONNECTION_INDICATOR = "source_connection_indicator"
    const val LIMIT = "source_limit"
    const val TOTAL_CALLS = "source_total_calls"
}

/** Sources tab; reloads the sources when shown. */
@Composable
fun SourcesScreen(
    modifier: Modifier = Modifier,
    viewModel: SourcesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.refresh() }
    SourcesContent(
        uiState = uiState,
        onRefresh = viewModel::refresh,
        onMessageShown = viewModel::onMessageShown,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesContent(
    uiState: SourcesUiState,
    onRefresh: () -> Unit,
    onMessageShown: (ApiError) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    uiState.message?.let { error ->
        val text = apiErrorMessage(error)
        LaunchedEffect(error) {
            snackbarHostState.showSnackbar(text)
            onMessageShown(error)
        }
    }
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            val sources = uiState.sources
            when {
                !sources.isNullOrEmpty() -> SourceList(sources)
                sources != null -> CenteredMessage(stringResource(R.string.sources_empty))
                uiState.loadError != null -> CenteredMessage(apiErrorMessage(uiState.loadError))
                else -> CenteredMessage("")
            }
        }
    }
}

@Composable
private fun SourceList(sources: List<Source>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(sources, key = { it.id.value }) { SourceCard(it) }
    }
}

@Composable
fun SourceCard(source: Source, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth().testTag(SourcesScreenTags.CARD)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .semantics { heading() }
                        .testTag(SourcesScreenTags.NAME),
                )
                if (source.isDefault) Marker(stringResource(R.string.source_default), SourcesScreenTags.DEFAULT_MARKER)
                if (!source.automaticRequests) Marker(stringResource(R.string.source_manual_only), SourcesScreenTags.MANUAL_ONLY_MARKER)
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    ConnectionIndicatorText(source.lastCall)
                }
            }
            defaultForText(source.defaultFor)?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(SourcesScreenTags.DEFAULT_FOR))
            }
            capabilitiesText(source.capabilities)?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            source.limits.forEach { LimitText(it) }
            source.totalCalls?.let { TotalCallsText(it) }
            if (source.limits.isEmpty() && source.totalCalls == null) {
                Text(text = stringResource(R.string.source_no_limits), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Marker(text: String, testTag: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.testTag(testTag),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ConnectionIndicatorText(lastCall: SourceCallOutcome?) {
    val indicator = ConnectionIndicator.of(lastCall)
    if (indicator.isEmpty()) return
    val description = when (lastCall?.result) {
        CallResult.HTTP_ERROR -> stringResource(R.string.connection_http_error, indicator)
        CallResult.TIMEOUT -> stringResource(R.string.connection_timeout)
        else -> stringResource(R.string.connection_app_error)
    }
    Text(
        text = indicator,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .semantics { contentDescription = description }
            .testTag(SourcesScreenTags.CONNECTION_INDICATOR),
    )
}

@Composable
private fun defaultForText(roles: List<SourceRole>): String? {
    val names = roles.mapNotNull { roleName(it) }.map { stringResource(it) }
    return names.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.source_default_for, it.joinToString(", ")) }
}

private fun roleName(role: SourceRole): Int? = when (role) {
    SourceRole.SEARCH -> R.string.source_role_search
    SourceRole.US -> R.string.source_role_us
    SourceRole.INTERNATIONAL -> R.string.source_role_international
    SourceRole.UNKNOWN -> null
}

@Composable
private fun capabilitiesText(capabilities: List<Capability>): String? {
    val names = capabilities.mapNotNull { capabilityName(it) }.map { stringResource(it) }
    return names.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.source_capabilities, it.joinToString(", ")) }
}

private fun capabilityName(capability: Capability): Int? = when (capability) {
    Capability.SEARCH -> R.string.capability_search
    Capability.QUOTE -> R.string.capability_quote
    Capability.RATIOS -> R.string.capability_ratios
    Capability.DIVIDENDS -> R.string.capability_dividends
    Capability.BALANCE_SHEET -> R.string.capability_balance_sheet
    Capability.COMPANY_PROFILE -> R.string.capability_company_profile
    Capability.LOGO -> R.string.capability_logo
    Capability.UNKNOWN -> null
}

@Composable
private fun LimitText(limit: RequestLimit) {
    val interval = stringResource(intervalName(limit.interval))
    val usageCount = if (limit.unit == LimitUnit.UNIQUE_SYMBOLS) {
        pluralStringResource(R.plurals.source_limit_usage_symbols, limit.limit, limit.used, limit.limit, interval)
    } else {
        stringResource(R.string.source_limit_usage, limit.used, limit.limit, interval)
    }
    val isReached = limit.used >= limit.limit
    val usage = if (isReached) stringResource(R.string.source_limit_reached, usageCount) else usageCount
    val clock = LocalClock.current
    val text = limit.resetsAt?.let { resetsAt ->
        stringResource(R.string.source_limit_with_reset, usage, stringResource(R.string.source_limit_resets, LimitResetTime.format(resetsAt, clock)))
    } ?: usage
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isReached) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag(SourcesScreenTags.LIMIT),
    )
}

@Composable
private fun TotalCallsText(totalCalls: Long) {
    Text(
        text = stringResource(R.string.source_total_calls, totalCalls),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag(SourcesScreenTags.TOTAL_CALLS),
    )
}

private fun intervalName(interval: LimitInterval): Int = when (interval) {
    LimitInterval.MINUTE -> R.string.limit_per_minute
    LimitInterval.HOUR -> R.string.limit_per_hour
    LimitInterval.DAY -> R.string.limit_per_day
    LimitInterval.MONTH -> R.string.limit_per_month
    LimitInterval.UNKNOWN -> R.string.limit_per_interval
}

@Preview(showBackground = true)
@Composable
private fun SourcesContentPreview() {
    SixteenTheme(dynamicColor = false) {
        SourcesContent(
            uiState = SourcesUiState(
                sources = listOf(
                    Source(
                        id = SourceId.FINNHUB,
                        name = "Finnhub",
                        capabilities = listOf(Capability.SEARCH, Capability.QUOTE, Capability.BALANCE_SHEET),
                        roles = listOf(SourceRole.SEARCH, SourceRole.US),
                        defaultFor = listOf(SourceRole.SEARCH, SourceRole.US),
                        isDefault = true,
                        automaticRequests = true,
                        limits = listOf(RequestLimit(LimitInterval.MINUTE, LimitUnit.REQUESTS, 60, 12, Instant.now().plusSeconds(30))),
                        lastCall = SourceCallOutcome(CallResult.HTTP_ERROR, 429, Instant.now()),
                    ),
                    Source(
                        id = SourceId("finnhub-logos"),
                        name = "Finnhub logos",
                        capabilities = listOf(Capability.LOGO),
                        roles = emptyList(),
                        defaultFor = emptyList(),
                        isDefault = false,
                        automaticRequests = true,
                        limits = emptyList(),
                        lastCall = SourceCallOutcome(CallResult.TIMEOUT, null, Instant.now()),
                        totalCalls = 1234,
                    ),
                    Source(
                        id = SourceId.EODHD,
                        name = "EODHD",
                        capabilities = listOf(Capability.QUOTE, Capability.DIVIDENDS),
                        roles = listOf(SourceRole.INTERNATIONAL),
                        defaultFor = listOf(SourceRole.INTERNATIONAL),
                        isDefault = false,
                        automaticRequests = false,
                        limits = listOf(RequestLimit(LimitInterval.DAY, LimitUnit.REQUESTS, 20, 3, null)),
                        lastCall = null,
                    ),
                ),
            ),
            onRefresh = {},
            onMessageShown = {},
        )
    }
}
