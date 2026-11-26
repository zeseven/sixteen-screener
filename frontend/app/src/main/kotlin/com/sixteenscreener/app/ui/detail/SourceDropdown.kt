package com.sixteenscreener.app.ui.detail

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sixteenscreener.app.R
import com.sixteenscreener.app.domain.model.SourceId
import com.sixteenscreener.app.domain.model.SourceOption
import com.sixteenscreener.app.ui.component.FilterDropdown
import com.sixteenscreener.app.ui.theme.SixteenTheme

object SourceDropdownTags {
    const val DROPDOWN = "detail_source_dropdown"
    const val NO_SOURCE = "detail_no_source"
}

/**
 * Source dropdown of a detailed screen tab: the [options] by name in the given order, the profile's default source and manual-only
 * sources marked as on the sources screen. Shown only with options.
 */
@Composable
fun SourceDropdown(
    options: List<SourceOption>,
    selectedSource: SourceId?,
    onSourceSelected: (SourceId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = options.firstOrNull { it.id == selectedSource } ?: options.firstOrNull() ?: return
    FilterDropdown(
        label = stringResource(R.string.detail_source_dropdown),
        selected = selected,
        options = options,
        optionLabel = { sourceOptionLabel(it) },
        onSelect = { onSourceSelected(it.id) },
        isActive = false,
        modifier = modifier
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .testTag(SourceDropdownTags.DROPDOWN),
    )
}

/** Message of a tab whose data no source provides for the company. */
@Composable
fun NoSourceMessage(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.detail_no_source),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(16.dp)
            .testTag(SourceDropdownTags.NO_SOURCE),
    )
}

@Composable
private fun sourceOptionLabel(option: SourceOption): String = when {
    option.isDefault && !option.automaticRequests -> stringResource(R.string.detail_source_option_default_manual_only, option.name)
    option.isDefault -> stringResource(R.string.detail_source_option_default, option.name)
    !option.automaticRequests -> stringResource(R.string.detail_source_option_manual_only, option.name)
    else -> option.name
}

@Preview(showBackground = true)
@Composable
private fun SourceDropdownPreview() {
    SixteenTheme(dynamicColor = false) {
        SourceDropdown(
            options = listOf(
                SourceOption(SourceId.FINNHUB, "Finnhub", isDefault = true, automaticRequests = true),
                SourceOption(SourceId("yahoo"), "Yahoo Finance", isDefault = false, automaticRequests = false),
            ),
            selectedSource = SourceId("yahoo"),
            onSourceSelected = {},
        )
    }
}
