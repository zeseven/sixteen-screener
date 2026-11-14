package com.sixteenscreener.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.sixteenscreener.app.ui.main.MainScreen

/**
 * Screens shown for the routes; each receives the [AppNavigator] to navigate further.
 */
@Immutable
class AppDestinations(
    val profile: @Composable () -> Unit,
    val tab: @Composable (tab: MainTab, navigator: AppNavigator, modifier: Modifier) -> Unit,
    val detail: @Composable (route: DetailRoute, navigator: AppNavigator) -> Unit,
    val settings: @Composable (navigator: AppNavigator) -> Unit,
)

/**
 * Navigation host: starts on the main screen when a profile is stored, on the profile prompt otherwise,
 * and follows later changes of [hasProfile].
 */
@Composable
fun AppNavHost(
    hasProfile: Boolean,
    destinations: AppDestinations,
    modifier: Modifier = Modifier,
    navigator: AppNavigator = rememberAppNavigator(hasProfile),
) {
    LaunchedEffect(navigator, hasProfile) { navigator.syncWithProfile(hasProfile) }
    NavDisplay(
        backStack = navigator.backStack,
        onBack = navigator::goBack,
        modifier = modifier,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<ProfileRoute> { destinations.profile() }
            entry<MainRoute> {
                MainScreen(
                    selectedTab = navigator.selectedTab,
                    onSelectTab = navigator::selectTab,
                    onOpenSettings = navigator::openSettings,
                ) { tab, contentModifier -> destinations.tab(tab, navigator, contentModifier) }
            }
            entry<DetailRoute> { route -> destinations.detail(route, navigator) }
            entry<SettingsRoute> { destinations.settings(navigator) }
        },
    )
}
