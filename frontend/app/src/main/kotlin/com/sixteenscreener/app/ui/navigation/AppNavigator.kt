package com.sixteenscreener.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.sixteenscreener.app.domain.model.Company
import com.sixteenscreener.app.domain.model.identity
import com.sixteenscreener.app.domain.model.Symbol

/**
 * App navigation state and the only place that changes it: the back stack of routes and the selected main screen tab.
 */
@Stable
class AppNavigator(
    val backStack: NavBackStack<NavKey>,
    selectedTabState: MutableState<MainTab> = mutableStateOf(MainTab.WATCHLIST),
) {
    var selectedTab: MainTab by selectedTabState
        private set

    val currentRoute: NavKey? get() = backStack.lastOrNull()

    fun selectTab(tab: MainTab) {
        selectedTab = tab
    }

    /** Opens the detailed screen with the row's [company] as its header until the company profile arrives. */
    fun openDetail(company: Company, inWatchlist: Boolean) {
        backStack.add(DetailRoute(company.symbol, company.identity(), inWatchlist))
    }

    /** Opens the detailed screen without row data; the screen loads its header itself. */
    fun openDetail(symbol: Symbol) {
        backStack.add(DetailRoute(symbol))
    }

    fun openSettings() {
        if (currentRoute != SettingsRoute) backStack.add(SettingsRoute)
    }

    fun goBack() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    /** Replaces the whole back stack with the main screen showing [tab], e.g. after a profile switch. */
    fun showMain(tab: MainTab = MainTab.WATCHLIST) {
        replaceAll(MainRoute)
        selectedTab = tab
    }

    /** Shows the main screen once a profile is stored, and the profile prompt when there is none (first start or a removed profile). */
    fun syncWithProfile(hasProfile: Boolean) {
        when {
            hasProfile && ProfileRoute in backStack -> showMain()
            !hasProfile && backStack.toList() != listOf(ProfileRoute) -> replaceAll(ProfileRoute)
        }
    }

    private fun replaceAll(route: NavKey) {
        backStack.clear()
        backStack.add(route)
    }

    companion object {
        fun startRoute(hasProfile: Boolean): NavKey = if (hasProfile) MainRoute else ProfileRoute
    }
}

/** Navigator whose back stack and selected tab survive configuration changes and process death. */
@Composable
fun rememberAppNavigator(hasProfile: Boolean): AppNavigator {
    val backStack = rememberNavBackStack(AppNavigator.startRoute(hasProfile))
    val selectedTab = rememberSaveable { mutableStateOf(MainTab.WATCHLIST) }
    return remember(backStack, selectedTab) { AppNavigator(backStack, selectedTab) }
}
