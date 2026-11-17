package com.sixteenscreener.app.ui.indicators

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sixteenscreener.app.R
import com.sixteenscreener.app.domain.NumberFormats
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.IndicatorIntervals
import com.sixteenscreener.app.domain.model.IndicatorLevel
import com.sixteenscreener.app.domain.model.IndicatorScale
import com.sixteenscreener.app.domain.model.IndicatorSetting
import com.sixteenscreener.app.domain.model.RatioCategory
import com.sixteenscreener.app.domain.model.RatioUnit
import com.sixteenscreener.app.ui.component.BackTopAppBar
import com.sixteenscreener.app.ui.component.IntervalBar
import com.sixteenscreener.app.ui.component.apiErrorMessage
import com.sixteenscreener.app.ui.component.ratioDisplayName
import com.sixteenscreener.app.ui.theme.SixteenTheme

object IndicatorSettingsTags {
    const val PROGRESS = "indicator_settings_progress"
    const val RETRY = "indicator_settings_retry"
    const val CATEGORY = "indicator_settings_category"
    const val SETTING = "indicator_settings_setting"
    const val NAME = "indicator_settings_name"
    const val THRESHOLDS = "indicator_settings_thresholds"

    fun setting(ratioCode: String) = "indicator_settings_setting_$ratioCode"
}

/** Indicator settings screen opened from the settings screen; pending edits are saved when the screen stops. */
@Composable
fun IndicatorSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IndicatorSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flush() }
    IndicatorSettingsContent(
        uiState = uiState,
        onBack = onBack,
        onRetry = viewModel::load,
        onEnabledChange = viewModel::onEnabledChange,
        onIntervalsChange = viewModel::onIntervalsChange,
        onSaveErrorShown = viewModel::onSaveErrorShown,
        modifier = modifier,
    )
}

@Composable
fun IndicatorSettingsContent(
    uiState: IndicatorSettingsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onEnabledChange: (ratioCode: String, enabled: Boolean) -> Unit,
    onIntervalsChange: (ratioCode: String, intervals: IndicatorIntervals) -> Unit,
    onSaveErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    SaveErrorSnackbar(uiState.saveError, snackbarHostState, onSaveErrorShown)
    Scaffold(
        modifier = modifier,
        topBar = { BackTopAppBar(title = stringResource(R.string.indicator_settings_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when {
                uiState.isLoading -> Loading()
                uiState.loadError != null -> LoadError(uiState.loadError, onRetry)
                else -> SettingsList(uiState.groups, onEnabledChange, onIntervalsChange)
            }
        }
    }
}

@Composable
private fun SettingsList(
    groups: List<IndicatorSettingsGroup>,
    onEnabledChange: (String, Boolean) -> Unit,
    onIntervalsChange: (String, IndicatorIntervals) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        groups.forEach { group ->
            item(key = "category_${group.category}", contentType = "category") { CategoryHeader(group.category) }
            items(group.settings, key = { it.ratioCode }, contentType = { "setting" }) { setting ->
                SettingItem(setting, onEnabledChange, onIntervalsChange)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun CategoryHeader(category: RatioCategory) {
    Text(
        text = stringResource(categoryLabel(category)),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() }
            .testTag(IndicatorSettingsTags.CATEGORY),
    )
}

@Composable
private fun SettingItem(
    setting: IndicatorSetting,
    onEnabledChange: (String, Boolean) -> Unit,
    onIntervalsChange: (String, IndicatorIntervals) -> Unit,
) {
    val name = ratioDisplayName(setting.name, setting.unit)
    val thresholds = setting.intervals.thresholds.joinToString(THRESHOLD_SEPARATOR) { NumberFormats.threshold(it, setting.scale.step) }
    val thresholdsDescription = stringResource(R.string.indicator_settings_thresholds_description, thresholds)
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag(IndicatorSettingsTags.setting(setting.ratioCode)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = setting.enabled, role = Role.Checkbox) { onEnabledChange(setting.ratioCode, it) }
                .testTag(IndicatorSettingsTags.SETTING),
        ) {
            Checkbox(checked = setting.enabled, onCheckedChange = null)
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp)
                    .testTag(IndicatorSettingsTags.NAME),
            )
            Text(
                text = thresholds,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier
                    .semantics { contentDescription = thresholdsDescription }
                    .testTag(IndicatorSettingsTags.THRESHOLDS),
            )
        }
        IntervalBar(
            intervals = setting.intervals,
            scale = setting.scale,
            ratioName = name,
            onIntervalsChange = { onIntervalsChange(setting.ratioCode, it) },
            muted = !setting.enabled,
        )
    }
}

private const val THRESHOLD_SEPARATOR = " / "

@StringRes
private fun categoryLabel(category: RatioCategory): Int = when (category) {
    RatioCategory.VALUATION -> R.string.ratio_category_valuation
    RatioCategory.PROFITABILITY -> R.string.ratio_category_profitability
    RatioCategory.MARGINS -> R.string.ratio_category_margins
    RatioCategory.GROWTH -> R.string.ratio_category_growth
    RatioCategory.LIQUIDITY -> R.string.ratio_category_liquidity
    RatioCategory.LEVERAGE -> R.string.ratio_category_leverage
    RatioCategory.EFFICIENCY -> R.string.ratio_category_efficiency
    RatioCategory.DIVIDENDS -> R.string.ratio_category_dividends
    RatioCategory.RISK -> R.string.ratio_category_risk
    RatioCategory.PER_SHARE -> R.string.ratio_category_per_share
    RatioCategory.UNKNOWN -> R.string.ratio_category_other
}

@Composable
private fun SaveErrorSnackbar(error: ApiError?, snackbarHostState: SnackbarHostState, onShown: () -> Unit) {
    error ?: return
    val text = stringResource(R.string.indicator_settings_save_failed, apiErrorMessage(error))
    LaunchedEffect(error) {
        snackbarHostState.showSnackbar(text)
        onShown()
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
        Button(onClick = onRetry, modifier = Modifier.testTag(IndicatorSettingsTags.RETRY)) {
            Text(stringResource(R.string.indicator_settings_retry))
        }
    }
}

@Composable
private fun Loading() {
    val description = stringResource(R.string.indicator_settings_loading)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier
                .semantics { contentDescription = description }
                .testTag(IndicatorSettingsTags.PROGRESS),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun IndicatorSettingsContentPreview() {
    val levels = listOf(IndicatorLevel.GOOD, IndicatorLevel.NEUTRAL, IndicatorLevel.WARNING, IndicatorLevel.BAD)
    SixteenTheme(dynamicColor = false) {
        IndicatorSettingsContent(
            uiState = IndicatorSettingsUiState(
                isLoading = false,
                settings = listOf(
                    IndicatorSetting(
                        "PE", "P/E", "P/E", RatioUnit.RATIO, RatioCategory.VALUATION, IndicatorScale(60.0, 1.0), true,
                        IndicatorIntervals(listOf(15.0, 25.0, 40.0), levels, IndicatorLevel.BAD),
                    ),
                    IndicatorSetting(
                        "ROE", "Return on equity", "ROE", RatioUnit.PERCENT, RatioCategory.PROFITABILITY, IndicatorScale(50.0, 0.5),
                        false, IndicatorIntervals(listOf(5.0, 12.5, 20.0), levels.reversed(), IndicatorLevel.BAD),
                    ),
                ),
            ),
            onBack = {},
            onRetry = {},
            onEnabledChange = { _, _ -> },
            onIntervalsChange = { _, _ -> },
            onSaveErrorShown = {},
        )
    }
}
