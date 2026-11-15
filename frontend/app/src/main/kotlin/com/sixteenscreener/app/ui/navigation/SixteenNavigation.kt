package com.sixteenscreener.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sixteenscreener.app.ui.detail.DetailScreen
import com.sixteenscreener.app.ui.profile.ProfileScreen
import com.sixteenscreener.app.ui.search.SearchScreen
import com.sixteenscreener.app.ui.settings.SettingsScreen
import com.sixteenscreener.app.ui.sources.SourcesScreen
import com.sixteenscreener.app.ui.watchlist.WatchlistScreen

/** App content: nothing until the stored profile has been read, then the navigation host. */
@Composable
fun SixteenNavigation(
    modifier: Modifier = Modifier,
    viewModel: AppViewModel = hiltViewModel(),
) {
    val hasProfile by viewModel.hasProfile.collectAsStateWithLifecycle()
    hasProfile?.let { AppNavHost(hasProfile = it, destinations = AppScreens, modifier = modifier) }
}

private val AppScreens = AppDestinations(
    profile = { ProfileScreen() },
    tab = { tab, navigator, modifier ->
        when (tab) {
            MainTab.WATCHLIST -> WatchlistScreen(
                onOpenDetail = { navigator.openDetail(it, inWatchlist = true) },
                onAddCompany = { navigator.selectTab(MainTab.SEARCH) },
                modifier = modifier,
            )
            MainTab.SEARCH -> SearchScreen(onOpenDetail = { navigator.openDetail(it, it.inWatchlist) }, modifier = modifier)
            MainTab.SOURCES -> SourcesScreen(modifier = modifier)
        }
    },
    detail = { route, navigator -> DetailScreen(route = route, onBack = navigator::goBack) },
    settings = { navigator ->
        SettingsScreen(onBack = navigator::goBack, onProfileSwitched = { navigator.showMain(MainTab.WATCHLIST) })
    },
)
