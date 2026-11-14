package com.sixteenscreener.app.ui.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.sixteenscreener.app.R
import com.sixteenscreener.app.ui.navigation.MainTab
import com.sixteenscreener.app.ui.theme.SixteenTheme

/**
 * Main screen: top app bar with the settings button, bottom bar with the [MainTab] tabs.
 * Each tab keeps its saveable state while another tab is shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    tabContent: @Composable (tab: MainTab, modifier: Modifier) -> Unit,
) {
    val tabStateHolder = rememberSaveableStateHolder()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings_open))
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selectedTab,
                        onClick = { onSelectTab(tab) },
                        icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        tabStateHolder.SaveableStateProvider(selectedTab.name) {
            tabContent(selectedTab, Modifier.padding(innerPadding))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    SixteenTheme(dynamicColor = false) {
        MainScreen(selectedTab = MainTab.WATCHLIST, onSelectTab = {}, onOpenSettings = {}) { _, _ -> }
    }
}
