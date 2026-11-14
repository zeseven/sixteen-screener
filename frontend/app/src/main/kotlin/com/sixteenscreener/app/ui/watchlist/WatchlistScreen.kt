package com.sixteenscreener.app.ui.watchlist

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.sixteenscreener.app.R
import com.sixteenscreener.app.domain.model.CompanySummary
import com.sixteenscreener.app.ui.component.PlaceholderContent

/** Watchlist tab. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun WatchlistScreen(
    onOpenDetail: (CompanySummary) -> Unit,
    onAddCompany: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderContent(title = stringResource(R.string.tab_watchlist), modifier = modifier)
}
