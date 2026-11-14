package com.sixteenscreener.app.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.sixteenscreener.app.R
import com.sixteenscreener.app.domain.model.CompanySummary
import com.sixteenscreener.app.ui.component.PlaceholderContent

/** Search tab. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun SearchScreen(
    onOpenDetail: (CompanySummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderContent(title = stringResource(R.string.tab_search), modifier = modifier)
}
