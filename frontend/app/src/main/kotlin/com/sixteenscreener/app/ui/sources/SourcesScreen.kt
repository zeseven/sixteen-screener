package com.sixteenscreener.app.ui.sources

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.sixteenscreener.app.R
import com.sixteenscreener.app.ui.component.PlaceholderContent

/** Sources tab. */
@Composable
fun SourcesScreen(modifier: Modifier = Modifier) {
    PlaceholderContent(title = stringResource(R.string.tab_sources), modifier = modifier)
}
