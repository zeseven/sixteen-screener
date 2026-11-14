package com.sixteenscreener.app.ui.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.sixteenscreener.app.domain.model.Symbol
import com.sixteenscreener.app.testing.Companies.summary
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigatorTest {

    private fun navigator(vararg routes: NavKey) = AppNavigator(NavBackStack(*routes))

    @Test
    fun `start route is the main screen with a stored profile and the profile prompt without`() {
        assertEquals(MainRoute, AppNavigator.startRoute(hasProfile = true))
        assertEquals(ProfileRoute, AppNavigator.startRoute(hasProfile = false))
    }

    @Test
    fun `detail opened from a row carries the summary`() {
        val navigator = navigator(MainRoute)
        val apple = summary("AAPL")

        navigator.openDetail(apple)

        assertEquals(listOf(MainRoute, DetailRoute(Symbol("AAPL"), apple)), navigator.backStack.toList())
    }

    @Test
    fun `detail opened by symbol has no summary`() {
        val navigator = navigator(MainRoute)

        navigator.openDetail(Symbol("MSFT"))

        assertEquals(DetailRoute(Symbol("MSFT"), initialSummary = null), navigator.currentRoute)
    }

    @Test
    fun `back pops the top route but never the last one`() {
        val navigator = navigator(MainRoute, SettingsRoute)

        navigator.goBack()
        navigator.goBack()

        assertEquals(listOf<NavKey>(MainRoute), navigator.backStack.toList())
    }

    @Test
    fun `settings are not stacked twice`() {
        val navigator = navigator(MainRoute)

        navigator.openSettings()
        navigator.openSettings()

        assertEquals(listOf(MainRoute, SettingsRoute), navigator.backStack.toList())
    }

    @Test
    fun `stored profile replaces the profile prompt with the watchlist tab`() {
        val navigator = navigator(ProfileRoute)
        navigator.selectTab(MainTab.SOURCES)

        navigator.syncWithProfile(hasProfile = true)

        assertEquals(listOf<NavKey>(MainRoute), navigator.backStack.toList())
        assertEquals(MainTab.WATCHLIST, navigator.selectedTab)
    }

    @Test
    fun `removed profile replaces every screen with the profile prompt`() {
        val navigator = navigator(MainRoute, DetailRoute(Symbol("AAPL")))

        navigator.syncWithProfile(hasProfile = false)

        assertEquals(listOf<NavKey>(ProfileRoute), navigator.backStack.toList())
    }

    @Test
    fun `profile state that matches the screens keeps the back stack`() {
        val withProfile = navigator(MainRoute, SettingsRoute)
        val withoutProfile = navigator(ProfileRoute)

        withProfile.syncWithProfile(hasProfile = true)
        withoutProfile.syncWithProfile(hasProfile = false)

        assertEquals(listOf(MainRoute, SettingsRoute), withProfile.backStack.toList())
        assertEquals(listOf<NavKey>(ProfileRoute), withoutProfile.backStack.toList())
    }

    @Test
    fun `show main resets the back stack and selects the tab`() {
        val navigator = navigator(MainRoute, SettingsRoute)
        navigator.selectTab(MainTab.SEARCH)

        navigator.showMain()

        assertEquals(listOf<NavKey>(MainRoute), navigator.backStack.toList())
        assertEquals(MainTab.WATCHLIST, navigator.selectedTab)
    }

    @Test
    fun `detail route with summary survives serialization`() {
        val route = DetailRoute(Symbol("SAP.DE"), summary("SAP.DE", market = null, sector = null, quote = null))

        assertEquals(route, Json.decodeFromString<DetailRoute>(Json.encodeToString(route)))
    }

    @Test
    fun `detail route without summary survives serialization`() {
        val route = DetailRoute(Symbol("AAPL"))

        assertEquals(route, Json.decodeFromString<DetailRoute>(Json.encodeToString(route)))
    }
}
