package com.sixteenscreener.app.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sixteenscreener.app.domain.model.Symbol
import com.sixteenscreener.app.testing.Companies.summary
import com.sixteenscreener.app.ui.theme.SixteenTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavHostTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val apple = summary("AAPL", name = "Apple Inc.")

    private val fakeDestinations = AppDestinations(
        profile = { Text("Profile prompt") },
        tab = { tab, navigator, modifier ->
            var count by rememberSaveable { mutableIntStateOf(0) }
            Column(modifier = modifier) {
                Text("Tab ${tab.name}")
                Button(onClick = { count++ }) { Text("${tab.name} count $count") }
                Button(onClick = { navigator.openDetail(apple) }) { Text("Open row") }
                Button(onClick = { navigator.openDetail(Symbol("MSFT")) }) { Text("Open symbol") }
            }
        },
        detail = { route, _ -> Text("Detail ${route.symbol.value}: ${route.initialSummary?.name ?: "no summary"}") },
        settings = { Text("Settings content") },
    )

    private var hasProfile by mutableStateOf(false)

    private fun start(hasProfile: Boolean) {
        this.hasProfile = hasProfile
        composeRule.setContent {
            SixteenTheme(dynamicColor = false) {
                AppNavHost(hasProfile = this.hasProfile, destinations = fakeDestinations)
            }
        }
    }

    private fun clickText(text: String) = composeRule.onNodeWithText(text).performClick()

    private fun pressBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    @Test
    fun `starts on the profile prompt without a stored profile`() {
        start(hasProfile = false)

        composeRule.onNodeWithText("Profile prompt").assertExists()
        composeRule.onNodeWithText("Tab WATCHLIST").assertDoesNotExist()
    }

    @Test
    fun `starts on the watchlist tab with a stored profile`() {
        start(hasProfile = true)

        composeRule.onNodeWithText("Tab WATCHLIST").assertExists()
        composeRule.onNodeWithText("Profile prompt").assertDoesNotExist()
    }

    @Test
    fun `selecting a profile opens the main screen`() {
        start(hasProfile = false)

        hasProfile = true

        composeRule.onNodeWithText("Tab WATCHLIST").assertExists()
        composeRule.onNodeWithText("Profile prompt").assertDoesNotExist()
    }

    @Test
    fun `removed profile returns to the profile prompt from any screen`() {
        start(hasProfile = true)
        clickText("Open row")

        hasProfile = false

        composeRule.onNodeWithText("Profile prompt").assertExists()
        composeRule.onNodeWithText("Detail AAPL: Apple Inc.").assertDoesNotExist()
    }

    @Test
    fun `tabs switch from the bottom bar`() {
        start(hasProfile = true)

        clickText("Search")
        composeRule.onNodeWithText("Tab SEARCH").assertExists()

        clickText("Sources")
        composeRule.onNodeWithText("Tab SOURCES").assertExists()
        composeRule.onNodeWithText("Tab SEARCH").assertDoesNotExist()
    }

    @Test
    fun `each tab keeps its state while switching`() {
        start(hasProfile = true)
        clickText("WATCHLIST count 0")
        clickText("WATCHLIST count 1")
        clickText("Search")
        clickText("SEARCH count 0")

        clickText("Watchlist")
        composeRule.onNodeWithText("WATCHLIST count 2").assertExists()

        clickText("Search")
        composeRule.onNodeWithText("SEARCH count 1").assertExists()
    }

    @Test
    fun `detail opened with a summary receives it`() {
        start(hasProfile = true)

        clickText("Open row")

        composeRule.onNodeWithText("Detail AAPL: Apple Inc.").assertExists()
    }

    @Test
    fun `detail opened without a summary receives only the symbol`() {
        start(hasProfile = true)

        clickText("Open symbol")

        composeRule.onNodeWithText("Detail MSFT: no summary").assertExists()
    }

    @Test
    fun `back from the detailed screen returns to the tab it was opened from with its state`() {
        start(hasProfile = true)
        clickText("Search")
        clickText("SEARCH count 0")
        clickText("Open row")
        composeRule.onNodeWithText("Tab SEARCH").assertDoesNotExist()

        pressBack()

        composeRule.onNodeWithText("Detail AAPL: Apple Inc.").assertDoesNotExist()
        composeRule.onNodeWithText("Tab SEARCH").assertExists()
        composeRule.onNodeWithText("SEARCH count 1").assertExists()
    }

    @Test
    fun `settings button opens settings and back returns to the main screen`() {
        start(hasProfile = true)
        clickText("Sources")

        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("Settings content").assertExists()

        pressBack()
        composeRule.onNodeWithText("Tab SOURCES").assertExists()
    }

    @Test
    fun `back stack with a detail route and the selected tab survive state restoration`() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            SixteenTheme(dynamicColor = false) {
                AppNavHost(hasProfile = true, destinations = fakeDestinations)
            }
        }
        clickText("Search")
        clickText("Open row")

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Detail AAPL: Apple Inc.").assertExists()
        pressBack()
        composeRule.onNodeWithText("Tab SEARCH").assertExists()
    }
}
