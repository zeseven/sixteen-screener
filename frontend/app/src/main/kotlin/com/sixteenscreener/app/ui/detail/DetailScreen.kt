package com.sixteenscreener.app.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.sixteenscreener.app.R
import com.sixteenscreener.app.ui.component.BackTopAppBar
import com.sixteenscreener.app.ui.component.CompanyRow
import com.sixteenscreener.app.ui.component.PlaceholderContent
import com.sixteenscreener.app.ui.navigation.DetailRoute

/** Detailed screen of a company; the header is the row data passed with the route when available. */
@Composable
fun DetailScreen(
    route: DetailRoute,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { BackTopAppBar(title = route.symbol.value, onBack = onBack) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            route.initialSummary?.let { summary -> CompanyRow(company = summary, onClick = {}) }
            PlaceholderContent(title = stringResource(R.string.detail_title))
        }
    }
}
