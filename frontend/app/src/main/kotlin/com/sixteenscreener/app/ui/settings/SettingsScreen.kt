package com.sixteenscreener.app.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.sixteenscreener.app.R
import com.sixteenscreener.app.ui.component.BackTopAppBar
import com.sixteenscreener.app.ui.component.PlaceholderContent

/** Settings screen. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { BackTopAppBar(title = stringResource(R.string.settings_title), onBack = onBack) },
    ) { innerPadding ->
        PlaceholderContent(title = stringResource(R.string.settings_title), modifier = Modifier.padding(innerPadding))
    }
}
